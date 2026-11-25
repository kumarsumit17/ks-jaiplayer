package com.ks.jaiplayer.vlc

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import java.io.File

/** Where libVLC is: the folder with `libvlc` and, on macOS, its plugins. */
data class VlcLocation(val libDir: File, val pluginDir: File? = null)

/** libVLC, loaded: the binding and the version string. */
internal class LoadedVlc(val lib: LibVlc, val version: String, val location: VlcLocation)

internal object VlcLoader {
    private val isWindows = System.getProperty("os.name").startsWith("Windows")
    private val libName = if (isWindows) "libvlc.dll" else "libvlc.dylib"
    private val coreName = if (isWindows) "libvlccore.dll" else "libvlccore.dylib"

    /** Loads the first location that holds a working libVLC 3.x, or returns null. Never throws. */
    fun load(locations: List<VlcLocation>): LoadedVlc? {
        for (location in locations) {
            val lib = File(location.libDir, libName)
            if (!lib.isFile) continue
            val loaded = runCatching { load(location, lib) }.getOrNull() ?: continue
            if (loaded.version.startsWith("3.")) return loaded
        }
        return null
    }

    private fun load(location: VlcLocation, lib: File): LoadedVlc {
        // libVLC finds its plugins through VLC_PLUGIN_PATH on macOS; it must be set before libvlc_new.
        location.pluginDir?.let { if (!isWindows) LibC.INSTANCE.setenv("VLC_PLUGIN_PATH", it.path, 1) }
        // Load libvlccore first by full path, so libvlc's dependency resolves to the same copy.
        NativeLibrary.getInstance(File(location.libDir, coreName).path)
        val vlc = Native.load(lib.path, LibVlc::class.java)
        return LoadedVlc(vlc, vlc.libvlc_get_version(), location)
    }

    @Suppress("FunctionName")
    private interface LibC : Library {
        fun setenv(name: String, value: String, overwrite: Int): Int

        companion object {
            val INSTANCE: LibC by lazy { Native.load("c", LibC::class.java) }
        }
    }
}
