package com.github.tvbox.osc.ui.activity;

import android.app.Activity;
import android.content.Context;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.DiffUtil;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.base.App;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.bean.SourceBean;
import com.github.tvbox.osc.event.RefreshEvent;
import com.github.tvbox.osc.server.ControlManager;
import com.github.tvbox.osc.ui.adapter.SelectDialogAdapter;
import com.github.tvbox.osc.ui.dialog.SelectDialog;
import com.github.tvbox.osc.ui.dialog.TipDialog;
import com.github.tvbox.osc.ui.fragment.UserFragment;
import com.github.tvbox.osc.util.AppManager;
import com.github.tvbox.osc.util.FastClickCheckUtil;
import com.github.tvbox.osc.util.FileUtils;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.MD5;
import com.orhanobut.hawk.Hawk;
import com.owen.tvrecyclerview.widget.V7GridLayoutManager;
import com.owen.tvrecyclerview.widget.TvRecyclerView;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import me.jessyan.autosize.utils.AutoSizeUtils;

/**
 * 启动桌面：仅保留首页内容（UserFragment），分类/影片网格已迁移至独立点播界面 VodActivity
 */
public class HomeActivity extends BaseActivity {

    // takagen99: Added to allow read string
    private static Resources res;

    /** 内部重启标记：主题一键切换等自重启时置 true，onDestroy 跳过 appExit 杀进程 */
    private static boolean sSkipAppExit = false;

