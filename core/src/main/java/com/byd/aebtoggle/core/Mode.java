package com.byd.aebtoggle.core;

/** The three protection modes (stored as "protection_mode"). Behaviour is decision D2 and must not change. */
public final class Mode {
    /** Only report. No automatic write. */
    public static final int NO_ACTION = 0;
    /** One automatic disable per service lifetime, right after the first stable reading. */
    public static final int ONCE_ON_START = 1;
    /** Disable at start and again whenever the driver turns AEB back on. */
    public static final int AUTO_DISABLE = 2;

    private Mode() {
    }

    public static boolean isValid(int mode) {
        return mode >= NO_ACTION && mode <= AUTO_DISABLE;
    }

    /** The name shown on the mode buttons (used in the event log, S3). */
    public static String label(int mode) {
        switch (mode) {
            case NO_ACTION:
                return "No Action";
            case ONCE_ON_START:
                return "Once on Start";
            case AUTO_DISABLE:
                return "Auto Disable";
            default:
                return "invalid(" + mode + ")";
        }
    }
}
