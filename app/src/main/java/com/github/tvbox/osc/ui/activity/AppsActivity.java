package com.github.tvbox.osc.ui.activity;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.view.animation.BounceInterpolator;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.base.App;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.bean.AppInfo;
import com.github.tvbox.osc.bean.AppMarketItem;
import com.github.tvbox.osc.event.RefreshEvent;
import com.github.tvbox.osc.n2n.ApkDownloader;
import com.github.tvbox.osc.n2n.AppMarketLoader;
import com.github.tvbox.osc.ui.adapter.AppMarketAdapter;
import com.github.tvbox.osc.ui.adapter.AppsAdapter;
import com.github.tvbox.osc.ui.dialog.AppMarketDetailDialog;
import com.github.tvbox.osc.ui.dialog.BaseDialog;
import com.github.tvbox.osc.ui.dialog.GridFilterDialog;
import com.github.tvbox.osc.ui.tv.widget.SearchKeyboard;
import com.github.tvbox.osc.util.FastClickCheckUtil;
import com.github.tvbox.osc.util.HawkConfig;
import com.owen.tvrecyclerview.widget.TvRecyclerView;
import com.owen.tvrecyclerview.widget.V7GridLayoutManager;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AppsActivity extends BaseActivity {

    private static final int TAB_FEATURED = 0;
    private static final int TAB_APP = 1;
    private static final int TAB_GAME = 2;
    private static final int TAB_RANK = 3;
    private static final int TAB_MANAGE = 4;

    private TextView tvDelTip;
    private TextView tvSearch;
    private TextView tvSectionTitle;
    private TextView tvEmpty;
    private TextView[] tabs = new TextView[5];

    private TvRecyclerView mGridMarket;
    private TvRecyclerView mGridViewApps;
    private AppMarketAdapter marketAdapter;
    private AppsAdapter appsAdapter;

    private int currentTab = TAB_FEATURED;
    private String keyword = "";
    private int page = 1;
    private boolean loading = false;
    private boolean hasMore = true;
    private int requestSeq = 0;
    private boolean updateIndexRequested = false;
    private final Map<String, Integer> marketVersionMap = new HashMap<>();

    private boolean delMode = false;
    private String packageName = "";
    private boolean isUnInstallClicked;
    private int appPosition;

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_apps;
    }

    @Override
    protected void init() {
        initView();
        initData();
        selectTab(TAB_FEATURED);
        mGridMarket.post(new Runnable() {
            @Override
            public void run() {
                updateMarketItemWidth();
                tabs[currentTab].requestFocus();
            }
        });
    }

    // ==================== 视图与交互 ====================

    private void initView() {
        EventBus.getDefault().register(this);
        tvDelTip = findViewById(R.id.tvDelTip);
        tvSearch = findViewById(R.id.tvSearch);
        tvSectionTitle = findViewById(R.id.tvSectionTitle);
        tvEmpty = findViewById(R.id.tvEmpty);

        tabs[0] = findViewById(R.id.tabFeatured);
        tabs[1] = findViewById(R.id.tabApp);
        tabs[2] = findViewById(R.id.tabGame);
        tabs[3] = findViewById(R.id.tabRank);
        tabs[4] = findViewById(R.id.tabManage);

        mGridMarket = findViewById(R.id.mGridMarket);
        mGridMarket.setHasFixedSize(true);
        mGridMarket.setLayoutManager(new V7GridLayoutManager(this, marketSpanCount()));
        marketAdapter = new AppMarketAdapter();
        mGridMarket.setAdapter(marketAdapter);
        mGridMarket.setOnItemListener(new TvRecyclerView.OnItemListener() {
            @Override
            public void onItemPreSelected(TvRecyclerView parent, View itemView, int position) {
                itemView.animate().scaleX(1.0f).scaleY(1.0f).setDuration(300)
                        .setInterpolator(new BounceInterpolator()).start();
            }

            @Override
            public void onItemSelected(TvRecyclerView parent, View itemView, int position) {
                itemView.animate().scaleX(1.08f).scaleY(1.08f).setDuration(300)
                        .setInterpolator(new BounceInterpolator()).start();
            }

            @Override
            public void onItemClick(TvRecyclerView parent, View itemView, int position) {
            }
        });
        mGridMarket.setOnInBorderKeyEventListener(new TvRecyclerView.OnInBorderKeyEventListener() {
            @Override
            public boolean onInBorderKeyEvent(int direction, View focused) {
                if (direction == View.FOCUS_UP) {
                    tabs[currentTab].requestFocus();
                    return true;
                }
                return false;
            }
        });
        marketAdapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                FastClickCheckUtil.check(view);
                if (position < 0 || position >= marketAdapter.getData().size()) {
                    return;
                }
                showDetail(marketAdapter.getData().get(position));
            }
        });
        marketAdapter.setOnLoadMoreListener(new BaseQuickAdapter.RequestLoadMoreListener() {
            @Override
            public void onLoadMoreRequested() {
                if (!hasMore || loading) {
                    marketAdapter.loadMoreEnd();
                    return;
                }
                loadMarket(page + 1);
            }
        }, mGridMarket);
        mGridMarket.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom,
                                       int oldLeft, int oldTop, int oldRight, int oldBottom) {
                mGridMarket.post(new Runnable() {
                    @Override
                    public void run() {
                        updateMarketItemWidth();
                    }
                });
            }
        });

        mGridViewApps = findViewById(R.id.mGridApps);
        mGridViewApps.setHasFixedSize(true);
        mGridViewApps.setLayoutManager(new V7GridLayoutManager(this, isBaseOnWidth() ? 6 : 7));
        appsAdapter = new AppsAdapter();
        mGridViewApps.setAdapter(appsAdapter);
        mGridViewApps.setOnItemListener(new TvRecyclerView.OnItemListener() {
            @Override
            public void onItemPreSelected(TvRecyclerView parent, View itemView, int position) {
                itemView.animate().scaleX(1.0f).scaleY(1.0f).setDuration(300)
                        .setInterpolator(new BounceInterpolator()).start();
            }

            @Override
            public void onItemSelected(TvRecyclerView parent, View itemView, int position) {
                itemView.animate().scaleX(1.2f).scaleY(1.2f).setDuration(300)
                        .setInterpolator(new BounceInterpolator()).start();
            }

            @Override
            public void onItemClick(TvRecyclerView parent, View itemView, int position) {
            }
        });
        mGridViewApps.setOnInBorderKeyEventListener(new TvRecyclerView.OnInBorderKeyEventListener() {
            @Override
            public boolean onInBorderKeyEvent(int direction, View focused) {
                if (direction == View.FOCUS_UP) {
                    tabs[currentTab].requestFocus();
                    return true;
                }
                return false;
            }
        });
        appsAdapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                FastClickCheckUtil.check(view);
                AppInfo appInfo = appsAdapter.getData().get(position);
                if (delMode) {
                    Uri packageURI = Uri.parse("package:" + appInfo.getPack());
                    Intent uninstallIntent = new Intent(Intent.ACTION_DELETE, packageURI);
                    uninstallIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(uninstallIntent);
                    packageName = appInfo.getPack();
                    isUnInstallClicked = true;
                    appPosition = position;
                } else {
                    launchApp(appInfo.getPack());
                }
            }
        });
        appsAdapter.setOnItemLongClickListener(new BaseQuickAdapter.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(BaseQuickAdapter adapter, View view, int position) {
                toggleDelMode();
                return true;
            }
        });

        tvSearch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showSearchDialog();
            }
        });
        tvSearch.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (event.getAction() == KeyEvent.ACTION_DOWN
                        && keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    tabs[currentTab].requestFocus();
                    return true;
                }
                return false;
            }
        });

        for (int i = 0; i < tabs.length; i++) {
            final int index = i;
            tabs[i].setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    selectTab(index);
                }
            });
            tabs[i].setOnKeyListener(new View.OnKeyListener() {
                @Override
                public boolean onKey(View v, int keyCode, KeyEvent event) {
                    if (event.getAction() != KeyEvent.ACTION_DOWN) {
                        return false;
                    }
                    if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                        if (index > 0) {
                            selectTab(index - 1);
                            return true;
                        }
                        return false;
                    }
                    if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                        if (index < tabs.length - 1) {
                            selectTab(index + 1);
                        } else {
                            tvSearch.requestFocus();
                        }
                        return true;
                    }
                    if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                        tvSearch.requestFocus();
                        return true;
                    }
                    if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                        View grid = currentTab == TAB_MANAGE ? mGridViewApps : mGridMarket;
                        grid.requestFocus();
                        return true;
                    }
                    return false;
                }
            });
        }
    }

    private int marketSpanCount() {
        // 电视/盒子 4 列；手机按设计宽度 5~6 列
        boolean tv = GridFilterDialog.isTvOrBox(this)
                || !getPackageManager().hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN);
        if (tv) {
            return 4;
        }
        return isBaseOnWidth() ? 5 : 6;
    }

    private void updateMarketItemWidth() {
        int width = mGridMarket.getWidth() - mGridMarket.getPaddingLeft() - mGridMarket.getPaddingRight();
        if (width <= 0) {
            return;
        }
        int spacing = getResources().getDimensionPixelSize(R.dimen.vs_25);
        int span = marketSpanCount();
        int itemWidth = (width - spacing * (span - 1)) / span;
        if (itemWidth > 0) {
            marketAdapter.setItemWidth(itemWidth);
        }
    }

    // ==================== Tab ====================

    private void selectTab(int index) {
        selectTab(index, "");
    }

    private void selectTab(int index, String newKeyword) {
        if (index < 0 || index >= tabs.length) {
            return;
        }
        currentTab = index;
        keyword = newKeyword == null ? "" : newKeyword.trim();

        int themeColor = getThemeColor();
        for (int i = 0; i < tabs.length; i++) {
            boolean selected = i == index;
            tabs[i].setTextColor(selected ? themeColor : Color.WHITE);
            tabs[i].getPaint().setFakeBoldText(selected);
            tabs[i].invalidate();
        }
        tabs[index].requestFocus();
        tvSectionTitle.setVisibility(index == TAB_RANK ? View.VISIBLE : View.GONE);

        if (index == TAB_MANAGE) {
            mGridMarket.setVisibility(View.GONE);
            mGridViewApps.setVisibility(View.VISIBLE);
            tvEmpty.setVisibility(View.GONE);
            initData();
            ensureUpdateIndex();
        } else {
            mGridViewApps.setVisibility(View.GONE);
            mGridMarket.setVisibility(View.VISIBLE);
            reloadMarket();
        }
    }

    private String categoryOf(int tab) {
        if (tab == TAB_APP) {
            return AppMarketLoader.CATEGORY_APP;
        }
        if (tab == TAB_GAME) {
            return AppMarketLoader.CATEGORY_GAME;
        }
        return AppMarketLoader.CATEGORY_ALL;
    }

    private String sortOf(int tab) {
        if (tab == TAB_FEATURED) {
            return AppMarketLoader.SORT_RECOMMEND;
        }
        return AppMarketLoader.SORT_DOWNLOAD;
    }

    // ==================== 市场列表 ====================

    private void reloadMarket() {
        if (currentTab == TAB_MANAGE) {
            return;
        }
        requestSeq++;
        page = 1;
        hasMore = true;
        loading = false;
        marketAdapter.setNewData(new ArrayList<AppMarketItem>());
        marketAdapter.setEnableLoadMore(false);
        showEmpty(true, R.string.app_market_loading);
        loadMarket(1);
    }

    private void loadMarket(final int requestPage) {
        if (loading || currentTab == TAB_MANAGE) {
            return;
        }
        loading = true;
        final int tab = currentTab;
        final int seq = requestSeq;
        AppMarketLoader.load(this, requestPage, categoryOf(tab), sortOf(tab), keyword,
                new AppMarketLoader.Callback() {
                    @Override
                    public void onResult(List<AppMarketItem> items, boolean more, String err) {
                        if (seq != requestSeq || tab != currentTab) {
                            return;
                        }
                        loading = false;
                        List<AppMarketItem> list = items == null
                                ? new ArrayList<AppMarketItem>() : items;
                        if (requestPage == 1) {
                            marketAdapter.setNewData(list);
                        } else if (list.isEmpty()) {
                            marketAdapter.loadMoreEnd();
                        } else {
                            marketAdapter.addData(list);
                            marketAdapter.loadMoreComplete();
                        }
                        hasMore = more && !list.isEmpty();
                        page = requestPage;
                        marketAdapter.setEnableLoadMore(hasMore);

                        if (marketAdapter.getData().isEmpty()) {
                            showEmpty(true, R.string.app_market_empty);
                            if (err != null) {
                                Toast.makeText(AppsActivity.this,
                                        R.string.app_market_network_error, Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            showEmpty(false, 0);
                        }
                    }
                });
    }

    private void showEmpty(boolean visible, int textRes) {
        if (visible) {
            tvEmpty.setText(textRes);
            tvEmpty.setVisibility(View.VISIBLE);
        } else {
            tvEmpty.setVisibility(View.GONE);
        }
    }

    // ==================== 详情与安装 ====================

    private void showDetail(AppMarketItem item) {
        if (item == null) {
            return;
        }
        final AppMarketDetailDialog dialog = new AppMarketDetailDialog(this, item,
                new AppMarketDetailDialog.OnActionListener() {
                    @Override
                    public void onAction(AppMarketItem app) {
                        handleMarketAction(app);
                    }
                });
        dialog.show();
        AppMarketLoader.loadDetail(this, item, new AppMarketLoader.DetailCallback() {
            @Override
            public void onResult(AppMarketItem detail, boolean fromNetwork) {
                if (fromNetwork && detail != null && dialog.isShowing()) {
                    dialog.setData(detail);
                }
            }
        });
    }

    private void handleMarketAction(final AppMarketItem item) {
        if (item == null) {
            return;
        }
        int state = AppMarketAdapter.getActionState(item);
        if (state == AppMarketAdapter.ACTION_DOWNLOADING) {
            Toast.makeText(this, R.string.app_market_downloading_tip, Toast.LENGTH_SHORT).show();
            return;
        }
        if (state == AppMarketAdapter.ACTION_OPEN) {
            launchApp(item.packageName);
            return;
        }
        if (TextUtils.isEmpty(item.downloadUrl)) {
            Toast.makeText(this, R.string.app_market_no_download_url, Toast.LENGTH_SHORT).show();
            return;
        }
        final String pkg = item.packageName;
        AppMarketLoader.markDownloading(pkg);
        marketAdapter.notifyDataSetChanged();
        ApkDownloader.download(this, item.downloadUrl, item.displayName(),
                new ApkDownloader.Listener() {
                    @Override
                    public void onDone(File apk, boolean success, String msg) {
                        AppMarketLoader.clearDownloading(pkg);
                        marketAdapter.notifyDataSetChanged();
                        if (success && apk != null) {
                            ApkDownloader.install(AppsActivity.this, apk);
                        }
                        // 失败提示由 ApkDownloader.fail() 统一 Toast，避免重复弹提示
                    }
                });
    }

    private void launchApp(String pkg) {
        if (TextUtils.isEmpty(pkg)) {
            Toast.makeText(this, R.string.app_market_open_failed, Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Intent intent = getPackageManager().getLaunchIntentForPackage(pkg);
            if (intent == null) {
                Toast.makeText(this, R.string.app_market_open_failed, Toast.LENGTH_SHORT).show();
                return;
            }
            startActivity(intent);
        } catch (Throwable t) {
            Toast.makeText(this, R.string.app_market_open_failed, Toast.LENGTH_SHORT).show();
        }
    }

    // ==================== 搜索 ====================

    private void showSearchDialog() {
        AppMarketSearchDialog dialog = new AppMarketSearchDialog(this, keyword,
                new AppMarketSearchDialog.OnSubmitListener() {
                    @Override
                    public void onSubmit(String word) {
                        int targetTab = currentTab == TAB_MANAGE ? TAB_FEATURED : currentTab;
                        selectTab(targetTab, word);
                    }
                });
        dialog.show();
    }

    // ==================== 已安装应用（管理 Tab） ====================

    private void toggleDelMode() {
        HawkConfig.hotVodDelete = !HawkConfig.hotVodDelete;
        appsAdapter.notifyDataSetChanged();

        delMode = !delMode;
        tvDelTip.setVisibility(delMode ? View.VISIBLE : View.GONE);
    }

    private void initData() {
        List<AppInfo> appInfos = getInstallApps(getApplicationContext());
        if (appInfos == null) {
            return;
        }
        AppInfo.Sorter.sort(appInfos);
        appsAdapter.setNewData(appInfos);
        bindUpdateBadges();
    }

    /**
     * 拉取一次全量市场目录，用于标记已安装应用的可更新状态。
     * 失败/无数据时不做标记，不影响管理 Tab 的本地列表。
     */
    private void ensureUpdateIndex() {
        if (updateIndexRequested) {
            return;
        }
        updateIndexRequested = true;
        marketVersionMap.clear();
        loadUpdateIndexPage(1);
    }

    private void loadUpdateIndexPage(final int page) {
        AppMarketLoader.load(this, page, AppMarketLoader.CATEGORY_ALL,
                AppMarketLoader.SORT_UPDATE, "", new AppMarketLoader.Callback() {
                    @Override
                    public void onResult(List<AppMarketItem> items, boolean hasMore, String err) {
                        if (items != null) {
                            for (AppMarketItem item : items) {
                                if (item == null || TextUtils.isEmpty(item.packageName)
                                        || item.versionCode <= 0) {
                                    continue;
                                }
                                Integer old = marketVersionMap.get(item.packageName);
                                if (old == null || item.versionCode > old) {
                                    marketVersionMap.put(item.packageName, item.versionCode);
                                }
                            }
                        }
                        // 最多取 5 页（约 120 个应用），避免榜单应用过多时反复请求
                        if (hasMore && page < 5) {
                            loadUpdateIndexPage(page + 1);
                        } else {
                            bindUpdateBadges();
                        }
                    }
                });
    }

    private void bindUpdateBadges() {
        if (appsAdapter == null) {
            return;
        }
        List<AppInfo> data = appsAdapter.getData();
        for (AppInfo info : data) {
            Integer marketVersion = marketVersionMap.get(info.getPack());
            info.updateAvailable = marketVersion != null && marketVersion > info.versionCode;
        }
        appsAdapter.notifyDataSetChanged();
    }

    public List<AppInfo> getInstallApps(Context context) {
        List<AppInfo> items = new ArrayList<>();
        PackageManager pm = getPackageManager();
        List<ApplicationInfo> apps = pm.getInstalledApplications(PackageManager.GET_META_DATA);
        for (ApplicationInfo app : apps) {
            if (pm.getLaunchIntentForPackage(app.packageName) != null) {
                if ((app.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0) {
                    // updated system apps
                } else if ((app.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
                    // system apps
                } else {
                    if (!app.packageName.equals(App.getInstance().getPackageName())) {
                        items.add(AppInfo.get(app));
                    }
                }
            }
        }
        return items;
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void refresh(RefreshEvent event) {
        if (event.type == RefreshEvent.TYPE_APP_REFRESH) {
            initData();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        EventBus.getDefault().unregister(this);
    }

    @Override
    public void onBackPressed() {
        if (delMode) {
            toggleDelMode();
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onResume() {
        super.onResume();
        ApkDownloader.resumePendingInstall(this);
        if (isUnInstallClicked && !appInstalledOrNot(packageName)) {
            if (appPosition >= 0 && appPosition < appsAdapter.getData().size()) {
                appsAdapter.remove(appPosition);
            }
            isUnInstallClicked = false;
        }
        if (marketAdapter != null) {
            marketAdapter.notifyDataSetChanged();
        }
    }

    private boolean appInstalledOrNot(String uri) {
        if (TextUtils.isEmpty(uri)) {
            return false;
        }
        PackageManager pm = getPackageManager();
        try {
            pm.getPackageInfo(uri, PackageManager.GET_ACTIVITIES);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    // ==================== 搜索弹窗 ====================

    private static class AppMarketSearchDialog extends BaseDialog {

        interface OnSubmitListener {
            void onSubmit(String keyword);
        }

        private final OnSubmitListener submitListener;
        private EditText etKeyword;
        private SearchKeyboard keyboard;

        AppMarketSearchDialog(Context context, String keyword, OnSubmitListener listener) {
            super(context, R.style.CustomDialogStyleDim);
            this.submitListener = listener;
            setContentView(R.layout.dialog_app_market_search);
            setCanceledOnTouchOutside(false);

            etKeyword = findViewById(R.id.etMarketKeyword);
            keyboard = findViewById(R.id.keyBoardMarket);
            if (!TextUtils.isEmpty(keyword)) {
                etKeyword.setText(keyword);
                etKeyword.setSelection(keyword.length());
            }
            // TV 适配：屏幕内 A-Z/0-9 键盘，支持按首字母搜索（无需软键盘/遥控器数字键）
            keyboard.setOnSearchKeyListener(new SearchKeyboard.OnSearchKeyListener() {
                @Override
                public void onSearchKey(int pos, String key) {
                    if (pos == 0) {
                        etKeyword.setText("");
                    } else if (pos == 1) {
                        String text = etKeyword.getText().toString();
                        if (text.length() > 0) {
                            etKeyword.setText(text.substring(0, text.length() - 1));
                            etKeyword.setSelection(etKeyword.getText().length());
                        }
                    } else if (!TextUtils.isEmpty(key)) {
                        String text = etKeyword.getText().toString() + key;
                        etKeyword.setText(text);
                        etKeyword.setSelection(text.length());
                    }
                }
            });
            findViewById(R.id.btnMarketSearch).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    submit();
                }
            });
            findViewById(R.id.btnMarketCancel).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dismiss();
                }
            });
            etKeyword.setOnKeyListener(new View.OnKeyListener() {
                @Override
                public boolean onKey(View v, int keyCode, KeyEvent event) {
                    if (event.getAction() == KeyEvent.ACTION_DOWN
                            && (keyCode == KeyEvent.KEYCODE_ENTER
                            || keyCode == KeyEvent.KEYCODE_DPAD_CENTER)) {
                        submit();
                        return true;
                    }
                    return false;
                }
            });
        }

        @Override
        public void show() {
            super.show();
            if (etKeyword == null) {
                return;
            }
            etKeyword.requestFocus();
            boolean touch = getContext().getPackageManager().hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN);
            if (touch) {
                InputMethodManager imm = (InputMethodManager) getContext()
                        .getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.showSoftInput(etKeyword, InputMethodManager.SHOW_IMPLICIT);
                }
            } else if (keyboard != null) {
                // 非触屏 TV：焦点交给屏幕键盘，方向键即可选择字母/数字
                keyboard.requestFocus();
            }
        }

        private void submit() {
            String word = etKeyword == null ? "" : etKeyword.getText().toString().trim();
            if (TextUtils.isEmpty(word)) {
                Toast.makeText(getContext(), R.string.search_input, Toast.LENGTH_SHORT).show();
                return;
            }
            if (submitListener != null) {
                submitListener.onSubmit(word);
            }
            dismiss();
        }
    }
}
