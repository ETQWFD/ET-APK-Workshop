package com.et.apkworkshop;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import com.et.apkworkshop.engine.ApkEngine;
import com.et.apkworkshop.engine.ProjectInfo;
import com.et.apkworkshop.engine.Unpacker;

import java.io.File;

/**
 * 前台服务：在通知栏显示进度，保证反编译/编译/脱壳不被系统杀死。
 * Activity 通过轮询 WorkState 单例获取进度。
 */
public class WorkService extends Service {
    public static final String ACTION_DECOMPILE = "com.et.apkworkshop.DECOMPILE";
    public static final String ACTION_COMPILE = "com.et.apkworkshop.COMPILE";
    public static final String ACTION_UNPACK = "com.et.apkworkshop.UNPACK";

    public static final String EXTRA_APK_PATH = "apk_path";
    public static final String EXTRA_PROJECT_DIR = "project_dir";
    public static final String EXTRA_API_LEVEL = "api_level";

    private static final int NOTIF_ID = 1001;
    private static final String CHANNEL_ID = "et_work_channel";

    private NotificationManager nm;
    private volatile boolean running = false;

    @Override
    public void onCreate() {
        super.onCreate();
        nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        createChannel();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "ETC APK 工坊", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("反编译 / 编译打包进度");
            ch.setSound(null, null);
            nm.createNotificationChannel(ch);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || intent.getAction() == null) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (running) {
            return START_NOT_STICKY;
        }
        running = true;

        final String action = intent.getAction();
        final String apkPath = intent.getStringExtra(EXTRA_APK_PATH);
        final String projectDir = intent.getStringExtra(EXTRA_PROJECT_DIR);
        final int apiLevel = intent.getIntExtra(EXTRA_API_LEVEL, 34);

        String title = "正在处理…";
        if (ACTION_DECOMPILE.equals(action)) title = "正在反编译 APK…";
        else if (ACTION_COMPILE.equals(action)) title = "正在编译打包…";
        else if (ACTION_UNPACK.equals(action)) title = "正在脱壳…";

        startForeground(NOTIF_ID, buildNotification(title, "初始化…", 0));

        WorkState ws = WorkState.get();
        ws.reset();
        ws.status = WorkState.STATUS_RUNNING;
        ws.workType = action;
        ws.projectDir = projectDir;

        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    if (ACTION_DECOMPILE.equals(action)) {
                        doDecompile(apkPath, projectDir, apiLevel);
                    } else if (ACTION_COMPILE.equals(action)) {
                        doCompile(projectDir);
                    } else if (ACTION_UNPACK.equals(action)) {
                        doUnpack(apkPath, projectDir);
                    }
                    WorkState.get().status = WorkState.STATUS_DONE;
                    updateNotification("完成", WorkState.get().message, 100);
                } catch (Exception e) {
                    WorkState.get().status = WorkState.STATUS_ERROR;
                    WorkState.get().error = e.getMessage() != null ? e.getMessage() : e.toString();
                    updateNotification("失败", WorkState.get().error, 0);
                } finally {
                    running = false;
                    try { Thread.sleep(500); } catch (InterruptedException ignored) {}
                    stopForeground(true);
                    stopSelf();
                }
            }
        }).start();

        return START_NOT_STICKY;
    }

    private void doDecompile(String apkPath, String projectDir, int apiLevel) throws Exception {
        File apk = new File(apkPath);
        File dir = new File(projectDir);
        ApkEngine.Progress prog = new ApkEngine.Progress() {
            int step = 0;
            @Override public void on(String message) {
                step++;
                WorkState.get().message = message;
                WorkState.get().progress = Math.min(95, step * 10);
                updateNotification("正在反编译…", message, WorkState.get().progress);
            }
        };
        ProjectInfo info = ApkEngine.decompile(apk, dir, prog);
        WorkState.get().resultPath = dir.getAbsolutePath();
        WorkState.get().message = "反编译完成: " + info.dexNames.size() + " 个 dex";
    }

    private void doCompile(String projectDir) throws Exception {
        File dir = new File(projectDir);
        ProjectInfo info = ProjectInfo.fromJson(dir);
        ApkEngine.Progress prog = new ApkEngine.Progress() {
            int step = 0;
            @Override public void on(String message) {
                step++;
                WorkState.get().message = message;
                WorkState.get().progress = Math.min(95, step * 15);
                updateNotification("正在编译打包…", message, WorkState.get().progress);
            }
        };
        File signed = ApkEngine.compile(info, prog);
        WorkState.get().resultPath = signed.getAbsolutePath();
        WorkState.get().message = "编译完成: " + signed.getName();
    }

    private void doUnpack(String apkPath, String projectDir) throws Exception {
        File apk = new File(apkPath);
        File dir = new File(projectDir);
        Unpacker.UnpackResult result = Unpacker.unpack(apk, dir, new Unpacker.Progress() {
            @Override public void on(String message) {
                WorkState.get().message = message;
                updateNotification("正在脱壳…", message, 50);
            }
        });
        WorkState.get().resultPath = dir.getAbsolutePath();
        WorkState.get().message = "脱壳完成: 提取 " + result.dexCount + " 个 dex, 检测到 " + result.detectedPacker;
    }

    private Notification buildNotification(String title, String text, int progress) {
        Intent i = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, i,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));
        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= 26) {
            b = new Notification.Builder(this, CHANNEL_ID);
        } else {
            b = new Notification.Builder(this);
        }
        b.setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_menu_upload)
                .setContentIntent(pi)
                .setOngoing(true)
                .setProgress(100, progress, progress <= 0);
        if (Build.VERSION.SDK_INT >= 21) {
            b.setCategory(Notification.CATEGORY_PROGRESS);
        }
        return b.build();
    }

    private void updateNotification(String title, String text, int progress) {
        nm.notify(NOTIF_ID, buildNotification(title, text, progress));
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
