import org.jetbrains.compose.desktop.application.dsl.TargetFormat

// Windows entry point and packaging (.msi). Builds an msi only on Windows (CI).
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

kotlin { jvmToolchain(17) }

dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
    implementation(libs.ks.utility)
    implementation(libs.jna)
}

// libVLC and its LGPL plugins go into the installer (build/vlc-resources).
apply(from = rootProject.file("gradle/bundled-vlc.gradle.kts"))

// The MSI version needs MAJOR >= 1; untagged development builds package as 1.0.0.
val versionName = (rootProject.extra["appVersionName.windows"] as String).substringBefore('-')
val installerVersion = versionName.takeIf { (it.substringBefore('.').toIntOrNull() ?: 0) >= 1 } ?: "1.0.0"

val VIDEO_TYPES = mapOf(
    "mp4" to "video/mp4", "m4v" to "video/x-m4v", "mkv" to "video/x-matroska", "webm" to "video/webm",
    "avi" to "video/x-msvideo", "mov" to "video/quicktime", "wmv" to "video/x-ms-wmv", "flv" to "video/x-flv",
    "ts" to "video/mp2t", "m2ts" to "video/mp2t", "mpg" to "video/mpeg", "mpeg" to "video/mpeg", "3gp" to "video/3gpp",
)
val AUDIO_TYPES = mapOf(
    "mp3" to "audio/mpeg", "m4a" to "audio/mp4", "aac" to "audio/aac", "flac" to "audio/flac", "wav" to "audio/wav",
    "ogg" to "audio/ogg", "opus" to "audio/opus", "wma" to "audio/x-ms-wma",
)

compose.desktop {
    application {
        jvmArgs += listOf("-Djaiplayer.version=${rootProject.extra["appVersionName.windows"]}")
        mainClass = "com.ks.jaiplayer.windows.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi)
            packageName = "KS JaiPlayer"
            packageVersion = installerVersion
            description = "KS JaiPlayer, a media player"
            vendor = "KS-Suite"
            appResourcesRootDir.set(layout.buildDirectory.dir("vlc-resources"))
            // jdk.accessibility lets Narrator and NVDA read the app (Java Access Bridge).
            modules("java.prefs", "java.desktop", "jdk.unsupported", "jdk.accessibility")
            windows {
                menu = true
                menuGroup = "KS-Suite"
                // KS JaiPlayer's icon, 16 to 256 px.
                iconFile.set(project.file("packaging/icon.ico"))
                shortcut = true
                dirChooser = true
                // Fixed for the life of the app, so new versions upgrade the installed one.
                upgradeUuid = "5d0f2a4e-8a3b-4f7e-9c51-2b6f1e0c7a93"
                // "Open with" and double click in Explorer, for the same kinds of file as on macOS.
                for ((ext, mime) in VIDEO_TYPES) fileAssociation(mime, ext, "Video")
                for ((ext, mime) in AUDIO_TYPES) fileAssociation(mime, ext, "Audio")
            }
        }
        buildTypes.release.proguard { isEnabled.set(false) }
    }
}
