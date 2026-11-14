package com.byd.aebtoggle.core;

/**
 * What the screen and the HUD show for one snapshot (refactor S2). Pure, so the display rules are
 * tested instead of being spread over view code.
 *
 * Decision D7 (owner, 2026-11-05): the state is shown as "AEB ON" / "AEB OFF" (no SECURED/HAZARD,
 * no word "reported"); a reading older than FRESH_MS is "unverified" and shown in a neutral colour.
 */
public final class StatusView {

    /** A reading older than this is unverified (STEADY reads every 10 s, so a live link is always fresher). */
    public static final long FRESH_MS = 15_000;

    public enum Kind {
        /** Fresh reading: AEB is off. */
        OFF,
        /** Fresh reading: AEB is on. */
        ON,
        /** Last reading said off, but it is older than FRESH_MS (sleep, DEGRADED wait, stalled link). */
        UNVERIFIED_OFF,
        /** Last reading said on, but it is older than FRESH_MS. */
        UNVERIFIED_ON,
        /** No reading; the car refused our key (AUTH_FAIL). */
        NEEDS_PERMISSION,
        /** No reading; the connection timed out. */
        TIMEOUT,
        /** No reading yet (connecting, closed, error, unreadable reply, stopped). */
        WAITING
    }

    /**
     * S5 (owner decision D6, 2026-11-05: no firmware gate; report problems instead). Shown on top of
     * the kind, in the attention colour, on the screen and the HUD.
     */
    public enum Attention {
        NONE,
        /** The engine gave up for now (3 failed writes or failed verification) and waits 30 s - 5 min. */
        DEGRADED,
        /** A reading exists but the last call to the car (a write) failed; the engine is reconnecting. */
        LAST_CALL_FAILED
    }

    public final Kind kind;
    public final Attention attention;
    /** Transport status of the last call (for the LAST_CALL_FAILED text). */
    public final LinkStatus link;
    /** Whole seconds since the last good read; -1 = never (or the clock is behind it). */
    public final long ageSeconds;
    /** "Disable AEB Now" is offered only for a fresh ON reading. */
    public final boolean canDisableNow;
    /** There is an AEB reading (v18.22: lastAebState != -1). */
    public final boolean linkUp;
    /** There is a reading AND the last call succeeded (v18.22: lastAdbStatus == SUCCESS && lastAebState != -1). */
    public final boolean authorized;

    private StatusView(Kind kind, Attention attention, LinkStatus link, long ageSeconds, boolean canDisableNow,
                       boolean linkUp, boolean authorized) {
        this.kind = kind;
        this.attention = attention;
        this.link = link;
        this.ageSeconds = ageSeconds;
        this.canDisableNow = canDisableNow;
        this.linkUp = linkUp;
        this.authorized = authorized;
    }

    /** nowMs must come from the same clock as the engine (Android: elapsedRealtime). */
    public static StatusView of(Snapshot s, long nowMs) {
        long age = (s.observedAtMs >= 0 && nowMs >= s.observedAtMs) ? (nowMs - s.observedAtMs) / 1000 : -1;
        boolean linkUp = s.aeb != Aeb.UNKNOWN;
        Kind kind;
        if (!linkUp) {
            if (s.link == LinkStatus.AUTH_FAIL) kind = Kind.NEEDS_PERMISSION;
            else if (s.link == LinkStatus.TIMEOUT) kind = Kind.TIMEOUT;
            else kind = Kind.WAITING;
        } else if (s.isFresh(nowMs, FRESH_MS)) {
            kind = s.aeb == Aeb.ON ? Kind.ON : Kind.OFF;
        } else {
            kind = s.aeb == Aeb.ON ? Kind.UNVERIFIED_ON : Kind.UNVERIFIED_OFF;
        }
        Attention attention;
        if (s.phase.startsWith("DEGRADED")) attention = Attention.DEGRADED;
        else if (linkUp && s.link != LinkStatus.SUCCESS) attention = Attention.LAST_CALL_FAILED;
        else attention = Attention.NONE;
        return new StatusView(kind, attention, s.link, age, kind == Kind.ON, linkUp, linkUp && s.link == LinkStatus.SUCCESS);
    }

    /** The HUD and the card use the attention colour: a problem is reported, or the car refused the key. */
    public boolean needsAttention() {
        return attention != Attention.NONE || kind == Kind.NEEDS_PERMISSION;
    }

    @Override
    public String toString() {
        return "StatusView{" + kind + "," + attention + ",age=" + ageSeconds + ",disableNow=" + canDisableNow
                + ",linkUp=" + linkUp + ",authorized=" + authorized + "}";
    }
}
