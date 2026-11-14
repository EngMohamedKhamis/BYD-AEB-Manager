package com.byd.aebtoggle.core;

import java.util.Arrays;

/** S6: the per-capability result of "Grant Permission". */
final class ProvisioningReportTest {
    private ProvisioningReportTest() {
    }

    static void run() {
        T.section("ProvisioningReport: command index -> capability (v18.22 list of 10 commands)");
        ProvisioningReport.Capability[] expected = {
                ProvisioningReport.Capability.OVERLAY, ProvisioningReport.Capability.OVERLAY, ProvisioningReport.Capability.OVERLAY,
                ProvisioningReport.Capability.BACKGROUND, ProvisioningReport.Capability.BACKGROUND,
                ProvisioningReport.Capability.BACKGROUND, ProvisioningReport.Capability.BACKGROUND,
                ProvisioningReport.Capability.DOZE, ProvisioningReport.Capability.DOZE,
                ProvisioningReport.Capability.STANDBY};
        for (int i = 0; i < expected.length; i++) {
            T.eq("command " + i, expected[i], ProvisioningReport.forCommandIndex(i));
        }

        T.section("ProvisioningReport: outcomes");
        ProvisioningReport r = new ProvisioningReport();
        T.eq("nothing run: overlay not tried", ProvisioningReport.Outcome.NOT_TRIED, r.outcome(ProvisioningReport.Capability.OVERLAY));
        T.isTrue("nothing run: not all delivered", !r.allDelivered());

        r.record(ProvisioningReport.Capability.OVERLAY, LinkStatus.SUCCESS);
        r.record(ProvisioningReport.Capability.OVERLAY, LinkStatus.TIMEOUT);
        r.record(ProvisioningReport.Capability.OVERLAY, LinkStatus.SUCCESS);
        T.eq("one failed command fails the capability", ProvisioningReport.Outcome.FAILED, r.outcome(ProvisioningReport.Capability.OVERLAY));

        for (ProvisioningReport.Capability c : ProvisioningReport.Capability.values()) {
            if (c != ProvisioningReport.Capability.OVERLAY) r.record(c, LinkStatus.SUCCESS);
        }
        T.eq("all commands delivered", ProvisioningReport.Outcome.DELIVERED, r.outcome(ProvisioningReport.Capability.DOZE));
        r.leftUntouched(ProvisioningReport.Capability.ACCESSIBILITY, "unexpected list format");
        r.record(ProvisioningReport.Capability.ACCESSIBILITY, LinkStatus.SUCCESS);
        T.eq("left untouched stays untouched", ProvisioningReport.Outcome.LEFT_UNTOUCHED, r.outcome(ProvisioningReport.Capability.ACCESSIBILITY));

        T.eq("lines: fixed order, one per capability", Arrays.asList(
                "FAIL Overlay (HUD) (TIMEOUT)",
                "OK   Background running",
                "OK   Battery optimisation exemption",
                "OK   App standby bucket",
                "SKIP Accessibility keep-alive: unexpected list format"), r.lines());

        ProvisioningReport fast = new ProvisioningReport();
        fast.record(ProvisioningReport.Capability.OVERLAY, LinkStatus.AUTH_FAIL);
        T.eq("fast fail: the rest is 'not tried'", "--   App standby bucket: not tried", fast.lines().get(3));
        T.isTrue("fast fail: not all delivered", !fast.allDelivered());

        ProvisioningReport all = new ProvisioningReport();
        for (ProvisioningReport.Capability c : ProvisioningReport.Capability.values()) all.record(c, LinkStatus.SUCCESS);
        T.isTrue("everything delivered", all.allDelivered());
    }
}
