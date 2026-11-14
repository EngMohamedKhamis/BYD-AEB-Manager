# BYD AEB Stealth Pilot - ProGuard Rules (Stabilized Hardening)

# Optimization settings
-allowaccessmodification
-overloadaggressively

# Keep Android entry points
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends android.view.View

# Keep SharedPreferences keys and Settings
-keepclassmembers class **.R$* {
    public static <fields>;
}


# Keep public API and Background Status fields
-keepclassmembers class com.byd.aebtoggle.AebService {
    public static int lastAebState;
    public static com.byd.aebtoggle.AdbHelper$AdbStatus lastAdbStatus;
}

-keepclassmembernames class com.byd.aebtoggle.AdbHelper {
    public static boolean grantSelfPermissions();
    public static int getAebState();
    public static boolean setAebState(boolean);
}

# Remove all Log statements for stealth
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}
