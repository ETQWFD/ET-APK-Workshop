package com.et.apkworkshop.engine;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 脱壳引擎：扫描 APK 中所有 dex 文件（包括隐藏在 assets/lib 中的），
 * 检测常见加固壳，提取所有 dex 到工程目录。
 *
 * 注意：真正的动态脱壳需要 root 或调试环境，本工具提供静态提取 + 壳检测，
 * 对于未完全加密的壳可直接提取出真实 dex。
 */
public final class Unpacker {

    public interface Progress {
        void on(String message);
    }

    public static final class UnpackResult {
        public int dexCount = 0;
        public String detectedPacker = "无";
        public List<String> dexFiles = new ArrayList<String>();
        public List<String> packerLibs = new ArrayList<String>();
    }

    // 常见加固壳的特征 so 库
    private static final String[][] PACKER_SIGNATURES = {
        {"360加固", "libjiagu.so", "libjiagu_art.so", "libjiagu_x86.so", "libjiagu_a64.so", "libjiagu_x86_64.so"},
        {"腾讯乐固", "libshell-super.2019.so", "libBugly.so", "libshella.so", "libshella-2.28.6.11.so", "libshellx.so", "libdexhelper.so", "libtup.so"},
        {"腾讯加固", "libtencentlegu.so", "liblegu.so", "libDexHelper.so", "libDexHelper-x86.so", "libshell.so"},
        {"爱加密", "libexec.so", "libexecmain.so", "libexecservice.so", "libijiami.so", "libDexHelper.so"},
        {"梆梆加固", "libsecexe.so", "libsecmain.so", "libSecShell.so", "libDexHelper.so", "libSecShell-x86.so"},
        {"百度加固", "libbaiduprotect.so", "libbaiduprotect_x86.so", "libprotectClass.so"},
        {"阿里聚安全", "libsgmain.so", "libsgsecuritybody.so", "libsgmiddletier.so", "libsgcrypto.so"},
        {"网易易盾", "libnesec.so", "libgdtdata.so", "libnesec_x86.so", "libnesecex.so"},
        {"顶象", "libdxs.so", "libdexload.so", "libdxs_x86.so"},
        {"娜迦", "libchaosvmp.so", "libddog.so", "libchaosvmp_64.so"},
        {"几维安全", "libkwscmm.so", "libkwscrash.so"},
        {"通付盾", "libegis.so", "libegis-x86.so", "libshell-x86.so"},
        {"APKProtect", "libprotect.so", "libAPKProtect.so"},
        {"瑞星加固", "librsprotect.so", "librsprotect_x86.so"},
        {"盾牌加固", "libshield.so", "libshield_x86.so"},
        {"爱加密V2", "libexecv2.so", "libexecmainv2.so"},
    };

    // 不需要深度扫描的扩展名（图片/音频/视频/字体/文本等不可能嵌入 dex）
    private static final String[] SKIP_EXT = {
            ".png", ".jpg", ".jpeg", ".webp", ".gif", ".bmp", ".ico",
            ".mp3", ".ogg", ".wav", ".m4a", ".aac", ".flac", ".mid",
            ".mp4", ".3gp", ".webm", ".mkv", ".avi",
            ".ttf", ".otf", ".ttc", ".woff", ".woff2",
            ".xml", ".json", ".txt", ".html", ".htm", ".css", ".js",
            ".properties", ".conf", ".cfg", ".ini", ".md",
            ".arsc", ".9.png"
    };
    private static final long MAX_DEEP_SCAN_SIZE = 15 * 1024 * 1024; // 单文件超过15MB不深度扫描

    private static boolean shouldDeepScan(String name, long size) {
        if (size > MAX_DEEP_SCAN_SIZE) return false;
        if (size <= 40) return false;
        String lower = name.toLowerCase();
        for (String ext : SKIP_EXT) {
            if (lower.endsWith(ext)) return false;
        }
        return true;
    }

    private Unpacker() {}

