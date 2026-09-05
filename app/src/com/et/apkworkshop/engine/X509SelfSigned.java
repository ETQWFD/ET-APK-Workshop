package com.et.apkworkshop.engine;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * 纯 Java 自签名 X.509 证书生成器（不依赖 BouncyCastle），
 * 用于对重打包 APK 进行 v1/v2 签名。生成的密钥对与证书会持久化复用，
 * 保证同一 App 多次重打包签名一致，可直接覆盖安装。
 */
public final class X509SelfSigned {

    private static final String CN = "ET APK 工坊";
    private static final String OID_SHA256_RSA = "1.2.840.113549.1.1.11";
    private static final String OID_RSA = "1.2.840.113549.1.1.1";
    private static final String OID_CN = "2.5.4.3";

    private X509SelfSigned() {}

    public static final class KeyMaterial {
        public final PrivateKey privateKey;
        public final X509Certificate certificate;
        public KeyMaterial(PrivateKey privateKey, X509Certificate certificate) {
            this.privateKey = privateKey;
            this.certificate = certificate;
        }
    }

    /** 若 keystore 文件存在则加载，否则生成并保存。 */
    public static KeyMaterial loadOrCreate(File storeFile) throws Exception {
        if (storeFile.exists() && storeFile.length() > 0) {
            try {
                return load(storeFile);
            } catch (Exception e) {
                // 文件损坏则重建
                storeFile.delete();
            }
        }
        KeyMaterial km = create();
        save(storeFile, km);
        return km;
    }

    public static void save(File storeFile, KeyMaterial km) throws Exception {
        if (!storeFile.getParentFile().exists()) storeFile.getParentFile().mkdirs();
        try (FileOutputStream fos = new FileOutputStream(storeFile)) {
            byte[] key = km.privateKey.getEncoded();
            byte[] cert = km.certificate.getEncoded();
            // 格式: 4 字节 key 长度 + key + 4 字节 cert 长度 + cert
            fos.write(new byte[]{
                    (byte) (key.length >>> 24), (byte) (key.length >>> 16),
                    (byte) (key.length >>> 8), (byte) key.length});
            fos.write(key);
            fos.write(new byte[]{
                    (byte) (cert.length >>> 24), (byte) (cert.length >>> 16),
                    (byte) (cert.length >>> 8), (byte) cert.length});
            fos.write(cert);
        }
    }

    public static KeyMaterial load(File storeFile) throws Exception {
        try (InputStream is = new FileInputStream(storeFile)) {
            byte[] len = readN(is, 4);
            int keyLen = ((len[0] & 0xFF) << 24) | ((len[1] & 0xFF) << 16)
                    | ((len[2] & 0xFF) << 8) | (len[3] & 0xFF);
            byte[] keyBytes = readN(is, keyLen);
            byte[] len2 = readN(is, 4);
            int certLen = ((len2[0] & 0xFF) << 24) | ((len2[1] & 0xFF) << 16)
                    | ((len2[2] & 0xFF) << 8) | (len2[3] & 0xFF);
            byte[] certBytes = readN(is, certLen);
            KeyFactory kf = KeyFactory.getInstance("RSA");
            PrivateKey pk = kf.generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            X509Certificate cert = (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(certBytes));
            return new KeyMaterial(pk, cert);
        }
    }

    private static byte[] readN(InputStream is, int n) throws IOException {
        byte[] b = new byte[n];
        int off = 0;
        while (off < n) {
            int r = is.read(b, off, n - off);
            if (r < 0) throw new IOException("keystore 文件截断");
            off += r;
        }
        return b;
    }

