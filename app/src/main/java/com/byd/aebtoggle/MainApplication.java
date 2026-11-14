package com.byd.aebtoggle;

import android.app.Application;

public class MainApplication extends Application {
    private static volatile MainApplication instance;

    public static MainApplication getAppContext() {
        return instance;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        // C2a: migrate legacy preferences on app startup before any service, receiver or activity reads them
        new AebSettings(this).migrateLegacy();
        // S8: initialize ADB key store (loads or generates per-install RSA key in app-private storage)
        AdbKeyStore.getInstance(this);
    }
}
