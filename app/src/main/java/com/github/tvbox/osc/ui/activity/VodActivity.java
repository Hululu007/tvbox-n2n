package com.github.tvbox.osc.ui.activity;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Color;
import android.os.Handler;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager.widget.ViewPager;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.base.BaseLazyFragment;
import com.github.tvbox.osc.bean.AbsSortXml;
import com.github.tvbox.osc.bean.Movie;
import com.github.tvbox.osc.bean.MovieSort;
import com.github.tvbox.osc.bean.SourceBean;
import com.github.tvbox.osc.ui.adapter.HomePageAdapter;
import com.github.tvbox.osc.ui.adapter.SelectDialogAdapter;
import com.github.tvbox.osc.ui.adapter.SortAdapter;
import com.github.tvbox.osc.ui.dialog.SelectDialog;
import com.github.tvbox.osc.ui.dialog.TipDialog;
import com.github.tvbox.osc.ui.fragment.GridFragment;
import com.github.tvbox.osc.ui.tv.widget.DefaultTransformer;
import com.github.tvbox.osc.ui.tv.widget.FixedSpeedScroller;
import com.github.tvbox.osc.ui.tv.widget.NoScrollViewPager;
import com.github.tvbox.osc.util.DefaultConfig;
import com.github.tvbox.osc.util.FastClickCheckUtil;
import com.github.tvbox.osc.util.ImgUtil;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.viewmodel.SourceViewModel;
import com.owen.tvrecyclerview.widget.TvRecyclerView;
import com.owen.tvrecyclerview.widget.V7GridLayoutManager;
import com.owen.tvrecyclerview.widget.V7LinearLayoutManager;
import com.orhanobut.hawk.Hawk;

import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;

import me.jessyan.autosize.utils.AutoSizeUtils;

/**
 * 独立点播界面：分类导航 + 影片网格（逻辑自原首页迁移，仿 FongMi/TV）
 */
public class VodActivity extends BaseActivity {

    private LinearLayout contentLayout;
    private TextView tvName;
    private TextView tvDate;
    private TvRecyclerView mGridView;
    private NoScrollViewPager mViewPager;
    private SourceViewModel sourceViewModel;
    private SortAdapter sortAdapter;
    private HomePageAdapter pageAdapter;
    private final List<BaseLazyFragment> fragments = new ArrayList<>();
    private boolean isDownOrUp = false;
    private boolean sortChange = false;
    private int currentSelected = 0;
    private int sortFocused = 0;
    public View sortFocusView = null;
    private View currentView = null;
    private final Handler mHandler = new Handler();
    private long mExitTime = 0;
    /** 当前等待的分类结果所属源 key：切源后丢弃上一源迟到的响应（type=3 的 Spider 无法中断） */
    private String pendingSortKey = "";

    /** 启动等待画面（主接口可下发图片/文案/背景色/最短展示时长；切源时同样展示） */
    private View splashView;
    private ImageView splashImage;
    private ProgressBar splashProgress;
    private TextView splashText;
    private TextView splashRetry;
    private boolean splashShowing = false;
    private long splashShowTime = 0;

    /** 点播首分类「热播推荐」数据（只使用主接口 home.recMovies，不再回退点播源首页/豆瓣） */
    private final List<Movie.Video> recommendVideos = new ArrayList<>();

