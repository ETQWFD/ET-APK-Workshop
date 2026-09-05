package com.et.apkworkshop;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.ClipboardManager;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.et.apkworkshop.engine.AiClient;
import com.et.apkworkshop.util.AppSettings;
import com.et.apkworkshop.util.Ui;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class AiChatActivity extends Activity {
    private File contextFile;
    private String projectDir;
    private List<AiClient.Msg> history = new ArrayList<AiClient.Msg>();
    private ChatAdapter adapter;
    private ListView chatList;
    private EditText inputBox;
    private TextView ctxBar;
    private ProgressDialog sending;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.applyAnimeBg(this);
        projectDir = getIntent().getStringExtra("project_dir");
        String fp = getIntent().getStringExtra("file_path");
        if (fp != null) contextFile = new File(fp);

        LinearLayout root = Ui.vertical(this);
        root.setBackgroundColor(Ui.BG_OVERLAY);
        root.setPadding(Ui.dp(this, 10), Ui.dp(this, 6), Ui.dp(this, 10), Ui.dp(this, 8));

        LinearLayout header = Ui.horizontal(this);
        TextView back = Ui.label(this, "‹", Ui.PRIMARY, 26, true);
        back.setPadding(Ui.dp(this, 6), 0, Ui.dp(this, 10), 0);
        back.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        header.addView(back);
        TextView t = Ui.label(this, "AI 助手", Ui.PRIMARY, 18, true);
        header.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView cfg = Ui.label(this, "配置AI", Ui.TEXT_DIM, 13, true);
        cfg.setPadding(Ui.dp(this, 8), Ui.dp(this, 4), Ui.dp(this, 2), Ui.dp(this, 4));
        cfg.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { startActivity(new Intent(AiChatActivity.this, SettingsActivity.class)); }
        });
        header.addView(cfg);
        root.addView(header);

        ctxBar = Ui.label(this, "", Ui.TEXT_DIM, 12, false);
        ctxBar.setPadding(Ui.dp(this, 6), Ui.dp(this, 6), Ui.dp(this, 6), Ui.dp(this, 6));
        ctxBar.setBackground(Ui.roundedStroke(Ui.CARD_OVERLAY, Ui.BORDER, 10, this));
        if (contextFile != null) {
            ctxBar.setText("已关联: " + contextFile.getName() + " (" + formatSize(contextFile.length()) + ")  点击发送给AI");
        } else {
            ctxBar.setText("未关联文件。可在工程页长按 .smali → 用AI分析。");
        }
        ctxBar.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { sendContextFile(); }
        });
        root.addView(ctxBar, lp(0, 4, 0, 6));

        chatList = new ListView(this);
        chatList.setDivider(null);
        chatList.setCacheColorHint(Color.TRANSPARENT);
        chatList.setBackgroundColor(Color.TRANSPARENT);
        adapter = new ChatAdapter();
        chatList.setAdapter(adapter);
        root.addView(chatList, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        LinearLayout inputRow = Ui.horizontal(this);
        inputBox = new EditText(this);
        inputBox.setHint("告诉 AI 你想怎么改…");
        inputBox.setHintTextColor(Ui.TEXT_DIM);
        inputBox.setTextColor(Ui.TEXT);
        inputBox.setTextSize(14);
        inputBox.setMaxLines(4);
        inputBox.setBackground(Ui.roundedStroke(Ui.CARD_OVERLAY, Ui.BORDER, 10, this));
        int pad = Ui.dp(this, 10);
        inputBox.setPadding(pad, pad, pad, pad);
        inputRow.addView(inputBox, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView sendBtn = Ui.label(this, "发送", Ui.PRIMARY, 15, true);
        sendBtn.setGravity(Gravity.CENTER);
        sendBtn.setPadding(Ui.dp(this, 14), Ui.dp(this, 10), Ui.dp(this, 14), Ui.dp(this, 10));
        sendBtn.setBackground(Ui.rounded(Ui.CARD_OVERLAY, 10, this));
        sendBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { sendMessage(); }
        });
        inputRow.addView(sendBtn, lp(4, 0, 0, 0));
        root.addView(inputRow, lp(0, 6, 0, 0));

        setContentView(root);
        addAiMsg("你好，我是 ET APK 的 AI 助手。\n\n"
                + "· 点击上方文件栏把当前 smali 发给我分析\n"
                + "· 直接告诉我想改的功能，我给出 smali 修改方案\n"
                + "· 我的修改代码可用「应用修改」一键写回文件\n\n"
                + "⚠ 请先在设置中配置 AI 接口。国内访问 OpenAI 官方地址需代理，推荐用兼容中转或本地服务。");
    }

    private LinearLayout.LayoutParams lp(int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(Ui.dp(this, l), Ui.dp(this, t), Ui.dp(this, r), Ui.dp(this, b));
        return p;
    }

    private void addUserMsg(String s) { history.add(new AiClient.Msg("user", s)); adapter.notifyDataSetChanged(); scrollBottom(); }
    private void addAiMsg(String s) { history.add(new AiClient.Msg("assistant", s)); adapter.notifyDataSetChanged(); scrollBottom(); }
    private void scrollBottom() { chatList.post(new Runnable() { @Override public void run() { chatList.setSelection(chatList.getCount() - 1); } }); }

    private void sendContextFile() {
        if (contextFile == null) { Ui.toast(this, "当前未关联文件"); return; }
        if (!contextFile.exists()) { Ui.toast(this, "文件不存在: " + contextFile.getAbsolutePath()); return; }
        if (contextFile.length() == 0) { Ui.toast(this, "文件为空（0 字节）"); return; }
        String content = readText(contextFile, 80000);
        if (content == null) { Ui.toast(this, "文件读取失败"); return; }
        if (content.isEmpty()) { Ui.toast(this, "文件内容为空"); return; }
        String msg = "请分析这个 smali 文件的作用，并给出修改建议。\n"
                + "文件名：" + contextFile.getName() + "\n"
                + "文件大小：" + formatSize(contextFile.length()) + "\n\n"
                + "文件内容：\n```smali\n" + content + "\n```\n\n"
                + "如果需要修改，请直接输出修改后的完整文件内容，放在 <<<ET_CODE>>> 和 <</ET_CODE>>> 之间。";
        addUserMsg(msg);
        Ui.toast(this, "已发送 " + formatSize(contextFile.length()) + " 给 AI");
        sendToAi();
    }

    private void sendMessage() {
        String text = inputBox.getText().toString().trim();
        if (text.isEmpty()) return;
        inputBox.setText("");
        addUserMsg(text);
        sendToAi();
    }

    private void sendToAi() {
        final AppSettings s = new AppSettings(this);
        if (!s.isAiConfigured()) {
            Ui.alert(this, "未配置 AI",
                    "请先在「设置」中配置 AI 的 API 地址、API Key 与模型名。\n\n"
                    + "API 地址示例：\n"
                    + "· OpenAI: https://api.openai.com/v1\n"
                    + "· 国内中转: https://你的中转地址/v1\n"
                    + "· 本地服务: http://127.0.0.1:8000/v1");
            return;
        }
        final List<AiClient.Msg> req = new ArrayList<AiClient.Msg>();
        req.add(new AiClient.Msg("system", systemPrompt()));
        req.addAll(history);

        sending = new ProgressDialog(this);
        sending.setMessage("AI 思考中 …（" + s.getAiModel() + "）");
        sending.setCancelable(true);
        sending.show();

        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    final String reply = AiClient.chat(s.getAiBaseUrl(), s.getAiApiKey(), s.getAiModel(),
                            s.getAiTemperature(), req, s.getAiMaxTokens());
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            if (sending != null && sending.isShowing()) sending.dismiss();
                            if (reply == null || reply.trim().isEmpty()) {
                                addAiMsg("⚠ AI 返回了空回复。\n\n可能原因：\n"
                                        + "1. 模型不支持该接口格式\n"
                                        + "2. API 地址不正确（需以 /v1 结尾）\n"
                                        + "3. 网络连接中断\n\n"
                                        + "请检查设置中的 API 配置。");
                            } else {
                                addAiMsg(reply);
                            }
                        }
                    });
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            if (sending != null && sending.isShowing()) sending.dismiss();
                            addAiMsg("⚠ 调用失败: " + e.getMessage()
                                    + "\n\n请检查：\n"
                                    + "· API 地址是否正确（以 /v1 结尾）\n"
                                    + "· API Key 是否有效\n"
                                    + "· 模型名是否正确\n"
                                    + "· 网络是否可达（国内访问 api.openai.com 需代理）\n"
                                    + "· 点击右上角「配置AI」修改设置");
                        }
                    });
                }
            }
        }).start();
    }

    private String systemPrompt() {
        return "你是「ET APK 工坊」的内置 AI 助手，精通 Android 逆向工程、smali 汇编、APK 反编译与重打包。\n\n"
                + "【守则】\n"
                + "1. 仅协助合法用途（学习、安全分析、自有应用改装），不协助破解盗版/绕过授权/窃取数据。\n"
                + "2. 修改代码时给出解释 + 可直接使用的 smali。\n"
                + "3. 提供完整修改后文件时，放在 <<<ET_CODE>>> 和 <</ET_CODE>>> 之间。\n"
                + "4. 分析 smali 时说明关键方法、寄存器、调用关系。\n"
                + "5. 用中文回答，简洁可操作。";
    }

    private String readText(File f, int maxChars) {
        try {
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            java.io.FileInputStream in = new java.io.FileInputStream(f);
            try { byte[] buf = new byte[65536]; int n; while ((n = in.read(buf)) > 0) bos.write(buf, 0, n); }
            finally { in.close(); }
            String str = new String(bos.toByteArray(), "UTF-8");
            if (str.length() > maxChars) str = str.substring(0, maxChars) + "\n...(已截断，共" + bos.size() + "字节)";
            return str;
        } catch (Exception e) { return null; }
    }

    static String extractCode(String reply) {
        if (reply == null) return null;
        String start = "<<<ET_CODE>>>", end = "<</ET_CODE>>>";
        int si = reply.lastIndexOf(start), ei = reply.lastIndexOf(end);
        if (si >= 0 && ei > si) return reply.substring(si + start.length(), ei).trim();
        int f1 = reply.lastIndexOf("```");
        if (f1 >= 0) {
            int f2 = reply.lastIndexOf("```", f1 - 1);
            if (f2 >= 0) {
                String block = reply.substring(f2 + 3, f1);
                int nl = block.indexOf('\n');
                if (nl >= 0) block = block.substring(nl + 1);
                return block.trim();
            }
        }
        return null;
    }

    private void applyCode(String code) {
        if (code == null || code.trim().isEmpty()) { Ui.toast(this, "AI 回复中没有可应用的代码块"); return; }
        if (contextFile != null) {
            try {
                java.io.FileOutputStream out = new java.io.FileOutputStream(contextFile);
                try { out.write(code.getBytes("UTF-8")); } finally { out.close(); }
                Ui.toast(this, "已应用修改到 " + contextFile.getName());
            } catch (Exception e) { Ui.toast(this, "写入失败: " + e.getMessage()); }
        } else {
            final EditText et = Ui.input(this, "相对路径，如 smali/com/x/Y.smali");
            new android.app.AlertDialog.Builder(this)
                    .setTitle("保存 AI 修改的代码").setMessage("未关联文件，输入保存路径：")
                    .setView(et)
                    .setPositiveButton("保存", new DialogInterface.OnClickListener() {
                        @Override public void onClick(DialogInterface d, int w) {
                            String rel = et.getText().toString().trim();
                            if (rel.isEmpty()) return;
                            File target = new File(projectDir, rel);
                            try {
                                if (!target.getParentFile().exists()) target.getParentFile().mkdirs();
                                java.io.FileOutputStream out = new java.io.FileOutputStream(target);
                                try { out.write(code.getBytes("UTF-8")); } finally { out.close(); }
                                Ui.toast(AiChatActivity.this, "已保存到 " + rel);
                            } catch (Exception e) { Ui.toast(AiChatActivity.this, "保存失败: " + e.getMessage()); }
                        }
                    })
                    .setNegativeButton("取消", null).show();
        }
    }

    @SuppressWarnings("deprecation")
    private void copyText(String s) { ((ClipboardManager) getSystemService(CLIPBOARD_SERVICE)).setText(s); Ui.toast(this, "已复制"); }

    private static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
    }

    private class ChatAdapter extends BaseAdapter {
        @Override public int getCount() { return history.size(); }
        @Override public AiClient.Msg getItem(int p) { return history.get(p); }
        @Override public long getItemId(int p) { return p; }
        @Override public View getView(final int p, View convertView, ViewGroup parent) {
            final AiClient.Msg m = getItem(p);
            LinearLayout wrap = convertView instanceof LinearLayout ? (LinearLayout) convertView : Ui.vertical(AiChatActivity.this);
            if (convertView == null) wrap.setPadding(0, Ui.dp(AiChatActivity.this, 2), 0, Ui.dp(AiChatActivity.this, 6));
            wrap.removeAllViews();
            LinearLayout bubble = Ui.vertical(AiChatActivity.this);
            bubble.setPadding(Ui.dp(AiChatActivity.this, 12), Ui.dp(AiChatActivity.this, 10),
                    Ui.dp(AiChatActivity.this, 12), Ui.dp(AiChatActivity.this, 10));
            boolean user = "user".equals(m.role);
            bubble.setBackground(user
                    ? Ui.roundedStroke(Ui.CARD_OVERLAY, Ui.PRIMARY, 12, AiChatActivity.this)
                    : Ui.roundedStroke(Ui.CARD_OVERLAY, Ui.BORDER, 12, AiChatActivity.this));
            String display = m.content;
            if (display.length() > 800) display = display.substring(0, 800) + "\n…(已截断，点复制查看全部)";
            TextView content = Ui.label(AiChatActivity.this, display, Ui.TEXT, 14, false);
            content.setTextIsSelectable(true);
            bubble.addView(content);
            if (!user) {
                LinearLayout ops = Ui.horizontal(AiChatActivity.this);
                TextView apply = op("应用修改");
                apply.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { applyCode(extractCode(m.content)); } });
                TextView copy = op("复制");
                copy.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { copyText(m.content); } });
                ops.addView(apply); ops.addView(copy);
                LinearLayout.LayoutParams olp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                olp.topMargin = Ui.dp(AiChatActivity.this, 6);
                bubble.addView(ops, olp);
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    (int) (getResources().getDisplayMetrics().widthPixels * 0.92f), ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.gravity = user ? Gravity.RIGHT : Gravity.LEFT;
            wrap.addView(bubble, lp);
            return wrap;
        }
        private TextView op(String s) {
            TextView tv = Ui.label(AiChatActivity.this, s, Ui.ACCENT, 12, true);
            tv.setPadding(Ui.dp(AiChatActivity.this, 4), Ui.dp(AiChatActivity.this, 2),
                    Ui.dp(AiChatActivity.this, 10), Ui.dp(AiChatActivity.this, 2));
            return tv;
        }
    }
}
