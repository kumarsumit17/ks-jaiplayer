package com.ks.jaiplayer.mac

import com.ks.jaiplayer.desktop.DesktopConfig
import com.ks.jaiplayer.desktop.ExternalOpen
import com.ks.jaiplayer.desktop.runDesktopApp
import com.ks.jaiplayer.vlc.VlcLocation
import java.awt.Desktop
import java.io.File
import java.util.concurrent.TimeUnit

/** macOS entry point: the system menu bar, Finder "Open With", and VLC.app discovery. */
fun main(args: Array<String>) {
    System.setProperty("apple.laf.useScreenMenuBar", "true")
    System.setProperty("apple.awt.application.name", "KS JaiPlayer")

    // Finder sends files as Apple events, before or after the window exists. Set this first.
    if (Desktop.isDesktopSupported()) {
        val desktop = Desktop.getDesktop()
        if (desktop.isSupported(Desktop.Action.APP_OPEN_FILE)) {
            desktop.setOpenFileHandler { event -> ExternalOpen.open(event.files) }
        }
    }

    runDesktopApp(
        DesktopConfig(
            vlcLocations = vlcLocations(),
            vlcArgs = MAC_VLC_ARGS,
            systemLanguages = preferredLanguages(),
            initialFiles = args.map(::File).filter { it.exists() },
            systemAppMenu = true,
            useMetaShortcuts = true,
            nativeFolderDialog = true,
        ),
    )
}

/**
 * VideoToolbox decodes into GPU buffers that libVLC cannot draw subtitles onto when frames go
 * through callbacks ("blending YUVA to CVPN failed"), so decoding is done by libavcodec in software.
 */
val MAC_VLC_ARGS = listOf("--codec=avcodec,all", "--avcodec-hw=none")

/** VLC.app in the usual places, or the folder named by JAIPLAYER_VLC_PATH. */
private fun vlcLocations(): List<VlcLocation> {
    val apps = listOfNotNull(
        System.getenv("JAIPLAYER_VLC_PATH")?.let(::File),
        File("/Applications/VLC.app"),
        File(System.getProperty("user.home"), "Applications/VLC.app"),
    )
    return apps.flatMap { app ->
        listOf(
            VlcLocation(File(app, "Contents/MacOS/lib"), File(app, "Contents/MacOS/plugins")),
            VlcLocation(app, File(app, "plugins")),
        )
    }
}

/** The user's preferred languages from System Settings (AppleLanguages), most preferred first. */
private fun preferredLanguages(): List<String> {
    val fromSystem = runCatching {
        val process = ProcessBuilder("defaults", "read", "-g", "AppleLanguages").redirectErrorStream(true).start()
        val text = process.inputStream.bufferedReader().readText()
        process.waitFor(2, TimeUnit.SECONDS)
        Regex("\"?([A-Za-z]{2,3}(?:-[A-Za-z0-9]+)*)\"?").findAll(text.substringAfter('(').substringBefore(')'))
            .map { it.groupValues[1] }.toList()
    }.getOrDefault(emptyList())
    return fromSystem.ifEmpty { listOf(java.util.Locale.getDefault().toLanguageTag()) }
}
