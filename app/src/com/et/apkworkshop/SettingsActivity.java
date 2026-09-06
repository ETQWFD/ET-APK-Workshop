package com.et.apkworkshop;

import android.app.Activity;
import android.app.ProgressDialog;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.et.apkworkshop.engine.AiClient;
import com.et.apkworkshop.util.AppSettings;
import com.et.apkworkshop.util.Ui;

/**
 * 设置页：AI 接口配置（自定义 endpoint/key/model）、smali API 级别、测试连接、关于。
 */
public class SettingsActivity extends Activity {

    private EditText urlEt, keyEt, modelEt, tempEt, tokenEt, apiLevelEt;
    private AppSettings s;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.applyAnimeBg(this);
        s = new AppSettings(this);

        LinearLayout root = Ui.vertical(this);
        root.setBackgroundColor(Ui.BG_OVERLAY);
        root.setPadding(Ui.dp(this, 16), Ui.dp(this, 10), Ui.dp(this, 16), Ui.dp(this, 16));

        LinearLayout header = Ui.horizontal(this);
        TextView back = Ui.label(this, "‹", Ui.PRIMARY, 26, true);
        back.setPadding(Ui.dp(this, 6), 0, Ui.dp(this, 10), 0);
        back.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        header.addView(back);
        TextView t = Ui.label(this, "设置", Ui.TEXT, 18, true);
        header.addView(t);
        root.addView(header);

        ScrollView scroll = new ScrollView(this);
        LinearLayout body = Ui.vertical(this);
        body.setPadding(0, Ui.dp(this, 8), 0, 0);

        // ---- AI 配置 ----
        TextView aiTitle = Ui.label(this, "AI 助手配置", Ui.PRIMARY, 16, true);
        body.addView(aiTitle, lp(0, 4, 0, 2));
        TextView aiTip = Ui.label(this, "支持任意 OpenAI 兼容接口（可填自有/第三方/本地服务地址）", Ui.TEXT_DIM, 12, false);
        body.addView(aiTip, lp(2, 0, 0, 6));

        urlEt = input("API 地址（如 https://api.openai.com/v1 或 http://192.168.x.x:8000/v1）", s.getAiBaseUrl());
        body.addView(urlEt, lp(0, 4, 0, 8));

        keyEt = input("API Key（没有可留空，用于免密钥的本地服务）", s.getAiApiKey());
        keyEt.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        body.addView(keyEt, lp(0, 4, 0, 8));

        modelEt = input("模型名（如 gpt-4o-mini / deepseek-chat / qwen-plus）", s.getAiModel());
        body.addView(modelEt, lp(0, 4, 0, 8));

        tempEt = input("温度 Temperature（0~2，默认 0.7）", String.valueOf(s.getAiTemperature()));
        tempEt.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        body.addView(tempEt, lp(0, 4, 0, 8));

        tokenEt = input("最大输出 tokens（默认 2048）", String.valueOf(s.getAiMaxTokens()));
        tokenEt.setInputType(InputType.TYPE_CLASS_NUMBER);
        body.addView(tokenEt, lp(0, 4, 0, 8));

        apiLevelEt = input("Smali API 级别（默认 34，反编译/汇编 opcode 用）", String.valueOf(s.getApiLevel()));
        apiLevelEt.setInputType(InputType.TYPE_CLASS_NUMBER);
        body.addView(apiLevelEt, lp(0, 4, 0, 8));

