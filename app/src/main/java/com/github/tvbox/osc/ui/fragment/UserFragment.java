package com.github.tvbox.osc.ui.fragment;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.animation.BounceInterpolator;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Toast;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.base.BaseLazyFragment;
import com.github.tvbox.osc.bean.Movie;
import com.github.tvbox.osc.n2n.RemoteConfig;
import com.github.tvbox.osc.bean.VodInfo;
import com.github.tvbox.osc.cache.RoomDataManger;
import com.github.tvbox.osc.event.ServerEvent;
import com.github.tvbox.osc.ui.activity.*;
import com.github.tvbox.osc.ui.adapter.HomeHotVodAdapter;
import com.github.tvbox.osc.util.FastClickCheckUtil;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.UA;
import com.github.tvbox.osc.util.ImgUtil;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.lzy.okgo.OkGo;
import com.lzy.okgo.callback.AbsCallback;
import com.lzy.okgo.model.Response;
import com.orhanobut.hawk.Hawk;
import com.owen.tvrecyclerview.widget.TvRecyclerView;
import com.owen.tvrecyclerview.widget.V7GridLayoutManager;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * @author pj567
 * @date :2021/3/9
 * @description:
 */
public class UserFragment extends BaseLazyFragment implements View.OnClickListener {
    private LinearLayout tvDrive;
    private LinearLayout tvLive;
    private LinearLayout tvSearch;
    private LinearLayout tvSetting;
    private LinearLayout tvHistory;
    private LinearLayout tvCollect;
    private LinearLayout tvPush;
    /** hin2n 组网内置模块入口（随入口行一起滚动） */
    private LinearLayout tvHin2nHome;
    public static HomeHotVodAdapter homeHotVodAdapter;
    public static TvRecyclerView tvHotListForGrid;
    public static TvRecyclerView tvHotListForLine;

    public static UserFragment newInstance() {
        return new UserFragment();
    }

