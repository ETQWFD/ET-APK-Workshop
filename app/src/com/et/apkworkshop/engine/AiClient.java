package com.et.apkworkshop.engine;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * OpenAI 兼容 Chat Completions 客户端。
 * 用户可在设置中配置任意兼容 endpoint / key / model。
 * 使用 Android 内置 org.json 与 HttpURLConnection，无第三方依赖。
 */
public final class AiClient {

    public static final class Msg {
        public final String role;   // "system" / "user" / "assistant"
        public final String content;
        public Msg(String role, String content) {
            this.role = role;
            this.content = content;
        }
    }

    private AiClient() {}

    /** 规范化 base url：补全协议与末尾斜杠。 */
    public static String normalizeBaseUrl(String base) {
        String b = base == null ? "" : base.trim();
        if (b.isEmpty()) return b;
        if (!b.startsWith("http://") && !b.startsWith("https://")) b = "https://" + b;
        while (b.endsWith("/")) b = b.substring(0, b.length() - 1);
        return b;
    }

    public static String chat(String baseUrl, String apiKey, String model,
                              double temperature, List<Msg> history, int maxTokens) throws Exception {
        String base = normalizeBaseUrl(baseUrl);
        if (base.isEmpty() || model == null || model.trim().isEmpty()) {
            throw new IOException("请先在设置中配置 AI 的 API 地址与模型名");
        }
        String url = base + "/chat/completions";

        JSONObject body = new JSONObject();
        body.put("model", model.trim());
        body.put("temperature", temperature);
        if (maxTokens > 0) body.put("max_tokens", maxTokens);
        JSONArray msgs = new JSONArray();
        for (Msg m : history) {
            JSONObject o = new JSONObject();
            o.put("role", m.role);
            o.put("content", m.content);
            msgs.put(o);
        }
        body.put("messages", msgs);

        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        try {
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("User-Agent", "ET-APK-Workshop/1.0");
            if (apiKey != null && !apiKey.trim().isEmpty()) {
                conn.setRequestProperty("Authorization", "Bearer " + apiKey.trim());
            }
            conn.setDoOutput(true);
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(180000);
            conn.setInstanceFollowRedirects(true);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }
            int code;
            try {
                code = conn.getResponseCode();
            } catch (IOException ioe) {
                throw new IOException("无法连接 AI 接口（" + url + "）：" + ioe.getMessage()
                        + "\n请检查 API 地址是否正确、网络是否可达（国内访问 OpenAI 官方地址需代理）。");
            }
            InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
            String resp = readAll(is);
            if (code < 200 || code >= 300) {
                String detail = resp.isEmpty() ? "(无返回体)" : truncate(resp, 500);
                throw new IOException("AI 接口返回 HTTP " + code + ": " + detail
                        + "\n常见原因：API Key 错误/过期、模型名不存在、额度不足。");
            }
            if (resp.isEmpty()) {
                throw new IOException("AI 接口返回空响应，请检查 API 地址是否为 /chat/completions 兼容端点");
            }
            JSONObject j;
            try {
                j = new JSONObject(resp);
            } catch (Exception pe) {
                throw new IOException("AI 返回内容无法解析: " + truncate(resp, 300));
            }
            JSONArray choices = j.optJSONArray("choices");
            if (choices != null && choices.length() > 0) {
                JSONObject msg = choices.optJSONObject(0).optJSONObject("message");
                if (msg != null && msg.has("content")) {
                    return msg.optString("content", "");
                }
                String text = choices.optJSONObject(0).optString("text", "");
                if (!text.isEmpty()) return text;
            }
            return resp;
        } finally {
            conn.disconnect();
        }
    }

    /** 测试连接：发送一条极简消息。 */
    public static String testConnection(String baseUrl, String apiKey, String model) throws Exception {
        List<Msg> history = new ArrayList<Msg>();
        history.add(new Msg("user", "你好，请只回复：连接成功"));
        return chat(baseUrl, apiKey, model, 0.3, history, 64);
    }

    private static String readAll(InputStream is) throws IOException {
        if (is == null) return "";
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
        return new String(bos.toByteArray(), StandardCharsets.UTF_8);
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
