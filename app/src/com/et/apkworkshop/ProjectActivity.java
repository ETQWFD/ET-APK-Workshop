package com.et.apkworkshop;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.ClipData;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.et.apkworkshop.engine.ApkEngine;
import com.et.apkworkshop.engine.ProjectInfo;
import com.et.apkworkshop.util.ApkProvider;
import com.et.apkworkshop.util.Ui;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 工程页：浏览 smali 源码树、重新反编译、AI 助手、一键编译打包与安装/分享。
 */
public class ProjectActivity extends Activity {

    private static final int REQ_INSTALL = 2001;
    private static final int REQ_WRITE_STORAGE = 2002;

    private File projectDir;
    private ProjectInfo info;
    private File currentDir;
    private ListView fileList;
    private FileAdapter adapter;
    private TextView breadcrumb;
    private TextView rootLabel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.applyAnimeBg(this);
        projectDir = new File(getIntent().getStringExtra("project_dir"));
        info = ProjectInfo.fromJson(projectDir);
        currentDir = projectDir;

        LinearLayout root = Ui.vertical(this);
        root.setBackgroundColor(Ui.BG_OVERLAY);
        root.setPadding(Ui.dp(this, 14), Ui.dp(this, 10), Ui.dp(this, 14), Ui.dp(this, 10));

