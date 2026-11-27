package com.ks.jaiplayer.windows

import com.ks.jaiplayer.desktop.IntegrationLabels
import com.ks.jaiplayer.desktop.NowPlaying
import com.ks.jaiplayer.player.PlayerAction
import com.sun.jna.Callback
import com.sun.jna.Function
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import com.sun.jna.ptr.PointerByReference
import java.awt.BasicStroke
import java.awt.Color
import java.awt.RenderingHints
import java.awt.geom.Path2D
import java.awt.image.BufferedImage

/**
 * The play/pause, previous and next buttons under the player's thumbnail when the pointer rests
 * on its taskbar button (`ITaskbarList3::ThumbBarAddButtons`).
 *
 * Windows reports a click as a `WM_COMMAND` (`THBN_CLICKED`) to the player's window. The window
 * belongs to AWT, so instead of replacing its window procedure the app watches its thread's
 * messages through two hooks (`WH_CALLWNDPROC` for sent and `WH_GETMESSAGE` for posted
 * messages). A hook only looks: every message goes on to the window unchanged
 * (`CallNextHookEx`), so a mistake here cannot stop the window from working.
 *
 * Slots, IDs and struct layouts follow the Windows SDK (`shobjidl_core.h`, `winuser.h`). Any
 * failure leaves the taskbar without the buttons; `JAIPLAYER_NO_THUMBBAR=1` turns them off.
 */
internal class ThumbBar private constructor(
    private val taskbar: Pointer,
    private val hwnd: Pointer,
    private val labels: IntegrationLabels,
) {
    private val icons = mapOf(
        BUTTON_PREVIOUS to icon(::drawPrevious),
        BUTTON_PLAY to icon(::drawPlay),
        BUTTON_PAUSE to icon(::drawPause),
        BUTTON_NEXT to icon(::drawNext),
    )
    private var added = false
    private var last: Triple<Boolean, Boolean, Boolean>? = null

    /** Adds the buttons (once the taskbar button exists), then keeps them in step with playback. */
    fun update(info: NowPlaying?) {
        val playing = info?.playing == true
        val state = Triple(playing, info?.hasPrevious == true, info?.hasNext == true)
        if (added && state == last) return
        val buttons = buttons(state.first, state.second, state.third, enabled = info != null)
        val hr = call(taskbar, if (added) THUMB_BAR_UPDATE_BUTTONS else THUMB_BAR_ADD_BUTTONS, hwnd, 3, buttons)
        if (hr >= 0) { added = true; last = state }
    }

    /** Windows recreated the taskbar button (Explorer restarted): the buttons go on again. */
    fun taskbarButtonCreated() { added = false; last = null }

    private fun buttons(playing: Boolean, previous: Boolean, next: Boolean, enabled: Boolean): Memory {
        val m = Memory(3L * BUTTON_SIZE).apply { clear() }
        fun put(i: Int, id: Int, iconKey: Int, tip: String, on: Boolean) {
            val o = i.toLong() * BUTTON_SIZE
            m.setInt(o, THB_ICON or THB_TOOLTIP or THB_FLAGS)
            m.setInt(o + 4, id)
            m.setPointer(o + 16, icons.getValue(iconKey))
            val chars = tip.take(259).toCharArray()
            chars.forEachIndexed { k, c -> m.setChar(o + 24 + k * 2L, c) }
            m.setInt(o + 544, if (on) THBF_ENABLED else THBF_DISABLED)
        }
        put(0, BUTTON_PREVIOUS, BUTTON_PREVIOUS, labels.previous, enabled && previous)
        put(1, BUTTON_PLAY, if (playing) BUTTON_PAUSE else BUTTON_PLAY, if (playing) labels.pause else labels.play, enabled)
        put(2, BUTTON_NEXT, BUTTON_NEXT, labels.next, enabled && next)
        return m
    }

    companion object {
        fun create(hwnd: Pointer, labels: IntegrationLabels, onAction: (PlayerAction) -> Unit): ThumbBar? {
            if (System.getenv("JAIPLAYER_NO_THUMBBAR") == "1") return null
            return runCatching {
                val ole = NativeLibrary.getInstance("ole32")
                ole.getFunction("CoInitializeEx").invokeInt(arrayOf(Pointer.NULL, COINIT_APARTMENTTHREADED)) // other modes are fine
                val ref = PointerByReference()
                check(ole.getFunction("CoCreateInstance").invokeInt(
                    arrayOf(MediaControls.guid(CLSID_TASKBAR_LIST), Pointer.NULL, CLSCTX_INPROC_SERVER, MediaControls.guid(IID_TASKBAR_LIST3), ref),
                ))
                val taskbar = ref.value
                check(call(taskbar, HR_INIT))
                val bar = ThumbBar(taskbar, hwnd, labels)
                Hooks.install(hwnd, onButton = { id ->
                    when (id) {
                        BUTTON_PLAY -> onAction(PlayerAction.TogglePlay)
                        BUTTON_PREVIOUS -> onAction(PlayerAction.PreviousItem)
                        BUTTON_NEXT -> onAction(PlayerAction.NextItem)
                    }
                }, onTaskbarButtonCreated = bar::taskbarButtonCreated)
                bar
            }.onFailure { System.err.println("KS JaiPlayer: taskbar buttons are not available ($it)") }.getOrNull()
        }

        private fun call(obj: Pointer, slot: Int, vararg args: Any?): Int {
            val fn = Function.getFunction(obj.getPointer(0).getPointer(slot.toLong() * Native.POINTER_SIZE), Function.ALT_CONVENTION)
            return fn.invokeInt(arrayOf(obj, *args))
        }

        private fun check(hr: Int) { if (hr < 0) error("HRESULT 0x%08x".format(hr)) }

        private const val CLSID_TASKBAR_LIST = "56fdf344-fd6d-11d0-958a-006097c9a090"
        private const val IID_TASKBAR_LIST3 = "ea1afb91-9e28-4b86-90e9-9e9f8a5eefaf"
        // IUnknown 0–2, ITaskbarList 3–7, ITaskbarList2 8, ITaskbarList3 9 on.
        private const val HR_INIT = 3
        private const val THUMB_BAR_ADD_BUTTONS = 15
        private const val THUMB_BAR_UPDATE_BUTTONS = 16
        /** THUMBBUTTON: dwMask, iId, iBitmap, (pad), hIcon at 16, szTip[260] at 24, dwFlags at 544; 552 bytes. */
        private const val BUTTON_SIZE = 552
        private const val THB_ICON = 2
        private const val THB_TOOLTIP = 4
        private const val THB_FLAGS = 8
        private const val THBF_ENABLED = 0
        private const val THBF_DISABLED = 1
        private const val COINIT_APARTMENTTHREADED = 2
        private const val CLSCTX_INPROC_SERVER = 1
        internal const val BUTTON_PREVIOUS = 1
        internal const val BUTTON_PLAY = 2
        internal const val BUTTON_NEXT = 3
        private const val BUTTON_PAUSE = 4 // an icon only; the button keeps the play id
    }
}

