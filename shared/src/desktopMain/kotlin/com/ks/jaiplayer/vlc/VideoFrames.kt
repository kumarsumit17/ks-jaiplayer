package com.ks.jaiplayer.vlc

import androidx.compose.runtime.mutableLongStateOf
import com.sun.jna.Memory
import com.sun.jna.Pointer

/**
 * Receives decoded frames from libVLC's video callbacks and hands the newest one to the surface,
 * without copying it.
 *
 * Three native buffers rotate: libVLC decodes into one, one holds the newest finished frame, and
 * one is on screen. The surface draws straight from the buffer's memory, and that buffer is not
 * given back to libVLC until the surface has moved on to a newer frame (the window may render
 * a recorded frame after the draw call returns). The decoder and the window never wait for each
 * other: the lock only guards the three indices. The pixel format is `RV32`, which is BGRA in
 * memory on every platform the app runs on.
 */
internal class VideoFrames {
    /** One decoded frame: its memory (kept referenced while shown) and its size. */
    class Frame(val memory: Memory, val width: Int, val height: Int)

    private val lock = Any()
    @Volatile var width = 0; private set
    @Volatile var height = 0; private set

    private var buffers: Array<Memory> = emptyArray()
    private var writing = -1
    private var latest = -1
    private var shown: Frame? = null
    private var shownIndex = -1

    /** Bumped for every frame; the surface reads it while drawing, so only drawing repeats. */
    val version = mutableLongStateOf(0L)

    // JNA callbacks must stay strongly referenced while libVLC can call them.
    val format = LibVlc.FormatCallback { _, chroma, w, h, pitches, lines ->
        val fw = w.getInt(0)
        val fh = h.getInt(0)
        chroma.write(0, "RV32".encodeToByteArray(), 0, 4)
        pitches.setInt(0, fw * 4)
        lines.setInt(0, fh)
        synchronized(lock) {
            buffers = Array(BUFFERS) { Memory(fw.toLong() * fh * 4) }
            writing = -1
            latest = -1
            shownIndex = -1 // The old shown frame keeps its own memory alive until replaced.
            width = fw
            height = fh
        }
        1 // One picture at a time: libVLC locks, fills and displays it before the next lock.
    }

    val cleanup = LibVlc.CleanupCallback { clear() }

    val lockFrame = LibVlc.LockCallback { _, planes ->
        val memory = synchronized(lock) {
            val i = (0 until buffers.size).firstOrNull { it != latest && it != shownIndex }
            writing = i ?: -1
            i?.let { buffers[it] }
        }
        planes.setPointer(0, memory ?: Pointer.NULL)
        null
    }

    val display = LibVlc.DisplayCallback { _, _ ->
        val delivered = synchronized(lock) {
            if (writing < 0) return@synchronized false
            latest = writing
            writing = -1
            true
        }
        if (delivered) {
            version.longValue++
            FrameStats.frameDecoded()
        }
    }

    /**
     * The frame to draw now: the newest one, which stays reserved until the next call, so its
     * memory is not overwritten while the window renders it. Null when there is no picture.
     */
    fun frameToDraw(): Frame? = synchronized(lock) {
        if (latest >= 0 && latest != shownIndex) {
            shownIndex = latest
            shown = Frame(buffers[latest], width, height)
        }
        shown?.takeIf { width > 0 }
    }

    fun clear() {
        synchronized(lock) {
            width = 0
            height = 0
            buffers = emptyArray()
            writing = -1
            latest = -1
            shownIndex = -1
            shown = null
        }
        version.longValue++
    }

    private companion object {
        const val BUFFERS = 3
    }
}
