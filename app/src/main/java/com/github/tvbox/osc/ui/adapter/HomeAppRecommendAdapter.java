package com.github.tvbox.osc.ui.adapter;

import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.n2n.EntryDispatcher;
import com.github.tvbox.osc.n2n.RemoteConfig;
import com.github.tvbox.osc.util.ImgUtil;

import java.util.ArrayList;

import me.jessyan.autosize.utils.AutoSizeUtils;

/**
 * 首页「应用推荐」适配器（主接口 home.appRecommends）。
 *
 * <p>卡片尺寸/排版与首页推荐影片卡片完全一致：宽高由 {@link ImgUtil.Style} 决定，
 * 未配置 style 时沿用布局默认的 vs_214 x vs_280。图标居中显示，底部显示应用名称，
 * 点击统一走 {@link EntryDispatcher}（默认按 app 类型启动/下载安装）。</p>
 */
public class HomeAppRecommendAdapter extends BaseQuickAdapter<RemoteConfig.AppRecommend, BaseViewHolder> {

    private int defaultWidth = ImgUtil.defaultWidth;
    private final ImgUtil.Style style;

    public HomeAppRecommendAdapter(ImgUtil.Style style) {
        super(R.layout.item_home_app_recommend, new ArrayList<>());
        this.style = style;
        if (style != null) {
            this.defaultWidth = ImgUtil.getStyleDefaultWidth(style);
        }
    }

    @Override
    protected void convert(BaseViewHolder helper, RemoteConfig.AppRecommend item) {
        if (item == null) {
            return;
        }
        helper.setText(R.id.tvName, item.name);
        TextView tvDesc = helper.getView(R.id.tvDesc);
        if (TextUtils.isEmpty(item.desc)) {
            tvDesc.setVisibility(View.GONE);
        } else {
            tvDesc.setText(item.desc);
            tvDesc.setVisibility(View.VISIBLE);
        }

        ImageView ivThumb = helper.getView(R.id.ivThumb);
        EntryDispatcher.loadIcon(mContext, ivThumb, item.icon);
        applyStyleToImage(ivThumb);

        View itemView = helper.itemView;
        itemView.setContentDescription(TextUtils.isEmpty(item.desc)
                ? item.name : item.name + " " + item.desc);
    }

    /**
     * 与 HomeHotVodAdapter 保持一致：按 style.ratio 动态设置卡片容器宽高。
     * style 为空时保留 XML 默认尺寸（vs_214 x vs_280）。
     */
    private void applyStyleToImage(ImageView ivThumb) {
        if (style == null || ivThumb == null) {
            return;
        }
        ViewGroup container = (ViewGroup) ivThumb.getParent();
        if (container == null) {
            return;
        }
        int width = defaultWidth;
        int height = (int) (width / style.ratio);
        ViewGroup.LayoutParams lp = container.getLayoutParams();
        lp.width = AutoSizeUtils.mm2px(mContext, width);
        lp.height = AutoSizeUtils.mm2px(mContext, height);
        container.setLayoutParams(lp);
    }

    /**
     * 将应用推荐条目转换为统一入口，复用 EntryDispatcher 的启动/下载安装逻辑。
     * type 留空时：配置了 pkg 按 app；否则若配置了 target 按 page 处理。
     */
    public static RemoteConfig.Entry toEntry(RemoteConfig.AppRecommend item) {
        if (item == null) {
            return null;
        }
        RemoteConfig.Entry e = new RemoteConfig.Entry();
        e.name = item.name;
        e.icon = item.icon;
        e.pkg = item.pkg;
        e.url = item.url;
        e.desc = item.desc;
        e.target = item.target;
        String type = item.type == null ? "" : item.type.trim();
        if (type.isEmpty()) {
            type = TextUtils.isEmpty(item.pkg) && !TextUtils.isEmpty(item.target) ? "page" : "app";
        }
        e.type = type;
        if ("app".equals(type) && TextUtils.isEmpty(e.pkg) && !TextUtils.isEmpty(item.target)) {
            // 兼容 target 直接填包名的写法
            e.pkg = item.target;
        }
        return e;
    }
}