    /**
     * 脱壳：提取 APK 中所有 dex，检测加固壳。
     * @param apkFile 原始 APK
     * @param outDir  输出目录（工程目录）
     * @param prog    进度回调
     */
    public static UnpackResult unpack(File apkFile, File outDir, Progress prog) throws Exception {
        if (prog == null) prog = new Progress() { @Override public void on(String m) {} };
        UnpackResult result = new UnpackResult();

        if (!outDir.exists()) outDir.mkdirs();
        File unpackedDir = new File(outDir, "unpacked_dex");
        if (!unpackedDir.exists()) unpackedDir.mkdirs();

        prog.on("扫描 APK 结构…");
        ZipFile zip = new ZipFile(apkFile);
        try {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry e = entries.nextElement();
                String name = e.getName();

                // 检测壳 so
                for (String[] sig : PACKER_SIGNATURES) {
                    for (int i = 1; i < sig.length; i++) {
                        if (name.endsWith("/" + sig[i]) || name.equals(sig[i])) {
                            if (!result.detectedPacker.equals(sig[0])) {
                                result.detectedPacker = sig[0];
                            }
                            if (!result.packerLibs.contains(name)) {
                                result.packerLibs.add(name);
                            }
                        }
                    }
                }

                // 提取所有 dex（包括根目录、assets、lib 等位置）
                if (name.toLowerCase().endsWith(".dex") && !e.isDirectory()) {
                    String outName = name.replace('/', '_');
                    if (outName.startsWith("_")) outName = outName.substring(1);
                    File out = new File(unpackedDir, outName);
                    extractEntry(zip, e, out);
                    result.dexFiles.add(out.getAbsolutePath());
                    result.dexCount++;
                    prog.on("提取 dex: " + name);
                }

                // 全文件深度扫描：只对可能含 dex 的文件扫描（跳过图片/音频/字体等）
                if (!e.isDirectory() && shouldDeepScan(name, e.getSize())) {
                    try {
                        InputStream is = zip.getInputStream(e);
                        byte[] data = readAllBytes(is);
                        is.close();
                        List<int[]> offsets = findDexMagic(data);
                        for (int[] off : offsets) {
                            int start = off[0];
                            int dexLen = off[1];
                            if (dexLen <= 0 || start + dexLen > data.length) {
                                // 长度不可信，取到文件末尾
                                dexLen = data.length - start;
                            }
                            if (dexLen < 100) continue; // 太小不是有效 dex
                            String outName = "deepscan_" + name.replace('/', '_').replace('.', '_')
                                    + "_off" + start + ".dex";
                            File out = new File(unpackedDir, outName);
                            java.io.FileOutputStream fos = new java.io.FileOutputStream(out);
                            try { fos.write(data, start, dexLen); } finally { fos.close(); }
                            // 去重：按内容哈希（更精确，避免不同偏移提取同一 dex）
                            boolean dup = false;
                            String hash = sha256Hex(out);
                            for (String existing : result.dexFiles) {
                                if (hash.equals(sha256Hex(new File(existing)))) { dup = true; break; }
                            }
                            if (!dup) {
                                result.dexFiles.add(out.getAbsolutePath());
                                result.dexCount++;
                                prog.on("深度扫描发现 dex: " + name + " @offset " + start);
                            } else {
                                out.delete();
                            }
                        }
                    } catch (Exception ex) {
                        // 单个文件扫描失败不影响整体
                    }
                }
            }
        } finally {
            zip.close();
        }

        // 写脱壳报告
        File report = new File(outDir, "unpack_report.txt");
        StringBuilder sb = new StringBuilder();
        sb.append("=== ET APK 工坊 脱壳报告 ===\n");
        sb.append("APK: ").append(apkFile.getName()).append("\n");
        sb.append("检测到加固壳: ").append(result.detectedPacker).append("\n");
        sb.append("提取 dex 数量: ").append(result.dexCount).append("\n");
        if (!result.packerLibs.isEmpty()) {
            sb.append("壳相关 so:\n");
            for (String lib : result.packerLibs) sb.append("  - ").append(lib).append("\n");
        }
        sb.append("\n提取的 dex 文件:\n");
        for (String dex : result.dexFiles) sb.append("  - ").append(dex).append("\n");
        sb.append("\n说明:\n");
        sb.append("1. 若检测到加固壳，提取的 dex 可能是壳的加载器而非真实代码。\n");
        sb.append("2. 对于动态加密的壳，需要在 root 设备上运行时内存转储才能完全脱壳。\n");
        sb.append("3. 可尝试对提取的 dex 逐个反编译，找到包含真实 Activity 的那个。\n");
        sb.append("4. 建议反编译顺序：classes.dex → classes2.dex → classes3.dex → 体积较大的 deepscan_*.dex。\n");
        if (result.detectedPacker.equals("无")) {
            sb.append("5. 未检测到加固壳特征：该 APK 可能是未加固或使用冷门壳，提取的 dex 通常可直接反编译使用。\n");
        } else {
            sb.append("5. 检测到 ").append(result.detectedPacker).append("：\n");
            sb.append("   · 若壳将真实 dex 加密存放在 assets/ 或 lib/ 中，深度扫描已尝试提取；\n");
            sb.append("   · 若仍无法还原真实代码，可尝试：免root运行内存转储、Shizuku 模式、或先用工具将 APK 脱壳后再分析。\n");
        }
        FileOutputStream fos = new FileOutputStream(report);
        try { fos.write(sb.toString().getBytes("UTF-8")); } finally { fos.close(); }