        // 顶部：返回 + 名称 + 设置
        LinearLayout header = Ui.horizontal(this);
        TextView back = Ui.label(this, "‹", Ui.PRIMARY, 26, true);
        back.setPadding(Ui.dp(this, 6), 0, Ui.dp(this, 12), 0);
        back.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        header.addView(back);
        TextView t = Ui.label(this, info.name, Ui.TEXT, 18, true);
        header.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView gear = Ui.label(this, "设置", Ui.PRIMARY, 13, true);
        gear.setPadding(Ui.dp(this, 8), Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 4));
        gear.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { startActivity(new Intent(ProjectActivity.this, SettingsActivity.class)); }
        });
        header.addView(gear);
        root.addView(header);

        // 操作按钮行
        LinearLayout ops = Ui.horizontal(this);
        TextView redo = actionChip("重新反编译");
        redo.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { redecompile(); }
        });
        TextView ai = actionChip("AI 助手");
        ai.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Intent i = new Intent(ProjectActivity.this, AiChatActivity.class);
                i.putExtra("project_dir", projectDir.getAbsolutePath());
                startActivity(i);
            }
        });
        TextView refresh = actionChip("刷新");
        refresh.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { reload(); }
        });
        ops.addView(redo, chipLp(1));
        ops.addView(ai, chipLp(1));
        ops.addView(refresh, chipLp(1));
        root.addView(ops, lp(0, 6, 0, 4));

        // 面包屑
        breadcrumb = Ui.label(this, "", Ui.TEXT_DIM, 12, false);
        root.addView(breadcrumb, lp(2, 2, 0, 4));

        // 文件列表
        fileList = new ListView(this);
        fileList.setDivider(null);
        fileList.setCacheColorHint(android.graphics.Color.TRANSPARENT);
        fileList.setBackgroundColor(Ui.BG);
        fileList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                File f = adapter.getItem(position);
                if (f.isDirectory()) {
                    currentDir = f;
                    reload();
                } else {
                    openFile(f);
                }
            }
        });
        fileList.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                final File f = adapter.getItem(position);
                longPressFile(f);
                return true;
            }
        });
        root.addView(fileList, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        // 底部：编译打包
        TextView compileBtn = Ui.primaryButton(this, "⚙  编译打包 APK");
        compileBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { doCompile(); }
        });
        root.addView(compileBtn, lp(0, 8, 0, 0));

        TextView outHint = Ui.label(this, "产物输出到: " + new File(projectDir, "output").getAbsolutePath(), Ui.TEXT_DIM, 11, false);
        root.addView(outHint, lp(2, 4, 0, 0));

        setContentView(root);
        reload();
    }

    private TextView actionChip(String s) {
        TextView tv = new TextView(this);
        tv.setText(s);
        tv.setTextColor(Ui.TEXT);
        tv.setTextSize(13);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(Ui.dp(this, 6), Ui.dp(this, 8), Ui.dp(this, 6), Ui.dp(this, 8));
        tv.setBackground(Ui.roundedStroke(Ui.CARD2, Ui.BORDER, 10, this));
        return tv;
    }

    private LinearLayout.LayoutParams chipLp(float weight) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight);
        int m = Ui.dp(this, 3);
        lp.setMargins(m, 0, m, 0);
        return lp;
    }

    private LinearLayout.LayoutParams lp(int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(Ui.dp(this, l), Ui.dp(this, t), Ui.dp(this, r), Ui.dp(this, b));
        return p;
    }

    private void reload() {
        if (adapter == null) {
            adapter = new FileAdapter(this, listFiles(currentDir));
            fileList.setAdapter(adapter);
        } else {
            adapter.update(listFiles(currentDir));
        }
        String rel = relativePath(currentDir);
        breadcrumb.setText(rel.isEmpty() ? "工程根目录" : "工程根目录 / " + rel);
        if (currentDir.equals(projectDir)) {
            breadcrumb.setText("工程根目录（smali 源码在 smali/ 下，原始资源在 apk_src/ 下）");
        }
    }

    private String relativePath(File dir) {
        String base = projectDir.getAbsolutePath();
        String path = dir.getAbsolutePath();
        if (path.startsWith(base)) return path.substring(base.length()).replace(File.separatorChar, '/').replaceFirst("^/", "");
        return dir.getName();
    }

    private List<File> listFiles(File dir) {
        File[] files = dir.listFiles();
        List<File> list = new ArrayList<File>();
        if (files != null) list.addAll(Arrays.asList(files));
        Collections.sort(list, new Comparator<File>() {
            @Override public int compare(File a, File b) {
                if (a.isDirectory() && !b.isDirectory()) return -1;
                if (!a.isDirectory() && b.isDirectory()) return 1;
                return a.getName().compareToIgnoreCase(b.getName());
            }
        });
        return list;
    }

    private boolean isSmaliProjectFile(File f) {
        File smaliDir = new File(projectDir, "smali");
        File smali2 = new File(projectDir, "smali_classes2");
        String p = f.getAbsolutePath();
        return (p.startsWith(smaliDir.getAbsolutePath()) || p.startsWith(smali2.getAbsolutePath()))
                && f.getName().endsWith(".smali");
    }

    private boolean isEditableText(File f) {
        String n = f.getName().toLowerCase(java.util.Locale.US);
        return n.endsWith(".smali") || n.endsWith(".txt") || n.endsWith(".json")
                || n.endsWith(".yml") || n.endsWith(".yaml") || n.endsWith(".md")
                || n.endsWith(".xml") || n.endsWith(".properties");
    }

    private void openFile(File f) {
        if (f.isDirectory()) return;
        if (isEditableText(f)) {
            Intent i = new Intent(this, EditorActivity.class);
            i.putExtra("file_path", f.getAbsolutePath());
            i.putExtra("project_dir", projectDir.getAbsolutePath());
            i.putExtra("editable", isSmaliProjectFile(f) || f.getName().endsWith(".txt") || f.getName().endsWith(".json") || f.getName().endsWith(".yml"));
            startActivity(i);
        } else {
            Ui.toast(this, "该文件为二进制/资源文件，重打包时会原样保留。可修改 smali/ 下的 .smali 源码。");
        }
    }

    private void longPressFile(final File f) {
        final boolean dir = f.isDirectory();
        String[] items = dir
                ? new String[]{"重命名", "删除"}
                : new String[]{"编辑", "用 AI 分析/修改", "重命名", "删除"};
        new android.app.AlertDialog.Builder(this)
                .setTitle(f.getName())
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        String action = items[w];
                        if (action.equals("编辑")) openFile(f);
                        else if (action.equals("用 AI 分析/修改")) {
                            Intent i = new Intent(ProjectActivity.this, AiChatActivity.class);
                            i.putExtra("project_dir", projectDir.getAbsolutePath());
                            i.putExtra("file_path", f.getAbsolutePath());
                            startActivity(i);
                        } else if (action.equals("重命名")) renameFile(f);
                        else if (action.equals("删除")) {
                            Ui.confirm(ProjectActivity.this, "删除", "确定删除 " + f.getName() + "？", "删除", new Runnable() {
                                @Override public void run() {
                                    if (f.isDirectory()) ApkEngine.deleteRecursive(f);
                                    else f.delete();
                                    reload();
                                }
                            });
                        }
                    }
                }).show();
    }

    private void renameFile(final File f) {
        final android.widget.EditText et = Ui.input(this, "新名称");
        et.setText(f.getName());
        new android.app.AlertDialog.Builder(this)
                .setTitle("重命名")
                .setView(et)
                .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        String newName = et.getText().toString().trim();
                        if (newName.isEmpty()) return;
                        File nf = new File(f.getParentFile(), newName);
                        if (nf.exists()) { Ui.toast(ProjectActivity.this, "同名文件已存在"); return; }
                        if (f.renameTo(nf)) reload();
                        else Ui.toast(ProjectActivity.this, "重命名失败");
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void redecompile() {
        Ui.confirm(this, "重新反编译", "将删除当前工程并重新反编译原始 APK，确定继续？", "重新反编译", new Runnable() {
            @Override public void run() {
                final ProgressDialog pd = new ProgressDialog(ProjectActivity.this);
                pd.setMessage("正在重新反编译 ...");
                pd.setCancelable(false);
                pd.show();
                new Thread(new Runnable() {
                    @Override public void run() {
                        try {
                            ApkEngine.Progress prog = new ApkEngine.Progress() {
                                @Override public void on(String m) { }
                            };
                            ApkEngine.decompile(info.originalApk(), projectDir, prog);
                            info = ProjectInfo.fromJson(projectDir);
                            currentDir = projectDir;
                            runOnUiThread(new Runnable() {
                                @Override public void run() {
                                    pd.dismiss();
                                    reload();
                                    Ui.toast(ProjectActivity.this, "重新反编译完成");
                                }
                            });
                        } catch (final Exception e) {
                            runOnUiThread(new Runnable() {
                                @Override public void run() {
                                    pd.dismiss();
                                    Ui.alert(ProjectActivity.this, "失败", e.getMessage());
                                }
                            });
                        }
                    }
                }).start();
            }
        });
    }

    private Handler compileHandler = new Handler();

    private void doCompile() {
        // 启动前台编译服务
        info.apiLevel = new com.et.apkworkshop.util.AppSettings(this).getApiLevel();
        try { info.save(); } catch (Exception ignored) {}

        Intent svc = new Intent(this, WorkService.class);
        svc.setAction(WorkService.ACTION_COMPILE);
        svc.putExtra(WorkService.EXTRA_PROJECT_DIR, projectDir.getAbsolutePath());
        svc.putExtra(WorkService.EXTRA_API_LEVEL, info.apiLevel);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(svc); else startService(svc);

        final ProgressDialog pd = new ProgressDialog(this);
        pd.setTitle("编译打包（前台服务）");
        pd.setMessage("开始编译…通知栏可见进度");
        pd.setCancelable(false);
        pd.setButton(DialogInterface.BUTTON_NEGATIVE, "后台运行", new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface d, int w) { pd.dismiss(); }
        });
        pd.show();

        compileHandler.postDelayed(new Runnable() {
            @Override public void run() {
                WorkState ws = WorkState.get();
                if (ws.isRunning()) {
                    pd.setMessage(ws.message + " (" + ws.progress + "%)");
                    compileHandler.postDelayed(this, 500);
                } else if (ws.isDone()) {
                    pd.dismiss();
                    info = ProjectInfo.fromJson(projectDir);
                    if (ws.resultPath != null) showBuildResult(new File(ws.resultPath));
                    else Ui.toast(ProjectActivity.this, "编译完成");
                    reload();
                } else if (ws.isError()) {
                    pd.dismiss();
                    Ui.alert(ProjectActivity.this, "编译失败",
                            (ws.error != null ? ws.error : "未知错误")
                            + "\n\n提示：可复制报错到 AI 助手，让 AI 帮忙修复 smali。");
                } else {
                    compileHandler.postDelayed(this, 500);
                }
            }
        }, 500);
    }

    private void showBuildResult(File out) {
        new android.app.AlertDialog.Builder(this)
                .setTitle("编译打包成功")
                .setMessage("已生成签名 APK：\n" + out.getAbsolutePath() + "\n\n" + formatSize(out.length()))
                .setPositiveButton("安装", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) { installApk(out); }
                })
                .setNeutralButton("分享", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) { shareApk(out); }
                })
                .setNegativeButton("保存到下载", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) { saveToDownloads(out); }
                })
                .show();
    }

    private Uri providerUri(File f) {
        String rel;
        try {
            String root = com.et.apkworkshop.util.Storage.getRoot().getCanonicalPath();
            String p = f.getCanonicalPath();
            if (p.startsWith(root + File.separator)) rel = p.substring(root.length() + 1);
            else rel = "share_" + System.currentTimeMillis() + ".apk";
        } catch (Exception e) {
            rel = "share_" + System.currentTimeMillis() + ".apk";
        }
        return Uri.parse("content://" + ApkProvider.AUTHORITY + "/" + rel);
    }

    private void installApk(File apk) {
        try {
            if (Build.VERSION.SDK_INT >= 26 && !getPackageManager().canRequestPackageInstalls()) {
                Intent settings = new Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + getPackageName()));
                startActivity(settings);
                Ui.toast(this, "请先允许安装未知来源应用，然后重新点安装");
                return;
            }
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(providerUri(apk), "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.setClipData(ClipData.newRawUri("apk", providerUri(apk)));
            startActivityForResult(intent, REQ_INSTALL);
        } catch (Exception e) {
            Ui.toast(this, "无法启动安装器: " + e.getMessage());
        }
    }

    private void shareApk(File apk) {
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("application/vnd.android.package-archive");
        send.putExtra(Intent.EXTRA_STREAM, providerUri(apk));
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        send.setClipData(ClipData.newRawUri("apk", providerUri(apk)));
        try {
            startActivity(Intent.createChooser(send, "分享 APK"));
        } catch (Exception e) {
            Ui.toast(this, "分享失败: " + e.getMessage());
        }
    }

    private void saveToDownloads(final File apk) {
        // Android 9 及以下写公共下载目录需要 WRITE_EXTERNAL_STORAGE 运行时权限
        if (Build.VERSION.SDK_INT < 29) {
            if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_WRITE_STORAGE);
                return;
            }
        }
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                android.content.ContentValues values = new android.content.ContentValues();
                values.put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, apk.getName());
                values.put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/vnd.android.package-archive");
                values.put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                Uri uri = getContentResolver().insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (uri != null) {
                    try (OutputStream os = getContentResolver().openOutputStream(uri);
                         FileInputStream in = new FileInputStream(apk)) {
                        byte[] buf = new byte[65536];
                        int n;
                        while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
                    }
                    Ui.toast(this, "已保存到 下载/" + apk.getName());
                    return;
                }
                throw new Exception("MediaStore 写入失败");
            } else {
                File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                if (!dir.exists()) dir.mkdirs();
                File dst = new File(dir, apk.getName());
                try (FileOutputStream out = new FileOutputStream(dst);
                     FileInputStream in = new FileInputStream(apk)) {
                    byte[] buf = new byte[65536];
                    int n;
                    while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                }
                Ui.toast(this, "已保存到 " + dst.getAbsolutePath());
            }
        } catch (Exception e) {
            Ui.toast(this, "保存失败: " + e.getMessage());
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_WRITE_STORAGE && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            File lastOut = new File(info.lastOutput);
            if (lastOut.exists()) saveToDownloads(lastOut);
            else Ui.toast(this, "请先编译打包后再保存");
        } else if (requestCode == REQ_WRITE_STORAGE) {
            Ui.toast(this, "未授予存储权限，无法保存到下载");
        }
    }

    static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(java.util.Locale.getDefault(), "%.1f KB", bytes / 1024.0);
        return String.format(java.util.Locale.getDefault(), "%.2f MB", bytes / (1024.0 * 1024.0));
    }

    static class FileAdapter extends BaseAdapter {
        private final Context ctx;
        private final List<File> list;
        FileAdapter(Context ctx, List<File> list) { this.ctx = ctx; this.list = list; }
        void update(List<File> l) { list.clear(); list.addAll(l); notifyDataSetChanged(); }
        @Override public int getCount() { return list.size(); }
        @Override public File getItem(int p) { return list.get(p); }
        @Override public long getItemId(int p) { return p; }
        @Override public View getView(int p, View convertView, ViewGroup parent) {
            LinearLayout item = convertView instanceof LinearLayout ? (LinearLayout) convertView : Ui.horizontal(ctx);
            if (convertView == null) {
                item.setPadding(Ui.dp(ctx, 10), Ui.dp(ctx, 9), Ui.dp(ctx, 10), Ui.dp(ctx, 9));
                item.setBackground(Ui.rounded(Ui.CARD, 10, ctx));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.setMargins(0, 0, 0, Ui.dp(ctx, 5));
                item.setLayoutParams(lp);
            }
            item.removeAllViews();
            File f = getItem(p);
            TextView icon = Ui.label(ctx, f.isDirectory() ? "📁" : (f.getName().endsWith(".smali") ? "📜" : "📄"), Ui.PRIMARY, 15, false);
            item.addView(icon);
            TextView name = Ui.label(ctx, f.getName(), Ui.TEXT, 14, f.isDirectory());
            item.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            if (f.isDirectory()) {
                File[] ch = f.listFiles();
                TextView cnt = Ui.label(ctx, (ch == null ? 0 : ch.length) + " 项", Ui.TEXT_DIM, 11, false);
                item.addView(cnt);
            } else {
                TextView sz = Ui.label(ctx, ProjectActivity.formatSize(f.length()), Ui.TEXT_DIM, 11, false);
                item.addView(sz);
            }
            return item;
        }
    }
}