    public static KeyMaterial create() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair kp = kpg.generateKeyPair();
        X509Certificate cert = buildSelfSigned(kp);
        return new KeyMaterial(kp.getPrivate(), cert);
    }

    // ---------------- DER 编码 ----------------

    static ByteArrayOutputStream derSeq(ByteArrayOutputStream... items) throws IOException {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        for (ByteArrayOutputStream item : items) body.write(item.toByteArray());
        return derWrap(0x30, body.toByteArray());
    }

    static ByteArrayOutputStream derWrap(int tag, byte[] content) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(tag);
        int len = content.length;
        if (len < 0x80) {
            out.write(len);
        } else if (len < 0x100) {
            out.write(0x81); out.write(len);
        } else if (len < 0x10000) {
            out.write(0x82); out.write(len >> 8); out.write(len & 0xFF);
        } else {
            out.write(0x83);
            out.write((len >>> 16) & 0xFF); out.write((len >>> 8) & 0xFF); out.write(len & 0xFF);
        }
        out.write(content);
        return out;
    }

    static ByteArrayOutputStream derInteger(BigInteger v) throws IOException {
        byte[] b = v.toByteArray();
        return derWrap(0x02, b);
    }

    static ByteArrayOutputStream derOid(String oid) throws IOException {
        String[] parts = oid.split("\\.");
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        int first = Integer.parseInt(parts[0]) * 40 + Integer.parseInt(parts[1]);
        writeBase128(body, first);
        for (int i = 2; i < parts.length; i++) writeBase128(body, Long.parseLong(parts[i]));
        return derWrap(0x06, body.toByteArray());
    }

    private static void writeBase128(ByteArrayOutputStream out, long value) throws IOException {
        // 编码为多个 7 位分组，大端，最后一位高位置 0
        long v = value;
        ByteArrayOutputStream tmp = new ByteArrayOutputStream();
        tmp.write((int) (v & 0x7F));
        v >>>= 7;
        while (v != 0) {
            tmp.write((int) ((v & 0x7F) | 0x80));
            v >>>= 7;
        }
        byte[] b = tmp.toByteArray();
        for (int i = b.length - 1; i >= 0; i--) out.write(b[i]);
    }

    static ByteArrayOutputStream derUtf8String(String s) throws IOException {
        return derWrap(0x0C, s.getBytes("UTF-8"));
    }

    static ByteArrayOutputStream derNull() throws IOException {
        return derWrap(0x05, new byte[0]);
    }

    static ByteArrayOutputStream derBitString(byte[] data) throws IOException {
        byte[] withPad = new byte[data.length + 1];
        withPad[0] = 0; // 0 unused bits
        System.arraycopy(data, 0, withPad, 1, data.length);
        return derWrap(0x03, withPad);
    }

    static ByteArrayOutputStream derUTCTime(Date d) throws IOException {
        SimpleDateFormat sdf = new SimpleDateFormat("yyMMddHHmmss'Z'", Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return derWrap(0x17, sdf.format(d).getBytes("US-ASCII"));
    }

    static ByteArrayOutputStream derRdn(String oid, String value) throws IOException {
        // SET { SEQUENCE { oid, value } }
        ByteArrayOutputStream atav = derSeq(derOid(oid), derUtf8String(value));
        return derWrap(0x31, atav.toByteArray());
    }

    static ByteArrayOutputStream derName(String cn) throws IOException {
        // SEQUENCE OF RDN
        return derSeq(derRdn(OID_CN, cn));
    }

    static ByteArrayOutputStream derAlgId(String oid) throws IOException {
        return derSeq(derOid(oid), derNull());
    }

    static ByteArrayOutputStream derSpi(PublicKey pub) throws Exception {
        // SubjectPublicKeyInfo
        byte[] rsaPub = rsaPublicKeyDer(pub);
        return derSeq(derAlgId(OID_RSA), derBitString(rsaPub));
    }

    static byte[] rsaPublicKeyDer(PublicKey pub) throws Exception {
        // 直接从 RSA 公钥取模数/指数，组装 RSAPublicKey ::= SEQ { n INTEGER, e INTEGER }
        java.security.interfaces.RSAPublicKey rsa = (java.security.interfaces.RSAPublicKey) pub;
        return derSeq(derInteger(rsa.getModulus()), derInteger(rsa.getPublicExponent())).toByteArray();
    }

    static ByteArrayOutputStream derVersion(int v) throws IOException {
        // [0] EXPLICIT INTEGER
        ByteArrayOutputStream inner = derInteger(BigInteger.valueOf(v));
        return derWrap(0xA0, inner.toByteArray());
    }

    public static X509Certificate buildSelfSigned(KeyPair kp) throws Exception {
        SecureRandom sr = new SecureRandom();
        byte[] serialBytes = new byte[16];
        sr.nextBytes(serialBytes);
        serialBytes[0] &= 0x7F; // 保证正数
        BigInteger serial = new BigInteger(serialBytes);

        Date now = new Date();
        Date notBefore = new Date(now.getTime() - 24L * 3600 * 1000);
        Date notAfter = new Date(now.getTime() + 30L * 365 * 24 * 3600 * 1000);

        ByteArrayOutputStream validity = derSeq(derUTCTime(notBefore), derUTCTime(notAfter));

        ByteArrayOutputStream tbs = derSeq(
                derVersion(2),
                derInteger(serial),
                derAlgId(OID_SHA256_RSA),
                derName(CN),          // issuer
                validity,
                derName(CN),          // subject
                derSpi(kp.getPublic())
        );

        byte[] tbsBytes = tbs.toByteArray();

        Signature sig = Signature.getInstance("SHA256withRSA");
        sig.initSign(kp.getPrivate());
        sig.update(tbsBytes);
        byte[] sigBytes = sig.sign();

        ByteArrayOutputStream cert = derSeq(tbs, derAlgId(OID_SHA256_RSA), derBitString(sigBytes));

        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        return (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(cert.toByteArray()));
    }
}
