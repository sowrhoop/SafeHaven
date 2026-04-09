# ==============================================================================
# SAFE-HAVEN SECURITY & OBFUSCATION RULES
# ==============================================================================

# Repack classes to a compact namespace for release builds
-repackageclasses 'a'
-allowaccessmodification

# Strip verbose logging in production builds
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int i(...);
    public static int w(...);
    public static int d(...);
    public static int e(...);
}

# Hide source file name attributes in release builds
-renamesourcefileattribute SourceFile
-keepattributes SourceFile

# Prevent R8 from warning about internal Android APIs used by the app
-dontwarn android.app.ActivityThread
-dontwarn android.app.ContextImpl
-dontwarn android.app.LoadedApk