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

    // 在线随机二次元壁纸 API（按优先级排列，国内可用，均为公开动漫图库）
    // 部分 API 返回图片流，部分返回 JSON（其中含图片 URL），均已兼容
    private static final String[] APIS = {
            "https://www.loliapi.com/acg/?type=pc",
            "https://api.anosu.top/img/",
            "https://www.dmoe.cc/random.php",
            "https://acg.toubiec.cn/random.php",
            "https://api.ixiaowiai.cn/api/api.php",
            "https://api.vvhan.com/api/acgimg?type=pc",
            "https://t.alcy.cc/pc",
            "https://api.likepoems.com/img/pc",
            "https://img.paulzzh.tech/touhou/random",
            "https://api.btstu.cn/sjbz/api.php?lx=dongman&format=json",
    };

    // 本地兜底背景图
    private static final int[] LOCAL_BGS = {
            com.et.apkworkshop.R.drawable.bg_1,
            com.et.apkworkshop.R.drawable.bg_2,
            com.et.apkworkshop.R.drawable.bg_3,
    };

    private static final long ROTATE_INTERVAL = 6000; // 6秒（用户要求每 6 秒自动更换）
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
                conn.setRequestProperty("User-Agent", "ET-APK-Workshop/2.29");
                conn.setRequestProperty("Accept", "image/*, application/json, */*");
                int code = conn.getResponseCode();
                if (code == 200) {
                    String url = conn.getURL().toString();
                    String contentType = conn.getContentType();
                    if (contentType == null) contentType = "";
                    Bitmap bmp = null;
                    String imgKey = null;
                    if (contentType.contains("json")) {
                        // JSON 接口：读取响应，解析出图片 URL 再加载
                        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                        try (InputStream is = conn.getInputStream()) {
                            byte[] buf = new byte[8192]; int n;
                            while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
                        }
                        conn.disconnect();
                        String json = new String(bos.toByteArray(), "UTF-8");
                        String imgUrl = extractImageUrl(json);
                        if (imgUrl != null) {
                            imgKey = imgUrl;
                            bmp = loadFromUrl(imgUrl);
                        }
                    } else {
                        // 直接返回图片流
                        try (InputStream is = conn.getInputStream()) {
                            bmp = BitmapFactory.decodeStream(is);
                        }
                        conn.disconnect();
                        imgKey = url;
                    }
                    if (bmp != null) {
                        if (recentUrls.contains(imgKey)) {
                            // 同源图片刚刚用过，继续轮询其他源
                            continue;
                        }
                        recentUrls.add(imgKey);
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

    /** 从 JSON 响应中提取图片 URL（支持常见字段 img/url/pic/wallpaper/data 等） */
    private static String extractImageUrl(String json) {
        if (json == null || json.isEmpty()) return null;
        String[] keys = {"\"img\"", "\"url\"", "\"pic\"", "\"wallpaper\"", "\"image\"", "\"data\"", "\"src\""};
        for (String k : keys) {
            int idx = json.indexOf(k);
            if (idx >= 0) {
                int s = json.indexOf('"', idx + k.length() + 1);
                if (s < 0) s = json.indexOf(':', idx + k.length());
                if (s >= 0) {
                    int e = json.indexOf('"', s + 1);
                    if (e > s) {
                        String v = json.substring(s + 1, e);
                        if (v.startsWith("http")) return v;
                        // 可能是 https:\/\/ 转义
                        v = v.replace("\\/", "/");
                        if (v.startsWith("http")) return v;
                    }
                }
            }
        }
        return null;
    }

    /** 从 URL 加载图片 */
    private static Bitmap loadFromUrl(String imgUrl) {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(imgUrl).openConnection();
            c.setConnectTimeout(8000);
            c.setReadTimeout(10000);
            c.setInstanceFollowRedirects(true);
            c.setRequestProperty("User-Agent", "ET-APK-Workshop/2.29");
            c.setRequestProperty("Referer", "https://www.bing.com/");
            int code = c.getResponseCode();
            if (code == 200) {
                try (InputStream is = c.getInputStream()) {
                    return BitmapFactory.decodeStream(is);
                }
            } else {
                c.disconnect();
            }
        } catch (Exception ignored) {}
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
