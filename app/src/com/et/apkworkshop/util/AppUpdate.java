package com.et.apkworkshop.util;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * 检测更新：通过 GitHub Releases Atom Feed（无 API 限流）查询最新版本。
 * 仓库公开，无需认证。
 */
public final class AppUpdate {
    public static final String REPO_OWNER = "ETQWFD";
    public static final String REPO_NAME = "ET-APK-Workshop";
    public static final String REPO_URL = "https://github.com/" + REPO_OWNER + "/" + REPO_NAME;
    public static final String RELEASES_URL = REPO_URL + "/releases";
    public static final String ATOM_URL = RELEASES_URL + ".atom";

    public static final class ReleaseInfo {
        public String tagName;     // 如 v1.2
        public String title;       // release 标题
        public String body;        // 更新说明（纯文本）
        public String htmlUrl;     // release 页面
    }

    private AppUpdate() {}

    public static String currentVersion(Activity ctx) {
        try {
            PackageInfo pi = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0);
            return pi.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "1.0";
        }
    }

    /** 比较版本号：>0 表示 latest 更新，=0 相同，<0 current 更新 */
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

    /** 从 Atom Feed 解析最新 release */
    public static ReleaseInfo fetchLatest() throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(ATOM_URL).openConnection();
        try {
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "ET-APK-Workshop");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(20000);
            int code = conn.getResponseCode();
            if (code != 200) throw new Exception("GitHub 返回 HTTP " + code + "（可能网络不通）");
            String xml = readAll(conn.getInputStream());
            return parseAtom(xml);
        } finally {
            conn.disconnect();
        }
    }

    private static ReleaseInfo parseAtom(String xml) throws Exception {
        if (xml == null || xml.isEmpty()) throw new Exception("GitHub 返回空内容");
        ReleaseInfo r = new ReleaseInfo();
        // 取第一个 <entry>
        int entryStart = xml.indexOf("<entry>");
        if (entryStart < 0) throw new Exception("尚未发布任何版本");
        int entryEnd = xml.indexOf("</entry>", entryStart);
        String entry = entryEnd > 0 ? xml.substring(entryStart, entryEnd) : xml.substring(entryStart);

        // title: <title>v1.2 - xxx</title>
        r.title = extractTag(entry, "<title>", "</title>");
        // tag 从 title 提取（第一个空格前的部分）
        int sp = r.title.indexOf(' ');
        r.tagName = sp > 0 ? r.title.substring(0, sp) : r.title;
        if (r.tagName.isEmpty()) r.tagName = r.title;

        // link: <link rel="alternate" type="text/html" href="..."/>
        int linkIdx = entry.indexOf("href=\"");
        if (linkIdx >= 0) {
            int linkEnd = entry.indexOf("\"", linkIdx + 6);
            if (linkEnd > 0) r.htmlUrl = entry.substring(linkIdx + 6, linkEnd);
        }
        if (r.htmlUrl == null) r.htmlUrl = RELEASES_URL;

        // content: <content type="html">...</content>
        String content = extractTag(entry, "<content type=\"html\">", "</content>");
        r.body = htmlToText(content);
        return r;
    }

    private static String extractTag(String xml, String open, String close) {
        int s = xml.indexOf(open);
        if (s < 0) return "";
        s += open.length();
        int e = xml.indexOf(close, s);
        if (e < 0) return "";
        return xml.substring(s, e).trim();
    }

    private static String htmlToText(String html) {
        if (html == null) return "";
        String t = html;
        t = t.replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")
                .replace("&quot;", "\"").replace("&#39;", "'").replace("&nbsp;", " ");
        t = t.replaceAll("<[^>]+>", "\n");
        t = t.replaceAll("\n{3,}", "\n\n");
        return t.trim();
    }

    /** 检查更新并弹窗。silent=true 时无新版本/失败不提示 */
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
                                        "当前版本 v" + cur + "\nGitHub 最新版本 " + latest.tagName);
                            }
                        }
                    });
                } catch (final Exception e) {
                    if (!silent) {
                        ctx.runOnUiThread(new Runnable() {
                            @Override public void run() {
                                Ui.alert(ctx, "检测更新失败",
                                        e.getMessage() + "\n\n可手动前往 GitHub 查看：\n" + RELEASES_URL);
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
        if (r.body != null && !r.body.isEmpty()) {
            msg.append("更新说明：\n").append(r.body.length() > 500 ? r.body.substring(0, 500) + "…" : r.body).append("\n\n");
        }
        msg.append("是否前往 GitHub 下载更新？");
        new AlertDialog.Builder(ctx)
                .setTitle("发现新版本 " + r.tagName)
                .setMessage(msg.toString())
                .setPositiveButton("立即更新", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        try { ctx.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(r.htmlUrl))); }
                        catch (Exception e) { Ui.toast(ctx, "无法打开浏览器"); }
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
