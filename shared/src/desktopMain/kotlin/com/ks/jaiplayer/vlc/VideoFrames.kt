package com.ks.jaiplayer.vlc

import androidx.compose.runtime.mutableLongStateOf
import com.sun.jna.Memory
import com.sun.jna.Pointer

/**
 * Receives decoded frames from libVLC's video callbacks and keeps the latest one for drawing.
 *
 * libVLC decodes into [buffer] (native memory) on its own thread; on `display` the frame is
 * copied into [pixels] under [lock], and [version] changes so the surface redraws. The pixel
 * format is `RV32`, which is BGRA in memory on every platform the app runs on.
 */
internal class VideoFrames {
    val lock = Any()
    @Volatile var width = 0; private set
    @Volatile var height = 0; private set
    var pixels = ByteArray(0); private set

    /** Bumped for every frame; the surface reads it while drawing, so only drawing repeats. */
    val version = mutableLongStateOf(0L)

    private var buffer: Memory? = null

    // JNA callbacks must stay strongly referenced while libVLC can call them.
    val format = LibVlc.FormatCallback { _, chroma, w, h, pitches, lines ->
        val fw = w.getInt(0)
        val fh = h.getInt(0)
        chroma.write(0, "RV32".encodeToByteArray(), 0, 4)
        pitches.setInt(0, fw * 4)
        lines.setInt(0, fh)
        synchronized(lock) {
            buffer = Memory(fw.toLong() * fh * 4)
            pixels = ByteArray(fw * fh * 4)
            width = fw
            height = fh
        }
        1
    }

    val cleanup = LibVlc.CleanupCallback { clear() }

    val lockFrame = LibVlc.LockCallback { _, planes ->
        planes.setPointer(0, buffer ?: Pointer.NULL)
        null
    }

    val display = LibVlc.DisplayCallback { _, _ ->
        val b = buffer
        if (b != null) {
            synchronized(lock) { if (pixels.size.toLong() == b.size()) b.read(0, pixels, 0, pixels.size) }
            version.longValue++
        }
    }

    fun clear() {
        synchronized(lock) {
            width = 0
            height = 0
            buffer = null
            pixels = ByteArray(0)
        }
        version.longValue++
    }
}
