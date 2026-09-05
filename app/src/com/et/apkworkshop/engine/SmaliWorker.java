package com.et.apkworkshop.engine;

import java.io.File;
import java.io.IOException;

/**
 * smali / baksmali 封装。
 * 依赖 com.android.tools.smali:* 3.0.10 系列库（内嵌于 APK）。
 */
public final class SmaliWorker {

    private SmaliWorker() {}

    /** 计算并行线程数：核心数，上限 8，至少 1。 */
    private static int threads() {
        try {
            int n = Runtime.getRuntime().availableProcessors();
            if (n < 1) return 1;
            return Math.min(n, 8);
        } catch (Throwable t) {
            return 1;
        }
    }

    /**
     * 反编译单个 dex 文件到 smali 目录。
     * @param dexFile  输入的 classes.dex
     * @param outDir   输出 smali 目录（baksmali 会在其中创建包结构）
     * @param apiLevel ART/DEX api 级别，用于 opcode 映射
     */
    public static void disassemble(File dexFile, File outDir, int apiLevel, ApkEngine.Progress prog) throws IOException {
        if (prog != null) prog.on("反编译 " + dexFile.getName() + " ...");
        com.android.tools.smali.dexlib2.Opcodes opcodes = com.android.tools.smali.dexlib2.Opcodes.forApi(apiLevel);
        com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile dex =
                com.android.tools.smali.dexlib2.DexFileFactory.loadDexFile(dexFile, opcodes);
        com.android.tools.smali.baksmali.BaksmaliOptions options = new com.android.tools.smali.baksmali.BaksmaliOptions();
        options.apiLevel = apiLevel;
        options.registerInfo = 0;
        options.codeOffsets = false;
        options.debugInfo = true;
        options.accessorComments = true;
        options.allowOdex = false;
        options.deodex = false;
        options.implicitReferences = false;
        options.normalizeVirtualMethods = false;
        if (!outDir.exists() && !outDir.mkdirs()) {
            throw new IOException("无法创建 smali 目录: " + outDir);
        }
        boolean ok = com.android.tools.smali.baksmali.Baksmali.disassembleDexFile(dex, outDir, threads(), options);
        if (!ok) {
            throw new IOException("baksmali 反编译失败: " + dexFile.getName());
        }
        if (prog != null) prog.on(dexFile.getName() + " 反编译完成");
    }

    /**
     * 将 smali 目录汇编为 dex 文件。
     * @param smaliDir  smali 源码目录（包含 com/xxx 等包结构）
     * @param outDex    输出的 classes.dex
     * @param apiLevel  api 级别
     */
    public static void assemble(File smaliDir, File outDex, int apiLevel, ApkEngine.Progress prog) throws IOException {
        if (prog != null) prog.on("汇编 " + smaliDir.getName() + " ...");
        com.android.tools.smali.smali.SmaliOptions options = new com.android.tools.smali.smali.SmaliOptions();
        options.apiLevel = apiLevel;
        options.outputDexFile = outDex.getAbsolutePath();
        options.jobs = threads();
        options.verboseErrors = true;
        options.allowOdexOpcodes = false;
        boolean ok;
        try {
            ok = com.android.tools.smali.smali.Smali.assemble(options, smaliDir.getAbsolutePath());
        } catch (Exception e) {
            throw new IOException("smali 汇编异常: " + e.getMessage(), e);
        }
        if (!ok) {
            throw new IOException("smali 汇编失败: " + smaliDir.getName()
                    + "（请检查是否有语法错误，可让 AI 助手协助修复）");
        }
        if (prog != null) prog.on(smaliDir.getName() + " 汇编完成");
    }
}
