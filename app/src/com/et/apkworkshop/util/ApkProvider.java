package com.et.apkworkshop.util;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.webkit.MimeTypeMap;
import java.io.File;
import java.io.FileNotFoundException;

/**
 * 极简 FileProvider：把 /storage/emulated/0/lookapks/ 下的文件以 content:// 暴露，
 * 用于安装 APK（ACTION_VIEW/INSTALL）与分享（ACTION_SEND）。
 * authority: com.et.apkworkshop.files
 */
public class ApkProvider extends ContentProvider {
    public static final String AUTHORITY = "com.et.apkworkshop.files";

    @Override public boolean onCreate() { return true; }

    @Override public String getType(Uri uri) {
        File f = resolve(uri);
        if (f == null) return "*/*";
        String name = f.getName();
        if (name.endsWith(".apk")) return "application/vnd.android.package-archive";
        if (name.endsWith(".smali") || name.endsWith(".xml") || name.endsWith(".txt")
                || name.endsWith(".json") || name.endsWith(".yml")) return "text/plain";
        String ext = MimeTypeMap.getFileExtensionFromUrl(name);
        String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
        return mime == null ? "*/*" : mime;
    }

    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        File f = resolve(uri);
        if (f == null) throw new FileNotFoundException("file not found: " + uri);
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    private File resolve(Uri uri) {
        if (uri == null) return null;
        String p = uri.getPath();
        if (p == null) return null;
        while (p.startsWith("/")) p = p.substring(1);
        if (p.isEmpty()) return null;
        try {
            // 根目录：/storage/emulated/0/lookapks/
            File root = Storage.getRoot();
            File f = new File(root, p);
            String canonicalRoot = root.getCanonicalPath();
            String canonicalFile = f.getCanonicalPath();
            if (canonicalFile.startsWith(canonicalRoot + File.separator) && f.exists()) return f;
        } catch (Exception ignored) {}
        return null;
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { return 0; }
}
