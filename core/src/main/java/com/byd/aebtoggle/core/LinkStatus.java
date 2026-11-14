package com.byd.aebtoggle.core;

/**
 * Transport status of the last ADB call. The names are identical to AdbHelper.AdbStatus on
 * purpose, so the Android side can map the two with valueOf(name()).
 */
public enum LinkStatus {
    SUCCESS,
    AUTH_FAIL,
    TIMEOUT,
    CLOSED,
    ERROR
}
