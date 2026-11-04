package com.github.tvbox.osc.n2n;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import com.github.tvbox.osc.bean.AppMarketItem;
import com.lzy.okgo.OkGo;
import com.lzy.okgo.callback.AbsCallback;
import com.lzy.okgo.model.HttpHeaders;
import com.lzy.okgo.model.Response;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 应用市场数据加载：
 * <ul>
 *   <li>列表：读 RemoteConfig.appMarket() 的 url 模板（{category}/{sort}/{page}/{pageSize}/{keyword}）；
 *       url 为空时使用内置目录 assets/app_market.json 的 apps 数组，并支持本地过滤/排序/分页。</li>
 *   <li>详情：detailUrl 模板（{packageName}），为空时直接复用列表字段。</li>
 *   <li>响应兼容根数组、{"code":0,"data":{"total":N,"list":[...]}}、{"data":[...]}、{"list":[...]}。</li>
 * </ul>
 */
public class AppMarketLoader {

    public static final String CATEGORY_ALL = "all";
    public static final String CATEGORY_APP = "app";
    public static final String CATEGORY_GAME = "game";

    public static final String SORT_RECOMMEND = "recommend";
    public static final String SORT_DOWNLOAD = "download";
    public static final String SORT_UPDATE = "update";

    private static final int DEFAULT_PAGE_SIZE = 24;
    private static final String LOCAL_ASSET = "app_market.json";
    private static final Handler H = new Handler(Looper.getMainLooper());
    /** 正在下载的包名（仅用于界面「下载中」文案） */
    private static final Set<String> DOWNLOADING = new HashSet<>();

    public interface Callback {
        /**
         * @param items  本页数据，非 null
         * @param hasMore 是否还有下一页
         * @param err     网络错误提示；为 null 表示成功。有本地兜底数据时也会带上错误信息，供界面提示。
         */
        void onResult(List<AppMarketItem> items, boolean hasMore, String err);
    }

    public interface DetailCallback {
        void onResult(AppMarketItem item, boolean fromNetwork);
    }

    // ==================== 下载状态（供适配器/详情弹窗共用） ====================

    public static boolean isDownloading(String packageName) {
        return !TextUtils.isEmpty(packageName) && DOWNLOADING.contains(packageName);
    }

    public static void markDownloading(String packageName) {
        if (!TextUtils.isEmpty(packageName)) {
            DOWNLOADING.add(packageName);
        }
    }

    public static void clearDownloading(String packageName) {
        if (!TextUtils.isEmpty(packageName)) {
            DOWNLOADING.remove(packageName);
        }
    }

