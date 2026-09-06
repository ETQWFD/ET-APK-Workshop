package com.et.apkworkshop;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.et.apkworkshop.util.Ui;

/**
 * 首次使用许可协议：用户必须同意才能进入应用。
 * 明确声明：本工具可能触犯厂商协议，一切责任由用户承担，开发者 ET 免责。
 */
public class LicenseActivity extends Activity {
    private static final String PREFS = "et_license";
    private static final String KEY_ACCEPTED = "accepted_v1";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 已同意过则直接进入主界面
        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        if (sp.getBoolean(KEY_ACCEPTED, false)) {
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }

        LinearLayout root = Ui.vertical(this);
        root.setBackgroundColor(Ui.BG);
        root.setPadding(Ui.dp(this, 20), Ui.dp(this, 20), Ui.dp(this, 20), Ui.dp(this, 20));

        // 标题
        TextView title = Ui.label(this, "ETC APK 工坊 - 使用许可协议", Ui.PRIMARY, 20, true);
        title.setGravity(Gravity.CENTER);
        root.addView(title, lp(0, 0, 0, 12));

        // 协议正文（可滚动）
        ScrollView scroll = new ScrollView(this);
        scroll.setBackground(Ui.roundedStroke(Ui.CARD, Ui.BORDER, 12, this));
        scroll.setPadding(Ui.dp(this, 14), Ui.dp(this, 14), Ui.dp(this, 14), Ui.dp(this, 14));

        TextView body = new TextView(this);
        body.setTextColor(Ui.TEXT);
        body.setTextSize(13);
        body.setLineSpacing(Ui.dp(this, 4), 1.0f);
        body.setMovementMethod(new ScrollingMovementMethod());
        body.setText(getLicenseText());
        scroll.addView(body);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        // 同意勾选
        final CheckBox agree = new CheckBox(this);
        agree.setText("我已阅读并完全同意以上协议");
        agree.setTextColor(Ui.TEXT);
        agree.setTextSize(14);
        agree.setPadding(Ui.dp(this, 4), Ui.dp(this, 10), 0, Ui.dp(this, 6));
        root.addView(agree, lp(0, 8, 0, 4));

        // 按钮
        LinearLayout btns = Ui.horizontal(this);
        Button accept = new Button(this);
        accept.setText("同意并进入");
        accept.setTextColor(Color.rgb(2, 20, 30));
        accept.setTextSize(15);
        accept.setAllCaps(false);
        accept.setBackground(Ui.rounded(Ui.PRIMARY, 10, this));
        accept.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (!agree.isChecked()) {
                    Ui.toast(LicenseActivity.this, "请先勾选同意协议");
                    return;
                }
                getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                        .putBoolean(KEY_ACCEPTED, true).apply();
                startActivity(new Intent(LicenseActivity.this, MainActivity.class));
                finish();
            }
        });

        Button reject = new Button(this);
        reject.setText("不同意并退出");
        reject.setTextColor(Ui.TEXT_DIM);
        reject.setTextSize(15);
        reject.setAllCaps(false);
        reject.setBackground(Ui.rounded(Ui.CARD2, 10, this));
        reject.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                finish();
                System.exit(0);
            }
        });

        btns.addView(accept, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        btns.addView(reject, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        root.addView(btns, lp(0, 4, 0, 0));

        setContentView(root);
    }

    private LinearLayout.LayoutParams lp(int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(Ui.dp(this, l), Ui.dp(this, t), Ui.dp(this, r), Ui.dp(this, b));
        return p;
    }

    private String getLicenseText() {
        return "【重要声明】请仔细阅读以下协议，使用本软件即表示您完全同意。\n\n"
                + "一、软件性质\n"
                + "ETC APK 工坊（以下简称\"本软件\"）是一款安卓 APK 反编译、脱壳、修改与重打包工具，"
                + "由 ETC 独立开发。"
                + "本软件仅供学习、安全研究、以及修改您自己拥有或有合法授权的应用使用。\n\n"
                + "二、法律保护与管辖\n"
                + "1. 本软件受中华人民共和国法律保护，著作权归 ETC 所有。\n"
                + "2. 任何利用本软件从事违法犯罪活动（包括但不限于入侵他人系统、窃取数据、破坏计算机信息系统），"
                + "将由公安机关依法管辖处理，开发者 ETC 将配合提供相关证据。\n"
                + "3. 禁止对本软件进行倒卖、二次销售或任何形式的商业牟利，违者将追究法律责任。\n\n"
                + "三、免责声明\n"
                + "1. 本软件的反编译、脱壳、重打包等功能可能触犯部分软件厂商的用户协议、服务条款或相关法律法规。\n"
                + "2. 您使用本软件所产生的一切后果（包括但不限于法律纠纷、账号封禁、数据损失、设备损坏）"
                + "均由您本人自行承担，开发者 ETC 不承担任何直接或间接责任。\n"
                + "3. 开发者 ETC 不对本软件的适用性、可靠性、完整性做任何明示或暗示的担保。"
                + "本软件按\"现状\"提供，使用风险由用户承担。\n"
                + "4. 因使用本软件导致的任何第三方索赔，由用户自行负责解决并赔偿开发者因此遭受的损失。\n\n"
                + "四、禁止行为\n"
                + "1. 禁止将本软件用于破解盗版软件、绕过付费/授权校验、窃取他人数据、入侵他人系统等非法行为。\n"
                + "2. 禁止对本软件进行倒卖、二次销售、付费转发或任何形式的商业牟利。"
                + "本软件为免费工具，版权归 ETC 所有，保留所有权利。\n"
                + "3. 禁止去除或修改本软件中的版权声明、作者信息及本协议内容。\n"
                + "4. 禁止将本软件用于任何违反当地法律法规的用途。\n\n"
                + "五、脱壳与加固说明\n"
                + "本软件内置深度脱壳引擎：全文件字节扫描 dex 魔数，可提取隐藏在任意文件中的 dex；"
                + "支持 root 设备运行时内存转储（需 root 权限）。"
                + "对于动态加密的加固壳，非 root 设备无法完全脱壳，这是安卓系统安全机制限制，非软件缺陷。"
                + "脱壳后的文件仅供分析学习使用。\n\n"
                + "六、隐私说明\n"
                + "本软件不会收集您的任何个人信息。AI 对话内容仅发送至您在设置中自行配置的 AI 接口，"
                + "开发者 ETC 无法获取或存储您的对话内容。\n\n"
                + "七、协议变更\n"
                + "开发者 ETC 保留随时修改本协议的权利。修改后的协议将在软件更新时提示，"
                + "继续使用即表示同意修改后的协议。\n\n"
                + "八、联系方式\n"
                + "如对本协议有疑问，可通过 GitHub 仓库提交 Issue。\n\n"
                + "【最终确认】\n"
                + "我已完整阅读并理解以上全部条款，自愿承担使用本软件的一切后果，"
                + "并承诺不将本软件用于非法用途或商业倒卖。";
    }
}