    /** 防止重复触发接口加载 */
    private boolean configLoading = false;
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
        return R.layout.activity_vod;
    }

    @Override
    protected void init() {
        initView();
        initViewModel();
        // 热播推荐与主界面使用同一套规则：主接口推荐 / 豆瓣热播 / 观看历史
        loadRecommendData();
        startEntry();
    }

    private void initView() {
        this.tvName = findViewById(R.id.tvName);
        this.tvDate = findViewById(R.id.tvDate);
        this.contentLayout = findViewById(R.id.contentLayout);
        this.mGridView = findViewById(R.id.mGridViewCategory);
        this.mViewPager = findViewById(R.id.mViewPager);
        this.splashView = findViewById(R.id.vodSplash);
        this.splashImage = findViewById(R.id.vodSplashImage);
        this.splashProgress = findViewById(R.id.vodSplashProgress);
        this.splashText = findViewById(R.id.vodSplashText);
        this.splashRetry = findViewById(R.id.vodSplashRetry);
        if (splashRetry != null) {
            splashRetry.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startEntry();
                }
            });
        }
        this.sortAdapter = new SortAdapter();
        this.mGridView.setLayoutManager(new V7LinearLayoutManager(this.mContext, 0, false));
        this.mGridView.setSpacingWithMargins(0, AutoSizeUtils.dp2px(this.mContext, 10.0f));
        this.mGridView.setAdapter(this.sortAdapter);
        sortAdapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override
            public void onChanged() {
                mGridView.post(() -> {
                    View firstChild = Objects.requireNonNull(mGridView.getLayoutManager()).findViewByPosition(0);
                    if (firstChild != null) {
                        mGridView.setSelectedPosition(0);
                        firstChild.requestFocus();
                    }
                });
            }
        });
        this.mGridView.setOnItemListener(new TvRecyclerView.OnItemListener() {
            public void onItemPreSelected(TvRecyclerView tvRecyclerView, View view, int position) {
                if (view != null && !VodActivity.this.isDownOrUp) {
                    view.animate().scaleX(1.0f).scaleY(1.0f).setDuration(250).start();
                    TextView textView = view.findViewById(R.id.tvTitle);
                    textView.getPaint().setFakeBoldText(false);
                    textView.setTextColor(VodActivity.this.getResources().getColor(R.color.color_FFFFFF_70));
                    textView.invalidate();
                    view.findViewById(R.id.tvFilter).setVisibility(View.GONE);
                }
            }

            public void onItemSelected(TvRecyclerView tvRecyclerView, View view, int position) {
                if (view != null) {
                    VodActivity.this.currentView = view;
                    VodActivity.this.isDownOrUp = false;
                    VodActivity.this.sortChange = true;
                    view.animate().scaleX(1.1f).scaleY(1.1f).setDuration(250).start();
                    TextView textView = view.findViewById(R.id.tvTitle);
                    textView.getPaint().setFakeBoldText(true);
                    textView.setTextColor(VodActivity.this.getResources().getColor(R.color.color_FFFFFF));
                    textView.invalidate();
                    if (position == -1) {
                        position = 0;
                        VodActivity.this.mGridView.setSelection(0);
                    }
                    MovieSort.SortData sortData = sortAdapter.getItem(position);
                    if (null != sortData && !sortData.filters.isEmpty()) {
                        showFilterIcon(sortData.filterSelectCount());
                    }
                    VodActivity.this.sortFocusView = view;
                    VodActivity.this.sortFocused = position;
                    mHandler.removeCallbacks(mDataRunnable);
                    mHandler.postDelayed(mDataRunnable, 200);
                }
            }

            @Override
            public void onItemClick(TvRecyclerView parent, View itemView, int position) {
                if (itemView != null && currentSelected == position) {
                    BaseLazyFragment baseLazyFragment = fragments.get(currentSelected);
                    if ((baseLazyFragment instanceof GridFragment) && !sortAdapter.getItem(position).filters.isEmpty()) {// 弹出筛选
                        ((GridFragment) baseLazyFragment).showFilter();
                    }
                }
            }
        });
        this.mGridView.setOnInBorderKeyEventListener(new TvRecyclerView.OnInBorderKeyEventListener() {
            public boolean onInBorderKeyEvent(int direction, View view) {
                if (direction == View.FOCUS_UP) {
                    BaseLazyFragment baseLazyFragment = fragments.get(sortFocused);
                    if ((baseLazyFragment instanceof GridFragment)) {// 强制刷新
                        ((GridFragment) baseLazyFragment).forceRefresh();
                    }
                }
                if (direction != View.FOCUS_DOWN) {
                    return false;
                }
                BaseLazyFragment baseLazyFragment = fragments.get(sortFocused);
                if (!(baseLazyFragment instanceof GridFragment)) {
                    return false;
                }
                return !((GridFragment) baseLazyFragment).isLoad();
            }
        });
        // 返回：回到主界面（不退出 App）
        findViewById(R.id.tvBack).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                onBackPressed();
            }
        });
        // 源名：点击切换站点 / 长按重新加载当前源分类
        tvName.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                FastClickCheckUtil.check(view);
                showSiteSwitch();
            }
        });
        tvName.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                reloadHome();
                return true;
            }
        });
        // 搜索
        tvFindListener();
        // 点播源配置入口（点播接口地址 + 手机扫码配置）
        findViewById(R.id.tvVodSetting).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                FastClickCheckUtil.check(view);
                showVodSourceConfig();
            }
        });
        setLoadSir(this.contentLayout);
        // 滚动公告：可由远程 JSON 配置是否在点播界面显示（notice.show.vod，默认显示）
        com.github.tvbox.osc.n2n.NoticeBar.show(this, com.github.tvbox.osc.n2n.NoticeBar.SCREEN_VOD);
    }

    private void tvFindListener() {
        ImageView tvFind = findViewById(R.id.tvFind);
        tvFind.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                jumpActivity(SearchActivity.class);
            }
        });
    }

    private void initViewModel() {
        sourceViewModel = new ViewModelProvider(this).get(SourceViewModel.class);
        sourceViewModel.sortResult.observe(this, new Observer<AbsSortXml>() {
            @Override
            public void onChanged(AbsSortXml absXml) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                // 切源后旧源的分类结果可能迟到（尤其 type=3 的 jar/js/py Spider 无法中断），直接丢弃
                if (absXml != null && absXml.sourceKey != null
                        && !absXml.sourceKey.equals(pendingSortKey)) {
                    return;
                }
                try {
                    // 推荐项未指定源时归属当前源，保证点击进详情时能找到站点
                    for (Movie.Video v : recommendVideos) {
                        if (v.sourceKey == null || v.sourceKey.isEmpty()) {
                            v.sourceKey = pendingSortKey;
                        }
                    }
                    // 不生成 my0 首页 Tab（withMy = false）
                    String key = ApiConfig.get().getHomeSourceBean() != null ? ApiConfig.get().getHomeSourceBean().getKey() : "";
                    if (absXml != null && absXml.classes != null && absXml.classes.sortList != null) {
                        sortAdapter.setNewData(DefaultConfig.adjustSort(key, absXml.classes.sortList, false));
                    } else {
                        sortAdapter.setNewData(DefaultConfig.adjustSort(key, new ArrayList<>(), false));
                    }
                    initViewPager();
                    SourceBean home = ApiConfig.get().getHomeSourceBean();
                    if (home != null && home.getName() != null && !home.getName().isEmpty()) {
                        tvName.setText(home.getName());
                    }
                    // 分类/推荐结果已返回：关闭启动等待画面（数据已自动加载）
                    hideSplash();
                } catch (Throwable th) {
                    th.printStackTrace();
                    hideSplash();
                }
            }
        });
    }

    private void initViewPager() {
        fragments.clear();
        currentSelected = 0;
        sortFocused = 0;
        List<MovieSort.SortData> sorts = new ArrayList<>(sortAdapter.getData());
        // 热播推荐固定为点播界面第一个分类并默认展示；数据与主界面一致（recMovies/豆瓣/历史）
        MovieSort.SortData rec = new MovieSort.SortData(GridFragment.RECOMMEND_SORT_ID, "热播推荐");
        rec.sort = -100;
        sorts.add(0, rec);
        if (!sorts.isEmpty()) {
            sortAdapter.setNewData(sorts);
            for (MovieSort.SortData data : sorts) {
                if (GridFragment.RECOMMEND_SORT_ID.equals(data.id)) {
                    fragments.add(GridFragment.newInstance(data, new ArrayList<>(recommendVideos)));
                } else {
                    fragments.add(GridFragment.newInstance(data));
                }
            }
            pageAdapter = new HomePageAdapter(getSupportFragmentManager(), fragments);
            try {
                Field field = ViewPager.class.getDeclaredField("mScroller");
                field.setAccessible(true);
                FixedSpeedScroller scroller = new FixedSpeedScroller(mContext, new AccelerateInterpolator());
                field.set(mViewPager, scroller);
                scroller.setmDuration(300);
            } catch (Exception e) {
            }
            mViewPager.setPageTransformer(true, new DefaultTransformer());
            mViewPager.setAdapter(pageAdapter);
            mViewPager.setCurrentItem(currentSelected, false);
            showSuccess();
        } else {
            showEmpty();
        }
    }

    /** 点播接口免责声明：仅在点播界面展示一次（主界面不再提示点播接口相关信息） */
    private static boolean sWarningShown = false;

    private void showWarningOnce() {
        if (sWarningShown || isFinishing() || isDestroyed()) {
            return;
        }
        String warningText = Hawk.get(HawkConfig.WARNING_TEXT, "");
        if (warningText == null || warningText.trim().isEmpty()) {
            return;
        }
        sWarningShown = true;
        try {
            final TipDialog[] holder = new TipDialog[1];
            holder[0] = new TipDialog(this, warningText, getString(android.R.string.ok), "",
                    new TipDialog.OnListener() {
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
            holder[0].show();
            int autoClose = com.github.tvbox.osc.n2n.RemoteConfig.warningAutoClose();
            if (autoClose > 0) {
                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (holder[0] != null && holder[0].isShowing()) {
                            holder[0].dismiss();
                        }
                    }
                }, autoClose * 1000L);
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * 进入点播：先加载点播接口配置（主界面不再加载点播接口字段），
     * 再预加载当前源对应的 jar/js/py 插件，最后自动拉取分类数据。
     */
    private void startEntry() {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        showSplash("正在加载点播接口…");
        if (ApiConfig.get().isConfigLoaded()) {
            afterConfigLoaded();
            return;
        }
        if (configLoading) {
            return;
        }
        configLoading = true;
        ApiConfig.get().loadConfig(true, new ApiConfig.LoadConfigCallback() {
            @Override
            public void retry() {
            }

            @Override
            public void success() {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        configLoading = false;
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }
                        com.github.tvbox.osc.n2n.ConfigGuard.done(VodActivity.this);
                        showWarningOnce();
                        afterConfigLoaded();
                    }
                });
            }

            @Override
            public void error(String msg) {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        configLoading = false;
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }
                        com.github.tvbox.osc.n2n.ConfigGuard.done(VodActivity.this);
                        showSplashError(msg);
                    }
                });
            }
        }, this);
    }

    /** 接口配置就绪：选可用源 → 预加载该源插件 → 拉分类（等待界面期间完成） */
    private void afterConfigLoaded() {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        SourceBean home = ApiConfig.get().getHomeSourceBean();
        if (!isUsableSource(home)) {
            home = null;
            for (SourceBean sb : ApiConfig.get().getSourceBeanList()) {
                if (isUsableSource(sb)) {
                    home = sb;
                    break;
                }
            }
            if (home != null) {
                ApiConfig.get().setSourceBean(home);
            }
        }
        if (home == null) {
            showSplashError("接口没有可用的点播源");
            return;
        }
        tvName.setText(home.getName() != null && !home.getName().isEmpty()
                ? home.getName() : getString(R.string.app_name));
        pendingSortKey = home.getKey();
        String name = home.getName() == null ? "" : home.getName();
        showSplashMessage("正在加载「" + name + "」…");
        final SourceBean src = home;
        ApiConfig.get().prepareSource(src, new Runnable() {
            @Override
            public void run() {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                // 插件（jar/js/py）已就绪后再请求分类，避免首次切源卡死或无数据
                sourceViewModel.getSort(pendingSortKey, true);
                // 兜底：接口异常时也关闭等待界面（正常结果返回时 observer 会立即关闭）
                mHandler.removeCallbacks(splashTimeout);
                mHandler.postDelayed(splashTimeout, 30000);
            }
        });
    }

    private final Runnable splashTimeout = new Runnable() {
        @Override
        public void run() {
            hideSplash();
        }
    };

    /** 源是否可用于点播（隐藏/不支持类型/空 api 的站点跳过） */
    private boolean isUsableSource(SourceBean sb) {
        if (sb == null || sb.getApi() == null || sb.getApi().trim().isEmpty()) {
            return false;
        }
        if (sb.getHide() != 0) {
            return false;
        }
        int type = sb.getType();
        return type == 0 || type == 1 || type == 3 || type == 4;
    }

    /** 展示/更新启动等待画面 */
    private void showSplash(String text) {
        if (splashView == null) {
            return;
        }
        if (!splashShowing) {
            applySplashConfig();
            splashShowTime = System.currentTimeMillis();
        }
        splashShowing = true;
        mHandler.removeCallbacks(splashHideRunnable);
        splashView.setAlpha(1f);
        splashView.setVisibility(View.VISIBLE);
        splashView.setFocusable(true);
        splashView.setFocusableInTouchMode(true);
        splashView.requestFocus();
        if (splashProgress != null) {
            splashProgress.setVisibility(View.VISIBLE);
        }
        if (splashRetry != null) {
            splashRetry.setVisibility(View.GONE);
        }
        if (splashText != null) {
            splashText.setText(text);
        }
    }

    private void showSplashMessage(String text) {
        if (splashText != null && splashShowing) {
            splashText.setText(text);
        }
    }

    /** 加载失败：等待界面留在原地并显示错误 + 重试（所有点播接口提示只出现在点播界面） */
    private void showSplashError(String msg) {
        if (splashView == null) {
            return;
        }
        if (!splashShowing) {
            applySplashConfig();
            splashShowTime = System.currentTimeMillis();
            splashShowing = true;
            splashView.setVisibility(View.VISIBLE);
        }
        mHandler.removeCallbacks(splashHideRunnable);
        mHandler.removeCallbacks(splashTimeout);
        splashView.setAlpha(1f);
        splashView.setVisibility(View.VISIBLE);
        splashView.setFocusable(true);
        splashView.setFocusableInTouchMode(true);
        splashView.requestFocus();
        if (splashProgress != null) {
            splashProgress.setVisibility(View.GONE);
        }
        String display = msg == null ? "" : msg.trim();
        if (display.isEmpty() || display.equalsIgnoreCase("-1")) {
            display = "未配置点播接口";
        }
        if (splashText != null) {
            splashText.setText("点播接口加载失败\n" + display);
        }
        if (splashRetry != null) {
            splashRetry.setVisibility(View.VISIBLE);
            splashRetry.post(new Runnable() {
                @Override
                public void run() {
                    splashRetry.requestFocus();
                }
            });
        }
    }

    /** 关闭启动等待画面（最短展示时长由主接口 vodSplash.duration 控制） */
    private void hideSplash() {
        if (!splashShowing || splashView == null) {
            return;
        }
        mHandler.removeCallbacks(splashTimeout);
        long elapsed = System.currentTimeMillis() - splashShowTime;
        int min = com.github.tvbox.osc.n2n.RemoteConfig.vodSplashDuration();
        long delay = min - elapsed;
        mHandler.removeCallbacks(splashHideRunnable);
        if (delay > 0) {
            mHandler.postDelayed(splashHideRunnable, delay);
        } else {
            splashHideRunnable.run();
        }
    }

    private final Runnable splashHideRunnable = new Runnable() {
        @Override
        public void run() {
            if (splashView == null) {
                splashShowing = false;
                return;
            }
            splashShowing = false;
            splashView.setFocusable(false);
            splashView.animate().alpha(0f).setDuration(200).withEndAction(new Runnable() {
                @Override
                public void run() {
                    splashView.setVisibility(View.GONE);
                    splashView.setAlpha(1f);
                }
            });
        }
    };

    /** 主接口下发的启动画面：背景图/背景色/默认文案 */
    private void applySplashConfig() {
        if (splashView == null) {
            return;
        }
        try {
            String color = com.github.tvbox.osc.n2n.RemoteConfig.vodSplashColor();
            if (!color.isEmpty()) {
                splashView.setBackgroundColor(Color.parseColor(color));
            }
        } catch (Throwable ignored) {
        }
        try {
            String image = com.github.tvbox.osc.n2n.RemoteConfig.vodSplashImage();
            if (splashImage != null) {
                if (image.isEmpty()) {
                    splashImage.setVisibility(View.GONE);
                } else {
                    splashImage.setVisibility(View.VISIBLE);
                    ImgUtil.load(image, splashImage, 0);
                }
            }
            String text = com.github.tvbox.osc.n2n.RemoteConfig.vodSplashText();
            if (splashText != null && !text.isEmpty()) {
                splashText.setText(text);
            }
        } catch (Throwable ignored) {
        }
    }


    private final Runnable mDataRunnable = new Runnable() {
        @Override
        public void run() {
            if (sortChange) {
                sortChange = false;
                if (sortFocused != currentSelected) {
                    currentSelected = sortFocused;
                    mViewPager.setCurrentItem(sortFocused, false);
                }
            }
        }
    };

    private void showFilterIcon(int count) {
        boolean activated = count > 0;
        currentView.findViewById(R.id.tvFilter).setVisibility(View.VISIBLE);
        ImageView imgView = currentView.findViewById(R.id.tvFilter);
        imgView.setColorFilter(activated ? this.getThemeColor() : Color.WHITE);
    }

    @Override
    public void onBackPressed() {
        // 等待/错误界面：直接退出点播界面（首次进入失败时无需两次返回）
        if (splashShowing) {
            finish();
            return;
        }
        //打断加载
        if (isLoading()) {
            showSuccess();
            return;
        }
        // 检查 fragments 状态
        if (this.fragments.size() <= 0 || this.sortFocused >= this.fragments.size() || this.sortFocused < 0) {
            doExit();
            return;
        }
        BaseLazyFragment baseLazyFragment = this.fragments.get(this.sortFocused);
        if (baseLazyFragment instanceof GridFragment) {
            GridFragment grid = (GridFragment) baseLazyFragment;
            // 如果当前 Fragment 能恢复之前保存的 UI 状态，则直接返回
            if (grid.restoreView()) {
                return;
            }
            // 如果 sortFocusView 存在且没有获取焦点，则请求焦点
            if (this.sortFocusView != null && !this.sortFocusView.isFocused()) {
                this.sortFocusView.requestFocus();
            }
            // 如果当前不是第一个界面，则将列表设置到第一项
            else if (this.sortFocused != 0) {
                this.mGridView.setSelection(0);
            } else {
                doExit();
            }
        } else {
            doExit();
        }
    }

    private void doExit() {
        // 两次返回间隔小于 2000 毫秒，则关闭点播界面回到主界面（不退出 App）
        if (System.currentTimeMillis() - mExitTime < 2000) {
            finish();
        } else {
            mExitTime = System.currentTimeMillis();
            Toast.makeText(mContext, getString(R.string.hm_exit), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        SourceBean home = ApiConfig.get().getHomeSourceBean();
        if (home != null && home.getName() != null && !home.getName().isEmpty()) {
            tvName.setText(home.getName());
        } else {
            tvName.setText(R.string.app_name);
        }
        mHandler.post(mRunnable);
        // 远程配置异步到达后，回到点播页也能刷新出公告条
        com.github.tvbox.osc.n2n.NoticeBar.show(this, com.github.tvbox.osc.n2n.NoticeBar.SCREEN_VOD);
    }

    @Override
    protected void onPause() {
        super.onPause();
        // 只停计时/滚动任务；保留启动等待画面的关闭回调，避免返回页面时等待层卡住
        mHandler.removeCallbacks(mRunnable);
        mHandler.removeCallbacks(mDataRunnable);
    }

    @Override
    protected void onDestroy() {
        mHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
        com.github.tvbox.osc.n2n.NoticeBar.hide(this, com.github.tvbox.osc.n2n.NoticeBar.SCREEN_VOD);
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

    /** 重启点播界面（选源/修改点播接口后使用），保留主界面返回栈 */
    private void restartVod() {
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                Intent intent = new Intent(VodActivity.this, VodActivity.class);
                finish();
                startActivity(intent);
                overridePendingTransition(0, 0);
            }
        }, 200);
    }

    /** 点播源配置：点播接口地址 + 手机扫码（仅显示点播接口表单） */
    private void showVodSourceConfig() {
        com.github.tvbox.osc.ui.dialog.ApiDialog dialog =
                new com.github.tvbox.osc.ui.dialog.ApiDialog(this, com.github.tvbox.osc.ui.dialog.ApiDialog.MODE_VOD);
        dialog.setOnListener(new com.github.tvbox.osc.ui.dialog.ApiDialog.OnListener() {
            @Override
            public void onchange(String api) {
                restartVod();
            }
        });
        dialog.show();
    }

    /**
     * 加载热播推荐：与主界面规则一致（主接口 home.recMovies / 豆瓣热播 / 观看历史）。
     * 网络数据晚到时刷新已创建的首个推荐分类。
     */
    private void loadRecommendData() {
        com.github.tvbox.osc.n2n.RecommendLoader.load(this, new com.github.tvbox.osc.n2n.RecommendLoader.Callback() {
            @Override
            public void onResult(List<Movie.Video> videos) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                recommendVideos.clear();
                if (videos != null) {
                    recommendVideos.addAll(videos);
                }
                for (Movie.Video v : recommendVideos) {
                    if (v.sourceKey == null || v.sourceKey.isEmpty()) {
                        v.sourceKey = pendingSortKey;
                    }
                }
                if (!fragments.isEmpty() && fragments.get(0) instanceof GridFragment) {
                    ((GridFragment) fragments.get(0)).setRecommendVideos(recommendVideos);
                }
            }
        });
    }

    // 站点切换
    void showSiteSwitch() {
        List<SourceBean> sites = new ArrayList<>();
        for (SourceBean sb : ApiConfig.get().getSourceBeanList()) {
            if (isUsableSource(sb)) sites.add(sb);
        }
        if (sites.size() > 0) {
            SelectDialog<SourceBean> dialog = new SelectDialog<>(VodActivity.this);

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
            final SelectDialog<SourceBean> fDialog = dialog;
            dialog.setAdapter(tvRecyclerView, new SelectDialogAdapter.SelectDialogInterface<SourceBean>() {
                @Override
                public void click(SourceBean value, int pos) {
                    // 换源前取消旧源请求，避免旧响应晚到覆盖新列表
                    SourceBean old = ApiConfig.get().getHomeSourceBean();
                    if (old != null && old.getKey() != null) {
                        com.lzy.okgo.OkGo.getInstance().cancelTag(old.getKey() + "_sort");
                    }
                    ApiConfig.get().setSourceBean(value);
                    fDialog.dismiss();
                    // 与参考项目一致：选源后重启点播界面，重新加载该源的分类与推荐
                    Toast.makeText(mContext, "已切换到「" + value.getName() + "」，正在重启点播界面", Toast.LENGTH_SHORT).show();
                    restartVod();
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

    /**
     * 切换源后在本界面刷新：先复用本地缓存的接口配置重新解析（源列表/解析器同步更新），
     * 再重建当前源的 jar/js/py 插件，最后强制绕过缓存重新请求分类。
     * 与参考项目一致：切源不重启界面，分类/推荐直接在当前点播界面刷新。
     */
    void reloadHome() {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        SourceBean home = ApiConfig.get().getHomeSourceBean();
        if (!isUsableSource(home)) {
            // 当前源不可用（或接口尚未加载）：重新走完整进入流程
            startEntry();
            return;
        }
        String key = home.getKey();
        // 源名立即跟随切换结果更新，不必等分类数据返回
        if (home.getName() != null && !home.getName().isEmpty()) {
            tvName.setText(home.getName());
        }
        currentSelected = 0;
        sortFocused = 0;
        pendingSortKey = key;
        showSplash("正在切换源…");
        if (configLoading) {
            return;
        }
        configLoading = true;
        // 切源=复用缓存重载接口配置（含 sites/parses/lives），成功后由 afterConfigLoaded 重建插件并拉分类
        ApiConfig.get().loadConfig(true, new ApiConfig.LoadConfigCallback() {
            @Override
            public void retry() {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        configLoading = false;
                        afterConfigLoaded();
                    }
                });
            }

            @Override
            public void success() {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        configLoading = false;
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }
                        com.github.tvbox.osc.n2n.ConfigGuard.done(VodActivity.this);
                        afterConfigLoaded();
                    }
                });
            }

            @Override
            public void error(String msg) {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        configLoading = false;
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }
                        com.github.tvbox.osc.n2n.ConfigGuard.done(VodActivity.this);
                        showSplashError(msg);
                    }
                });
            }
        }, this);
    }
}
