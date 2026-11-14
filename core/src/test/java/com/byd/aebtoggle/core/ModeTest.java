package com.byd.aebtoggle.core;

final class ModeTest {
    private ModeTest() {
    }

    static void run() {
        T.section("Mode: values and labels");
        T.eq("No Action = 0", Integer.valueOf(0), Integer.valueOf(Mode.NO_ACTION));
        T.eq("Once on Start = 1", Integer.valueOf(1), Integer.valueOf(Mode.ONCE_ON_START));
        T.eq("Auto Disable = 2", Integer.valueOf(2), Integer.valueOf(Mode.AUTO_DISABLE));
        T.eq("label 0", "No Action", Mode.label(0));
        T.eq("label 1", "Once on Start", Mode.label(1));
        T.eq("label 2", "Auto Disable", Mode.label(2));
        T.eq("label of an invalid value", "invalid(7)", Mode.label(7));
        T.isTrue("isValid -1/3 false, 0..2 true",
                !Mode.isValid(-1) && Mode.isValid(0) && Mode.isValid(1) && Mode.isValid(2) && !Mode.isValid(3));
    }
}
