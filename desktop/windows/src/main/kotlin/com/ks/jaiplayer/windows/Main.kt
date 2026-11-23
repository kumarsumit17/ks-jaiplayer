package com.ks.jaiplayer.windows

import com.ks.jaiplayer.desktop.DesktopConfig
import com.ks.jaiplayer.desktop.runDesktopApp
import com.ks.jaiplayer.vlc.VlcLocation
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Windows entry point: the file to play comes as a command-line argument ("Open with"). */
fun main(args: Array<String>) {
    runDesktopApp(
        DesktopConfig(
            vlcLocations = vlcLocations(),
            systemLanguages = listOf(Locale.getDefault().toLanguageTag()),
            initialFiles = args.map(::File).filter { it.exists() },
        ),
    )
}

/** 64-bit VLC: JAIPLAYER_VLC_PATH, the folder the VLC installer records, then Program Files. */
private fun vlcLocations(): List<VlcLocation> {
    val dirs = listOfNotNull(
        System.getenv("JAIPLAYER_VLC_PATH")?.let(::File),
        registryInstallDir()?.let(::File),
        System.getenv("ProgramFiles")?.let { File(it, "VideoLAN\\VLC") },
    )
    // libVLC finds its plugins in the "plugins" folder next to libvlc.dll by itself.
    return dirs.distinct().map { VlcLocation(it) }
}

private fun registryInstallDir(): String? = runCatching {
    val process = ProcessBuilder("reg", "query", "HKLM\\SOFTWARE\\VideoLAN\\VLC", "/v", "InstallDir")
        .redirectErrorStream(true).start()
    val text = process.inputStream.bufferedReader().readText()
    process.waitFor(2, TimeUnit.SECONDS)
    Regex("InstallDir\\s+REG_SZ\\s+(.+)").find(text)?.groupValues?.get(1)?.trim()
}.getOrNull()