    /**
     * 把历史一行（历史/点播/直播/搜索/收藏/推送/云盘）入口图标染成远程 JSON
     * home.iconColor（若有）；未下发则保持原样。
     * 与 HomeEntry.applyHomeStyleToEntries 对顶栏图标做同一件事，避免首界面
     * 「顶栏图标是主题色、历史一行图标却是白色」的配色不一致。
     */
    private void applyEntryIconColor() {
        try {
            JSONObject home = RemoteConfig.homeStyle();
            if (home == null) return;
            int iconColor = com.github.tvbox.osc.n2n.HomeEntry.parseColor(home.optString("iconColor", ""));
            if (iconColor == 0) return;
            // 固定入口布局里的 ImageView：fragment_user.xml 给每个卡片里的图标写死了 id，
            // 直接按 id 查找；找不到（如 rebuildHomeEntriesIfConfigured 整体替换后）则跳过。
            int[] iconIds = {
                    R.id.hm_history, R.id.hm_vod, R.id.hm_live, R.id.hm_search,
                    R.id.hm_fav, R.id.hm_push, R.id.hm_drive, R.id.hm_settings, R.id.hm_hin2n
            };
            for (int id : iconIds) {
                View v = findViewById(id);
                if (v instanceof android.widget.ImageView) {
                    ((android.widget.ImageView) v).setColorFilter(iconColor);
                }
            }
            // JSON 配置重建了整个 tvUserHome 时，遍历 LinearLayout 子 View 里的 ImageView
            android.view.ViewGroup row = findViewById(R.id.tvUserHome);
            if (row != null) {
                for (int i = 0; i < row.getChildCount(); i++) {
                    View child = row.getChildAt(i);
                    if (child instanceof android.view.ViewGroup) {
                        android.view.ViewGroup card = (android.view.ViewGroup) child;
                        for (int j = 0; j < card.getChildCount(); j++) {
                            View inner = card.getChildAt(j);
                            if (inner instanceof android.widget.ImageView) {
                                ((android.widget.ImageView) inner).setColorFilter(iconColor);
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }

    /** 顶栏主题色/远程 iconColor 更新后，同步刷新历史一行入口图标颜色 */
    public void refreshEntryIconStyle() {
        applyEntryIconColor();
    }

    @Override
    public void onFragmentResume() {
        // 每次回到首页都重新应用，确保远程配置刷新后图标色同步
        applyEntryIconColor();

        // takagen99: Initialize Icon Placement
        // 空值保护：init() 若因远程配置/布局异常未完成，这里不能 NPE（否则整个首页空黑）
        if (tvSearch != null) {
            tvSearch.setVisibility(Hawk.get(HawkConfig.HOME_SEARCH_POSITION, true)
                    ? View.GONE : View.VISIBLE);
        }
        // 点播界面的设置入口统一由顶栏 tvN2n 承载，历史一行不再显示设置入口
        if (tvSetting != null) {
            tvSetting.setVisibility(View.GONE);
        }

        super.onFragmentResume();
        if (Hawk.get(HawkConfig.HOME_REC, 0) == 2 && homeHotVodAdapter != null) {
            List<VodInfo> allVodRecord = RoomDataManger.getAllVodRecord(20);
            List<Movie.Video> vodList = new ArrayList<>();
            for (VodInfo vodInfo : allVodRecord) {
                Movie.Video vod = new Movie.Video();
                vod.id = vodInfo.id;
                vod.sourceKey = vodInfo.sourceKey;
                vod.name = vodInfo.name;
                vod.pic = vodInfo.pic;
                if (vodInfo.playNote != null && !vodInfo.playNote.isEmpty())
                    vod.note = "上次看到" + vodInfo.playNote;
                vodList.add(vod);
            }
            homeHotVodAdapter.setNewData(vodList);
        }
    }

    @Override
    protected int getLayoutResID() {
        return R.layout.fragment_user;
    }
    private ImgUtil.Style style;
    /** 首页「点播」入口（FongMi 风格，位于直播入口之前） */
    private android.view.View tvVod;
    @Override
    protected void init() {
        EventBus.getDefault().register(this);
        tvDrive = findViewById(R.id.tvDrive);
        tvVod = findViewById(R.id.tvVod);
        tvLive = findViewById(R.id.tvLive);
        tvSearch = findViewById(R.id.tvSearch);
        tvSetting = findViewById(R.id.tvSetting);
        tvCollect = findViewById(R.id.tvFavorite);
        tvHistory = findViewById(R.id.tvHistory);
        tvPush = findViewById(R.id.tvPush);
        tvHin2nHome = findViewById(R.id.tvHin2nHome);
        if (tvHin2nHome != null) {
            tvHin2nHome.setVisibility(RemoteConfig.hin2nEntryEnabled() ? View.VISIBLE : View.GONE);
        }
        // 点播界面/设置入口已由顶栏 tvN2n 统一承载，历史一行不再重复显示
        if (tvSetting != null) {
            tvSetting.setVisibility(View.GONE);
        }
        tvDrive.setOnClickListener(this);
        tvVod.setOnClickListener(this);
        tvLive.setOnClickListener(this);
        tvSearch.setOnClickListener(this);
        tvSetting.setOnClickListener(this);
        tvHistory.setOnClickListener(this);
        tvPush.setOnClickListener(this);
        tvCollect.setOnClickListener(this);
        tvHin2nHome.setOnClickListener(this);
        tvDrive.setOnFocusChangeListener(focusChangeListener);
        tvVod.setOnFocusChangeListener(focusChangeListener);
        tvLive.setOnFocusChangeListener(focusChangeListener);
        tvSearch.setOnFocusChangeListener(focusChangeListener);
        tvSetting.setOnFocusChangeListener(focusChangeListener);
        tvHistory.setOnFocusChangeListener(focusChangeListener);
        tvPush.setOnFocusChangeListener(focusChangeListener);
        tvCollect.setOnFocusChangeListener(focusChangeListener);
        tvHin2nHome.setOnFocusChangeListener(focusChangeListener);
        // JSON "homeEntries" 配置存在时整体替换历史一行入口：内容/顺序/图标全部按 JSON
        rebuildHomeEntriesIfConfigured();
        // 远程配置 iconColor 下发时，把历史一行入口图标也染成主题色
        applyEntryIconColor();
        // 焦点在入口行与下方内容列表之间移动时，外层 ScrollView 跟随滚动，
        // 让「历史」一行入口随内容列表一起上/下滚动（D-pad 上下）。
        installScrollFollow();
        tvHotListForLine = findViewById(R.id.tvHotListForLine);
        tvHotListForGrid = findViewById(R.id.tvHotListForGrid);
        // 多行网格嵌在外层 ScrollView 中且高度为 wrap_content：必须为 false，
        // 否则首次测量时 adapter 为空，网格高度被固定为极小值，数据到达后不再重新测量，
        // 导致网格内部滚动、入口行无法随内容一起滚动（根因）
        tvHotListForGrid.setHasFixedSize(false);
        int spanCount = 5;
        // 首页推荐改由主接口（远程配置 home.recMovies）提供；配置后为海报网格样式
        List<Movie.Video> customRec = RemoteConfig.homeRecMovies();
        if (Hawk.get(HawkConfig.HOME_REC, 0) != 2 && !customRec.isEmpty()) {
            style = ImgUtil.initStyle();
        }
        if(style!=null) {
            spanCount=ImgUtil.spanCountByStyle(style,spanCount);
        }
        tvHotListForGrid.setLayoutManager(new V7GridLayoutManager(this.mContext, spanCount));
        // 多行模式：关闭网格列表内部滚动，内容完全展开、由外层 ScrollView 统一滚动，
        // 入口行（历史一行）即可随下方内容一起上/下滚动（与单行模式行为一致）
        tvHotListForGrid.setNestedScrollingEnabled(false);
        // 推荐列表左上角标题：
        //  - 模式 0/1（推荐）：主接口配置了 home.recMovies 时叫「推荐」，未配置回退「豆瓣热播」；
        //  - 模式 2（观看历史）不显示该标题。
        String tvRate = "";
        if (Hawk.get(HawkConfig.HOME_REC, 0) == 0 || Hawk.get(HawkConfig.HOME_REC, 0) == 1) {
            tvRate = customRec.isEmpty() ? "豆瓣热播" : "推荐";
        }
        homeHotVodAdapter = new HomeHotVodAdapter(style, tvRate);
        homeHotVodAdapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                // 首页设计上不预加载点播接口，源列表为空是常态，不能在此整体拦截点击：
                // 详情分支（下方）已自带空源守卫并提示，搜索分支本就不依赖源列表
                Movie.Video vod = ((Movie.Video) adapter.getItem(position));
                // 防御：数据刷新瞬间 position 可能已越界/对应项被移除，取不到条目直接忽略点击
                if (vod == null) {
                    return;
                }

                // takagen99: CHeck if in Delete Mode
                if ((vod.id != null && !vod.id.isEmpty()) && (Hawk.get(HawkConfig.HOME_REC, 0) == 2) && HawkConfig.hotVodDelete) {
                    homeHotVodAdapter.remove(position);
                    VodInfo vodInfo = RoomDataManger.getVodInfo(vod.sourceKey, vod.id);
                    // 防御：历史记录可能已被其它页面删除，查不到时只从列表移除，避免空指针
                    if (vodInfo != null) {
                        RoomDataManger.deleteVodRecord(vod.sourceKey, vodInfo);
                    }
                    Toast.makeText(mContext, getString(R.string.hm_hist_del), Toast.LENGTH_SHORT).show();
                } else if (vod.id != null && !vod.id.isEmpty()) {
                    // 首页不再加载点播接口：未进过点播界面时源列表为空，直接提示先加载接口
                    if (ApiConfig.get().getSourceBeanList().isEmpty()) {
                        Toast.makeText(mContext, "请先进入点播界面加载接口", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Bundle bundle = new Bundle();
                    bundle.putString("id", vod.id);
                    bundle.putString("sourceKey", vod.sourceKey);
                    if (vod.id.startsWith("msearch:")) {
                        bundle.putString("title", vod.name);
                        jumpActivity(FastSearchActivity.class, bundle);
                    } else {
                        jumpActivity(DetailActivity.class, bundle);
                    }
                } else {
                    Intent newIntent;
                    if(Hawk.get(HawkConfig.FAST_SEARCH_MODE, false)){
                        newIntent = new Intent(mContext, FastSearchActivity.class);
                    }else {
                        newIntent = new Intent(mContext, SearchActivity.class);
                    }
                    newIntent.putExtra("title", vod.name);
                    newIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    mActivity.startActivity(newIntent);
                }
            }
        });
        // takagen99 : Long press to trigger Delete Mode for VOD History on Home Page
        homeHotVodAdapter.setOnItemLongClickListener(new BaseQuickAdapter.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(BaseQuickAdapter adapter, View view, int position) {
                if (ApiConfig.get().getSourceBeanList().isEmpty())
                    return false;
                Movie.Video vod = ((Movie.Video) adapter.getItem(position));
                // 防御：长按瞬间列表可能已刷新，取不到条目直接忽略
                if (vod == null) {
                    return false;
                }
                // Additional Check if : Home Rec 0=豆瓣, 1=推荐, 2=历史
                if ((vod.id != null && !vod.id.isEmpty()) && (Hawk.get(HawkConfig.HOME_REC, 0) == 2)) {
                    HawkConfig.hotVodDelete = !HawkConfig.hotVodDelete;
                    homeHotVodAdapter.notifyDataSetChanged();
                } else {
                    Intent newIntent = new Intent(mContext, FastSearchActivity.class);
                    newIntent.putExtra("title", vod.name);
                    newIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    mActivity.startActivity(newIntent);
                }
                return true;
            }
        });
        
        tvHistory.setOnLongClickListener(new View.OnLongClickListener() {
        	@Override
            public boolean onLongClick(View v) {
                HomeActivity.homeRecf();
                return HomeActivity.reHome(mContext);
            }
        });
        
        // Grid View
        tvHotListForGrid.setOnItemListener(new TvRecyclerView.OnItemListener() {
            @Override
            public void onItemPreSelected(TvRecyclerView parent, View itemView, int position) {
                itemView.animate().scaleX(1.0f).scaleY(1.0f).setDuration(300).setInterpolator(new BounceInterpolator()).start();
            }

            @Override
            public void onItemSelected(TvRecyclerView parent, View itemView, int position) {
                itemView.animate().scaleX(1.2f).scaleY(1.2f).setDuration(300).setInterpolator(new BounceInterpolator()).start();
            }

            @Override
            public void onItemClick(TvRecyclerView parent, View itemView, int position) {

            }
        });
        tvHotListForGrid.setAdapter(homeHotVodAdapter);
        // Line View
        tvHotListForLine.setOnItemListener(new TvRecyclerView.OnItemListener() {
            @Override
            public void onItemPreSelected(TvRecyclerView parent, View itemView, int position) {
                itemView.animate().scaleX(1.0f).scaleY(1.0f).setDuration(300).setInterpolator(new BounceInterpolator()).start();
            }

            @Override
            public void onItemSelected(TvRecyclerView parent, View itemView, int position) {
                itemView.animate().scaleX(1.2f).scaleY(1.2f).setDuration(300).setInterpolator(new BounceInterpolator()).start();
            }

            @Override
            public void onItemClick(TvRecyclerView parent, View itemView, int position) {

            }
        });
        tvHotListForLine.setAdapter(homeHotVodAdapter);

        initHomeHotVod(homeHotVodAdapter);

        // Swifly: Home Style
        if (Hawk.get(HawkConfig.HOME_REC_STYLE, true)) {
            tvHotListForGrid.setVisibility(View.VISIBLE);
            tvHotListForLine.setVisibility(View.GONE);
        } else {
            tvHotListForGrid.setVisibility(View.GONE);
            tvHotListForLine.setVisibility(View.VISIBLE);
        }
    }

    /**
     * JSON "homeEntries" 整体替换历史一行：移除布局内置的固定入口卡片，
     * 按 JSON 数组顺序动态生成相同样式的卡片，点击统一走 EntryDispatcher
     * （page=打开界面 / feature=内置功能 / app=其他 App / vod=自定义点播界面）。
     * 未配置时保持布局原样。
     */
    private void rebuildHomeEntriesIfConfigured() {
        java.util.List<RemoteConfig.Entry> entries = RemoteConfig.homeEntries();
        if (entries == null) {
            return;
        }
        LinearLayout row = findViewById(R.id.tvUserHome);
        if (row == null) {
            return;
        }
        row.removeAllViews();
        android.content.res.Resources res = getResources();
        int h = res.getDimensionPixelSize(R.dimen.vs_100);
        int margin = res.getDimensionPixelSize(R.dimen.vs_5);
        int pad = res.getDimensionPixelSize(R.dimen.vs_15);
        int minW = res.getDimensionPixelSize(R.dimen.vs_150);
        int iconW = res.getDimensionPixelSize(R.dimen.vs_40);
        int iconMargin = res.getDimensionPixelSize(R.dimen.vs_3);
        int tvPad = res.getDimensionPixelSize(R.dimen.vs_3);
        for (final RemoteConfig.Entry e : entries) {
            // 设置入口已全面移除，历史一行不再生成设置卡片
            if ("page".equals(e.type) && "setting".equals(e.target)) {
                continue;
            }
            LinearLayout card = new LinearLayout(mContext);
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, h);
            clp.setMargins(margin, margin, margin, margin);
            card.setLayoutParams(clp);
            card.setGravity(android.view.Gravity.CENTER);
            card.setMinimumWidth(minW);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setPadding(pad, pad, pad, pad);
            card.setBackgroundResource(R.drawable.shape_user_home);
            card.setClickable(true);
            card.setFocusable(true);
            card.setContentDescription(e.name);

            android.widget.ImageView iv = new android.widget.ImageView(mContext);
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(iconW,
                    LinearLayout.LayoutParams.MATCH_PARENT);
            ilp.setMargins(iconMargin, iconMargin, iconMargin, iconMargin);
            iv.setLayoutParams(ilp);
            iv.setAlpha(0.9f);
            com.github.tvbox.osc.n2n.EntryDispatcher.loadIcon(mContext, iv, e);
            card.addView(iv);

            android.widget.TextView tv = new android.widget.TextView(mContext);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            tv.setLayoutParams(tlp);
            tv.setGravity(android.view.Gravity.CENTER);
            tv.setPadding(tvPad, tvPad, tvPad, tvPad);
            tv.setText(e.name);
            tv.setTextColor(0xE6FFFFFF); // color_FFFFFF_90
            tv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,
                    res.getDimensionPixelSize(R.dimen.ts_24));
            tv.setTypeface(android.graphics.Typeface.create("sans-serif-black", android.graphics.Typeface.NORMAL));
            tv.setFocusable(false);
            tv.setClickable(false);
            card.addView(tv);

            card.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    FastClickCheckUtil.check(v);
                    com.github.tvbox.osc.n2n.EntryDispatcher.open(mActivity, e);
                }
            });
            // 长按「历史」入口：切换推荐模式并重载首页（与固定卡片行为一致）
            if ("page".equals(e.type) && "history".equals(e.target)) {
                card.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View v) {
                        HomeActivity.homeRecf();
                        return HomeActivity.reHome(mContext);
                    }
                });
            }
            card.setOnFocusChangeListener(focusChangeListener);
            row.addView(card);
        }
    }

    /**
     * 初始化首页推荐列表（首页内容模式由 HawkConfig.HOME_REC 决定）。
     *
     * <p>主页面已去除对点播接口的引用，推荐数据统一由主接口下发：</p>
     * <ol>
     *   <li>模式 2：前台 {@link #onFragmentResume()} 已填充观看历史，这里不再处理；</li>
     *   <li>模式 0/1（推荐）：主接口配置了 home.recMovies 时直接显示该列表，<b>完全不发豆瓣请求</b>；
     *       未配置才走原有豆瓣热播（含当日缓存）。</li>
     * </ol>
     */
    private void initHomeHotVod(HomeHotVodAdapter adapter) {
        if (Hawk.get(HawkConfig.HOME_REC, 0) == 2) {
            return;
        }
        // 推荐改由主接口（远程配置 home.recMovies）提供：配置后不再请求豆瓣
        List<Movie.Video> customRec = RemoteConfig.homeRecMovies();
        if (!customRec.isEmpty()) {
            adapter.setNewData(customRec);
            return;
        }
        try {
            Calendar cal = Calendar.getInstance();
            int year = cal.get(Calendar.YEAR);
            int month = cal.get(Calendar.MONTH) + 1;
            int day = cal.get(Calendar.DATE);
            String today = String.format("%d%d%d", year, month, day);
            String requestDay = Hawk.get("home_hot_day", "");
            if (requestDay.equals(today)) {
                String json = Hawk.get("home_hot", "");
                if (!json.isEmpty()) {
                    adapter.setNewData(loadHots(json));
                    return;
                }
            }
            String doubanHotURL = "https://movie.douban.com/j/new_search_subjects?sort=U&range=0,10&tags=&playable=1&start=0&year_range=" + year + "," + year;
            String userAgent = UA.random();
            OkGo.<String>get(doubanHotURL).headers("User-Agent", userAgent).execute(new AbsCallback<String>() {
                @Override
                public void onSuccess(Response<String> response) {
                    String netJson = response.body();
                    Hawk.put("home_hot_day", today);
                    Hawk.put("home_hot", netJson);
                    mActivity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            adapter.setNewData(loadHots(netJson));
                        }
                    });
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

    private ArrayList<Movie.Video> loadHots(String json) {
        ArrayList<Movie.Video> result = new ArrayList<>();
        try {
            JsonObject infoJson = new Gson().fromJson(json, JsonObject.class);
            JsonArray array = infoJson.getAsJsonArray("data");
            for (JsonElement ele : array) {
                JsonObject obj = (JsonObject) ele;
                Movie.Video vod = new Movie.Video();
                vod.name = obj.get("title").getAsString();
                vod.note = obj.get("rate").getAsString();
                vod.pic = obj.get("cover").getAsString() + "@User-Agent=" + UA.random() + "@Referer=https://www.douban.com/";                
                result.add(vod);
            }
        } catch (Throwable th) {

        }
        return result;
    }

    /**
     * 首页滚动联动：入口行（历史/点播/直播/…/组网）与下方内容列表同处一个 ScrollView。
     * 横向 TvRecyclerView 内部移动焦点时 ScrollView 默认不会自动滚动，
     * 这里监听全局焦点变化，把新聚焦视图滚动到可视区内（上下留出边距）。
     */
    private void installScrollFollow() {
        View root = getView();
        if (!(root instanceof ScrollView)) {
            return;
        }
        final ScrollView sv = (ScrollView) root;
        final int margin = (int) (24 * getResources().getDisplayMetrics().density);
        sv.getViewTreeObserver().addOnGlobalFocusChangeListener(new ViewTreeObserver.OnGlobalFocusChangeListener() {
            @Override
            public void onGlobalFocusChanged(View oldFocus, View newFocus) {
                if (newFocus == null || sv.getHeight() <= 0) {
                    return;
                }
                // 只关心本 Fragment 内的视图
                if (!sv.isShown()) {
                    return;
                }
                int[] svPos = new int[2];
                int[] fPos = new int[2];
                sv.getLocationOnScreen(svPos);
                newFocus.getLocationOnScreen(fPos);
                int top = fPos[1] - svPos[1] + sv.getScrollY();
                int bottom = top + newFocus.getHeight();
                int visibleTop = sv.getScrollY();
                int visibleBottom = visibleTop + sv.getHeight();
                if (bottom > visibleBottom - margin) {
                    sv.smoothScrollTo(0, bottom - sv.getHeight() + margin);
                } else if (top < visibleTop + margin) {
                    sv.smoothScrollTo(0, Math.max(0, top - margin));
                }
            }
        });
    }

    private final View.OnFocusChangeListener focusChangeListener = new View.OnFocusChangeListener() {
        @Override
        public void onFocusChange(View v, boolean hasFocus) {
            if (hasFocus)
                v.animate().scaleX(1.05f).scaleY(1.05f).setDuration(300).setInterpolator(new BounceInterpolator()).start();
            else
                v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(300).setInterpolator(new BounceInterpolator()).start();
        }
    };

    @Override
    public void onClick(View v) {

        // takagen99: Remove Delete Mode
        HawkConfig.hotVodDelete = false;

        FastClickCheckUtil.check(v);
        if (v.getId() == R.id.tvVod) {
            // 点播入口：打开独立点播界面（分类/影片网格已自首页迁移）
            jumpActivity(VodActivity.class);
        } else if (v.getId() == R.id.tvLive) {
            jumpActivity(LivePlayActivity.class);
        } else if (v.getId() == R.id.tvSearch) {
            jumpActivity(SearchActivity.class);
        } else if (v.getId() == R.id.tvHistory) {
            jumpActivity(HistoryActivity.class);
        } else if (v.getId() == R.id.tvPush) {
            jumpActivity(PushActivity.class);
        } else if (v.getId() == R.id.tvFavorite) {
            jumpActivity(CollectActivity.class);
        } else if (v.getId() == R.id.tvDrive) {
            jumpActivity(DriveActivity.class);
        } else if (v.getId() == R.id.tvHin2nHome) {
            // 组网入口：独立 hin2n App（已安装拉起，未安装走下载安装管线）
            RemoteConfig.Entry e = new RemoteConfig.Entry();
            e.type = "page";
            e.name = "组网";
            e.target = "hin2n";
            com.github.tvbox.osc.n2n.EntryDispatcher.open(mActivity, e);
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void server(ServerEvent event) {
        if (event.type == ServerEvent.SERVER_CONNECTION) {
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        EventBus.getDefault().unregister(this);
    }
}
