package com.github.tvbox.osc.ui.activity;

import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.event.RefreshEvent;
import com.github.tvbox.osc.n2n.EntryDispatcher;
import com.github.tvbox.osc.n2n.NoticeBar;
import com.github.tvbox.osc.n2n.RemoteConfig;
import com.github.tvbox.osc.util.ImgUtil;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 酒店 IPTV 桌面（主界面）。
 *
 * <p>由主接口 {@code screens} + {@code defaultScreen} 决定是否加载及使用哪种布局：
 * <ul>
 *   <li>{@code hotel_grid}：宫格大卡片（默认）</li>
 *   <li>{@code hotel_list}：左侧纵向菜单 + 右侧大图预览</li>
 *   <li>{@code hotel_showcase}：顶部大图 + 底部一排入口</li>
 * </ul>
 *
 * <p>界面配置支持 {@code layoutUrl}：主接口下发布局 JSON 地址，开机按需下载并缓存本地，
 * 下载失败回退上次缓存 / 主接口内联配置。这样新增布局只需下发 JSON，无需改代码。
 */
public class HotelActivity extends BaseActivity {

    private TextView tvTitle;
    private TextView tvClock;
    private TextView tvTicker;
    private ImageView ivLogo;
    private ImageView ivBg;
    private FrameLayout body;

    private RemoteConfig.Screen screen;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean clockRunning = false;
    /** 上次渲染使用的配置版本，主接口配置更新后 onResume 自动重绘 */
    private int lastConfigVersion = -1;

