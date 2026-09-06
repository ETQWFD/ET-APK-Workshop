package com.et.apkworkshop.engine;

/**
 * C++ 原生脱壳引擎 JNI 接口。
 * 原生库 libunpacker.so 提供高速 dex 魔数扫描与字节提取。
 * 若原生库加载失败（如不支持的 ABI），自动回退到 Java 实现。
 */
public final class NativeUnpacker {

    private static volatile Boolean available = null;

    static {
        try {
            System.loadLibrary("unpacker");
            available = true;
        } catch (UnsatisfiedLinkError e) {
            available = false;
        }
    }

    private NativeUnpacker() {}

    public static boolean isAvailable() {
        return available != null && available;
    }

    /**
     * 扫描字节流中的 dex 魔数。
     * @param data 原始字节
     * @param offset 起始偏移
     * @param length 扫描长度
     * @return 交错数组 [offset1, length1, offset2, length2, ...]，长度为 0 表示未找到
     */
    public static native int[] scanDexMagic(byte[] data, int offset, int length);

    /** 从字节流中提取指定区间 */
    public static native byte[] extractBytes(byte[] data, int offset, int length);

    /** 原生库版本信息 */
    public static native String getVersion();
}
