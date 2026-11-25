package com.ks.jaiplayer.windows

import com.ks.jaiplayer.desktop.DesktopConfig
import com.ks.jaiplayer.desktop.runDesktopApp
import com.ks.jaiplayer.vlc.BundledVlc
import com.ks.jaiplayer.vlc.VlcLocation
import java.io.File
import java.util.Locale

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

/**
 * The libVLC bundled in the app. JAIPLAYER_VLC_PATH (a VLC folder, for diagnosis only) replaces it;
 * an installed VLC is never looked for. libVLC finds its "plugins" folder next to libvlc.dll.
 */
private fun vlcLocations(): List<VlcLocation> {
    val override = System.getenv("JAIPLAYER_VLC_PATH")?.takeIf { it.isNotBlank() }?.let(::File)
    return if (override != null) listOf(VlcLocation(override)) else listOfNotNull(BundledVlc.location())
}
