package com.et.apkworkshop.engine;

import com.android.apksig.ApkSigner;

import java.io.File;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * APK v1/v2 签名封装（基于 apksig 库，纯 Java，可在 Android 上运行）。
 * 使用与项目绑定的持久化密钥，保证同一工程多次打包签名一致。
 */
public final class ApkSignerUtil {

    private ApkSignerUtil() {}

    public static void sign(File unsignedApk, File signedApk, File keyStoreFile) throws Exception {
        X509SelfSigned.KeyMaterial km = X509SelfSigned.loadOrCreate(keyStoreFile);
        List<X509Certificate> certs = new ArrayList<X509Certificate>();
        certs.add(km.certificate);

        ApkSigner.SignerConfig signerConfig =
                new ApkSigner.SignerConfig.Builder("ET", km.privateKey, certs).build();

        ApkSigner signer = new ApkSigner.Builder(Arrays.asList(signerConfig))
                .setInputApk(unsignedApk)
                .setOutputApk(signedApk)
                .setV1SigningEnabled(true)
                .setV2SigningEnabled(true)
                .setV3SigningEnabled(false)
                .setV4SigningEnabled(false)
                .setMinSdkVersion(21)
                .setDebuggableApkPermitted(false)
                .build();
        signer.sign();
    }
}
