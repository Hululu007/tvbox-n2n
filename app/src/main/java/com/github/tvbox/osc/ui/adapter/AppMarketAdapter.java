package com.github.tvbox.osc.ui.adapter;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.base.App;
import com.github.tvbox.osc.bean.AppMarketItem;
import com.github.tvbox.osc.n2n.AppMarketLoader;
import com.github.tvbox.osc.util.ImgUtil;

import java.util.ArrayList;

/** 应用市场卡片：图标、名称、版本、大小、下载量、状态按钮 */
public class AppMarketAdapter extends BaseQuickAdapter<AppMarketItem, BaseViewHolder> {

    public static final int ACTION_INSTALL = 0;
    public static final int ACTION_OPEN = 1;
    public static final int ACTION_UPDATE = 2;
    public static final int ACTION_DOWNLOADING = 3;

    private int itemWidth = 0;

    public AppMarketAdapter() {
        super(R.layout.item_app_market, new ArrayList<>());
    }

    /** 由 Activity 按列宽动态设置，保证电视 4 列 / 手机 5 列都铺满 */
    public void setItemWidth(int width) {
        if (this.itemWidth != width && width > 0) {
            this.itemWidth = width;
            notifyDataSetChanged();
        }
    }

    @Override
    protected void convert(BaseViewHolder helper, AppMarketItem item) {
        Context ctx = App.getInstance();
        String unknown = ctx.getString(R.string.app_market_unknown);
        String displayName = item.displayName();
        helper.setText(R.id.tvAppName, displayName.isEmpty() ? unknown : displayName);

        helper.setText(R.id.tvVersion, ctx.getString(R.string.app_market_version,
                item.versionName.isEmpty() ? unknown : item.versionName));
        helper.setText(R.id.tvSize, ctx.getString(R.string.app_market_size,
                item.size.isEmpty() ? unknown : item.size));
        helper.setText(R.id.tvDownloads, ctx.getString(R.string.app_market_downloads,
                AppMarketItem.formatDownloads(item.downloads)));
        helper.setText(R.id.tvMarketAction, getActionText(ctx, item));

        ImageView icon = helper.getView(R.id.ivMarketIcon);
        ImgUtil.load(item.icon, icon, 10);

        if (itemWidth > 0) {
            View itemView = helper.itemView;
            ViewGroup.LayoutParams lp = itemView.getLayoutParams();
            if (lp != null && lp.width != itemWidth) {
                lp.width = itemWidth;
                itemView.setLayoutParams(lp);
            }
        }
    }

    /** 根据本机安装情况与线上版本号计算按钮状态 */
    public static int getActionState(AppMarketItem item) {
        if (item == null) {
            return ACTION_INSTALL;
        }
        if (AppMarketLoader.isDownloading(item.packageName)) {
            return ACTION_DOWNLOADING;
        }
        int installed = AppMarketLoader.installedVersionCode(App.getInstance(), item.packageName);
        if (installed < 0) {
            return ACTION_INSTALL;
        }
        if (item.versionCode > installed) {
            return ACTION_UPDATE;
        }
        return ACTION_OPEN;
    }

    public static String getActionText(Context ctx, AppMarketItem item) {
        int state = getActionState(item);
        switch (state) {
            case ACTION_OPEN:
                return ctx.getString(R.string.app_market_open);
            case ACTION_UPDATE:
                return ctx.getString(R.string.app_market_update);
            case ACTION_DOWNLOADING:
                return ctx.getString(R.string.app_market_downloading);
            case ACTION_INSTALL:
            default:
                return ctx.getString(R.string.app_market_install);
        }
    }
}
