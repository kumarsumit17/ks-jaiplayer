package com.ks.jaiplayer.windows

import com.ks.jaiplayer.desktop.NowPlaying
import com.ks.jaiplayer.player.PlayerAction
import com.sun.jna.CallbackReference
import com.sun.jna.Function
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import com.sun.jna.WString
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.LongByReference
import com.sun.jna.ptr.PointerByReference
import com.sun.jna.win32.StdCallLibrary
import kotlin.math.abs

/**
 * Windows' System Media Transport Controls (SMTC): the media overlay that appears with the
 * volume keys, the keyboard's media keys, and the lock screen show the title, playing or
 * paused, and the position, and their play, pause, next and previous buttons reach the player.
 *
 * Called through JNA as WinRT COM interfaces, with no native code to build. The interface IDs
 * and method slots follow Microsoft's Windows SDK metadata (`Windows.Media`,
 * `systemmediatransportcontrolsinterop.h`). Every call checks its HRESULT; [create] returns null
 * if any step fails, and the app then simply has no media overlay. `JAIPLAYER_NO_SMTC=1` turns
 * it off for diagnosis.
 *
 * All calls but the button handler come on the window's thread.
 */
internal class MediaControls private constructor(
    private val smtc: Pointer,
    private val video: Pointer,
    private val updater: Pointer,
    private val smtc2: Pointer?,
    private val timeline: Pointer?,
) {
    private var title: String? = null
    private var playing: Boolean? = null
    private var next: Boolean? = null
    private var previous: Boolean? = null
    private var lastPositionMs = Long.MIN_VALUE
    private var lastDurationMs = -1L
    private var rate = -1f

    /** Whether the player is playing, for the overlay's separate play and pause buttons. */
    @Volatile var isPlaying = false
        private set

    fun update(info: NowPlaying?) {
        if (info == null) {
            if (playing != null) {
                call(smtc, SMTC_SET_PLAYBACK_STATUS, STATUS_STOPPED)
                call(updater, UPDATER_CLEAR_ALL)
                call(updater, UPDATER_UPDATE)
                title = null; playing = null; lastDurationMs = -1
            }
            isPlaying = false
            return
        }
        isPlaying = info.playing
        if (info.title != title) {
            title = info.title
            call(updater, UPDATER_SET_TYPE, TYPE_VIDEO)
            withHString(info.title ?: "") { call(video, VIDEO_SET_TITLE, it) }
            call(updater, UPDATER_UPDATE)
        }
        if (info.playing != playing) {
            playing = info.playing
            call(smtc, SMTC_SET_PLAYBACK_STATUS, if (info.playing) STATUS_PLAYING else STATUS_PAUSED)
        }
        if (info.hasNext != next) { next = info.hasNext; call(smtc, SMTC_SET_NEXT_ENABLED, flag(info.hasNext)) }
        if (info.hasPrevious != previous) { previous = info.hasPrevious; call(smtc, SMTC_SET_PREVIOUS_ENABLED, flag(info.hasPrevious)) }
        // The overlay moves its own position forward from the rate; resend only on jumps and changes.
        if (smtc2 != null && timeline != null && info.durationMs > 0) {
            val jumped = abs(info.positionMs - lastPositionMs) > 2_000
            if (jumped || info.durationMs != lastDurationMs) {
                val end = info.durationMs * TICKS_PER_MS
                call(timeline, TIMELINE_SET_START, 0L)
                call(timeline, TIMELINE_SET_END, end)
                call(timeline, TIMELINE_SET_MIN_SEEK, 0L)
                call(timeline, TIMELINE_SET_MAX_SEEK, end)
                call(timeline, TIMELINE_SET_POSITION, info.positionMs * TICKS_PER_MS)
                call(smtc2, SMTC2_UPDATE_TIMELINE, timeline)
                lastDurationMs = info.durationMs
            }
            lastPositionMs = info.positionMs
            if (info.speed != rate) { rate = info.speed; call(smtc2, SMTC2_SET_PLAYBACK_RATE, info.speed.toDouble()) }
        }
    }

    fun close() = runCatching { call(smtc, SMTC_SET_ENABLED, flag(false)) }

    companion object {
        /** Connects the overlay to [hwnd], or returns null when Windows does not offer it or a step fails. */
        fun create(hwnd: Pointer, onAction: (PlayerAction, MediaControls) -> Unit): MediaControls? {
            if (System.getenv("JAIPLAYER_NO_SMTC") == "1") return null
            return runCatching {
                combase.getFunction("RoInitialize").invokeInt(arrayOf(RO_INIT_MULTITHREADED)) // RPC_E_CHANGED_MODE is fine
                val interop = PointerByReference()
                withHString("Windows.Media.SystemMediaTransportControls") {
                    check(combase.getFunction("RoGetActivationFactory").invokeInt(arrayOf(it, guid(IID_INTEROP), interop)))
                }
                val smtcRef = PointerByReference()
                check(call(interop.value, INTEROP_GET_FOR_WINDOW, hwnd, guid(IID_SMTC), smtcRef))
                release(interop.value)
                val smtc = smtcRef.value

                val updaterRef = PointerByReference()
                check(call(smtc, SMTC_GET_DISPLAY_UPDATER, updaterRef))
                val updater = updaterRef.value
                check(call(updater, UPDATER_SET_TYPE, TYPE_VIDEO))
                val videoRef = PointerByReference()
                check(call(updater, UPDATER_GET_VIDEO_PROPERTIES, videoRef))

                // The timeline (position on the overlay) is optional: Windows 10 1607 and later.
                val smtc2 = queryInterface(smtc, IID_SMTC2)
                val timeline = runCatching {
                    val instance = PointerByReference()
                    withHString("Windows.Media.SystemMediaTransportControlsTimelineProperties") {
                        check(combase.getFunction("RoActivateInstance").invokeInt(arrayOf(it, instance)))
                    }
                    queryInterface(instance.value, IID_TIMELINE).also { release(instance.value) }
                }.getOrNull()

                val controls = MediaControls(smtc, videoRef.value, updater, smtc2, timeline)
                check(call(smtc, SMTC_SET_ENABLED, flag(true)))
                check(call(smtc, SMTC_SET_PLAY_ENABLED, flag(true)))
                check(call(smtc, SMTC_SET_PAUSE_ENABLED, flag(true)))
                check(call(smtc, SMTC_SET_FAST_FORWARD_ENABLED, flag(true)))
                check(call(smtc, SMTC_SET_REWIND_ENABLED, flag(true)))
                val token = LongByReference()
                val handler = ButtonHandler.create { button ->
                    // Play and Pause are separate buttons: each acts only when it changes something.
                    val action = when (button) {
                        BUTTON_PLAY -> PlayerAction.TogglePlay.takeIf { !controls.isPlaying }
                        BUTTON_PAUSE -> PlayerAction.TogglePlay.takeIf { controls.isPlaying }
                        BUTTON_FAST_FORWARD -> PlayerAction.SeekForward
                        BUTTON_REWIND -> PlayerAction.SeekBack
                        BUTTON_NEXT -> PlayerAction.NextItem
                        BUTTON_PREVIOUS -> PlayerAction.PreviousItem
                        else -> null
                    }
                    action?.let { onAction(it, controls) }
                }
                check(call(smtc, SMTC_ADD_BUTTON_PRESSED, handler, token))
                controls
            }.onFailure { System.err.println("KS JaiPlayer: Windows media controls are not available ($it)") }.getOrNull()
        }

        private val combase: NativeLibrary by lazy { NativeLibrary.getInstance("combase") }

        /** Calls method [slot] of the COM object [obj] (its vtable's slot-th entry); returns the HRESULT. */
        internal fun call(obj: Pointer, slot: Int, vararg args: Any?): Int {
            val vtable = obj.getPointer(0)
            val fn = Function.getFunction(vtable.getPointer(slot.toLong() * Native.POINTER_SIZE), Function.ALT_CONVENTION)
            return fn.invokeInt(arrayOf(obj, *args))
        }

        private fun release(obj: Pointer) { call(obj, 2) }

        private fun queryInterface(obj: Pointer, iid: String): Pointer? {
            val out = PointerByReference()
            return if (call(obj, 0, guid(iid), out) >= 0) out.value else null
        }

        private fun check(hr: Int) { if (hr < 0) error("HRESULT 0x%08x".format(hr)) }

        private inline fun <T> withHString(text: String, block: (Pointer) -> T): T {
            val ref = PointerByReference()
            check(combase.getFunction("WindowsCreateString").invokeInt(arrayOf(WString(text), text.length, ref)))
            try {
                return block(ref.value)
            } finally {
                combase.getFunction("WindowsDeleteString").invokeInt(arrayOf(ref.value))
            }
        }

        /** WinRT `boolean` is one byte. */
        private fun flag(on: Boolean): Byte = if (on) 1 else 0

        /** A GUID in memory: Data1 (4 bytes), Data2 and Data3 (2 each) little-endian, then 8 bytes as written. */
        internal fun guid(text: String): Memory {
            val hex = text.replace("-", "")
            val m = Memory(16)
            m.setInt(0, hex.substring(0, 8).toLong(16).toInt())
            m.setShort(4, hex.substring(8, 12).toInt(16).toShort())
            m.setShort(6, hex.substring(12, 16).toInt(16).toShort())
            for (i in 0 until 8) m.setByte(8L + i, hex.substring(16 + i * 2, 18 + i * 2).toInt(16).toByte())
            return m
        }

        internal fun sameGuid(p: Pointer, text: String): Boolean = p.getByteArray(0, 16).contentEquals(guid(text).getByteArray(0, 16))

        // Interface IDs (Windows SDK metadata).
        private const val IID_INTEROP = "ddb0472d-c911-4a1f-86d9-dc3d71a95f5a"
        private const val IID_SMTC = "99fa3ff4-1742-42a6-902e-087d41f965ec"
        private const val IID_SMTC2 = "ea98d2f6-7f3c-4af2-a586-72889808efb1"
        private const val IID_TIMELINE = "5125316a-c3a2-475b-8507-93534dc88f15"
                /** TypedEventHandler<SystemMediaTransportControls, SystemMediaTransportControlsButtonPressedEventArgs>. */
        internal const val IID_BUTTON_HANDLER = "0557e996-7b23-5bae-aa81-ea0d671143a4"

        // Method slots: IUnknown 0–2, IInspectable 3–5, then each interface's methods in order.
        private const val INTEROP_GET_FOR_WINDOW = 6
        private const val SMTC_SET_PLAYBACK_STATUS = 7
        private const val SMTC_GET_DISPLAY_UPDATER = 8
        private const val SMTC_SET_ENABLED = 11
        private const val SMTC_SET_PLAY_ENABLED = 13
        private const val SMTC_SET_PAUSE_ENABLED = 17
        private const val SMTC_SET_FAST_FORWARD_ENABLED = 21
        private const val SMTC_SET_REWIND_ENABLED = 23
        private const val SMTC_SET_PREVIOUS_ENABLED = 25
        private const val SMTC_SET_NEXT_ENABLED = 27
        private const val SMTC_ADD_BUTTON_PRESSED = 32
        private const val SMTC2_SET_PLAYBACK_RATE = 11
        private const val SMTC2_UPDATE_TIMELINE = 12
        private const val UPDATER_SET_TYPE = 7
        private const val UPDATER_GET_VIDEO_PROPERTIES = 13
        private const val UPDATER_CLEAR_ALL = 16
        private const val UPDATER_UPDATE = 17
        private const val VIDEO_SET_TITLE = 7
        private const val TIMELINE_SET_START = 7
        private const val TIMELINE_SET_END = 9
        private const val TIMELINE_SET_MIN_SEEK = 11
        private const val TIMELINE_SET_MAX_SEEK = 13
        private const val TIMELINE_SET_POSITION = 15

        /** SystemMediaTransportControlsButton values. */
        private const val BUTTON_PLAY = 0
        private const val BUTTON_PAUSE = 1
        private const val BUTTON_FAST_FORWARD = 4
        private const val BUTTON_REWIND = 5
        private const val BUTTON_NEXT = 6
        private const val BUTTON_PREVIOUS = 7

        private const val RO_INIT_MULTITHREADED = 1
        private const val TYPE_VIDEO = 2
        private const val STATUS_STOPPED = 2
        private const val STATUS_PLAYING = 3
        private const val STATUS_PAUSED = 4
        /** TimeSpan counts 100-nanosecond ticks. */
        private const val TICKS_PER_MS = 10_000L
    }
}

