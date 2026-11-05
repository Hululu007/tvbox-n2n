package com.github.catvod.crawler.python;

import com.github.catvod.crawler.Spider;

import java.util.Map;

public interface IPyLoader {
    void clear();
    void remove(String key);
    void setConfig(String jsonStr);
    void setRecentPyKey(String pyApi);
    Spider getSpider(String key, String cls, String ext);
    Object[] proxyInvoke(Map<String, String> params);
}
