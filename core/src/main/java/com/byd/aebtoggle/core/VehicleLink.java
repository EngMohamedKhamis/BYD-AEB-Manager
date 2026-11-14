package com.byd.aebtoggle.core;

/**
 * The only door from the engine to the car. Both calls BLOCK (a network round trip) and must not
 * throw; the engine still guards against a Throwable, exactly like the old safety nets.
 *
 * Android side (later stage): a thin adapter over AdbHelper. Tests use a fake.
 */
public interface VehicleLink {
    /** Reads the AEB property. Never returns null. */
    ReadResult read();

    /**
     * Writes "AEB off" (today: both vendor properties, see AdbHelper.setAebState(false)).
     * There is deliberately NO method that turns AEB on.
     */
    WriteResult writeDisable();
}
