package com.ks.jaiplayer.windows

import com.ks.jaiplayer.desktop.DesktopIntegration
import com.ks.jaiplayer.desktop.NowPlaying
import com.ks.jaiplayer.player.PlayerAction
import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.WString
import java.io.File
import java.net.URI

/**
 * Windows integrations through JNA, with no native code to build:
 * - the display stays awake while a video plays (`SetThreadExecutionState`);
 * - each opened file goes to the taskbar and Start jump list (`SHAddToRecentDocs`);
 * - the title bar is dark like the app (`DwmSetWindowAttribute`, Windows 10 1809 and later).
 * The system media overlay (SMTC) comes later, by the owner's decision.
 * Every call is made on the window's thread, which lives as long as the app, as
 * `SetThreadExecutionState` requires. Anything that fails is skipped.
 */
class WindowsIntegration : DesktopIntegration {
    private var awake = false
    private var lastUri: String? = null

    override fun start(window: java.awt.Window, perform: (PlayerAction) -> Unit) {
        runCatching {
            val hwnd = Native.getWindowPointer(window) ?: return@runCatching
            val on = Memory(4).apply { setInt(0, 1) }
            // DWMWA_USE_IMMERSIVE_DARK_MODE is 20 (19 before Windows 10 20H1).
            if (Dwm.INSTANCE.DwmSetWindowAttribute(hwnd, 20, on, 4) != 0) Dwm.INSTANCE.DwmSetWindowAttribute(hwnd, 19, on, 4)
        }
    }

    override fun update(nowPlaying: NowPlaying?, hasVideo: Boolean) {
        val wantAwake = nowPlaying?.playing == true && hasVideo
        if (wantAwake != awake) {
            runCatching {
                Kernel32.INSTANCE.SetThreadExecutionState(if (wantAwake) ES_CONTINUOUS or ES_DISPLAY_REQUIRED or ES_SYSTEM_REQUIRED else ES_CONTINUOUS)
                awake = wantAwake
            }
        }
        val uri = nowPlaying?.uri
        if (uri != null && uri != lastUri) {
            lastUri = uri
            runCatching {
                val file = URI(uri).takeIf { it.scheme == "file" }?.let(::File)
                if (file != null && file.isFile) Shell32.INSTANCE.SHAddToRecentDocs(SHARD_PATHW, WString(file.absolutePath))
            }
        }
    }

    override fun stop() {
        if (awake) runCatching { Kernel32.INSTANCE.SetThreadExecutionState(ES_CONTINUOUS) }
        awake = false
    }

    @Suppress("FunctionName")
    private interface Kernel32 : Library {
        fun SetThreadExecutionState(flags: Int): Int
        companion object { val INSTANCE: Kernel32 by lazy { Native.load("kernel32", Kernel32::class.java) } }
    }

    @Suppress("FunctionName")
    private interface Shell32 : Library {
        fun SHAddToRecentDocs(flags: Int, path: WString)
        companion object { val INSTANCE: Shell32 by lazy { Native.load("shell32", Shell32::class.java) } }
    }

    @Suppress("FunctionName")
    private interface Dwm : Library {
        fun DwmSetWindowAttribute(hwnd: Pointer, attribute: Int, value: Pointer, size: Int): Int
        companion object { val INSTANCE: Dwm by lazy { Native.load("dwmapi", Dwm::class.java) } }
    }

    private companion object {
        const val ES_CONTINUOUS = 0x80000000.toInt()
        const val ES_SYSTEM_REQUIRED = 0x00000001
        const val ES_DISPLAY_REQUIRED = 0x00000002
        const val SHARD_PATHW = 3
    }
}
