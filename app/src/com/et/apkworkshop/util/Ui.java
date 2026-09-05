package com.et.apkworkshop.util;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * UI 常量与构造辅助（纯程序化布局，暗黑风）。
 */
public final class Ui {

    public static final int BG       = Color.rgb(11, 15, 20);
    public static final int CARD     = Color.rgb(21, 27, 35);
    public static final int CARD2    = Color.rgb(29, 37, 47);
    public static final int PRIMARY  = Color.rgb(34, 211, 238);   // 青
    public static final int ACCENT   = Color.rgb(74, 222, 128);   // 绿
    public static final int TEXT     = Color.rgb(230, 237, 243);
    public static final int TEXT_DIM = Color.rgb(139, 148, 158);
    public static final int DANGER   = Color.rgb(248, 113, 113);
    public static final int BORDER   = Color.rgb(48, 58, 70);
    /** 半透明暗色（用于覆盖在二次元背景上保证文字可读） */
    public static final int BG_OVERLAY = Color.argb(210, 11, 15, 20);
    public static final int CARD_OVERLAY = Color.argb(230, 21, 27, 35);

    private static final int[] BG_RES_IDS = {
            com.et.apkworkshop.R.drawable.bg_1,
            com.et.apkworkshop.R.drawable.bg_2,
            com.et.apkworkshop.R.drawable.bg_3,
    };

    private Ui() {}

    /** 随机返回一张二次元美少女背景图资源 ID */
    public static int randomBgResId() {
        return BG_RES_IDS[(int) (Math.random() * BG_RES_IDS.length)];
    }

    /** 给 Activity 设置随机二次元背景 + 暗色遮罩 */
    public static void applyAnimeBg(android.app.Activity act) {
        act.getWindow().setBackgroundDrawableResource(randomBgResId());
    }

    public static int dp(Context c, float v) {
        return (int) (v * c.getResources().getDisplayMetrics().density + 0.5f);
    }

    public static void toast(Context c, String s) {
        Toast.makeText(c, s, Toast.LENGTH_LONG).show();
    }

    public static GradientDrawable rounded(int color, float radiusDp, Context c) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(c, radiusDp));
        return g;
    }

    public static GradientDrawable roundedStroke(int fill, int stroke, float radiusDp, Context c) {
        GradientDrawable g = rounded(fill, radiusDp, c);
        g.setStroke(dp(c, 1), stroke);
        return g;
    }

    public static Button primaryButton(Context c, String text) {
        Button b = new Button(c);
        b.setText(text);
        b.setTextColor(Color.rgb(2, 20, 30));
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextSize(16);
        b.setAllCaps(false);
        b.setBackground(rounded(PRIMARY, 14, c));
        return b;
    }

    public static Button ghostButton(Context c, String text, int textColor) {
        Button b = new Button(c);
        b.setText(text);
        b.setTextColor(textColor);
        b.setTextSize(14);
        b.setAllCaps(false);
        b.setBackground(roundedStroke(CARD2, BORDER, 12, c));
        return b;
    }

    public static TextView label(Context c, String text, int color, float sp, boolean bold) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextColor(color);
        t.setTextSize(sp);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    public static EditText input(Context c, String hint) {
        EditText e = new EditText(c);
        e.setHint(hint);
        e.setHintTextColor(TEXT_DIM);
        e.setTextColor(TEXT);
        e.setTextSize(15);
        e.setSingleLine(true);
        e.setBackground(roundedStroke(CARD2, BORDER, 10, c));
        int pad = dp(c, 12);
        e.setPadding(pad, pad, pad, pad);
        return e;
    }

    public static LinearLayout vertical(Context c) {
        LinearLayout ll = new LinearLayout(c);
        ll.setOrientation(LinearLayout.VERTICAL);
        return ll;
    }

    public static LinearLayout horizontal(Context c) {
        LinearLayout ll = new LinearLayout(c);
        ll.setOrientation(LinearLayout.HORIZONTAL);
        ll.setGravity(Gravity.CENTER_VERTICAL);
        return ll;
    }

    public static AlertDialog confirm(Context c, String title, String msg, String okText, Runnable onOk) {
        return new AlertDialog.Builder(c)
                .setTitle(title)
                .setMessage(msg)
                .setPositiveButton(okText, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) { if (onOk != null) onOk.run(); }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    public static void alert(Context c, String title, String msg) {
        new AlertDialog.Builder(c).setTitle(title).setMessage(msg).setPositiveButton("好的", null).show();
    }
}
