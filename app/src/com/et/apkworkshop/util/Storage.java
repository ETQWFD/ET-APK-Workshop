package com.et.apkworkshop.util;

import android.content.Context;
import android.os.Build;
import android.os.Environment;

import java.io.File;

/**
 * 统一存储管理。优先使用 /storage/emulated/0/lookapks/，
 * 若无权限自动回退到应用私有外部目录（无需权限）。
 */
public final class Storage {
    public static final String ROOT_NAME = "lookapks";
    private static File cachedRoot = null;
    private static Boolean cachedWritable = null;

    private Storage() {}

    /**
     * 获取可用的根目录。优先外部存储 lookapks（必须实际可写），
     * 不可写则回退应用私有外部目录（无需任何权限，一定可用）。
     */
    public static File getRoot() {
        if (cachedRoot != null && cachedRoot.exists() && isWritable(cachedRoot)) return cachedRoot;
        File ext = new File(Environment.getExternalStorageDirectory(), ROOT_NAME);
        if (ensureDir(ext) && isWritable(ext)) {
            cachedRoot = ext;
            return ext;
        }
        Context ctx = AppContext.get();
        File fallback;
        if (ctx != null && ctx.getExternalFilesDir(null) != null) {
            fallback = new File(ctx.getExternalFilesDir(null), ROOT_NAME);
        } else {
            fallback = new File(System.getProperty("java.io.tmpdir"), ROOT_NAME);
        }
        ensureDir(fallback);
        cachedRoot = fallback;
        return fallback;
    }

    /** 测试目录是否实际可写 */
    private static boolean isWritable(File dir) {
        try {
            if (!dir.exists() || !dir.isDirectory()) return false;
            File test = new File(dir, ".wtest_" + System.currentTimeMillis());
            java.io.FileOutputStream out = new java.io.FileOutputStream(test);
            try { out.write(new byte[]{1}); } finally { out.close(); }
            boolean ok = test.exists() && test.length() == 1;
            test.delete();
            return ok;
        } catch (Exception e) {
            return false;
        }
    }

    public static File getProjectsDir() {
        File f = new File(getRoot(), "projects");
        ensureDir(f);
        return f;
    }

    public static File getOutputDir() {
        File f = new File(getRoot(), "output");
        ensureDir(f);
        return f;
    }

    /** 确保目录存在，返回是否成功（已存在也算成功） */
    public static boolean ensureDir(File dir) {
        if (dir == null) return false;
        if (dir.exists()) return dir.isDirectory();
        if (dir.mkdirs()) return true;
        return dir.exists() && dir.isDirectory();
    }

    /** 检查外部存储是否可写（不是回退模式） */
    public static boolean isExternalWritable() {
        if (cachedWritable != null) return cachedWritable;
        try {
            File root = new File(Environment.getExternalStorageDirectory(), ROOT_NAME);
            if (!ensureDir(root)) { cachedWritable = false; return false; }
            File test = new File(root, ".t" + System.currentTimeMillis());
            java.io.FileOutputStream out = new java.io.FileOutputStream(test);
            try { out.write(new byte[]{1}); } finally { out.close(); }
            boolean ok = test.exists() && test.length() == 1;
            test.delete();
            cachedWritable = ok;
            return ok;
        } catch (Exception e) {
            cachedWritable = false;
            return false;
        }
    }

    public static boolean hasPermission(Context ctx) {
        return isExternalWritable();
    }

    /** 重置缓存（权限变更后调用） */
    public static void reset() {
        cachedRoot = null;
        cachedWritable = null;
    }

    public static android.content.Intent getPermissionIntent() {
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                android.content.Intent i = new android.content.Intent(
                        android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                i.setData(android.net.Uri.parse("package:com.et.apkworkshop"));
                return i;
            } catch (Exception e) {
                return new android.content.Intent(
                        android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
            }
        }
        return null;
    }
}
