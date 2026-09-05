package com.et.apkworkshop;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
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
import com.et.apkworkshop.util.Ui;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 主界面：选择 APK 反编译 / 最近工程列表 / 设置入口。
 */
public class MainActivity extends Activity {

    private static final int REQ_PICK_APK = 1001;
    private File projectsRoot;
    private ListView projectList;
    private ProjectAdapter adapter;
    private File pickedApkCache;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        projectsRoot = new File(getFilesDir(), "projects");
        if (!projectsRoot.exists()) projectsRoot.mkdirs();

        LinearLayout root = Ui.vertical(this);
        root.setBackgroundColor(Ui.BG);
        root.setPadding(Ui.dp(this, 16), Ui.dp(this, 12), Ui.dp(this, 16), Ui.dp(this, 12));

        // 顶部标题栏
        LinearLayout header = Ui.horizontal(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = Ui.label(this, "ET APK 工坊", Ui.PRIMARY, 22, true);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        header.addView(title, titleLp);
        ImageView gear = new ImageView(this);
        gear.setImageDrawable(gearIcon());
        int sz = Ui.dp(this, 34);
        gear.setLayoutParams(new LinearLayout.LayoutParams(sz, sz));
        gear.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, SettingsActivity.class));
            }
        });
        header.addView(gear);
        root.addView(header);

        TextView subtitle = Ui.label(this, "反编译 · 修改 · 一键编译打包", Ui.TEXT_DIM, 13, false);
        root.addView(subtitle, lp(0, 0, 0, 10));

        // 选择 APK 大按钮
        TextView pickBtn = new TextView(this);
        pickBtn.setText("＋  选择 APK 反编译");
        pickBtn.setTextColor(Color.rgb(2, 20, 30));
        pickBtn.setTextSize(17);
        pickBtn.setTypeface(Typeface.DEFAULT_BOLD);
        pickBtn.setGravity(Gravity.CENTER);
        pickBtn.setPadding(0, Ui.dp(this, 16), 0, Ui.dp(this, 16));
        pickBtn.setBackground(Ui.rounded(Ui.PRIMARY, 16, this));
        pickBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { pickApk(); }
        });
        root.addView(pickBtn, lp(0, 16, 0, 4));

        TextView hint = Ui.label(this, "支持任意 APK：反编译出 smali 源码，可修改后用内置引擎重新编译打包", Ui.TEXT_DIM, 12, false);
        root.addView(hint, lp(2, 4, 0, 16));

        // 最近工程
        TextView section = Ui.label(this, "最近工程", Ui.TEXT, 15, true);
        root.addView(section, lp(2, 0, 0, 8));

        projectList = new ListView(this);
        projectList.setDivider(null);
        projectList.setCacheColorHint(Color.TRANSPARENT);
        projectList.setBackgroundColor(Ui.BG);
        projectList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                File dir = adapter.getItem(position);
                openProject(dir);
            }
        });
        projectList.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override public boolean onItemLongClick(AdapterView<?> parent, View view, final int position, long id) {
                final File dir = adapter.getItem(position);
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle(dir.getName())
                        .setItems(new String[]{"打开工程", "删除工程"}, new DialogInterface.OnClickListener() {
                            @Override public void onClick(DialogInterface d, int w) {
                                if (w == 0) openProject(dir);
                                else {
                                    Ui.confirm(MainActivity.this, "删除工程", "确定删除工程 " + dir.getName() + "？\n该操作不可恢复。",
                                            "删除", new Runnable() {
                                                @Override public void run() {
                                                    com.et.apkworkshop.engine.ApkEngine.deleteRecursive(dir);
                                                    refreshProjects();
                                                }
                                            });
                                }
                            }
                        }).show();
                return true;
            }
        });
        root.addView(projectList, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        TextView footer = Ui.label(this, "ET 出品 · 仅供学习与自有应用改装使用，请勿用于破解盗版或绕过授权", Ui.TEXT_DIM, 11, false);
        root.addView(footer, lp(2, 10, 0, 0));

        setContentView(root);
        refreshProjects();
        // 启动时静默检测更新（有新版本才提示）
        com.et.apkworkshop.util.AppUpdate.checkAndPrompt(this, true);
    }

    private LinearLayout.LayoutParams lp(int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(Ui.dp(this, l), Ui.dp(this, t), Ui.dp(this, r), Ui.dp(this, b));
        return p;
    }

    private void pickApk() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        String[] mimeTypes = {"application/vnd.android.package-archive", "application/octet-stream"};
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        try {
            startActivityForResult(intent, REQ_PICK_APK);
        } catch (Exception e) {
            Ui.toast(this, "无法打开文件选择器");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_PICK_APK && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            startDecompile(uri);
        }
    }

    private void startDecompile(Uri uri) {
        try {
            File cache = new File(getCacheDir(), "picked.apk");
            try (InputStream in = getContentResolver().openInputStream(uri);
                 OutputStream out = new FileOutputStream(cache)) {                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            }
            pickedApkCache = cache;
            Intent i = new Intent(this, DecompileActivity.class);
            i.putExtra("apk_path", cache.getAbsolutePath());
            startActivity(i);
        } catch (Exception e) {
            Ui.toast(this, "读取 APK 失败: " + e.getMessage());
        }
    }

    private void openProject(File dir) {
        Intent i = new Intent(this, ProjectActivity.class);
        i.putExtra("project_dir", dir.getAbsolutePath());
        startActivity(i);
    }

    private void refreshProjects() {
        File[] dirs = projectsRoot.listFiles();
        List<File> list = new ArrayList<File>();
        if (dirs != null) {
            for (File d : dirs) {
                if (d.isDirectory() && new File(d, "info.json").exists()) list.add(d);
            }
        }
        Collections.sort(list, new Comparator<File>() {
            @Override public int compare(File a, File b) {
                return Long.compare(lastModified(b), lastModified(a));
            }
        });
        if (adapter == null) {
            adapter = new ProjectAdapter(this, list);
            projectList.setAdapter(adapter);
        } else {
            adapter.update(list);
        }
    }

    private static long lastModified(File dir) {
        File f = new File(dir, "info.json");
        return f.exists() ? f.lastModified() : dir.lastModified();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshProjects();
    }

    private android.graphics.drawable.Drawable gearIcon() {
        android.graphics.drawable.GradientDrawable g = Ui.rounded(Ui.CARD2, 12, this);
        g.setStroke(Ui.dp(this, 1), Ui.BORDER);
        return g;
    }

    static class ProjectAdapter extends BaseAdapter {
        private final Context ctx;
        private final List<File> list;

        ProjectAdapter(Context ctx, List<File> list) { this.ctx = ctx; this.list = list; }
        void update(List<File> l) { list.clear(); list.addAll(l); notifyDataSetChanged(); }

        @Override public int getCount() { return list.size(); }
        @Override public File getItem(int p) { return list.get(p); }
        @Override public long getItemId(int p) { return p; }

        @Override public View getView(int p, View convertView, ViewGroup parent) {
            LinearLayout item = convertView instanceof LinearLayout ? (LinearLayout) convertView : Ui.vertical(ctx);
            if (convertView == null) {
                item.setPadding(Ui.dp(ctx, 12), Ui.dp(ctx, 12), Ui.dp(ctx, 12), Ui.dp(ctx, 12));
                item.setBackground(Ui.roundedStroke(Ui.CARD, Ui.BORDER, 12, ctx));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.setMargins(0, 0, 0, Ui.dp(ctx, 8));
                item.setLayoutParams(lp);
            }
            File dir = getItem(p);
            ProjectInfo info = ProjectInfo.fromJson(dir);
            item.removeAllViews();
            TextView name = Ui.label(ctx, dir.getName(), Ui.TEXT, 15, true);
            item.addView(name);
            StringBuilder sb = new StringBuilder();
            sb.append(info.dexNames.size()).append(" 个 dex");
            if (!TextUtils.isEmpty(info.lastOutput)) {
                File out = new File(info.lastOutput);
                if (out.exists()) sb.append(" · 已打包: ").append(formatSize(out.length()));
            }
            String date = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(new Date(lastModified(dir)));
            TextView meta = Ui.label(ctx, sb.toString() + " · " + date, Ui.TEXT_DIM, 12, false);
            item.addView(meta);
            return item;
        }
    }

    static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(Locale.getDefault(), "%.1f KB", bytes / 1024.0);
        return String.format(Locale.getDefault(), "%.2f MB", bytes / (1024.0 * 1024.0));
    }
}
