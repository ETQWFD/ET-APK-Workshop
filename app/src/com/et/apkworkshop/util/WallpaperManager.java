package com.et.apkworkshop.util;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;

import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 随机二次元壁纸管理器：
 * - 从在线 API 获取随机二次元美少女图片
 * - 每 6 秒自动切换，不重复（最近 20 张不重复）
 * - API 失败时回退到本地内置背景图
 * - 通过监听器通知所有 Activity 更新背景
 */
public final class WallpaperManager {

    public interface WallpaperListener {
        void onWallpaperChanged(Bitmap bmp);
    }

    // 在线随机二次元壁纸 API（按优先级排列，国内可用）
    private static final String[] APIS = {
            "https://api.anosu.top/img/",
            "https://www.dmoe.cc/random.php",
            "https://acg.toubiec.cn/random.php",
    };

    // 本地兜底背景图
    private static final int[] LOCAL_BGS = {
            com.et.apkworkshop.R.drawable.bg_1,
            com.et.apkworkshop.R.drawable.bg_2,
            com.et.apkworkshop.R.drawable.bg_3,
    };

    private static final long ROTATE_INTERVAL = 60000; // 60秒（每分钟更换，次次不同）
    private static final int MAX_HISTORY = 50;

    private static WallpaperManager instance;
    private final List<WeakReference<WallpaperListener>> listeners = new ArrayList<>();
    private final Set<String> recentUrls = new LinkedHashSet<String>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Handler workHandler;
    private Thread workThread;
    private volatile boolean running = false;
    private Bitmap currentBitmap;
    private int localIndex = 0;
    private int apiIndex = 0;

    private WallpaperManager() {}

    public static synchronized WallpaperManager get() {
        if (instance == null) instance = new WallpaperManager();
        return instance;
    }

    public void addListener(WallpaperListener l) {
        listeners.add(new WeakReference<WallpaperListener>(l));
        if (currentBitmap != null) l.onWallpaperChanged(currentBitmap);
    }

    public void removeListener(WallpaperListener l) {
        for (int i = listeners.size() - 1; i >= 0; i--) {
            WallpaperListener wl = listeners.get(i).get();
            if (wl == null || wl == l) listeners.remove(i);
        }
    }

    public Bitmap getCurrent() { return currentBitmap; }

    /** 启动壁纸轮换（在 Application 或首个 Activity 调用） */
    public void start() {
        if (running) return;
        running = true;
        workThread = new Thread(new Runnable() {
            @Override public void run() {
                Looper.prepare();
                workHandler = new Handler();
                // 立即加载第一张
                loadNext();
                Looper.loop();
            }
        }, "WallpaperLoader");
        workThread.start();
    }

    public void stop() {
        running = false;
        if (workHandler != null) workHandler.removeCallbacksAndMessages(null);
        if (workThread != null) workThread.interrupt();
    }

    private void loadNext() {
        if (!running) return;
        new Thread(new Runnable() {
            @Override public void run() {
                Bitmap bmp = fetchOnline();
                if (bmp == null) bmp = fetchLocal();
                if (bmp != null) {
                    currentBitmap = bmp;
                    notifyListeners(bmp);
                }
                // 6秒后加载下一张
                if (running && workHandler != null) {
                    workHandler.postDelayed(new Runnable() {
                        @Override public void run() { loadNext(); }
                    }, ROTATE_INTERVAL);
                }
            }
        }).start();
    }

    private Bitmap fetchOnline() {
        // 轮询 API，避免单个 API 挂掉
        for (int attempt = 0; attempt < APIS.length * 2; attempt++) {
            String api = APIS[apiIndex % APIS.length];
            apiIndex++;
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(api).openConnection();
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(10000);
                conn.setInstanceFollowRedirects(true);
                conn.setRequestProperty("User-Agent", "ET-APK-Workshop");
                int code = conn.getResponseCode();
                if (code == 200) {
                    String url = conn.getURL().toString();
                    // 去重：最近 20 张不重复
                    if (recentUrls.contains(url)) {
                        conn.disconnect();
                        continue;
                    }
                    InputStream is = conn.getInputStream();
                    Bitmap bmp = BitmapFactory.decodeStream(is);
                    is.close();
                    conn.disconnect();
                    if (bmp != null) {
                        recentUrls.add(url);
                        if (recentUrls.size() > MAX_HISTORY) {
                            String first = recentUrls.iterator().next();
                            recentUrls.remove(first);
                        }
                        return bmp;
                    }
                } else {
                    conn.disconnect();
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    private Bitmap fetchLocal() {
        int resId = LOCAL_BGS[localIndex % LOCAL_BGS.length];
        localIndex++;
        try {
            // 从资源加载，缩放以节省内存
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = 2;
            // 需要 Context，这里用 AppContext
            android.content.Context ctx = com.et.apkworkshop.util.AppContext.get();
            if (ctx != null) {
                return BitmapFactory.decodeResource(ctx.getResources(), resId, opts);
            }
        } catch (Exception ignored) {}
        return null;
    }

    private void notifyListeners(final Bitmap bmp) {
        mainHandler.post(new Runnable() {
            @Override public void run() {
                for (int i = listeners.size() - 1; i >= 0; i--) {
                    WallpaperListener l = listeners.get(i).get();
                    if (l == null) { listeners.remove(i); continue; }
                    try { l.onWallpaperChanged(bmp); } catch (Exception ignored) {}
                }
            }
        });
    }
}
