package com.byd.aebtoggle.core;

/**
 * Immutable picture of what the engine knows. It replaces the three public static fields
 * AebService.lastAebState / lastAdbStatus / lastPhase.
 *
 * aeb          last reading (UNKNOWN when the last read failed)
 * link         raw transport status of the last call (SUCCESS, AUTH_FAIL, TIMEOUT, CLOSED, ERROR)
 * phase        same text the old lastPhase showed: CONNECTING, DISCOVERING, VERIFY_BOOT (1000ms), SETTLING, STEADY, DEGRADED (30s), STOPPED ...
 * observedAtMs Clock time at which the last SUCCESSFUL read completed; -1 = never
 * sequence     grows by one with every published snapshot
 */
public final class Snapshot {
    public final Aeb aeb;
    public final LinkStatus link;
    public final String phase;
    public final long observedAtMs;
    public final long sequence;

    public Snapshot(Aeb aeb, LinkStatus link, String phase, long observedAtMs, long sequence) {
        this.aeb = aeb;
        this.link = link;
        this.phase = phase;
        this.observedAtMs = observedAtMs;
        this.sequence = sequence;
    }

    /** State before the engine has started (old defaults: -1, CLOSED, "IDLE"). */
    public static Snapshot initial() {
        return new Snapshot(Aeb.UNKNOWN, LinkStatus.CLOSED, "IDLE", -1L, 0L);
    }

    /** True when a successful read exists and it is not older than maxAgeMs at nowMs. */
    public boolean isFresh(long nowMs, long maxAgeMs) {
        return observedAtMs >= 0 && nowMs >= observedAtMs && nowMs - observedAtMs <= maxAgeMs;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Snapshot)) return false;
        Snapshot s = (Snapshot) o;
        return aeb == s.aeb && link == s.link && phase.equals(s.phase)
                && observedAtMs == s.observedAtMs && sequence == s.sequence;
    }

    @Override
    public int hashCode() {
        int h = aeb.hashCode();
        h = h * 31 + link.hashCode();
        h = h * 31 + phase.hashCode();
        h = h * 31 + (int) (observedAtMs ^ (observedAtMs >>> 32));
        h = h * 31 + (int) (sequence ^ (sequence >>> 32));
        return h;
    }

    @Override
    public String toString() {
        return "Snapshot{" + aeb + "," + link + ",\"" + phase + "\",observedAt=" + observedAtMs + ",#" + sequence + "}";
    }
}