        // 按钮
        LinearLayout btns = Ui.horizontal(this);
        TextView save = Ui.primaryButton(this, "保存设置");
        save.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { save(); }
        });
        TextView test = Ui.ghostButton(this, "测试连接", Ui.ACCENT);
        test.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                save();
                testConnection();
            }
        });
        btns.addView(save, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        btns.addView(test, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        body.addView(btns, lp(0, 8, 0, 4));

        // ---- 关于 ----
        TextView about = Ui.label(this, "关于", Ui.PRIMARY, 16, true);
        body.addView(about, lp(0, 20, 0, 2));

        // 检查更新按钮
        TextView updateBtn = Ui.ghostButton(this, "检查更新", Ui.ACCENT);
        updateBtn.setGravity(Gravity.CENTER);
        updateBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                com.et.apkworkshop.util.AppUpdate.checkAndPrompt(SettingsActivity.this, false);
            }
        });
        body.addView(updateBtn, lp(0, 4, 0, 8));

        TextView aboutText = Ui.label(this,
                "ETC APK 工坊 v2.9\n"
                        + "ETC 出品，版权所有 © ETC · MIT License\n\n"
                        + "功能：\n"
                        + "· 反编译任意 APK → smali 源码工程\n"
                        + "· C++ 原生深度脱壳（免root + Shizuku支持）\n"
                        + "· 内置 smali 源码浏览器与编辑器\n"
                        + "· AI 助手协助分析与修改代码\n"
                        + "· 一键重新编译、重打包并自动签名（v1+v2）\n"
                        + "· 随机二次元壁纸，每分钟自动切换\n"
                        + "· 检测更新（GitHub Release，无限流）\n\n"
                        + "使用须知：\n"
                        + "本工具仅用于学习、安全研究、以及修改你自己拥有或有授权修改的应用。"
                        + "请勿用于破解盗版、绕过付费/授权校验等非法用途。禁止倒卖。",
                Ui.TEXT_DIM, 12, false);
        aboutText.setLineSpacing(Ui.dp(this, 3), 1.0f);
        body.addView(aboutText, lp(2, 0, 0, 0));

        scroll.addView(body);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(root);
    }

    private LinearLayout.LayoutParams lp(int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(Ui.dp(this, l), Ui.dp(this, t), Ui.dp(this, r), Ui.dp(this, b));
        return p;
    }

    private EditText input(String hint, String value) {
        EditText e = Ui.input(this, hint);
        e.setText(value);
        return e;
    }

    private void save() {
        try {
            s.setAiBaseUrl(urlEt.getText().toString().trim());
            s.setAiApiKey(keyEt.getText().toString().trim());
            String model = modelEt.getText().toString().trim();
            if (model.isEmpty()) model = "gpt-4o-mini";
            s.setAiModel(model);
            try {
                s.setAiTemperature(Double.parseDouble(tempEt.getText().toString().trim()));
            } catch (Exception ignored) {
            }
            try {
                s.setAiMaxTokens(Integer.parseInt(tokenEt.getText().toString().trim()));
            } catch (Exception ignored) {
            }
            try {
                s.setApiLevel(Integer.parseInt(apiLevelEt.getText().toString().trim()));
            } catch (Exception ignored) {
            }
            Ui.toast(this, "设置已保存");
        } catch (Exception e) {
            Ui.toast(this, "保存失败: " + e.getMessage());
        }
    }

    private void testConnection() {
        // 直接读取输入框当前内容（不要求先保存），空 key 也允许测试（本地免密钥服务）
        final String url = urlEt.getText().toString().trim();
        final String key = keyEt.getText().toString().trim();
        final String model = modelEt.getText().toString().trim();
        if (url.isEmpty()) { Ui.alert(this, "配置不完整", "请先填写 API 地址"); return; }
        if (model.isEmpty()) { Ui.alert(this, "配置不完整", "请先填写模型名"); return; }
        final ProgressDialog pd = new ProgressDialog(this);
        pd.setMessage("正在测试连接 …");
        pd.setCancelable(false);
        pd.show();
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    final String reply = AiClient.testConnection(url, key, model);
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            pd.dismiss();
                            Ui.alert(SettingsActivity.this, "连接成功", "AI 返回：\n" + reply);
                        }
                    });
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            pd.dismiss();
                            Ui.alert(SettingsActivity.this, "连接失败", e.getMessage());
                        }
                    });
                }
            }
        }).start();
    }
}
