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
        {"360加固", "libjiagu.so", "libjiagu_art.so", "libjiagu_x86.so"},
        {"腾讯乐固", "libshell-super.2019.so", "libBugly.so", "libshella.so"},
        {"爱加密", "libexec.so", "libexecmain.so", "libexecservice.so"},
        {"梆梆加固", "libsecexe.so", "libsecmain.so", "libSecShell.so"},
        {"百度加固", "libbaiduprotect.so", "libbaiduprotect_x86.so"},
        {"阿里聚安全", "libsgmain.so", "libsgsecuritybody.so", "libsgmiddletier.so"},
        {"网易易盾", "libnesec.so", "libgdtdata.so"},
        {"顶象", "libdxs.so", "libdexload.so"},
        {"娜迦", "libchaosvmp.so", "libddog.so"},
        {"几维安全", "libkwscmm.so", "libkwscrash.so"},
    };

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

                // 有些壳把 dex 藏在 assets 下用 .dat/.jar 等扩展名
                if (name.startsWith("assets/") && (name.endsWith(".jar") || name.endsWith(".dat")
                        || name.endsWith(".bin") || name.endsWith(".so"))) {
                    // 检查文件头是否是 dex（dex\n035）
                    byte[] header = new byte[8];
                    InputStream is = zip.getInputStream(e);
                    int read = is.read(header);
                    is.close();
                    if (read >= 4 && header[0] == 'd' && header[1] == 'e' && header[2] == 'x' && header[3] == '\n') {
                        String outName = "hidden_" + name.replace('/', '_');
                        File out = new File(unpackedDir, outName + ".dex");
                        extractEntry(zip, e, out);
                        result.dexFiles.add(out.getAbsolutePath());
                        result.dexCount++;
                        prog.on("发现隐藏 dex: " + name);
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
        FileOutputStream fos = new FileOutputStream(report);
        try { fos.write(sb.toString().getBytes("UTF-8")); } finally { fos.close(); }

        prog.on("脱壳完成: 提取 " + result.dexCount + " 个 dex, 检测到 " + result.detectedPacker);
        return result;
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
}
