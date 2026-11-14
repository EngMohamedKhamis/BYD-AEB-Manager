package com.byd.aebtoggle.core;

/** S5: "needs attention" reporting (owner decision D6: no firmware gate, report problems instead). */
final class StatusAttentionTest {
    private StatusAttentionTest() {
    }

    private static StatusView v(Aeb aeb, LinkStatus link, String phase) {
        return StatusView.of(new Snapshot(aeb, link, phase, 1000, 1), 2000);
    }

    static void run() {
        T.section("StatusView attention: normal operation needs none");
        StatusView ok = v(Aeb.OFF, LinkStatus.SUCCESS, "STEADY");
        T.eq("STEADY, good read: NONE", StatusView.Attention.NONE, ok.attention);
        T.isTrue("STEADY, good read: no attention colour", !ok.needsAttention());
        T.eq("initial snapshot: NONE", StatusView.Attention.NONE, StatusView.of(Snapshot.initial(), 0).attention);
        T.eq("CONNECTING without reading: NONE", StatusView.Attention.NONE, v(Aeb.UNKNOWN, LinkStatus.CLOSED, "CONNECTING").attention);
        T.eq("a failed read (no reading left): NONE, the kind already says it",
                StatusView.Attention.NONE, v(Aeb.UNKNOWN, LinkStatus.TIMEOUT, "CONNECTING").attention);
        T.isTrue("TIMEOUT without reading: no attention colour (ADB unreachable = app does nothing)",
                !v(Aeb.UNKNOWN, LinkStatus.TIMEOUT, "CONNECTING").needsAttention());

        T.section("StatusView attention: problems are reported");
        StatusView deg = v(Aeb.ON, LinkStatus.ERROR, "DEGRADED (30s)");
        T.eq("DEGRADED phase: DEGRADED", StatusView.Attention.DEGRADED, deg.attention);
        T.isTrue("DEGRADED: attention colour", deg.needsAttention());
        T.eq("DEGRADED wins over a failed call", StatusView.Attention.DEGRADED, deg.attention);
        T.eq("DEGRADED (300s) too", StatusView.Attention.DEGRADED, v(Aeb.UNKNOWN, LinkStatus.TIMEOUT, "DEGRADED (300s)").attention);
        T.eq("DEGRADED with a good last read too", StatusView.Attention.DEGRADED, v(Aeb.OFF, LinkStatus.SUCCESS, "DEGRADED (60s)").attention);

        StatusView failed = v(Aeb.ON, LinkStatus.TIMEOUT, "CONNECTING");
        T.eq("reading kept, last call failed: LAST_CALL_FAILED", StatusView.Attention.LAST_CALL_FAILED, failed.attention);
        T.eq("the failed status is carried for the text", LinkStatus.TIMEOUT, failed.link);
        T.eq("the kind is unchanged by attention", StatusView.Kind.ON, failed.kind);
        T.isTrue("Disable Now is still offered for a fresh ON", failed.canDisableNow);
        T.eq("AUTH_FAIL after a reading: LAST_CALL_FAILED", StatusView.Attention.LAST_CALL_FAILED, v(Aeb.OFF, LinkStatus.AUTH_FAIL, "RECOVERY_ENFORCE").attention);

        StatusView perm = v(Aeb.UNKNOWN, LinkStatus.AUTH_FAIL, "CONNECTING");
        T.eq("refused key without reading: kind NEEDS_PERMISSION, attention NONE", StatusView.Attention.NONE, perm.attention);
        T.isTrue("refused key: attention colour", perm.needsAttention());
    }
}
