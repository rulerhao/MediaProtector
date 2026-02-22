# MediaProtector ProGuard Rules - Optimized for minimum APK size

# ============================================================================
# AGGRESSIVE OPTIMIZATIONS
# ============================================================================

# More optimization passes for better results
-optimizationpasses 10

# Enable all safe optimizations (override default restrictions)
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*,!code/allocation/variable

# Allow access modification for better optimization
-allowaccessmodification

# Repackage all classes into a single package for smaller DEX
-repackageclasses ''

# Remove unused code aggressively
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
    public static int wtf(...);
}

# ============================================================================
# REMOVE DEBUG INFO
# ============================================================================

# Remove source file names and line numbers (saves space, but makes debugging harder)
-renamesourcefileattribute ''
-keepattributes Signature

# Remove unused attributes
-keepattributes !LocalVariableTable,!LocalVariableTypeTable,!LineNumberTable

# ============================================================================
# KEEP RULES (minimal - only what's necessary)
# ============================================================================

# Keep Activities (required - referenced in AndroidManifest.xml)
-keep public class * extends android.app.Activity

# Keep crypto classes that use reflection or file operations
-keep class com.rulerhao.media_protector.crypto.HeaderObfuscator {
    public <methods>;
}

# Keep MediaDataSource implementation (used by MediaPlayer via reflection-like mechanism)
-keep class com.rulerhao.media_protector.util.EncryptedMediaDataSource {
    public <methods>;
}

# Keep SharedPreferences keys (string constants accessed by name)
-keepclassmembers class com.rulerhao.media_protector.util.OriginalPathStore {
    static final java.lang.String *;
}
-keepclassmembers class com.rulerhao.media_protector.util.SecurityHelper {
    static final java.lang.String *;
}
-keepclassmembers class com.rulerhao.media_protector.util.ThemeHelper {
    static final java.lang.String *;
}

# ============================================================================
# SHRINK AGGRESSIVELY
# ============================================================================

# Don't keep generic signatures for lambdas (not needed at runtime)
-dontwarn java.lang.invoke.**

# Remove Kotlin metadata if any sneaks in
-dontwarn kotlin.**
-dontwarn kotlinx.**

# Suppress warnings for missing classes we don't use
-dontwarn javax.annotation.**
-dontwarn org.jetbrains.annotations.**
