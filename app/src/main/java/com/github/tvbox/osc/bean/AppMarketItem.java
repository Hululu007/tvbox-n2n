package com.github.tvbox.osc.bean;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 应用市场条目（列表接口与内置目录共用）。
 *
 * <p>字段全部按接口约定解析，缺失时使用默认值，保证服务端只返回部分字段也不会崩溃。</p>
 */
public class AppMarketItem {

    public String packageName = "";
    public String name = "";
    public String versionName = "";
    public int versionCode = 0;
    public String icon = "";
    public String size = "";
    public long downloads = 0;
    public String category = "app";
    public String summary = "";
    public String description = "";
    public List<String> screenshots = new ArrayList<>();
    public String downloadUrl = "";
    public String developer = "";
    public String updateTime = "";

    public static AppMarketItem fromJson(JSONObject o) {
        AppMarketItem item = new AppMarketItem();
        if (o == null) {
            return item;
        }
        item.packageName = o.optString("packageName", "").trim();
        item.name = o.optString("name", "").trim();
        item.versionName = o.optString("versionName", "").trim();
        item.versionCode = o.optInt("versionCode", 0);
        item.icon = o.optString("icon", "").trim();
        item.category = o.optString("category", "app").trim();
        if (item.category.isEmpty()) {
            item.category = "app";
        }
        item.summary = o.optString("summary", "").trim();
        item.description = o.optString("description", "").trim();
        item.downloadUrl = o.optString("downloadUrl", "").trim();
        item.developer = o.optString("developer", "").trim();
        item.updateTime = o.optString("updateTime", "").trim();
        item.downloads = o.optLong("downloads", 0);

        // size 兼容两种写法：数字（字节）或已经格式化好的字符串（如 "12.6MB"）
        Object sizeObj = o.opt("size");
        if (sizeObj instanceof Number) {
            item.size = formatSize(((Number) sizeObj).longValue());
        } else {
            item.size = o.optString("size", "").trim();
        }

        JSONArray shots = o.optJSONArray("screenshots");
        if (shots != null) {
            for (int i = 0; i < shots.length(); i++) {
                Object shot = shots.opt(i);
                String url = "";
                if (shot instanceof JSONObject) {
                    url = ((JSONObject) shot).optString("url", "").trim();
                } else if (shot != null) {
                    url = String.valueOf(shot).trim();
                }
                if (!url.isEmpty()) {
                    item.screenshots.add(url);
                }
            }
        }
        return item;
    }

    /** 详情接口返回后合并字段：非空字段覆盖，避免详情只返回部分字段时把列表数据清空 */
    public void mergeFrom(AppMarketItem other) {
        if (other == null) {
            return;
        }
        if (!other.name.isEmpty()) name = other.name;
        if (!other.versionName.isEmpty()) versionName = other.versionName;
        if (other.versionCode > 0) versionCode = other.versionCode;
        if (!other.icon.isEmpty()) icon = other.icon;
        if (!other.size.isEmpty()) size = other.size;
        if (other.downloads > 0) downloads = other.downloads;
        if (!other.summary.isEmpty()) summary = other.summary;
        if (!other.description.isEmpty()) description = other.description;
        if (!other.screenshots.isEmpty()) screenshots = other.screenshots;
        if (!other.downloadUrl.isEmpty()) downloadUrl = other.downloadUrl;
        if (!other.developer.isEmpty()) developer = other.developer;
        if (!other.updateTime.isEmpty()) updateTime = other.updateTime;
    }

    /** 列表展示名：name 为空时退回包名 */
    public String displayName() {
        if (name != null && !name.isEmpty()) {
            return name;
        }
        return packageName == null ? "" : packageName;
    }

    /** 下载量格式化：千分位，避免各语言下的单位差异 */
    public static String formatDownloads(long n) {
        if (n <= 0) {
            return "0";
        }
        return String.format(Locale.US, "%,d", n);
    }

    /** 字节数格式化为 KB/MB/GB */
    public static String formatSize(long bytes) {
        if (bytes <= 0) {
            return "";
        }
        if (bytes >= 1024L * 1024 * 1024) {
            return String.format(Locale.US, "%.1f GB", bytes / 1024.0 / 1024.0 / 1024.0);
        }
        if (bytes >= 1024L * 1024) {
            return String.format(Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0);
        }
        if (bytes >= 1024L) {
            return String.format(Locale.US, "%.0f KB", bytes / 1024.0);
        }
        return bytes + " B";
    }
}
