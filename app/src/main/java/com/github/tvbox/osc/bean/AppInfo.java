package com.github.tvbox.osc.bean;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;

import com.github.tvbox.osc.base.App;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class AppInfo {

    public String name;
    public Drawable icon;
    public String pack;
    /** 本机安装版本号，取不到时为 0（仅用于应用市场更新检测） */
    public int versionCode;
    /** 应用市场标记：存在更高版本 */
    public boolean updateAvailable;

    public AppInfo(String name, String pack, Drawable icon) {
        this(name, pack, icon, 0);
    }

    public AppInfo(String name, String pack, Drawable icon, int versionCode) {
        this.name = name;
        this.pack = pack;
        this.icon = icon;
        this.versionCode = versionCode;
    }

    public static AppInfo get(ApplicationInfo info) {
        Drawable icon = info.loadIcon(App.getInstance().getPackageManager());
        String name = info.loadLabel(App.getInstance().getPackageManager()).toString();
        String pack = info.packageName;
        int versionCode = 0;
        try {
            versionCode = App.getInstance().getPackageManager()
                    .getPackageInfo(pack, 0).versionCode;
        } catch (PackageManager.NameNotFoundException ignored) {
        } catch (Throwable ignored) {
        }
        return new AppInfo(name, pack, icon, versionCode);
    }

    public String getName() {
        return name;
    }

    public String getPack() {
        return pack;
    }

    public Drawable getIcon() {
        return icon;
    }

    public static class Sorter implements Comparator<AppInfo> {

        public static void sort(List<AppInfo> items) {
            Collections.sort(items, new Sorter());
        }

        @Override
        public int compare(AppInfo info1, AppInfo info12) {
            return info1.getName().compareToIgnoreCase(info12.getName());
        }
    }
}