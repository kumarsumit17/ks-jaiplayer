package com.ks.jaiplayer.mac

import com.ks.jaiplayer.desktop.DesktopIntegration
import com.ks.jaiplayer.desktop.NowPlaying
import com.ks.jaiplayer.player.PlayerAction
import com.sun.jna.Callback
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import com.sun.jna.ptr.IntByReference
import javax.swing.SwingUtilities

/**
 * macOS integrations through JNA, with no native code to build:
 * - the display stays awake while a video plays (an IOKit power assertion);
 * - Now Playing in Control Centre and the menu bar shows the title, length and position, and
 *   the keyboard's media keys, AirPods and Control Centre control the player
 *   (`MPNowPlayingInfoCenter` and `MPRemoteCommandCenter`, through the Objective-C runtime).
 * Anything that fails to load is skipped; the player works without it.
 */
class MacIntegration : DesktopIntegration {
    private var assertion = 0
    private var perform: (PlayerAction) -> Unit = {}
    private val objc by lazy { runCatching { ObjC() }.getOrNull() }
    private var lastInfo: NowPlaying? = null

    override fun start(window: java.awt.Window, labels: com.ks.jaiplayer.desktop.IntegrationLabels, perform: (PlayerAction) -> Unit) {
        this.perform = perform
        runCatching { objc?.registerCommands(::onCommand) }
        runCatching { dockMenu(labels) }
    }

    /**
     * Trackpad pinch through the JDK's macOS gesture support (`com.apple.eawt.event`, exported to
     * the app with `--add-exports`; reached by reflection because the package is not public API).
     * The magnification of one gesture adds up; past ±0.25 it counts once, and a pause of 300 ms
     * starts a new gesture.
     */
    override fun pinch(window: java.awt.Window, onPinch: (zoomIn: Boolean) -> Unit) {
        val root = (window as? javax.swing.RootPaneContainer)?.rootPane ?: return
        runCatching {
            val utilities = Class.forName("com.apple.eawt.event.GestureUtilities")
            val gestureListener = Class.forName("com.apple.eawt.event.GestureListener")
            val magnification = Class.forName("com.apple.eawt.event.MagnificationListener")
            val getMagnification = Class.forName("com.apple.eawt.event.MagnificationEvent").getMethod("getMagnification")
            var total = 0.0
            var last = 0L
            var done = false
            lateinit var self: Any
            self = java.lang.reflect.Proxy.newProxyInstance(magnification.classLoader, arrayOf(magnification)) { _, method, args ->
                when (method.name) {
                    "magnify" -> {
                        val now = System.currentTimeMillis()
                        if (now - last > PINCH_GAP_MS) { total = 0.0; done = false }
                        last = now
                        total += getMagnification.invoke(args!![0]) as Double
                        if (!done && kotlin.math.abs(total) > PINCH_THRESHOLD) { done = true; onPinch(total > 0) }
                        null
                    }
                    "equals" -> args!![0] === self
                    "hashCode" -> System.identityHashCode(self)
                    "toString" -> "JaiPlayer pinch"
                    else -> null
                }
            }
            utilities.getMethod("addGestureListenerTo", javax.swing.JComponent::class.java, gestureListener).invoke(null, root, self)
        }.onFailure { System.err.println("KS JaiPlayer: trackpad pinch unavailable: $it") }
    }

    private var dockItems: Triple<java.awt.MenuItem, java.awt.MenuItem, java.awt.MenuItem>? = null

    /** Right-click on the Dock icon: play or pause, next and previous episode. */
    private fun dockMenu(labels: com.ks.jaiplayer.desktop.IntegrationLabels) {
        val taskbar = java.awt.Taskbar.getTaskbar()
        if (!taskbar.isSupported(java.awt.Taskbar.Feature.MENU)) return
        val playItem = java.awt.MenuItem(labels.play).apply { addActionListener { onCommand(PlayerAction.TogglePlay) } }
        val nextItem = java.awt.MenuItem(labels.next).apply { addActionListener { onCommand(PlayerAction.NextItem) } }
        val previousItem = java.awt.MenuItem(labels.previous).apply { addActionListener { onCommand(PlayerAction.PreviousItem) } }
        taskbar.menu = java.awt.PopupMenu().apply { add(playItem); add(previousItem); add(nextItem) }
        dockItems = Triple(playItem, nextItem, previousItem)
        dockLabels = labels.play to labels.pause
        refreshDock(null)
    }

    private var dockLabels: Pair<String, String>? = null

