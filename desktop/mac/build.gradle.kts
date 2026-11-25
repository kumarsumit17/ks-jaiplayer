import org.jetbrains.compose.desktop.application.dsl.TargetFormat

// macOS entry point and packaging (.dmg). Builds a dmg only on a Mac.
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
}

// libVLC and its LGPL plugins go into the installer (build/vlc-resources).
apply(from = rootProject.file("gradle/bundled-vlc.gradle.kts"))

// jpackage needs MAJOR >= 1; untagged development builds package as 1.0.0.
val versionName = (rootProject.extra["appVersionName"] as String).substringBefore('-')
val installerVersion = versionName.takeIf { (it.substringBefore('.').toIntOrNull() ?: 0) >= 1 } ?: "1.0.0"

compose.desktop {
    application {
        jvmArgs += listOf("-Djaiplayer.version=${rootProject.extra["appVersionName"]}")
        mainClass = "com.ks.jaiplayer.mac.MainKt"
        jvmArgs += listOf("-Dapple.awt.application.appearance=NSAppearanceNameDarkAqua")
        nativeDistributions {
            targetFormats(TargetFormat.Dmg)
            packageName = "KS JaiPlayer"
            packageVersion = installerVersion
            description = "KS JaiPlayer, a media player"
            vendor = "KS-Suite"
            appResourcesRootDir.set(layout.buildDirectory.dir("vlc-resources"))
            modules("java.prefs", "java.desktop", "jdk.unsupported")
            macOS {
                bundleID = "com.ks.jaiplayer"
                appCategory = "public.app-category.video"
                infoPlist {
                    extraKeysRawXml = file("packaging/document-types.plist").readText()
                }
            }
        }
        buildTypes.release.proguard { isEnabled.set(false) }
    }
}
