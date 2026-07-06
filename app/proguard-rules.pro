# ProGuard / R8 rules for Guardian AI

# Keep TensorFlow Lite and its JNI bindings
-keep class org.tensorflow.lite.** { *; }
-keepclassmembers class org.tensorflow.lite.** { *; }
-keep class org.tensorflow.lite.support.** { *; }

# Keep Google ML Kit
-keep class com.google.mlkit.** { *; }

# Keep CameraX internals
-keep class androidx.camera.core.** { *; }
-keep class androidx.camera.camera2.** { *; }

# Keep Room generated code and models
-keep class com.ai.guardian.data.entity.** { *; }
-keepclassmembers class com.ai.guardian.data.entity.** { *; }

# Remove all debug logging in release builds to prevent leaking sensitive biometric/security states
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int i(...);
    public static int w(...);
    public static int d(...);
    public static int e(...);
}
