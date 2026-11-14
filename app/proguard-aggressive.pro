# BYD AEB Stealth Pilot - ProGuard Rules (Aggressive Hardening)

# Aggressive Renaming
-repackageclasses ''
-allowaccessmodification
-overloadaggressively

# Keep Android entry points
-keep public class * extends android.app.Application
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends android.view.View

# Keep XML onClick handlers
-keepclassmembers class * extends android.app.Activity {
    public void *(android.view.View);
}


# (S2) The AebService status fields are gone; screen and HUD read StateStore, which needs no keep rule.

-keepclassmembernames class com.byd.aebtoggle.AdbHelper {
    public static boolean grantSelfPermissions();
    public static com.byd.aebtoggle.AdbHelper$AdbResponse getAebState();
    public static com.byd.aebtoggle.AdbHelper$AdbResponse setAebState(boolean);
    public static com.byd.aebtoggle.AdbHelper$VerificationResult queryAebStatus();
    public static volatile com.byd.aebtoggle.AdbHelper$AdbStatus lastRawStatus;
}

# Remove debug/verbose/info Log statements for stealth (keep w/e for diagnostics)
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}
