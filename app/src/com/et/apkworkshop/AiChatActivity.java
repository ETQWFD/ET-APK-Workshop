package com.et.apkworkshop;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
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
import java.util.Locale;

/**
 * AI 助手聊天：可把当前 smali 文件发给 AI，让其分析并给出修改后的完整源码，
 * 一键应用修改后回工程页编译打包。
 */
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
        projectDir = getIntent().getStringExtra("project_dir");
        String fp = getIntent().getStringExtra("file_path");
        if (fp != null) contextFile = new File(fp);

        LinearLayout root = Ui.vertical(this);
        root.setBackgroundColor(Ui.BG);
        root.setPadding(Ui.dp(this, 10), Ui.dp(this, 6), Ui.dp(this, 10), Ui.dp(this, 8));

        // 顶栏
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

        // 上下文栏
        ctxBar = Ui.label(this, "", Ui.TEXT_DIM, 12, false);
        ctxBar.setPadding(Ui.dp(this, 6), Ui.dp(this, 6), Ui.dp(this, 6), Ui.dp(this, 6));
        ctxBar.setBackground(Ui.roundedStroke(Ui.CARD, Ui.BORDER, 10, this));
        if (contextFile != null) {
            ctxBar.setText("已关联文件: " + contextFile.getName());
        } else {
            ctxBar.setText("未关联文件。可在工程页长按 .smali 文件选择“用 AI 分析/修改”来关联。");
        }
        ctxBar.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { sendContextFile(); }
        });
        root.addView(ctxBar, lp(0, 4, 0, 6));

        // 聊天列表
        chatList = new ListView(this);
        chatList.setDivider(null);
        chatList.setCacheColorHint(Color.TRANSPARENT);
        chatList.setBackgroundColor(Ui.BG);
        adapter = new ChatAdapter();
        chatList.setAdapter(adapter);
        root.addView(chatList, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        // 输入行
        LinearLayout inputRow = Ui.horizontal(this);
        inputBox = new EditText(this);
        inputBox.setHint("告诉 AI 你想怎么改这个软件…");
        inputBox.setHintTextColor(Ui.TEXT_DIM);
        inputBox.setTextColor(Ui.TEXT);
        inputBox.setTextSize(14);
        inputBox.setMaxLines(4);
        inputBox.setBackground(Ui.roundedStroke(Ui.CARD2, Ui.BORDER, 10, this));
        int pad = Ui.dp(this, 10);
        inputBox.setPadding(pad, pad, pad, pad);
        inputRow.addView(inputBox, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView sendBtn = Ui.label(this, "发送", Ui.PRIMARY, 15, true);
        sendBtn.setGravity(Gravity.CENTER);
        sendBtn.setPadding(Ui.dp(this, 14), Ui.dp(this, 10), Ui.dp(this, 14), Ui.dp(this, 10));
        sendBtn.setBackground(Ui.rounded(Ui.CARD2, 10, this));
        sendBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { sendMessage(); }
        });
        inputRow.addView(sendBtn, lp(4, 0, 0, 0));
        root.addView(inputRow, lp(0, 6, 0, 0));

        setContentView(root);

        // 欢迎语
        addAiMsg("你好，我是 ET APK 的 AI 助手，熟悉 Android 逆向与 smali 汇编。\n\n你可以：\n① 点击上方文件栏把当前 smali 文件发给我分析；\n② 直接告诉我你想改的功能（比如去广告、改文案、改逻辑），我会给出 smali 修改方案；\n③ 我返回的修改代码会放在代码块里，你可以一键应用到文件，然后回工程页“编译打包”。\n\n请先在设置中配置 AI 的 API 地址、密钥和模型。");
    }

    private LinearLayout.LayoutParams lp(int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(Ui.dp(this, l), Ui.dp(this, t), Ui.dp(this, r), Ui.dp(this, b));
        return p;
    }

    private void addUserMsg(String s) {
        history.add(new AiClient.Msg("user", s));
        adapter.notifyDataSetChanged();
        scrollBottom();
    }

    private void addAiMsg(String s) {
        history.add(new AiClient.Msg("assistant", s));
        adapter.notifyDataSetChanged();
        scrollBottom();
    }

    private void scrollBottom() {
        chatList.post(new Runnable() {
            @Override public void run() { chatList.setSelection(chatList.getCount() - 1); }
        });
    }

    private void sendContextFile() {
        if (contextFile == null) {
            Ui.toast(this, "当前未关联文件，请在工程页长按 .smali 文件进入");
            return;
        }
        String content = readText(contextFile, 80000);
        if (content == null) {
            Ui.toast(this, "文件读取失败");
            return;
        }
        String msg = "请分析这个文件，并告诉我它的作用：\n文件名：" + contextFile.getName()
                + "\n\n```\n" + content + "\n```\n"
                + "\n（如果需要修改，请直接输出修改后的完整文件内容，并放在 <<<ET_CODE>>> 与 <</ET_CODE>>> 标记之间）";
        addUserMsg(msg);
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
                    "请先在“设置”中配置 AI 的 API 地址、API Key 与模型名，再回来聊天。");
            return;
        }
        sending = new ProgressDialog(this);
        sending.setMessage("AI 思考中 …");
        sending.setCancelable(false);
        sending.show();
        final List<AiClient.Msg> req = new ArrayList<AiClient.Msg>();
        req.add(new AiClient.Msg("system", systemPrompt()));
        req.addAll(history);
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    final String reply = AiClient.chat(s.getAiBaseUrl(), s.getAiApiKey(), s.getAiModel(),
                            s.getAiTemperature(), req, s.getAiMaxTokens());
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            if (sending != null) sending.dismiss();
                            addAiMsg(reply);
                        }
                    });
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            if (sending != null) sending.dismiss();
                            addAiMsg("⚠ 调用失败: " + e.getMessage() + "\n\n请检查设置中的 API 地址/密钥/模型，或稍后重试。");
                        }
                    });
                }
            }
        }).start();
    }

    private String systemPrompt() {
        StringBuilder sb = new StringBuilder();
        sb.append("你是“ET APK 工坊”的内置 AI 助手，精通 Android 逆向工程、smali 汇编语言、");
        sb.append("APK 反编译与重打包，也熟悉 Java/Android 开发。");
        sb.append("\n\n【工作守则】");
        sb.append("\n1. 用户正在修改自己拥有或有权修改的应用。请你只协助合法的修改用途（如学习、安全分析、自有应用改装），");
        sb.append("不要协助绕过授权校验、破解付费/盗版、窃取他人数据等非法行为。");
        sb.append("\n2. 当用户请求修改代码时，给出清晰的解释 + 可直接使用的 smali 修改方案。");
        sb.append("\n3. 当需要提供修改后的完整文件时，把完整文件内容放在这两行之间：");
        sb.append("\n<<<ET_CODE>>>");
        sb.append("\n<这里放完整的修改后文件内容>");
        sb.append("\n<</ET_CODE>>>");
        sb.append("\n确保标记成对出现，且代码块内不要包含这两个标记本身。");
        sb.append("\n4. 分析 smali 时说明关键逻辑所在（方法名、寄存器、调用关系）。");
        sb.append("\n5. 回答使用中文，简洁、可操作。");
        return sb.toString();
    }

    private String readText(File f, int maxChars) {
        try {
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            try (java.io.FileInputStream in = new java.io.FileInputStream(f)) {
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            }
            String s = new String(bos.toByteArray(), "UTF-8");
            if (s.length() > maxChars) s = s.substring(0, maxChars) + "\n...(内容过长已截断)";
            return s;
        } catch (Exception e) {
            return null;
        }
    }

    /** 从 AI 回复中提取修改代码。 */
    static String extractCode(String reply) {
        if (reply == null) return null;
        String start = "<<<ET_CODE>>>";
        String end = "<</ET_CODE>>>";
        int si = reply.lastIndexOf(start);
        int ei = reply.lastIndexOf(end);
        if (si >= 0 && ei > si) {
            return reply.substring(si + start.length(), ei).trim();
        }
        // 回退：取最后一个代码块
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
        if (code == null || code.trim().isEmpty()) {
            Ui.toast(this, "AI 回复中没有可应用的代码块");
            return;
        }
        if (contextFile != null) {
            try {
                java.io.FileOutputStream out = new java.io.FileOutputStream(contextFile);
                try {
                    out.write(code.getBytes("UTF-8"));
                } finally {
                    out.close();
                }
                Ui.toast(this, "已应用修改到 " + contextFile.getName() + "，可返回工程页编译打包");
            } catch (Exception e) {
                Ui.toast(this, "写入失败: " + e.getMessage());
            }
        } else {
            // 无关联文件：询问保存路径
            final EditText et = Ui.input(this, "相对工程路径，如 smali/com/example/X.smali");
            new android.app.AlertDialog.Builder(this)
                    .setTitle("保存 AI 修改的代码")
                    .setMessage("未关联文件，请输入保存路径（相对工程根目录）：")
                    .setView(et)
                    .setPositiveButton("保存", new DialogInterface.OnClickListener() {
                        @Override public void onClick(DialogInterface d, int w) {
                            String rel = et.getText().toString().trim();
                            if (rel.isEmpty()) return;
                            File target = new File(projectDir, rel);
                            try {
                                if (!target.getParentFile().exists()) target.getParentFile().mkdirs();
                                java.io.FileOutputStream out = new java.io.FileOutputStream(target);
                                try {
                                    out.write(code.getBytes("UTF-8"));
                                } finally {
                                    out.close();
                                }
                                Ui.toast(AiChatActivity.this, "已保存到 " + rel);
                            } catch (Exception e) {
                                Ui.toast(AiChatActivity.this, "保存失败: " + e.getMessage());
                            }
                        }
                    })
                    .setNegativeButton("取消", null)
                    .show();
        }
    }

    @SuppressWarnings("deprecation")
    private void copyText(String s) {
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        cm.setText(s);
        Ui.toast(this, "已复制");
    }

    // ---------- 聊天列表适配器 ----------

    private class ChatAdapter extends BaseAdapter {
        @Override public int getCount() { return history.size(); }
        @Override public AiClient.Msg getItem(int p) { return history.get(p); }
        @Override public long getItemId(int p) { return p; }

        @Override public View getView(final int p, View convertView, ViewGroup parent) {
            final AiClient.Msg m = getItem(p);
            LinearLayout wrap = convertView instanceof LinearLayout ? (LinearLayout) convertView : Ui.vertical(AiChatActivity.this);
            if (convertView == null) {
                wrap.setPadding(0, Ui.dp(AiChatActivity.this, 2), 0, Ui.dp(AiChatActivity.this, 6));
            }
            wrap.removeAllViews();

            LinearLayout bubble = Ui.vertical(AiChatActivity.this);
            bubble.setPadding(Ui.dp(AiChatActivity.this, 12), Ui.dp(AiChatActivity.this, 10),
                    Ui.dp(AiChatActivity.this, 12), Ui.dp(AiChatActivity.this, 10));
            boolean user = "user".equals(m.role);
            bubble.setBackground(user
                    ? Ui.roundedStroke(Ui.CARD2, Ui.PRIMARY, 12, AiChatActivity.this)
                    : Ui.roundedStroke(Ui.CARD, Ui.BORDER, 12, AiChatActivity.this));

            String display = m.content;
            if (display.length() > 600) {
                display = display.substring(0, 600) + "\n…（内容较长，完整内容已发送给 AI，点击“复制”可查看全部）";
            }
            TextView content = Ui.label(AiChatActivity.this, display, Ui.TEXT, 14, false);
            content.setTextIsSelectable(true);
            bubble.addView(content);

            if (!user) {
                LinearLayout ops = Ui.horizontal(AiChatActivity.this);
                TextView apply = op("应用修改");
                apply.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) { applyCode(extractCode(m.content)); }
                });
                TextView copy = op("复制");
                copy.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) { copyText(m.content); }
                });
                TextView full = op("应用并返回");
                full.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        applyCode(extractCode(m.content));
                    }
                });
                ops.addView(apply);
                ops.addView(copy);
                LinearLayout.LayoutParams olp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                olp.topMargin = Ui.dp(AiChatActivity.this, 6);
                bubble.addView(ops, olp);
            }

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    (int) (AiChatActivity.this.getResources().getDisplayMetrics().widthPixels * 0.92f),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
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
