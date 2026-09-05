package com.et.apkworkshop.util;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 检测更新：通过 GitHub Releases API 查询最新版本，提示用户更新。
 * 仓库公开，无需认证即可访问 /releases/latest。
 */
public final class AppUpdate {
    // GitHub 仓库所有者（用户名），构建时填入
    public static final String REPO_OWNER = "ETQWFD";
    // GitHub 仓库名
    public static final String REPO_NAME = "ET-APK-Workshop";
    // 仓库主页（用户点击"更新"时跳转）
    public static final String REPO_URL = "https://github.com/" + REPO_OWNER + "/" + REPO_NAME;
    public static final String RELEASES_URL = REPO_URL + "/releases";

    public static final class ReleaseInfo {
        public String tagName;     // 如 v1.1
        public String name;        // release 标题
        public String body;        // 更新说明
        public String htmlUrl;     // release 页面
        public String apkUrl;      // APK 资产下载地址（可能为 null）
    }

    private AppUpdate() {}

    /** 获取当前应用版本名，如 "1.1" */
    public static String currentVersion(Activity ctx) {
        try {
            PackageInfo pi = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0);
            return pi.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "1.0";
        }
    }

    /**
     * 比较版本号。返回 >0 表示 latest 比 current 新，=0 相同，<0 current 更新。
     * 支持 "v1.1" / "1.1" / "1.0.2" 等格式。
     */
    public static int compareVersions(String latestTag, String current) {
        String a = normalize(latestTag);
        String b = normalize(current);
        String[] pa = a.split("\\.");
        String[] pb = b.split("\\.");
        int len = Math.max(pa.length, pb.length);
        for (int i = 0; i < len; i++) {
            int na = i < pa.length ? parseIntSafe(pa[i]) : 0;
            int nb = i < pb.length ? parseIntSafe(pb[i]) : 0;
            if (na != nb) return Integer.compare(na, nb);
        }
        return 0;
    }

    private static String normalize(String v) {
        if (v == null) return "0";
        v = v.trim();
        while (v.startsWith("v") || v.startsWith("V")) v = v.substring(1);
        return v.isEmpty() ? "0" : v;
    }

    private static int parseIntSafe(String s) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return 0; }
    }

    /** 查询 GitHub 最新 release。失败抛异常。 */
    public static ReleaseInfo fetchLatest() throws Exception {
        String repoEnc = URLEncoder.encode(REPO_NAME, "UTF-8");
        String apiUrl = "https://api.github.com/repos/" + REPO_OWNER + "/" + repoEnc + "/releases/latest";
        HttpURLConnection conn = (HttpURLConnection) new URL(apiUrl).openConnection();
        try {
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/vnd.github+json");
            conn.setRequestProperty("User-Agent", "ET-APK-Workshop");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);
            int code = conn.getResponseCode();
            InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
            String resp = readAll(is);
            if (code == 404) {
                throw new Exception("尚未发布任何版本（仓库可能还没有 Release）");
            }
            if (code < 200 || code >= 300) {
                throw new Exception("GitHub API 返回 HTTP " + code);
            }
            JSONObject j = new JSONObject(resp);
            ReleaseInfo r = new ReleaseInfo();
            r.tagName = j.optString("tag_name", "");
            r.name = j.optString("name", r.tagName);
            r.body = j.optString("body", "");
            r.htmlUrl = j.optString("html_url", RELEASES_URL);
            // 找 APK 资产
            JSONArray assets = j.optJSONArray("assets");
            if (assets != null) {
                for (int i = 0; i < assets.length(); i++) {
                    JSONObject a = assets.optJSONObject(i);
                    if (a != null) {
                        String name = a.optString("name", "");
                        if (name.endsWith(".apk")) {
                            r.apkUrl = a.optString("browser_download_url", null);
                            break;
                        }
                    }
                }
            }
            return r;
        } finally {
            conn.disconnect();
        }
    }

    /**
     * 检查更新并在有新版本时弹出对话框。
     * @param ctx Activity
     * @param silent true=启动时静默检测（无新版本不提示，失败不提示）；false=手动点击（总是提示结果）
     */
    public static void checkAndPrompt(final Activity ctx, final boolean silent) {
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    final ReleaseInfo latest = fetchLatest();
                    final String cur = currentVersion(ctx);
                    final boolean hasUpdate = compareVersions(latest.tagName, cur) > 0;
                    ctx.runOnUiThread(new Runnable() {
                        @Override public void run() {
                            if (hasUpdate) {
                                showUpdateDialog(ctx, latest, cur);
                            } else if (!silent) {
                                Ui.alert(ctx, "已是最新版本",
                                        "当前版本 v" + cur + "，GitHub 最新版本 " + latest.tagName + "。");
                            }
                        }
                    });
                } catch (final Exception e) {
                    if (!silent) {
                        ctx.runOnUiThread(new Runnable() {
                            @Override public void run() {
                                Ui.alert(ctx, "检测更新失败", e.getMessage());
                            }
                        });
                    }
                }
            }
        }).start();
    }

    private static void showUpdateDialog(final Activity ctx, final ReleaseInfo r, String current) {
        StringBuilder msg = new StringBuilder();
        msg.append("当前版本：v").append(current).append("\n");
        msg.append("最新版本：").append(r.tagName).append("\n\n");
        if (r.body != null && !r.body.trim().isEmpty()) {
            msg.append("更新说明：\n").append(r.body.trim()).append("\n\n");
        }
        msg.append("是否前往 GitHub 下载更新？");
        new AlertDialog.Builder(ctx)
                .setTitle("发现新版本 " + r.tagName)
                .setMessage(msg.toString())
                .setPositiveButton("立即更新", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        String url = (r.apkUrl != null) ? r.apkUrl : r.htmlUrl;
                        try {
                            ctx.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                        } catch (Exception e) {
                            Ui.toast(ctx, "无法打开浏览器: " + e.getMessage());
                        }
                    }
                })
                .setNegativeButton("暂不更新", null)
                .setCancelable(true)
                .show();
    }

    private static String readAll(InputStream is) throws Exception {
        if (is == null) return "";
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
        return new String(bos.toByteArray(), StandardCharsets.UTF_8);
    }
}
