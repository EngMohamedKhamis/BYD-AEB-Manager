package com.byd.aebtoggle.core;

/** Entry point of the self-test: `gradlew :core:selfTest`. Exit code 0 only if every check passed. */
public final class RunAll {
    private RunAll() {
    }

    private interface Suite {
        void run() throws Exception;
    }

    private static void guard(String name, Suite suite) {
        try {
            suite.run();
        } catch (Throwable t) {
            T.failures++;
            System.out.println("[FAIL] suite " + name + " crashed: " + t);
            t.printStackTrace(System.out);
        }
    }

    public static void main(String[] args) {
        guard("ParcelParserTest", ParcelParserTest::run);
        guard("AccessibilityListTest", AccessibilityListTest::run);
        guard("AdbFrameTest", AdbFrameTest::run);
        guard("AdbPublicKeyTest", AdbPublicKeyTest::run);
        guard("AdbCryptoTest", AdbCryptoTest::run);
        guard("EngineTest", EngineTest::run);
        guard("SettingsRulesTest", SettingsRulesTest::run);
        guard("StatusViewTest", StatusViewTest::run);
        guard("ModeTest", ModeTest::run);
        guard("EventLogTest", EventLogTest::run);
        guard("AdbShellTest", AdbShellTest::run);
        guard("StatusAttentionTest", StatusAttentionTest::run);
        guard("ProvisioningReportTest", ProvisioningReportTest::run);
        System.out.println("SELFTEST: " + (T.failures == 0 ? "PASS" : "FAIL") + " - " + T.checks + " checks, " + T.failures + " failures");
        System.exit(T.failures == 0 ? 0 : 1);
    }
}
