// 文件: app/src/python/java/com/github/catvod/crawler/python/PyLoader.java
package com.github.catvod.crawler;

import android.Manifest;
import android.content.pm.PackageManager;
import android.util.Log;

import androidx.core.content.ContextCompat;

import com.github.catvod.crawler.python.IPyLoader;
import com.github.tvbox.osc.base.App;
import com.github.tvbox.osc.util.LOG;
import com.github.tvbox.osc.util.MD5;
import com.undcover.freedom.pyramid.PythonLoader;
import com.undcover.freedom.pyramid.PythonSpider;
import java.io.UnsupportedEncodingException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class pyLoader implements IPyLoader {
    // 延迟初始化：Python 运行时启动较重（解压资源、加载 .so），
    // 不能在构造 ApiConfig 时（主线程）同步启动，否则部分设备会黑屏/ANR。
    private volatile PythonLoader pythonLoader;
    private final ConcurrentHashMap<String, Spider> spiders;
    private String lastConfig = null; // 记录上次的配置

    public pyLoader() {
        spiders = new ConcurrentHashMap<>();
    }

    /** 首次真正需要执行 Python 源时才启动 Python 运行时，失败时返回 null（降级为空源） */
    private PythonLoader ensureLoader() {
        if (pythonLoader != null) {
            return pythonLoader;
        }
        synchronized (this) {
            if (pythonLoader == null) {
                try {
                    pythonLoader = PythonLoader.getInstance().setApplication(App.getInstance());
                } catch (Throwable th) {
                    // 部分设备/系统缺少 Python 运行所需组件：记录后降级，保证 App 仍可正常打开
                    Log.e("PyLoader", "Python 运行时初始化失败: " + th.getMessage(), th);
                    pythonLoader = null;
                }
            }
        }
        return pythonLoader;
    }

    @Override
    public void clear() {
        spiders.clear();
    }

    @Override
    public void setConfig(String jsonStr) {
        if (jsonStr != null && !jsonStr.equals(lastConfig)) {
            PythonLoader loader = ensureLoader();
            if (loader == null) {
                return;
            }
            try {
                Log.i("PyLoader", "echo-setConfig 初始化json ");
                loader.setConfig(jsonStr);
                lastConfig = jsonStr;
            } catch (Throwable th) {
                Log.e("PyLoader", "setConfig 失败: " + th.getMessage(), th);
            }
        }
    }

    private String recentPyApi;
    @Override
    public void setRecentPyKey(String pyApi) {
        recentPyApi = pyApi;
    }

    @Override
    public Spider getSpider(String key, String cls, String ext) {
        if (spiders.containsKey(key)) {
            Log.i("PyLoader", "echo-getSpider spider缓存: " + key);
            return spiders.get(key);
        }
        try {
            if (ContextCompat.checkSelfPermission(App.getInstance(), Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                Log.i("PyLoader", "无存储权限，终止执行");
                return new SpiderNull();
            }
            PythonLoader loader = ensureLoader();
            if (loader == null) {
                return new SpiderNull();
            }
            Log.i("PyLoader", "echo-getSpider url: " + getPyUrl(cls, ext));
            Spider sp = loader.getSpider(key, getPyUrl(cls, ext));
//            Log.i("PyLoader", "echo-getSpider homeContent: " + sp.homeContent(true));
            spiders.put(key, sp);
            Log.i("PyLoader", "echo-getSpider 加载spider: " + key);
            return sp;
        } catch (Throwable th) {
            th.printStackTrace();
        }
        return new SpiderNull();
    }

    @Override
    public Object[] proxyInvoke(Map<String, String> params){
        if(recentPyApi==null)return null;
        LOG.i("echo-recentPyApi" + recentPyApi);
        try {
            PythonSpider originalSpider = (PythonSpider) getSpider(MD5.string2MD5(recentPyApi), recentPyApi,"");
            return originalSpider.proxyLocal(params);
        } catch (Throwable th) {
            LOG.i("echo-proxyInvoke_Throwable:---" + th.getMessage());
            th.printStackTrace();
        }
        return null;
    }

    private String getPyUrl(String api, String ext) throws UnsupportedEncodingException {
        StringBuilder urlBuilder = new StringBuilder(api);
        if (!ext.isEmpty()) {
//            ext= URLEncoder.encode(ext,"utf8");
            urlBuilder.append(api.contains("?") ? "&" : "?").append("extend=").append(ext);
        }
        return urlBuilder.toString();
    }
}
