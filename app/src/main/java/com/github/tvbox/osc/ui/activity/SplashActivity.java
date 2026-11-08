package com.github.tvbox.osc.ui.activity;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.n2n.HomeEntry;
import com.github.tvbox.osc.n2n.RemoteConfig;
import com.github.tvbox.osc.util.AppManager;
import com.github.tvbox.osc.util.LOG;

import org.json.JSONObject;

/**
 * App 启动界面（LAUNCHER）。
 *
 * <p>启动立刻显示内置启动图与「正在加载配置…」，同时等待主接口加载结果：
 * 成功 → 最短展示时长后进入首页（首页再按「开机进入直播 / defaultScreen」分流）；
 * 失败 → 显示失败提示与「重试 / 退出」按钮。</p>
 *
 * <p>主接口 splash 段（image/title/loadingText/failText/backgroundColor/duration）会在配置
 * 拉取完成后覆盖内置启动图与文案；上次启动缓存过的配置可在本次首帧直接生效。</p>
 */
public class SplashActivity extends BaseActivity {

    /** 本次进程是否已经从启动画面进入过首页（HomeActivity 冷启动判断用，避免重复显示启动图） */
    private static volatile boolean sEnteredHome = false;

    public static boolean hasEnteredHome() {
        return sEnteredHome;
    }

    /** 主接口最长等待时间：超时后显示失败提示（仍保留监听，配置迟到成功会自动进入首页） */
    private static final long MAX_WAIT_MS = 20000L;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private ImageView bgImage;
    private ImageView logo;
    private TextView title;
    private TextView loading;
    private ProgressBar progress;
    private LinearLayout actions;

    private long startAt;
    private int minDuration = 1200;
    private boolean leaving = false;
    private boolean homeScheduled = false;
    private String failText = "配置加载失败，请检查网络后重试";
    private String loadingText = "正在加载配置…";

