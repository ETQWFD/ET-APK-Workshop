#include <jni.h>
#include <cstring>
#include <cstdint>
#include <cstdlib>
#include <vector>

#define DEX_MAGIC_035 "dex\n035"
#define DEX_MAGIC_036 "dex\n036"
#define DEX_MAGIC_LEN 8

struct DexHit {
    int offset;
    int length;
};

static bool is_dex_magic(const uint8_t* p) {
    return (p[0] == 'd' && p[1] == 'e' && p[2] == 'x' && p[3] == '\n' &&
            p[4] == '0' && p[5] == '3' && (p[6] == '5' || p[6] == '6') && p[7] == 0);
}

static int read_dex_length(const uint8_t* p, int remain) {
    if (remain < 36) return 0;
    int len = (int)p[32] | ((int)p[33] << 8) | ((int)p[34] << 16) | ((int)p[35] << 24);
    if (len < 100 || len > 100 * 1024 * 1024) return 0;
    return len;
}

extern "C" {

JNIEXPORT jintArray JNICALL
Java_com_et_apkworkshop_engine_NativeUnpacker_scanDexMagic(
        JNIEnv* env, jobject thiz, jbyteArray data, jint offset, jint length) {
    if (data == nullptr || length < DEX_MAGIC_LEN) {
        return env->NewIntArray(0);
    }
    jbyte* buf = env->GetByteArrayElements(data, nullptr);
    if (buf == nullptr) return env->NewIntArray(0);

    const uint8_t* base = reinterpret_cast<const uint8_t*>(buf) + offset;
    int len = length;
    std::vector<DexHit> hits;
    hits.reserve(16);

    int i = 0;
    while (i <= len - DEX_MAGIC_LEN) {
        if (base[i] == 'd' && is_dex_magic(base + i)) {
            int dlen = read_dex_length(base + i, len - i);
            hits.push_back({i, dlen});
            if (dlen > 0) {
                i += dlen;
                continue;
            }
        }
        i++;
    }

    env->ReleaseByteArrayElements(data, buf, JNI_ABORT);

    jintArray result = env->NewIntArray((jsize)hits.size() * 2);
    if (result == nullptr) return nullptr;
    jint* arr = env->GetIntArrayElements(result, nullptr);
    for (size_t k = 0; k < hits.size(); k++) {
        arr[k * 2] = hits[k].offset;
        arr[k * 2 + 1] = hits[k].length;
    }
    env->ReleaseIntArrayElements(result, arr, 0);
    return result;
}

JNIEXPORT jbyteArray JNICALL
Java_com_et_apkworkshop_engine_NativeUnpacker_extractBytes(
        JNIEnv* env, jobject thiz, jbyteArray data, jint offset, jint length) {
    if (data == nullptr || length <= 0) return nullptr;
    jbyte* buf = env->GetByteArrayElements(data, nullptr);
    if (buf == nullptr) return nullptr;

    jbyteArray result = env->NewByteArray(length);
    if (result == nullptr) {
        env->ReleaseByteArrayElements(data, buf, JNI_ABORT);
        return nullptr;
    }
    env->SetByteArrayRegion(result, 0, length, buf + offset);
    env->ReleaseByteArrayElements(data, buf, JNI_ABORT);
    return result;
}

JNIEXPORT jstring JNICALL
Java_com_et_apkworkshop_engine_NativeUnpacker_getVersion(
        JNIEnv* env, jobject thiz) {
    return env->NewStringUTF("native-unpacker 2.9 (C++17, aarch64+armv7)");
}

}
