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
// One version for every platform (KS-Suite specs/versioning-signing.md): a plain vX.Y.Z tag releases
// all four builds. Priority: -PversionName / -PversionCode (the release workflow passes them from the
// tag), then the latest plain git tag, then a development fallback. The modules read these extras.
fun git(vararg args: String): String? = runCatching {
    val result = providers.exec {
        commandLine("git", *args)
        isIgnoreExitValue = true
    }
    if (result.result.get().exitValue == 0) result.standardOutput.asText.get().trim().ifEmpty { null } else null
}.getOrNull()

val appVersionName: String =
    providers.gradleProperty("versionName").orNull
        ?: git("describe", "--tags", "--abbrev=0", "--match", "v[0-9]*")?.removePrefix("v")
        ?: "0.0.0" // A build from source: no version, so it never checks for updates by itself.

val appVersionCode: Int =
    providers.gradleProperty("versionCode").orNull?.toIntOrNull()
        ?: git("rev-list", "--count", "HEAD")?.toIntOrNull()
        ?: 1

extra["appVersionName"] = appVersionName
extra["appVersionCode"] = appVersionCode

// ---- The two Android apps: phone (mobile/android) and Google TV (tv/google-tv) ---------------------
// Separate APKs and AABs with the same package name, version and keystore, so other KS apps hand
// playback to com.ks.jaiplayer whichever one is installed, and each updates over itself. Google
// Play needs a different versionCode for every bundle of one app: the TV app adds TV_CODE_OFFSET,
// which also stops the phone build from being installed over the TV build (a lower code).
val TV_CODE_OFFSET = 1_000_000_000
val keystoreProperties = java.util.Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use(::load)
}

// Blank counts as missing: GitHub Actions passes an unset secret as an empty string.
fun signingValue(env: String, key: String): String? =
    providers.environmentVariable(env).orNull?.takeIf { it.isNotBlank() }
        ?: keystoreProperties.getProperty(key)?.takeIf { it.isNotBlank() }

val androidCompileSdk = libs.versions.androidCompileSdk.get().toInt()
val androidMinSdk = libs.versions.androidMinSdk.get().toInt()
val androidTargetSdk = libs.versions.androidTargetSdk.get().toInt()

subprojects {
    plugins.withId("com.android.application") {
        val tv = path == ":tv:google-tv"
        extensions.configure<com.android.build.api.dsl.ApplicationExtension> {
            compileSdk = androidCompileSdk
            defaultConfig {
                applicationId = "com.ks.jaiplayer"
                minSdk = androidMinSdk
                targetSdk = androidTargetSdk
                versionCode = appVersionCode + if (tv) TV_CODE_OFFSET else 0
                versionName = appVersionName
            }
            val storeFilePath = signingValue("SIGNING_STORE_FILE", "storeFile")
            if (storeFilePath != null) {
                signingConfigs.create("release") {
                    storeFile = file(storeFilePath)
                    storePassword = signingValue("SIGNING_STORE_PASSWORD", "storePassword")
                    keyAlias = signingValue("SIGNING_KEY_ALIAS", "keyAlias")
                    keyPassword = signingValue("SIGNING_KEY_PASSWORD", "keyPassword")
                }
            }
            // R8 removes unused code and resources: a smaller APK/AAB and a faster start (also for the
            // `play` build, which starts from this one). Libraries bring their own keep rules.
            buildTypes.getByName("release") {
                isMinifyEnabled = true
                isShrinkResources = true
                proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
                signingConfig = signingConfigs.findByName("release")
            }
            // The Google Play build (the AABs, ks-utility release workflow `play-build-type`): the
            // release build without the in-app updater, which Play forbids.
            buildTypes.create("play") {
                initWith(buildTypes.getByName("release"))
                matchingFallbacks += "release"
            }
            sourceSets.getByName("play").manifest.srcFile(rootProject.file("shared/android-play/AndroidManifest.xml"))
            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_17
                targetCompatibility = JavaVersion.VERSION_17
            }
            androidResources { localeFilters += listOf("en", "hi") }
        }
        dependencies.add("implementation", project(":shared"))
    }
}
