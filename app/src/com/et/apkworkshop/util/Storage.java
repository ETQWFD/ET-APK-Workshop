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

    /**
     * 解析一个可用的工程目录：优先公共 lookapks/projects 下，
     * 实际写入测试失败则自动回退到应用私有目录（无需权限）。
     * 返回的目录保证已存在且实际可写（除非设备存储本身损坏）。
     */
    public static File resolveProjectDir(String name) {
        if (name == null || name.isEmpty()) name = "proj_" + System.currentTimeMillis() % 100000;
        // 去掉路径分隔符，只保留目录名
        name = new File(name).getName();
        File dir = new File(getProjectsDir(), name);
        if (ensureDir(dir) && isWritable(dir)) return dir;
        // 回退：应用私有目录（外部存储私有区，无需任何权限）
        Context ctx = AppContext.get();
        File fallbackRoot;
        if (ctx != null && ctx.getExternalFilesDir(null) != null) {
            fallbackRoot = new File(ctx.getExternalFilesDir(null), "projects");
        } else {
            fallbackRoot = new File(System.getProperty("java.io.tmpdir"), "projects");
        }
        ensureDir(fallbackRoot);
        File fd = new File(fallbackRoot, name);
        if (ensureDir(fd) && isWritable(fd)) return fd;
        // 最后兜底：缓存目录
        File cd = new File(ctx != null ? ctx.getCacheDir() : new File(System.getProperty("java.io.tmpdir")), name);
        ensureDir(cd);
        return cd;
    }

    /** 目录是否真正可用（存在 + 实际可写） */
    public static boolean isUsableDir(File dir) {
        return dir != null && ensureDir(dir) && isWritable(dir);
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
