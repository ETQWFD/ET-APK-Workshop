#!/bin/bash
# ET APK 工坊 - 完整构建脚本（无需 Android Studio / Gradle）
set -e
cd "$(dirname "$0")"

JAVA_HOME="$(pwd)/dl/jdk-17.0.2"
BT="$(pwd)/build-tools/android-14"
ANDROID_JAR="$(pwd)/dl/android-34.jar"
LIBS="dl/smali-3.0.10.jar:dl/smali-baksmali-3.0.10.jar:dl/smali-dexlib2-3.0.10.jar:dl/smali-util-3.0.10.jar:dl/guava-31.1-android.jar:dl/jsr305-3.0.2.jar:dl/antlr-3.5.2.jar:dl/antlr-runtime-3.5.2.jar:dl/stringtemplate-3.2.1.jar:dl/jcommander-1.82.jar:dl/apksig-7.4.2.jar:dl/org.json-20231013.jar:dl/failureaccess-1.0.1.jar"

export JAVA_HOME
mkdir -p out/build/classes out/build/res out/build/dex

echo "== 1/6 编译资源 =="
"$BT/aapt2" compile --dir app/res -o out/build/res.zip

echo "== 2/6 链接资源 + Manifest =="
"$BT/aapt2" link \
    -o out/build/base.apk \
    -I "$ANDROID_JAR" \
    --manifest app/AndroidManifest.xml \
    -R out/build/res.zip \
    --min-sdk-version 21 \
    --target-sdk-version 33 \
    --version-code 2 \
    --version-name 1.1 \
    --auto-add-overlay

echo "== 3/6 编译 Java =="
find app/src -name "*.java" > out/build/sources.txt
"$JAVA_HOME/bin/javac" -source 8 -target 8 -nowarn \
    -cp "$ANDROID_JAR:$LIBS" \
    -d out/build/classes \
    @out/build/sources.txt

echo "== 4/6 dex 化（合并引擎库）=="
"$BT/d8" --release --lib "$ANDROID_JAR" --min-api 21 \
    --output out/build/dex \
    $(find out/build/classes -name "*.class" | tr '\n' ' ') \
    dl/smali-3.0.10.jar dl/smali-baksmali-3.0.10.jar dl/smali-dexlib2-3.0.10.jar dl/smali-util-3.0.10.jar \
    dl/guava-31.1-android.jar dl/jsr305-3.0.2.jar dl/antlr-3.5.2.jar dl/antlr-runtime-3.5.2.jar \
    dl/stringtemplate-3.2.1.jar dl/jcommander-1.82.jar dl/apksig-7.4.2.jar

echo "== 5/6 打包 dex =="
python3 - <<'PYEOF'
import zipfile, glob
with zipfile.ZipFile('out/build/base.apk','a',zipfile.ZIP_DEFLATED) as z:
    for dex in sorted(glob.glob('out/build/dex/classes*.dex')):
        name = dex.split('/')[-1]
        z.write(dex, name)
        print('  +', name)
PYEOF

echo "== 6/6 对齐 + 签名 =="
"$BT/zipalign" -f -p 4 out/build/base.apk out/build/aligned.apk
"$BT/apksigner" sign --ks test/debug.keystore --ks-pass pass:android \
    --ks-key-alias androiddebugkey --key-pass pass:android \
    --out "out/ET-APK-工坊-v1.1.apk" out/build/aligned.apk

echo ""
echo "== 验证 =="
"$BT/apksigner" verify --print-certs "out/ET-APK-工坊-v1.1.apk" | head -3
"$BT/aapt2" dump badging "out/ET-APK-工坊-v1.1.apk" | head -3
ls -la "out/ET-APK-工坊-v1.1.apk"
echo "BUILD OK"