    private fun refreshDock(info: NowPlaying?) {
        val (playItem, nextItem, previousItem) = dockItems ?: return
        val (play, pause) = dockLabels ?: return
        playItem.label = if (info?.playing == true) pause else play
        playItem.isEnabled = info != null
        nextItem.isEnabled = info?.hasNext == true
        previousItem.isEnabled = info?.hasPrevious == true
    }

    override fun update(nowPlaying: NowPlaying?, hasVideo: Boolean) {
        keepAwake(nowPlaying?.playing == true && hasVideo)
        runCatching { refreshDock(nowPlaying) }
        if (nowPlaying == lastInfo) return
        val previous = lastInfo
        lastInfo = nowPlaying
        // Position changes every poll; the system extrapolates from rate, so only resend on jumps.
        if (previous != null && nowPlaying != null && previous.copy(positionMs = 0) == nowPlaying.copy(positionMs = 0) &&
            kotlin.math.abs(nowPlaying.positionMs - previous.positionMs) < 2_000
        ) return
        runCatching { objc?.setNowPlaying(nowPlaying) }
    }

    override fun stop() {
        keepAwake(false)
        runCatching { objc?.setNowPlaying(null) }
    }

    private fun onCommand(action: PlayerAction) = SwingUtilities.invokeLater { perform(action) }

    private fun keepAwake(on: Boolean) {
        if (on == (assertion != 0)) return
        runCatching {
            if (on) {
                val id = IntByReference()
                val type = CF.string("PreventUserIdleDisplaySleep")
                val reason = CF.string("KS JaiPlayer is playing a video")
                if (IOKit.INSTANCE.IOPMAssertionCreateWithName(type, ASSERTION_LEVEL_ON, reason, id) == 0) assertion = id.value
                CF.release(type); CF.release(reason)
            } else {
                IOKit.INSTANCE.IOPMAssertionRelease(assertion)
                assertion = 0
            }
        }
    }

    @Suppress("FunctionName")
    private interface IOKit : Library {
        fun IOPMAssertionCreateWithName(type: Pointer, level: Int, name: Pointer, id: IntByReference): Int
        fun IOPMAssertionRelease(id: Int): Int

        companion object {
            val INSTANCE: IOKit by lazy { Native.load("IOKit", IOKit::class.java) }
        }
    }

    private object CF {
        private val lib = NativeLibrary.getInstance("CoreFoundation")
        private const val UTF8 = 0x08000100

        fun string(s: String): Pointer =
            lib.getFunction("CFStringCreateWithCString").invokePointer(arrayOf(Pointer.NULL, s, UTF8))

        fun release(p: Pointer) { lib.getFunction("CFRelease").invokeVoid(arrayOf(p)) }
    }

    private companion object {
        const val ASSERTION_LEVEL_ON = 255
    }
}

/**
 * The few Objective-C calls Now Playing needs. `objc_msgSend` is called with a fixed argument
 * list for each shape (never as a variadic function, which arm64 passes differently).
 */
private class ObjC {
    private val runtime = NativeLibrary.getInstance("objc")
    private val mediaPlayer = NativeLibrary.getInstance("/System/Library/Frameworks/MediaPlayer.framework/MediaPlayer")
    private val msgSend = runtime.getFunction("objc_msgSend")

    private fun cls(name: String): Pointer = runtime.getFunction("objc_getClass").invokePointer(arrayOf(name))
    private fun sel(name: String): Pointer = runtime.getFunction("sel_registerName").invokePointer(arrayOf(name))
    private fun send(target: Pointer?, selector: String, vararg args: Any?): Pointer? =
        target?.let { msgSend.invokePointer(arrayOf(it, sel(selector), *args)) }
    private fun sendLong(target: Pointer?, selector: String, vararg args: Any?): Long =
        target?.let { msgSend.invokeLong(arrayOf(it, sel(selector), *args)) } ?: 0L

    private fun nsString(s: String): Pointer? = send(cls("NSString"), "stringWithUTF8String:", s)
    private fun nsNumber(d: Double): Pointer? = send(cls("NSNumber"), "numberWithDouble:", d)
    private fun key(name: String): Pointer = mediaPlayer.getGlobalVariableAddress(name).getPointer(0)

