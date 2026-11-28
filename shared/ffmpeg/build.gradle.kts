// FFmpeg's AC-3 and E-AC-3 decoders for the phone and Google TV apps (specs/plan-audio-codecs.md,
// specs/licences.md). Media3's FFmpeg extension, ported to Kotlin, around FFmpeg built here from
// the pinned release by build-ffmpeg.sh. LGPL 2.1.
plugins {
    alias(libs.plugins.android.library)
}

val abis = listOf("arm64-v8a", "armeabi-v7a", "x86_64")
val minSdkLevel = libs.versions.androidMinSdk.get().toInt()

android {
    namespace = "com.ks.jaiplayer.ffmpeg"
    compileSdk = libs.versions.androidCompileSdk.get().toInt()
    ndkVersion = libs.versions.androidNdk.get()
    defaultConfig {
        minSdk = minSdkLevel
        consumerProguardFiles("consumer-rules.pro")
        // Real devices: 64- and 32-bit ARM. x86_64 only in debug builds, for emulators.
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
        externalNativeBuild {
            cmake {
                arguments += listOf("-DFFMPEG_DIR=" + layout.buildDirectory.dir("ffmpeg").get().asFile.path, "-DANDROID_STL=none")
            }
        }
    }
    buildTypes {
        getByName("debug") { ndk { abiFilters += "x86_64" } }
        getByName("release") { externalNativeBuild { cmake { cFlags += "-Os"; cppFlags += "-Os" } } }
    }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = libs.versions.androidCmake.get()
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.media3.exoplayer)
}

val buildFfmpeg = tasks.register<Exec>("buildFfmpeg") {
    description = "Downloads the pinned FFmpeg release and builds its AC-3 and E-AC-3 decoders for ${abis.joinToString()}."
    val script = file("build-ffmpeg.sh")
    val out = layout.buildDirectory.dir("ffmpeg")
    val downloads = rootProject.layout.buildDirectory.dir("ffmpeg-download")
    inputs.file(script)
    inputs.property("abis", abis)
    outputs.dir(out)
    executable = "bash"
    // Only values and providers here (configuration cache): no references to the script.
    val ndk = androidComponents.sdkComponents.ndkDirectory
    val fixed = listOf(minSdkLevel.toString()) + abis
    val scriptPath = script.path
    argumentProviders += CommandLineArgumentProvider {
        listOf(scriptPath, ndk.get().asFile.path, downloads.get().asFile.path, out.get().asFile.path) + fixed
    }
}
tasks.matching { it.name.startsWith("configureCMake") || it.name.startsWith("buildCMake") }.configureEach { dependsOn(buildFfmpeg) }
