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
}

// The MSI version needs MAJOR >= 1; untagged development builds package as 1.0.0.
val versionName = (rootProject.extra["appVersionName"] as String).substringBefore('-')
val packageVersion = versionName.takeIf { (it.substringBefore('.').toIntOrNull() ?: 0) >= 1 } ?: "1.0.0"

compose.desktop {
    application {
        jvmArgs += listOf("-Djaiplayer.version=${rootProject.extra["appVersionName"]}")
        mainClass = "com.ks.jaiplayer.windows.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi)
            packageName = "KS JaiPlayer"
            this.packageVersion = packageVersion
            description = "KS JaiPlayer, a media player"
            vendor = "KS-Suite"
            modules("java.prefs", "java.desktop", "jdk.unsupported")
            windows {
                menu = true
                menuGroup = "KS-Suite"
                shortcut = true
                dirChooser = true
                // Fixed for the life of the app, so new versions upgrade the installed one.
                upgradeUuid = "5d0f2a4e-8a3b-4f7e-9c51-2b6f1e0c7a93"
            }
        }
        buildTypes.release.proguard { isEnabled.set(false) }
    }
}
