package com.github.tvbox.osc.n2n;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.Scroller;
import android.widget.TextView;

import androidx.constraintlayout.widget.ConstraintLayout;

import com.github.tvbox.osc.R;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;

/**
 * 滚动公告：样式（位置/背景色/字号字色/透明度/显示时间段/过期时间/滚动速度）全部来自
 * 远程 JSON 的 notice 段，可按界面（首页 / 直播 / 点播）分别控制是否显示。
 *
 * <p>2026-11-06 起改为「全局唯一公告条」：整个进程只创建一个 TextView，挂在当前窗口
 * decorView 的最顶层。切换界面（首页 → 直播 → 点播 → 酒店）时只是把这个 View 从一个窗口
 * 挪到另一个窗口，不重建 View、不重置文字，滚动位置与计时继续沿用，
 * 因此不会再出现「每个界面重新加载一次公告」的现象。</p>
 *
 * <p>滚动方向为「由右向左」（标准跑马灯方向）。</p>
 */
public class NoticeBar {

    public static final String SCREEN_HOME = "home";
    public static final String SCREEN_LIVE = "live";
    public static final String SCREEN_VOD = "vod";
    /** 酒店 IPTV 桌面（ticker 由 HotelActivity 传入，不动态叠加） */
    public static final String SCREEN_HOTEL = "hotel";

    private static final String TAG_OVERLAY = "tvbox_notice_bar_global";

    private static final Handler H = new Handler(Looper.getMainLooper());

    /** 全局唯一公告 View：跨界面复用（使用 Application Context 创建，避免持有 Activity 泄漏） */
    private static TextView sView;
    /** 全局公告的滚动/计时状态：与界面无关 */
    private static final ScreenState GLOBAL = new ScreenState();
    /** 酒店界面自带 ticker 的独立状态（不参与全局公告） */
    private static final HashMap<String, ScreenState> STATES = new HashMap<>();

    /** 系统悬浮窗模式：公告挂在系统 WindowManager（覆盖所有 App），不跟随 Activity 搬家 */
    private static volatile boolean sOverlayActive = false;
    private static android.view.WindowManager sOverlayWm = null;
    /** 最近的前台 Activity：悬浮窗渲染时优先用它的（AutoSize 适配后）资源 */
    private static java.lang.ref.WeakReference<Activity> sTopAct = null;

    /** 系统悬浮窗公告是否已生效 */
    public static boolean isOverlayActive() {
        return sOverlayActive && sView != null;
    }

    /** 记录最近前台 Activity（App 生命周期回调中调用） */
    public static void trackActivity(Activity act) {
        if (act != null) {
            sTopAct = new java.lang.ref.WeakReference<>(act);
        }
    }

    private static Activity bestActivity() {
        Activity a = sTopAct == null ? null : sTopAct.get();
        if (a != null && !a.isFinishing() && !a.isDestroyed()) {
            return a;
        }
        return null;
    }

    /**
     * 把全局公告 View 挂到系统 WindowManager（TYPE_APPLICATION_OVERLAY）：
     * 显示在所有 App 最顶层，切换界面甚至切出本 App 滚动都不中断。
     * 由 NoticeOverlayService 调用；幂等。
     */
    public static synchronized void attachSystemOverlay(android.content.Context ctx,
                                                        android.view.WindowManager wm) {
        if (ctx == null || wm == null) {
            return;
        }
        try {
            if (sView == null) {
                Activity act = bestActivity();
                sView = createOverlay(act != null ? act : ctx);
            }
            // 从 Activity 窗口摘除（如果之前挂在 decorView）
            ViewParent p = sView.getParent();
            if (p instanceof ViewGroup) {
                ((ViewGroup) p).removeView(sView);
            }
            if (!sOverlayActive) {
                sOverlayWm = wm;
                wm.addView(sView, createOverlayWmParams(ctx));
                sOverlayActive = true;
                RcLog.i("NoticeBar: system overlay attached");
            }
            renderSystemOverlay();
        } catch (Throwable t) {
            RcLog.e("NoticeBar: attachSystemOverlay failed", t);
        }
    }

