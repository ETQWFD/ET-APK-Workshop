package com.et.apkworkshop.util;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 应用设置（SharedPreferences 封装）。
 */
public final class AppSettings {

    private static final String PREF = "et_apk_settings";

    private final SharedPreferences sp;

    public AppSettings(Context c) {
        sp = c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public String getAiBaseUrl() {
        return sp.getString("ai_base_url", "https://api.openai.com/v1");
    }
    public void setAiBaseUrl(String v) { sp.edit().putString("ai_base_url", v).apply(); }

    public String getAiApiKey() {
        return sp.getString("ai_api_key", "");
    }
    public void setAiApiKey(String v) { sp.edit().putString("ai_api_key", v).apply(); }

    public String getAiModel() {
        return sp.getString("ai_model", "gpt-4o-mini");
    }
    public void setAiModel(String v) { sp.edit().putString("ai_model", v).apply(); }

    public double getAiTemperature() {
        return sp.getFloat("ai_temperature", 0.7f);
    }
    public void setAiTemperature(double v) { sp.edit().putFloat("ai_temperature", (float) v).apply(); }

    public int getAiMaxTokens() {
        return sp.getInt("ai_max_tokens", 2048);
    }
    public void setAiMaxTokens(int v) { sp.edit().putInt("ai_max_tokens", v).apply(); }

    public int getApiLevel() {
        return sp.getInt("api_level", 34);
    }
    public void setApiLevel(int v) { sp.edit().putInt("api_level", v).apply(); }

    public boolean isAiConfigured() {
        String url = getAiBaseUrl();
        String model = getAiModel();
        return url != null && !url.trim().isEmpty() && model != null && !model.trim().isEmpty();
    }
}
