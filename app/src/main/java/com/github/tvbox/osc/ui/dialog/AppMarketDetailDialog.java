package com.github.tvbox.osc.ui.dialog;

import android.content.Context;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.bean.AppMarketItem;
import com.github.tvbox.osc.ui.adapter.AppMarketAdapter;
import com.github.tvbox.osc.util.ImgUtil;
import com.owen.tvrecyclerview.widget.TvRecyclerView;
import com.owen.tvrecyclerview.widget.V7LinearLayoutManager;

import java.util.ArrayList;
import java.util.List;

/**
 * 应用详情弹窗：大图标、名称、开发者、版本、大小、下载量、简介/描述、截图横滑与安装按钮。
 */
public class AppMarketDetailDialog extends BaseDialog {

    public interface OnActionListener {
        void onAction(AppMarketItem item);
    }

    private final OnActionListener actionListener;
    private AppMarketItem item;

    private ImageView ivIcon;
    private TextView tvName;
    private TextView tvDeveloper;
    private TextView tvSummary;
    private TextView tvVersion;
    private TextView tvSize;
    private TextView tvDownloads;
    private TextView tvDesc;
    private TextView tvScreenshotsLabel;
    private TextView tvAction;
    private TvRecyclerView mScreenshots;
    private ScreenshotAdapter screenshotAdapter;

    public AppMarketDetailDialog(Context context, AppMarketItem item, OnActionListener listener) {
        super(context, R.style.CustomDialogStyleDim);
        this.item = item;
        this.actionListener = listener;
        setContentView(R.layout.dialog_app_market_detail);
        setCanceledOnTouchOutside(false);

        ivIcon = findViewById(R.id.ivDetailIcon);
        tvName = findViewById(R.id.tvDetailName);
        tvDeveloper = findViewById(R.id.tvDetailDeveloper);
        tvSummary = findViewById(R.id.tvDetailSummary);
        tvVersion = findViewById(R.id.tvDetailVersion);
        tvSize = findViewById(R.id.tvDetailSize);
        tvDownloads = findViewById(R.id.tvDetailDownloads);
        tvDesc = findViewById(R.id.tvDetailDesc);
        tvScreenshotsLabel = findViewById(R.id.tvScreenshotsLabel);
        tvAction = findViewById(R.id.btnDetailAction);
        mScreenshots = findViewById(R.id.mScreenshotGrid);

        screenshotAdapter = new ScreenshotAdapter();
        mScreenshots.setLayoutManager(new V7LinearLayoutManager(context, 0, false));
        mScreenshots.setAdapter(screenshotAdapter);
        mScreenshots.setVisibility(View.GONE);
        tvScreenshotsLabel.setVisibility(View.GONE);

        tvAction.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (actionListener != null && item != null) {
                    actionListener.onAction(item);
                }
                dismiss();
            }
        });
        findViewById(R.id.btnDetailClose).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
            }
        });
        setData(item);
    }

    @Override
    public void show() {
        super.show();
        if (tvAction != null) {
            tvAction.requestFocus();
        }
    }

    /** 详情接口回来后刷新内容（列表字段先展示，避免等待） */
    public void setData(AppMarketItem item) {
        if (item == null) {
            return;
        }
        this.item = item;
        Context ctx = getContext();
        String unknown = ctx.getString(R.string.app_market_unknown);
        String name = item.displayName();
        tvName.setText(name.isEmpty() ? unknown : name);
        tvDeveloper.setText(ctx.getString(R.string.app_market_developer,
                item.developer.isEmpty() ? unknown : item.developer));
        if (item.summary.isEmpty()) {
            tvSummary.setVisibility(View.GONE);
        } else {
            tvSummary.setVisibility(View.VISIBLE);
            tvSummary.setText(item.summary);
        }
        tvVersion.setText(ctx.getString(R.string.app_market_version,
                item.versionName.isEmpty() ? unknown : item.versionName));
        tvSize.setText(ctx.getString(R.string.app_market_size,
                item.size.isEmpty() ? unknown : item.size));
        tvDownloads.setText(ctx.getString(R.string.app_market_downloads,
                AppMarketItem.formatDownloads(item.downloads)));
        String desc = item.description;
        if (desc.isEmpty()) {
            desc = item.summary;
        }
        tvDesc.setText(desc.isEmpty() ? ctx.getString(R.string.app_market_no_description) : desc);
        ImgUtil.load(item.icon, ivIcon, 10);
        tvAction.setText(AppMarketAdapter.getActionText(ctx, item));

        boolean hasShots = !item.screenshots.isEmpty();
        tvScreenshotsLabel.setVisibility(hasShots ? View.VISIBLE : View.GONE);
        mScreenshots.setVisibility(hasShots ? View.VISIBLE : View.GONE);
        List<String> shots = new ArrayList<>();
        if (hasShots) {
            shots.addAll(item.screenshots);
        }
        screenshotAdapter.setNewData(shots);
    }

    private static class ScreenshotAdapter extends BaseQuickAdapter<String, BaseViewHolder> {
        ScreenshotAdapter() {
            super(R.layout.item_app_market_screenshot, new ArrayList<String>());
        }

        @Override
        protected void convert(BaseViewHolder helper, String url) {
            ImageView iv = helper.getView(R.id.ivScreenshot);
            ImgUtil.load(url, iv, 8);
        }
    }
}
