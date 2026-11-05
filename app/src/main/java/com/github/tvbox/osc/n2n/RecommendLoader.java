package com.github.tvbox.osc.n2n;

import android.app.Activity;

import com.github.tvbox.osc.bean.Movie;
import com.github.tvbox.osc.bean.VodInfo;
import com.github.tvbox.osc.cache.RoomDataManger;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.UA;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.lzy.okgo.OkGo;
import com.lzy.okgo.callback.AbsCallback;
import com.lzy.okgo.model.Response;
import com.orhanobut.hawk.Hawk;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * 首页 / 点播首屏「热播推荐」统一数据源。
 * 规则与主界面完全一致：
 *  - HOME_REC == 2：观看历史；
 *  - 主接口配置了 home.recMovies：使用接口推荐内容；
 *  - 否则回退豆瓣热播（按天缓存）。
 */
public class RecommendLoader {

    public interface Callback {
        void onResult(List<Movie.Video> videos);
    }

    /** 读取推荐（历史模式同步返回；其它模式可能异步回调） */
    public static void load(final Activity activity, final Callback callback) {
        if (Hawk.get(HawkConfig.HOME_REC, 0) == 2) {
            callback.onResult(loadHistory());
            return;
        }
        List<Movie.Video> custom = RemoteConfig.homeRecMovies();
        if (custom != null && !custom.isEmpty()) {
            callback.onResult(custom);
            return;
        }
        loadDouban(activity, callback);
    }

    /** 观看历史 → 推荐列表（最多 20 条，与首页一致） */
    public static List<Movie.Video> loadHistory() {
        List<Movie.Video> list = new ArrayList<>();
        List<VodInfo> records = RoomDataManger.getAllVodRecord(20);
        for (VodInfo info : records) {
            Movie.Video v = new Movie.Video();
            v.id = info.id;
            v.sourceKey = info.sourceKey;
            v.name = info.name;
            v.pic = info.pic;
            if (info.playNote != null && !info.playNote.isEmpty()) {
                v.note = "上次看到" + info.playNote;
            }
            list.add(v);
        }
        return list;
    }

    private static void loadDouban(final Activity activity, final Callback callback) {
        try {
            Calendar cal = Calendar.getInstance();
            int year = cal.get(Calendar.YEAR);
            String today = String.format("%d%d%d", year, cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DATE));
            String cachedDay = Hawk.get("home_hot_day", "");
            if (today.equals(cachedDay)) {
                String json = Hawk.get("home_hot", "");
                if (!json.isEmpty()) {
                    callback.onResult(parseDouban(json));
                    return;
                }
            }
            String url = "https://movie.douban.com/j/new_search_subjects?sort=U&range=0,10&tags=&playable=1&start=0&year_range=" + year + "," + year;
            OkGo.<String>get(url).headers("User-Agent", UA.random()).execute(new AbsCallback<String>() {
                @Override
                public void onSuccess(Response<String> response) {
                    final String netJson = response.body();
                    Hawk.put("home_hot_day", today);
                    Hawk.put("home_hot", netJson);
                    final List<Movie.Video> list = parseDouban(netJson);
                    if (activity != null) {
                        activity.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                callback.onResult(list);
                            }
                        });
                    } else {
                        callback.onResult(list);
                    }
                }

                @Override
                public String convertResponse(okhttp3.Response response) throws Throwable {
                    return response.body().string();
                }
            });
        } catch (Throwable th) {
            th.printStackTrace();
        }
    }

    private static List<Movie.Video> parseDouban(String json) {
        List<Movie.Video> result = new ArrayList<>();
        try {
            JsonObject infoJson = new Gson().fromJson(json, JsonObject.class);
            JsonArray array = infoJson.getAsJsonArray("data");
            for (JsonElement ele : array) {
                JsonObject obj = (JsonObject) ele;
                Movie.Video vod = new Movie.Video();
                // 豆瓣条目没有影视源 id：留空，点击时走搜索（与首页推荐一致）
                vod.name = obj.get("title").getAsString();
                vod.pic = obj.get("cover").getAsString() + "@User-Agent=" + UA.random() + "@Referer=https://www.douban.com/";
                if (obj.has("rate")) {
                    vod.note = obj.get("rate").getAsString();
                }
                result.add(vod);
            }
        } catch (Throwable th) {
            th.printStackTrace();
        }
        return result;
    }
}
