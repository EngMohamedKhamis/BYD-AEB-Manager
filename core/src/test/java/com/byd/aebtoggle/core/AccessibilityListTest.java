package com.byd.aebtoggle.core;

final class AccessibilityListTest {
    private static final String OURS = "com.mk.aebmanager/com.byd.aebtoggle.AebAccessibilityService";

    private AccessibilityListTest() {
    }

    static void run() {
        T.section("AccessibilityList: isSafe");
        T.isTrue("empty", AccessibilityList.isSafe(""));
        T.isTrue("literal null", AccessibilityList.isSafe("null"));
        T.isTrue("one component", AccessibilityList.isSafe("a.b/a.b.C"));
        T.isTrue("inner class and dash", AccessibilityList.isSafe("a.b/a.b.C$D:x-y.z/x-y.z.E"));
        T.isTrue("space is not safe", !AccessibilityList.isSafe("a b"));
        T.isTrue("quote is not safe", !AccessibilityList.isSafe("a'b"));
        T.isTrue("semicolon is not safe", !AccessibilityList.isSafe("a;reboot"));
        T.isTrue("dollar-paren is not safe", !AccessibilityList.isSafe("$(id)"));
        T.isTrue("newline is not safe", !AccessibilityList.isSafe("a\nb"));
        T.isTrue("null reference is not safe", !AccessibilityList.isSafe(null));

        T.section("AccessibilityList: withComponent");
        T.eq("empty list", OURS, AccessibilityList.withComponent("", OURS));
        T.eq("literal null list", OURS, AccessibilityList.withComponent("null", OURS));
        T.eq("surrounding whitespace is trimmed", OURS, AccessibilityList.withComponent("  \n", OURS));
        T.eq("append to one entry", "x.y/x.y.S:" + OURS, AccessibilityList.withComponent("x.y/x.y.S", OURS));
        T.eq("append to two entries", "a/a.A:b/b.B:" + OURS, AccessibilityList.withComponent("a/a.A:b/b.B", OURS));
        T.eq("already present: unchanged", "a/a.A:" + OURS, AccessibilityList.withComponent("a/a.A:" + OURS, OURS));
        T.eq("already present alone", OURS, AccessibilityList.withComponent(OURS, OURS));
        T.eq("a longer name that merely contains ours does not count",
                "x" + OURS + ":" + OURS, AccessibilityList.withComponent("x" + OURS, OURS));
        T.eq("a prefix of ours does not count", "com.mk.aebmanager/com.byd.aebtoggle.AebAccessibilityServic:" + OURS,
                AccessibilityList.withComponent("com.mk.aebmanager/com.byd.aebtoggle.AebAccessibilityServic", OURS));
        T.eq("unsafe list: do not write", null, AccessibilityList.withComponent("a/a.A;rm", OURS));
        T.eq("unsafe list with a quote: do not write", null, AccessibilityList.withComponent("a'b", OURS));
        T.eq("null list: do not write", null, AccessibilityList.withComponent(null, OURS));
        T.eq("unsafe component: do not write", null, AccessibilityList.withComponent("a/a.A", "x y"));
        T.eq("empty component: do not write", null, AccessibilityList.withComponent("a/a.A", ""));
        T.eq("trailing colon is kept as it was (pure move of the old logic)", "a/a.A::" + OURS,
                AccessibilityList.withComponent("a/a.A:", OURS));

        T.section("AccessibilityList: contains / isEmpty");
        T.isTrue("contains exact", AccessibilityList.contains("a/a.A:" + OURS, OURS));
        T.isTrue("not contained", !AccessibilityList.contains("a/a.A", OURS));
        T.isTrue("empty contains nothing", !AccessibilityList.contains("", OURS));
        T.isTrue("null contains nothing", !AccessibilityList.contains("null", OURS));
        T.isTrue("isEmpty empty", AccessibilityList.isEmpty(""));
        T.isTrue("isEmpty literal null", AccessibilityList.isEmpty("null"));
        T.isTrue("isEmpty false", !AccessibilityList.isEmpty("a/a.A"));
    }
}
