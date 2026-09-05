package com.et.apkworkshop;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.et.apkworkshop.util.Ui;

import java.io.File;

/**
 * 简易代码编辑器：查看/编辑 smali 源码，可保存，可一键发送给 AI 协助修改。
 */
public class EditorActivity extends Activity {

    private File file;
    private boolean editable;
    private EditText editor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.applyAnimeBg(this);
        file = new File(getIntent().getStringExtra("file_path"));
        editable = getIntent().getBooleanExtra("editable", true);
        final String projectDir = getIntent().getStringExtra("project_dir");

        LinearLayout root = Ui.vertical(this);
        root.setBackgroundColor(Ui.BG_OVERLAY);
        root.setPadding(Ui.dp(this, 8), Ui.dp(this, 6), Ui.dp(this, 8), Ui.dp(this, 6));

        // 顶栏
        LinearLayout header = Ui.horizontal(this);
        TextView back = Ui.label(this, "‹", Ui.PRIMARY, 26, true);
        back.setPadding(Ui.dp(this, 6), 0, Ui.dp(this, 8), 0);
        back.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        header.addView(back);
        TextView name = Ui.label(this, file.getName(), Ui.TEXT, 15, true);
        header.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        root.addView(header);

        // 按钮行
        LinearLayout ops = Ui.horizontal(this);
        TextView save = chip("保存");
        save.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { saveFile(); }
        });
        TextView ai = chip("发送给 AI");
        ai.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                saveFile();
                Intent i = new Intent(EditorActivity.this, AiChatActivity.class);
                i.putExtra("project_dir", projectDir);
                i.putExtra("file_path", file.getAbsolutePath());
                startActivity(i);
            }
        });
        ops.addView(save, chipLp(1));
        ops.addView(ai, chipLp(1));
        root.addView(ops, lp(0, 4, 0, 4));

        // 编辑器
        editor = new EditText(this);
        editor.setBackgroundColor(Ui.CARD);
        editor.setTextColor(Ui.TEXT);
        editor.setHintTextColor(Ui.TEXT_DIM);
        editor.setTextSize(13);
        editor.setTypeface(Typeface.MONOSPACE);
        editor.setHorizontallyScrolling(false);
        editor.setPadding(Ui.dp(this, 10), Ui.dp(this, 10), Ui.dp(this, 10), Ui.dp(this, 10));
        editor.setEnabled(editable);
        root.addView(editor, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        TextView hint = Ui.label(this, editable ? "编辑后点保存，再到 AI 助手让 AI 帮你改" : "只读文件", Ui.TEXT_DIM, 11, false);
        root.addView(hint, lp(2, 4, 0, 0));

        setContentView(root);
        loadFile();
    }

    private TextView chip(String s) {
        TextView tv = new TextView(this);
        tv.setText(s);
        tv.setTextColor(Ui.PRIMARY);
        tv.setTextSize(13);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(Ui.dp(this, 6), Ui.dp(this, 8), Ui.dp(this, 6), Ui.dp(this, 8));
        tv.setBackground(Ui.roundedStroke(Ui.CARD2, Ui.BORDER, 10, this));
        return tv;
    }

    private LinearLayout.LayoutParams chipLp(float weight) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight);
        int m = Ui.dp(this, 3);
        lp.setMargins(m, 0, m, 0);
        return lp;
    }

    private LinearLayout.LayoutParams lp(int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(Ui.dp(this, l), Ui.dp(this, t), Ui.dp(this, r), Ui.dp(this, b));
        return p;
    }

    private void loadFile() {
        try {
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            try (java.io.FileInputStream in = new java.io.FileInputStream(file)) {
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            }
            editor.setText(new String(bos.toByteArray(), "UTF-8"));
        } catch (Exception e) {
            editor.setText("无法读取文件: " + e.getMessage());
            editor.setEnabled(false);
        }
    }

    private void saveFile() {
        if (!editable) {
            Ui.toast(this, "只读文件不可保存");
            return;
        }
        try {
            java.io.FileOutputStream out = new java.io.FileOutputStream(file);
            try {
                out.write(editor.getText().toString().getBytes("UTF-8"));
            } finally {
                out.close();
            }
            Ui.toast(this, "已保存");
        } catch (Exception e) {
            Ui.toast(this, "保存失败: " + e.getMessage());
        }
    }
}
