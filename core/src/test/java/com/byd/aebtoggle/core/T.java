package com.byd.aebtoggle.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Minimal assertion helper. No JUnit, no dependencies. Prints section titles and failures only. */
final class T {
    static int checks = 0;
    static int failures = 0;
    private static String section = "";

    private T() {
    }

    interface Body {
        void run() throws Exception;
    }

    static void section(String name) {
        section = name;
        System.out.println("== " + name);
    }

    static void isTrue(String what, boolean condition) {
        checks++;
        if (!condition) {
            failures++;
            System.out.println("[FAIL] " + section + ": " + what);
        }
    }

    static void eq(String what, Object expected, Object actual) {
        checks++;
        boolean same = expected == null ? actual == null : expected.equals(actual);
        if (!same) {
            failures++;
            System.out.println("[FAIL] " + section + ": " + what);
            System.out.println("         expected: " + expected);
            System.out.println("         actual:   " + actual);
        }
    }

    static void eqBytes(String what, byte[] expected, byte[] actual) {
        checks++;
        if (!Arrays.equals(expected, actual)) {
            failures++;
            System.out.println("[FAIL] " + section + ": " + what);
            System.out.println("         expected: " + toHex(expected));
            System.out.println("         actual:   " + toHex(actual));
        }
    }

    /** The body must throw something that is an instance of type. */
    static void throwsA(String what, Class<? extends Throwable> type, Body body) {
        checks++;
        try {
            body.run();
        } catch (Throwable t) {
            if (!type.isInstance(t)) {
                failures++;
                System.out.println("[FAIL] " + section + ": " + what + " threw " + t + " instead of " + type.getSimpleName());
            }
            return;
        }
        failures++;
        System.out.println("[FAIL] " + section + ": " + what + " did not throw " + type.getSimpleName());
    }

    /** "43 4E 58" or "434E58" to bytes. */
    static byte[] hex(String text) {
        String s = text.replace(" ", "");
        byte[] out = new byte[s.length() / 2];
        for (int i = 0; i < out.length; i++) out[i] = (byte) Integer.parseInt(s.substring(2 * i, 2 * i + 2), 16);
        return out;
    }

    static String toHex(byte[] b) {
        if (b == null) return "null";
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02X ", x & 0xFF));
        return sb.toString().trim();
    }

    /** Long list literal: L(0, 3000, 6000). */
    static List<Long> L(long... values) {
        List<Long> out = new ArrayList<Long>();
        for (long v : values) out.add(Long.valueOf(v));
        return out;
    }
}
