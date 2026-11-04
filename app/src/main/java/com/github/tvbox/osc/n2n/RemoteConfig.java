package com.github.tvbox.osc.n2n;

import android.app.Activity;
import android.content.Context;

import com.github.tvbox.osc.base.App;
import com.github.tvbox.osc.bean.Movie;
import com.github.tvbox.osc.util.HawkConfig;
import com.orhanobut.hawk.Hawk;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.List;

/**
 * 统一配置中心：TVBox 的全部功能设置均来自 JSON（远程或本地），应用内不再提供设置页。
 *
 * 配置来源：由 {@link #CONFIG_URL} 在代码中手动指定，支持
 * {@code http(s)://} 远程地址、设备本地文件路径、{@code assets/xxx.json}；
 * 留空则全部使用代码内置默认值。APK 内不打包任何默认配置 json。
 *
 * 主配置内的 {@code remoteConfigUrl} 可作为二次远程覆盖（可选）。
 * 接口字段（api/live/epg/proxy/upgrade/appMarket 等）若未写 http(s)://，
 * 会自动拼接主接口（api）的协议 + 域名/IP，详见 {@link #resolveInterfaceUrl(String)}。
 * 所有接口请求均支持 ke 前缀加密（{@link ConfigCrypto}），并自动携带本机参数。
 *
 * JSON 结构（所有字段可选，缺省保持本地现状；支持 // 与 /* 注释、尾逗号）：
 * {
 *   "remoteConfigUrl": "https://.../config.json",     // 二次远程覆盖（可选，空=不启用）
 *   "useRemoteConfig": false,                         // true 且远程可访问时：远程覆盖设置项/
 *                                                     //   bootStart/api/live/epg/proxy
 *   "bootStart": true,                                // TVBox 开机自启：默认开启
 *   "notice": {                                       // 公告：字符串（旧格式）或对象
 *     "text": "公告文本",
 *     "position": "bottom",                           // top | bottom
 *     "bgColor": "#CC1B1B1B", "fontColor": "#FFFFD54F",
 *     "fontSize": 18, "alpha": 100, "duration": 15,
 *     "show": { "home": true, "live": true, "vod": true },
 *     "timeStart": "08:00", "timeEnd": "22:00", "expire": "2027-12-31",
 *     "speed": 60
 *   },
 *   "home": { "bgImage": "...", "sourceName": "...", "entryBgColor": "...", "iconColor": "...",
 *             "textColor": "...", "alpha": 90, "inputBgColor": "...", "buttonBgColor": "...",
 *             "recMovies": [                       // 首页「豆瓣热播」替换为自定义影片（可选）
 *               {"name":"片名","pic":"封面图","note":"备注","id":"影片ID","sourceKey":"站点key"}
 *             ] },
 *             // sourceName=首页左上角默认文字（用户未配置点播接口时显示）
 *             // recMovies 存在且非空时：首页推荐列表不再请求豆瓣，直接显示这里配置的影片；
 *             //   配了 id+sourceKey 点击直接进详情，只配 name 则点击按片名去搜索
 *   "loadSuccessDuration": 3,                         // 点播接口加载成功通知：多少秒后自动关闭
 *                                                     //   （0=不显示该通知；默认 3；范围 0~60）
 *   "warningAutoClose": 0,                            // 点播接口免责声明（warningText）弹窗：
 *                                                     //   多少秒后自动关闭（默认 0=必须手动点确定）
 *   "tkp2p": { "engine": "auto|hls|media", "announce": [...], "token": "",
 *              "zone": "cn", "iceServers": [...] },   // iceServers 也可简写 ice
 *   "checkInterval": 30,                              // 配置检查间隔（分钟，本地模式下列出兼容）
 *   "ua": "Mozilla/5.0 ...",                          // 全局 UA
 *   "epgDays": 3,                                     // 直播 EPG 节目表显示天数（含今天，1~9）
 *   "aliLicenseKey": "",                              // 阿里云播放器 SDK License Key
 *                                                     //   （5.5+ 不配时 SDK 弹「配置信息初始化失败」提示）
 *   "bootStart": true,                                // TVBox 开机自启（用户手动改过后不覆盖）
 *   "api": "...", "live": "...", "epg": "...", "proxy": "...",
 *   "theme": "violet",                                // 主题：索引 / 中文名 / 英文别名
 *   "themes": [ {"name":"红色","alias":"red","style":0,"color":"#D81F26",
 *                "dark":"#8C1418","bgStart":"#3D0A0D","bgEnd":"#D81F26",
 *                "bgImage":"url" 或 "bgImages":["url1","url2"]} ],
 *   "upgrade": {                                      // TVBox APK 自检升级（可被酒店桌面定制使用）
 *     "checkUrl": "https://.../version.json",         // 远程版本信息地址（优先），或直接内联下列字段
 *     "versionCode": 2, "versionName": "1.0.2", "url": "https://.../x.apk",
 *     "force": false, "notes": "更新内容" },
 *   "appMarket": {                                    // 「应用市场」联网配置（「应用」页面）
 *     // url 模板支持 {category}(all/app/game)、{sort}(recommend/download/update)、
 *     // {page}(从 1 开始)、{pageSize}、{keyword}；为空=仅使用下方 apps 数组
 *     "url": "https://.../apps?category={category}&sort={sort}&page={page}",
 *     "detailUrl": "https://.../app/detail?packageName={packageName}",  // 可选；空=直接用列表字段
 *     "headers": { "X-Token": "..." },                // 可选请求头
 *     "pageSize": 24,
 *     "apps": [ {"packageName":"com.x","name":"示例","versionName":"1.0",
 *                "versionCode":10,"icon":"https://.../i.png","size":"12MB",
 *                "downloads":1000,"category":"app","summary":"...",
 *                "description":"...","screenshots":["https://.../s1.jpg"],
 *                "downloadUrl":"https://.../x.apk","developer":"...",
 *                "updateTime":"2026-01-01"} ]        // 直接下发的应用数组（可选）
 *   },
 *   "hin2nEntry": true,                               // 首页是否显示 hin2n 固定入口
 *   "hin2n": { "apkUrl": "https://.../hin2n.apk" },   // 独立组网 App：未安装时的 APK 下载地址（可选）
 *   "secretKey": "",                                  // 接口固定密钥模式的秘钥覆盖（空=用 ConfigCrypto.SECRET_KEY）
 *   "donate": { "title": "支持我们", "desc": "...",    // 捐助界面：标题/介绍/二维码图片/底部说明
 *               "image": "https://.../qr.png",        //   image 支持 http(s) / assets/路径 / drawable 名
 *               "tip": "扫码后请备注..." },            //   tip=二维码下方说明文字（可选）
 *   "entries": [ {"name":"文件管理","pkg":"...","icon":"...","url":"..."} ],
 *   "topEntries": [                               // 顶栏整体替换（缺省=内置默认顶栏）
 *     {"name":"搜索","type":"page","target":"search","icon":"hm_search"},
 *     {"name":"切源","type":"feature","target":"siteSwitch","icon":"hm_drawer"},
 *     {"name":"文件","type":"app","pkg":"com.x","icon":"","url":"http://.../a.apk"},
 *     {"name":"影院","type":"vod","target":"assets/vod.json","icon":"hm_vod","theme":"1"} ],
 *   "homeEntries": [ ...同 topEntries... ]        // 历史一行整体替换
 * }
 *
 * 入口类型：page=打开 TVBox 界面（target: vod/live/search/history/collect/push/
 * drive/hin2n/apps/fastsearch/system=安卓系统设置/donate=捐助界面；应用内已无设置页）；
 * feature=内置功能（target: siteSwitch/themeSwitch/styleSwitch/clearCache/reloadHome/
 * wifi/date）；app=打开其他 App（pkg，未安装且配 url 时后台下载安装）；vod=打开自定义
 * 点播列表界面（target=数据接口，格式同点播 api json 但仅供该界面，不影响 app 其它配置）。
 *
 * 接口加密（ConfigCrypto）：所有远程接收接口（远程配置/点播/直播/EPG/自定义点播/
 * 应用市场/升级检查）支持密文下发——ke 前缀模式兼容 PHP：ke=substr(md5(...),13)
 * 前缀 + AES-128-ECB hex；固定密钥模式用 "secretKey" 或 ConfigCrypto.SECRET_KEY。
 * 接口本机参数（DeviceParams）：所有 OkGo 请求统一携带请求头 X-Device-Mac /
 * X-Device-Ip / X-OS-Version / X-App-Version；自有接口额外在 URL 拼接
 * mac/ip/model/brand/device/sdk/release/app 参数，服务端可直接取用。
 *
 * 覆盖规则：除 bootStart（是否开机自启）外，所有配置每次加载都以 JSON 为准；
 * 设置页已移除，不存在用户手动值冲突。
 */
