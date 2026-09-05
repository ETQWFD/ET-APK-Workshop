package com.et.apkworkshop;

/**
 * 前台服务工作状态单例：Activity 可轮询此对象获取反编译/编译进度。
 */
public final class WorkState {
    public static final int STATUS_IDLE = 0;
    public static final int STATUS_RUNNING = 1;
    public static final int STATUS_DONE = 2;
    public static final int STATUS_ERROR = 3;

    public volatile int status = STATUS_IDLE;
    public volatile String workType = "";      // "decompile" / "compile" / "unpack"
    public volatile String message = "";        // 当前步骤描述
    public volatile int progress = 0;           // 0~100
    public volatile String resultPath = null;   // 成功后的产物路径
    public volatile String error = null;        // 失败原因
    public volatile String projectDir = null;   // 关联的工程目录

    private static final WorkState INSTANCE = new WorkState();
    public static WorkState get() { return INSTANCE; }

    public void reset() {
        status = STATUS_IDLE;
        workType = "";
        message = "";
        progress = 0;
        resultPath = null;
        error = null;
        projectDir = null;
    }

    public boolean isRunning() { return status == STATUS_RUNNING; }
    public boolean isDone() { return status == STATUS_DONE; }
    public boolean isError() { return status == STATUS_ERROR; }
}
