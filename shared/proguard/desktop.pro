# ProGuard rules for the macOS and Windows release builds (desktop/mac, desktop/windows).
# Compose Desktop adds its own rules for Compose and Kotlin; these keep what is reached by
# reflection. Shrinking removes unused library code; names stay readable (no obfuscation), so
# stack traces and third-party plugins keep working.

-dontobfuscate

# KS JaiPlayer and ks-utility: kept whole. Plugins (jars in the plugins folder) link against the
# plugin API by name, JNA binds interfaces and structures by reflection, ServiceLoader finds classes.
-keep class com.ks.** { *; }

# JNA: its own classes, and every interface, structure and callback it maps to native code.
-keep class com.sun.jna.** { *; }
-keep class * implements com.sun.jna.Library { *; }
-keep class * implements com.sun.jna.Callback { *; }
-keep class * extends com.sun.jna.Structure { *; }
-dontwarn com.sun.jna.**

# Coroutines' Swing dispatcher is found through ServiceLoader.
-keep class kotlinx.coroutines.swing.** { *; }
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory { *; }
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler { *; }
-dontwarn kotlinx.coroutines.**

# Skia (Compose's renderer) is reached from native code.
-keep class org.jetbrains.skia.** { *; }
-keep class org.jetbrains.skiko.** { *; }

# The macOS gesture classes are JDK classes reached by reflection (MacIntegration.pinch).
-dontwarn com.apple.eawt.**
