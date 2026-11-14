package com.byd.aebtoggle.core;

final class StatusViewTest {
    private StatusViewTest() {
    }

    private static Snapshot snap(Aeb aeb, LinkStatus link, long observedAtMs) {
        return new Snapshot(aeb, link, "STEADY", observedAtMs, 1);
    }

    private static StatusView.Kind kind(Aeb aeb, LinkStatus link, long observedAtMs, long nowMs) {
        return StatusView.of(snap(aeb, link, observedAtMs), nowMs).kind;
    }

    static void run() {
        T.section("StatusView: no reading");
        StatusView init = StatusView.of(Snapshot.initial(), 5000);
        T.eq("initial snapshot = WAITING", StatusView.Kind.WAITING, init.kind);
        T.eq("initial: age -1", Long.valueOf(-1), Long.valueOf(init.ageSeconds));
        T.isTrue("initial: no Disable Now, not linked, not authorized", !init.canDisableNow && !init.linkUp && !init.authorized);
        T.eq("AUTH_FAIL = NEEDS_PERMISSION", StatusView.Kind.NEEDS_PERMISSION, kind(Aeb.UNKNOWN, LinkStatus.AUTH_FAIL, -1, 0));
        T.eq("TIMEOUT = TIMEOUT", StatusView.Kind.TIMEOUT, kind(Aeb.UNKNOWN, LinkStatus.TIMEOUT, -1, 0));
        T.eq("CLOSED = WAITING", StatusView.Kind.WAITING, kind(Aeb.UNKNOWN, LinkStatus.CLOSED, -1, 0));
        T.eq("ERROR = WAITING", StatusView.Kind.WAITING, kind(Aeb.UNKNOWN, LinkStatus.ERROR, -1, 0));
        T.eq("unreadable reply (UNKNOWN + SUCCESS) = WAITING", StatusView.Kind.WAITING, kind(Aeb.UNKNOWN, LinkStatus.SUCCESS, -1, 0));
        T.eq("a failed read after good ones = no reading, even with an old observedAt", StatusView.Kind.TIMEOUT, kind(Aeb.UNKNOWN, LinkStatus.TIMEOUT, 1000, 2000));
        StatusView stopped = StatusView.of(new Snapshot(Aeb.UNKNOWN, LinkStatus.CLOSED, "STOPPED", -1, 9), 100_000);
        T.eq("STOPPED = WAITING", StatusView.Kind.WAITING, stopped.kind);

        T.section("StatusView: fresh readings (D7: AEB ON / AEB OFF)");
        StatusView off = StatusView.of(snap(Aeb.OFF, LinkStatus.SUCCESS, 1000), 1000);
        T.eq("OFF just read", StatusView.Kind.OFF, off.kind);
        T.eq("OFF: age 0", Long.valueOf(0), Long.valueOf(off.ageSeconds));
        T.isTrue("OFF: no Disable Now, linked, authorized", !off.canDisableNow && off.linkUp && off.authorized);
        StatusView on = StatusView.of(snap(Aeb.ON, LinkStatus.SUCCESS, 1000), 16_000);
        T.eq("ON read exactly 15 s ago is still fresh", StatusView.Kind.ON, on.kind);
        T.eq("ON: age 15", Long.valueOf(15), Long.valueOf(on.ageSeconds));
        T.isTrue("ON: Disable Now offered", on.canDisableNow);
        StatusView onFailedWrite = StatusView.of(snap(Aeb.ON, LinkStatus.TIMEOUT, 1000), 2000);
        T.eq("ON with a failed write afterwards stays ON", StatusView.Kind.ON, onFailedWrite.kind);
        T.isTrue("ON + failed write: linked but not authorized (grant button glows, as in v18.22)", onFailedWrite.linkUp && !onFailedWrite.authorized);
        T.isTrue("ON + failed write: Disable Now still offered (as in v18.22)", onFailedWrite.canDisableNow);

        T.section("StatusView: unverified (older than 15 s)");
        StatusView staleOn = StatusView.of(snap(Aeb.ON, LinkStatus.SUCCESS, 1000), 16_001);
        T.eq("ON read 15.001 s ago = UNVERIFIED_ON", StatusView.Kind.UNVERIFIED_ON, staleOn.kind);
        T.isTrue("UNVERIFIED_ON: no Disable Now", !staleOn.canDisableNow);
        T.isTrue("UNVERIFIED_ON: still linked (protection row stays usable)", staleOn.linkUp);
        StatusView staleOff = StatusView.of(snap(Aeb.OFF, LinkStatus.SUCCESS, 0), 47_900);
        T.eq("OFF read 47.9 s ago = UNVERIFIED_OFF", StatusView.Kind.UNVERIFIED_OFF, staleOff.kind);
        T.eq("age is whole seconds, rounded down", Long.valueOf(47), Long.valueOf(staleOff.ageSeconds));
        StatusView behind = StatusView.of(snap(Aeb.OFF, LinkStatus.SUCCESS, 5000), 4000);
        T.eq("clock behind the reading = unverified, never fresh", StatusView.Kind.UNVERIFIED_OFF, behind.kind);
        T.eq("clock behind the reading: age -1", Long.valueOf(-1), Long.valueOf(behind.ageSeconds));
        T.eq("a DEGRADED wait of 30 s after an ON reading = UNVERIFIED_ON", StatusView.Kind.UNVERIFIED_ON,
                kind(Aeb.ON, LinkStatus.SUCCESS, 10_000, 40_000));
    }
}