    private LinearLayout topLayout;
    private LinearLayout contentLayout;
    private TextView tvName;
    private ImageView tvWifi;
    private ImageView tvFind;
    private ImageView tvStyle;
    private ImageView tvDraw;
    private ImageView tvN2n;
    private TextView tvDate;
    private UserFragment userFragment;
    private final Handler mHandler = new Handler();
    private long mExitTime = 0;
    private final Runnable mRunnable = new Runnable() {
        @SuppressLint({"DefaultLocale", "SetTextI18n"})
        @Override
        public void run() {
            Date date = new Date();
            @SuppressLint("SimpleDateFormat")
			//修改时间分隔符
            SimpleDateFormat timeFormat = new SimpleDateFormat(getString(R.string.hm_date1) + " | " + getString(R.string.hm_date2));
            tvDate.setText(timeFormat.format(date));
            mHandler.postDelayed(this, 1000);
        }
    };

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_home;
    }

    boolean useCacheConfig = false;

    @Override
    protected void init() {
        // takagen99: Added to allow read string
        res = getResources();

        EventBus.getDefault().register(this);
        // 远程控制/局域网 Web 服务端口绑定与 AndServer 路由扫描较慢，移到后台线程，不阻塞首页首帧
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    ControlManager.get().startServer();
                } catch (Throwable ignored) {
                }
                try {
                    App.startWebserver();
                } catch (Throwable ignored) {
                }
            }
        }, "home-servers").start();
        initView();
        useCacheConfig = false;
        Intent intent = getIntent();
        if (intent != null && intent.getExtras() != null) {
            Bundle bundle = intent.getExtras();
            useCacheConfig = bundle.getBoolean("useCache", false);
        }
        initData();
    }

    // takagen99: Added to allow read string
    public static Resources getRes() {
        return res;
    }

    private void initView() {
        this.topLayout = findViewById(R.id.topLayout);
        this.tvName = findViewById(R.id.tvName);
        this.tvWifi = findViewById(R.id.tvWifi);
        this.tvFind = findViewById(R.id.tvFind);
        this.tvStyle = findViewById(R.id.tvStyle);
        this.tvDraw = findViewById(R.id.tvDrawer);
        this.tvN2n = findViewById(R.id.tvN2n);
        this.tvDate = findViewById(R.id.tvDate);
        this.contentLayout = findViewById(R.id.contentLayout);
        // 首页左上角名称：仅作为标题展示，不再作为「切换点播源」入口。
        // 2026-10-23 按需求取消该入口及其焦点：TV 遥控器上下左右不会再落到这里，
        // 切源统一走 MENU 键 / 设置页；长按清缓存也一并移除（避免误触导致重载）。
        tvName.setClickable(false);
        tvName.setFocusable(false);
        tvName.setFocusableInTouchMode(false);
        tvName.setOnClickListener(null);
        tvName.setOnLongClickListener(null);
        // Button : Wifi >> Go into Android Wifi Settings -------------
        tvWifi.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
                }catch (Exception ignored){
                }
            }
        });
        // Button : Search --------------------------------------------
        tvFind.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                jumpActivity(SearchActivity.class);
            }
        });
        // Button : Style --------------------------------------------
        tvStyle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    Hawk.put(HawkConfig.HOME_REC_STYLE, !Hawk.get(HawkConfig.HOME_REC_STYLE, true));
                    if (Hawk.get(HawkConfig.HOME_REC_STYLE, true)) {
                        UserFragment.tvHotListForGrid.setVisibility(View.VISIBLE);
                        UserFragment.tvHotListForLine.setVisibility(View.GONE);
                        Toast.makeText(HomeActivity.this, getString(R.string.hm_style_grid), Toast.LENGTH_SHORT).show();
                        tvStyle.setImageResource(R.drawable.hm_up_down);
                    } else {
                        UserFragment.tvHotListForGrid.setVisibility(View.GONE);
                        UserFragment.tvHotListForLine.setVisibility(View.VISIBLE);
                        Toast.makeText(HomeActivity.this, getString(R.string.hm_style_line), Toast.LENGTH_SHORT).show();
                        tvStyle.setImageResource(R.drawable.hm_left_right);
                    }
                } catch (Exception ex) {
                }
            }
        });
        // Button : Drawer >> To go into App Drawer -------------------
        tvDraw.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                jumpActivity(AppsActivity.class);
            }
        });
        // Button : Settings >> 已按需求移除应用内设置入口，全部配置改由 JSON 下发。
        tvN2n.setVisibility(View.GONE);
        tvN2n.setOnClickListener(null);
        tvN2n.setFocusable(false);
        // Button : Theme >> 一键切换主题配色（远程 JSON 也可下发默认主题）
        findViewById(R.id.tvTheme).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                FastClickCheckUtil.check(view);
                switchTheme(HomeActivity.this, useCacheConfig);
            }
        });
        // Button : Date >> Go into Android Date Settings --------------
        tvDate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(Settings.ACTION_DATE_SETTINGS));
            }
        });
        // 中间一行入口（历史/点播/直播/…）与下方内容区立即默认加载：
        // 主页面不再引用点播接口数据，推荐内容改由主接口（远程配置 home.recMovies）下发；
        // 未配置点播接口（仅直播）时，入口行也和顶部一行一样立即可用。
        try {
            userFragment = UserFragment.newInstance();
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.homeFragmentContainer, userFragment)
                    .commitNowAllowingStateLoss();
        } catch (Throwable th) {
            th.printStackTrace();
        }
        setLoadSir(this.contentLayout);
        // 立即切成成功态：保证 LoadSir 注册后内容区（含「历史」入口行）从首帧就可见，
        // 与顶部标题行同一时机加载。
        showSuccess();
        // 首页扩展（hin2n 入口 / 自定义入口栏 / 滚动公告）：放在内容区创建之后并整段兜底，
        // 扩展初始化异常不能中断 initView，否则 UserFragment 未创建、内容区空白表现为黑屏
        try {
            com.github.tvbox.osc.n2n.HomeEntry.refresh(this);
        } catch (Throwable th) {
            th.printStackTrace();
        }
    }

    //站点切换
    public static void homeRecf() {
        int homeRec = Hawk.get(HawkConfig.HOME_REC, -1);
        int limit = 2;
        if (homeRec == limit) homeRec = -1;
        homeRec++;
        Hawk.put(HawkConfig.HOME_REC, homeRec);
    }

    /**
     * 一键切换主题（顶栏 tvTheme 与 JSON feature=themeSwitch 入口共用）。
     * recreate()/finish 重启会触发 onDestroy 的 appExit 杀进程（表现为"切主题=退出应用"），
     * 内部重启前置 sSkipAppExit，让本次 onDestroy 跳过杀进程，实现平滑换肤。
     * 切主题只刷新界面 UI、不刷新数据：useCache 强制为 true，点播接口配置与 spider jar
     * 均走本地缓存，homeContent 命中 SourceViewModel.sortCache 内存缓存（进程未杀，缓存仍在）。
     */
    public static void switchTheme(Activity act, boolean useCache) {
        int idx = com.github.tvbox.osc.n2n.ThemeManager.cycle();
        Toast.makeText(act, "主题：" + com.github.tvbox.osc.n2n.ThemeManager.name(idx), Toast.LENGTH_SHORT).show();
        sSkipAppExit = true;
        com.github.tvbox.osc.base.BaseActivity.resetWallpaper();
        Intent it = new Intent(act, HomeActivity.class);
        it.putExtra("useCache", true);
        act.finish();
        act.startActivity(it);
        act.overridePendingTransition(0, 0);
    }

    /**
     * 清理缓存并重载首页（tvName 长按与 JSON feature=clearCache 入口共用）：
     * 清缓存目录 → 删除当前源 csp jar 缓存 → reloadHome。
     */
    public void clearHomeCache() {
        File dir = getCacheDir();
        FileUtils.recursiveDelete(dir);
        dir = getExternalCacheDir();
        FileUtils.recursiveDelete(dir);
        Toast.makeText(HomeActivity.this, getString(R.string.hm_cache_del), Toast.LENGTH_SHORT).show();
        if (dataInitOk && jarInitOk && ApiConfig.get().isConfigLoaded()
                && ApiConfig.get().getHomeSourceBean() != null) {
            String cspCachePath = FileUtils.getFilePath() + "/csp/";
            String jar = ApiConfig.get().getHomeSourceBean().getJar();
            String jarUrl = !jar.isEmpty() ? jar : ApiConfig.get().getSpider();
            File cspCacheDir = new File(cspCachePath + MD5.string2MD5(jarUrl) + ".jar");
            if (!cspCacheDir.exists()) {
                reloadHome();
                return;
            }
            new Thread(() -> {
                try {
                    FileUtils.deleteFile(cspCacheDir);
                    ApiConfig.get().clearJarLoader();
                    reloadHome();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }).start();
        }
    }

    public static boolean reHome(Context appContext) {
        Intent intent = new Intent(appContext, HomeActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
        Bundle bundle = new Bundle();
        bundle.putBoolean("useCache", true);
        intent.putExtras(bundle);
        appContext.startActivity(intent);
        return true;
    }

    /**
     * 左上角名称显示规则（2026-10-22 起固定为纯标题）：
     * 始终显示主接口 json「home.sourceName」配置的文字，不再随点播接口/当前源切换而变化；
     * sourceName 为空时回退显示 app 名称。
     * 自 2026-10-23 起该标题已取消点击与焦点，仅作展示（切源入口从首页移除）。
     */
    private void applySourceName() {
        String sn = com.github.tvbox.osc.n2n.RemoteConfig.homeSourceName();
        if (sn.isEmpty()) {
            tvName.setText(R.string.app_name);
        } else {
            tvName.setText(sn);
        }
        tvName.clearAnimation();
    }

    /** 供首页入口样式刷新时调用：把最新主题色/远程 iconColor 应用到历史一行 */
    public void refreshUserEntryStyle() {
        if (userFragment != null) {
            userFragment.refreshEntryIconStyle();
        }
    }

    // ★ 改为 static：进程级跟踪，主题切换重启 Activity（进程未杀）时直接跳过 loadConfig/loadJar，
    // 大幅缩短 Activity 重启后的首帧显示时间。冷启动时类加载初始化 false，正常加载后置 true。
    private static boolean dataInitOk = false;
    private static boolean jarInitOk = false;

    /** 直播模式每次启动只跳转一次，避免 initData 多次回调重复打开直播页 */
    private static boolean liveModeJumped = false;

    /** 开启「启动时进直播」时跳到直播页（关闭时保持首页） */
    private void jumpToLiveIfNeeded() {
        if (liveModeJumped) {
            return;
        }
        if (!Hawk.get(HawkConfig.HOME_DEFAULT_SHOW, false)) {
            return;
        }
        liveModeJumped = true;
        jumpActivity(LivePlayActivity.class);
    }

    // takagen99 : Switch to show / hide source title
    boolean HomeShow = Hawk.get(HawkConfig.HOME_SHOW_SOURCE, false);

    // takagen99 : Check if network is available
    boolean isNetworkAvailable() {
        ConnectivityManager cm
                = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = cm.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnectedOrConnecting();
    }

    private void initData() {
        // takagen99: If network available, check connected Wifi or Lan
        // 网络状态瞬时变化时 getActiveNetworkInfo() 可能为 null（弱网/切换瞬间），需空值保护
        try {
            if (isNetworkAvailable()) {
                ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
                NetworkInfo ni = cm == null ? null : cm.getActiveNetworkInfo();
                if (ni != null) {
                    if (ni.getType() == ConnectivityManager.TYPE_WIFI) {
                        tvWifi.setImageDrawable(res.getDrawable(R.drawable.hm_wifi));
                    } else if (ni.getType() == ConnectivityManager.TYPE_MOBILE) {
                        tvWifi.setImageDrawable(res.getDrawable(R.drawable.hm_mobile));
                    } else if (ni.getType() == ConnectivityManager.TYPE_ETHERNET) {
                        tvWifi.setImageDrawable(res.getDrawable(R.drawable.hm_lan));
                    }
                }
            }
        } catch (Throwable th) {
            th.printStackTrace();
        }

        // takagen99: Set Style either Grid or Line
        if (Hawk.get(HawkConfig.HOME_REC_STYLE, true)) {
            tvStyle.setImageResource(R.drawable.hm_up_down);
        } else {
            tvStyle.setImageResource(R.drawable.hm_left_right);
        }

        // 首页只依赖主接口（主配置 RemoteConfig），点播接口的所有字段/插件/提示均延迟到点播界面加载。
        // 主接口仍在加载中：等结果回调后再决定；主接口失败且无缓存：直接弹全屏报错，不再加载首页内容。
        if (!dataInitOk) {
            int state = com.github.tvbox.osc.n2n.RemoteConfig.primaryState();
            if (state == com.github.tvbox.osc.n2n.RemoteConfig.PRIMARY_LOADING) {
                addPrimaryListenerOnce();
                return;
            }
            if (state == com.github.tvbox.osc.n2n.RemoteConfig.PRIMARY_FAIL) {
                showPrimaryConfigError();
                return;
            }
            dataInitOk = true;
            jarInitOk = true;
        }
        // 主接口通过 screens/defaultScreen 指定加载的界面（酒店 IPTV 桌面等）
        jumpToScreenIfNeeded();
        jumpToLiveIfNeeded();
        upgradeCheckOnce();
        // 主接口就绪后（首次启动配置为异步加载）也能及时弹出悬浮窗权限引导
        promptNoticeOverlayOnce();
    }

    /** 非 standard 默认界面只跳转一次，避免主接口回调重复进入 */
    private static boolean screenJumped = false;

    private void jumpToScreenIfNeeded() {
        if (screenJumped) {
            return;
        }
        screenJumped = true;
        String id = com.github.tvbox.osc.n2n.RemoteConfig.defaultScreenId();
        if (id == null || id.isEmpty() || "standard".equals(id)) {
            return;
        }
        com.github.tvbox.osc.n2n.RemoteConfig.Screen s = com.github.tvbox.osc.n2n.RemoteConfig.findScreen(id);
        if (s == null || "standard".equalsIgnoreCase(s.layout)) {
            return;
        }
        com.github.tvbox.osc.n2n.RemoteConfig.applyScreenEndpoints(s);
        sSkipAppExit = true;
        Intent intent = new Intent(this, com.github.tvbox.osc.ui.activity.HotelActivity.class);
        intent.putExtra("screenId", s.id);
        startActivity(intent);
        finish();
        overridePendingTransition(0, 0);
    }

    /** 界面（屏幕）切换：列出主接口 screens 配置的多个界面 */
    public void showScreenSwitch() {
        List<com.github.tvbox.osc.n2n.RemoteConfig.Screen> screens =
                com.github.tvbox.osc.n2n.RemoteConfig.screens();
        if (screens.isEmpty()) {
            Toast.makeText(this, "主接口未配置 screens 界面列表", Toast.LENGTH_SHORT).show();
            return;
        }
        SelectDialog<com.github.tvbox.osc.n2n.RemoteConfig.Screen> dialog = new SelectDialog<>(this);
        TvRecyclerView tvRecyclerView = dialog.findViewById(R.id.list);
        tvRecyclerView.setLayoutManager(new V7GridLayoutManager(dialog.getContext(), 1));
        dialog.setTip("选择界面");
        dialog.setAdapter(tvRecyclerView, new SelectDialogAdapter.SelectDialogInterface<com.github.tvbox.osc.n2n.RemoteConfig.Screen>() {
            @Override
            public void click(com.github.tvbox.osc.n2n.RemoteConfig.Screen value, int pos) {
                com.github.tvbox.osc.n2n.RemoteConfig.applyScreenEndpoints(value);
                // layout=standard 的界面回到标准首页
                openScreen("standard".equalsIgnoreCase(value.layout) ? "standard" : value.id);
            }

            @Override
            public String getDisplay(com.github.tvbox.osc.n2n.RemoteConfig.Screen val) {
                return val.name == null || val.name.isEmpty() ? val.id : val.name;
            }
        }, new DiffUtil.ItemCallback<com.github.tvbox.osc.n2n.RemoteConfig.Screen>() {
            @Override
            public boolean areItemsTheSame(@NonNull @NotNull com.github.tvbox.osc.n2n.RemoteConfig.Screen oldItem,
                                           @NonNull @NotNull com.github.tvbox.osc.n2n.RemoteConfig.Screen newItem) {
                return oldItem.id.equals(newItem.id);
            }

            @Override
            public boolean areContentsTheSame(@NonNull @NotNull com.github.tvbox.osc.n2n.RemoteConfig.Screen oldItem,
                                              @NonNull @NotNull com.github.tvbox.osc.n2n.RemoteConfig.Screen newItem) {
                return oldItem.id.equals(newItem.id);
            }
        }, screens, 0);
        dialog.show();
    }

    /** 打开指定界面：standard=首页，其它=酒店桌面 Activity */
    private void openScreen(String id) {
        sSkipAppExit = true;
        Intent intent;
        if (id == null || id.isEmpty() || "standard".equals(id)) {
            intent = new Intent(this, HomeActivity.class);
        } else {
            intent = new Intent(this, com.github.tvbox.osc.ui.activity.HotelActivity.class);
            intent.putExtra("screenId", id);
        }
        finish();
        startActivity(intent);
        overridePendingTransition(0, 0);
    }

    /** 主接口结果监听只注册一次（主题切换重启时进程内状态已为 OK，不再重复注册） */
    private boolean primaryListenerAdded = false;

    private void addPrimaryListenerOnce() {
        if (primaryListenerAdded) {
            return;
        }
        primaryListenerAdded = true;
        com.github.tvbox.osc.n2n.RemoteConfig.addPrimaryListener(new com.github.tvbox.osc.n2n.RemoteConfig.PrimaryListener() {
            @Override
            public void onPrimaryResult(int state) {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }
                        if (state == com.github.tvbox.osc.n2n.RemoteConfig.PRIMARY_FAIL) {
                            showPrimaryConfigError();
                        } else {
                            initData();
                        }
                    }
                });
            }
        });
    }

    /** 主接口无法加载/访问且无本地缓存：全屏报错，阻止进入其它界面 */
    private void showPrimaryConfigError() {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        // 保留进程给报错界面（否则 HomeActivity.onDestroy 会直接退出 App，报错页无法展示）
        sSkipAppExit = true;
        Intent intent = new Intent(this, ConfigErrorActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        finish();
    }

    /** 每次进程只做一次远程升级检查（原逻辑跟在点播接口加载成功后，现改为主接口就绪后） */
    private static boolean sUpgradeChecked = false;

    private void upgradeCheckOnce() {
        if (sUpgradeChecked) {
            return;
        }
        sUpgradeChecked = true;
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!isFinishing() && !isDestroyed()) {
                    com.github.tvbox.osc.n2n.UpgradeChecker.check(HomeActivity.this);
                }
            }
        }, 3000);
    }


    /** 免责声明是否已在本进程内弹过（每次启动只弹一次） */
    private static boolean sWarningShown = false;

    /** 免责声明弹窗自动关闭倒计时任务（null=当前没有进行中的倒计时） */
    private Runnable mWarningTick;

    /**
     * 点播接口配置的 warningText 生效：配置加载成功后弹一次免责声明。
     *
     * <p>2026-10-23 需求 6：弹窗支持「自定义秒数后自动关闭」——秒数取自 json 顶层
     * {@code warningAutoClose}（RemoteConfig.warningAutoClose）：</p>
     * <ul>
     *   <li>0（默认）：保持原行为，必须点「确定」才关闭；</li>
     *   <li>N&gt;0：确定按钮显示「确定 (Ns)」并每秒递减，到点自动关闭，不需要任何按键；
     *       适合无人值守的电视/机顶盒，避免免责声明长期挡住首页。</li>
     * </ul>
     */
    private void showWarningTextOnce() {
        if (sWarningShown || isFinishing() || isDestroyed()) return;
        String warningText = Hawk.get(HawkConfig.WARNING_TEXT, "");
        if (warningText == null || warningText.trim().isEmpty()) return;
        sWarningShown = true;
        try {
            final TipDialog[] holder = new TipDialog[1];
            holder[0] = new TipDialog(HomeActivity.this, warningText, getString(R.string.hm_ok), "", new TipDialog.OnListener() {
                @Override
                public void left() {
                    if (holder[0] != null) holder[0].dismiss();
                }

                @Override
                public void right() {
                }

                @Override
                public void cancel() {
                }
            });
            // 弹窗被手动确认/取消/系统关闭时，立即停止倒计时，避免残留任务继续跑
            holder[0].setOnDismissListener(new android.content.DialogInterface.OnDismissListener() {
                @Override
                public void onDismiss(android.content.DialogInterface dialog) {
                    stopWarningCountdown();
                }
            });
            holder[0].show();
            // 自定义秒数自动关闭：倒计时显示在「确定」按钮上，到点自动 dismiss
            final int autoClose = com.github.tvbox.osc.n2n.RemoteConfig.warningAutoClose();
            if (autoClose > 0) {
                final android.widget.TextView leftBtn = holder[0].findViewById(R.id.leftBtn);
                final int[] leftSec = {autoClose};
                updateWarningButton(leftBtn, autoClose);
                mWarningTick = new Runnable() {
                    @Override
                    public void run() {
                        if (isFinishing() || isDestroyed() || !holder[0].isShowing()) {
                            return;
                        }
                        leftSec[0]--;
                        if (leftSec[0] <= 0) {
                            holder[0].dismiss();
                            return;
                        }
                        updateWarningButton(leftBtn, leftSec[0]);
                        mHandler.postDelayed(this, 1000);
                    }
                };
                mHandler.postDelayed(mWarningTick, 1000);
            }
        } catch (Throwable th) {
            th.printStackTrace();
        }
    }

    /** 刷新免责声明「确定」按钮上的剩余秒数：确定 (Ns) */
    private void updateWarningButton(android.widget.TextView btn, int seconds) {
        if (btn != null) {
            btn.setText(getString(R.string.hm_ok) + " (" + seconds + "s)");
        }
    }

    /** 停止免责声明倒计时（幂等） */
    private void stopWarningCountdown() {
        if (mWarningTick != null) {
            mHandler.removeCallbacks(mWarningTick);
            mWarningTick = null;
        }
    }

    @Override
    public void onBackPressed() {
        //打断加载
        if(isLoading()){
            refreshEmpty();
            return;
        }
        // 如果处于 VOD 删除模式，则退出该模式并刷新界面
        if (HawkConfig.hotVodDelete) {
            HawkConfig.hotVodDelete = false;
            UserFragment.homeHotVodAdapter.notifyDataSetChanged();
            return;
        }

        // 首页热门列表已滚动时，先回到顶部
        if (UserFragment.tvHotListForGrid != null && UserFragment.tvHotListForGrid.canScrollVertically(-1)) {
            UserFragment.tvHotListForGrid.scrollToPosition(0);
            return;
        }

        doExit();
    }

    private void doExit() {
        // 如果两次返回间隔小于 2000 毫秒，则退出应用
        if (System.currentTimeMillis() - mExitTime < 2000) {
            AppManager.getInstance().finishAllActivity();
            EventBus.getDefault().unregister(this);
            ControlManager.get().stopServer();
            finish();
            android.os.Process.killProcess(android.os.Process.myPid());
            System.exit(0);
        } else {
            // 否则仅提示用户，再按一次退出应用
            mExitTime = System.currentTimeMillis();
            Toast.makeText(mContext, getString(R.string.hm_exit), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        com.github.tvbox.osc.n2n.HomeEntry.refresh(this);
        // APK 下载完成但缺「未知来源」权限、从系统设置返回后自动继续安装
        com.github.tvbox.osc.n2n.ApkDownloader.resumePendingInstall(this);

        // takagen99 : Switch to show / hide source title
        applySourceName();

        // takagen99: Icon Placement（JSON topEntries 整体替换顶栏时，固定图标显隐由 HomeEntry 全权控制）
        if (com.github.tvbox.osc.n2n.RemoteConfig.topEntries() == null) {
            if (Hawk.get(HawkConfig.HOME_SEARCH_POSITION, true)) {
                tvFind.setVisibility(View.VISIBLE);
            } else {
                tvFind.setVisibility(View.GONE);
            }
        }
        mHandler.post(mRunnable);

        // 滚动公告：首次运行时引导开启「悬浮窗」权限，使公告能显示在所有 App 最顶层。
        promptNoticeOverlayOnce();

        // 桌面模式在清单中直接声明 HOME（参考 MQLauncher），不再在应用内引导选择桌面

    }

    /**
     * 滚动公告悬浮窗权限引导（每次安装只弹一次）。
     *
     * <p>有 {@code SYSTEM_ALERT_WINDOW} 权限时公告由 {@code NoticeOverlayService} 挂到系统
     * 最顶层，切换界面/切出本 App 都不中断；无权限时退化为当前窗口内公告。授权后无需重启，
     * 下次界面 resume 时服务会自动接管。</p>
     */
    private void promptNoticeOverlayOnce() {
        try {
            if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.M) {
                return;
            }
            if (Settings.canDrawOverlays(this)) {
                return;
            }
            String notice = com.github.tvbox.osc.n2n.RemoteConfig.notice();
            if (notice == null || notice.trim().isEmpty()) {
                return;
            }
            if (Hawk.get("notice_overlay_prompted", false)) {
                return;
            }
            Hawk.put("notice_overlay_prompted", true);
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    try {
                        final TipDialog[] holder = new TipDialog[1];
                        holder[0] = new TipDialog(HomeActivity.this,
                                "开启「悬浮窗」权限后，滚动公告可显示在所有应用最顶层，切换界面或应用时不会中断。",
                                "去开启", "暂不", new TipDialog.OnListener() {
                            @Override
                            public void left() {
                                if (holder[0] != null) {
                                    holder[0].dismiss();
                                }
                                openNoticeOverlaySettings();
                            }

                            @Override
                            public void right() {
                                if (holder[0] != null) {
                                    holder[0].dismiss();
                                }
                            }

                            @Override
                            public void cancel() {
                            }
                        });
                        holder[0].show();
                    } catch (Throwable ignored) {
                    }
                }
            }, 1500);
        } catch (Throwable ignored) {
        }
    }

    /** 打开本应用的「显示在其他应用上层」系统设置页；厂商 ROM 无此页面时回退应用详情页 */
    private void openNoticeOverlaySettings() {
        try {
            Intent it = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    android.net.Uri.parse("package:" + getPackageName()));
            startActivity(it);
        } catch (Throwable th) {
            try {
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        android.net.Uri.parse("package:" + getPackageName())));
            } catch (Throwable ignored) {
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // 页面不可见时先移除浮层通知与倒计时：onPause 会清空 Handler 队列，
        // 若只清队列不移除视图，返回首页后顶部提示会残留不再消失。
        hideLoadSuccessNotice();
        stopWarningCountdown();
        mHandler.removeCallbacksAndMessages(null);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void refresh(RefreshEvent event) {
        if (event.type == RefreshEvent.TYPE_PUSH_URL) {
            if (ApiConfig.get().getSource("push_agent") != null) {
                Intent newIntent = new Intent(mContext, DetailActivity.class);
                newIntent.putExtra("id", (String) event.obj);
                newIntent.putExtra("sourceKey", "push_agent");
                newIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                HomeActivity.this.startActivity(newIntent);
            }
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (event.getKeyCode() == KeyEvent.KEYCODE_MENU) {
                showSiteSwitch();
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    protected void onDestroy() {
        // 先移除未执行完的加载成功通知倒计时，避免 Activity 销毁后 Runnable 仍持有引用
        hideLoadSuccessNotice();
        // 免责声明自动关闭倒计时同样需要停止
        stopWarningCountdown();
        super.onDestroy();
        EventBus.getDefault().unregister(this);
        if (sSkipAppExit) {
            // 内部重启（主题一键切换）：保留进程，由新实例接管
            sSkipAppExit = false;
        } else {
            AppManager.getInstance().appExit(0);
            ControlManager.get().stopServer();
        }
        com.github.tvbox.osc.n2n.HomeEntry.stopTicker();
    }

    // Site Switch on Home Button（JSON feature=siteSwitch 入口也调用，需 public）
    public void showSiteSwitch() {
        List<SourceBean> sites = new ArrayList<>();
        for (SourceBean sb : ApiConfig.get().getSourceBeanList()) {
            if (sb.getHide() == 0) sites.add(sb);
        }
        if (sites.size() > 0) {
            SelectDialog<SourceBean> dialog = new SelectDialog<>(HomeActivity.this);

            // Multi Column Selection
            int spanCount = (int) Math.floor(sites.size() / 10);
            if (spanCount <= 1) spanCount = 1;
            if (spanCount >= 3) spanCount = 3;

            TvRecyclerView tvRecyclerView = dialog.findViewById(R.id.list);
            tvRecyclerView.setLayoutManager(new V7GridLayoutManager(dialog.getContext(), spanCount));
            ConstraintLayout cl_root = dialog.findViewById(R.id.cl_root);
            ViewGroup.LayoutParams clp = cl_root.getLayoutParams();
            if (spanCount != 1) {
                clp.width = AutoSizeUtils.mm2px(dialog.getContext(), 400 + 260 * (spanCount - 1));
            }

            dialog.setTip(getString(R.string.dia_source));
            dialog.setAdapter(tvRecyclerView, new SelectDialogAdapter.SelectDialogInterface<SourceBean>() {
                @Override
                public void click(SourceBean value, int pos) {
                    ApiConfig.get().setSourceBean(value);
                    // 主页面不再引用点播接口数据，切源后无需重建首页；
                    // 点播内容在点播界面内切换源时自行刷新
                    Toast.makeText(HomeActivity.this, "已切换到「" + value.getName() + "」", Toast.LENGTH_SHORT).show();
                }

                @Override
                public String getDisplay(SourceBean val) {
                    return val.getName();
                }
            }, new DiffUtil.ItemCallback<SourceBean>() {
                @Override
                public boolean areItemsTheSame(@NonNull @NotNull SourceBean oldItem, @NonNull @NotNull SourceBean newItem) {
                    return oldItem == newItem;
                }

                @Override
                public boolean areContentsTheSame(@NonNull @NotNull SourceBean oldItem, @NonNull @NotNull SourceBean newItem) {
                    return oldItem.getKey().equals(newItem.getKey());
                }
            }, sites, sites.indexOf(ApiConfig.get().getHomeSourceBean()));
            dialog.show();
        }
    }

    /** 重新加载首页（JSON feature=reloadHome 入口也调用，需 public） */
    public void reloadHome() {
        sSkipAppExit = true;
        Intent intent = new Intent(getApplicationContext(), HomeActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
        Bundle bundle = new Bundle();
        bundle.putBoolean("useCache", true);
        intent.putExtras(bundle);
        HomeActivity.this.startActivity(intent);
        finish();
    }

    private void refreshEmpty() {
        showSuccess();
        tvName.clearAnimation();
    }

    // ==================== 点播接口加载成功通知（可配置自动关闭秒数） ====================

    /** 当前显示中的「点播接口加载成功」通知，null 表示未显示 */
    private TextView mLoadSuccessTip;
    /** 通知剩余显示秒数：每秒递减，减到 0 自动从界面上移除 */
    private int mLoadSuccessLeft = 0;

    /**
     * 显示点播接口加载成功通知（2026-10-23 需求 6）。
     *
     * <p>行为：</p>
     * <ul>
     *   <li>秒数取自 json 顶层 {@code loadSuccessDuration}（RemoteConfig.loadSuccessDuration）：
     *       0=不显示，默认 3 秒，最大 60 秒；</li>
     *   <li>通知为非模态浮层，{@code focusable=false / clickable=false}，不抢遥控器焦点，
     *       到点自动移除，不需要用户按键确认（替代原来的系统 Toast 与转圈提示）；</li>
     *   <li>文本每秒刷新为「点播接口加载成功 · Ns」，让用户明确知道何时自动关闭；</li>
     *   <li>重复调用会先移除旧通知再重新计时，避免多层叠加。</li>
     * </ul>
     */
    private void showLoadSuccessNotice() {
        final int seconds = com.github.tvbox.osc.n2n.RemoteConfig.loadSuccessDuration();
        if (seconds <= 0 || isFinishing() || isDestroyed()) {
            return;
        }
        hideLoadSuccessNotice();
        ViewGroup root = findViewById(android.R.id.content);
        if (root == null) {
            return;
        }
        TextView tip = new TextView(this);
        tip.setTextColor(0xFFFFFFFF);
        tip.setBackgroundColor(0xCC1A1A1A);
        tip.setGravity(android.view.Gravity.CENTER);
        tip.setFocusable(false);
        tip.setClickable(false);
        tip.setPadding(res.getDimensionPixelSize(R.dimen.vs_25), res.getDimensionPixelSize(R.dimen.vs_12),
                res.getDimensionPixelSize(R.dimen.vs_25), res.getDimensionPixelSize(R.dimen.vs_12));
        tip.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,
                res.getDimensionPixelSize(R.dimen.ts_20));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        // 顶部居中、位于标题栏下方，避免遮挡左上角标题与右上角图标
        lp.gravity = android.view.Gravity.TOP | android.view.Gravity.CENTER_HORIZONTAL;
        lp.topMargin = res.getDimensionPixelSize(R.dimen.vs_90);
        tip.setLayoutParams(lp);
        root.addView(tip);
        mLoadSuccessTip = tip;
        mLoadSuccessLeft = seconds;
        updateLoadSuccessNoticeText(seconds);
        // 先显示完整秒数，1 秒后再开始递减
        mHandler.postDelayed(mLoadSuccessTick, 1000);
    }

    /** 通知倒计时任务：每秒刷新文本，归零时移除通知 */
    private final Runnable mLoadSuccessTick = new Runnable() {
        @Override
        public void run() {
            if (mLoadSuccessTip == null) {
                return;
            }
            mLoadSuccessLeft--;
            if (mLoadSuccessLeft <= 0) {
                hideLoadSuccessNotice();
                return;
            }
            updateLoadSuccessNoticeText(mLoadSuccessLeft);
            mHandler.postDelayed(this, 1000);
        }
    };

    /** 刷新通知文本：「点播接口加载成功 · Ns」 */
    private void updateLoadSuccessNoticeText(int seconds) {
        if (mLoadSuccessTip != null) {
            mLoadSuccessTip.setText(getString(R.string.load_success_notice, seconds));
        }
    }

    /** 立即移除通知并取消倒计时（幂等） */
    private void hideLoadSuccessNotice() {
        mHandler.removeCallbacks(mLoadSuccessTick);
        mLoadSuccessLeft = 0;
        if (mLoadSuccessTip != null) {
            ViewGroup parent = (ViewGroup) mLoadSuccessTip.getParent();
            if (parent != null) {
                parent.removeView(mLoadSuccessTip);
            }
            mLoadSuccessTip = null;
        }
    }

}