    /** 本机已安装版本号，未安装返回 -1 */
    public static int installedVersionCode(Context context, String packageName) {
        if (context == null || TextUtils.isEmpty(packageName)) {
            return -1;
        }
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(packageName, 0);
            return info.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            return -1;
        } catch (Throwable ignored) {
            return -1;
        }
    }

    // ==================== 列表 ====================

    public static int pageSize() {
        int size = RemoteConfig.appMarket().optInt("pageSize", DEFAULT_PAGE_SIZE);
        if (size < 1) {
            size = DEFAULT_PAGE_SIZE;
        }
        if (size > 100) {
            size = 100;
        }
        return size;
    }

    /**
     * 请求列表。
     *
     * @param page     页码，从 1 开始
     * @param category all/app/game
     * @param sort     recommend/download/update
     * @param keyword  搜索关键字，空=不搜索
     */
    public static void load(final Context context, final int page, String category, String sort,
                            String keyword, final Callback callback) {
        if (callback == null) {
            return;
        }
        final String safeCategory = TextUtils.isEmpty(category) ? CATEGORY_ALL : category;
        final String safeSort = TextUtils.isEmpty(sort) ? SORT_RECOMMEND : sort;
        final String safeKeyword = keyword == null ? "" : keyword.trim();
        JSONObject cfg = RemoteConfig.appMarket();
        String template = cfg.optString("url", "").trim();
        if (template.isEmpty()) {
            loadLocal(context, page, safeCategory, safeSort, safeKeyword, null, callback);
            return;
        }
        final String url = buildUrl(template, safeCategory, safeSort, page, pageSize(), safeKeyword);
        OkGo.<String>get(url)
                .headers(appHeaders(cfg))
                .execute(new AbsCallback<String>() {
                    @Override
                    public String convertResponse(okhttp3.Response response) throws Throwable {
                        if (response.body() != null) {
                            return response.body().string();
                        }
                        throw new IllegalStateException("empty response");
                    }

                    @Override
                    public void onSuccess(Response<String> response) {
                        List<AppMarketItem> items;
                        boolean hasMore;
                        try {
                            String raw = ConfigCrypto.maybeDecrypt(response.body());
                            JSONArray list = extractList(raw);
                            long total = extractTotal(raw);
                            items = parseItems(list);
                            hasMore = total > 0 ? (long) page * pageSize() < total : list.length() >= pageSize();
                        } catch (Throwable t) {
                            // 接口异常时退回内置目录，至少保证页面可用
                            loadLocal(context, page, safeCategory, safeSort, safeKeyword,
                                    "接口数据解析失败", callback);
                            return;
                        }
                        callback.onResult(items, hasMore, null);
                    }

                    @Override
                    public void onError(Response<String> response) {
                        Throwable ex = response.getException();
                        String msg = ex == null ? "网络请求失败" : ex.getMessage();
                        loadLocal(context, page, safeCategory, safeSort, safeKeyword, msg, callback);
                    }
                });
    }

    /** 详情：detailUrl 为空时直接回调列表里的条目（fromNetwork=false） */
    public static void loadDetail(final Context context, final AppMarketItem item,
                                  final DetailCallback callback) {
        if (callback == null) {
            return;
        }
        if (item == null) {
            callback.onResult(null, false);
            return;
        }
        final JSONObject cfg = RemoteConfig.appMarket();
        String template = cfg.optString("detailUrl", "").trim();
        if (template.isEmpty() || TextUtils.isEmpty(item.packageName)) {
            callback.onResult(item, false);
            return;
        }
        String url = template.replace("{packageName}", Uri.encode(item.packageName));
        OkGo.<String>get(url)
                .headers(appHeaders(cfg))
                .execute(new AbsCallback<String>() {
                    @Override
                    public String convertResponse(okhttp3.Response response) throws Throwable {
                        if (response.body() != null) {
                            return response.body().string();
                        }
                        throw new IllegalStateException("empty response");
                    }

                    @Override
                    public void onSuccess(Response<String> response) {
                        try {
                            String raw = ConfigCrypto.maybeDecrypt(response.body());
                            JSONObject detail = extractDetailObject(raw);
                            if (detail != null) {
                                item.mergeFrom(AppMarketItem.fromJson(detail));
                                callback.onResult(item, true);
                                return;
                            }
                        } catch (Throwable ignored) {
                        }
                        callback.onResult(item, false);
                    }

                    @Override
                    public void onError(Response<String> response) {
                        callback.onResult(item, false);
                    }
                });
    }

    private static String buildUrl(String template, String category, String sort, int page,
                                   int pageSize, String keyword) {
        String url = template.replace("{category}", category)
                .replace("{sort}", sort)
                .replace("{page}", String.valueOf(page))
                .replace("{pageSize}", String.valueOf(pageSize))
                .replace("{keyword}", Uri.encode(keyword));
        return url;
    }

    // ==================== 本地内置目录 ====================

    private static void loadLocal(final Context context, final int page, final String category,
                                  final String sort, final String keyword, final String err,
                                  final Callback callback) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                List<AppMarketItem> all = readLocalApps(context);
                List<AppMarketItem> filtered = new ArrayList<>();
                for (AppMarketItem item : all) {
                    if (!CATEGORY_ALL.equalsIgnoreCase(category)
                            && !category.equalsIgnoreCase(item.category)) {
                        continue;
                    }
                    if (!keyword.isEmpty() && !matchesKeyword(item, keyword)) {
                        continue;
                    }
                    filtered.add(item);
                }
                sortItems(filtered, sort);
                int size = pageSize();
                int from = (page - 1) * size;
                List<AppMarketItem> pageItems = new ArrayList<>();
                if (from < filtered.size() && from >= 0) {
                    int to = Math.min(from + size, filtered.size());
                    pageItems.addAll(filtered.subList(from, to));
                }
                final boolean hasMore = from + size < filtered.size();
                final List<AppMarketItem> result = pageItems;
                H.post(new Runnable() {
                    @Override
                    public void run() {
                        callback.onResult(result, hasMore, err);
                    }
                });
            }
        }, "app-market-local").start();
    }

    /** 读取内置目录：优先用配置里的 apps 数组，为空再读 assets/app_market.json */
    private static List<AppMarketItem> readLocalApps(Context context) {
        List<AppMarketItem> items = new ArrayList<>();
        JSONObject cfg = RemoteConfig.appMarket();
        JSONArray apps = cfg.optJSONArray("apps");
        if (apps == null || apps.length() == 0) {
            apps = readAssetApps(context);
        }
        if (apps == null) {
            return items;
        }
        for (int i = 0; i < apps.length(); i++) {
            JSONObject o = apps.optJSONObject(i);
            if (o == null) {
                continue;
            }
            AppMarketItem item = AppMarketItem.fromJson(o);
            item.category = normalizeCategory(item.category);
            items.add(item);
        }
        return items;
    }

    private static JSONArray readAssetApps(Context context) {
        InputStream in = null;
        try {
            Context app = context == null ? null : context.getApplicationContext();
            if (app == null) {
                return null;
            }
            in = app.getAssets().open(LOCAL_ASSET);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) {
                bos.write(buf, 0, n);
            }
            JSONObject root = RemoteConfig.parseLoose(new String(bos.toByteArray(), "UTF-8"));
            return root.optJSONArray("apps");
        } catch (Throwable ignored) {
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

    private static boolean matchesKeyword(AppMarketItem item, String keyword) {
        String lower = keyword.toLowerCase();
        return (item.displayName().toLowerCase().contains(lower))
                || (item.packageName != null && item.packageName.toLowerCase().contains(lower));
    }

    private static void sortItems(List<AppMarketItem> items, String sort) {
        if (SORT_DOWNLOAD.equalsIgnoreCase(sort)) {
            Collections.sort(items, new Comparator<AppMarketItem>() {
                @Override
                public int compare(AppMarketItem a, AppMarketItem b) {
                    long diff = b.downloads - a.downloads;
                    return diff > 0 ? 1 : (diff < 0 ? -1 : 0);
                }
            });
        } else if (SORT_UPDATE.equalsIgnoreCase(sort)) {
            Collections.sort(items, new Comparator<AppMarketItem>() {
                @Override
                public int compare(AppMarketItem a, AppMarketItem b) {
                    return b.updateTime.compareTo(a.updateTime);
                }
            });
        }
        // recommend：保持配置顺序
    }

    private static String normalizeCategory(String category) {
        if (CATEGORY_GAME.equalsIgnoreCase(category)) {
            return CATEGORY_GAME;
        }
        return CATEGORY_APP;
    }

    // ==================== 解析 ====================

    private static JSONArray extractList(String raw) throws Exception {
        String text = raw == null ? "" : raw.trim();
        if (text.startsWith("[")) {
            return new JSONArray(text);
        }
        JSONObject root = RemoteConfig.parseLoose(text);
        JSONObject data = root.optJSONObject("data");
        if (data != null) {
            JSONArray arr = data.optJSONArray("list");
            if (arr == null) {
                arr = data.optJSONArray("apps");
            }
            if (arr != null) {
                return arr;
            }
        }
        JSONArray arr = root.optJSONArray("data");
        if (arr == null) {
            arr = root.optJSONArray("list");
        }
        if (arr == null) {
            arr = root.optJSONArray("apps");
        }
        return arr == null ? new JSONArray() : arr;
    }

    private static long extractTotal(String raw) {
        try {
            String text = raw == null ? "" : raw.trim();
            if (text.startsWith("[")) {
                return -1;
            }
            JSONObject root = RemoteConfig.parseLoose(text);
            JSONObject data = root.optJSONObject("data");
            if (data != null) {
                return data.optLong("total", -1);
            }
            return root.optLong("total", -1);
        } catch (Throwable t) {
            return -1;
        }
    }

    private static List<AppMarketItem> parseItems(JSONArray arr) {
        List<AppMarketItem> items = new ArrayList<>();
        if (arr == null) {
            return items;
        }
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o != null) {
                items.add(AppMarketItem.fromJson(o));
            }
        }
        return items;
    }

    private static JSONObject extractDetailObject(String raw) throws Exception {
        String text = raw == null ? "" : raw.trim();
        if (text.startsWith("[")) {
            JSONArray arr = new JSONArray(text);
            return arr.optJSONObject(0);
        }
        JSONObject root = RemoteConfig.parseLoose(text);
        JSONObject data = root.optJSONObject("data");
        if (data != null) {
            return data;
        }
        JSONArray dataArray = root.optJSONArray("data");
        if (dataArray != null && dataArray.length() > 0) {
            return dataArray.optJSONObject(0);
        }
        return root;
    }

    /** appMarket.headers → OkGo HttpHeaders（无配置时返回空头，避免 OkGo 收到 null） */
    private static HttpHeaders appHeaders(JSONObject cfg) {
        JSONObject headers = cfg.optJSONObject("headers");
        HttpHeaders h = new HttpHeaders();
        if (headers == null || headers.length() == 0) {
            return h;
        }
        java.util.Iterator<String> keys = headers.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            String value = headers.optString(key, "");
            if (!TextUtils.isEmpty(value)) {
                h.put(key, value);
            }
        }
        return h;
    }
}
