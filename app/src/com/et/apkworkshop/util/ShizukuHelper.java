package com.et.apkworkshop.util;

import android.content.Context;
import android.content.pm.PackageManager;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * Shizuku 临时权限助手。
 * Shizuku 是一款通过 adb 或 root 启动的权限管理工具，可让普通应用获得 shell (uid 2000) 级别的执行权限。
 * 本类提供最佳-effort 的 Shizuku 集成：若 Shizuku 可用且已授权，可执行更高权限的命令；
 * 否则自动回退到普通应用权限。正常模式下不主动请求 root。
 */
public final class ShizukuHelper {

    private static final String SHIZUKU_PACKAGE = "moe.shizuku.privileged.api";
    private static volatile Boolean available = null;

    private ShizukuHelper() {}

    /** 检测 Shizuku 是否已安装 */
    public static boolean isInstalled(Context ctx) {
        try {
            ctx.getPackageManager().getPackageInfo(SHIZUKU_PACKAGE, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    /** 检测 Shizuku 是否正在运行且本应用已授权 */
    public static boolean isActive() {
        if (available != null) return available;
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"sh", "-c", "shizuku echo ok 2>/dev/null"});
            BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line = r.readLine();
            r.close();
            p.waitFor();
            available = line != null && line.contains("ok");
        } catch (Exception e) {
            available = false;
        }
        return available;
    }

    /**
     * 执行命令。优先使用 Shizuku 权限，失败则回退普通 shell。
     * @return 命令输出
     */
    public static String exec(String cmd) {
        try {
            if (isActive()) {
                Process p = Runtime.getRuntime().exec(new String[]{"sh", "-c", "shizuku " + cmd});
                return readStream(p);
            }
        } catch (Exception ignored) {}
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"sh", "-c", cmd});
            return readStream(p);
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 使用 Shizuku 读取目标应用的 dex 内存映射（需 root 或 Shizuku 高级权限）。
     * 非 root 设备上此功能受限，仅作最佳尝试。
     */
    public static String dumpProcessMaps(int pid) {
        return exec("cat /proc/" + pid + "/maps 2>/dev/null");
    }

    private static String readStream(Process p) {
        try {
            BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line).append("\n");
            r.close();
            p.waitFor();
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }
}
