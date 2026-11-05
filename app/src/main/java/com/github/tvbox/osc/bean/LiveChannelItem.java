package com.github.tvbox.osc.bean;

import java.util.ArrayList;

/**
 * @author pj567
 * @date :2021/1/12
 * @description:
 */
public class LiveChannelItem {
    /**
     * channelIndex : 频道索引号
     * channelNum : 频道名称
     * channelSourceNames : 频道源名称
     * channelUrls : 频道源地址
     * sourceIndex : 频道源索引
     * sourceNum : 频道源总数
     */
    private int channelIndex;
    private int channelNum;
    private String channelName;
    private ArrayList<String> channelSourceNames;
    private ArrayList<String> channelUrls;
    /** 每条线路由接口下发的默认解码方式（soft/hard/system/exo/ali，空=跟随全局） */
    private ArrayList<String> channelDecodes = new ArrayList<>();
    public int sourceIndex = 0;
    public int sourceNum = 0;
    public boolean include_back = false;

    public void setinclude_back(boolean include_back) {
        this.include_back = include_back;
    }

    public boolean getinclude_back() {
        return include_back;
    }


    public void setChannelIndex(int channelIndex) {
        this.channelIndex = channelIndex;
    }

    public int getChannelIndex() {
        return channelIndex;
    }

    public void setChannelNum(int channelNum) {
        this.channelNum = channelNum;
    }

    public int getChannelNum() {
        return channelNum;
    }

    public void setChannelName(String channelName) {
        this.channelName = channelName;
    }

    public String getChannelName() {
        return channelName;
    }

    public ArrayList<String> getChannelUrls() {
        return channelUrls;
    }

    public void setChannelUrls(ArrayList<String> channelUrls) {
        this.channelUrls = channelUrls;
        sourceNum = channelUrls.size();
    }

    /** 每条线路的接口默认解码方式（可为空列表） */
    public void setChannelDecodes(ArrayList<String> channelDecodes) {
        this.channelDecodes = channelDecodes == null ? new ArrayList<String>() : channelDecodes;
    }

    public ArrayList<String> getChannelDecodes() {
        return channelDecodes;
    }

    /** 当前线路由接口下发的默认解码方式，未下发返回空串 */
    public String getDecode() {
        return getDecode(sourceIndex);
    }

    public String getDecode(int index) {
        if (channelDecodes == null || index < 0 || index >= channelDecodes.size()) return "";
        String decode = channelDecodes.get(index);
        return decode == null ? "" : decode;
    }

    public String getUrl(int index) {
        if (channelUrls == null || index < 0 || index >= channelUrls.size()) return "";
        return channelUrls.get(index);
    }
    public void preSource() {
        sourceIndex--;
        if (sourceIndex < 0) sourceIndex = sourceNum - 1;
    }
    public void nextSource() {
        sourceIndex++;
        if (sourceIndex == sourceNum) sourceIndex = 0;
    }

    public void setSourceIndex(int sourceIndex) {
        this.sourceIndex = sourceIndex;
    }

    public int getSourceIndex() {
        return sourceIndex;
    }

    public String getUrl() {
        return channelUrls.get(sourceIndex);
    }

    public int getSourceNum() {
        return sourceNum;
    }

    public ArrayList<String> getChannelSourceNames() {
        return channelSourceNames;
    }

    public void setChannelSourceNames(ArrayList<String> channelSourceNames) {
        this.channelSourceNames = channelSourceNames;
    }

    public String getSourceName() {
        return channelSourceNames.get(sourceIndex);
    }
}