package com.github.tvbox.osc.n2n;

import android.util.Log;

import com.github.tvbox.osc.base.App;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 主接口（RemoteConfig）诊断日志：同时写 logcat 与本地文件
 * {@code filesDir/logs/remote-config.log}。
 *
 * <p>用户反馈「开启调试模式仍无任何日志」的根因：此前主接口拉取只写了 logcat，
 * 电视盒子上用户没有 adb 看不到；App 内的 LogEvent 又没有消费端。这里把关键事件
 * （启动 / 缓存命中 / 每次请求的结果码与异常 / 状态通知决策）全部落盘，随时可用
 * 文件管理器或 adb pull 查看。</p>
 *
 * <p>文件按 256KB 截断重写，最多保留一个 .old 备份，避免无限增长。</p>
 */
public final class RcLog {

    private static final String TAG = "RemoteConfig";
    private static final String DIR = "logs";
    private static final String FILE = "remote-config.log";
    private static final long MAX_BYTES = 256 * 1024;

    private static final Object LOCK = new Object();
    private static final SimpleDateFormat FMT =
            new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US);

    private RcLog() {
    }

    public static void i(String msg) {
        Log.i(TAG, msg);
        write("I", msg, null);
    }

    public static void w(String msg) {
        Log.w(TAG, msg);
        write("W", msg, null);
    }

    public static void e(String msg, Throwable t) {
        Log.e(TAG, msg, t);
        write("E", msg, t);
    }

    /** 当前日志文件（便于设置页展示路径 / 用户导出） */
    public static File logFile() {
        try {
            return new File(new File(App.getInstance().getFilesDir(), DIR), FILE);
        } catch (Throwable t) {
            return null;
        }
    }

    private static void write(String level, String msg, Throwable t) {
        synchronized (LOCK) {
            FileOutputStream fos = null;
            try {
                File dir = new File(App.getInstance().getFilesDir(), DIR);
                if (!dir.exists() && !dir.mkdirs()) {
                    return;
                }
                File f = new File(dir, FILE);
                if (f.exists() && f.length() > MAX_BYTES) {
                    File old = new File(dir, FILE + ".old");
                    // 覆盖旧备份；renameTo 失败则直接删了重写
                    if (old.exists()) {
                        old.delete();
                    }
                    if (!f.renameTo(old)) {
                        f.delete();
                    }
                }
                StringBuilder sb = new StringBuilder(160);
                sb.append(FMT.format(new Date()))
                        .append(' ').append(level).append("/RC: ")
                        .append(msg == null ? "" : msg).append('\n');
                if (t != null) {
                    sb.append(Log.getStackTraceString(t)).append('\n');
                }
                fos = new FileOutputStream(f, true);
                fos.write(sb.toString().getBytes("UTF-8"));
                fos.flush();
            } catch (Throwable ignored) {
            } finally {
                if (fos != null) {
                    try {
                        fos.close();
                    } catch (Throwable ignored) {
                    }
                }
            }
        }
    }
}
