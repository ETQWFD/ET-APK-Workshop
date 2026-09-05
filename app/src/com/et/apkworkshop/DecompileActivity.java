package com.et.apkworkshop;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import com.et.apkworkshop.util.AppSettings;
import com.et.apkworkshop.util.Ui;

import java.io.File;

/**
 * 反编译/脱壳进度页：启动前台服务执行，轮询 WorkState 显示进度。
 * 前台服务保证不被系统杀死，通知栏显示实时进度。
 */
public class DecompileActivity extends Activity {

    private TextView logView;
    private ProgressBar progressBar;
    private TextView statusText;
    private StringBuilder log = new StringBuilder();
    private Handler handler = new Handler();
    private String apkPath;
    private String projectDir;
    private boolean unpackMode;
    private String lastMessage = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.applyAnimeBg(this);

        apkPath = getIntent().getStringExtra("apk_path");
        projectDir = getIntent().getStringExtra("project_dir");
        unpackMode = "unpack".equals(getIntent().getStringExtra("mode"));

        if (apkPath == null || projectDir == null) { finish(); return; }

        LinearLayout root = Ui.vertical(this);
        root.setBackgroundColor(Ui.BG_OVERLAY);
        root.setPadding(Ui.dp(this, 16), Ui.dp(this, 16), Ui.dp(this, 16), Ui.dp(this, 16));

        String titleStr = unpackMode ? "正在脱壳提取 dex…" : "正在反编译 APK…";
        TextView title = Ui.label(this, titleStr, Ui.PRIMARY, 20, true);
        root.addView(title);

        // 进度条
        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setProgress(0);
        progressBar.getProgressDrawable().setColorFilter(Ui.PRIMARY, android.graphics.PorterDuff.Mode.SRC_IN);
        root.addView(progressBar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 8)));

        statusText = Ui.label(this, "初始化…", Ui.TEXT_DIM, 13, false);
        statusText.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 6));
        root.addView(statusText);

        logView = Ui.label(this, "", Ui.TEXT, 13, false);
        logView.setTypeface(Typeface.MONOSPACE);
        logView.setPadding(Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8));
        logView.setBackground(Ui.roundedStroke(Ui.CARD_OVERLAY, Ui.BORDER, 10, this));
        logView.setTextIsSelectable(true);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(logView);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        TextView hint = Ui.label(this, "已切换为前台服务，通知栏可见进度，不会被系统杀死。可按返回键后台运行。", Ui.TEXT_DIM, 11, false);
        root.addView(hint, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        setContentView(root);

        // 启动前台服务
        Intent svc = new Intent(this, WorkService.class);
        svc.putExtra(WorkService.EXTRA_APK_PATH, apkPath);
        svc.putExtra(WorkService.EXTRA_PROJECT_DIR, projectDir);
        svc.putExtra(WorkService.EXTRA_API_LEVEL, new AppSettings(this).getApiLevel());
        if (unpackMode) {
            svc.setAction(WorkService.ACTION_UNPACK);
        } else {
            svc.setAction(WorkService.ACTION_DECOMPILE);
        }
        if (Build.VERSION.SDK_INT >= 26) {
            startForegroundService(svc);
        } else {
            startService(svc);
        }

        appendLog((unpackMode ? "[脱壳] " : "[反编译] ") + "启动前台服务…");
        appendLog("工程目录: " + projectDir);

        // 轮询进度
        handler.postDelayed(pollRunnable, 500);
    }

    private Runnable pollRunnable = new Runnable() {
        @Override public void run() {
            WorkState ws = WorkState.get();
            if (ws.isRunning()) {
                if (!ws.message.equals(lastMessage)) {
                    lastMessage = ws.message;
                    appendLog("· " + ws.message);
                }
                progressBar.setProgress(ws.progress);
                statusText.setText(ws.message);
                handler.postDelayed(this, 500);
            } else if (ws.isDone()) {
                progressBar.setProgress(100);
                statusText.setText("完成");
                appendLog("\n[完成] " + ws.message);
                appendLog("工程目录: " + projectDir);
                // 延迟跳转，让用户看到完成状态
                handler.postDelayed(new Runnable() {
                    @Override public void run() {
                        // 如果是脱壳模式，把脱壳结果也写入 info.json 以便在工程页打开
                        if (unpackMode) {
                            try {
                                com.et.apkworkshop.engine.ProjectInfo info = new com.et.apkworkshop.engine.ProjectInfo();
                                info.projectDir = new File(projectDir);
                                info.name = new File(apkPath).getName();
                                info.created = System.currentTimeMillis();
                                info.apiLevel = 34;
                                info.dexNames = new java.util.ArrayList<String>();
                                // 扫描 unpacked_dex 目录
                                File unpacked = new File(projectDir, "unpacked_dex");
                                if (unpacked.exists()) {
                                    File[] dexes = unpacked.listFiles();
                                    if (dexes != null) for (File d : dexes) if (d.getName().endsWith(".dex")) info.dexNames.add(d.getName());
                                }
                                if (info.dexNames.isEmpty()) info.dexNames.add("classes.dex");
                                info.save();
                            } catch (Exception ignored) {}
                        }
                        Intent i = new Intent(DecompileActivity.this, ProjectActivity.class);
                        i.putExtra("project_dir", projectDir);
                        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        startActivity(i);
                        finish();
                    }
                }, 800);
            } else if (ws.isError()) {
                progressBar.setProgress(0);
                statusText.setText("失败");
                appendLog("\n[失败] " + ws.error);
                Ui.alert(DecompileActivity.this, unpackMode ? "脱壳失败" : "反编译失败",
                        (ws.error != null ? ws.error : "未知错误") + "\n\n可尝试重新选择 APK。");
            } else {
                // 还没开始，继续等
                handler.postDelayed(this, 500);
            }
        }
    };

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
        handler.removeCallbacks(pollRunnable);
    }
}
