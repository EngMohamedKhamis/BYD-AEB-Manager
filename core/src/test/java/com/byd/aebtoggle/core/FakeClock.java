package com.byd.aebtoggle.core;

final class FakeClock implements Clock {
    long now = 0;

    @Override
    public long nowMs() {
        return now;
    }
}
