package com.et.apkworkshop.engine;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 反编译 / 编译打包主引擎。纯 Java，不依赖 Android 环境，可在桌面 JVM 上验证。
 *
 * 反编译：把 APK 解压 + 用 baksmali 把每个 classes*.dex 转成 smali 工程。
 * 编译：  用 smali 把每个 smali 目录汇编回 classes*.dex，重建 APK（保留原资源），再 v1/v2 签名。
 */
public final class ApkEngine {

    public interface Progress {
        void on(String message);
    }

    private static final Progress NOOP = new Progress() {
        @Override public void on(String message) {}
    };

    private ApkEngine() {}

    public static int defaultApiLevel() {
        return 34;
    }

    /**
     * 反编译 APK 到工程目录。
     * @return ProjectInfo
     */
    public static ProjectInfo decompile(File apkFile, File projectDir, Progress prog) throws Exception {
        if (prog == null) prog = NOOP;
        if (!apkFile.exists()) throw new Exception("APK 文件不存在: " + apkFile);

        if (projectDir.exists()) {
            deleteRecursive(projectDir);
        }
        if (!projectDir.mkdirs()) throw new Exception("无法创建工程目录: " + projectDir);

        String apkName = apkFile.getName();
        if (apkName.endsWith(".apk")) apkName = apkName.substring(0, apkName.length() - 4);

        ProjectInfo info = new ProjectInfo();
        info.projectDir = projectDir;
        info.name = apkName;
        info.created = System.currentTimeMillis();
        info.apiLevel = defaultApiLevel();
        info.dexNames = new ArrayList<String>();
        info.originalApkPath = apkFile.getAbsolutePath();

        prog.on("解压 APK 资源 ...");
        ZipUtil.unzip(apkFile, info.apkSrcDir());

        // 找到所有 classes*.dex
        List<String> dexList = new ArrayList<String>();
        File[] files = info.apkSrcDir().listFiles();
        if (files != null) {
            for (File f : files) {
                String n = f.getName();
                if (ZipUtil.isDex(n)) dexList.add(n);
            }
        }
        java.util.Collections.sort(dexList);
        if (dexList.isEmpty()) {
            throw new Exception("该 APK 中未找到 classes.dex（可能不是有效的可反编译 APK）");
        }
        info.dexNames = dexList;

        // 逐个反编译
        for (int i = 0; i < dexList.size(); i++) {
            File dexFile = new File(info.apkSrcDir(), dexList.get(i));
            File smaliDir = info.smaliDir(i);
            SmaliWorker.disassemble(dexFile, smaliDir, info.apiLevel, prog);
        }

        info.save();
        prog.on("反编译完成，共 " + dexList.size() + " 个 dex");
        return info;
    }

    /**
     * 编译打包：smali -> dex -> 重建 APK -> 签名。
     * @return 签名后的 APK 文件
     */
    public static File compile(ProjectInfo info, Progress prog) throws Exception {
        if (prog == null) prog = NOOP;
        if (!info.outputDir().exists()) info.outputDir().mkdirs();

        File origApk = info.originalApk();
        if (!origApk.exists()) {
            throw new Exception("原始 APK 已丢失（路径: " + info.originalApkPath
                    + "），请重新选择该 APK 进行反编译后再编译");
        }

        // 1. 汇编每个 smali 目录
        Map<String, byte[]> dexMap = new LinkedHashMap<String, byte[]>();
        for (int i = 0; i < info.dexNames.size(); i++) {
            File smaliDir = info.smaliDir(i);
            if (!smaliDir.exists() || !smaliDir.isDirectory()) {
                throw new Exception("缺少 smali 目录: " + smaliDir);
            }
            File tmpDex = new File(info.outputDir(), "_tmp_" + i + ".dex");
            SmaliWorker.assemble(smaliDir, tmpDex, info.apiLevel, prog);
            byte[] data = readBytes(tmpDex);
            tmpDex.delete();
            dexMap.put(info.dexNames.get(i), data);
        }

        // 2. 重建 APK
        prog.on("重建 APK ...");
        File unsigned = new File(info.outputDir(), "unsigned.apk");
        ZipUtil.buildApk(info.originalApk(), unsigned, dexMap);

        // 3. 签名
        prog.on("APK 签名 ...");
        String outName = info.name + "-signed.apk";
        File signed = new File(info.outputDir(), outName);
        if (signed.exists()) signed.delete();
        ApkSignerUtil.sign(unsigned, signed, new File(info.projectDir, "keystore.bin"));

        // 清理临时文件
        unsigned.delete();

        info.lastBuild = System.currentTimeMillis();
        info.lastOutput = signed.getAbsolutePath();
        info.save();

        prog.on("编译打包完成: " + signed.getName());
        return signed;
    }

    // ---------------- 工具 ----------------

    static byte[] readBytes(File f) throws Exception {
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        java.io.FileInputStream in = new java.io.FileInputStream(f);
        try {
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
        } finally {
            in.close();
        }
        return bos.toByteArray();
    }

    static void copyFile(File src, File dst) throws Exception {
        if (!dst.getParentFile().exists()) dst.getParentFile().mkdirs();
        java.io.FileInputStream in = new java.io.FileInputStream(src);
        try {
            java.io.FileOutputStream out = new java.io.FileOutputStream(dst);
            try {
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            } finally {
                out.close();
            }
        } finally {
            in.close();
        }
    }

    public static void deleteRecursive(File f) {
        if (f == null || !f.exists()) return;
        if (f.isDirectory()) {
            File[] children = f.listFiles();
            if (children != null) {
                for (File c : children) deleteRecursive(c);
            }
        }
        f.delete();
    }
}