/**
 * Two thread hooks on the window's thread that only watch for the thumbnail buttons' clicks and
 * for "TaskbarButtonCreated". They hold no state of the window and always pass messages on.
 */
private object Hooks {
    fun interface HookProc : Callback {
        fun invoke(code: Int, wParam: Pointer?, lParam: Pointer?): Pointer?
    }

    private val keep = mutableListOf<Any>()

    fun install(hwnd: Pointer, onButton: (Int) -> Unit, onTaskbarButtonCreated: () -> Unit) {
        val user32 = NativeLibrary.getInstance("user32")
        val callNext = user32.getFunction("CallNextHookEx")
        val thread = user32.getFunction("GetWindowThreadProcessId").invokeInt(arrayOf(hwnd, Pointer.NULL))
        check(thread != 0) { "no window thread" }
        val created = user32.getFunction("RegisterWindowMessageW").invokeInt(arrayOf(com.sun.jna.WString("TaskbarButtonCreated")))

        fun handle(target: Pointer?, message: Int, wParam: Long) {
            if (target != hwnd) return
            if (message == WM_COMMAND && (wParam ushr 16 and 0xFFFF) == THBN_CLICKED.toLong()) {
                val id = (wParam and 0xFFFF).toInt()
                javax.swing.SwingUtilities.invokeLater { onButton(id) }
            } else if (created != 0 && message == created) {
                javax.swing.SwingUtilities.invokeLater(onTaskbarButtonCreated)
            }
        }

        // Sent messages: lParam is a CWPSTRUCT (lParam 0, wParam 8, message 16, hwnd 24).
        val sent = HookProc { code, w, l ->
            if (code >= 0 && l != null) runCatching { handle(l.getPointer(24), l.getInt(16), Pointer.nativeValue(l.getPointer(8))) }
            callNext.invokePointer(arrayOf(Pointer.NULL, code, w, l))
        }
        // Posted messages: lParam is a MSG (hwnd 0, message 8, wParam 16); only when it is taken off the queue.
        val posted = HookProc { code, w, l ->
            if (code >= 0 && l != null && Pointer.nativeValue(w) == PM_REMOVE) {
                runCatching { handle(l.getPointer(0), l.getInt(8), Pointer.nativeValue(l.getPointer(16))) }
            }
            callNext.invokePointer(arrayOf(Pointer.NULL, code, w, l))
        }
        val setHook = user32.getFunction("SetWindowsHookExW")
        val h1 = setHook.invokePointer(arrayOf(WH_CALLWNDPROC, sent, Pointer.NULL, thread))
        val h2 = setHook.invokePointer(arrayOf(WH_GETMESSAGE, posted, Pointer.NULL, thread))
        check(h1 != null || h2 != null) { "hooks not installed" }
        keep.addAll(listOf(sent, posted))
    }

