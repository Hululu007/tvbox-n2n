package com.github.tvbox.osc.bean;

import androidx.annotation.NonNull;
import androidx.exifinterface.media.ExifInterface;

import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.PlayerHelper;
import com.orhanobut.hawk.Hawk;

import org.json.JSONException;
import org.json.JSONObject;

import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Objects;

import xyz.doikki.videoplayer.player.VideoView;

public class LivePlayerManager {
    /** 节目源（具体线路 URL）解码配置的存储前缀，与按频道名保存的旧配置隔离 */
    private static final String SOURCE_CONFIG_PREFIX = "live_source_decode_";
    JSONObject defaultPlayerConfig = new JSONObject();
    JSONObject currentPlayerConfig;

    public void init(VideoView videoView) {
        try {
            int playerType = Hawk.get(HawkConfig.LIVE_PLAYER_TYPE, -1);
            if (playerType == -1) playerType = Hawk.get(HawkConfig.PLAY_TYPE, 0);
            defaultPlayerConfig.put("pl", playerType);
            defaultPlayerConfig.put("ijk", Hawk.get(HawkConfig.IJK_CODEC, "软解码"));
            defaultPlayerConfig.put("pr", Hawk.get(HawkConfig.PLAY_RENDER, 0));
            defaultPlayerConfig.put("sc", Hawk.get(HawkConfig.PLAY_SCALE, 0));
        } catch (JSONException e) {
            e.printStackTrace();
        }
        getDefaultLiveChannelPlayer(videoView);
    }

    public void getDefaultLiveChannelPlayer(VideoView videoView) {
        PlayerHelper.updateCfg(videoView, defaultPlayerConfig);
        try {
            currentPlayerConfig = new JSONObject(defaultPlayerConfig.toString());
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    /**
     * 读取并应用解码配置。
     * 优先使用「节目源 URL」维度保存的配置（每个线路独立解码方式），
     * 未单独设置时回退到旧的「频道名」配置，最后回退到全局默认配置。
     */
    public void getLiveChannelPlayer(VideoView videoView, String channelName) {
        getLiveChannelPlayer(videoView, channelName, null);
    }

    public void getLiveChannelPlayer(VideoView videoView, String channelName, String sourceUrl) {
        JSONObject playerConfig = getSourceConfig(sourceUrl);
        if (playerConfig == null) {
            playerConfig = channelName == null ? null : Hawk.get(channelName, null);
        }
        if (playerConfig == null) {
            if (!currentPlayerConfig.toString().equals(defaultPlayerConfig.toString()))
                getDefaultLiveChannelPlayer(videoView);
            return;
        }
        if (playerConfig.toString().equals(currentPlayerConfig.toString()))
            return;

        try {
            if (playerConfig.getInt("pl") == currentPlayerConfig.getInt("pl")
                    && playerConfig.getInt("pr") == currentPlayerConfig.getInt("pr")
                    && playerConfig.getString("ijk").equals(currentPlayerConfig.getString("ijk"))) {
                videoView.setScreenScaleType(playerConfig.getInt("sc"));
            } else {
                PlayerHelper.updateCfg(videoView, playerConfig);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

        currentPlayerConfig = playerConfig;
    }

    public int getLivePlayerType() {
        int playerTypeIndex = 0;
        try {
            int playerType = currentPlayerConfig.getInt("pl");
            String ijkCodec = currentPlayerConfig.getString("ijk");
            switch (playerType) {
                case 0:
                    playerTypeIndex = 0;
                    break;
                case 1:
                    if (ijkCodec.equals("硬解码"))
                        playerTypeIndex = 1;
                    else
                        playerTypeIndex = 2;
                    break;
                case 2:
                    playerTypeIndex = 3;
                    break;
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return playerTypeIndex;
    }

    public int getLivePlayerScale() {
        try {
            return currentPlayerConfig.getInt("sc");
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return 0;
    }

    public void changeLivePlayerType(VideoView videoView, int playerType, String channelName) {
        changeLivePlayerType(videoView, playerType, channelName, null);
    }

    /**
     * 保存当前节目源（线路 URL）的解码方式并立即生效。
     * 同一频道切换线路时会读取各自线路保存的解码配置。
     */
    public void changeLivePlayerType(VideoView videoView, int playerType, String channelName, String sourceUrl) {
        JSONObject playerConfig = currentPlayerConfig;
        try {
            switch (playerType) {
                case 0:
                    playerConfig.put("pl", 0);
                    playerConfig.put("ijk", "软解码");
                    break;
                case 1:
                    playerConfig.put("pl", 1);
                    playerConfig.put("ijk", "硬解码");
                    break;
                case 2:
                    playerConfig.put("pl", 1);
                    playerConfig.put("ijk", "软解码");
                    break;
                case 3:
                    playerConfig.put("pl", 2);
                    playerConfig.put("ijk", "软解码");
                    break;
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        PlayerHelper.updateCfg(videoView, playerConfig);

        String storeKey = getSourceConfigKey(sourceUrl);
        if (storeKey == null) storeKey = channelName;
        if (playerConfig.toString().equals(defaultPlayerConfig.toString()))
            Hawk.delete(storeKey);
        else
            Hawk.put(storeKey, playerConfig);

        currentPlayerConfig = playerConfig;
    }

    /** 节目源是否单独保存过解码方式 */
    public boolean hasSourceDecode(String sourceUrl) {
        return getSourceConfig(sourceUrl) != null;
    }

    /** 返回节目源的解码方式简称，未单独设置返回空串（用于线路列表上的标记） */
    public String getSourceDecodeLabel(String sourceUrl) {
        JSONObject config = getSourceConfig(sourceUrl);
        if (config == null) return "";
        int playerType = config.optInt("pl", 0);
        String ijkCodec = config.optString("ijk", "软解码");
        if (playerType == 0) return "系统";
        if (playerType == 1) return ijkCodec.equals("硬解码") ? "硬解" : "软解";
        return "EXO";
    }

    private JSONObject getSourceConfig(String sourceUrl) {
        String key = getSourceConfigKey(sourceUrl);
        return key == null ? null : Hawk.get(key, null);
    }

    private String getSourceConfigKey(String sourceUrl) {
        if (sourceUrl == null || sourceUrl.isEmpty()) return null;
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] bytes = digest.digest(sourceUrl.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder(SOURCE_CONFIG_PREFIX);
            for (byte b : bytes) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Throwable t) {
            return SOURCE_CONFIG_PREFIX + sourceUrl.hashCode();
        }
    }

    public void changeLivePlayerScale(@NonNull VideoView videoView, int playerScale, String channelName){
        videoView.setScreenScaleType(playerScale);

        JSONObject playerConfig = currentPlayerConfig;
        try {
            playerConfig.put("sc", playerScale);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        if (playerConfig.toString().equals(defaultPlayerConfig.toString()))
            Hawk.delete(channelName);
        else
            Hawk.put(channelName, playerConfig);

        currentPlayerConfig = playerConfig;
    }
}