public class RemoteConfig {

    /** Hawk 键：缓存与开关（K_URL / hin2nUrl / 远程更新相关键已随远程功能移除） */
    public static final String K_NOTICE = "rc_notice";
    public static final String K_NOTICE_STYLE = "rc_notice_style";
    public static final String K_HOME_STYLE = "rc_home_style";
    public static final String K_TKP2P = "rc_tkp2p";
    public static final String K_CHECK_INTERVAL = "rc_check_interval";
    public static final String K_UA = "rc_ua";
    public static final String K_ENTRIES = "rc_entries";
    public static final String K_TOP_ENTRIES = "rc_top_entries";
    public static final String K_HOME_ENTRIES = "rc_home_entries";
    /** 点播接口加载成功通知的自动关闭秒数（json "loadSuccessDuration"，0=不显示） */
    public static final String K_LOAD_SUCCESS_DURATION = "rc_load_success_duration";
    /** 点播接口免责声明（warningText）弹窗的自动关闭秒数（json "warningAutoClose"，0=不自动关闭） */
    public static final String K_WARNING_AUTO_CLOSE = "rc_warning_auto_close";
    public static final String K_UPGRADE = "rc_upgrade";
    public static final String K_HIN2N = "rc_hin2n_entry";
    public static final String K_BOOT = "n2n_boot_start";      // TVBox 开机自启开关（用户值优先）
    public static final String K_BOOT_DEFAULT_SET = "n2n_boot_default_set"; // 内置默认值是否已写入过
    /** 接口固定密钥模式的秘钥覆盖值（json "secretKey"；空=用 ConfigCrypto.SECRET_KEY） */
    public static final String K_SECRET = "rc_secret_key";
    /** 捐助界面配置（json "donate" 段原文） */
    public static final String K_DONATE = "rc_donate";
    /** 直播 EPG 节目表显示天数（json "epgDays"，含今天；默认 3 = 昨天/今天/明天） */
    public static final String K_EPG_DAYS = "rc_epg_days";
    /** 阿里云播放器 SDK License Key（json "aliLicenseKey"；5.5+ 版本不配时 SDK 会弹「配置信息初始化失败」） */
    public static final String K_ALI_LICENSE = "rc_ali_license";
    /** 应用市场配置（json "appMarket" 段原文；列表/详情接口模板 + 请求头 + 内置兜底 apps） */
    public static final String K_APP_MARKET = "rc_app_market";
    /** 最近一次成功拉取的远程配置原文（断网/下次启动兜底；存解密后的明文） */
    public static final String K_REMOTE_RAW = "rc_remote_raw";

    /** 最近一次加载的主配置 JSON（入口/主题/hin2n 播种等逻辑读取） */
    private static volatile JSONObject sConfig = null;

    /** 单个自定义入口 */
    public static class Entry {
        public String name;
        public String type;    // page=打开 TVBox 界面 | feature=内置功能 | app=打开其他 App | vod=自定义点播界面
        public String target;  // page: vod/live/search/history/collect/push/setting/drive/hin2n/apps/fastsearch
                               // feature: siteSwitch/themeSwitch/styleSwitch/clearCache/reloadHome/wifi/date
                               // vod: 数据接口地址（http(s):// 或 assets/xxx.json），格式同点播 api json 但仅供新界面
        public String pkg;     // app: 包名
        public String icon;    // 图标：http(s) URL 或内置 drawable 名（如 hm_search），空=兜底图标
        public String url;     // app 未安装时的 APK 下载地址（可选）
        public String theme;   // vod: 自定义点播界面独立主题（主题索引/中文名/英文别名），空=跟随全局主题
    }

