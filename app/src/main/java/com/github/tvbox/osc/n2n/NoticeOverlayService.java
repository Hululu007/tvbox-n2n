package com.github.tvbox.osc.n2n;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.view.WindowManager;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.ui.activity.HomeActivity;

/**
 * 滚动公告系统悬浮窗服务。
 *
 * <p>以「前台服务 + TYPE_APPLICATION_OVERLAY 窗口」的方式把全局公告条挂到系统
 * WindowManager：公告显示在<b>所有 App 最顶层</b>，切换界面甚至切出本 App，
 * 滚动都不会中断或重启（满足「滚动通知显示在所有 app 最顶层，切换界面及 app
 * 滚动通知不切换」）。</p>
 *
 * <p>需要悬浮窗权限（{@link Settings#canDrawOverlays}）。无权限时服务不启动，
 * 公告退化为原来的「挂当前窗口 decorView」行为。公告为空时服务自动停止。</p>
 */
public class NoticeOverlayService extends Service {

    private static final String CHANNEL_ID = "notice_overlay";
    private static final int NOTIFY_ID = 0x4E4F54; // "NOT"

    /** 公告配置存在且有悬浮窗权限 → 启动服务；公告为空 → 停止服务。可在任意处调用，幂等 */
    public static void sync(Context ctx) {
        if (ctx == null) {
            return;
        }
        try {
            String notice = RemoteConfig.notice();
            boolean hasNotice = notice != null && !notice.trim().isEmpty();
            Context app = ctx.getApplicationContext();
            if (!hasNotice) {
                app.stopService(new Intent(app, NoticeOverlayService.class));
                return;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(app)) {
                // 无悬浮窗权限：保持界面内公告（decorView 模式），不启动服务
                RcLog.w("NoticeOverlayService: no overlay permission, fallback to in-app bar");
                return;
            }
            Intent it = new Intent(app, NoticeOverlayService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                app.startForegroundService(it);
            } else {
                app.startService(it);
            }
        } catch (Throwable t) {
            RcLog.e("NoticeOverlayService: sync error", t);
        }
    }

    /** 是否具备悬浮窗权限 */
    public static boolean canDrawOverlays(Context ctx) {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(ctx);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        startForegroundCompat();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String notice = RemoteConfig.notice();
        if (notice == null || notice.trim().isEmpty()) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        NoticeBar.attachSystemOverlay(this, (WindowManager) getSystemService(WINDOW_SERVICE));
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        NoticeBar.detachSystemOverlay();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void startForegroundCompat() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                if (nm != null && nm.getNotificationChannel(CHANNEL_ID) == null) {
                    NotificationChannel ch = new NotificationChannel(
                            CHANNEL_ID, "滚动公告", NotificationManager.IMPORTANCE_MIN);
                    ch.setDescription("滚动公告悬浮窗保活");
                    ch.setShowBadge(false);
                    nm.createNotificationChannel(ch);
                }
            }
            Intent open = new Intent(this, HomeActivity.class);
            open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            PendingIntent pi = PendingIntent.getActivity(this, 0, open,
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                            ? PendingIntent.FLAG_IMMUTABLE : 0);
            Notification.Builder b = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    ? new Notification.Builder(this, CHANNEL_ID)
                    : new Notification.Builder(this);
            b.setSmallIcon(R.drawable.app_icon)
                    .setContentTitle(getString(R.string.app_name))
                    .setContentText("滚动公告运行中")
                    .setContentIntent(pi)
                    .setOngoing(true);
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTIFY_ID, b.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } else {
                startForeground(NOTIFY_ID, b.build());
            }
        } catch (Throwable t) {
            RcLog.e("NoticeOverlayService: startForeground error", t);
        }
    }
}
