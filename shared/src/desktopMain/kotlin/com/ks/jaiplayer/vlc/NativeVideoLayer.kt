package com.ks.jaiplayer.vlc

import com.sun.jna.Function
import com.sun.jna.Memory
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import java.io.File
import java.io.RandomAccessFile

/**
 * Feasibility trial only (specs/plan-macos-playback.md, "Native video layer trial"); off unless
 * `JAIPLAYER_VIDEO_LAYER` is set, and never in an installer. The native helper
 * (`desktop/mac/trial/JaiVideoLayer.m`, built by its `build.sh`) shows frames in a macOS layer
 * under the Compose window; its C functions are libVLC's video callbacks, so the JVM does no
 * work per frame.
 */
internal object NativeVideoLayer {
    /** The route on trial: `avsb` (AVSampleBufferDisplayLayer); null when the trial is off. */
    val route: String? = System.getenv("JAIPLAYER_VIDEO_LAYER")?.takeIf { it.isNotBlank() }

    private val helper: NativeLibrary? by lazy {
        if (route == null) return@lazy null
        val path = System.getenv("JAIPLAYER_VIDEO_LAYER_LIB") ?: "desktop/mac/build/trial/libjaivideolayer.dylib"
        runCatching { NativeLibrary.getInstance(File(path).absolutePath) }
            .onFailure { System.err.println("jvl: cannot load $path: $it") }.getOrNull()
    }

    val enabled: Boolean get() = helper != null

    /** The helper's state; created on the libVLC thread (it waits for the AppKit thread). */
    @Volatile var handle: Pointer? = null; private set

    private fun fn(name: String): Function = helper!!.getFunction(name)

    /** Makes the layer and gives libVLC the helper's callbacks instead of [VideoFrames]'. */
    fun install(location: VlcLocation, player: Pointer) {
        val h = fn("jvl_create").invokePointer(emptyArray()) ?: return
        handle = h
        val vlc = NativeLibrary.getInstance(File(location.libDir, "libvlc.dylib").path)
        vlc.getFunction("libvlc_video_set_format_callbacks").invokeVoid(arrayOf(player, fn("jvl_format"), fn("jvl_cleanup")))
        vlc.getFunction("libvlc_video_set_callbacks").invokeVoid(arrayOf(player, fn("jvl_lock"), fn("jvl_unlock"), fn("jvl_display"), h))
        // The format callback receives a pointer to the opaque pointer, which starts as this one.
        if (FrameStats.enabled) printStats()
    }

    /** With JAIPLAYER_PERF=1: the same once-a-second lines as the current path (drawn = given to the layer). */
    private fun printStats() = Thread({
        var last = LongArray(5)
        var first = false
        while (true) {
            Thread.sleep(1000)
            val now = stats() ?: continue
            if (!first && now[1] > 0) {
                first = true
                val started = ProcessHandle.current().info().startInstant().orElse(null)
                if (started != null) System.err.println("jaiplayer-startup firstFrameMs=${java.time.Duration.between(started, java.time.Instant.now()).toMillis()}")
            }
            System.err.println("jaiplayer-perf decoded=${now[0] - last[0]} drawn=${now[1] - last[1]} failed=${now[2] - last[2]}")
            last = now
        }
    }, "jvl-stats").apply { isDaemon = true }.start()

    fun show(visible: Boolean, fill: Boolean) {
        val h = handle ?: return
        fn("jvl_show").invokeVoid(arrayOf(h, if (visible) 1 else 0, if (fill) 1 else 0))
    }

    /** Before playback: the file's colours and its raw HDR metadata (MP4 `mdcv` and `clli`). */
    fun setColour(colour: VideoColour, path: String?) {
        val h = handle ?: return
        val (mastering, light) = path?.let(::hdrBoxes) ?: (null to null)
        fun mem(b: ByteArray?) = b?.let { Memory(it.size.toLong()).apply { write(0, it, 0, it.size) } }
        fn("jvl_set_colour").invokeVoid(arrayOf(h, colour.primaries, colour.transfer, colour.matrix, if (colour.fullRange) 1 else 0,
            mem(mastering), mastering?.size ?: 0, mem(light), light?.size ?: 0))
    }

    /** Decoded, displayed, failed, width, height. */
    fun stats(): LongArray? {
        val h = handle ?: return null
        val out = Memory(5 * 8)
        fn("jvl_stats").invokeVoid(arrayOf(h, out))
        return LongArray(5) { out.getLong(it * 8L) }
    }

    /**
     * The payloads of the MP4 `mdcv` (24 bytes, SMPTE ST 2086) and `clli` (4 bytes) boxes, found
     * by a plain search of the file's first and last 16 MB (trial only; Matroska not covered).
     */
    internal fun hdrBoxes(path: String): Pair<ByteArray?, ByteArray?> = runCatching {
        RandomAccessFile(path, "r").use { f ->
            val window = 16L shl 20
            val parts = listOf(0L, maxOf(0L, f.length() - window)).distinct().map { start ->
                f.seek(start)
                ByteArray(minOf(window, f.length() - start).toInt()).also { f.readFully(it) }
            }
            fun box(type: String, size: Int): ByteArray? {
                val t = type.toByteArray(Charsets.ISO_8859_1)
                for (b in parts) for (i in 4..b.size - t.size - size) {
                    if (b[i] == t[0] && b[i + 1] == t[1] && b[i + 2] == t[2] && b[i + 3] == t[3] &&
                        ((b[i - 4].toInt() and 0xff) shl 24 or (b[i - 3].toInt() and 0xff shl 16) or
                            (b[i - 2].toInt() and 0xff shl 8) or (b[i - 1].toInt() and 0xff)) == size + 8
                    ) return b.copyOfRange(i + 4, i + 4 + size)
                }
                return null
            }
            box("mdcv", 24) to box("clli", 4)
        }
    }.getOrDefault(null to null)
}
