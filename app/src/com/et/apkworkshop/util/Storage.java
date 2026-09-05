package com.et.apkworkshop.util;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;

import java.io.File;

/**
 * 统一存储管理：所有工程文件存放在 /storage/emulated/0/lookapks/
 * 支持 Android 5.0 ~ 14，自动处理各版本存储权限差异。
 */
public final class Storage {
    public static final String ROOT_NAME = "lookapks";

    private Storage() {}

    /** 根目录：/storage/emulated/0/lookapks/ */
    public static File getRoot() {
        File f = new File(Environment.getExternalStorageDirectory(), ROOT_NAME);
        if (!f.exists()) f.mkdirs();
        return f;
    }

    /** 工程目录：/storage/emulated/0/lookapks/projects/ */
    public static File getProjectsDir() {
        File f = new File(getRoot(), "projects");
        if (!f.exists()) f.mkdirs();
        return f;
    }

    /** 输出目录：/storage/emulated/0/lookapks/output/ */
    public static File getOutputDir() {
        File f = new File(getRoot(), "output");
        if (!f.exists()) f.mkdirs();
        return f;
    }

    /** 检查是否有存储权限 */
    public static boolean hasPermission(Context ctx) {
        if (Build.VERSION.SDK_INT >= 30) {
            // Android 11+: 需要"所有文件访问权限"
            return Environment.isExternalStorageManager();
        }
        if (Build.VERSION.SDK_INT >= 23) {
            return ctx.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return true; // API < 23 安装时自动授予
    }

    /** 获取存储权限申请的 Intent（Android 11+ 跳转设置页） */
    public static android.content.Intent getPermissionIntent() {
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                android.content.Intent i = new android.content.Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                i.setData(android.net.Uri.parse("package:com.et.apkworkshop"));
                return i;
            } catch (Exception e) {
                return new android.content.Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
            }
        }
        return null;
    }
}
