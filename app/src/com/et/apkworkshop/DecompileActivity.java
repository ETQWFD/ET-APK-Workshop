package com.et.apkworkshop;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.et.apkworkshop.engine.ApkEngine;
import com.et.apkworkshop.engine.ProjectInfo;
import com.et.apkworkshop.util.AppSettings;
import com.et.apkworkshop.util.Ui;

import java.io.File;

/**
 * 反编译进度页：后台线程执行反编译，实时输出日志。
 */
public class DecompileActivity extends Activity {

    private TextView logView;
    private StringBuilder log = new StringBuilder();
    private volatile boolean running = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final String apkPath = getIntent().getStringExtra("apk_path");
        if (apkPath == null) { finish(); return; }

        LinearLayout root = Ui.vertical(this);
        root.setBackgroundColor(Ui.BG);
        root.setPadding(Ui.dp(this, 16), Ui.dp(this, 16), Ui.dp(this, 16), Ui.dp(this, 16));

        TextView title = Ui.label(this, "正在反编译", Ui.PRIMARY, 20, true);
        root.addView(title);

        logView = Ui.label(this, "", Ui.TEXT, 13, false);
        logView.setTypeface(android.graphics.Typeface.MONOSPACE);
        logView.setPadding(Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8));
        logView.setBackground(Ui.roundedStroke(Ui.CARD, Ui.BORDER, 10, this));
        logView.setTextIsSelectable(true);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(logView);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(root);

        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    File apk = new File(apkPath);
                    File projectRoot = new File(getFilesDir(), "projects");
                    if (!projectRoot.exists()) projectRoot.mkdirs();
                    String name = apk.getName();
                    if (name.endsWith(".apk")) name = name.substring(0, name.length() - 4);
                    File projectDir = new File(projectRoot, sanitize(name));

                    final AppSettings settings = new AppSettings(DecompileActivity.this);
                    ApkEngine.Progress prog = new ApkEngine.Progress() {
                        @Override public void on(String m) {
                            appendLog("· " + m);
                            if (m.startsWith("反编译完成")) appendLog("\n[完成] 工程已创建于 projects/" + projectDir.getName());
                        }
                    };
                    ProjectInfo info = ApkEngine.decompile(apk, projectDir, prog);
                    appendLog("\n[完成] 反编译成功，共 " + info.dexNames.size() + " 个 dex");

                    // 编译接口使用用户配置的 api level
                    settings.setApiLevel(info.apiLevel);

                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            Intent i = new Intent(DecompileActivity.this, ProjectActivity.class);
                            i.putExtra("project_dir", projectDir.getAbsolutePath());
                            startActivity(i);
                            finish();
                        }
                    });
                } catch (final Exception e) {
                    appendLog("\n[失败] " + e.getMessage());
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            Ui.alert(DecompileActivity.this, "反编译失败", e.getMessage() + "\n\n可尝试重新选择 APK。");
                            finish();
                        }
                    });
                }
            }
        }).start();
    }

    private static String sanitize(String s) {
        return s.replaceAll("[^a-zA-Z0-9_.-]", "_");
    }

    private void appendLog(String s) {
        final String line = s + "\n";
        runOnUiThread(new Runnable() {
            @Override public void run() {
                log.append(line);
                logView.setText(log.toString());
                logView.post(new Runnable() {
                    @Override public void run() {
                        int scrollTo = logView.getLineCount() * logView.getLineHeight();
                        ViewGroup vg = (ViewGroup) logView.getParent();
                        if (vg instanceof ScrollView) ((ScrollView) vg).scrollTo(0, scrollTo);
                    }
                });
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        running = false;
    }
}
