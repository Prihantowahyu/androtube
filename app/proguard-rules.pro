# Proguard rules for WhyTube Lite

# Keep OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keepclasseswithmembers class * {
    native <methods>;
}

# Keep Media3 / ExoPlayer
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Keep Coil
-keep class coil.** { *; }
-dontwarn coil.**

# Keep Models
-keep class com.whytube.app.api.** { *; }
