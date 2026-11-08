package com.github.tvbox.osc.util;

import android.util.Log;

import com.github.tvbox.osc.event.LogEvent;

import org.greenrobot.eventbus.EventBus;

/**
 * @author pj567
 * @date :2020/12/18
 * @description:
 */
public class LOG {
    private static String TAG = "TVBox";

    /** 调试开关：读取主接口 settings.debugOpen（Hawk），读取失败/未初始化时视为关闭 */
    public static boolean isDebug() {
        try {
            return com.orhanobut.hawk.Hawk.get(com.github.tvbox.osc.util.HawkConfig.DEBUG_OPEN, false);
        } catch (Throwable t) {
            return false;
        }
    }

    /** 调试日志：仅在开启调试模式时输出（Logcat + 应用内日志面板） */
    public static void d(String msg) {
        if (!isDebug()) return;
        Log.d(TAG, "" + msg);
        EventBus.getDefault().post(new LogEvent(String.format("【D/%s】=>>>", TAG) + msg));
    }

    public static void d(String tag, String msg) {
        if (!isDebug()) return;
        Log.d(tag, msg);
        EventBus.getDefault().post(new LogEvent(String.format("【D/%s】=>>>", tag) + msg));
    }

    /** 警告日志：仅在开启调试模式时输出 */
    public static void w(String msg) {
        if (!isDebug()) return;
        Log.w(TAG, "" + msg);
        EventBus.getDefault().post(new LogEvent(String.format("【W/%s】=>>>", TAG) + msg));
    }

    public static void w(String tag, String msg) {
        if (!isDebug()) return;
        Log.w(tag, msg);
        EventBus.getDefault().post(new LogEvent(String.format("【W/%s】=>>>", tag) + msg));
    }

    public static void e(Throwable t) {
        Log.e(TAG, t.getMessage(), t);
        EventBus.getDefault().post(new LogEvent(String.format("【E/%s】=>>>", TAG) + Log.getStackTraceString(t)));
    }

    public static void e(String tag, Throwable t) {
        Log.e(tag, t.getMessage(), t);
        EventBus.getDefault().post(new LogEvent(String.format("【E/%s】=>>>", tag) + Log.getStackTraceString(t)));
    }

    public static void e(String msg) {
        Log.e(TAG, "" + msg);
        EventBus.getDefault().post(new LogEvent(String.format("【E/%s】=>>>", TAG) + msg));
    }

    public static void e(String tag, String msg) {
        Log.e(tag, msg);
        EventBus.getDefault().post(new LogEvent(String.format("【E/%s】=>>>", tag) + msg));
    }

    public static void i(String msg) {
        Log.i(TAG, msg);
        EventBus.getDefault().post(new LogEvent(String.format("【I/%s】=>>>", TAG) + msg));
    }

    public static void i(String tag, String msg) {
        Log.i(tag, msg);
        EventBus.getDefault().post(new LogEvent(String.format("【I/%s】=>>>", tag) + msg));
    }
}