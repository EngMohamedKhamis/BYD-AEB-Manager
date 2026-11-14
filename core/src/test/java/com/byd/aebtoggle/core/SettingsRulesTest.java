package com.byd.aebtoggle.core;

final class SettingsRulesTest {
    private SettingsRulesTest() {
    }

    static void run() {
        T.section("SettingsRules: defaults");
        T.eq("start_on_boot default", Boolean.TRUE, Boolean.valueOf(SettingsRules.DEFAULT_START_ON_BOOT));
        T.eq("show_floating default is true everywhere (S1 change b)", Boolean.TRUE, Boolean.valueOf(SettingsRules.DEFAULT_SHOW_FLOATING));
        T.eq("protection_mode default is Once on Start", Integer.valueOf(Mode.ONCE_ON_START), Integer.valueOf(SettingsRules.DEFAULT_MODE));

        T.section("SettingsRules: mode (same rule as AebService v18.22 readMode)");
        T.eq("missing = Once on Start", Integer.valueOf(1), Integer.valueOf(SettingsRules.mode(null)));
        T.eq("0 stays No Action", Integer.valueOf(0), Integer.valueOf(SettingsRules.mode(Integer.valueOf(0))));
        T.eq("1 stays Once on Start", Integer.valueOf(1), Integer.valueOf(SettingsRules.mode(Integer.valueOf(1))));
        T.eq("2 stays Auto Disable", Integer.valueOf(2), Integer.valueOf(SettingsRules.mode(Integer.valueOf(2))));
        T.eq("-1 = No Action", Integer.valueOf(0), Integer.valueOf(SettingsRules.mode(Integer.valueOf(-1))));
        T.eq("3 = No Action (S1 change a: the screen no longer shows Once on Start)", Integer.valueOf(0), Integer.valueOf(SettingsRules.mode(Integer.valueOf(3))));
        T.eq("MAX_VALUE = No Action", Integer.valueOf(0), Integer.valueOf(SettingsRules.mode(Integer.valueOf(Integer.MAX_VALUE))));
        T.eq("String \"1\" (wrong type) = No Action", Integer.valueOf(0), Integer.valueOf(SettingsRules.mode("1")));
        T.eq("Long 1 (wrong type) = No Action", Integer.valueOf(0), Integer.valueOf(SettingsRules.mode(Long.valueOf(1))));
        T.eq("Boolean (wrong type) = No Action", Integer.valueOf(0), Integer.valueOf(SettingsRules.mode(Boolean.TRUE)));
        boolean allValid = true;
        for (int i = -5; i <= 5; i++) {
            if (!Mode.isValid(SettingsRules.mode(Integer.valueOf(i)))) allValid = false;
        }
        T.isTrue("every stored int from -5 to 5 maps to a valid mode", allValid);
        T.eq("a write can only happen for 1 or 2: 7 maps to No Action, never to a writing mode",
                Integer.valueOf(Mode.NO_ACTION), Integer.valueOf(SettingsRules.mode(Integer.valueOf(7))));

        T.section("SettingsRules: flag");
        T.eq("missing, default true", Boolean.TRUE, Boolean.valueOf(SettingsRules.flag(null, true)));
        T.eq("missing, default false", Boolean.FALSE, Boolean.valueOf(SettingsRules.flag(null, false)));
        T.eq("stored true wins over default false", Boolean.TRUE, Boolean.valueOf(SettingsRules.flag(Boolean.TRUE, false)));
        T.eq("stored false wins over default true", Boolean.FALSE, Boolean.valueOf(SettingsRules.flag(Boolean.FALSE, true)));
        T.eq("String \"false\" (wrong type) = default", Boolean.TRUE, Boolean.valueOf(SettingsRules.flag("false", true)));
        T.eq("Integer 0 (wrong type) = default", Boolean.TRUE, Boolean.valueOf(SettingsRules.flag(Integer.valueOf(0), true)));

        T.section("SettingsRules: number");
        T.eq("missing = default", Integer.valueOf(100), Integer.valueOf(SettingsRules.number(null, 100)));
        T.eq("stored value", Integer.valueOf(5), Integer.valueOf(SettingsRules.number(Integer.valueOf(5), 0)));
        T.eq("negative stored value", Integer.valueOf(-20), Integer.valueOf(SettingsRules.number(Integer.valueOf(-20), 0)));
        T.eq("String (wrong type) = default", Integer.valueOf(7), Integer.valueOf(SettingsRules.number("5", 7)));
        T.eq("Long (wrong type) = default", Integer.valueOf(7), Integer.valueOf(SettingsRules.number(Long.valueOf(5), 7)));

        T.section("SettingsRules: migrateLegacy (table of MainActivity v18.22)");
        T.eq("disable_aeb false, old 0 = No Action", Integer.valueOf(0), Integer.valueOf(SettingsRules.migrateLegacy(Boolean.FALSE, Integer.valueOf(0))));
        T.eq("disable_aeb false, old 1 = No Action", Integer.valueOf(0), Integer.valueOf(SettingsRules.migrateLegacy(Boolean.FALSE, Integer.valueOf(1))));
        T.eq("disable_aeb true, old 0 (startup only) = Once on Start", Integer.valueOf(1), Integer.valueOf(SettingsRules.migrateLegacy(Boolean.TRUE, Integer.valueOf(0))));
        T.eq("disable_aeb true, old 1 (continuous) = Auto Disable", Integer.valueOf(2), Integer.valueOf(SettingsRules.migrateLegacy(Boolean.TRUE, Integer.valueOf(1))));
        T.eq("disable_aeb true, old mode missing (old default 1) = Auto Disable", Integer.valueOf(2), Integer.valueOf(SettingsRules.migrateLegacy(Boolean.TRUE, null)));
        T.eq("disable_aeb true, old mode 5 = Auto Disable (old table: anything but 0)", Integer.valueOf(2), Integer.valueOf(SettingsRules.migrateLegacy(Boolean.TRUE, Integer.valueOf(5))));
        T.eq("disable_aeb wrong type (old default true), old 0 = Once on Start", Integer.valueOf(1), Integer.valueOf(SettingsRules.migrateLegacy("x", Integer.valueOf(0))));
        T.eq("disable_aeb missing, old 0 = Once on Start", Integer.valueOf(1), Integer.valueOf(SettingsRules.migrateLegacy(null, Integer.valueOf(0))));
        T.eq("both missing = Auto Disable", Integer.valueOf(2), Integer.valueOf(SettingsRules.migrateLegacy(null, null)));
    }
}
