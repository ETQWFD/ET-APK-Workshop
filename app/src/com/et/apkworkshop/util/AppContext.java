package com.et.apkworkshop.util;

import android.content.Context;

/** 全局 Application Context 持有者，供工具类使用。 */
public final class AppContext {
    private static volatile Context ctx;
    private AppContext() {}
    public static void init(Context c) { if (c != null) ctx = c.getApplicationContext(); }
    public static Context get() { return ctx; }
}