    private const val WH_GETMESSAGE = 3
    private const val WH_CALLWNDPROC = 4
    private const val WM_COMMAND = 0x0111
    private const val THBN_CLICKED = 0x1800
    private const val PM_REMOVE = 1L
}

// ---- Icons: white glyphs drawn at the system's small-icon size, as 32-bit icons with alpha ----

private fun icon(draw: (java.awt.Graphics2D, Int) -> Unit): Pointer {
    val user32 = NativeLibrary.getInstance("user32")
    val gdi32 = NativeLibrary.getInstance("gdi32")
    val size = user32.getFunction("GetSystemMetrics").invokeInt(arrayOf(SM_CXSMICON)).takeIf { it > 0 } ?: 16
    val image = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
    image.createGraphics().apply {
        setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        color = Color.WHITE
        draw(this, size)
        dispose()
    }
    // BITMAPINFOHEADER: 40 bytes; a negative height makes the rows run top to bottom.
    val header = Memory(40).apply {
        clear()
        setInt(0, 40); setInt(4, size); setInt(8, -size); setShort(12, 1); setShort(14, 32)
    }
    val bits = PointerByReference()
    val color = gdi32.getFunction("CreateDIBSection").invokePointer(arrayOf(Pointer.NULL, header, 0, bits, Pointer.NULL, 0))
        ?: error("CreateDIBSection failed")
    val pixels = image.getRGB(0, 0, size, size, null, 0, size)
    bits.value.write(0, pixels, 0, pixels.size) // ARGB ints are BGRA bytes in memory, as the DIB wants
    val mask = gdi32.getFunction("CreateBitmap").invokePointer(arrayOf(size, size, 1, 1, Pointer.NULL)) ?: error("CreateBitmap failed")
    // ICONINFO: fIcon, xHotspot, yHotspot, (pad), hbmMask at 16, hbmColor at 24.
    val info = Memory(32).apply {
        clear(); setInt(0, 1); setPointer(16, mask); setPointer(24, color)
    }
    return user32.getFunction("CreateIconIndirect").invokePointer(arrayOf(info)) ?: error("CreateIconIndirect failed")
}

private fun drawPlay(g: java.awt.Graphics2D, s: Int) {
    g.fill(Path2D.Float().apply { moveTo(s * 0.3, s * 0.2); lineTo(s * 0.8, s * 0.5); lineTo(s * 0.3, s * 0.8); closePath() })
}

private fun drawPause(g: java.awt.Graphics2D, s: Int) {
    g.fillRect((s * 0.28).toInt(), (s * 0.2).toInt(), (s * 0.15).toInt().coerceAtLeast(2), (s * 0.6).toInt())
    g.fillRect((s * 0.57).toInt(), (s * 0.2).toInt(), (s * 0.15).toInt().coerceAtLeast(2), (s * 0.6).toInt())
}

private fun drawNext(g: java.awt.Graphics2D, s: Int) {
    g.fill(Path2D.Float().apply { moveTo(s * 0.22, s * 0.2); lineTo(s * 0.65, s * 0.5); lineTo(s * 0.22, s * 0.8); closePath() })
    g.stroke = BasicStroke((s * 0.12f).coerceAtLeast(1.5f))
    g.drawLine((s * 0.75).toInt(), (s * 0.2).toInt(), (s * 0.75).toInt(), (s * 0.8).toInt())
}

private fun drawPrevious(g: java.awt.Graphics2D, s: Int) {
    g.fill(Path2D.Float().apply { moveTo(s * 0.78, s * 0.2); lineTo(s * 0.35, s * 0.5); lineTo(s * 0.78, s * 0.8); closePath() })
    g.stroke = BasicStroke((s * 0.12f).coerceAtLeast(1.5f))
    g.drawLine((s * 0.25).toInt(), (s * 0.2).toInt(), (s * 0.25).toInt(), (s * 0.8).toInt())
}

private const val SM_CXSMICON = 49
