// A sample third-party plugin for the desktop apps (specs/writing-plugins.md). It is not part
// of any installer: build it with `./gradlew :desktop:plugin-sample:jar` and copy the jar from
// build/libs into the app's plugins folder (Settings › Plugins › Open folder).
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

kotlin { jvmToolchain(17) }

dependencies {
    // Everything a plugin uses is already in the app, so nothing is bundled into the jar.
    compileOnly(project(":shared"))
    compileOnly(compose.runtime)
    compileOnly(compose.ui)
    compileOnly(libs.kotlinx.coroutines.core)
}

tasks.jar {
    archiveBaseName.set("ks-jaiplayer-loop-plugin")
    manifest {
        attributes(
            "JaiPlayer-Plugin-Id" to "sample.loop",
            "JaiPlayer-Plugin-Name" to "Loop (sample plugin)",
            "JaiPlayer-Plugin-Api" to "1",
        )
    }
}
