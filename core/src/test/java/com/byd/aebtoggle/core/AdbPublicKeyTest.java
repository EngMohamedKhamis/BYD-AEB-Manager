package com.byd.aebtoggle.core;

import java.io.File;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;

/**
 * The encoder is checked against the golden reference ADB public key structure, which is PUBLIC data.
 * No private key is read or needed.
 */
final class AdbPublicKeyTest {
    private static final String DEFAULT_ADBHELPER = "../app/src/main/java/com/byd/aebtoggle/AdbHelper.java";

    /** Golden reference test vector: the 524-byte ADB RSAPublicKey structure used in v18.x */
    private static final String GOLDEN_PUBLIC_KEY =
            "QAAAANPLPjylOx9skyshGEm0VjuV3Wp69lNdIi0xg2+2PqT1X+hRxx01w5Mx2+vBBKOStOkTFQ2XlPC9bYXNXPepyI2v3gBpraIWT4U6Yb1+9gqqjxt11g9Yr3Ttfp/krLAMpLJss9XpYVoyMa9tnW2EPrznwVuv/E3ivDXKA9/d8kCEcK++yk4eex0agsAu3oGj/wZ97fZNApNswhFbOAj17ptY+ipYwPJUIwWzmg6qTQ/TJucCNNo08aUQj+gtKpejqAzrs3ImrfqoarSU4IzcSo+c+ZpwH1p62yD326XPvSKB2HuiQE0bqiSOtWiwZ6iE3ebpoLtgNYDafaD+A9BTMySNR9Cfe7V5nHYwUZGD9cW5R38yxRAD0oEEQD9xOIz/zagQA/jbgFdSgJvZ4GXcXMtZXUi1zyDBICaU3elu0CGXKThuLRjG1mPzS2V3etZNeRuKgEgz9qKEeDjSQwwJZL6aNRAjW32haBsjrsounXAmhsenmeg5pgzxlHo9/S99bx+/sunsHGupQWRGOjQaLEtMFzov5Yhvox1ZSk49B+bfw+MH50MWv1+F0/YNG35KCphOX0SMJaScq8gdm+czLZu5TNO3q4BYHDap2sf/GI+p92mVXy86pjujIuxwwxkxtmQ01G4Og4yeqy3KxUYIZW2s/rWsmEc2Vjd2UaFiUpRcNHEWGQEAAQA= wireless@adb\0";

    private AdbPublicKeyTest() {
    }

    static void run() throws Exception {
        T.section("AdbPublicKey: reproduces golden reference key byte for byte");
        String path = System.getProperty("adbhelper.path", DEFAULT_ADBHELPER);
        File source = new File(path);
        T.isTrue("AdbHelper.java found at " + source.getAbsolutePath(), source.isFile());
        checkAgainstGoldenKey();

        T.section("AdbPublicKey: a freshly generated 2048-bit key");
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(2048);
        KeyPair kp = g.generateKeyPair();
        RSAPublicKey pub = (RSAPublicKey) kp.getPublic();
        BigInteger n = pub.getModulus();
        BigInteger e = pub.getPublicExponent();
        byte[] struct = AdbPublicKey.encodeStruct(n, e);
        T.eq("length", AdbPublicKey.STRUCT_BYTES, struct.length);
        T.eq("modulus round trip", n, AdbPublicKey.modulusOf(struct));
        T.eq("exponent round trip", e, AdbPublicKey.exponentOf(struct));
        long n0inv = ((struct[4] & 0xFFL)) | ((struct[5] & 0xFFL) << 8) | ((struct[6] & 0xFFL) << 16) | ((struct[7] & 0xFFL) << 24);
        BigInteger product = n.multiply(BigInteger.valueOf(n0inv)).mod(BigInteger.ONE.shiftLeft(32));
        T.eq("n * n0inv == -1 (mod 2^32)", BigInteger.ONE.shiftLeft(32).subtract(BigInteger.ONE), product);
        byte[] payload = AdbPublicKey.authPayload(n, e, "tester@host");
        String text = new String(payload, Charset.forName("US-ASCII"));
        T.isTrue("payload ends with ' tester@host' + NUL", text.endsWith(" tester@host\0"));
        T.eqBytes("payload Base64 decodes to the struct", struct, Base64.getDecoder().decode(text.substring(0, text.indexOf(' '))));

        T.section("AdbPublicKey: bad input");
        T.throwsA("1024-bit modulus", IllegalArgumentException.class, () -> AdbPublicKey.encodeStruct(BigInteger.ONE.shiftLeft(1023).add(BigInteger.ONE), BigInteger.valueOf(65537)));
        T.throwsA("even modulus", IllegalArgumentException.class, () -> AdbPublicKey.encodeStruct(n.clearBit(0), e));
        T.throwsA("zero exponent", IllegalArgumentException.class, () -> AdbPublicKey.encodeStruct(n, BigInteger.ZERO));
        T.throwsA("null modulus", IllegalArgumentException.class, () -> AdbPublicKey.encodeStruct(null, e));
        T.throwsA("modulusOf wrong length", IllegalArgumentException.class, () -> AdbPublicKey.modulusOf(new byte[10]));
        byte[] wrongWords = struct.clone();
        wrongWords[0] = 32;
        T.throwsA("modulusOf wrong word count", IllegalArgumentException.class, () -> AdbPublicKey.modulusOf(wrongWords));
    }

    private static void checkAgainstGoldenKey() {
        int space = GOLDEN_PUBLIC_KEY.indexOf(' ');
        String base64 = GOLDEN_PUBLIC_KEY.substring(0, space);
        String comment = GOLDEN_PUBLIC_KEY.substring(space + 1, GOLDEN_PUBLIC_KEY.length() - 1); // strip \0
        byte[] original = Base64.getDecoder().decode(base64);
        T.eq("original length", AdbPublicKey.STRUCT_BYTES, original.length);

        BigInteger n = AdbPublicKey.modulusOf(original);
        BigInteger e = AdbPublicKey.exponentOf(original);
        T.eq("modulus is 2048 bits", 2048, n.bitLength());
        T.eq("exponent is 65537", BigInteger.valueOf(65537), e);
        T.eqBytes("encodeStruct(n, e) == original struct", original, AdbPublicKey.encodeStruct(n, e));

        byte[] expectedPayload = GOLDEN_PUBLIC_KEY.getBytes(Charset.forName("US-ASCII"));
        T.eqBytes("authPayload == golden reference vector", expectedPayload, AdbPublicKey.authPayload(n, e, comment));
    }
}