/**
 * The COM delegate Windows calls when an overlay button or media key is pressed: an object whose
 * first field points to a vtable of QueryInterface, AddRef, Release and Invoke. It lives for the
 * whole run, so AddRef and Release do no counting.
 */
private object ButtonHandler {
    fun interface QueryInterface : StdCallLibrary.StdCallCallback {
        fun invoke(self: Pointer, riid: Pointer, out: PointerByReference): Int
    }

    fun interface RefCount : StdCallLibrary.StdCallCallback {
        fun invoke(self: Pointer): Int
    }

    fun interface Invoke : StdCallLibrary.StdCallCallback {
        fun invoke(self: Pointer, sender: Pointer?, args: Pointer?): Int
    }

    private val keep = mutableListOf<Any>()

    /** A handler object that passes each pressed button (SystemMediaTransportControlsButton) to [onButton]. */
    fun create(onButton: (Int) -> Unit): Pointer {
        val qi = QueryInterface { self, riid, out ->
            val ok = MediaControls.sameGuid(riid, IID_UNKNOWN) || MediaControls.sameGuid(riid, IID_AGILE) ||
                MediaControls.sameGuid(riid, MediaControls.IID_BUTTON_HANDLER)
            out.value = if (ok) self else Pointer.NULL
            if (ok) 0 else E_NOINTERFACE
        }
        val addRef = RefCount { 1 }
        val release = RefCount { 1 }
        val invoke = Invoke { _, _, args ->
            runCatching {
                val button = IntByReference()
                if (args != null && MediaControls.call(args, ARGS_GET_BUTTON, button) >= 0) onButton(button.value)
            }
            0
        }
        val vtable = Memory(4L * Native.POINTER_SIZE)
        listOf(qi, addRef, release, invoke).forEachIndexed { i, cb ->
            vtable.setPointer(i.toLong() * Native.POINTER_SIZE, CallbackReference.getFunctionPointer(cb))
        }
        val obj = Memory(Native.POINTER_SIZE.toLong()).apply { setPointer(0, vtable) }
        keep.addAll(listOf(qi, addRef, release, invoke, vtable, obj))
        return obj
    }

    private const val ARGS_GET_BUTTON = 6
    private const val IID_UNKNOWN = "00000000-0000-0000-c000-000000000046"
    private const val IID_AGILE = "94ea2b94-e9cc-49e0-c0ff-ee64ca8f5b90"
    private const val E_NOINTERFACE = 0x80004002.toInt()
}
