package com.et.apkworkshop;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.et.apkworkshop.engine.ProjectInfo;
import com.et.apkworkshop.util.AppUpdate;
import com.et.apkworkshop.util.Storage;
import com.et.apkworkshop.util.Ui;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int REQ_PICK_APK = 1001;
    private static final int REQ_PICK_UNPACK = 1002;
    private static final int REQ_NOTIFICATION = 1003;
    private static final int REQ_STORAGE = 1004;
    private static final int REQ_MANAGE_STORAGE = 1005;

    private File projectsRoot;
    private ListView projectList;
    private ProjectAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.applyAnimeBg(this);
        projectsRoot = Storage.getProjectsDir();

        LinearLayout root = Ui.vertical(this);
        root.setBackgroundColor(Ui.BG_OVERLAY);
        root.setPadding(Ui.dp(this, 16), Ui.dp(this, 12), Ui.dp(this, 16), Ui.dp(this, 12));

        LinearLayout header = Ui.horizontal(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = Ui.label(this, "ETC APK 工坊", Ui.PRIMARY, 22, true);
        header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        ImageView gear = new ImageView(this);
        gear.setImageDrawable(Ui.roundedStroke(Ui.CARD2, Ui.BORDER, 12, this));
        int sz = Ui.dp(this, 34);
        gear.setLayoutParams(new LinearLayout.LayoutParams(sz, sz));
        gear.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { startActivity(new Intent(MainActivity.this, SettingsActivity.class)); }
        });
        header.addView(gear);
        root.addView(header);
        root.addView(Ui.label(this, "反编译 · 脱壳 · 修改 · 一键编译打包", Ui.TEXT_DIM, 13, false), lp(0, 0, 0, 10));

        TextView pickBtn = new TextView(this);
        pickBtn.setText("＋  选择 APK 反编译");
        pickBtn.setTextColor(Color.rgb(2, 20, 30));
        pickBtn.setTextSize(17);
        pickBtn.setTypeface(Typeface.DEFAULT_BOLD);
        pickBtn.setGravity(Gravity.CENTER);
        pickBtn.setPadding(0, Ui.dp(this, 16), 0, Ui.dp(this, 16));
        pickBtn.setBackground(Ui.rounded(Ui.PRIMARY, 16, this));
        pickBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { pickApk(REQ_PICK_APK, false); }
        });
        root.addView(pickBtn, lp(0, 16, 0, 4));

        TextView unpackBtn = new TextView(this);
        unpackBtn.setText("🔓  选择 APK 脱壳（提取所有 dex + 壳检测）");
        unpackBtn.setTextColor(Ui.TEXT);
        unpackBtn.setTextSize(14);
        unpackBtn.setTypeface(Typeface.DEFAULT_BOLD);
        unpackBtn.setGravity(Gravity.CENTER);
        unpackBtn.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 12));
        unpackBtn.setBackground(Ui.roundedStroke(Ui.CARD_OVERLAY, Ui.PRIMARY, 14, this));
        unpackBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { pickApk(REQ_PICK_UNPACK, true); }
        });
        root.addView(unpackBtn, lp(0, 6, 0, 4));

        root.addView(Ui.label(this, "工程保存在 /storage/emulated/0/lookapks/projects/", Ui.TEXT_DIM, 12, false), lp(2, 4, 0, 16));

        root.addView(Ui.label(this, "最近工程", Ui.TEXT, 15, true), lp(2, 0, 0, 8));
        projectList = new ListView(this);
        projectList.setDivider(null);
        projectList.setCacheColorHint(Color.TRANSPARENT);
        projectList.setBackgroundColor(Color.TRANSPARENT);
        projectList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> parent, View view, int position, long id) { openProject(adapter.getItem(position)); }
        });
        projectList.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override public boolean onItemLongClick(AdapterView<?> parent, View view, final int position, long id) {
                final File dir = adapter.getItem(position);
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle(dir.getName())
                        .setItems(new String[]{"打开工程", "删除工程"}, new DialogInterface.OnClickListener() {
                            @Override public void onClick(DialogInterface d, int w) {
                                if (w == 0) openProject(dir);
                                else Ui.confirm(MainActivity.this, "删除工程", "确定删除 " + dir.getName() + "？", "删除", new Runnable() {
                                    @Override public void run() { com.et.apkworkshop.engine.ApkEngine.deleteRecursive(dir); refreshProjects(); }
                                });
                            }
                        }).show();
                return true;
            }
        });
        root.addView(projectList, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        root.addView(Ui.label(this, "ETC 出品 · 版权所有 · 禁止倒卖 · 仅供学习与自有应用改装", Ui.TEXT_DIM, 11, false), lp(2, 10, 0, 0));
        setContentView(root);

        requestPermissionsIfNeeded();
        refreshProjects();
        AppUpdate.checkAndPrompt(this, true);
    }

    private void requestPermissionsIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATION);
        }
        if (!Storage.hasPermission(this)) {
            if (Build.VERSION.SDK_INT >= 30) {
                new AlertDialog.Builder(this)
                        .setTitle("需要存储权限")
                        .setMessage("需要访问 /storage/emulated/0/lookapks/ 保存工程。\n请在设置中允许\"所有文件访问权限\"。")
                        .setPositiveButton("去设置", new DialogInterface.OnClickListener() {
                            @Override public void onClick(DialogInterface d, int w) {
                                try {
                                    Intent i = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                                    i.setData(Uri.parse("package:" + getPackageName()));
                                    startActivityForResult(i, REQ_MANAGE_STORAGE);
                                } catch (Exception e) {
                                    startActivityForResult(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION), REQ_MANAGE_STORAGE);
                                }
                            }
                        }).setNegativeButton("取消", null).show();
            } else if (Build.VERSION.SDK_INT >= 23) {
                requestPermissions(new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_STORAGE);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_STORAGE && grantResults.length > 0
                && grantResults[0] != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            Ui.toast(this, "未授予存储权限");
        }
    }

    private LinearLayout.LayoutParams lp(int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(Ui.dp(this, l), Ui.dp(this, t), Ui.dp(this, r), Ui.dp(this, b));
        return p;
    }

    private void pickApk(int reqCode, boolean unpack) {
        // ACTION_OPEN_DOCUMENT 不需要存储权限（系统授予 URI 访问），直接打开选择器
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/vnd.android.package-archive", "application/octet-stream"});
        try { startActivityForResult(intent, reqCode); } catch (Exception e) { Ui.toast(this, "无法打开文件选择器"); }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if ((requestCode == REQ_PICK_APK || requestCode == REQ_PICK_UNPACK) && resultCode == RESULT_OK && data != null && data.getData() != null) {
            startWork(data.getData(), requestCode == REQ_PICK_UNPACK);
        } else if (requestCode == REQ_MANAGE_STORAGE) {
            if (Storage.hasPermission(this)) { Ui.toast(this, "存储权限已授予"); refreshProjects(); }
            else Ui.toast(this, "未授予所有文件访问权限");
        }
    }

    private void startWork(Uri uri, boolean unpack) {
        try {
            // 检查存储权限，未授予则提示但不阻塞（缓存复制不需要权限）
            if (!Storage.hasPermission(this)) {
                Ui.toast(this, "提示：未授予存储权限，工程可能无法保存，请在设置中允许");
                requestPermissionsIfNeeded();
            }
            String suffix = unpack ? "unpack.apk" : "picked.apk";
            File cache = new File(getCacheDir(), suffix);
            try (InputStream in = getContentResolver().openInputStream(uri); OutputStream out = new FileOutputStream(cache)) {
                byte[] buf = new byte[65536]; int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            }
            String prefix = unpack ? "unpack_" : "proj_";
            File projectDir = new File(projectsRoot, prefix + System.currentTimeMillis() % 100000);
            Intent i = new Intent(this, DecompileActivity.class);
            i.putExtra("apk_path", cache.getAbsolutePath());
            i.putExtra("project_dir", projectDir.getAbsolutePath());
            if (unpack) i.putExtra("mode", "unpack");
            startActivity(i);
        } catch (Exception e) { Ui.toast(this, "读取 APK 失败: " + e.getMessage()); }
    }

    private void openProject(File dir) {
        Intent i = new Intent(this, ProjectActivity.class);
        i.putExtra("project_dir", dir.getAbsolutePath());
        startActivity(i);
    }

    private void refreshProjects() {
        File[] dirs = projectsRoot.listFiles();
        List<File> list = new ArrayList<File>();
        if (dirs != null) for (File d : dirs) if (d.isDirectory() && new File(d, "info.json").exists()) list.add(d);
        Collections.sort(list, new Comparator<File>() {
            @Override public int compare(File a, File b) { return Long.compare(lastModified(b), lastModified(a)); }
        });
        if (adapter == null) { adapter = new ProjectAdapter(this, list); projectList.setAdapter(adapter); }
        else adapter.update(list);
    }

    private static long lastModified(File dir) {
        File f = new File(dir, "info.json");
        return f.exists() ? f.lastModified() : dir.lastModified();
    }

    @Override protected void onResume() { super.onResume(); refreshProjects(); }

    static class ProjectAdapter extends BaseAdapter {
        private final Context ctx; private final List<File> list;
        ProjectAdapter(Context ctx, List<File> list) { this.ctx = ctx; this.list = list; }
        void update(List<File> l) { list.clear(); list.addAll(l); notifyDataSetChanged(); }
        @Override public int getCount() { return list.size(); }
        @Override public File getItem(int p) { return list.get(p); }
        @Override public long getItemId(int p) { return p; }
        @Override public View getView(int p, View convertView, ViewGroup parent) {
            LinearLayout item = convertView instanceof LinearLayout ? (LinearLayout) convertView : Ui.vertical(ctx);
            if (convertView == null) {
                item.setPadding(Ui.dp(ctx, 12), Ui.dp(ctx, 12), Ui.dp(ctx, 12), Ui.dp(ctx, 12));
                item.setBackground(Ui.roundedStroke(Ui.CARD_OVERLAY, Ui.BORDER, 12, ctx));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.setMargins(0, 0, 0, Ui.dp(ctx, 8));
                item.setLayoutParams(lp);
            }
            File dir = getItem(p);
            ProjectInfo info = ProjectInfo.fromJson(dir);
            item.removeAllViews();
            item.addView(Ui.label(ctx, dir.getName(), Ui.TEXT, 15, true));
            StringBuilder sb = new StringBuilder();
            sb.append(info.dexNames.size()).append(" 个 dex");
            if (!TextUtils.isEmpty(info.lastOutput)) { File out = new File(info.lastOutput); if (out.exists()) sb.append(" · 已打包: ").append(formatSize(out.length())); }
            String date = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(new Date(lastModified(dir)));
            item.addView(Ui.label(ctx, sb.toString() + " · " + date, Ui.TEXT_DIM, 12, false));
            return item;
        }
    }

    static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(Locale.getDefault(), "%.1f KB", bytes / 1024.0);
        return String.format(Locale.getDefault(), "%.2f MB", bytes / (1024.0 * 1024.0));
    }
}
