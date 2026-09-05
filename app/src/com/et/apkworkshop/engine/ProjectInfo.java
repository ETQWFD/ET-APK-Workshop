package com.et.apkworkshop.engine;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 反编译工程元数据。工程目录结构：
 *   <projectDir>/
 *     original.apk       原始 APK 副本
 *     info.json          元数据
 *     apk_src/           解压出的原始资源（resources.arsc、res/、assets/、lib/、AndroidManifest.xml 等）
 *     smali/             classes.dex 反编译结果
 *     smali_classes2/    classes2.dex 反编译结果（如存在）
 *     ...
 *     output/            编译打包输出
 */
public final class ProjectInfo {

    public File projectDir;
    public String name;
    public long created;
    public int apiLevel;
    public List<String> dexNames;   // 例如 ["classes.dex", "classes2.dex"]
    public long lastBuild;
    public String lastOutput;

    public ProjectInfo() {
        dexNames = new ArrayList<String>();
    }

    public File originalApk() { return new File(projectDir, "original.apk"); }
    public File apkSrcDir()   { return new File(projectDir, "apk_src"); }
    public File outputDir()   { return new File(projectDir, "output"); }
    public File infoFile()    { return new File(projectDir, "info.json"); }

    /** 第 i 个 dex 对应的 smali 目录。 */
    public File smaliDir(int i) {
        if (i == 0) return new File(projectDir, "smali");
        return new File(projectDir, "smali_classes" + (i + 1));
    }

    public List<File> smaliDirs() {
        List<File> list = new ArrayList<File>();
        for (int i = 0; i < dexNames.size(); i++) list.add(smaliDir(i));
        return list;
    }

    public static ProjectInfo fromJson(File dir) {
        ProjectInfo pi = new ProjectInfo();
        pi.projectDir = dir;
        try {
            JSONObject j = new JSONObject(readText(new File(dir, "info.json")));
            pi.name = j.optString("name", dir.getName());
            pi.created = j.optLong("created");
            pi.apiLevel = j.optInt("apiLevel", 34);
            pi.lastBuild = j.optLong("lastBuild");
            pi.lastOutput = j.optString("lastOutput", "");
            JSONArray arr = j.optJSONArray("dexNames");
            pi.dexNames.clear();
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) pi.dexNames.add(arr.getString(i));
            }
        } catch (Exception e) {
            // 损坏则最小化恢复
            pi.name = dir.getName();
            pi.apiLevel = 34;
            pi.dexNames.add("classes.dex");
        }
        return pi;
    }

    public void save() throws Exception {
        JSONObject j = new JSONObject();
        j.put("name", name);
        j.put("created", created);
        j.put("apiLevel", apiLevel);
        j.put("lastBuild", lastBuild);
        j.put("lastOutput", lastOutput == null ? "" : lastOutput);
        JSONArray arr = new JSONArray();
        for (String d : dexNames) arr.put(d);
        j.put("dexNames", arr);
        writeText(infoFile(), j.toString(2));
    }

    static String readText(File f) throws Exception {
        java.io.FileInputStream in = new java.io.FileInputStream(f);
        try {
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            return new String(bos.toByteArray(), "UTF-8");
        } finally {
            in.close();
        }
    }

    static void writeText(File f, String text) throws Exception {
        java.io.FileOutputStream out = new java.io.FileOutputStream(f);
        try {
            out.write(text.getBytes("UTF-8"));
        } finally {
            out.close();
        }
    }
}
