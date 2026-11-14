package com.byd.aebtoggle.core;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.util.Base64;

/**
 * ADB's RSAPublicKey structure (what AUTH_RSAPUBLICKEY carries), computed from a modulus and an
 * exponent. Public data only: this class never sees a private key.
 *
 * Layout, little endian, 524 bytes for a 2048-bit key:
 *   uint32 words (=64) | uint32 n0inv (= -1/n mod 2^32) | 256 bytes modulus | 256 bytes rr (= 2^4096 mod n) | uint32 exponent
 *
 * It exists now so that a later stage can generate a key per installation; its output is checked
 * against the constant AdbHelper.PUBLIC_KEY (see AdbPublicKeyTest), so no private key is needed to test it.
 */
public final class AdbPublicKey {
    public static final int KEY_BITS = 2048;
    public static final int MODULUS_BYTES = KEY_BITS / 8; // 256
    public static final int WORDS = KEY_BITS / 32; // 64
    public static final int STRUCT_BYTES = 4 + 4 + MODULUS_BYTES + MODULUS_BYTES + 4; // 524

    private AdbPublicKey() {
    }

    public static byte[] encodeStruct(BigInteger modulus, BigInteger exponent) {
        if (modulus == null || exponent == null) throw new IllegalArgumentException("null key part");
        if (modulus.bitLength() != KEY_BITS) throw new IllegalArgumentException("modulus must be exactly 2048 bits");
        if (!modulus.testBit(0)) throw new IllegalArgumentException("modulus must be odd");
        if (exponent.signum() <= 0 || exponent.bitLength() > 32) throw new IllegalArgumentException("bad exponent");

        BigInteger two32 = BigInteger.ONE.shiftLeft(32);
        BigInteger inverse = modulus.mod(two32).modInverse(two32); // n^-1 mod 2^32
        BigInteger n0inv = two32.subtract(inverse).mod(two32); // -n^-1 mod 2^32
        BigInteger rr = BigInteger.ONE.shiftLeft(KEY_BITS * 2).mod(modulus); // (2^2048)^2 mod n

        ByteBuffer b = ByteBuffer.allocate(STRUCT_BYTES).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(WORDS);
        b.putInt(n0inv.intValue()); // low 32 bits = the unsigned value
        b.put(toLittleEndian(modulus, MODULUS_BYTES));
        b.put(toLittleEndian(rr, MODULUS_BYTES));
        b.putInt(exponent.intValue());
        return b.array();
    }

    /** Reads the modulus back out of an encoded structure. Throws IllegalArgumentException if it is not one. */
    public static BigInteger modulusOf(byte[] struct) {
        check(struct);
        return fromLittleEndian(struct, 8, MODULUS_BYTES);
    }

    public static BigInteger exponentOf(byte[] struct) {
        check(struct);
        return fromLittleEndian(struct, STRUCT_BYTES - 4, 4);
    }

    /** The AUTH_RSAPUBLICKEY payload: Base64(struct) + " " + comment + NUL (comment is user@host style text). */
    public static byte[] authPayload(BigInteger modulus, BigInteger exponent, String comment) {
        String text = Base64.getEncoder().encodeToString(encodeStruct(modulus, exponent)) + " " + comment + "\0";
        return text.getBytes(Charset.forName("US-ASCII"));
    }

    private static void check(byte[] struct) {
        if (struct == null || struct.length != STRUCT_BYTES) throw new IllegalArgumentException("not a 524 byte ADB key");
        int words = ByteBuffer.wrap(struct).order(ByteOrder.LITTLE_ENDIAN).getInt(0);
        if (words != WORDS) throw new IllegalArgumentException("not a 2048-bit ADB key");
    }

    private static byte[] toLittleEndian(BigInteger value, int length) {
        byte[] bigEndian = value.toByteArray(); // may carry a leading sign byte
        byte[] out = new byte[length];
        for (int i = 0; i < length; i++) {
            int src = bigEndian.length - 1 - i; // least significant byte first
            out[i] = src >= 0 ? bigEndian[src] : 0;
        }
        return out;
    }

    private static BigInteger fromLittleEndian(byte[] data, int offset, int length) {
        byte[] bigEndian = new byte[length];
        for (int i = 0; i < length; i++) bigEndian[length - 1 - i] = data[offset + i];
        return new BigInteger(1, bigEndian);
    }
}