    public interface Callback {
        void onDone(boolean success);
    }

    /** 最近一次加载的内置配置原始 JSON（无则空对象，不为 null） */
    public static JSONObject raw() {
        return sConfig != null ? sConfig : new JSONObject();
    }

    public static String notice() {
        return Hawk.get(K_NOTICE, "");
    }

    public static JSONObject noticeStyle() {
        try {
            return new JSONObject(Hawk.get(K_NOTICE_STYLE, "{}"));
        } catch (Throwable t) {
            return new JSONObject();
        }
    }

    public static JSONObject homeStyle() {
        try {
            return new JSONObject(Hawk.get(K_HOME_STYLE, "{}"));
        } catch (Throwable t) {
            return new JSONObject();
        }
    }

    /**
     * 首页左上角源名按钮的默认显示文字（home 段 "sourceName" 字段）。
     * 仅当用户尚未配置点播接口（api_url 为空）或当前源未就绪时作为兜底显示；
     * 用户配置过点播接口后，左上角显示当前源名称。
     */
    public static String homeSourceName() {
        String sn = homeStyle().optString("sourceName", "");
        return sn == null ? "" : sn.trim();
    }

    /**
     * 首页自定义推荐影片（home 段的 "recMovies" 数组）。
     *
     * <p>作用：把首页默认的「豆瓣热播」替换为运营方自己配置的影片，避免依赖豆瓣接口
     * （弱网/豆瓣风控时列表为空）。配置后首页不再发起豆瓣请求。</p>
     *
     * <p>数组元素字段（均可选，name 必填才有效）：</p>
     * <ul>
     *   <li>name（兼容 title）：影片名；点击时未配 id 则按片名跳搜索</li>
     *   <li>pic（兼容 cover）：封面图地址，空=占位图</li>
     *   <li>note（兼容 rate）：右下角角标文字（评分/更新说明等）</li>
     *   <li>id：站点内影片 ID，配合 sourceKey 点击直接进详情</li>
     *   <li>sourceKey：点播接口中的站点 key，缺省时使用当前首页站点</li>
     * </ul>
     *
     * @return 非 null 列表；未配置或格式错误时返回空列表（调用方回退豆瓣）
     */
    public static List<Movie.Video> homeRecMovies() {
        List<Movie.Video> list = new ArrayList<>();
        try {
            JSONArray arr = homeStyle().optJSONArray("recMovies");
            if (arr == null || arr.length() == 0) {
                return list;
            }
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) {
                    continue;
                }
                String name = o.optString("name", o.optString("title", "")).trim();
                if (name.isEmpty()) {
                    continue;
                }
                Movie.Video vod = new Movie.Video();
                vod.name = name;
                vod.pic = o.optString("pic", o.optString("cover", "")).trim();
                vod.note = o.optString("note", o.optString("rate", "")).trim();
                vod.id = o.optString("id", "").trim();
                vod.sourceKey = o.optString("sourceKey", "").trim();
                list.add(vod);
            }
        } catch (Throwable ignored) {
            // 配置格式异常时按「未配置」处理，首页仍走豆瓣，不影响启动
        }
        return list;
    }

    /**
     * 点播接口加载成功通知的自动关闭秒数（顶层 "loadSuccessDuration"）。
     * 0=不显示该通知；默认 3 秒；上限 60 秒，防止配置过大导致提示长期遮挡界面。
     */
    public static int loadSuccessDuration() {
        int s = Hawk.get(K_LOAD_SUCCESS_DURATION, 3);
        if (s < 0) {
            s = 0;
        }
        if (s > 60) {
            s = 60;
        }
        return s;
    }

    /**
     * 点播接口免责声明（warningText）弹窗的自动关闭秒数（顶层 "warningAutoClose"）。
     *
     * <p>0=保持原行为（不自动关闭，必须点「确定」）；大于 0 时弹窗按钮显示倒计时，
     * 到点自动关闭，避免无人值守的电视/机顶盒被免责声明弹窗长期挡住。</p>
     *
     * @return 0~120 秒，默认 0
     */
    public static int warningAutoClose() {
        int s = Hawk.get(K_WARNING_AUTO_CLOSE, 0);
        if (s < 0) {
            s = 0;
        }
        if (s > 120) {
            s = 120;
        }
        return s;
    }

    public static JSONObject tkP2pStyle() {
        try {
            return new JSONObject(Hawk.get(K_TKP2P, "{}"));
        } catch (Throwable t) {
            return new JSONObject();
        }
    }

    public static int checkIntervalMinutes() {
        return Math.max(1, Hawk.get(K_CHECK_INTERVAL, 30));
    }

    public static String ua() {
        String u = Hawk.get(K_UA, "");
        return u == null ? "" : u.trim();
    }

    public static boolean hin2nEntryEnabled() {
        return Hawk.get(K_HIN2N, true);
    }

    /** 接口固定密钥模式的秘钥覆盖（json "secretKey"；空=用 ConfigCrypto.SECRET_KEY 常量） */
    public static String secretKey() {
        String k = Hawk.get(K_SECRET, "");
        return k == null ? "" : k.trim();
    }

    /** 捐助界面配置（json "donate" 段：{"title":"","desc":"","image":"","tip":""}），未配置返回 null */
    public static JSONObject donate() {
        String raw = Hawk.get(K_DONATE, "");
        if (raw.isEmpty()) {
            return null;
        }
        try {
            return new JSONObject(raw);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * 直播 EPG 节目表显示的总天数（json "epgDays"，含今天）。
     * 默认 3 天 = 昨天/今天/明天；范围限制在 1~9 天，防止配置过大导致请求过多。
     * 日期列表以今天为中心：前天数 = (days-1)/2，其余为今天及未来天数。
     */
    public static int epgDays() {
        int d = Hawk.get(K_EPG_DAYS, 3);
        if (d < 1) d = 1;
        if (d > 9) d = 9;
        return d;
    }

    /** 阿里云播放器 SDK License Key（json "aliLicenseKey"；空=不设置，SDK 会提示配置信息初始化失败但不影响播放） */
    public static String aliLicenseKey() {
        String k = Hawk.get(K_ALI_LICENSE, "");
        return k == null ? "" : k.trim();
    }

    /**
     * 独立 hin2n 组网 App 的 APK 下载地址（内置 JSON "hin2n" 段的 "apkUrl" 字段）。
     * 组网入口点击时：已安装直接拉起；未安装且此处非空时走后台下载安装管线。
     */
    public static String hin2nApkUrl() {
        try {
            JSONObject h = raw().optJSONObject("hin2n");
            return h == null ? "" : h.optString("apkUrl", "").trim();
        } catch (Throwable t) {
            return "";
        }
    }

    public static boolean bootStartEnabled() {
        // 默认开启：随开机自动启动 TVBox；JSON（内置或远程）显式配置 "bootStart": false 时禁用
        return Hawk.get(K_BOOT, true);
    }

    /** 是否使用远程配置覆盖本地设置（内置 JSON "useRemoteConfig" 字段，远程可访问时才生效） */
    public static boolean useRemoteConfig() {
        try {
            return sConfig != null && sConfig.optBoolean("useRemoteConfig", false);
        } catch (Throwable t) {
            return false;
        }
    }

    /** 读取已缓存的自定义入口列表（旧版 "entries"，仅 app 类型，顶栏兜底） */
    public static List<Entry> entries() {
        return parseEntries(Hawk.get(K_ENTRIES, ""));
    }

    /**
     * 顶栏入口配置（"topEntries"）：返回 null 表示未配置（保持内置默认顶栏）；
     * 非 null（可为空列表）表示整体替换顶栏图标，顺序/内容/图标全部按 JSON。
     */
    public static List<Entry> topEntries() {
        String raw = Hawk.get(K_TOP_ENTRIES, null);
        return raw == null ? null : parseEntries(raw);
    }

    /** 首页历史一行入口配置（"homeEntries"）：null=保持内置默认行；非 null=整体替换 */
    public static List<Entry> homeEntries() {
        String raw = Hawk.get(K_HOME_ENTRIES, null);
        return raw == null ? null : parseEntries(raw);
    }

    /** 远程升级配置（"upgrade" 段）：未配置返回 null */
    public static JSONObject upgrade() {
        String raw = Hawk.get(K_UPGRADE, "");
        if (raw.isEmpty()) {
            return null;
        }
        try {
            return new JSONObject(raw);
        } catch (Throwable t) {
            return null;
        }
    }

    /** 应用市场配置（"appMarket" 段）：未配置返回空对象，调用方无需判空 */
    public static JSONObject appMarket() {
        String raw = Hawk.get(K_APP_MARKET, "");
        if (raw.isEmpty()) {
            return new JSONObject();
        }
        try {
            return new JSONObject(raw);
        } catch (Throwable t) {
            return new JSONObject();
        }
    }

    private static List<Entry> parseEntries(String raw) {
        List<Entry> list = new ArrayList<>();
        if (raw == null || raw.isEmpty()) {
            return list;
        }
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) {
                    continue;
                }
                Entry e = new Entry();
                e.name = o.optString("name", "");
                e.type = o.optString("type", "").trim();
                e.target = o.optString("target", "").trim();
                e.pkg = o.optString("pkg", "");
                e.icon = o.optString("icon", "");
                e.url = o.optString("url", "");
                e.theme = o.optString("theme", "").trim();
                if (e.type.isEmpty()) {
                    // 兼容旧格式：有 pkg 即 app 入口
                    e.type = e.pkg.isEmpty() ? "page" : "app";
                }
                if (e.name.isEmpty()) {
                    e.name = e.pkg;
                }
                list.add(e);
            }
        } catch (Throwable ignored) {
        }
        return list;
    }

    /**
     * 主配置地址：由开发者在代码中手动指定（APK 内不再打包任何配置 json）。
     *
     * <p>支持三种写法：</p>
     * <ul>
     *   <li>远程：{@code http://...} / {@code https://...}（推荐，可热更新）</li>
     *   <li>设备本地文件：绝对路径，如 {@code /sdcard/tvbox-config.json}，或 {@code file:///...}（需存储读写权限）</li>
     *   <li>APK 内资源：{@code assets/xxx.json}（如需随包携带配置时使用）</li>
     * </ul>
     *
     * <p>留空 = 不加载任何配置，全部使用代码内置默认值。</p>
     *
     * <p>主配置内部的 {@code remoteConfigUrl} 仍可作为「二次远程覆盖」使用；
     * 主配置为远程时，建议直接留空该字段，避免重复请求。</p>
     */
    public static final String CONFIG_URL = "";

    /**
     * App 启动时调用：按 {@link #CONFIG_URL} 加载主配置并应用。
     * 远程主配置在后台线程拉取（不阻塞启动），本地/资源主配置同步读取。
     */
    public static void bootstrap(Context ctx) {
        if (ctx == null) {
            return;
        }
        final String src = CONFIG_URL == null ? "" : CONFIG_URL.trim();
        if (src.isEmpty()) {
            // 未配置：完全使用代码默认值（无需任何本地 json）
            return;
        }
        if (src.startsWith("http://") || src.startsWith("https://")) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    fetchPrimary(appSingleton(), src);
                }
            }, "rc-primary-fetch").start();
            return;
        }
        loadLocalPrimary(ctx.getApplicationContext(), src);
    }

    /** 避免在匿名内部类里直接持有 Activity Context：统一取全局 Application */
    private static Context appSingleton() {
        try {
            return App.getInstance().getApplicationContext();
        } catch (Throwable t) {
            return null;
        }
    }

    /** 同步读取本地/资源主配置并应用 */
    private static void loadLocalPrimary(Context app, String src) {
        try {
            String raw = readLocalConfig(app, src);
            if (raw == null || raw.isEmpty()) {
                return;
            }
            raw = ConfigCrypto.maybeDecrypt(raw);
            JSONObject cfg = parseLoose(raw);
            sConfig = cfg;
            apply(app, cfg, false);
            String secondary = cfg.optString("remoteConfigUrl", "").trim();
            if (!secondary.isEmpty()) {
                fetchRemoteAsync(app, resolveInterfaceUrl(secondary));
            }
        } catch (Throwable ignored) {
        }
    }

    /** 读取本地文件或 assets 资源 */
    private static String readLocalConfig(Context app, String src) {
        try {
            if (src.startsWith("assets/")) {
                byte[] data = readAsset(app, src.substring("assets/".length()));
                return data == null ? null : new String(data, "UTF-8");
            }
            String path = src;
            if (path.startsWith("file://")) {
                path = path.substring("file://".length());
            }
            java.io.File f = new java.io.File(path);
            if (!f.exists()) {
                // 兼容只写文件名的场景：到应用私有目录找
                f = new java.io.File(app.getFilesDir(), path);
            }
            if (!f.exists()) {
                return null;
            }
            java.io.FileInputStream in = new java.io.FileInputStream(f);
            try {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int n;
                while ((n = in.read(buf)) > 0) {
                    bos.write(buf, 0, n);
                }
                return new String(bos.toByteArray(), "UTF-8");
            } finally {
                try { in.close(); } catch (Throwable ignored) {}
            }
        } catch (Throwable t) {
            return null;
        }
    }

    /** 后台线程拉取主远程配置 */
    private static void fetchPrimary(Context app, String url) {
        try {
            String raw = httpGet(DeviceParams.append(url), RemoteConfig.ua());
            if (raw == null || raw.isEmpty()) {
                restoreCachedRemote(app, false);
                return;
            }
            raw = ConfigCrypto.maybeDecrypt(raw);
            JSONObject cfg = parseLoose(raw);
            sConfig = cfg;
            apply(app, cfg, false);
            Hawk.put(K_REMOTE_RAW, raw);
            String secondary = cfg.optString("remoteConfigUrl", "").trim();
            if (!secondary.isEmpty()) {
                fetchRemoteAsync(app, resolveInterfaceUrl(secondary));
            }
        } catch (Throwable t) {
            restoreCachedRemote(app, false);
        }
    }

    /** 断网时用上次缓存的主配置兜底 */
    private static void restoreCachedRemote(Context app, boolean remoteOnly) {
        try {
            String cached = Hawk.get(K_REMOTE_RAW, "");
            if (cached != null && !cached.isEmpty()) {
                JSONObject cfg = parseLoose(cached);
                if (remoteOnly == false) {
                    sConfig = cfg;
                }
                apply(app, cfg, remoteOnly);
            }
        } catch (Throwable ignored) {
        }
    }

    /** 后台线程拉取远程 json 并应用（非 settings/hin2n 段） */
    private static void fetchRemoteAsync(final Context app, final String url) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                fetchAndApply(app, url, null);
            }
        }, "rc-remote-fetch").start();
    }

    /** 同步拉取远程 json 并应用；cb 可为空 */
    private static boolean fetchAndApply(Context app, String url, Callback cb) {
        try {
            // 请求附加本机参数（mac/ip/机型/系统版本等），服务端可按机下发
            String raw = httpGet(DeviceParams.append(url), RemoteConfig.ua());
            if (raw == null || raw.isEmpty()) {
                if (cb != null) cb.onDone(false);
                return false;
            }
            // 是 json 直接解析，不是 json 先解密后加载（ke 前缀/固定密钥两种模式自动识别）
            raw = ConfigCrypto.maybeDecrypt(raw);
            JSONObject remote = parseLoose(raw);
            apply(app, remote, true);
            // 缓存存解密后的明文：断网兜底无需再解密，与旧版本明文缓存格式兼容
            Hawk.put(K_REMOTE_RAW, raw);
            if (cb != null) cb.onDone(true);
            return true;
        } catch (Throwable t) {
            // 网络失败/解析失败：用上次缓存的远程配置兜底（若有）
            try {
                String cached = Hawk.get(K_REMOTE_RAW, "");
                if (cached != null && !cached.isEmpty()) {
                    JSONObject remote = parseLoose(cached);
                    apply(app, remote, true);
                }
            } catch (Throwable ignored) {
            }
            if (cb != null) cb.onDone(false);
            return false;
        }
    }

    /** 简单 HttpURLConnection GET（带 UA，超时 8s） */
    private static String httpGet(String urlStr, String ua) {
        HttpURLConnection conn = null;
        InputStream in = null;
        try {
            java.net.URL url = new java.net.URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setRequestProperty("User-Agent", ua == null || ua.isEmpty()
                    ? "Mozilla/5.0 (Linux; Android) TVBox" : ua);
            conn.setRequestProperty("Accept", "application/json,*/*");
            int code = conn.getResponseCode();
            in = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
            if (in == null) return null;
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) {
                bos.write(buf, 0, n);
            }
            return new String(bos.toByteArray(), "UTF-8");
        } catch (Throwable t) {
            return null;
        } finally {
            if (in != null) { try { in.close(); } catch (Throwable ignored) {} }
            if (conn != null) { conn.disconnect(); }
        }
    }

    /**
     * 远程拉取入口：从内置配置的 remoteConfigUrl 拉取并应用（非 settings/hin2n 段）。
     * 未配置 remoteConfigUrl 时直接回调成功（保持内置）。
     */
    public static void fetchAuto(Context ctx, Callback cb) {
        String url = remoteConfigUrl(ctx);
        if (url.isEmpty()) {
            // 主配置本身就是远程时，刷新/重载直接重新拉取主配置
            String primary = CONFIG_URL == null ? "" : CONFIG_URL.trim();
            if (primary.startsWith("http://") || primary.startsWith("https://")) {
                url = primary;
            }
        }
        if (url.isEmpty()) {
            if (cb != null) cb.onDone(true);
            return;
        }
        fetchAndApply(ctx.getApplicationContext(), resolveInterfaceUrl(url), cb);
    }

    /** 同步拉取指定远程地址并应用 */
    public static void fetch(Context ctx, Callback cb) {
        fetchAuto(ctx, cb);
    }

    /** 内置配置中的远程 json 地址 */
    public static String remoteConfigUrl(Context ctx) {
        try {
            if (sConfig != null && sConfig.has("remoteConfigUrl")) {
                return sConfig.optString("remoteConfigUrl", "").trim();
            }
        } catch (Throwable ignored) {
        }
        return "";
    }

    /** 远程 APK 更新入口已移除：BaseActivity.onResume 仍会调用，这里空实现 */
    public static void tryPendingUpdate(Activity act) {
    }

    private static byte[] readAsset(Context app, String name) {
        InputStream in = null;
        try {
            in = app.getAssets().open(name);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) {
                bos.write(buf, 0, n);
            }
            return bos.toByteArray();
        } catch (Throwable t) {
            return null;
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    /**
     * 宽容解析：除标准 JSON 外，还兼容 jsonc 写法 ——
     * 去掉 UTF-8 BOM、剥除 // 行注释与 /* 块注释（字符串内不剥，URL 不受影响）、容忍尾逗号。
     */
    static JSONObject parseLoose(String raw) throws Exception {
        if (raw == null) {
            throw new Exception("empty config");
        }
        String s = raw;
        if (s.startsWith("\uFEFF")) {
            s = s.substring(1);
        }
        StringBuilder sb = new StringBuilder(s.length());
        boolean inStr = false, esc = false;
        for (int i = 0, len = s.length(); i < len; i++) {
            char c = s.charAt(i);
            if (inStr) {
                sb.append(c);
                if (esc) {
                    esc = false;
                } else if (c == '\\') {
                    esc = true;
                } else if (c == '"') {
                    inStr = false;
                }
                continue;
            }
            if (c == '"') {
                inStr = true;
                sb.append(c);
                continue;
            }
            if (c == '/' && i + 1 < len && s.charAt(i + 1) == '/') {
                while (i < len && s.charAt(i) != '\n') {
                    i++;
                }
                if (i < len) {
                    sb.append('\n');
                }
                continue;
            }
            if (c == '/' && i + 1 < len && s.charAt(i + 1) == '*') {
                i += 2;
                while (i + 1 < len && !(s.charAt(i) == '*' && s.charAt(i + 1) == '/')) {
                    i++;
                }
                i++;
                continue;
            }
            sb.append(c);
        }
        String out = sb.toString().replaceAll(",(\\s*[\\]}])", "$1");
        return new JSONObject(out);
    }

    /**
     * 解析配置并落盘。
     * @param remoteOnly true=来自远程配置：默认只覆盖非设置(settings)/非n2n(hin2n)段，
     *                   且不覆盖 TVBox 开机自启(bootStart)/设为桌面(setAsLauncher)；
     *                   但内置 JSON "useRemoteConfig"=true 且远程可访问时，远程强制覆盖
     *                   settings/bootStart/setAsLauncher 与接口地址等用户配置。
     *                   false=内置配置，全部段应用（设置项按 putIfAbsent 语义写入默认值）。
     */
    private static void apply(Context ctx, JSONObject j, boolean remoteOnly) {
        // 远程覆盖模式：内置 JSON 开启 useRemoteConfig 且本次远程拉取成功（走到这里即可访问）
        boolean forceRemote = remoteOnly && useRemoteConfig();
        // 接口秘钥（固定密钥模式）：本地/远程均可下发，每次启动应用；空=用 ConfigCrypto.SECRET_KEY
        if (j.has("secretKey")) {
            Hawk.put(K_SECRET, j.optString("secretKey", "").trim());
        }
        if (j.has("notice")) {
            Object n = j.opt("notice");
            if (n instanceof JSONObject) {
                JSONObject st = (JSONObject) n;
                Hawk.put(K_NOTICE_STYLE, st.toString());
                Hawk.put(K_NOTICE, st.optString("text", ""));
            } else {
                Hawk.put(K_NOTICE, j.optString("notice", ""));
                Hawk.put(K_NOTICE_STYLE, "");
            }
        }
        if (j.has("home") && j.optJSONObject("home") != null) {
            Hawk.put(K_HOME_STYLE, j.optJSONObject("home").toString());
        }
        // 点播接口加载成功通知：多少秒后自动关闭（0=不显示）。本地/远程均可下发，每次启动应用，
        // 写入前做 0~60 范围保护，避免异常配置导致通知长期遮挡首页。
        if (j.has("loadSuccessDuration")) {
            int dur = j.optInt("loadSuccessDuration", 3);
            if (dur < 0) {
                dur = 0;
            }
            if (dur > 60) {
                dur = 60;
            }
            Hawk.put(K_LOAD_SUCCESS_DURATION, dur);
        }
        // 点播接口免责声明弹窗：多少秒后自动关闭（0=保持原行为，必须手动点确定）
        if (j.has("warningAutoClose")) {
            int dur = j.optInt("warningAutoClose", 0);
            if (dur < 0) {
                dur = 0;
            }
            if (dur > 120) {
                dur = 120;
            }
            Hawk.put(K_WARNING_AUTO_CLOSE, dur);
        }
        if (j.has("tkp2p") && j.optJSONObject("tkp2p") != null) {
            Hawk.put(K_TKP2P, j.optJSONObject("tkp2p").toString());
        }
        if (j.has("checkInterval")) {
            Hawk.put(K_CHECK_INTERVAL, Math.max(1, j.optInt("checkInterval", 30)));
        }
        if (j.has("ua")) {
            Hawk.put(K_UA, j.optString("ua", "").trim());
        }
        if (j.has("entries")) {
            Hawk.put(K_ENTRIES, j.optJSONArray("entries") != null
                    ? j.optJSONArray("entries").toString() : "");
        }
        // 注意：空数组与「不写该字段」等价 = 保持内置默认 UI。
        JSONArray topArr = j.optJSONArray("topEntries");
        if (topArr != null && topArr.length() > 0) {
            Hawk.put(K_TOP_ENTRIES, topArr.toString());
        } else {
            Hawk.delete(K_TOP_ENTRIES);
        }
        JSONArray homeArr = j.optJSONArray("homeEntries");
        if (homeArr != null && homeArr.length() > 0) {
            Hawk.put(K_HOME_ENTRIES, homeArr.toString());
        } else {
            Hawk.delete(K_HOME_ENTRIES);
        }
        if (j.has("upgrade") && j.optJSONObject("upgrade") != null) {
            JSONObject up = new JSONObject(j.optJSONObject("upgrade").toString());
            // 升级检查接口支持相对地址
            if (up.has("checkUrl")) {
                up.put("checkUrl", resolveInterfaceUrl(up.optString("checkUrl", "")));
            }
            Hawk.put(K_UPGRADE, up.toString());
        }
        // 应用市场配置：本地/远程均可下发；空对象或 null 时清除（界面为空，APK 内无内置目录）
        if (j.has("appMarket") && j.optJSONObject("appMarket") != null) {
            JSONObject market = j.optJSONObject("appMarket");
            if (market.length() > 0) {
                JSONObject resolved = new JSONObject(market.toString());
                // 应用市场接口同样支持相对地址（默认拼接主接口域名/IP）
                if (resolved.has("url")) {
                    resolved.put("url", resolveInterfaceUrl(resolved.optString("url", "")));
                }
                if (resolved.has("detailUrl")) {
                    resolved.put("detailUrl", resolveInterfaceUrl(resolved.optString("detailUrl", "")));
                }
                Hawk.put(K_APP_MARKET, resolved.toString());
            } else {
                Hawk.delete(K_APP_MARKET);
            }
        }
        // 捐助界面配置：本地/远程均可下发；空对象或 null 时清除（界面用代码内置介绍）
        if (j.has("donate")) {
            JSONObject dn = j.optJSONObject("donate");
            if (dn != null && dn.length() > 0) {
                Hawk.put(K_DONATE, dn.toString());
            } else {
                Hawk.delete(K_DONATE);
            }
        }
        // EPG 节目表显示天数：本地/远程均可下发，每次启动应用（读取处做 1~9 范围保护）
        if (j.has("epgDays")) {
            Hawk.put(K_EPG_DAYS, j.optInt("epgDays", 3));
        }
        // 阿里云播放器 SDK License：本地/远程均可下发；空字符串=清除（SDK 弹提示但不影响播放）
        if (j.has("aliLicenseKey")) {
            Hawk.put(K_ALI_LICENSE, j.optString("aliLicenseKey", "").trim());
        }
        if (j.has("hin2nEntry")) {
            Hawk.put(K_HIN2N, j.optBoolean("hin2nEntry", true));
        }
        JSONArray themes = j.optJSONArray("themes");
        if (themes != null && themes.length() > 0) {
            ThemeManager.updateThemes(themes);
        }
        // 开机启动：默认开启（bootStartEnabled 缺省 true）。远程 json 只要显式写了 bootStart
        // 就以远程为准并每次拉取同步——满足「本地/远程 json 未禁用则默认开机启动」；
        // 远程未声明该字段时保留用户在设置页的选择。
        if (remoteOnly && j.has("bootStart")) {
            Hawk.put(K_BOOT, j.optBoolean("bootStart", true));
            Hawk.put(K_BOOT_DEFAULT_SET, true);
        }
        // 以下四项属于用户本地设置，默认远程配置不覆盖：
        //   - bootStart（TVBox 开机自启，设置页项）
        //   - setAsLauncher（设为系统桌面，设置页项）
        //   - settings（设置页所有项）
        //   - hin2n（n2n 组网模块配置）
        // 例外：内置 JSON "useRemoteConfig"=true 且远程可访问（forceRemote）时，远程强制覆盖。
        if (!remoteOnly) {
            if (j.has("bootStart") && !Hawk.get(K_BOOT_DEFAULT_SET, false)) {
                Hawk.put(K_BOOT, j.optBoolean("bootStart", false));
                Hawk.put(K_BOOT_DEFAULT_SET, true);
            }
            JSONObject st = j.optJSONObject("settings");
            if (st != null) {
                // APK 内已无设置页：JSON 是本机配置的唯一来源，因此设置项每次都按 JSON 生效
                applySettingsDefaults(st, true);
            }
        } else if (forceRemote) {
            // 远程覆盖模式：强制覆盖用户已修改的设置
            if (j.has("bootStart")) {
                Hawk.put(K_BOOT, j.optBoolean("bootStart", false));
            }
            JSONObject st = j.optJSONObject("settings");
            if (st != null) {
                applySettingsDefaults(st, true);
            }
        }
        applyEndpoint(ctx, j, "api", HawkConfig.API_URL, "点播接口", forceRemote);
        applyEndpoint(ctx, j, "live", HawkConfig.LIVE_URL, "直播接口", forceRemote);
        applyEndpoint(ctx, j, "epg", HawkConfig.EPG_URL, "EPG 接口", forceRemote);
        applyEndpoint(ctx, j, "proxy", HawkConfig.PROXY_SERVER, "代理地址", forceRemote);
        if (j.has("theme")) {
            int ti = ThemeManager.indexFromConfig(j.optString("theme", ""));
            if (ti >= 0) {
                Hawk.put(HawkConfig.THEME_SELECT, ti);
            }
        }
    }

    /**
     * 功能设置默认值（JSON "settings" 段 → Hawk）。字段含义见 docs/tvbox-config.example.jsonc 的 settings 段。
     * 应用内已无设置页，调用方统一传 force=true（JSON 每次加载都以配置为准）。
     */
    private static void applySettingsDefaults(JSONObject st, boolean force) {
        // ---- 首页选项 ----
        putBool(st, "homeShowSource", HawkConfig.HOME_SHOW_SOURCE, force);   // 数据源名称显示
        putBool(st, "homeSearchPosition", HawkConfig.HOME_SEARCH_POSITION, force); // 搜索按钮位置
        putBool(st, "homeMenuPosition", HawkConfig.HOME_MENU_POSITION, force);     // 设置按钮位置
        putInt(st, "homeRec", HawkConfig.HOME_REC, force);                   // 首页推荐: 0=豆瓣 1=站点 2=历史
        putInt(st, "homeNum", HawkConfig.HOME_NUM, force);                   // 历史条数档位 0-4
        putBool(st, "homeRecStyle", HawkConfig.HOME_REC_STYLE, force);       // 首页默认多行（网格）
        putBool(st, "homeDefaultShow", HawkConfig.HOME_DEFAULT_SHOW, force); // 启动直接进直播
        // ---- 播放器选项 ----
        putBool(st, "showPreview", HawkConfig.SHOW_PREVIEW, force);          // 窗口预览
        putInt(st, "playScale", HawkConfig.PLAY_SCALE, force);               // 画面缩放 0-5
        putInt(st, "backgroundPlayType", HawkConfig.BACKGROUND_PLAY_TYPE, force); // 后台播放 0/1/2
        putInt(st, "playType", HawkConfig.PLAY_TYPE, force);                 // 播放器类型
        putStr(st, "ijkCodec", HawkConfig.IJK_CODEC, force);                 // IJK 解码方式
        putBool(st, "ijkCachePlay", HawkConfig.IJK_CACHE_PLAY, force);       // IJK 边播边缓存
        putInt(st, "exoRenderer", HawkConfig.EXO_RENDERER, force);           // Exo 扩展渲染器索引
        putInt(st, "exoRendererMode", HawkConfig.EXO_RENDERER_MODE, force);  // Exo 渲染器模式索引
        putInt(st, "vodPlayerPreferred", HawkConfig.VOD_PLAYER_PREFERRED, force); // 点播首选播放器索引
        putInt(st, "playRender", HawkConfig.PLAY_RENDER, force);             // 渲染方式 0=TextureView 1=SurfaceView
        putBool(st, "videoPurify", HawkConfig.VIDEO_PURIFY, force);          // 视频去广告（嗅探净化）
        // ---- 系统选项 ----
        putInt(st, "homeLocale", HawkConfig.HOME_LOCALE, force);             // 语言 0=中文 1=英文
        putInt(st, "themeSelect", HawkConfig.THEME_SELECT, force);           // 主题索引（缺省值）
        putInt(st, "searchView", HawkConfig.SEARCH_VIEW, force);             // 搜索展示 0=文字 1=缩略图
        putBool(st, "parseWebview", HawkConfig.PARSE_WEBVIEW, force);        // 嗅探内核
        putInt(st, "dohUrl", HawkConfig.DOH_URL, force);                     // 安全 DNS
        putBool(st, "fastSearchMode", HawkConfig.FAST_SEARCH_MODE, force);   // 聚合搜索（快搜）
        putBool(st, "debugOpen", HawkConfig.DEBUG_OPEN, force);              // 调试模式开关
    }

    private static void putBool(JSONObject st, String key, String hawkKey, boolean force) {
        try {
            if (st.has(key) && (force || !Hawk.contains(hawkKey))) {
                Hawk.put(hawkKey, st.optBoolean(key));
            }
        } catch (Throwable ignored) {
        }
    }

    private static void putInt(JSONObject st, String key, String hawkKey, boolean force) {
        try {
            if (st.has(key) && (force || !Hawk.contains(hawkKey))) {
                Hawk.put(hawkKey, st.optInt(key));
            }
        } catch (Throwable ignored) {
        }
    }

    private static void putStr(JSONObject st, String key, String hawkKey, boolean force) {
        try {
            if (st.has(key) && (force || !Hawk.contains(hawkKey))) {
                Hawk.put(hawkKey, st.optString(key, ""));
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * 写入接口地址。APK 内已无设置页，JSON 是唯一配置来源，因此每次加载都以 JSON 为准。
     * live/epg/proxy 等「其它接口」若未写 http(s)://，自动拼接主接口（api）的域名/IP。
     */
    private static void applyEndpoint(final Context app, JSONObject j, String key, String hawkKey, String label, boolean force) {
        if (!j.has(key)) {
            return;
        }
        String v = j.optString(key, "").trim();
        if (v.isEmpty()) {
            return;
        }
        if (!"api".equals(key)) {
            v = resolveInterfaceUrl(v);
        }
        Hawk.put(hawkKey, v);
    }

    /**
     * 其它接口相对地址解析：不带 http:// / https:// 的地址，默认使用主接口（api）的
     * 协议 + 域名 + 端口。例如主接口为 {@code http://1.2.3.4/config.json}，
     * live 填 {@code /live.txt} → {@code http://1.2.3.4/live.txt}；
     * 填 {@code live.txt} → {@code http://1.2.3.4/live.txt}。
     *
     * <p>已带协议、clan://、file://、assets/、proxy://、tkp2p:// 等特殊地址原样返回。</p>
     */
    public static String resolveInterfaceUrl(String url) {
        if (url == null) {
            return "";
        }
        String u = url.trim();
        if (u.isEmpty()) {
            return u;
        }
        if (u.startsWith("http://") || u.startsWith("https://")
                || u.startsWith("clan://") || u.startsWith("file://")
                || u.startsWith("assets/") || u.startsWith("proxy://")
                || u.startsWith("tkp2p://") || u.startsWith("rtmp://")
                || u.startsWith("rtsp://") || u.startsWith("content://")) {
            return u;
        }
        try {
            String api = Hawk.get(HawkConfig.API_URL, "");
            if (api == null || api.isEmpty()
                    || (!api.startsWith("http://") && !api.startsWith("https://"))) {
                return u;
            }
            java.net.URL base = new java.net.URL(api);
            String origin = base.getProtocol() + "://" + base.getAuthority();
            if (u.startsWith("/")) {
                return origin + u;
            }
            // 相对路径：以主接口所在目录为基准
            String path = base.getPath();
            int slash = path == null ? -1 : path.lastIndexOf('/');
            String dir = slash >= 0 ? path.substring(0, slash + 1) : "/";
            return origin + dir + u;
        } catch (Throwable t) {
            return u;
        }
    }
}