    /** 移除系统悬浮窗公告 */
    public static synchronized void detachSystemOverlay() {
        if (sOverlayActive && sOverlayWm != null && sView != null) {
            try {
                sOverlayWm.removeView(sView);
            } catch (Throwable ignored) {
            }
        }
        sOverlayActive = false;
        sOverlayWm = null;
    }

    /** 按最新 notice 配置重渲染系统悬浮窗（文本/样式/显隐/位置）；无主配置时隐藏 */
    public static void renderSystemOverlay() {
        if (!sOverlayActive || sView == null) {
            return;
        }
        Runnable r = new Runnable() {
            @Override
            public void run() {
                try {
                    Activity act = bestActivity();
                    if (act == null) {
                        // 无前台界面（App 在后台）：保持当前滚动，不重渲染
                        return;
                    }
                    // 位置是窗口级属性：按最新配置更新 gravity
                    if (sOverlayWm != null && sView.getParent() != null) {
                        sOverlayWm.updateViewLayout(sView, createOverlayWmParams(act));
                    }
                    render(act, sView, sLastScreen, GLOBAL);
                } catch (Throwable t) {
                    RcLog.e("NoticeBar: renderSystemOverlay error", t);
                }
            }
        };
        if (Looper.myLooper() == Looper.getMainLooper()) {
            r.run();
        } else {
            H.post(r);
        }
    }