    private final Runnable clockTask = new Runnable() {
        @Override
        public void run() {
            if (tvClock != null) {
                tvClock.setText(new SimpleDateFormat("HH:mm  MM月dd日", Locale.CHINA).format(new Date()));
            }
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_hotel;
    }

    @Override
    protected void init() {
        tvTitle = findViewById(R.id.hotelTitle);
        tvClock = findViewById(R.id.hotelClock);
        tvTicker = findViewById(R.id.hotelTicker);
        ivLogo = findViewById(R.id.hotelLogo);
        ivBg = findViewById(R.id.hotelBg);
        body = findViewById(R.id.hotelBody);

        String id = getIntent() != null ? getIntent().getStringExtra("screenId") : null;
        if (TextUtils.isEmpty(id)) {
            id = RemoteConfig.defaultScreenId();
        }
        screen = RemoteConfig.findScreen(id);
        if (screen == null) {
            // 未配置 screens 时给一个可直接使用的默认酒店桌面
            screen = new RemoteConfig.Screen();
            screen.id = "hotel_default";
            screen.name = "酒店桌面";
            screen.layout = "hotel_grid";
        }
        RemoteConfig.applyScreenEndpoints(screen);
        render();
        loadRemoteLayoutIfNeeded();
        lastConfigVersion = RemoteConfig.configVersion();
        EventBus.getDefault().register(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 主接口配置（screens/接口地址/公告）更新后无需重启，回到界面即刷新
        if (RemoteConfig.configVersion() != lastConfigVersion) {
            refreshFromConfig();
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEndpointChanged(RefreshEvent event) {
        if (event != null && event.type == RefreshEvent.TYPE_ENDPOINT_CHANGE) {
            // 当前界面使用的接口发生变化：重新读取屏幕配置并重绘（公告/入口随主接口更新）
            refreshFromConfig();
        }
    }

    /** 重新读取当前屏幕配置并重绘（保留 screenId，插件布局会重新下载/回退缓存） */
    private void refreshFromConfig() {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        lastConfigVersion = RemoteConfig.configVersion();
        String id = screen != null && screen.id != null ? screen.id : RemoteConfig.defaultScreenId();
        RemoteConfig.Screen latest = RemoteConfig.findScreen(id);
        if (latest != null) {
            screen = latest;
        }
        render();
        loadRemoteLayoutIfNeeded();
    }

    private void render() {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        String title = screen.title != null && !screen.title.isEmpty() ? screen.title : screen.name;
        tvTitle.setText(title == null ? "" : title);
        tvClock.setVisibility(screen.showClock ? View.VISIBLE : View.GONE);
        if (screen.showClock && !clockRunning) {
            clockRunning = true;
            clockTask.run();
        }
        // 滚动通知：屏幕自带 ticker 优先；未配置时回退主接口 notice 全局公告。
        // 支持 notice.speed / notice.pause（整条走完后的停顿秒数）。
        NoticeBar.showOn(this, tvTicker, NoticeBar.SCREEN_HOTEL, screen.ticker);
        if (screen.background != null && !screen.background.isEmpty()) {
            ivBg.setVisibility(View.VISIBLE);
            ImgUtil.load(screen.background, ivBg, 0, 1920, 1080);
        }
        if (screen.logo != null && !screen.logo.isEmpty()) {
            ivLogo.setVisibility(View.VISIBLE);
            EntryDispatcher.loadIcon(this, ivLogo, screen.logo);
        }
        body.removeAllViews();
        String layout = screen.layout == null ? "" : screen.layout.toLowerCase(Locale.ROOT);
        if ("hotel_list".equals(layout) || "list".equals(layout)) {
            buildListLayout();
        } else if ("hotel_showcase".equals(layout) || "showcase".equals(layout)) {
            buildShowcaseLayout();
        } else {
            buildGridLayout();
        }
        focusFirstTile();
    }

    private List<RemoteConfig.Entry> entries() {
        if (screen.entries != null && !screen.entries.isEmpty()) {
            return screen.entries;
        }
        List<RemoteConfig.Entry> list = new ArrayList<>();
        list.add(entry("点播", "page", "vod", "hm_vod"));
        list.add(entry("直播", "page", "live", "hm_live"));
        list.add(entry("搜索", "page", "search", "hm_search"));
        list.add(entry("收藏", "page", "collect", "hm_fav"));
        list.add(entry("历史", "page", "history", "hm_history"));
        list.add(entry("应用", "page", "apps", "hm_drawer"));
        list.add(entry("组网", "page", "hin2n", "ic_hin2n"));
        return list;
    }

    private RemoteConfig.Entry entry(String name, String type, String target, String icon) {
        RemoteConfig.Entry e = new RemoteConfig.Entry();
        e.name = name;
        e.type = type;
        e.target = target;
        e.icon = icon;
        return e;
    }

    /** 宫格布局：columns × rows 大卡片 */
    private void buildGridLayout() {
        int columns = Math.max(1, screen.columns);
        int rows = Math.max(1, screen.rows);
        List<RemoteConfig.Entry> list = entries();
        if (rows <= 0) {
            rows = (list.size() + columns - 1) / columns;
        }
        LinearLayout columnLayout = new LinearLayout(this);
        columnLayout.setOrientation(LinearLayout.VERTICAL);
        columnLayout.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        int index = 0;
        for (int r = 0; r < rows && index < list.size(); r++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
            for (int c = 0; c < columns; c++) {
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
                lp.setMargins(dp(8), dp(8), dp(8), dp(8));
                if (index < list.size()) {
                    row.addView(createTile(list.get(index), false), lp);
                    index++;
                } else {
                    View placeholder = new View(this);
                    placeholder.setLayoutParams(lp);
                    row.addView(placeholder);
                }
            }
            columnLayout.addView(row);
        }
        body.addView(columnLayout);
    }

    /** 列表布局：左侧纵向菜单 + 右侧大图预览（焦点移动时更新预览） */
    private void buildListLayout() {
        List<RemoteConfig.Entry> list = entries();
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        left.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
        for (RemoteConfig.Entry e : list) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1);
            lp.setMargins(dp(4), dp(4), dp(4), dp(4));
            left.addView(createTile(e, false), lp);
        }

        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setGravity(Gravity.CENTER);
        right.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 2));
        ImageView preview = new ImageView(this);
        preview.setId(android.R.id.icon);
        preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        preview.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        TextView previewName = new TextView(this);
        previewName.setId(android.R.id.text1);
        previewName.setTextColor(Color.WHITE);
        previewName.setTextSize(TypedValue.COMPLEX_UNIT_MM, 26);
        previewName.setGravity(Gravity.CENTER);
        previewName.setPadding(dp(10), dp(10), dp(10), dp(10));
        previewName.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        right.addView(preview);
        right.addView(previewName);

        root.addView(left);
        root.addView(right);
        body.addView(root);

        // 默认选中第一项
        if (!list.isEmpty()) {
            showPreview(preview, previewName, list.get(0));
        }
        for (int i = 0; i < left.getChildCount() && i < list.size(); i++) {
            final RemoteConfig.Entry e = list.get(i);
            View tile = left.getChildAt(i);
            tile.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View v, boolean hasFocus) {
                    if (hasFocus) {
                        showPreview(preview, previewName, e);
                    }
                }
            });
        }
    }

    private void showPreview(ImageView preview, TextView name, RemoteConfig.Entry e) {
        String pic = e.pic != null && !e.pic.isEmpty() ? e.pic : e.icon;
        if (pic != null && !pic.isEmpty()) {
            ImgUtil.load(pic, preview, dp(8));
        } else {
            preview.setImageResource(R.drawable.img_loading_placeholder);
        }
        String text = e.name == null ? "" : e.name;
        if (e.desc != null && !e.desc.isEmpty()) {
            text = text + "\n" + e.desc;
        }
        name.setText(text);
    }

    /** 展示布局：顶部大图 + 底部入口一排 */
    private void buildShowcaseLayout() {
        List<RemoteConfig.Entry> list = entries();
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        ImageView banner = new ImageView(this);
        banner.setScaleType(ImageView.ScaleType.CENTER_CROP);
        banner.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 2));
        String bannerPic = screen.background != null && !screen.background.isEmpty()
                ? screen.background
                : (list.isEmpty() ? "" : (list.get(0).pic != null ? list.get(0).pic : list.get(0).icon));
        if (!bannerPic.isEmpty()) {
            ImgUtil.load(bannerPic, banner, dp(10));
        } else {
            banner.setImageResource(R.drawable.img_loading_placeholder);
        }
        root.addView(banner);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        for (RemoteConfig.Entry e : list) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
            lp.setMargins(dp(6), dp(6), dp(6), dp(6));
            row.addView(createTile(e, false), lp);
        }
        root.addView(row);
        body.addView(root);
    }

    /** 创建可聚焦入口卡片；horizontal=false 时图标在上、文字在下 */
    private View createTile(final RemoteConfig.Entry entry, boolean horizontal) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(horizontal ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setFocusable(true);
        box.setFocusableInTouchMode(true);
        box.setClickable(true);
        box.setBackgroundResource(R.drawable.shape_hotel_tile);
        box.setPadding(dp(10), dp(10), dp(10), dp(10));

        ImageView icon = new ImageView(this);
        if (!horizontal && entry.pic != null && !entry.pic.isEmpty()) {
            // 卡片大图模式
            icon.setScaleType(ImageView.ScaleType.CENTER_CROP);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1);
            icon.setLayoutParams(lp);
            ImgUtil.load(entry.pic, icon, dp(8));
        } else {
            icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            int size = horizontal ? dp(48) : dp(72);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            if (!horizontal) {
                lp.topMargin = dp(10);
            }
            if (horizontal) {
                lp.rightMargin = dp(16);
            }
            icon.setLayoutParams(lp);
            EntryDispatcher.loadIcon(this, icon, entry.icon);
        }
        box.addView(icon);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setGravity(Gravity.CENTER);
        TextView name = new TextView(this);
        name.setText(entry.name == null ? "" : entry.name);
        name.setTextColor(Color.WHITE);
        name.setTextSize(TypedValue.COMPLEX_UNIT_MM, 26);
        name.setGravity(Gravity.CENTER);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        texts.addView(name);
        if (entry.desc != null && !entry.desc.isEmpty()) {
            TextView desc = new TextView(this);
            desc.setText(entry.desc);
            desc.setTextColor(0xAAFFFFFF);
            desc.setTextSize(TypedValue.COMPLEX_UNIT_MM, 18);
            desc.setGravity(Gravity.CENTER);
            desc.setSingleLine(true);
            desc.setEllipsize(android.text.TextUtils.TruncateAt.END);
            texts.addView(desc);
        }
        if (horizontal) {
            texts.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            texts.setGravity(Gravity.CENTER_VERTICAL);
            name.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        }
        box.addView(texts);

        box.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                EntryDispatcher.open(HotelActivity.this, entry);
            }
        });
        return box;
    }

    private void focusFirstTile() {
        body.post(new Runnable() {
            @Override
            public void run() {
                View first = findFirstFocusable(body);
                if (first != null) {
                    first.requestFocus();
                }
            }
        });
    }

    private View findFirstFocusable(ViewGroup parent) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (child.isFocusable()) {
                return child;
            }
            if (child instanceof ViewGroup) {
                View found = findFirstFocusable((ViewGroup) child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private int dp(int mm) {
        return me.jessyan.autosize.utils.AutoSizeUtils.mm2px(this, mm);
    }

    // ================= 布局插件下载 / 缓存 =================

    private void loadRemoteLayoutIfNeeded() {
        final String raw = screen.layoutUrl;
        if (raw == null || raw.isEmpty()) {
            return;
        }
        // 支持相对地址（相对主配置所在目录），与 live/epg 等接口解析规则一致
        final String url = RemoteConfig.resolveInterfaceUrl(raw);
        new Thread(new Runnable() {
            @Override
            public void run() {
                final RemoteConfig.Screen merged = downloadAndMerge(url);
                if (merged == null) {
                    return;
                }
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }
                        screen = merged;
                        render();
                    }
                });
            }
        }).start();
    }

    /** 下载布局 JSON（失败读本地缓存），并与主接口内联配置合并 */
    private RemoteConfig.Screen downloadAndMerge(String url) {
        String json = null;
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setRequestProperty("User-Agent", "TVBox");
            if (conn.getResponseCode() == 200) {
                InputStream in = conn.getInputStream();
                json = readAll(in);
                in.close();
                writeCache(url, json);
            }
        } catch (Throwable ignored) {
        }
        if (json == null || json.isEmpty()) {
            json = readCache(url);
        }
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            JSONObject obj = new JSONObject(json);
            RemoteConfig.Screen s = RemoteConfig.Screen.from(obj);
            // 插件未提供字段时回退主接口内联配置
            if (s.id.isEmpty()) s.id = screen.id;
            if (s.name.isEmpty()) s.name = screen.name;
            if (s.layout.isEmpty() || "standard".equals(s.layout)) s.layout = screen.layout;
            if (s.title.isEmpty()) s.title = screen.title;
            if (s.logo.isEmpty()) s.logo = screen.logo;
            if (s.background.isEmpty()) s.background = screen.background;
            if (s.ticker.isEmpty()) s.ticker = screen.ticker;
            if (s.entries == null || s.entries.isEmpty()) s.entries = screen.entries;
            return s;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private File cacheFile(String url) {
        String name = Integer.toHexString(url.hashCode());
        return new File(getFilesDir(), "hotel_layout_" + name + ".json");
    }

    private void writeCache(String url, String json) {
        try {
            FileOutputStream out = new FileOutputStream(cacheFile(url));
            out.write(json.getBytes("UTF-8"));
            out.close();
        } catch (Throwable ignored) {
        }
    }

    private String readCache(String url) {
        try {
            File f = cacheFile(url);
            if (!f.exists()) {
                return "";
            }
            FileInputStream in = new FileInputStream(f);
            String s = readAll(in);
            in.close();
            return s;
        } catch (Throwable ignored) {
            return "";
        }
    }

    private String readAll(InputStream in) throws Exception {
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int len;
        while ((len = in.read(buf)) > 0) {
            bos.write(buf, 0, len);
        }
        return new String(bos.toByteArray(), "UTF-8");
    }

    /** 界面（屏幕）切换：列出主接口 screens 配置的界面 */
    public void showScreenSwitch() {
        List<RemoteConfig.Screen> screens = RemoteConfig.screens();
        if (screens.isEmpty()) {
            android.widget.Toast.makeText(this, "主接口未配置 screens 界面列表", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        com.github.tvbox.osc.ui.dialog.SelectDialog<RemoteConfig.Screen> dialog =
                new com.github.tvbox.osc.ui.dialog.SelectDialog<>(this);
        com.owen.tvrecyclerview.widget.TvRecyclerView tvRecyclerView = dialog.findViewById(R.id.list);
        tvRecyclerView.setLayoutManager(new com.owen.tvrecyclerview.widget.V7GridLayoutManager(dialog.getContext(), 1));
        dialog.setTip("选择界面");
        // 当前所在界面默认勾选（原实现固定传 0，切换后选中项不正确）
        int selected = 0;
        if (screen != null && screen.id != null) {
            for (int i = 0; i < screens.size(); i++) {
                if (screen.id.equals(screens.get(i).id)) {
                    selected = i;
                    break;
                }
            }
        }
        dialog.setAdapter(tvRecyclerView,
                new com.github.tvbox.osc.ui.adapter.SelectDialogAdapter.SelectDialogInterface<RemoteConfig.Screen>() {
                    @Override
                    public void click(RemoteConfig.Screen value, int pos) {
                        RemoteConfig.applyScreenEndpoints(value);
                        openScreen("standard".equalsIgnoreCase(value.layout) ? "standard" : value.id);
                    }

                    @Override
                    public String getDisplay(RemoteConfig.Screen val) {
                        return val.name == null || val.name.isEmpty() ? val.id : val.name;
                    }
                },
                new androidx.recyclerview.widget.DiffUtil.ItemCallback<RemoteConfig.Screen>() {
                    @Override
                    public boolean areItemsTheSame(RemoteConfig.Screen oldItem, RemoteConfig.Screen newItem) {
                        return oldItem.id.equals(newItem.id);
                    }

                    @Override
                    public boolean areContentsTheSame(RemoteConfig.Screen oldItem, RemoteConfig.Screen newItem) {
                        return oldItem.id.equals(newItem.id);
                    }
                }, screens, selected);
        dialog.show();
    }

    private void openScreen(String id) {
        android.content.Intent intent;
        if (id == null || id.isEmpty() || "standard".equals(id)) {
            intent = new android.content.Intent(this, HomeActivity.class);
        } else {
            intent = new android.content.Intent(this, HotelActivity.class);
            intent.putExtra("screenId", id);
        }
        finish();
        startActivity(intent);
        overridePendingTransition(0, 0);
    }

    @Override
    protected void onDestroy() {
        clockRunning = false;
        handler.removeCallbacksAndMessages(null);
        try {
            EventBus.getDefault().unregister(this);
        } catch (Throwable ignored) {
        }
        NoticeBar.hideOn(tvTicker, NoticeBar.SCREEN_HOTEL);
        super.onDestroy();
    }
}
