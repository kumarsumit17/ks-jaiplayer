// Bundled libVLC (specs/plan-bundled-vlc-and-plugins.md), applied by desktop/mac and desktop/windows.
// Downloads the pinned VLC build for the OS and chip this runs on, checks its SHA-256, and keeps
// libVLC with its LGPL plugins in build/vlc-resources (the app's resources, set in each module).
// The work is done by the build tool in shared/src/desktopMain (vlc/VlcBundle.kt).
// -PnoBundledVlc skips it, for a build that uses an installed VLC only.

val vlcVersion = "3.0.24"
val vlcBase = "https://download.videolan.org/pub/videolan/vlc/$vlcVersion"
val osName = System.getProperty("os.name")
val osArch = System.getProperty("os.arch")

val vlcPackage: Pair<String, String>? = when {
    osName.startsWith("Mac") && osArch == "aarch64" ->
        "$vlcBase/macosx/vlc-$vlcVersion-arm64.dmg" to "64a89d93cdd30b0e97131743e246373db82d6826ea882d265d46d74b136da2b7"
    osName.startsWith("Mac") ->
        "$vlcBase/macosx/vlc-$vlcVersion-intel64.dmg" to "1ef6c903e2dc026d4e58ddf7b2a9ed0eb6f8caf80a9a9f46f96490340b962707"
    osName.startsWith("Windows") ->
        "$vlcBase/win64/vlc-$vlcVersion-win64.zip" to "fcf30850371ad10c9373cc4f0f4501e7dee49e3e9ae9f20c72fb2661a1ca6323"
    else -> null
}
val licenceUrl = "https://code.videolan.org/videolan/vlc/-/raw/$vlcVersion/COPYING.LIB"
val licenceSha = "dc626520dcd53a22f727af3ee42c770e56c97a64fe3adb063799d8ab032fe551"

val vlcResources = layout.buildDirectory.dir("vlc-resources")
val vlcDownloads = rootProject.layout.buildDirectory.dir("vlc-download")

if (vlcPackage != null && !providers.gradleProperty("noBundledVlc").isPresent) {
    val bundleVlc = tasks.register<JavaExec>("bundleVlc") {
        description = "Downloads VLC $vlcVersion and keeps libVLC with its LGPL plugins for the installer."
        classpath = project.extensions.getByType<SourceSetContainer>()["main"].runtimeClasspath
        mainClass.set("com.ks.jaiplayer.vlc.VlcBundleKt")
        val out = vlcResources.get().asFile
        args(vlcPackage.first, vlcPackage.second, vlcDownloads.get().asFile.path, licenceUrl, licenceSha, vlcVersion, out.path)
        inputs.property("package", vlcPackage.first)
        outputs.dir(out)
    }
    tasks.matching { it.name == "prepareAppResources" }.configureEach { dependsOn(bundleVlc) }
}