    private final RemoteConfig.PrimaryListener primaryListener = new RemoteConfig.PrimaryListener() {
        @Override
        public void onPrimaryResult(final int state) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    onPrimary(state);
                }
            });
        }
    };

    private final Runnable timeoutRun = new Runnable() {
        @Override
        public void run() {
            if (!leaving && !homeScheduled) {
                LOG.w("Splash", "wait config timeout");
                showFailUi();
            }
        }
    };

    private final Runnable openHomeRun = new Runnable() {
        @Override
        public void run() {
            openHome();
        }
    };

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_splash;
    }

    /** 启动画面固定背景色（主接口 splash.backgroundColor，缺省深蓝黑），不跟随主界面主题 */
    @Override
    protected int backgroundColorOverride() {
        try {
            int c = HomeEntry.parseColor(RemoteConfig.splashColor());
            return c != 0 ? c : 0xFF0B1024;
        } catch (Throwable ignored) {
            return 0xFF0B1024;
        }
    }

    /** 启动画面使用内置/主接口启动图，不套用首页背景图 */
    @Override
    protected boolean applyHomeBackground() {
        return false;
    }

    @Override
    protected void init() {
        startAt = SystemClock.elapsedRealtime();
        bindViews();
        applySplashConfig(false);
        LOG.d("Splash", "splash shown, primaryState=" + RemoteConfig.primaryState());
        // 主接口可能仍在加载，也可能已有结果（会立即回调）
        RemoteConfig.addPrimaryListener(primaryListener);
        handler.postDelayed(timeoutRun, MAX_WAIT_MS);
    }

    private void bindViews() {
        bgImage = findViewById(R.id.splashBgImage);
        logo = findViewById(R.id.splashLogo);
        title = findViewById(R.id.splashTitle);
        loading = findViewById(R.id.splashLoading);
        progress = findViewById(R.id.splashProgress);
        actions = findViewById(R.id.splashActions);
        findViewById(R.id.btnSplashRetry).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                retry();
            }
        });
        findViewById(R.id.btnSplashExit).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                exitApp();
            }
        });
    }

    /** 应用内置/主接口 splash 配置（主接口字段缺省时保留内置启动图与默认文案） */
    private void applySplashConfig(boolean configReady) {
        JSONObject j = RemoteConfig.splash();
        int color = backgroundColorOverride();
        try {
            findViewById(R.id.splashRoot).setBackgroundColor(color);
            getWindow().setBackgroundDrawable(new ColorDrawable(color));
        } catch (Throwable ignored) {
        }

        String image = RemoteConfig.splashImage();
        if (image != null && !image.isEmpty()) {
            try {
                if (bgImage != null) {
                    bgImage.setVisibility(View.VISIBLE);
                    Glide.with(this).load(image).into(bgImage);
                }
                View dim = findViewById(R.id.splashDim);
                if (dim != null) dim.setVisibility(View.VISIBLE);
                if (logo != null) logo.setVisibility(View.GONE);
            } catch (Throwable ignored) {
            }
        } else {
            if (bgImage != null) bgImage.setVisibility(View.GONE);
            View dim = findViewById(R.id.splashDim);
            if (dim != null) dim.setVisibility(View.GONE);
            if (logo != null) logo.setVisibility(View.VISIBLE);
        }

        String t = RemoteConfig.splashTitle();
        if (title != null) {
            title.setText(t.isEmpty() ? getString(R.string.app_name) : t);
        }
        String lt = RemoteConfig.splashLoadingText();
        loadingText = lt.isEmpty() ? "正在加载配置…" : lt;
        String ft = RemoteConfig.splashFailText();
        failText = ft.isEmpty() ? "配置加载失败，请检查网络后重试" : ft;
        if (loading != null && !actions.isShown()) {
            loading.setText(loadingText);
        }
        // 配置刚拉取成功时刷新最短展示时长；首帧（缓存）也按缓存值展示
        minDuration = RemoteConfig.splashDuration();
        LOG.d("Splash", "splash config image=" + (image != null && !image.isEmpty())
                + " duration=" + minDuration + " configReady=" + configReady
                + " keys=" + (j == null ? 0 : j.length()));
    }

    private void onPrimary(int state) {
        if (leaving) {
            return;
        }
        LOG.d("Splash", "primary result=" + state);
        // 配置已写入 Hawk：立即刷新主接口下发的启动图/文案
        applySplashConfig(true);
        if (state == RemoteConfig.PRIMARY_OK) {
            showLoadingUi();
            scheduleOpenHome();
        } else {
            showFailUi();
        }
    }

    private void showLoadingUi() {
        if (progress != null) progress.setVisibility(View.VISIBLE);
        if (loading != null) {
            loading.setText(loadingText);
            loading.setVisibility(View.VISIBLE);
        }
        if (actions != null) actions.setVisibility(View.GONE);
    }

    private void showFailUi() {
        if (leaving) {
            return;
        }
        handler.removeCallbacks(openHomeRun);
        if (progress != null) progress.setVisibility(View.GONE);
        if (loading != null) loading.setText(failText);
        if (actions != null) {
            actions.setVisibility(View.VISIBLE);
            View retry = findViewById(R.id.btnSplashRetry);
            if (retry != null) retry.requestFocus();
        }
    }

    private void scheduleOpenHome() {
        if (leaving || homeScheduled) {
            return;
        }
        homeScheduled = true;
        handler.removeCallbacks(timeoutRun);
        long elapsed = SystemClock.elapsedRealtime() - startAt;
        long delay = Math.max(0L, minDuration - elapsed);
        LOG.d("Splash", "open home in " + delay + "ms");
        handler.postDelayed(openHomeRun, delay);
    }

    private void openHome() {
        if (leaving) {
            return;
        }
        leaving = true;
        handler.removeCallbacksAndMessages(null);
        RemoteConfig.removePrimaryListener(primaryListener);
        LOG.i("Splash", "launch HomeActivity");
        sEnteredHome = true;
        // 复用已有首页，避免从桌面重复进入时堆叠多个 HomeActivity
        Intent home = new Intent(this, HomeActivity.class);
        home.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(home);
        finish();
        overridePendingTransition(0, 0);
    }

    private void retry() {
        if (leaving) {
            return;
        }
        LOG.d("Splash", "retry primary config");
        homeScheduled = false;
        startAt = SystemClock.elapsedRealtime();
        showLoadingUi();
        handler.removeCallbacks(timeoutRun);
        handler.postDelayed(timeoutRun, MAX_WAIT_MS);
        RemoteConfig.retryPrimary(getApplicationContext());
        RemoteConfig.addPrimaryListener(primaryListener);
    }

    private void exitApp() {
        leaving = true;
        handler.removeCallbacksAndMessages(null);
        RemoteConfig.removePrimaryListener(primaryListener);
        try {
            AppManager.getInstance().appExit(0);
        } catch (Throwable ignored) {
        }
        finish();
    }

    @Override
    public void onBackPressed() {
        // 启动界面不允许返回键跳过配置加载
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        RemoteConfig.removePrimaryListener(primaryListener);
    }
}
