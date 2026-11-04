package com.github.tvbox.osc.n2n;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import com.github.tvbox.osc.util.HawkConfig;
import com.orhanobut.hawk.Hawk;

import java.io.File;

/**
 * 点播配置加载守卫。
 *
 * <p>Android 4.4 等低版本设备上，某些点播接口 JSON / 动态 jar 会在加载或首页初始化阶段
 * 直接导致进程被系统杀死。此时用户在下次打开 App 时仍会读同一个接口地址，形成「一开就崩」
 * 的死循环。</p>
 *
 * <p>这里在开始加载配置前写入一个「加载中」标记，只有首页初始化成功后才清除。
 * 若进程在中途死亡，下次启动检测到残留标记，就自动清空点播接口地址并删除对应缓存，
 * 回退到内置配置，保证 App 至少可以正常打开。</p>
 */
public final class ConfigGuard {

    private static final String PREF = "tvbox_config_guard";
    private static final String KEY_LOADING = "loading";
    private static final String KEY_URL = "url";
    private static final String KEY_CACHE = "cache";

    private ConfigGuard() {
    }

    private static SharedPreferences sp(Context context) {
        return context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    /** 配置开始加载（网络拉取或读缓存）时调用 */
    public static void begin(Context context, String url, File cache) {
        try {
            sp(context).edit()
                    .putBoolean(KEY_LOADING, true)
                    .putString(KEY_URL, url == null ? "" : url)
                    .putString(KEY_CACHE, cache == null ? "" : cache.getAbsolutePath())
                    .apply();
        } catch (Throwable ignored) {
        }
    }

    /** 首页配置初始化完成（含失败后进入内置配置）时调用 */
    public static void done(Context context) {
        try {
            sp(context).edit().putBoolean(KEY_LOADING, false).apply();
        } catch (Throwable ignored) {
        }
    }

    /** App 启动时调用：上次进程在配置加载中途死亡，则清理坏配置并回退内置 */
    public static void recoverIfNeeded(Context context) {
        try {
            SharedPreferences s = sp(context);
            if (!s.getBoolean(KEY_LOADING, false)) {
                return;
            }
            String cachePath = s.getString(KEY_CACHE, "");
            if (cachePath != null && !cachePath.isEmpty()) {
                File cache = new File(cachePath);
                if (cache.exists()) {
                    cache.delete();
                }
            }
            // 清空点播接口地址，回退到内置空配置；直播接口等其它设置保持不变
            Hawk.put(HawkConfig.API_URL, "");
            s.edit().clear().apply();
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    try {
                        Toast.makeText(context, "检测到上次点播配置导致异常，已恢复默认配置", Toast.LENGTH_LONG).show();
                    } catch (Throwable ignored) {
                    }
                }
            });
        } catch (Throwable ignored) {
        }
    }
}
