# ProGuard / R8 Rules for Veyl Audio Player

# UniFFI Rust bindings & JNA
-keep class uniffi.** { *; }
-keepclassmembers class uniffi.** { *; }
-keep class com.sun.jna.** { *; }
-keepclassmembers class * extends com.sun.jna.* { public *; }

# OkHttp & Okio
-dontwarn okhttp3.**
-dontwarn okio.**

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
