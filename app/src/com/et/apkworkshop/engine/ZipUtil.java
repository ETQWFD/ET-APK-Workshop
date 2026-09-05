package com.et.apkworkshop.engine;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/**
 * ZIP / APK 工具：解压、重建 APK、对齐 STORED 条目到 4 字节（等价于 zipalign）。
 * 纯 Java 实现，可运行在 Android 与桌面 JVM 上。
 */
public final class ZipUtil {

    public static final int STORED = 0;
    public static final int DEFLATED = 8;

    private ZipUtil() {}

    public static boolean isDex(String name) {
        return name.startsWith("classes") && name.endsWith(".dex");
    }

    public static boolean isSignatureEntry(String name) {
        String n = name.toUpperCase(java.util.Locale.US);
        return n.equals("META-INF/MANIFEST.MF")
                || n.startsWith("META-INF/") && (n.endsWith(".SF")
                || n.endsWith(".RSA") || n.endsWith(".DSA") || n.endsWith(".EC"))
                || n.startsWith("META-INF/SIG-");
    }

    /** 解压整个 zip 到目标目录。 */
    public static void unzip(File zipFile, File destDir) throws IOException {
        if (!destDir.exists() && !destDir.mkdirs()) {
            throw new IOException("无法创建目录: " + destDir);
        }
        byte[] buf = new byte[65536];
        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(zipFile)))) {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                if (e.isDirectory()) continue;
                File out = new File(destDir, e.getName());
                File parent = out.getParentFile();
                if (parent != null && !parent.exists() && !parent.mkdirs()) {
                    throw new IOException("无法创建目录: " + parent);
                }
                try (FileOutputStream fos = new FileOutputStream(out)) {
                    int n;
                    while ((n = zis.read(buf)) > 0) fos.write(buf, 0, n);
                }
            }
        }
    }

    /** 列出 zip 内全部条目名。 */
    public static List<String> listEntries(File zipFile) throws IOException {
        List<String> list = new ArrayList<String>();
        try (ZipFile zf = new ZipFile(zipFile)) {
            Enumeration<? extends ZipEntry> en = zf.entries();
            while (en.hasMoreElements()) list.add(en.nextElement().getName());
        }
        return list;
    }

    public static byte[] readEntry(ZipFile zf, ZipEntry e) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream((int) e.getSize());
        try (InputStream is = zf.getInputStream(e)) {
            byte[] buf = new byte[65536];
            int n;
            while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
        }
        return bos.toByteArray();
    }

    /**
     * 重建 APK：
     *  - 从 originalApk 复制所有条目；
     *  - 删除旧的 classes*.dex 与签名条目（META-INF/MANIFEST.MF、*.SF、*.RSA 等）；
     *  - 将 dexEntries（Map: "classes.dex" -> 字节）作为 STORED 写入并对齐到 4 字节；
     *  - 原 STORED 条目保持 STORED 并对齐；原 DEFLATED 条目重新压缩。
     */
    public static void buildApk(File originalApk, File outApk,
                                Map<String, byte[]> dexEntries) throws IOException {
        List<Map.Entry<String, byte[]>> kept = new ArrayList<Map.Entry<String, byte[]>>();
        try (ZipFile zf = new ZipFile(originalApk)) {
            Enumeration<? extends ZipEntry> en = zf.entries();
            while (en.hasMoreElements()) {
                final ZipEntry e = en.nextElement();
                String name = e.getName();
                if (isDex(name) || isSignatureEntry(name)) continue;
                byte[] data = readEntry(zf, e);
                kept.add(new java.util.AbstractMap.SimpleEntry<String, byte[]>(name, data));
            }
        }

        try (ZipWriter zw = new ZipWriter(new BufferedOutputStream(new FileOutputStream(outApk), 1 << 16))) {
            // 先写需要对齐的 STORED 条目与 dex，保证 4 字节对齐
            // 简单起见：全部条目按"对齐优先级"排序：resources.arsc、dex 优先
            List<Map.Entry<String, byte[]>> aligned = new ArrayList<Map.Entry<String, byte[]>>();
            List<Map.Entry<String, byte[]>> others = new ArrayList<Map.Entry<String, byte[]>>();
            for (Map.Entry<String, byte[]> en : kept) {
                if (en.getKey().equals("resources.arsc") || en.getKey().startsWith("res/")) {
                    aligned.add(en);
                } else {
                    others.add(en);
                }
            }
            // 保持原始顺序偏好：先 aliged 再 others，dex 最后
            for (Map.Entry<String, byte[]> en : aligned) writeEntry(zw, en);
            for (Map.Entry<String, byte[]> en : others) writeEntry(zw, en);
            for (Map.Entry<String, byte[]> en : dexEntries.entrySet()) {
                zw.putEntry(en.getKey(), STORED, en.getValue(), System.currentTimeMillis(), true);
            }
            zw.finish();
        }
    }

    private static void writeEntry(ZipWriter zw, Map.Entry<String, byte[]> en) throws IOException {
        String name = en.getKey();
        byte[] data = en.getValue();
        int method = inferMethod(name, data);
        // 所有 STORED 条目一律 4 字节对齐（等价 zipalign -p 4）
        if (method == STORED) {
            zw.putEntry(name, STORED, data, 0L, true);
        } else {
            byte[] c = deflate(data);
            if (c.length < data.length) {
                zw.putEntry(name, DEFLATED, data, 0L, false);
            } else {
                zw.putEntry(name, STORED, data, 0L, true);
            }
        }
    }

    private static int inferMethod(String name, byte[] data) {
        if (name.equals("resources.arsc") || isDex(name)) return STORED;
        String n = name.toLowerCase(java.util.Locale.US);
        if (n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg")
                || n.endsWith(".webp") || n.endsWith(".gif") || n.endsWith(".mp3")
                || n.endsWith(".mp4") || n.endsWith(".ogg") || n.endsWith(".wav")) {
            return STORED; // 已压缩媒体不再压缩
        }
        return DEFLATED;
    }

    public static byte[] deflate(byte[] data) throws IOException {
        Deflater d = new Deflater(Deflater.DEFAULT_COMPRESSION, true);
        d.setInput(data);
        d.finish();
        ByteArrayOutputStream bos = new ByteArrayOutputStream(data.length / 2 + 64);
        byte[] buf = new byte[65536];
        while (!d.finished()) {
            int n = d.deflate(buf);
            bos.write(buf, 0, n);
        }
        d.end();
        return bos.toByteArray();
    }

    public static long crc32(byte[] data) {
        CRC32 c = new CRC32();
        c.update(data);
        return c.getValue();
    }

    /**
     * 手写 ZIP 写入器：完全控制本地头/中央目录/EOCD，支持 STORED 4 字节对齐，
     * 不产生 data descriptor（可被 android 包安装器与 apksig 正常解析）。
     */
    public static final class ZipWriter implements java.io.Closeable {
        private final OutputStream out;
        private final List<byte[]> cd = new ArrayList<byte[]>();
        private long offset = 0;

        public ZipWriter(OutputStream out) { this.out = out; }

        private void leInt(long v) throws IOException {
            out.write((int) (v & 0xFF));
            out.write((int) ((v >>> 8) & 0xFF));
            out.write((int) ((v >>> 16) & 0xFF));
            out.write((int) ((v >>> 24) & 0xFF));
        }
        private void leShort(int v) throws IOException {
            out.write(v & 0xFF);
            out.write((v >>> 8) & 0xFF);
        }

        public void putEntry(String name, int method, byte[] data, long time, boolean align4) throws IOException {
            byte[] nameBytes = name.getBytes("UTF-8");
            int nameLen = nameBytes.length;
            long localHeaderOffset = offset;   // 本地头起始偏移（写入中央目录）
            long crc = crc32(data);
            long csize = data.length;
            long usize = data.length;
            byte[] comp = data;
            if (method == DEFLATED) {
                comp = deflate(data);
                csize = comp.length;
            }
            // 计算 extra 填充使数据区起始 4 字节对齐
            int extraLen = 0;
            if (align4) {
                long headerEnd = 30 + nameLen; // 本地头长度 + 名称
                long pad = (4 - ((offset + headerEnd) % 4)) % 4;
                extraLen = (int) pad;
            }
            long dosTime = time <= 0 ? System.currentTimeMillis() : time;
            int dt = dosDateTime(dosTime);
            // local file header
            leInt(0x04034b50L);
            leShort(20);        // version needed
            leShort(0x0800);    // flags: UTF-8
            leShort(method);
            leShort(dt >> 16);  // time
            leShort(dt & 0xFFFF); // date
            leInt(crc);
            leInt(csize);
            leInt(usize);
            leShort(nameLen);
            leShort(extraLen);
            out.write(nameBytes);
            if (extraLen > 0) for (int i = 0; i < extraLen; i++) out.write(0);
            long dataOffset = offset + 30 + nameLen + extraLen;
            out.write(comp);
            offset = dataOffset + csize;
            // central directory entry
            ByteArrayOutputStream cen = new ByteArrayOutputStream(64 + nameLen);
            writeLeInt(cen, 0x02014b50L);   // central sig
            writeLeShort(cen, 20);          // version made by
            writeLeShort(cen, 20);          // version needed
            writeLeShort(cen, 0x0800);      // flags: UTF-8
            writeLeShort(cen, method);
            writeLeShort(cen, dt >> 16);    // time
            writeLeShort(cen, dt & 0xFFFF); // date
            writeLeInt(cen, crc);
            writeLeInt(cen, csize);
            writeLeInt(cen, usize);
            writeLeShort(cen, nameLen);
            writeLeShort(cen, 0);           // extra len
            writeLeShort(cen, 0);           // comment len
            writeLeShort(cen, 0);           // disk number
            writeLeShort(cen, 0);           // internal attrs
            writeLeInt(cen, 0);             // external attrs
            writeLeInt(cen, localHeaderOffset);  // local header offset
            cen.write(nameBytes);
            cd.add(cen.toByteArray());
        }

        private static void writeLeInt(ByteArrayOutputStream b, long v) {
            b.write((int) (v & 0xFF)); b.write((int) ((v >>> 8) & 0xFF));
            b.write((int) ((v >>> 16) & 0xFF)); b.write((int) ((v >>> 24) & 0xFF));
        }
        private static void writeLeShort(ByteArrayOutputStream b, int v) {
            b.write(v & 0xFF); b.write((v >>> 8) & 0xFF);
        }

        public void finish() throws IOException {
            long cdOffset = offset;
            long cdSize = 0;
            for (byte[] c : cd) {
                out.write(c);
                cdSize += c.length;
            }
            long cdEnd = cdOffset + cdSize;
            leInt(0x06054b50L);
            leShort(0);
            leShort(0);
            leShort(cd.size());
            leShort(cd.size());
            leInt(cdSize);
            leInt(cdOffset);
            leShort(0);
            out.flush();
            offset = cdEnd + 22;
        }

        @Override
        public void close() throws IOException {
            try { finish(); } finally { out.close(); }
        }
    }

    /** 将 java time 转 DOS time/date 合成字。 */
    public static int dosDateTime(long millis) {
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.setTimeInMillis(millis);
        int year = c.get(java.util.Calendar.YEAR);
        if (year < 1980) year = 1980;
        int time = (c.get(java.util.Calendar.HOUR_OF_DAY) << 11)
                | (c.get(java.util.Calendar.MINUTE) << 5)
                | (c.get(java.util.Calendar.SECOND) >> 1);
        int date = ((year - 1980) << 9)
                | ((c.get(java.util.Calendar.MONTH) + 1) << 5)
                | c.get(java.util.Calendar.DAY_OF_MONTH);
        return (time << 16) | date;
    }
}
