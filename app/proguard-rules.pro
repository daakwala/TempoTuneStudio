# FFmpegKit
-keep class com.arthenica.ffmpegkit.** { *; }

# Media3 / ExoPlayer
-keep class androidx.media3.** { *; }

# OkHttp
-dontwarn okhttp3.**
-keep class okhttp3.** { *; }

# Glide
-keep public class * implements com.bumptech.glide.module.GlideModule
