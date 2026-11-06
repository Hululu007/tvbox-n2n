package com.github.tvbox.osc.n2n;

import android.content.Context;

import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.event.RefreshEvent;
import com.github.tvbox.osc.util.EpgCache;
import com.github.tvbox.osc.util.MD5;

import org.greenrobot.eventbus.EventBus;

import java.io.File;

/**
 * 接口配置变更后的本地缓存清理与界面通知。
 *
 * <p>点播接口配置以 MD5(接口地址) 为文件名缓存在 filesDir 下，EPG 节目单缓存在
 * filesDir/epg_cache；进程内 ApiConfig 还有 {@code loaded} 标记。三者只要有一项残留，
 * 用户修改「点播 / 直播 / EPG 接口」后就必须重启 App 才能看到新接口内容。</p>
 *
 * <p>本类在接口地址发生变化时同步清理对应缓存，并发送 {@link RefreshEvent#TYPE_ENDPOINT_CHANGE}
 * 事件，让正在显示的界面立即重新加载，无需重启 App。</p>
 */
public final class EndpointCache {

    /** 事件 obj：本次发生变化的接口集合，如 "api" / "live,epg" */
    public static final String KEY_API = "api";
    public static final String KEY_LIVE = "live";
    public static final String KEY_EPG = "epg";

    private EndpointCache() {
    }

    /**
     * 接口地址变化回调（点播/直播/EPG 任一变化时调用）。
     *
     * @param ctx     应用上下文
     * @param key     变化的接口：api / live / epg
     * @param oldValue 变更前地址（可为空）
     * @param newValue 变更后地址（可为空）
     */
    public static void onEndpointChanged(Context ctx, String key, String oldValue, String newValue) {
        String oldV = oldValue == null ? "" : oldValue.trim();
        String newV = newValue == null ? "" : newValue.trim();
        if (oldV.equals(newV)) {
            return;
        }
        if (KEY_API.equals(key)) {
            if (ctx != null) {
                // 旧接口缓存、以及回退到曾用过的接口地址时可能命中的旧缓存，一起删除
                deleteVodCache(ctx, oldV);
                deleteVodCache(ctx, newV);
            }
            ApiConfig.get().invalidateConfig();
        } else if (KEY_LIVE.equals(key) || KEY_EPG.equals(key)) {
            // 直播源变化：EPG 频道名/日期缓存可能对应旧源，一并清空；
            // 同时清掉 ApiConfig 内存中的直播分组列表，否则 LivePlayActivity
            // 仍按旧分组的 URL 加载旧直播源（客户端改接口不生效）。
            EpgCache.clear();
            ApiConfig.get().clearLives();
        } else {
            return;
        }
        // 当前运行中的点播/直播/酒店界面立即重新加载（无订阅者时无副作用）
        EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_ENDPOINT_CHANGE, key));
    }

    /** 删除点播接口自身的磁盘缓存（filesDir/MD5(接口地址)） */
    private static void deleteVodCache(Context ctx, String apiUrl) {
        if (apiUrl == null || apiUrl.isEmpty()) {
            return;
        }
        try {
            File cache = new File(ctx.getFilesDir().getAbsolutePath() + "/" + MD5.encode(apiUrl));
            if (cache.exists()) {
                //noinspection ResultOfMethodCallIgnored
                cache.delete();
            }
        } catch (Throwable ignored) {
        }
    }
}
