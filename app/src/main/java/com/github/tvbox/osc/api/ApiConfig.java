package com.github.tvbox.osc.api;

import static com.github.tvbox.osc.util.RegexUtils.getPattern;
import android.app.Activity;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Base64;

import com.github.catvod.crawler.JarLoader;
import com.github.catvod.crawler.JsLoader;
import com.github.catvod.crawler.pyLoader;
import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.python.IPyLoader;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.base.App;
import com.github.tvbox.osc.bean.IJKCode;
import com.github.tvbox.osc.bean.LiveChannelGroup;
import com.github.tvbox.osc.bean.LiveChannelItem;
import com.github.tvbox.osc.bean.ParseBean;
import com.github.tvbox.osc.bean.SourceBean;
import com.github.tvbox.osc.server.ControlManager;
import com.github.tvbox.osc.util.AES;
import com.github.tvbox.osc.util.AdBlocker;
import com.github.tvbox.osc.util.DefaultConfig;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.M3U8;
import com.github.tvbox.osc.util.MD5;
import com.github.tvbox.osc.util.FileUtils;
import com.github.tvbox.osc.util.VideoParseRuler;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.lzy.okgo.OkGo;
import com.lzy.okgo.callback.AbsCallback;
import com.lzy.okgo.model.Response;
import com.orhanobut.hawk.Hawk;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.github.tvbox.osc.util.LOG;
/**
 * @author pj567
 * @date :2020/12/18
 * @description:
 */
public class ApiConfig {
    private static ApiConfig instance;
    /** 回调统一回投主线程（调用方均在回调里做 UI 操作） */
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static void postMain(Runnable r) {
        MAIN.post(r);
    }
    private final LinkedHashMap<String, SourceBean> sourceBeanList;
    private SourceBean mHomeSource;
    private ParseBean mDefaultParse;
    private final List<LiveChannelGroup> liveChannelGroupList;
    private final List<ParseBean> parseBeanList;
    private List<String> vipParseFlags;
    // IJK 解码配置：构造时即写入内置默认（软解/硬解），点播配置加载成功后会被配置中的 ijk 段替换。
    // 关键：该列表任何时刻都不得为 null/空 —— 未配置点播接口（仅直播）时，
    // PlayerHelper.updateCfg → getIJKCodec 也会被调用，空列表取 get(0) 会直接崩溃闪退。
    private List<IJKCode> ijkCodes = new ArrayList<>();
    private String spider = null;
    /** 点播接口配置是否已在本进程内解析成功（主界面不再加载点播接口，由点播界面按需加载） */
    private volatile boolean loaded = false;
    public String wallpaper = "";
    public JsonArray livePlayHeaders;
    private final SourceBean emptyHome = new SourceBean();

    private final JarLoader jarLoader = new JarLoader();
    private final JsLoader jsLoader = new JsLoader();
    private final IPyLoader pyLoader =  new pyLoader();
    private final String userAgent = "okhttp/3.15";

    private final String requestAccept = "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9";

    private ApiConfig() {
        clearLoader();
        sourceBeanList = new LinkedHashMap<>();
        liveChannelGroupList = new ArrayList<>();
        parseBeanList = new ArrayList<>();
        // 初始化内置默认 IJK 解码配置，保证未加载任何点播配置时播放器仍可用
        initDefaultIjkCodes();
    }

    public static ApiConfig get() {
        if (instance == null) {
            synchronized (ApiConfig.class) {
                if (instance == null) {
                    instance = new ApiConfig();
                }
            }
        }
        return instance;
    }

