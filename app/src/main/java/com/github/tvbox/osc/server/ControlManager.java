package com.github.tvbox.osc.server;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;

import com.github.tvbox.osc.event.RefreshEvent;
import com.github.tvbox.osc.receiver.DetailReceiver;
import com.github.tvbox.osc.receiver.SearchReceiver;
import com.github.tvbox.osc.util.HawkConfig;
import com.orhanobut.hawk.Hawk;

import org.greenrobot.eventbus.EventBus;

import java.io.IOException;

import tv.danmaku.ijk.media.player.IjkMediaPlayer;

/**
 * @author pj567
 * @date :2021/1/4
 * @description:
 */
public class ControlManager {
    private static ControlManager instance;
    private RemoteServer mServer = null;
    public static Context mContext;

    private ControlManager() {

    }

    public static ControlManager get() {
        if (instance == null) {
            synchronized (ControlManager.class) {
                if (instance == null) {
                    instance = new ControlManager();
                }
            }
        }
        return instance;
    }

    public static void init(Context context) {
        mContext = context;
    }

    /**
     * 手机/电脑网页端扫码配置：立即写入本地并标记「用户手动配置」，
     * 这样即使配置对话框已关闭也能生效；主接口未要求强制覆盖时保留用户配置。
     */
    private void saveFromPhone(String hawkKey, String historyKey, String url) {
        try {
            String v = url == null ? "" : url.trim();
            Hawk.put(hawkKey, v);
            Hawk.put(hawkKey + "_manual", true);
            if (historyKey != null && !v.isEmpty()) {
                java.util.ArrayList<String> history = Hawk.get(historyKey, new java.util.ArrayList<String>());
                if (!history.contains(v)) {
                    history.add(0, v);
                }
                if (history.size() > 20) {
                    history.remove(20);
                }
                Hawk.put(historyKey, history);
            }
            // 点播/直播接口变更后，回到对应界面时由界面自行重新加载
        } catch (Throwable ignored) {
        }
    }

    public String getAddress(boolean local) {
        return local ? mServer.getLoadAddress() : mServer.getServerAddress();
    }

    public void startServer() {
        if (mServer != null) {
            return;
        }
        do {
            mServer = new RemoteServer(RemoteServer.serverPort, mContext);
            mServer.setDataReceiver(new DataReceiver() {
                @Override
                public void onTextReceived(String text) {
                    if (!TextUtils.isEmpty(text)) {
                        Intent intent = new Intent();
                        Bundle bundle = new Bundle();
                        bundle.putString("title", text);
                        intent.setAction(SearchReceiver.action);
                        intent.setPackage(mContext.getPackageName());
                        intent.setComponent(new ComponentName(mContext, SearchReceiver.class));
                        intent.putExtras(bundle);
                        mContext.sendBroadcast(intent);
                    }
                }

                @Override
                public void onApiReceived(String url) {
                    saveFromPhone(com.github.tvbox.osc.util.HawkConfig.API_URL,
                            com.github.tvbox.osc.util.HawkConfig.API_HISTORY, url);
                    EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_API_URL_CHANGE, url));
                }

                @Override
                public void onLiveReceived(String url) {
                    saveFromPhone(com.github.tvbox.osc.util.HawkConfig.LIVE_URL,
                            com.github.tvbox.osc.util.HawkConfig.LIVE_HISTORY, url);
                    EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_LIVE_URL_CHANGE, url));
                }

                @Override
                public void onEpgReceived(String url) {
                    saveFromPhone(com.github.tvbox.osc.util.HawkConfig.EPG_URL,
                            com.github.tvbox.osc.util.HawkConfig.EPG_HISTORY, url);
                    EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_EPG_URL_CHANGE, url));
                }

                @Override
                public void onProxysReceived(String url) {
                    saveFromPhone(com.github.tvbox.osc.util.HawkConfig.PROXY_SERVER, null, url);
                    EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_PROXYS_CHANGE, url));
                }

                @Override
                public void onPushReceived(String url) {
                    EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_PUSH_URL, url));
                }

                @Override
                public void onMirrorReceived(String id, String sourceKey) {
                    if (!TextUtils.isEmpty(id) && !TextUtils.isEmpty(sourceKey)) {
                        Intent intent = new Intent();
                        Bundle bundle = new Bundle();
                        bundle.putString("id", id);
                        bundle.putString("sourceKey", sourceKey);
                        intent.setAction(DetailReceiver.action);
                        intent.setPackage(mContext.getPackageName());
                        intent.setComponent(new ComponentName(mContext, DetailReceiver.class));
                        intent.putExtras(bundle);
                        mContext.sendBroadcast(intent);
                    }
                }
            });
            try {
                mServer.start();
                IjkMediaPlayer.setDotPort(Hawk.get(HawkConfig.DOH_URL, 0) > 0, RemoteServer.serverPort);
                break;
            } catch (IOException ex) {
                RemoteServer.serverPort++;
                mServer.stop();
            }
        } while (RemoteServer.serverPort < 9999);
    }

    public void stopServer() {
        if (mServer != null && mServer.isStarting()) {
            mServer.stop();
        }
    }
}