    /** Sets (or clears, for null) the system's Now Playing entry. */
    fun setNowPlaying(info: NowPlaying?) {
        val center = send(cls("MPNowPlayingInfoCenter"), "defaultCenter") ?: return
        if (info == null) {
            send(center, "setNowPlayingInfo:", Pointer.NULL)
            sendLong(center, "setPlaybackState:", STATE_STOPPED)
            return
        }
        val dict = send(cls("NSMutableDictionary"), "dictionary") ?: return
        fun put(value: Pointer?, keyName: String) { if (value != null) send(dict, "setObject:forKey:", value, key(keyName)) }
        put(nsString(info.title ?: ""), "MPMediaItemPropertyTitle")
        put(nsNumber(info.durationMs / 1000.0), "MPMediaItemPropertyPlaybackDuration")
        put(nsNumber(info.positionMs / 1000.0), "MPNowPlayingInfoPropertyElapsedPlaybackTime")
        put(nsNumber(if (info.playing) info.speed.toDouble() else 0.0), "MPNowPlayingInfoPropertyPlaybackRate")
        put(nsNumber(1.0), "MPNowPlayingInfoPropertyDefaultPlaybackRate")
        send(center, "setNowPlayingInfo:", dict)
        sendLong(center, "setPlaybackState:", if (info.playing) STATE_PLAYING else STATE_PAUSED)
        nextPrevious?.invoke(info.hasNext, info.hasPrevious)
    }

    private var nextPrevious: ((Boolean, Boolean) -> Unit)? = null
    private val keep = mutableListOf<Any>() // callbacks and objects libobjc holds on to

    /** Routes the system's media commands (keys, Control Centre, AirPods) to [onAction]. */
    fun registerCommands(onAction: (PlayerAction) -> Unit) {
        val center = send(cls("MPRemoteCommandCenter"), "sharedCommandCenter") ?: return
        val nsObject = cls("NSObject")
        val targetClass = runtime.getFunction("objc_allocateClassPair").invokePointer(arrayOf(nsObject, "KSJaiPlayerRemoteTarget", 0L))
            ?: cls("KSJaiPlayerRemoteTarget")
        val handlers = mapOf(
            "playCommand" to { _: Pointer? -> onAction(PlayerAction.TogglePlay) },
            "pauseCommand" to { _: Pointer? -> onAction(PlayerAction.TogglePlay) },
            "togglePlayPauseCommand" to { _: Pointer? -> onAction(PlayerAction.TogglePlay) },
            "nextTrackCommand" to { _: Pointer? -> onAction(PlayerAction.NextItem) },
            "previousTrackCommand" to { _: Pointer? -> onAction(PlayerAction.PreviousItem) },
            "skipForwardCommand" to { _: Pointer? -> onAction(PlayerAction.SeekForward) },
            "skipBackwardCommand" to { _: Pointer? -> onAction(PlayerAction.SeekBack) },
        )
        val selectors = handlers.keys.associateWith { "ks_$it:" }
        for ((command, handler) in handlers) {
            val imp = RemoteHandler { _, _, event -> runCatching { handler(event) }; HANDLER_SUCCESS }
            keep += imp
            runtime.getFunction("class_addMethod").invokeInt(arrayOf(targetClass, sel(selectors.getValue(command)), imp, "q@:@"))
        }
        runtime.getFunction("objc_registerClassPair").invokeVoid(arrayOf(targetClass))
        val target = send(send(targetClass, "alloc"), "init") ?: return
        keep += target
        for (command in handlers.keys) {
            val cmd = send(center, command) ?: continue
            if (command == "skipForwardCommand" || command == "skipBackwardCommand") {
                send(cmd, "setPreferredIntervals:", send(cls("NSArray"), "arrayWithObject:", nsNumber(10.0)))
            }
            send(cmd, "addTarget:action:", target, sel(selectors.getValue(command)))
            send(cmd, "setEnabled:", 1L)
        }
        nextPrevious = { next, previous ->
            send(send(center, "nextTrackCommand"), "setEnabled:", if (next) 1L else 0L)
            send(send(center, "previousTrackCommand"), "setEnabled:", if (previous) 1L else 0L)
        }
    }

    /** An Objective-C method body: (self, _cmd, MPRemoteCommandEvent) → MPRemoteCommandHandlerStatus. */
    fun interface RemoteHandler : Callback {
        fun invoke(self: Pointer?, cmd: Pointer?, event: Pointer?): Long
    }

    private companion object {
        const val STATE_PLAYING = 1L
        const val STATE_PAUSED = 2L
        const val STATE_STOPPED = 3L
        const val HANDLER_SUCCESS = 0L
    }
}

private const val PINCH_THRESHOLD = 0.25
private const val PINCH_GAP_MS = 300L
