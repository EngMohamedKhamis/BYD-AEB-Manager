package com.byd.aebtoggle.core;

/**
 * How stored settings are interpreted - one set of rules for the service, the screen, the boot
 * receiver and the HUD (until v18.22 each of them had its own, see REFACTOR_PLAN.md fact F4).
 *
 * Pure: the Android side (AebSettings) only fetches the raw stored value (null when the key is
 * missing) and passes it here. A value of the wrong type never throws; it is treated as below.
 */
public final class SettingsRules {

    /** "start_on_boot" when missing (unchanged: BootReceiver and the screen both used true). */
    public static final boolean DEFAULT_START_ON_BOOT = true;
    /** "show_floating" when missing. v18.22: true in the screen's defaults, false in two reads (S1 change b). */
    public static final boolean DEFAULT_SHOW_FLOATING = true;
    /** "protection_mode" when missing. */
    public static final int DEFAULT_MODE = Mode.ONCE_ON_START;

    private SettingsRules() {
    }

    /**
     * The effective protection mode. Identical to AebService v18.22 readMode(): missing = Once on
     * Start; a stored number outside 0..2, or a value that is not an Integer, = No Action.
     * (v18.22's screen showed an out-of-range value as Once on Start; S1 change a.)
     */
    public static int mode(Object stored) {
        if (stored == null) return DEFAULT_MODE;
        if (!(stored instanceof Integer)) return Mode.NO_ACTION;
        int m = ((Integer) stored).intValue();
        return Mode.isValid(m) ? m : Mode.NO_ACTION;
    }

    /** A boolean setting: missing, or not a Boolean = the default. */
    public static boolean flag(Object stored, boolean defaultValue) {
        return stored instanceof Boolean ? ((Boolean) stored).booleanValue() : defaultValue;
    }

    /** An int setting (HUD position): missing, or not an Integer = the default. */
    public static int number(Object stored, int defaultValue) {
        return stored instanceof Integer ? ((Integer) stored).intValue() : defaultValue;
    }

    /**
     * Migration of the old two-key system ("disable_aeb" + "protection_mode" where 0 = startup only,
     * 1 = continuous) to today's mode. Same table as MainActivity v18.22 loadSettings():
     * disable_aeb false = No Action; else old mode 0 = Once on Start; else Auto Disable.
     * Missing or wrong-type values take the old defaults (disable_aeb true, old mode 1).
     */
    public static int migrateLegacy(Object disableAeb, Object oldMode) {
        boolean disable = flag(disableAeb, true);
        int old = number(oldMode, 1);
        if (!disable) return Mode.NO_ACTION;
        if (old == 0) return Mode.ONCE_ON_START;
        return Mode.AUTO_DISABLE;
    }

    /** Legitimate system / vendor boot broadcast actions accepted by BootReceiver (v18.10 hardening). */
    public static boolean isBootBroadcastAction(String action) {
        return "android.intent.action.BOOT_COMPLETED".equals(action)
                || "android.intent.action.LOCKED_BOOT_COMPLETED".equals(action)
                || "android.intent.action.QUICKBOOT_POWERON".equals(action)
                || "com.htc.intent.action.QUICKBOOT_POWERON".equals(action);
    }

    /** True when a boot/ignition signal arrives within DUPLICATE_IGNITION_WINDOW_MS of an active session. */
    public static boolean isDuplicateBootSignal(long lastSignalMs, long nowMs) {
        return lastSignalMs >= 0 && nowMs >= lastSignalMs
                && (nowMs - lastSignalMs) < Engine.DUPLICATE_IGNITION_WINDOW_MS;
    }

    /**
     * True when a boot/ignition broadcast received while Start-on-Boot is OFF (or when starting
     * AebService threw with no attached engine) represents a genuine new trip rather than:
     *  - a duplicate broadcast within DUPLICATE_IGNITION_WINDOW_MS of a just-handled offline boot, or
     *  - a delayed cold-boot broadcast arriving within DUPLICATE_IGNITION_WINDOW_MS of an engine
     *    already started manually in this boot session.
     */
    public static boolean shouldResetOfflineBoot(long engineAttachedAtMs, long lastOfflineBootMs, long nowMs) {
        return !isDuplicateBootSignal(engineAttachedAtMs, nowMs)
                && !isDuplicateBootSignal(lastOfflineBootMs, nowMs);
    }
}
