package com.byd.aebtoggle.core;

/** Outcome of one blocking "disable AEB" write (transport level only; the engine verifies by reading). */
public final class WriteResult {
    public final LinkStatus link;

    private WriteResult(LinkStatus link) {
        this.link = link;
    }

    public static WriteResult of(LinkStatus link) {
        return new WriteResult(link == null ? LinkStatus.ERROR : link);
    }

    public boolean isSuccess() {
        return link == LinkStatus.SUCCESS;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof WriteResult && ((WriteResult) o).link == link;
    }

    @Override
    public int hashCode() {
        return link.hashCode();
    }

    @Override
    public String toString() {
        return "WriteResult{" + link + "}";
    }
}
