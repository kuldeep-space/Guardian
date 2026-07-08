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

# Keep all remote and Firestore data models (avoids reflection crashes)
-keep class com.ai.guardian.data.remote.models.** { *; }
-keepclassmembers class com.ai.guardian.data.remote.models.** { *; }

# Keep Accessibility and Foreground services
-keep class com.ai.guardian.services.** { *; }

# Keep Security components and KeyStore helpers
-keep class com.ai.guardian.security.** { *; }

# Strip verbose, info, and debug logs in release builds for security
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int i(...);
    public static int d(...);
}

