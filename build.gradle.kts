// Plugins are declared once here and applied in the modules that use them.
plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.kmp.library) apply false
}

// ---- Versioning (never hardcoded) ----------------------------------------------------------------
// Priority: -PversionName / -PversionCode (the release workflow passes them from the tag), then the
// latest git tag, then a development fallback. The modules read these two extras.
fun git(vararg args: String): String? = runCatching {
    val result = providers.exec {
        commandLine("git", *args)
        isIgnoreExitValue = true
    }
    if (result.result.get().exitValue == 0) result.standardOutput.asText.get().trim().ifEmpty { null } else null
}.getOrNull()

val appVersionName: String =
    providers.gradleProperty("versionName").orNull
        ?: git("describe", "--tags", "--abbrev=0")?.removePrefix("v")
        ?: "0.0.0" // A build from source: no version, so it never checks for updates by itself.

val appVersionCode: Int =
    providers.gradleProperty("versionCode").orNull?.toIntOrNull()
        ?: git("rev-list", "--count", "HEAD")?.toIntOrNull()
        ?: 1

extra["appVersionName"] = appVersionName
extra["appVersionCode"] = appVersionCode
