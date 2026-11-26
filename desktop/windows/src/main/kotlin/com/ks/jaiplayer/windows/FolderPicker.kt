package com.ks.jaiplayer.windows

import com.sun.jna.Function
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import com.sun.jna.ptr.PointerByReference
import java.awt.Frame
import java.io.File

/**
 * The Windows folder picker (`IFileOpenDialog` with `FOS_PICKFOLDERS`), the one Explorer and other
 * apps use, instead of Swing's chooser. COM through JNA; the method slots and IDs follow the
 * Windows SDK (`shobjidl_core.h`). The dialog runs on its own COM thread, owned by the player's
 * window; [onPicked] gets the folder, or null if it was cancelled or the dialog is unavailable
 * (then [fallback] is shown instead).
 */
internal object FolderPicker {
    fun pick(owner: Frame?, fallback: () -> File?, onPicked: (File?) -> Unit) {
        val hwnd = owner?.let { runCatching { Native.getWindowPointer(it) }.getOrNull() }
        Thread({
            val result = runCatching { show(hwnd) }
            javax.swing.SwingUtilities.invokeLater {
                result.fold(onSuccess = onPicked, onFailure = {
                    System.err.println("KS JaiPlayer: the Windows folder picker is not available ($it)")
                    onPicked(fallback())
                })
            }
        }, "folder-picker").apply { isDaemon = true }.start()
    }

    /** Shows the dialog; returns the chosen folder, null if cancelled; throws if COM fails. */
    private fun show(owner: Pointer?): File? {
        val ole = NativeLibrary.getInstance("ole32")
        ole.getFunction("CoInitializeEx").invokeInt(arrayOf(Pointer.NULL, COINIT_APARTMENTTHREADED))
        try {
            val dialogRef = PointerByReference()
            check(ole.getFunction("CoCreateInstance").invokeInt(
                arrayOf(guid(CLSID_FILE_OPEN_DIALOG), Pointer.NULL, CLSCTX_INPROC_SERVER, guid(IID_FILE_OPEN_DIALOG), dialogRef),
            ))
            val dialog = dialogRef.value
            try {
                val options = Memory(4)
                check(call(dialog, GET_OPTIONS, options))
                check(call(dialog, SET_OPTIONS, options.getInt(0) or FOS_PICKFOLDERS or FOS_FORCEFILESYSTEM))
                val hr = call(dialog, SHOW, owner ?: Pointer.NULL)
                if (hr == ERROR_CANCELLED) return null
                check(hr)
                val itemRef = PointerByReference()
                check(call(dialog, GET_RESULT, itemRef))
                val item = itemRef.value
                try {
                    val pathRef = PointerByReference()
                    check(call(item, GET_DISPLAY_NAME, SIGDN_FILESYSPATH, pathRef))
                    val path = pathRef.value.getWideString(0)
                    ole.getFunction("CoTaskMemFree").invokeVoid(arrayOf(pathRef.value))
                    return File(path)
                } finally {
                    call(item, RELEASE)
                }
            } finally {
                call(dialog, RELEASE)
            }
        } finally {
            ole.getFunction("CoUninitialize").invokeVoid(arrayOf())
        }
    }

    private fun call(obj: Pointer, slot: Int, vararg args: Any?): Int {
        val fn = Function.getFunction(obj.getPointer(0).getPointer(slot.toLong() * Native.POINTER_SIZE), Function.ALT_CONVENTION)
        return fn.invokeInt(arrayOf(obj, *args))
    }

    private fun check(hr: Int) { if (hr < 0) error("HRESULT 0x%08x".format(hr)) }

    private fun guid(text: String) = MediaControls.guid(text)

    private const val CLSID_FILE_OPEN_DIALOG = "dc1c5a9c-e88a-4dde-a5a1-60f82a20aef7"
    private const val IID_FILE_OPEN_DIALOG = "d57c7288-d4ad-4768-be02-9d969532d960"
    // IUnknown 0–2, IModalWindow::Show 3, then IFileDialog in order; IShellItem::GetDisplayName 5.
    private const val RELEASE = 2
    private const val SHOW = 3
    private const val SET_OPTIONS = 9
    private const val GET_OPTIONS = 10
    private const val GET_RESULT = 20
    private const val GET_DISPLAY_NAME = 5
    private const val FOS_PICKFOLDERS = 0x20
    private const val FOS_FORCEFILESYSTEM = 0x40
    private const val SIGDN_FILESYSPATH = 0x80058000.toInt()
    private const val COINIT_APARTMENTTHREADED = 2
    private const val CLSCTX_INPROC_SERVER = 1
    /** HRESULT_FROM_WIN32(ERROR_CANCELLED): the user closed the dialog. */
    private const val ERROR_CANCELLED = 0x800704C7.toInt()
}
