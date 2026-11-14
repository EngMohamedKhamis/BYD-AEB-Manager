package com.byd.aebtoggle.core;

/**
 * Outcome of one blocking read of the AEB property. It carries the same information as
 * AdbHelper.queryAebStatus() plus AdbHelper.lastRawStatus, but in one immutable value.
 */
public final class ReadResult {
    public final Aeb aeb;
    public final LinkStatus link;

    private ReadResult(Aeb aeb, LinkStatus link) {
        this.aeb = aeb;
        this.link = link;
    }

    public static ReadResult off() {
        return new ReadResult(Aeb.OFF, LinkStatus.SUCCESS);
    }

    public static ReadResult on() {
        return new ReadResult(Aeb.ON, LinkStatus.SUCCESS);
    }

    /**
     * The transport failed, or the reply could not be understood. link is the raw transport
     * status and may itself be SUCCESS (reply received but not parseable).
     */
    public static ReadResult failed(LinkStatus link) {
        return new ReadResult(Aeb.UNKNOWN, link == null ? LinkStatus.ERROR : link);
    }

    /** True when there is no usable AEB reading (old name: TRANSPORT_FAILURE). */
    public boolean isFailure() {
        return aeb == Aeb.UNKNOWN;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ReadResult)) return false;
        ReadResult other = (ReadResult) o;
        return aeb == other.aeb && link == other.link;
    }

    @Override
    public int hashCode() {
        return aeb.hashCode() * 31 + link.hashCode();
    }

    @Override
    public String toString() {
        return "ReadResult{" + aeb + "," + link + "}";
    }
}
