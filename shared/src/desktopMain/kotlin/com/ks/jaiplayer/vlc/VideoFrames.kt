package com.ks.jaiplayer.vlc

import androidx.compose.runtime.mutableLongStateOf
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer

/**
 * Receives decoded frames from libVLC's video callbacks and hands the newest one to the surface,
 * without copying it.
 *
 * Frames arrive as planar YUV 4:2:0 (`I420`): the surface turns them into colour on the GPU,
 * so libVLC does no colour conversion on the CPU and each frame is under half the size of an
 * RGB one. Subtitles are already blended into the planes.
 *
 * Three native buffers rotate: libVLC decodes into one, one holds the newest finished frame, and
 * one is on screen. The surface draws straight from the buffer's memory, and that buffer is not
 * given back to libVLC until the surface has moved on to a newer frame (the window may render
 * a recorded frame after the draw call returns). The decoder and the window never wait for each
 * other: the lock only guards the three indices.
 */
internal class VideoFrames {
    /** Where the three planes of a frame lie in its buffer. */
    class Layout(val width: Int, val height: Int) {
        val chromaWidth = (width + 1) / 2
        val chromaHeight = (height + 1) / 2
        val lumaPitch = align(width, 32)
        val chromaPitch = align(chromaWidth, 32)
        val lumaLines = align(height, 16)
        val chromaLines = lumaLines / 2
        val uOffset = lumaPitch.toLong() * lumaLines
        val vOffset = uOffset + chromaPitch.toLong() * chromaLines
        val bytes = vOffset + chromaPitch.toLong() * chromaLines

        private fun align(value: Int, to: Int) = (value + to - 1) / to * to
    }

    /** One decoded frame: its memory (kept referenced while shown) and its plane layout. */
    class Frame(val memory: Memory, val layout: Layout) {
        val width get() = layout.width
        val height get() = layout.height
    }

    private val lock = Any()
    @Volatile var width = 0; private set
    @Volatile var height = 0; private set

    private var layout: Layout? = null
    private var buffers: Array<Memory> = emptyArray()
    private var writing = -1
    private var latest = -1
    private var shown: Frame? = null
    private var shownIndex = -1

    /** Bumped for every frame; the surface reads it while drawing, so only drawing repeats. */
    val version = mutableLongStateOf(0L)

    // JNA callbacks must stay strongly referenced while libVLC can call them.
    val format = LibVlc.FormatCallback { _, chroma, w, h, pitches, lines ->
        val l = Layout(w.getInt(0), h.getInt(0))
        chroma.write(0, "I420".encodeToByteArray(), 0, 4)
        pitches.setInt(0, l.lumaPitch)
        pitches.setInt(4, l.chromaPitch)
        pitches.setInt(8, l.chromaPitch)
        lines.setInt(0, l.lumaLines)
        lines.setInt(4, l.chromaLines)
        lines.setInt(8, l.chromaLines)
        synchronized(lock) {
            layout = l
            buffers = Array(BUFFERS) { Memory(l.bytes) }
            writing = -1
            latest = -1
            shownIndex = -1 // The old shown frame keeps its own memory alive until replaced.
            width = l.width
            height = l.height
        }
        1 // One picture at a time: libVLC locks, fills and displays it before the next lock.
    }

    val cleanup = LibVlc.CleanupCallback { clear() }

    val lockFrame = LibVlc.LockCallback { _, planes ->
        var l: Layout? = null
        val memory = synchronized(lock) {
            val i = (0 until buffers.size).firstOrNull { it != latest && it != shownIndex }
            writing = i ?: -1
            l = layout
            i?.let { buffers[it] }
        }
        val ptr = Native.POINTER_SIZE.toLong()
        val layoutNow = l
        if (memory == null || layoutNow == null) {
            for (p in 0 until 3) planes.setPointer(p * ptr, Pointer.NULL)
        } else {
            planes.setPointer(0, memory)
            planes.setPointer(ptr, memory.share(layoutNow.uOffset))
            planes.setPointer(2 * ptr, memory.share(layoutNow.vOffset))
        }
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
            bump()
            FrameStats.frameDecoded()
        }
    }

    /**
     * The frame to draw now: the newest one, which stays reserved until the next call, so its
     * memory is not overwritten while the window renders it. Null when there is no picture.
     */
    fun frameToDraw(): Frame? = synchronized(lock) {
        val l = layout
        if (latest >= 0 && latest != shownIndex && l != null) {
            shownIndex = latest
            shown = Frame(buffers[latest], l)
        }
        shown?.takeIf { width > 0 }
    }

    fun clear() {
        synchronized(lock) {
            width = 0
            height = 0
            layout = null
            buffers = emptyArray()
            writing = -1
            latest = -1
            shownIndex = -1
            shown = null
        }
        bump()
    }

    /** Tells the surface a new frame is ready (Compose picks up this write from any thread). */
    private fun bump() {
        version.longValue++
    }

    private companion object {
        const val BUFFERS = 3
    }
}