    /** 系统悬浮窗的 WindowManager 参数：整宽、位置随 notice.position、不可触摸不可聚焦 */
    private static android.view.WindowManager.LayoutParams createOverlayWmParams(android.content.Context ctx) {
        int height;
        int margin;
        try {
            height = ctx.getResources().getDimensionPixelSize(R.dimen.vs_40);
            margin = ctx.getResources().getDimensionPixelSize(R.dimen.vs_10);
        } catch (Throwable t) {
            height = 40;
            margin = 10;
        }
        boolean top = false;
        try {
            top = "top".equals(RemoteConfig.noticeStyle().optString("position", "bottom").trim());
        } catch (Throwable ignored) {
        }
        android.view.WindowManager.LayoutParams lp = new android.view.WindowManager.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, height,
                android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O
                        ? android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : android.view.WindowManager.LayoutParams.TYPE_PHONE,
                android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | android.view.WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                android.graphics.PixelFormat.TRANSLUCENT);
        lp.gravity = (top ? Gravity.TOP : Gravity.BOTTOM) | Gravity.CENTER_HORIZONTAL;
        lp.y = margin;
        return lp;
    }

    private static final class ScreenState {
        TextView view;
        Runnable speedRun;
        Runnable hideRun;
        /** 当前显示文本，用于判断 ticker 重新渲染是否同一内容 */
        String content;
        /** hideRun 预定执行时间（uptimeMillis），0 表示常显或无隐藏任务 */
        long deadline;
        /** 当前内容已展示到期：同内容再次 render 时保持隐藏，不再重新计时 */
        boolean expired;
        /** 正在滚动的内容文本：周期 render 时用于判断是否需要重启滚动（避免跑马灯位置被重置） */
        String scrollText;
        /** 正在使用的滚动速度（像素/秒），与 scrollText 一起作为「是否需要重启滚动」的判断依据 */
        int speed;
        /** 正在使用的整条走完后的停顿秒数（notice.pause），参与「是否需要重启滚动」判断 */
        int pause;
        /** 整条走完停顿模式下的属性动画（停止时需主动 cancel） */
        ValueAnimator scrollAnim;
        /** 滚动代次：每次启动/停止滚动自增，用于让上一次遗留的延时任务失效 */
        int scrollToken;
        /** 上一窗口的滚动位置：视图换窗口时从这里接着滚 */
        int savedScroll;
    }

    private static ScreenState getOrCreate(String screen) {
        ScreenState ss = STATES.get(screen);
        if (ss == null) {
            ss = new ScreenState();
            STATES.put(screen, ss);
        }
        return ss;
    }

    /** 最近一次调用 show() 的界面类型（用于新界面恢复公告时沿用同一套样式） */
    private static String sLastScreen = SCREEN_HOME;

    /** 在指定界面渲染公告条（幂等，可重复调用） */
    public static void show(Activity act, String screen) {
        if (act == null || act.isFinishing() || act.isDestroyed()) {
            return;
        }
        sLastScreen = screen;
        trackActivity(act);
        // 有悬浮窗权限且有公告 → 拉起前台服务把公告挂到系统最顶层（覆盖所有 App）
        NoticeOverlayService.sync(act);
        // 首页布局自带的 tvNotice 仅隐藏不再使用，避免同一条公告显示两份
        if (SCREEN_HOME.equals(screen)) {
            View builtin = act.findViewById(R.id.tvNotice);
            if (builtin != null) {
                builtin.setVisibility(View.GONE);
            }
        }
        // 系统悬浮窗模式：公告在系统窗口，仅按最新配置重渲染，不往界面窗口挂
        // （sView 为 null 说明服务刚启动尚未 attach，走原 decorView 路径兜底，
        //   服务 attach 时会自动从界面窗口摘除并迁移到系统窗口）
        if (sOverlayActive && sView != null) {
            render(act, sView, screen, GLOBAL);
            return;
        }
        TextView tv = obtainOverlay(act);
        if (tv == null) {
            return;
        }
        GLOBAL.view = tv;
        render(act, tv, screen, GLOBAL);
    }

    /**
     * 任意界面 resume 时由 Application 生命周期回调调用：
     * 把全局公告 View 重新挂到当前窗口最顶层，保证详情/搜索/设置等没有主动调用 show() 的界面
     * 也能继续显示同一条公告（不重建、不重置滚动）。
     */
    public static void onActivityResumed(Activity act) {
        if (act == null || act.isFinishing() || act.isDestroyed()) {
            return;
        }
        trackActivity(act);
        try {
            // 酒店界面自带 ticker 时由 showOn() 决定用哪一个，避免覆盖
            if (act instanceof com.github.tvbox.osc.ui.activity.HotelActivity) {
                return;
            }
            String notice = RemoteConfig.notice();
            if (notice == null || notice.trim().isEmpty()) {
                return;
            }
            // 系统悬浮窗模式：公告常驻系统窗口，切换界面无需任何搬动/重渲染
            if (sOverlayActive) {
                return;
            }
            TextView tv = obtainOverlay(act);
            if (tv == null) {
                return;
            }
            GLOBAL.view = tv;
            render(act, tv, sLastScreen, GLOBAL);
        } catch (Throwable ignored) {
        }
    }

    /**
     * 主动隐藏（离开播放页时调用）。
     *
     * <p>全局公告条不随界面销毁而停止：同一个 View 会被下一个界面接管并继续滚动，
     * 所以这里只解除本窗口对 View 的持有（防泄漏），不清空文字/滚动位置。</p>
     */
    public static void hide(Activity act, String screen) {
        detach(act);
    }

    /**
     * 在指定 TextView 上渲染公告（用于酒店桌面等自带 ticker 的界面）。
     *
     * @param fallbackText 界面自带滚动文案（如 screens[].ticker）；非空时优先显示它，
     *                     不使用全局公告样式；为空时回退到主接口 notice 全局公告
     */
    public static void showOn(Activity act, TextView tv, String screen, String fallbackText) {
        if (act == null || tv == null) {
            return;
        }
        String fb = fallbackText == null ? "" : fallbackText.trim();
        if (!fb.isEmpty()) {
            // 本屏使用布局自带 ticker：隐藏全局公告条，避免两条公告同时出现
            hideGlobal();
            ScreenState ss = getOrCreate(screen);
            ss.view = tv;
            ss.content = null;
            ss.scrollText = null;
            ss.speed = 0;
            ss.pause = 0;
            ss.deadline = 0;
            ss.expired = false;
            cancelAutoHide(ss);
            stopSpeedScroll(ss);
            tv.setTranslationX(0f);
            tv.setText(fb);
            tv.setVisibility(View.VISIBLE);
            tv.setSelected(true);
            return;
        }
        // 本屏没有自带 ticker：同样走全局最顶层公告条（不占用布局内的 ticker 位置）
        tv.setVisibility(View.GONE);
        show(act, screen);
    }

    /** 隐藏指定界面自带的公告 TextView */
    public static void hideOn(TextView tv, String screen) {
        ScreenState ss = STATES.remove(screen);
        if (ss != null) {
            hideScreen(ss);
        }
        if (tv != null) {
            tv.setVisibility(View.GONE);
        }
    }

    /** 界面销毁时调用：只解除该窗口对全局公告 View 的持有，滚动状态保留 */
    public static void detach(Activity act) {
        if (act == null || sView == null) {
            return;
        }
        try {
            ViewParent p = sView.getParent();
            if (p != null && p == act.getWindow().getDecorView()) {
                ((ViewGroup) p).removeView(sView);
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * 应用退到后台（所有界面都已 stop）：暂停滚动循环，避免后台每 300ms 唤醒一次。
     * 记录当前滚动位置，回到前台后从该位置继续。
     */
    public static void onAppHidden() {
        // 系统悬浮窗模式：切出 App 后公告仍悬浮于屏幕，滚动必须继续，不暂停
        if (sOverlayActive) {
            return;
        }
        if (sView != null) {
            GLOBAL.savedScroll = Math.max(0, sView.getScrollX());
        }
        stopSpeedScroll(GLOBAL);
    }

    /**
     * 首页销毁时调用。全局公告条不在这里停止：切到酒店/点播界面时由同一个 View
     * 继续滚动（否则每次换界面都会「重新加载」）。仅清理酒店自带 ticker 的状态。
     */
    public static void stopScroll() {
        for (ScreenState ss : STATES.values()) {
            cancelAutoHide(ss);
            stopSpeedScroll(ss);
            ss.view = null;
        }
        STATES.clear();
    }

    // ==================== 渲染 ====================

    private static void render(final Activity act, final TextView tv, String screen, final ScreenState ss) {
        final TextView prevView = ss.view;
        if (prevView != null && prevView != tv && prevView.getScrollX() > 0) {
            ss.savedScroll = prevView.getScrollX();
        }
        ss.view = tv;
        String notice = RemoteConfig.notice();
        JSONObject st = RemoteConfig.noticeStyle();

        // ① 空内容 / 当前界面未开启显示 → 隐藏，并清空内容标记（下次出现时重新计时）
        if (notice == null || notice.trim().isEmpty() || !enabledFor(screen, st)) {
            ss.content = null;
            hideScreen(ss);
            return;
        }
        // ② 过期时间（yyyy-MM-dd，当天结束前仍显示）
        String expire = st.optString("expire", "").trim();
        if (!expire.isEmpty()) {
            try {
                Date exp = new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(expire);
                if (exp != null && new Date().after(new Date(exp.getTime() + 24L * 3600 * 1000 - 1))) {
                    ss.content = null;
                    hideScreen(ss);
                    return;
                }
            } catch (Throwable ignored) {
            }
        }
        // ③ 每日显示时间段（HH:mm，支持跨零点）
        String timeStart = st.optString("timeStart", "").trim();
        String timeEnd = st.optString("timeEnd", "").trim();
        if (!timeStart.isEmpty() && !timeEnd.isEmpty()) {
            try {
                SimpleDateFormat hf = new SimpleDateFormat("HH:mm", Locale.US);
                int now = toMinute(hf.format(new Date()));
                int s = toMinute(timeStart);
                int e = toMinute(timeEnd);
                boolean in = (s <= e) ? (now >= s && now <= e) : (now >= s || now <= e);
                if (!in) {
                    ss.content = null;
                    hideScreen(ss);
                    return;
                }
            } catch (Throwable ignored) {
            }
        }

        final String text = notice.trim();
        final long now = SystemClock.uptimeMillis();
        final int duration = st.optInt("duration", 0);

        // ④ duration 计时判断：必须在「置可见」之前完成全部到期判断
        if (duration > 0) {
            if (!text.equals(ss.content)) {
                ss.content = text;
                ss.deadline = now + duration * 1000L;
                ss.expired = false;
            } else if (ss.expired) {
                hideScreen(ss);
                return;
            } else if (ss.deadline <= 0) {
                ss.deadline = now + duration * 1000L;
            }
            if (ss.deadline - now <= 0) {
                ss.expired = true;
                hideScreen(ss);
                return;
            }
        } else {
            cancelAutoHide(ss);
            ss.deadline = 0;
            ss.content = text;
            ss.expired = false;
        }

        // ⑤ 应用样式（各界面共用同一套尺寸/颜色逻辑，尺寸按当前 Activity 资源换算）
        if (!SCREEN_HOTEL.equals(screen)) {
            applyPosition(act, tv, st.optString("position", "bottom").trim());
        }
        int bg = HomeEntry.parseColor(st.optString("bgColor", ""));
        if (bg != 0) {
            tv.setBackgroundColor(bg);
        }
        int fontColor = HomeEntry.parseColor(st.optString("fontColor", ""));
        if (fontColor != 0) {
            tv.setTextColor(fontColor);
        }
        applyFontSize(act, tv, st);
        try {
            if (st.has("alpha")) {
                tv.setAlpha(Math.max(0, Math.min(100, st.optInt("alpha", 100))) / 100f);
            }
        } catch (Throwable ignored) {
        }

        // ⑥ 滚动：speed>0 用自定义平滑滚动（由右向左）；否则回退系统 marquee。
        int speed = st.optInt("speed", 0);
        int pause = st.optInt("pause", 0);
        if (pause > 0 && speed <= 0) {
            speed = 100;
        }
        if (speed > 0) {
            tv.setSelected(false);
            tv.setEllipsize(null);
            // 全局是同一个 View：文本/速度/停顿都没变时保持当前滚动，不回到起点
            boolean sameScrolling = ss.speedRun != null && prevView == tv && ss.speed == speed
                    && ss.pause == pause && text.equals(ss.scrollText);
            if (!sameScrolling) {
                ss.scrollText = text;
                ss.speed = speed;
                ss.pause = pause;
                startSpeedScroll(tv, text, speed, pause, ss);
            }
        } else {
            ss.scrollText = null;
            ss.speed = 0;
            ss.pause = 0;
            stopSpeedScroll(ss);
            tv.setScroller(null);
            tv.scrollTo(0, 0);
            tv.setTranslationX(0f);
            tv.setText(text);
            try {
                tv.setEllipsize(android.text.TextUtils.TruncateAt.MARQUEE);
            } catch (Throwable ignored) {
            }
            tv.setSelected(true);
        }

        // ⑦ 全部检查通过，确实需要显示时才置可见
        tv.setVisibility(View.VISIBLE);

        // ⑧ 重新挂一次性自动隐藏任务：使用「剩余时间」而不是完整 duration
        if (duration > 0) {
            cancelAutoHide(ss);
            final TextView ftv = tv;
            ss.hideRun = new Runnable() {
                @Override
                public void run() {
                    ss.hideRun = null;
                    ss.deadline = 0;
                    ss.expired = true;
                    if (ftv != null) {
                        ftv.setVisibility(View.GONE);
                    }
                    stopSpeedScroll(ss);
                }
            };
            H.postDelayed(ss.hideRun, Math.max(1, ss.deadline - now));
        }
    }

    /**
     * 应用公告字号：JSON fontSize>0 时按 sp 应用；未配置时使用首页同款 ts_24 像素值。
     * 全局 View 用 Application Context 创建，尺寸仍按当前界面资源换算，保证各界面一致。
     */
    private static void applyFontSize(Activity act, TextView tv, JSONObject st) {
        try {
            int fontSize = st.optInt("fontSize", 0);
            if (fontSize > 0) {
                tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSize);
            } else {
                tv.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        act.getResources().getDimensionPixelSize(R.dimen.ts_24));
            }
        } catch (Throwable ignored) {
        }
    }

    private static void hideScreen(ScreenState ss) {
        cancelAutoHide(ss);
        stopSpeedScroll(ss);
        ss.deadline = 0;
        if (ss.view != null) {
            ss.view.setVisibility(View.GONE);
        }
    }

    private static void hideGlobal() {
        cancelAutoHide(GLOBAL);
        stopSpeedScroll(GLOBAL);
        if (sView != null) {
            sView.setVisibility(View.GONE);
        }
    }

    private static void cancelAutoHide(ScreenState ss) {
        if (ss.hideRun != null) {
            H.removeCallbacks(ss.hideRun);
            ss.hideRun = null;
        }
    }

    private static void applyPosition(Activity act, TextView tv, String position) {
        boolean top = "top".equals(position);
        try {
            int topMargin = act.getResources().getDimensionPixelSize(R.dimen.vs_20);
            int bottomMargin = act.getResources().getDimensionPixelSize(R.dimen.vs_10);
            if (tv.getLayoutParams() instanceof ConstraintLayout.LayoutParams) {
                ConstraintLayout.LayoutParams lp = (ConstraintLayout.LayoutParams) tv.getLayoutParams();
                if (top) {
                    lp.topToTop = ConstraintLayout.LayoutParams.PARENT_ID;
                    lp.bottomToBottom = ConstraintLayout.LayoutParams.UNSET;
                    lp.topMargin = topMargin;
                    lp.bottomMargin = 0;
                } else {
                    lp.topToTop = ConstraintLayout.LayoutParams.UNSET;
                    lp.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID;
                    lp.topMargin = 0;
                    lp.bottomMargin = bottomMargin;
                }
                tv.setLayoutParams(lp);
            } else if (tv.getLayoutParams() instanceof FrameLayout.LayoutParams) {
                FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) tv.getLayoutParams();
                lp.gravity = (top ? Gravity.TOP : Gravity.BOTTOM) | Gravity.CENTER_HORIZONTAL;
                lp.topMargin = top ? topMargin : 0;
                lp.bottomMargin = top ? 0 : bottomMargin;
                tv.setLayoutParams(lp);
            }
        } catch (Throwable ignored) {
        }
    }

    // ==================== 显示开关 ====================

    /** 该界面是否显示公告：读取 notice.show.home/live/vod，默认三个界面均显示 */
    private static boolean enabledFor(String screen, JSONObject st) {
        boolean def = true;
        try {
            JSONObject show = st.optJSONObject("show");
            if (show != null && show.has(screen)) {
                return show.optBoolean(screen, def);
            }
            String camel = "showOn" + Character.toUpperCase(screen.charAt(0)) + screen.substring(1);
            if (st.has(camel)) {
                return st.optBoolean(camel, def);
            }
        } catch (Throwable ignored) {
        }
        return def;
    }

    // ==================== 全局最顶层叠加层 ====================

    /** 公告条容器：窗口 decorView（FrameLayout），保证公告位于本窗口所有内容之上 */
    private static ViewGroup noticeRoot(Activity act) {
        if (act == null || act.isFinishing() || act.isDestroyed()) {
            return null;
        }
        try {
            View decor = act.getWindow().getDecorView();
            if (decor instanceof ViewGroup) {
                return (ViewGroup) decor;
            }
        } catch (Throwable ignored) {
        }
        return act.findViewById(android.R.id.content);
    }

    /**
     * 取得全局公告 View 并挂到当前窗口最顶层。
     * 同一个 View 在不同界面之间「搬家」，不重建、不重新设置文本，滚动位置保持不变。
     */
    private static TextView obtainOverlay(Activity act) {
        ViewGroup root = noticeRoot(act);
        if (root == null) {
            return sView;
        }
        try {
            if (sView == null) {
                sView = createOverlay(act);
            }
            if (sView.getParent() != root) {
                ViewParent p = sView.getParent();
                if (p instanceof ViewGroup) {
                    ((ViewGroup) p).removeView(sView);
                }
                try {
                    root.addView(sView, createParams(act));
                } catch (Throwable th) {
                    // 个别 ROM 的 decorView 不是 FrameLayout：退回内容容器（FrameLayout）
                    View content = act.findViewById(android.R.id.content);
                    if (content instanceof ViewGroup) {
                        ((ViewGroup) content).addView(sView, createParams(act));
                    }
                }
            }
            // 尺寸/内边距按当前界面（AutoSize 适配后的资源）重新换算
            int padH = act.getResources().getDimensionPixelSize(R.dimen.vs_15);
            sView.setPadding(padH, 0, padH, 0);
        } catch (Throwable ignored) {
        }
        return sView;
    }

    private static TextView createOverlay(android.content.Context ctx) {
        // 用 Application Context：全局静态 View 不持有任何 Activity
        TextView tv = new TextView(ctx.getApplicationContext());
        tv.setTag(TAG_OVERLAY);
        tv.setLayoutParams(createParams(ctx));
        tv.setSingleLine(true);
        tv.setEllipsize(android.text.TextUtils.TruncateAt.MARQUEE);
        tv.setMarqueeRepeatLimit(-1);
        tv.setGravity(Gravity.CENTER_VERTICAL);
        tv.setTextColor(0xFFFFD54F);
        tv.setBackgroundColor(0x66000000);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                ctx.getResources().getDimensionPixelSize(R.dimen.ts_24));
        tv.setFocusable(false);
        tv.setClickable(false);
        tv.setVisibility(View.GONE);
        try {
            // 抬高 Z 轴：即使后续有其它视图加入同一窗口，公告仍覆盖在最上层
            tv.setElevation(1000f);
        } catch (Throwable ignored) {
        }
        return tv;
    }

    private static FrameLayout.LayoutParams createParams(android.content.Context ctx) {
        // 公告条占满屏幕整宽（无左右边距）
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ctx.getResources().getDimensionPixelSize(R.dimen.vs_40));
        lp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        lp.bottomMargin = ctx.getResources().getDimensionPixelSize(R.dimen.vs_10);
        return lp;
    }

    // ==================== 自定义速度滚动（由右向左） ====================

    /**
     * 启动「由右向左」的自定义平滑滚动。
     *
     * <p>实现方式：把原文重复拼接若干份（保证任意时刻屏内都有文字），再用 Scroller 平移一个
     * 周期后归零重来，形成无缝循环。像素速度由 JSON speed 配置（像素/秒）。</p>
     */
    private static void startSpeedScroll(final TextView tv, final String text,
                                         final int speedPxPerSec, final int pauseSec, final ScreenState ss) {
        stopSpeedScroll(ss);
        tv.setScroller(null);
        tv.scrollTo(0, 0);
        tv.setTranslationX(0f);
        tv.setText(text);
        if (pauseSec > 0) {
            startPauseScroll(tv, text, speedPxPerSec, pauseSec, ss);
            return;
        }
        final int token = ++ss.scrollToken;
        H.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (ss.scrollToken != token) {
                    return;
                }
                // 视图暂时不在窗口上（界面切换中）：稍后重试，保持同一条滚动循环
                if (!tv.isAttachedToWindow() || tv.getWidth() <= 0) {
                    H.postDelayed(this, 200);
                    return;
                }
                int viewWidth = tv.getWidth();
                String unit = text + "        ";
                float unitWidth = tv.getPaint().measureText(unit);
                if (unitWidth <= 0) {
                    return;
                }
                int copies = 1;
                while (unitWidth * copies < viewWidth + unitWidth) {
                    copies++;
                }
                StringBuilder sb = new StringBuilder(unit.length() * copies);
                for (int i = 0; i < copies; i++) {
                    sb.append(unit);
                }
                tv.setText(sb.toString());
                final int periodPx = (int) Math.ceil(unitWidth);
                final int duration = (int) (periodPx / (float) Math.max(1, speedPxPerSec) * 1000f);
                int startOffset = Math.min(Math.max(ss.savedScroll, 0), Math.max(0, periodPx - 1));
                ss.savedScroll = 0;
                final Scroller scroller = new Scroller(tv.getContext(), new LinearInterpolator());
                tv.setScroller(scroller);
                tv.scrollTo(startOffset, 0);
                scroller.startScroll(startOffset, 0, periodPx - startOffset, 0,
                        Math.max(1, (int) (duration * (periodPx - startOffset) / (float) periodPx)));
                final Runnable loop = new Runnable() {
                    @Override
                    public void run() {
                        if (ss.speedRun != this) {
                            return;
                        }
                        if (!tv.isAttachedToWindow()) {
                            // 界面切换的短暂空档：保持循环，不重置位置
                            H.postDelayed(this, 300);
                            return;
                        }
                        if (scroller.isFinished()) {
                            tv.scrollTo(0, 0);
                            scroller.startScroll(0, 0, periodPx, 0, Math.max(1, duration));
                        }
                        H.postDelayed(this, 300);
                    }
                };
                ss.speedRun = loop;
                H.postDelayed(loop, 300);
            }
        }, 60);
    }

    /**
     * 「整条走完 + 停顿」模式：公告文本从右侧完全进入，向左完整走出一遍后，
     * 停顿 notice.pause 秒，再重复。
     */
    private static void startPauseScroll(final TextView tv, final String text,
                                         final int speedPxPerSec, final int pauseSec, final ScreenState ss) {
        final int token = ++ss.scrollToken;
        H.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (ss.scrollToken != token) {
                    return;
                }
                final int viewWidth = tv.getWidth();
                if (!tv.isAttachedToWindow() || viewWidth <= 0) {
                    H.postDelayed(this, 200);
                    return;
                }
                final float textWidth = tv.getPaint().measureText(text);
                if (textWidth <= 0) {
                    return;
                }
                // 前导空格：宽度 ≥ 屏宽，使文字初始位于屏幕右侧外
                StringBuilder lead = new StringBuilder();
                float oneSpace = tv.getPaint().measureText(" ");
                int spaces = oneSpace > 0 ? (int) Math.ceil(viewWidth / oneSpace) + 1 : 0;
                for (int i = 0; i < spaces; i++) {
                    lead.append(' ');
                }
                final String padded = lead.toString() + text;
                final float leadWidth = tv.getPaint().measureText(lead.toString());
                final int toScroll = (int) Math.ceil(leadWidth + textWidth);
                final int duration = (int) (toScroll / (float) Math.max(1, speedPxPerSec) * 1000f);
                final int[] fromHolder = {
                        Math.min(Math.max(ss.savedScroll, 0), Math.max(0, toScroll - 1))
                };
                ss.savedScroll = 0;
                final Runnable[] loopRef = new Runnable[1];
                final Runnable loop = new Runnable() {
                    @Override
                    public void run() {
                        if (ss.speedRun != this) {
                            return;
                        }
                        if (!tv.isAttachedToWindow()) {
                            H.postDelayed(this, 300);
                            return;
                        }
                        tv.setText(padded);
                        tv.scrollTo(fromHolder[0], 0);
                        ValueAnimator va = ValueAnimator.ofInt(fromHolder[0], toScroll);
                        fromHolder[0] = 0;
                        va.setDuration(Math.max(1, duration));
                        va.setInterpolator(new LinearInterpolator());
                        va.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                            @Override
                            public void onAnimationUpdate(ValueAnimator animation) {
                                tv.scrollTo((Integer) animation.getAnimatedValue(), 0);
                            }
                        });
                        va.addListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                if (ss.scrollAnim == animation) {
                                    ss.scrollAnim = null;
                                }
                                if (ss.speedRun != loopRef[0]) {
                                    return;
                                }
                                H.postDelayed(loopRef[0], Math.max(0, pauseSec) * 1000L);
                            }
                        });
                        ss.scrollAnim = va;
                        va.start();
                    }
                };
                loopRef[0] = loop;
                ss.speedRun = loop;
                loop.run();
            }
        }, 60);
    }

    private static void stopSpeedScroll(ScreenState ss) {
        ss.scrollToken++;   // 让尚未执行的延时任务作废
        if (ss.scrollAnim != null) {
            ss.scrollAnim.cancel();
            ss.scrollAnim = null;
        }
        if (ss.speedRun != null) {
            H.removeCallbacks(ss.speedRun);
            ss.speedRun = null;
        }
        if (ss.view != null) {
            ss.view.setTranslationX(0f);
            ss.view.scrollTo(0, 0);
        }
    }

    private static int toMinute(String hhmm) {
        String[] p = hhmm.split(":");
        return Integer.parseInt(p[0].trim()) * 60 + Integer.parseInt(p[1].trim());
    }
}