        prog.on("脱壳完成: 提取 " + result.dexCount + " 个 dex, 检测到 " + result.detectedPacker);
        return result;
    }

    private static String sha256Hex(File f) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            java.io.FileInputStream in = new java.io.FileInputStream(f);
            try {
                byte[] buf = new byte[65536]; int n;
                while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
            } finally { in.close(); }
            StringBuilder sb = new StringBuilder();
            for (byte b : md.digest()) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(f.length());
        }
    }

    private static void extractEntry(ZipFile zip, ZipEntry e, File out) throws Exception {
        if (!out.getParentFile().exists()) out.getParentFile().mkdirs();
        InputStream is = zip.getInputStream(e);
        FileOutputStream os = new FileOutputStream(out);
        try {
            byte[] buf = new byte[65536];
            int n;
            while ((n = is.read(buf)) > 0) os.write(buf, 0, n);
        } finally {
            is.close();
            os.close();
        }
    }

    private static byte[] readAllBytes(InputStream is) throws Exception {
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[65536];
        int n;
        while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
        return bos.toByteArray();
    }

    /**
     * 在字节流中查找所有 dex 魔数（dex\n035 / dex\n036）。
     * 返回 [起始偏移, dex文件长度] 列表；长度从 dex 头解析，不可信时返回 0。
     */
    private static List<int[]> findDexMagic(byte[] data) {
        // 优先使用 C++ 原生引擎（速度快 5~10 倍），不可用时回退 Java 实现
        if (NativeUnpacker.isAvailable()) {
            try {
                int[] raw = NativeUnpacker.scanDexMagic(data, 0, data.length);
                List<int[]> results = new ArrayList<int[]>();
                if (raw != null) {
                    for (int i = 0; i + 1 < raw.length; i += 2) {
                        results.add(new int[]{raw[i], raw[i + 1]});
                    }
                }
                return results;
            } catch (Exception ignored) {}
        }
        return findDexMagicJava(data);
    }

    private static List<int[]> findDexMagicJava(byte[] data) {
        List<int[]> results = new ArrayList<int[]>();
        if (data == null || data.length < 8) return results;
        // dex 魔数: 'd','e','x','\n','0','3','5' 或 '0','3','6'
        for (int i = 0; i < data.length - 8; i++) {
            if (data[i] == 'd' && data[i+1] == 'e' && data[i+2] == 'x' && data[i+3] == '\n'
                    && data[i+4] == '0' && data[i+5] == '3'
                    && (data[i+6] == '5' || data[i+6] == '6')
                    && data[i+7] == 0) {
                // 从 dex 头第 32~35 字节读取文件长度（小端 uint32）
                int len = 0;
                if (i + 36 <= data.length) {
                    len = (data[i+32] & 0xFF)
                            | ((data[i+33] & 0xFF) << 8)
                            | ((data[i+34] & 0xFF) << 16)
                            | ((data[i+35] & 0xFF) << 24);
                }
                // 合理性检查：dex 长度应在 100 ~ 100MB 之间
                if (len < 100 || len > 100 * 1024 * 1024) len = 0;
                results.add(new int[]{i, len});
                // 跳过已找到的 dex，避免重复
                if (len > 0) i += Math.min(len, data.length - i - 1);
            }
        }
        return results;
    }

    /**
     * 检测设备是否有 root 权限。
     */
    public static boolean hasRoot() {
        try {
            Process p = Runtime.getRuntime().exec("su -c id");
            String out = new String(readAllBytes(p.getInputStream()));
            p.waitFor();
            return out.contains("uid=0");
        } catch (Exception e) {
            return false;
        }
    }
}