    public static String FindResult(String json, String configKey) {
        String content = json;
        try {
            if (AES.isJson(content)) return content;
            Pattern pattern = getPattern("[A-Za-z0]{8}\\*\\*");
            Matcher matcher = pattern.matcher(content);
            if (matcher.find()) {
                content = content.substring(content.indexOf(matcher.group()) + 10);
                content = new String(Base64.decode(content, Base64.DEFAULT));
            }
            if (content.startsWith("2423")) {
                String data = content.substring(content.indexOf("2324") + 4, content.length() - 26);
                content = new String(AES.toBytes(content)).toLowerCase();
                String key = AES.rightPadding(content.substring(content.indexOf("$#") + 2, content.indexOf("#$")), "0", 16);
                String iv = AES.rightPadding(content.substring(content.length() - 13), "0", 16);
                json = AES.CBC(data, key, iv);
            } else if (configKey != null && !AES.isJson(content)) {
                json = AES.ECB(content, configKey);
            } else {
                // 对接本系统的密文接口（ke 前缀 / 固定密钥两种模式）：是 json 已在上面返回，
                // 不是 json 且非原生加密格式时尝试 ConfigCrypto 解密，失败原样返回
                json = com.github.tvbox.osc.n2n.ConfigCrypto.maybeDecrypt(content);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return json;
    }

    private static byte[] getImgJar(String body) {
        Pattern pattern = getPattern("[A-Za-z0]{8}\\*\\*");
        Matcher matcher = pattern.matcher(body);
        if (matcher.find()) {
            body = body.substring(body.indexOf(matcher.group()) + 10);
            return Base64.decode(body, Base64.DEFAULT);
        }
        return "".getBytes();
    }

    public void loadConfig(boolean useCache, LoadConfigCallback callback, Activity activity) {
        // Embedded Source : Update in Strings.xml if required
        // 点播界面调用时 HomeActivity 可能尚未 setRes（如从入口直达），优先用 App 资源
        String apiUrl = Hawk.get(HawkConfig.API_URL, App.getInstance().getString(R.string.app_source));
        if (apiUrl.isEmpty()) {
            // 未配置点播接口：若已配置直播接口，走静默跳过（错误码 "-1"，HomeActivity 收到后直接
            // 标记初始化完成、不再弹「源地址为空」对话框），让用户可以只把本应用当直播客户端用；
            // 直播接口也没有时才提示「源地址为空」。
            String liveUrl = Hawk.get(HawkConfig.LIVE_URL, "");
            if (liveUrl != null && !liveUrl.trim().isEmpty()) {
                callback.error("-1");
                return;
            }
            callback.error("源地址为空");
            return;
        }
        File cache = new File(App.getInstance().getFilesDir().getAbsolutePath() + "/" + MD5.encode(apiUrl));
        // 标记本次配置开始加载：若进程在加载/首页初始化阶段被系统杀死，
        // 下次启动由 ConfigGuard 自动清理该坏配置，避免反复崩溃
        com.github.tvbox.osc.n2n.ConfigGuard.begin(App.getInstance(), apiUrl, cache);
        if (useCache && cache.exists()) {
            // 缓存解析（文件 IO + Gson）放工作线程，避免阻塞首页主线程
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        parseJson(apiUrl, cache);
                        postMain(new Runnable() {
                            @Override
                            public void run() {
                                callback.success();
                            }
                        });
                        return;
                    } catch (Throwable th) {
                        th.printStackTrace();
                    }
                    // 缓存损坏时按原逻辑回落网络拉取
                    fetchConfig(apiUrl, cache, callback);
                }
            }, "cfg-cache").start();
            return;
        }
        fetchConfig(apiUrl, cache, callback);
    }

    /** 网络拉取配置并解析（可从任意线程调用；回调均回投主线程） */
    private void fetchConfig(final String apiUrl, final File cache, final LoadConfigCallback callback) {
        String TempKey = null, configUrl = "", pk = ";pk;";
        if (apiUrl.contains(pk)) {
            String[] a = apiUrl.split(pk);
            TempKey = a[1];
            if (apiUrl.startsWith("clan")) {
                configUrl = clanToAddress(a[0]);
            } else if (apiUrl.startsWith("http")) {
                configUrl = a[0];
            } else {
                configUrl = "http://" + a[0];
            }
        } else if (apiUrl.startsWith("clan")) {
            configUrl = clanToAddress(apiUrl);
        } else if (!apiUrl.startsWith("http")) {
            configUrl = "http://" + configUrl;
        } else {
            configUrl = apiUrl;
        }
        System.out.println("API URL :" + configUrl);
        String configKey = TempKey;
        // 请求附加本机参数（mac/ip/机型/系统版本等），服务端可按机下发点播源
        OkGo.<String>get(com.github.tvbox.osc.n2n.DeviceParams.append(configUrl))
                .headers("User-Agent", userAgent)
                .headers("Accept", requestAccept)
                .execute(new AbsCallback<String>() {
                    @Override
                    public void onSuccess(Response<String> response) {
                        final String json = response.body();
                        // 极端情况下 response.body() 可能返回 null（HTTP 204 / 空 body / 反序列化失败），
                        // 此时不能再传给 parseJson 与 FileOutputStream，直接回退错误并提示用户。
                        if (json == null || json.trim().isEmpty()) {
                            postMain(new Runnable() {
                                @Override
                                public void run() {
                                    callback.error("接口返回为空，请检查地址");
                                }
                            });
                            return;
                        }
                        // 解析 + 写缓存放工作线程，避免主线程掉帧
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    parseJson(apiUrl, json);
                                    try {
                                        File cacheDir = cache.getParentFile();
                                        if (!cacheDir.exists())
                                            cacheDir.mkdirs();
                                        if (cache.exists())
                                            cache.delete();
                                        FileOutputStream fos = new FileOutputStream(cache);
                                        fos.write(json.getBytes("UTF-8"));
                                        fos.flush();
                                        fos.close();
                                    } catch (Throwable th) {
                                        th.printStackTrace();
                                    }
                                    postMain(new Runnable() {
                                        @Override
                                        public void run() {
                                            callback.success();
                                        }
                                    });
                                } catch (Throwable th) {
                                    th.printStackTrace();
                                    postMain(new Runnable() {
                                        @Override
                                        public void run() {
                                            callback.error("解析配置失败");
                                        }
                                    });
                                }
                            }
                        }, "cfg-parse").start();
                    }

                    @Override
                    public void onError(Response<String> response) {
                        super.onError(response);
                        final String errMsg = response.getException() != null ? response.getException().getMessage() : "";
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                if (cache.exists()) {
                                    try {
                                        parseJson(apiUrl, cache);
                                        postMain(new Runnable() {
                                            @Override
                                            public void run() {
                                                callback.success();
                                            }
                                        });
                                        return;
                                    } catch (Throwable th) {
                                        th.printStackTrace();
                                    }
                                }
                                postMain(new Runnable() {
                                    @Override
                                    public void run() {
                                        callback.error("拉取配置失败\n" + errMsg);
                                    }
                                });
                            }
                        }, "cfg-err").start();
                    }

                    public String convertResponse(okhttp3.Response response) throws Throwable {
                        String result = "";
                        if (response.body() == null) {
                            result = "";
                        } else {
                            result = FindResult(response.body().string(), configKey);
                        }
                        if (apiUrl.startsWith("clan")) {
                            result = clanContentFix(clanToAddress(apiUrl), result);
                        }
                        result = fixContentPath(apiUrl, result);
                        return result;
                    }
                });
    }

    public void loadJar(final boolean useCache, String spider, final LoadConfigCallback callback) {
        String[] urls = spider.split(";md5;");
        final String jarUrlRaw = urls[0];
        final String md5 = urls.length > 1 ? urls[1].trim() : "";
        final File cache = new File(App.getInstance().getFilesDir().getAbsolutePath() + "/csp/"+MD5.string2MD5(jarUrlRaw)+".jar");
        if (!md5.isEmpty() || useCache) {
            if (cache.exists()) {
                // md5 校验 + dex 加载放工作线程（DexClassLoader 加载非常耗时）
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        boolean hit = useCache || MD5.getFileMd5(cache).equalsIgnoreCase(md5);
                        if (hit) {
                            if (jarLoader.load(cache.getAbsolutePath())) {
                                postMain(new Runnable() {
                                    @Override
                                    public void run() {
                                        callback.success();
                                    }
                                });
                            } else {
                                postMain(new Runnable() {
                                    @Override
                                    public void run() {
                                        callback.error("从缓存加载jar失败");
                                    }
                                });
                            }
                        } else {
                            loadJarNet(jarUrlRaw, cache, callback);
                        }
                    }
                }, "jar-cache").start();
                return;
            }
        } else {
            if (Boolean.parseBoolean(jarCache) && cache.exists()) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        if (!FileUtils.isWeekAgo(cache) && jarLoader.load(cache.getAbsolutePath())) {
                            postMain(new Runnable() {
                                @Override
                                public void run() {
                                    callback.success();
                                }
                            });
                        } else {
                            loadJarNet(jarUrlRaw, cache, callback);
                        }
                    }
                }, "jar-cache").start();
                return;
            }
        }
        loadJarNet(jarUrlRaw, cache, callback);
    }

    /** 网络下载 spider jar 并加载（可从任意线程调用；回调均回投主线程） */
    private void loadJarNet(String jarUrlRaw, final File cache, final LoadConfigCallback callback) {
        boolean isJarInImg = jarUrlRaw.startsWith("img+");
        String jarUrl = jarUrlRaw.replace("img+", "");
        OkGo.<File>get(jarUrl)
                .headers("User-Agent", userAgent)
                .headers("Accept", requestAccept)
                .execute(new AbsCallback<File>() {

                    @Override
                    public File convertResponse(okhttp3.Response response){
                        File cacheDir = cache.getParentFile();
                        assert cacheDir != null;
                        if (!cacheDir.exists()) cacheDir.mkdirs();
                        if (cache.exists()) cache.delete();
                        // 3. 使用 try-with-resources 确保流关闭
                        assert response.body() != null;
                        try (FileOutputStream fos = new FileOutputStream(cache)) {
                            if (isJarInImg) {
                                String respData = response.body().string();
                                LOG.i("echo---jar Response: " + respData);
                                byte[] imgJar = getImgJar(respData);
                                if (imgJar == null || imgJar.length == 0) {
                                    LOG.e("echo---Generated JAR data is empty");
                                    callback.error("JAR data is empty");
                                }
                                fos.write(imgJar);
                            } else {
                                // 使用流式传输避免内存溢出
                                InputStream inputStream = response.body().byteStream();
                                byte[] buffer = new byte[4096];
                                int bytesRead;
                                while ((bytesRead = inputStream.read(buffer)) != -1) {
                                    fos.write(buffer, 0, bytesRead);
                                }
                            }
                            fos.flush();
                        } catch (IOException e) {
                            return null;
                        }
                        return cache;
                    }

                    @Override
                    public void onSuccess(Response<File> response) {
                        final File file = response.body();
                        // dex 加载放工作线程
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                if (file != null && file.exists()) {
                                    try {
                                        if (jarLoader.load(file.getAbsolutePath())) {
                                            postMain(new Runnable() {
                                                @Override
                                                public void run() {
                                                    callback.success();
                                                }
                                            });
                                        } else {
                                            LOG.e("echo---jar Loader returned false");
                                            postMain(new Runnable() {
                                                @Override
                                                public void run() {
                                                    callback.error("从网络上加载jar写入缓存后加载失败");
                                                }
                                            });
                                        }
                                    } catch (Exception e) {
                                        LOG.e("echo---jar Loader threw exception: " + e.getMessage());
                                        final String em = "JAR加载异常: " + e.getMessage();
                                        postMain(new Runnable() {
                                            @Override
                                            public void run() {
                                                callback.error(em);
                                            }
                                        });
                                    }
                                } else {
                                    LOG.e("echo---jar File not found");
                                    postMain(new Runnable() {
                                        @Override
                                        public void run() {
                                            callback.error("从网络上加载jar地址字节数据为空");
                                        }
                                    });
                                }
                            }
                        }, "jar-load").start();
                    }

                    @Override
                    public void onError(Response<File> response) {
                        Throwable ex = response.getException();
                        if (ex != null) {
                            LOG.i("echo---jar Request failed: " + ex.getMessage());
                        }
                        final String em = ex != null ? "从网络上加载jar失败：" + ex.getMessage() : "未知网络错误";
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                if (cache.exists()) jarLoader.load(cache.getAbsolutePath());
                                postMain(new Runnable() {
                                    @Override
                                    public void run() {
                                        callback.error(em);
                                    }
                                });
                            }
                        }, "jar-err").start();
                    }
                });
    }

    private void parseJson(String apiUrl, File f) throws Throwable {
        System.out.println("从本地缓存加载" + f.getAbsolutePath());
        BufferedReader bReader = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String s = "";
        while ((s = bReader.readLine()) != null) {
            sb.append(s + "\n");
        }
        bReader.close();
        parseJson(apiUrl, sb.toString());
    }

    private static  String jarCache ="true";
    private void parseJson(String apiUrl, String jsonStr) {
        // 防御：jsonStr 为空或 null 时 Gson.fromJson 会抛 NullPointerException，
        // 调用方外层虽然有 try-catch，但这里先做显式保护让异常信息更可追踪
        if (jsonStr == null || jsonStr.trim().isEmpty()) {
            throw new IllegalStateException("点播接口返回内容为空，apiUrl=" + apiUrl);
        }
//        pyLoader.setConfig(jsonStr);
        JsonObject infoJson = new Gson().fromJson(jsonStr, JsonObject.class);
        loaded = false;
        // 重新加载点播接口时以新配置为准：清掉旧的主配置源（保留自定义点播界面注入的 cv_ 源）
        List<String> oldKeys = new ArrayList<>(sourceBeanList.keySet());
        for (String k : oldKeys) {
            if (k != null && !k.startsWith(CUSTOM_SRC_PREFIX)) {
                sourceBeanList.remove(k);
            }
        }
        jarCache = DefaultConfig.safeJsonString(infoJson, "jarCache", "true");
        // spider
        spider = DefaultConfig.safeJsonString(infoJson, "spider", "");
        // wallpaper
        wallpaper = DefaultConfig.safeJsonString(infoJson, "wallpaper", "");
        // 免责声明（点播接口配置 warningText，为空则不弹窗）
        Hawk.put(HawkConfig.WARNING_TEXT, DefaultConfig.safeJsonString(infoJson, "warningText", ""));
        // 直播播放请求头
        livePlayHeaders = infoJson.getAsJsonArray("livePlayHeaders");
        // 远端站点源
        SourceBean firstSite = null;
        JsonArray sites = infoJson.has("video") ? infoJson.getAsJsonObject("video").getAsJsonArray("sites") : infoJson.get("sites").getAsJsonArray();
        for (JsonElement opt : sites) {
            JsonObject obj = (JsonObject) opt;
            SourceBean sb = new SourceBean();
            String siteKey = obj.get("key").getAsString().trim();
            sb.setKey(siteKey);
            sb.setName(obj.has("name")?obj.get("name").getAsString().trim():siteKey);
            sb.setType(obj.get("type").getAsInt());
            sb.setApi(obj.get("api").getAsString().trim());
            sb.setSearchable(DefaultConfig.safeJsonInt(obj, "searchable", 1));
            sb.setQuickSearch(DefaultConfig.safeJsonInt(obj, "quickSearch", 1));
            if(siteKey.startsWith("py_")){
                sb.setFilterable(1);
            }else {
                sb.setFilterable(DefaultConfig.safeJsonInt(obj, "filterable", 1));
            }
            sb.setHide(DefaultConfig.safeJsonInt(obj, "hide", 0));
            sb.setPlayerUrl(DefaultConfig.safeJsonString(obj, "playUrl", ""));
            sb.setExt(DefaultConfig.safeJsonString(obj, "ext", ""));
            sb.setJar(DefaultConfig.safeJsonString(obj, "jar", ""));
            sb.setPlayerType(DefaultConfig.safeJsonInt(obj, "playerType", -1));
            sb.setCategories(DefaultConfig.safeJsonStringList(obj, "categories"));
            sb.setClickSelector(DefaultConfig.safeJsonString(obj, "click", ""));
            sb.setStyle(DefaultConfig.safeJsonString(obj, "style", ""));
            if (firstSite == null && sb.getHide() == 0)
                firstSite = sb;
            sourceBeanList.put(siteKey, sb);
        }
        if (sourceBeanList != null && sourceBeanList.size() > 0) {
            String home = Hawk.get(HawkConfig.HOME_API, "");
            SourceBean sh = getSource(home);
            if (sh == null || sh.getHide() == 1) {
                // 全部站点都被 hide 时 firstSite 为空，不再写首页源（避免空指针），由点播界面报「无可用源」
                if (firstSite != null) setSourceBean(firstSite);
            } else {
                setSourceBean(sh);
            }
        }
        // 需要使用vip解析的flag
        vipParseFlags = DefaultConfig.safeJsonStringList(infoJson, "flags");
        // 解析地址
        parseBeanList.clear();
        if (infoJson.has("parses")) {
            JsonArray parses = infoJson.get("parses").getAsJsonArray();
            for (JsonElement opt : parses) {
                JsonObject obj = (JsonObject) opt;
                ParseBean pb = new ParseBean();
                pb.setName(obj.get("name").getAsString().trim());
                pb.setUrl(obj.get("url").getAsString().trim());
                String ext = obj.has("ext") ? obj.get("ext").getAsJsonObject().toString() : "";
                pb.setExt(ext);
                pb.setType(DefaultConfig.safeJsonInt(obj, "type", 0));
                parseBeanList.add(pb);
            }
            if(!parseBeanList.isEmpty()){
                addSuperParse();
            }
        }
        // 获取默认解析
        if (parseBeanList != null && parseBeanList.size() > 0) {
            String defaultParse = Hawk.get(HawkConfig.DEFAULT_PARSE, "");
            if (!TextUtils.isEmpty(defaultParse))
                for (ParseBean pb : parseBeanList) {
                    if (pb.getName().equals(defaultParse))
                        setDefaultParse(pb);
                }
            if (mDefaultParse == null)
                setDefaultParse(parseBeanList.get(0));
        }

        // takagen99: Check if Live URL is setup in Settings, if no, get from File Config
        liveChannelGroupList.clear();           //修复从后台切换重复加载频道列表
        String liveURL = Hawk.get(HawkConfig.LIVE_URL, "");
        String epgURL  = Hawk.get(HawkConfig.EPG_URL, "");

        String liveURL_final = null;
        try {
            if (infoJson.has("lives") && infoJson.get("lives").getAsJsonArray() != null) {
                JsonObject livesOBJ = infoJson.get("lives").getAsJsonArray().get(0).getAsJsonObject();
                String lives = livesOBJ.toString();
                int index = lives.indexOf("proxy://");
                if (index != -1) {
                    int endIndex = lives.lastIndexOf("\"");
                    String url = lives.substring(index, endIndex);
                    url = DefaultConfig.checkReplaceProxy(url);

                    //clan
                    String extUrl = Uri.parse(url).getQueryParameter("ext");
                    if (extUrl != null && !extUrl.isEmpty()) {
                        String extUrlFix;
                        if (extUrl.startsWith("http") || extUrl.startsWith("clan://")) {
                            extUrlFix = extUrl;
                        } else {
                            extUrlFix = new String(Base64.decode(extUrl, Base64.DEFAULT | Base64.URL_SAFE | Base64.NO_WRAP), "UTF-8");
                        }
                        if (extUrlFix.startsWith("clan://")) {
                            extUrlFix = clanContentFix(clanToAddress(apiUrl), extUrlFix);
                        }

                        // takagen99: Capture Live URL into Config
                        System.out.println("Live URL :" + extUrlFix);
                        putLiveHistory(extUrlFix);
                        // Overwrite with Live URL from Settings
                        if (StringUtils.isBlank(liveURL)) {
                            if (com.github.tvbox.osc.n2n.RemoteConfig.forceOverrideFor("live")
                                    || !Hawk.get(HawkConfig.LIVE_URL + "_manual", false)) {
                                Hawk.put(HawkConfig.LIVE_URL, extUrlFix);
                            } else {
                                // 用户手动清空过：不再使用 JSON 的直播地址
                                extUrlFix = "";
                            }
                        } else {
                            extUrlFix = liveURL;
                        }

                        // Final Live URL
                        liveURL_final = extUrlFix;

//                    // Encoding the Live URL
//                    extUrlFix = Base64.encodeToString(extUrlFix.getBytes("UTF-8"), Base64.DEFAULT | Base64.URL_SAFE | Base64.NO_WRAP);
//                    url = url.replace(extUrl, extUrlFix);
                    }

                    // takagen99 : Getting EPG URL from File Config & put into Settings
                    if (livesOBJ.has("epg")) {
                        String epg = livesOBJ.get("epg").getAsString();
                        System.out.println("EPG URL :" + epg);
                        putEPGHistory(epg);
                        // Overwrite with EPG URL from Settings
                        if (StringUtils.isBlank(epgURL)) {
                            if (com.github.tvbox.osc.n2n.RemoteConfig.forceOverrideFor("epg")
                                    || !Hawk.get(HawkConfig.EPG_URL + "_manual", false)) {
                                Hawk.put(HawkConfig.EPG_URL, epg);
                            }
                        } else {
                            Hawk.put(HawkConfig.EPG_URL, epgURL);
                        }
                    }

//                // Populate Live Channel Listing
//                LiveChannelGroup liveChannelGroup = new LiveChannelGroup();
//                liveChannelGroup.setGroupName(url);
//                liveChannelGroupList.add(liveChannelGroup);

                } else {

                    // if FongMi Live URL Formatting exists
                    if (!lives.contains("type")) {
                        loadLives(infoJson.get("lives").getAsJsonArray());
                    } else {
                        JsonObject fengMiLives = infoJson.get("lives").getAsJsonArray().get(0).getAsJsonObject();
                        Hawk.put(HawkConfig.LIVE_PLAYER_TYPE, DefaultConfig.safeJsonInt(fengMiLives, "playerType", -1));
                        String type = fengMiLives.get("type").getAsString();
                        if (type.equals("0")) {
                            String url = fengMiLives.get("url").getAsString();

                            // takagen99 : Getting EPG URL from File Config & put into Settings
                            if (fengMiLives.has("epg")) {
                                String epg = fengMiLives.get("epg").getAsString();
                                System.out.println("EPG URL :" + epg);
                                putEPGHistory(epg);
                                // Overwrite with EPG URL from Settings
                                if (StringUtils.isBlank(epgURL)) {
                                    if (com.github.tvbox.osc.n2n.RemoteConfig.forceOverrideFor("epg")
                                            || !Hawk.get(HawkConfig.EPG_URL + "_manual", false)) {
                                        Hawk.put(HawkConfig.EPG_URL, epg);
                                    }
                                } else {
                                    Hawk.put(HawkConfig.EPG_URL, epgURL);
                                }
                            }

                            if (url.startsWith("http")) {
                                // takagen99: Capture Live URL into Settings
                                System.out.println("Live URL :" + url);
                                putLiveHistory(url);
                                // Overwrite with Live URL from Settings
                                if (StringUtils.isBlank(liveURL)) {
                                    if (com.github.tvbox.osc.n2n.RemoteConfig.forceOverrideFor("live")
                                            || !Hawk.get(HawkConfig.LIVE_URL + "_manual", false)) {
                                        Hawk.put(HawkConfig.LIVE_URL, url);
                                    } else {
                                        // 用户手动清空过：不再使用 JSON 的直播地址
                                        url = "";
                                    }
                                } else {
                                    url = liveURL;
                                }

                                // Final Live URL
                                liveURL_final = url;

//                            url = Base64.encodeToString(url.getBytes("UTF-8"), Base64.DEFAULT | Base64.URL_SAFE | Base64.NO_WRAP);
                            }
                        }
                    }
                }

                // takagen99: Load Live Channel from settings URL (WIP)
                if (StringUtils.isBlank(liveURL_final)) {
                    liveURL_final = liveURL;
                }
                // 直接存原始直播列表 URL（不再强制包 n2n 代理壳），
                // LivePlayActivity.loadProxyLives 同时支持代理格式（带 ext）和直接 URL
                LiveChannelGroup liveChannelGroup = new LiveChannelGroup();
                liveChannelGroup.setGroupName(liveURL_final);
                liveChannelGroupList.add(liveChannelGroup);
            }


        } catch (Throwable th) {
            th.printStackTrace();
        }

        // Video parse rule for host
        if (infoJson.has("rules")) {
            VideoParseRuler.clearRule();
            for(JsonElement oneHostRule : infoJson.getAsJsonArray("rules")) {
                JsonObject obj = (JsonObject) oneHostRule;
                //嗅探过滤规则
                if (obj.has("host")) {
                    String host = obj.get("host").getAsString();
                    if (obj.has("rule")) {
                        JsonArray ruleJsonArr = obj.getAsJsonArray("rule");
                        ArrayList<String> rule = new ArrayList<>();
                        for (JsonElement one : ruleJsonArr) {
                            String oneRule = one.getAsString();
                            rule.add(oneRule);
                        }
                        if (rule.size() > 0) {
                            VideoParseRuler.addHostRule(host, rule);
                        }
                    }
                    if (obj.has("filter")) {
                        JsonArray filterJsonArr = obj.getAsJsonArray("filter");
                        ArrayList<String> filter = new ArrayList<>();
                        for (JsonElement one : filterJsonArr) {
                            String oneFilter = one.getAsString();
                            filter.add(oneFilter);
                        }
                        if (filter.size() > 0) {
                            VideoParseRuler.addHostFilter(host, filter);
                        }
                    }
                }
                //广告过滤规则
                if (obj.has("hosts") && obj.has("regex")) {
                    ArrayList<String> rule = new ArrayList<>();
                    ArrayList<String> ads = new ArrayList<>();
                    JsonArray regexArray = obj.getAsJsonArray("regex");
                    for (JsonElement one : regexArray) {
                        String regex = one.getAsString();
                        if (M3U8.isAd(regex)) ads.add(regex);
                        else rule.add(regex);
                    }
                    JsonArray array = obj.getAsJsonArray("hosts");
                    for (JsonElement one : array) {
                        String host = one.getAsString();
                        VideoParseRuler.addHostRule(host, rule);
                        VideoParseRuler.addHostRegex(host, ads);
                    }
                }
                //嗅探脚本规则 如 click
                if (obj.has("hosts") && obj.has("script")) {
                    ArrayList<String> scripts = new ArrayList<>();
                    JsonArray scriptArray = obj.getAsJsonArray("script");
                    for (JsonElement one : scriptArray) {
                        String script = one.getAsString();
                        scripts.add(script);
                    }
                    JsonArray array = obj.getAsJsonArray("hosts");
                    for (JsonElement one : array) {
                        String host = one.getAsString();
                        VideoParseRuler.addHostScript(host, scripts);
                    }
                }
            }
        }

        String defaultIJKADS = "{\"ijk\":[{\"options\":[{\"name\":\"opensles\",\"category\":4,\"value\":\"0\"},{\"name\":\"overlay-format\",\"category\":4,\"value\":\"842225234\"},{\"name\":\"framedrop\",\"category\":4,\"value\":\"0\"},{\"name\":\"soundtouch\",\"category\":4,\"value\":\"1\"},{\"name\":\"start-on-prepared\",\"category\":4,\"value\":\"1\"},{\"name\":\"http-detect-rangeupport\",\"category\":1,\"value\":\"0\"},{\"name\":\"fflags\",\"category\":1,\"value\":\"fastseek\"},{\"name\":\"skip_loop_filter\",\"category\":2,\"value\":\"48\"},{\"name\":\"reconnect\",\"category\":4,\"value\":\"1\"},{\"name\":\"enable-accurate-seek\",\"category\":4,\"value\":\"0\"},{\"name\":\"mediacodec\",\"category\":4,\"value\":\"0\"},{\"name\":\"mediacodec-auto-rotate\",\"category\":4,\"value\":\"0\"},{\"name\":\"mediacodec-handle-resolution-change\",\"category\":4,\"value\":\"0\"},{\"name\":\"mediacodec-hevc\",\"category\":4,\"value\":\"0\"},{\"name\":\"dns_cache_timeout\",\"category\":1,\"value\":\"600000000\"}],\"group\":\"软解码\"},{\"options\":[{\"name\":\"opensles\",\"category\":4,\"value\":\"0\"},{\"name\":\"overlay-format\",\"category\":4,\"value\":\"842225234\"},{\"name\":\"framedrop\",\"category\":4,\"value\":\"0\"},{\"name\":\"soundtouch\",\"category\":4,\"value\":\"1\"},{\"name\":\"start-on-prepared\",\"category\":4,\"value\":\"1\"},{\"name\":\"http-detect-rangeupport\",\"category\":1,\"value\":\"0\"},{\"name\":\"fflags\",\"category\":1,\"value\":\"fastseek\"},{\"name\":\"skip_loop_filter\",\"category\":2,\"value\":\"48\"},{\"name\":\"reconnect\",\"category\":4,\"value\":\"1\"},{\"name\":\"enable-accurate-seek\",\"category\":4,\"value\":\"0\"},{\"name\":\"mediacodec\",\"category\":4,\"value\":\"1\"},{\"name\":\"mediacodec-auto-rotate\",\"category\":4,\"value\":\"1\"},{\"name\":\"mediacodec-handle-resolution-change\",\"category\":4,\"value\":\"1\"},{\"name\":\"mediacodec-hevc\",\"category\":4,\"value\":\"1\"},{\"name\":\"dns_cache_timeout\",\"category\":1,\"value\":\"600000000\"}],\"group\":\"硬解码\"}],\"ads\":[\"mimg.0c1q0l.cn\",\"www.googletagmanager.com\",\"www.google-analytics.com\",\"mc.usihnbcq.cn\",\"mg.g1mm3d.cn\",\"mscs.svaeuzh.cn\",\"cnzz.hhttm.top\",\"tp.vinuxhome.com\",\"cnzz.mmstat.com\",\"www.baihuillq.com\",\"s23.cnzz.com\",\"z3.cnzz.com\",\"c.cnzz.com\",\"stj.v1vo.top\",\"z12.cnzz.com\",\"img.mosflower.cn\",\"tips.gamevvip.com\",\"ehwe.yhdtns.com\",\"xdn.cqqc3.com\",\"www.jixunkyy.cn\",\"sp.chemacid.cn\",\"hm.baidu.com\",\"s9.cnzz.com\",\"z6.cnzz.com\",\"um.cavuc.com\",\"mav.mavuz.com\",\"wofwk.aoidf3.com\",\"z5.cnzz.com\",\"xc.hubeijieshikj.cn\",\"tj.tianwenhu.com\",\"xg.gars57.cn\",\"k.jinxiuzhilv.com\",\"cdn.bootcss.com\",\"ppl.xunzhuo123.com\",\"xomk.jiangjunmh.top\",\"img.xunzhuo123.com\",\"z1.cnzz.com\",\"s13.cnzz.com\",\"xg.huataisangao.cn\",\"z7.cnzz.com\",\"xg.huataisangao.cn\",\"z2.cnzz.com\",\"s96.cnzz.com\",\"q11.cnzz.com\",\"thy.dacedsfa.cn\",\"xg.whsbpw.cn\",\"s19.cnzz.com\",\"z8.cnzz.com\",\"s4.cnzz.com\",\"f5w.as12df.top\",\"ae01.alicdn.com\",\"www.92424.cn\",\"k.wudejia.com\",\"vivovip.mmszxc.top\",\"qiu.xixiqiu.com\",\"cdnjs.hnfenxun.com\",\"cms.qdwght.com\"]}";
        JsonObject defaultJson = new Gson().fromJson(defaultIJKADS, JsonObject.class);
        // 广告地址
        if(AdBlocker.isEmpty()){
            //默认广告拦截
            for (JsonElement host : defaultJson.getAsJsonArray("ads")) {
                AdBlocker.addAdHost(host.getAsString());
            }
            //追加的广告拦截
            if(infoJson.has("ads")){
                for (JsonElement host : infoJson.getAsJsonArray("ads")) {
                    if(!AdBlocker.hasHost(host.getAsString())){
                        AdBlocker.addAdHost(host.getAsString());
                    }
                }
            }
        }
        // IJK解码配置
        // IJK 解码配置：点播配置中带了 "ijk" 段时，用配置整体替换内置默认；
        // 未带时保留构造函数中写入的内置默认（保证仅直播场景下播放器可用，不崩溃）
        if (infoJson.has("ijk")) {
            List<IJKCode> fromConfig = new ArrayList<>();
            boolean foundOldSelect = false;
            String ijkCodec = Hawk.get(HawkConfig.IJK_CODEC, "");
            JsonArray ijkJsonArray = infoJson.get("ijk").getAsJsonArray();
            for (JsonElement opt : ijkJsonArray) {
                JsonObject obj = (JsonObject) opt;
                String name = obj.get("group").getAsString();
                LinkedHashMap<String, String> baseOpt = new LinkedHashMap<>();
                for (JsonElement cfg : obj.get("options").getAsJsonArray()) {
                    JsonObject cObj = (JsonObject) cfg;
                    String key = cObj.get("category").getAsString() + "|" + cObj.get("name").getAsString();
                    String val = cObj.get("value").getAsString();
                    baseOpt.put(key, val);
                }
                IJKCode codec = new IJKCode();
                codec.setName(name);
                codec.setOption(baseOpt);
                if (name.equals(ijkCodec) || TextUtils.isEmpty(ijkCodec)) {
                    codec.selected(true);
                    ijkCodec = name;
                    foundOldSelect = true;
                } else {
                    codec.selected(false);
                }
                fromConfig.add(codec);
            }
            if (!foundOldSelect && fromConfig.size() > 0) {
                fromConfig.get(0).selected(true);
            }
            if (!fromConfig.isEmpty()) {
                ijkCodes = fromConfig;
            }
        }
        // 全部字段解析完成才算加载成功（失败时保持 false，点播界面重试会重新拉取接口）
        loaded = true;
    }

    /**
     * 写入内置默认 IJK 解码配置（软解码/硬解码两组，参数与历史上 defaultIJKADS 内置值一致）。
     * 构造函数调用，保证 ijkCodes 任何时刻非空：未配置点播接口、仅配置直播的场景下，
     * 直播播放器初始化同样依赖该列表（此前为空导致 get(0) 空指针、点直播即闪退）。
     */
    private void initDefaultIjkCodes() {
        ijkCodes.clear();
        ijkCodes.add(buildDefaultIjkCode("软解码", false));
        ijkCodes.add(buildDefaultIjkCode("硬解码", true));
        // 按用户上次选择（Hawk IJK_CODEC）标记选中项；无记录时默认选中第一项
        String sel = Hawk.get(HawkConfig.IJK_CODEC, "");
        boolean found = false;
        for (IJKCode code : ijkCodes) {
            if (code.getName().equals(sel)) {
                code.selected(true);
                found = true;
            } else {
                code.selected(false);
            }
        }
        if (!found) {
            ijkCodes.get(0).selected(true);
        }
    }

    /**
     * 构造一组默认 IJK 解码参数。两组参数仅 mediacodec 系列不同（硬解开启、软解关闭），
     * 其余与上游 takagen99/Box 内置默认值保持一致。
     * @param name 组名（软解码/硬解码）
     * @param hardware true=硬解码（开启 mediacodec），false=软解码
     */
    private IJKCode buildDefaultIjkCode(String name, boolean hardware) {
        LinkedHashMap<String, String> opt = new LinkedHashMap<>();
        opt.put("4|opensles", "0");
        opt.put("4|overlay-format", "842225234");
        opt.put("4|framedrop", "0");
        opt.put("4|soundtouch", "1");
        opt.put("4|start-on-prepared", "1");
        opt.put("1|http-detect-rangeupport", "0");
        opt.put("1|fflags", "fastseek");
        opt.put("2|skip_loop_filter", "48");
        opt.put("4|reconnect", "1");
        opt.put("4|enable-accurate-seek", "0");
        opt.put("4|mediacodec", hardware ? "1" : "0");
        opt.put("4|mediacodec-auto-rotate", hardware ? "1" : "0");
        opt.put("4|mediacodec-handle-resolution-change", hardware ? "1" : "0");
        opt.put("4|mediacodec-hevc", hardware ? "1" : "0");
        opt.put("1|dns_cache_timeout", "600000000");
        IJKCode code = new IJKCode();
        code.setName(name);
        code.setOption(opt);
        return code;
    }

    private void putLiveHistory(String url) {
        if (!url.isEmpty()) {
            ArrayList<String> liveHistory = Hawk.get(HawkConfig.LIVE_HISTORY, new ArrayList<String>());
            if (!liveHistory.contains(url))
                liveHistory.add(0, url);
            if (liveHistory.size() > 20)
                liveHistory.remove(20);
            Hawk.put(HawkConfig.LIVE_HISTORY, liveHistory);
        }
    }

    public static void putEPGHistory(String url) {
        if (!url.isEmpty()) {
            ArrayList<String> epgHistory = Hawk.get(HawkConfig.EPG_HISTORY, new ArrayList<String>());
            if (!epgHistory.contains(url))
                epgHistory.add(0, url);
            if (epgHistory.size() > 20)
                epgHistory.remove(20);
            Hawk.put(HawkConfig.EPG_HISTORY, epgHistory);
        }
    }

    public void loadLives(JsonArray livesArray) {
        liveChannelGroupList.clear();
        int groupIndex = 0;
        int channelIndex = 0;
        int channelNum = 0;
        for (JsonElement groupElement : livesArray) {
            if (!groupElement.isJsonObject()) continue;
            JsonObject groupObj = groupElement.getAsJsonObject();
            LiveChannelGroup liveChannelGroup = new LiveChannelGroup();
            liveChannelGroup.setLiveChannels(new ArrayList<LiveChannelItem>());
            liveChannelGroup.setGroupIndex(groupIndex++);
            String groupName = optString(groupObj, "group");
            if (groupName.isEmpty()) groupName = optString(groupObj, "name");
            if (groupName.isEmpty()) groupName = "未分组";
            String[] splitGroupName = groupName.split("_", 2);
            liveChannelGroup.setGroupName(splitGroupName[0]);
            if (splitGroupName.length > 1)
                liveChannelGroup.setGroupPassword(splitGroupName[1]);
            else
                liveChannelGroup.setGroupPassword("");
            channelIndex = 0;
            JsonElement channelsEle = groupObj.get("channels");
            if (channelsEle == null || !channelsEle.isJsonArray()) continue;
            for (JsonElement channelElement : channelsEle.getAsJsonArray()) {
                if (!channelElement.isJsonObject()) continue;
                JsonObject obj = channelElement.getAsJsonObject();
                LiveChannelItem liveChannelItem = new LiveChannelItem();
                String channelName = optString(obj, "name");
                liveChannelItem.setChannelName(channelName.isEmpty() ? "未命名" : channelName);
                liveChannelItem.setChannelIndex(channelIndex++);
                liveChannelItem.setChannelNum(++channelNum);
                // 频道级默认解码方式：频道下所有线路未单独声明时使用
                String channelDecode = normalizeDecode(obj, "", "");
                // 兼容 urls:[{url,name,decode}] 与 urls:["地址$线路名"] 两种写法
                ArrayList<String> sourceUrls = new ArrayList<>();
                ArrayList<String> sourceNames = new ArrayList<>();
                ArrayList<String> sourceDecodes = new ArrayList<>();
                ArrayList<String> parallelDecodes = parseDecodeList(obj.get("decodes"));
                int sourceIndex = 0;
                JsonElement urlsEle = obj.get("urls");
                if (urlsEle != null && urlsEle.isJsonArray()) {
                    for (JsonElement urlElement : urlsEle.getAsJsonArray()) {
                        String rawUrl = "";
                        String lineName = "";
                        String lineDecode = channelDecode;
                        if (urlElement.isJsonPrimitive()) {
                            rawUrl = urlElement.getAsString().trim();
                        } else if (urlElement.isJsonObject()) {
                            JsonObject urlObj = urlElement.getAsJsonObject();
                            rawUrl = optString(urlObj, "url");
                            lineName = optString(urlObj, "name");
                            String apiDecode = normalizeDecode(urlObj, "", "");
                            if (!apiDecode.isEmpty()) lineDecode = apiDecode;
                        } else {
                            continue;
                        }
                        String[] splitText = rawUrl.split("\\$", 2);
                        rawUrl = splitText[0].trim();
                        if (lineName.isEmpty() && splitText.length > 1) lineName = splitText[1].trim();
                        if (sourceIndex < parallelDecodes.size() && !parallelDecodes.get(sourceIndex).isEmpty())
                            lineDecode = parallelDecodes.get(sourceIndex);
                        if (rawUrl.isEmpty()) continue;
                        sourceUrls.add(rawUrl);
                        if (lineName.isEmpty())
                            sourceNames.add("源" + (sourceIndex + 1));
                        else
                            sourceNames.add(lineName);
                        sourceDecodes.add(lineDecode == null ? "" : lineDecode);
                        sourceIndex++;
                    }
                } else {
                    // 兼容单链接写法 {"url":"..."}
                    String rawUrl = optString(obj, "url");
                    String[] splitText = rawUrl.split("\\$", 2);
                    rawUrl = splitText[0].trim();
                    if (!rawUrl.isEmpty()) {
                        sourceUrls.add(rawUrl);
                        sourceNames.add(splitText.length > 1 && !splitText[1].trim().isEmpty()
                                ? splitText[1].trim() : "源1");
                        sourceDecodes.add(channelDecode);
                    }
                }
                if (sourceUrls.isEmpty()) continue;
                liveChannelItem.setChannelSourceNames(sourceNames);
                liveChannelItem.setChannelUrls(sourceUrls);
                liveChannelItem.setChannelDecodes(sourceDecodes);
                liveChannelGroup.getLiveChannels().add(liveChannelItem);
            }
            liveChannelGroupList.add(liveChannelGroup);
        }
    }

    /** 读取字符串字段（不存在/非字符串返回空串） */
    private static String optString(JsonObject obj, String key) {
        if (obj == null) return "";
        JsonElement ele = obj.get(key);
        return ele != null && ele.isJsonPrimitive() ? ele.getAsString().trim() : "";
    }

    /**
     * 统一解析解码方式：支持 "decode":"soft/hard/system/exo/ali/auto"，
     * 也兼容 "playerType":-1/0/1/2/3（-1=跟随全局，0=系统，1=Ijk，2=Exo，3=Ali）。
     */
    private static String normalizeDecode(JsonObject obj, String decodeKey, String playerTypeKey) {
        if (obj == null) return "";
        String decode = decodeKey == null || decodeKey.isEmpty() ? optString(obj, "decode") : optString(obj, decodeKey);
        if (decode.isEmpty()) decode = optString(obj, "codec");
        if (!decode.isEmpty()) return normalizeDecodeToken(decode);
        JsonElement typeEle = obj.get(playerTypeKey == null || playerTypeKey.isEmpty() ? "playerType" : playerTypeKey);
        if (typeEle != null && typeEle.isJsonPrimitive()) {
            int playerType = -1;
            try {
                playerType = typeEle.getAsInt();
            } catch (Throwable ignored) {
                return "";
            }
            switch (playerType) {
                case 0:
                    return "system";
                case 1:
                    return "soft";
                case 2:
                    return "exo";
                case 3:
                    return "ali";
                default:
                    return "";
            }
        }
        return "";
    }

    /** 解码方式别名统一为英文标记，未知值原样保留（由播放器侧忽略） */
    private static String normalizeDecodeToken(String decode) {
        String token = decode == null ? "" : decode.trim().toLowerCase();
        if (token.isEmpty()) return "";
        switch (token) {
            case "auto":
            case "default":
            case "follow":
            case "跟随":
                return "auto";
            case "system":
            case "sys":
            case "系统":
            case "0":
                return "system";
            case "soft":
            case "sw":
            case "ijk":
            case "ijk-soft":
            case "软解":
            case "软解码":
            case "1":
                return "soft";
            case "hard":
            case "hw":
            case "ijk-hard":
            case "硬解":
            case "硬解码":
                return "hard";
            case "exo":
            case "2":
                return "exo";
            case "ali":
            case "aliplayer":
            case "3":
                return "ali";
            default:
                return token;
        }
    }

    /** 解析 urls 的平行解码数组 decodes（元素可为字符串或数字） */
    private static ArrayList<String> parseDecodeList(JsonElement ele) {
        ArrayList<String> list = new ArrayList<>();
        if (ele == null || !ele.isJsonArray()) return list;
        for (JsonElement item : ele.getAsJsonArray()) {
            if (item != null && item.isJsonPrimitive()) {
                list.add(normalizeDecodeToken(item.getAsString()));
            } else {
                list.add("");
            }
        }
        return list;
    }

    public String getSpider() {
        return spider;
    }

    public Spider getCSP(SourceBean sourceBean) {
        if (sourceBean.getApi().endsWith(".js") || sourceBean.getApi().contains(".js?")){
            return jsLoader.getSpider(sourceBean.getKey(), sourceBean.getApi(), sourceBean.getExt(), sourceBean.getJar());
        }else if (sourceBean.getApi().contains(".py")) {
            return pyLoader.getSpider(sourceBean.getKey(), sourceBean.getApi(), sourceBean.getExt());
        } else {
            return jarLoader.getSpider(sourceBean.getKey(), sourceBean.getApi(), sourceBean.getExt(), sourceBean.getJar());
        }
    }

    /** 点播接口配置是否已在本进程解析成功（未加载时点播界面先加载接口再拉数据） */
    public boolean isConfigLoaded() {
        return loaded;
    }

    /**
     * 切源/进入点播前预加载该源对应的插件（jar / js / py），并丢弃该源旧的 Spider 实例。
     *
     * <p>背景：点播接口里的站点可以来自不同的 jar/js/py 插件，主界面已不再统一加载点播配置，
     * 如果等 getSort 时再惰性加载，首次切换会同步下载/初始化插件（几秒甚至超时），表现为
     * 列表无数据或切源瞬间闪退。这里在等待界面期间先在后台把插件准备好，再拉分类数据。</p>
     *
     * <p>type=3 的站点：api 以 .js 结尾=QuickJS、含 .py=Python、其余=jar 类名；
     * jar 为空的 csp_ 站点依赖本接口的全局 spider jar，先确保该 jar 已加载。
     * 非 Spider 站点（type=0/1/4 接口）无需加载插件，直接回调。</p>
     *
     * @param sourceBean 目标源（可为 null，直接回调）
     * @param onReady    插件就绪后回主线程回调（调用方在此开始 getSort）
     */
    public void prepareSource(final SourceBean sourceBean, final Runnable onReady) {
        final Runnable done = new Runnable() {
            @Override
            public void run() {
                if (onReady != null) onReady.run();
            }
        };
        if (sourceBean == null) {
            postMain(done);
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (sourceBean.getType() == 3) {
                        String key = sourceBean.getKey();
                        if (key != null) {
                            // 切源=刷新插件：丢弃该源曾创建的 Spider，按最新配置重新实例化
                            jarLoader.remove(key);
                            jsLoader.remove(key);
                            pyLoader.remove(key);
                        }
                        String api = sourceBean.getApi() == null ? "" : sourceBean.getApi().trim();
                        boolean isJs = api.endsWith(".js") || api.contains(".js?");
                        boolean isPy = api.contains(".py");
                        if (!isJs && !isPy) {
                            String jar = sourceBean.getJar() == null ? "" : sourceBean.getJar().trim();
                            if (jar.isEmpty()) {
                                // api 只写 csp_ 类名的站点：依赖接口级全局 spider jar
                                String mainJar = getSpider();
                                if (!mainJar.isEmpty()) {
                                    ensureMainJar(mainJar);
                                }
                            }
                        }
                        // 实际加载插件（jar=下载+DexClassLoader；js=脚本引擎；py=Python 运行时）
                        getCSP(sourceBean);
                    }
                } catch (Throwable th) {
                    // 插件加载失败不阻塞分类请求：后续 getSort 仍会按原逻辑重试/返回空
                    th.printStackTrace();
                }
                postMain(done);
            }
        }, "vod-plugin-prepare").start();
    }

    /** 同步等待接口级 spider jar 加载完成（可在后台线程调用；回调由 loadJar 回投主线程） */
    private boolean ensureMainJar(String jar) {
        final CountDownLatch latch = new CountDownLatch(1);
        try {
            loadJar(true, jar, new LoadConfigCallback() {
                @Override
                public void success() {
                    latch.countDown();
                }

                @Override
                public void retry() {
                    latch.countDown();
                }

                @Override
                public void error(String msg) {
                    latch.countDown();
                }
            });
            return latch.await(20, TimeUnit.SECONDS);
        } catch (Throwable th) {
            th.printStackTrace();
            return false;
        }
    }

    public Spider getPyCSP(String url) {
        return pyLoader.getSpider(MD5.string2MD5(url), url, "");
    }
	
    public Object[] proxyLocal(Map<String,String> param) {
        if ("js".equals(param.get("do"))) {
            return jsLoader.proxyInvoke(param);
        }
        SourceBean sourceBean = ApiConfig.get().getHomeSourceBean(); 
        String apiString = sourceBean.getApi();
        return apiString.contains(".py") ? pyLoader.proxyInvoke(param) : jarLoader.proxyInvoke(param);
    }

    public JSONObject jsonExt(String key, LinkedHashMap<String, String> jxs, String url) {
        return jarLoader.jsonExt(key, jxs, url);
    }

    public JSONObject jsonExtMix(String flag, String key, String name, LinkedHashMap<String, HashMap<String, String>> jxs, String url) {
        return jarLoader.jsonExtMix(flag, key, name, jxs, url);
    }

    public interface LoadConfigCallback {
        void success();

        void retry();

        void error(String msg);
    }

    public interface FastParseCallback {
        void success(boolean parse, String url, Map<String, String> header);

        void fail(int code, String msg);
    }

    public SourceBean getSource(String key) {
        if (!sourceBeanList.containsKey(key))
            return null;
        return sourceBeanList.get(key);
    }

    /** 自定义点播界面注入的源 key 前缀（与主配置隔离，退出界面时整体移除） */
    public static final String CUSTOM_SRC_PREFIX = "cv_";

    /**
     * 自定义点播列表界面专用：解析与主点播接口相同格式的 json，仅取 sites 数组，
     * 全部以 "cv_" 前缀注入源表；其它字段（live/epg/wallpaper/parses 等）一律忽略，
     * 不影响 app 其它配置。返回注入成功的源列表（保持 json 中的顺序）。
     */
    public synchronized List<SourceBean> injectCustomSources(String jsonStr) {
        return injectCustomSources(jsonStr, "");
    }

    /**
     * 带接口地址的自定义点播解析：configUrl 为该数据接口的实际请求地址（http(s)），
     * 用于把配置内的相对地址（spider / api / jar / playUrl / ext 里的 "./"）解析成绝对地址。
     * 这样远程接口（如 /xw/xxx 下发的多源配置）里的 "./py/x.py"、"./js/drpy2.min.js" 等
     * 各类源（type=0 Xml / 1 Json / 3 Spider / 4 Json 扩展）都能在独立点播界面正常切换。
     */
    public synchronized List<SourceBean> injectCustomSources(String jsonStr, String configUrl) {
        removeCustomSources();
        List<SourceBean> out = new ArrayList<>();
        String base = configBase(configUrl);
        try {
            JsonObject infoJson = new Gson().fromJson(jsonStr, JsonObject.class);
            JsonArray sites = infoJson.has("video")
                    ? infoJson.getAsJsonObject("video").getAsJsonArray("sites")
                    : infoJson.get("sites").getAsJsonArray();
            // 本接口自己的全局 Spider jar（相对地址按接口所在目录解析）
            String spider = resolvePathWithMd5(base, DefaultConfig.safeJsonString(infoJson, "spider", ""));
            int idx = 0;
            for (JsonElement opt : sites) {
                try {
                    JsonObject obj = (JsonObject) opt;
                    SourceBean sb = new SourceBean();
                    String siteKey = obj.has("key") ? obj.get("key").getAsString().trim() : ("site" + idx);
                    String injectKey = CUSTOM_SRC_PREFIX + (idx++) + "_" + siteKey;
                    sb.setKey(injectKey);
                    sb.setName(obj.has("name") ? obj.get("name").getAsString().trim() : siteKey);
                    // 缺 type/api 的站点按 type=0 / 空串兜底，单个站点解析失败不影响其它站点注入
                    sb.setType(DefaultConfig.safeJsonInt(obj, "type", 0));
                    String api = obj.has("api") && !obj.get("api").isJsonNull() ? obj.get("api").getAsString().trim() : "";
                    sb.setApi(resolvePathWithMd5(base, api));
                    sb.setSearchable(DefaultConfig.safeJsonInt(obj, "searchable", 1));
                    sb.setQuickSearch(DefaultConfig.safeJsonInt(obj, "quickSearch", 1));
                    sb.setFilterable(siteKey.startsWith("py_") ? 1 : DefaultConfig.safeJsonInt(obj, "filterable", 1));
                    sb.setHide(DefaultConfig.safeJsonInt(obj, "hide", 0));
                    sb.setPlayerUrl(resolvePathWithMd5(base, DefaultConfig.safeJsonString(obj, "playUrl", "")));
                    sb.setExt(resolveExtPaths(base, DefaultConfig.safeJsonString(obj, "ext", "")));
                    sb.setJar(resolvePathWithMd5(base, DefaultConfig.safeJsonString(obj, "jar", "")));
                    sb.setPlayerType(DefaultConfig.safeJsonInt(obj, "playerType", -1));
                    sb.setCategories(DefaultConfig.safeJsonStringList(obj, "categories"));
                    sb.setClickSelector(DefaultConfig.safeJsonString(obj, "click", ""));
                    sb.setStyle(DefaultConfig.safeJsonString(obj, "style", ""));
                    // type=3 且 api 只写类名（csp_xxx）的站点：自身没配 jar 时用本接口的 spider，
                    // 避免回落到主配置的全局 jar 而出现类不存在 / 串源
                    if (sb.getType() == 3 && !spider.isEmpty() && sb.getJar().isEmpty()
                            && sb.getApi().startsWith("csp_")) {
                        sb.setJar(spider);
                    }
                    sourceBeanList.put(injectKey, sb);
                    out.add(sb);
                } catch (Throwable perSite) {
                    idx++;
                }
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    /** 数据接口地址的目录基准（去掉 query/fragment；无路径时补 "/"；非 http(s) 返回空） */
    private String configBase(String url) {
        if (url == null) {
            return "";
        }
        String u = url.trim();
        if (u.isEmpty() || (!u.startsWith("http://") && !u.startsWith("https://"))) {
            return "";
        }
        int cut = u.length();
        int q = u.indexOf('?');
        if (q >= 0) cut = Math.min(cut, q);
        int f = u.indexOf('#');
        if (f >= 0) cut = Math.min(cut, f);
        u = u.substring(0, cut);
        int schemeEnd = u.indexOf("://");
        if (u.indexOf('/', schemeEnd + 3) < 0) {
            return u + "/";
        }
        return u.substring(0, u.lastIndexOf('/') + 1);
    }

    /** "./" 相对地址 → 基准目录；"/" 绝对路径 → 协议+域名；其余（完整 URL / clan / assets / file）原样 */
    private String resolvePath(String base, String value) {
        if (value == null) {
            return "";
        }
        String v = value.trim();
        if (base.isEmpty() || v.isEmpty()) {
            return value;
        }
        if (v.startsWith("clan://") || v.startsWith("assets/") || v.startsWith("file://")
                || v.contains("://")) {
            return value;
        }
        if (v.startsWith("./")) {
            return base + v.substring(2);
        }
        if (v.startsWith("/")) {
            int schemeEnd = base.indexOf("://");
            if (schemeEnd < 0) {
                return value;
            }
            return base.substring(0, schemeEnd + 3) + v.substring(1);
        }
        return value;
    }

    /** 兼容 jar/spider 的 ";md5;" 后缀：只解析地址部分，md5 原样保留 */
    private String resolvePathWithMd5(String base, String value) {
        if (value == null) {
            return "";
        }
        int md5 = value.indexOf(";md5;");
        if (md5 >= 0) {
            return resolvePath(base, value.substring(0, md5)) + value.substring(md5);
        }
        return resolvePath(base, value);
    }

    /** ext 可能是路径字符串，也可能是 JSON 对象字符串：只把 "./" 形式的相对路径替换成接口目录 */
    private String resolveExtPaths(String base, String ext) {
        if (base.isEmpty() || ext == null || ext.isEmpty() || !ext.contains("./")) {
            return ext;
        }
        if (ext.startsWith("./")) {
            return resolvePath(base, ext);
        }
        return ext.replace("\"./", "\"" + base);
    }

    /** 移除全部自定义点播界面注入的源（退出自定义点播界面时调用） */
    public synchronized void removeCustomSources() {
        List<String> keys = new ArrayList<>(sourceBeanList.keySet());
        for (String k : keys) {
            if (k != null && k.startsWith(CUSTOM_SRC_PREFIX)) {
                sourceBeanList.remove(k);
            }
        }
    }

    /** 仅切换内存中的当前源（不写 Hawk）：自定义点播界面用，退出后由调用方恢复 */
    public void setHomeSourceTemp(SourceBean sourceBean) {
        this.mHomeSource = sourceBean;
    }

    public void setSourceBean(SourceBean sourceBean) {
        this.mHomeSource = sourceBean;
        Hawk.put(HawkConfig.HOME_API, sourceBean.getKey());
    }

    public void setDefaultParse(ParseBean parseBean) {
        if (this.mDefaultParse != null)
            this.mDefaultParse.setDefault(false);
        this.mDefaultParse = parseBean;
        Hawk.put(HawkConfig.DEFAULT_PARSE, parseBean.getName());
        parseBean.setDefault(true);
    }

    public ParseBean getDefaultParse() {
        return mDefaultParse;
    }

    public List<SourceBean> getSourceBeanList() {
        return new ArrayList<>(sourceBeanList.values());
    }

    public List<ParseBean> getParseBeanList() {
        return parseBeanList;
    }

    public List<String> getVipParseFlags() {
        // 点播接口未加载时（主界面已不再加载点播配置）返回空列表，
        // 避免播放器里 getVipParseFlags().contains(...) 直接空指针闪退
        return vipParseFlags == null ? new ArrayList<String>() : vipParseFlags;
    }

    public SourceBean getHomeSourceBean() {
        return mHomeSource == null ? emptyHome : mHomeSource;
    }

    public List<LiveChannelGroup> getChannelGroupList() {
        return liveChannelGroupList;
    }

    public List<IJKCode> getIjkCodes() {
        return ijkCodes;
    }

    public IJKCode getCurrentIJKCode() {
        String codeName = Hawk.get(HawkConfig.IJK_CODEC, "");
        return getIJKCodec(codeName);
    }

    /**
     * 按名称取 IJK 解码配置；列表为空（理论不会发生，构造时已内置默认）或找不到时回退第一项，
     * 任何情况下都不抛异常 —— 该方法的调用方在播放器初始化关键路径上，崩溃即闪退。
     */
    public IJKCode getIJKCodec(String name) {
        if (ijkCodes == null || ijkCodes.isEmpty()) {
            initDefaultIjkCodes();
        }
        if (name != null) {
            for (IJKCode code : ijkCodes) {
                if (code.getName() != null && code.getName().equals(name))
                    return code;
            }
        }
        return ijkCodes.get(0);
    }

    /**
     * 直播播放自定义请求头；未加载点播配置时为 null，调用方已按 null 容错（见 setPlayHeaders 的 try/catch），
     * 这里返回空数组更稳妥，避免调用方 toString() 空指针。
     */
    public JsonArray getLivePlayHeaders() {
        return livePlayHeaders == null ? new JsonArray() : livePlayHeaders;
    }

    String clanToAddress(String lanLink) {
        if (lanLink.startsWith("clan://localhost/")) {
            return lanLink.replace("clan://localhost/", ControlManager.get().getAddress(true) + "file/");
        } else {
            String link = lanLink.substring(7);
            int end = link.indexOf('/');
            return "http://" + link.substring(0, end) + "/file/" + link.substring(end + 1);
        }
    }

    String clanContentFix(String lanLink, String content) {
        String fix = lanLink.substring(0, lanLink.indexOf("/file/") + 6);
        return content.replace("clan://", fix);
    }

    String fixContentPath(String url, String content) {
        if (content.contains("\"./")) {
            url=url.replace("file://","clan://localhost/");
            if (!url.startsWith("http") && !url.startsWith("clan://")) {
                url = "http://" + url;
            }
            if (url.startsWith("clan://")) url = clanToAddress(url);
            content = content.replace("./", url.substring(0, url.lastIndexOf("/") + 1));
        }
        return content;
    }

    public void clearJarLoader()
    {
        jarLoader.clear();
    }
    private void addSuperParse(){
        ParseBean superPb = new ParseBean();
        superPb.setName("超级解析");
        superPb.setUrl("SuperParse");
        superPb.setExt("");
        superPb.setType(4);
        parseBeanList.add(0, superPb);
    }
    public void clearLoader(){
        jarLoader.clear();
        pyLoader.clear();
        jsLoader.clear();
    }
    String miTV(String url) {
        if (url.startsWith("p") || url.startsWith("mitv")) {

        }
        return url;
    }

}
