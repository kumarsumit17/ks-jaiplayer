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
    /** The route on trial: `avsb` (A), `metal` (B) or `vout` (C, libVLC draws); null when the trial is off. */
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

    /** Route C: libVLC draws through its caopengllayer output (not in the bundled copy: run with JAIPLAYER_VLC_PATH). */
    val libVlcDraws: Boolean get() = route == "vout"

    /** Extra libVLC arguments the route needs. */
    val vlcArgs: List<String> get() = if (enabled && libVlcDraws) listOf("--vout=caopengllayer") else emptyList()

    private var vlc: NativeLibrary? = null
    private var player: Pointer? = null

    /** Makes the layer and gives libVLC the helper's callbacks instead of [VideoFrames]'. */
    fun install(location: VlcLocation, player: Pointer) {
        val h = fn("jvl_create").invokePointer(emptyArray()) ?: return
        handle = h
        System.err.println("jvl: route $route, extra libVLC options $vlcArgs")
        val vlc = NativeLibrary.getInstance(File(location.libDir, "libvlc.dylib").path)
        this.vlc = vlc
        this.player = player
        if (libVlcDraws) {
            vlc.getFunction("libvlc_media_player_set_nsobject").invokeVoid(arrayOf(player, fn("jvl_vout_host").invokePointer(arrayOf(h))))
        } else {
            vlc.getFunction("libvlc_video_set_format_callbacks").invokeVoid(arrayOf(player, fn("jvl_format"), fn("jvl_cleanup")))
            vlc.getFunction("libvlc_video_set_callbacks").invokeVoid(arrayOf(player, fn("jvl_lock"), fn("jvl_unlock"), fn("jvl_display"), h))
            // The format callback receives a pointer to the opaque pointer, which starts as this one.
        }
        if (FrameStats.enabled) printStats()
    }

    /** With JAIPLAYER_PERF=1: the same once-a-second lines as the current path (drawn = given to the layer). */
    private fun printStats() = Thread({
        // The first frame is watched closely (every 5 ms) for the start-up time, then once a second.
        while ((stats()?.get(1) ?: 0L) == 0L) Thread.sleep(5)
        val started = ProcessHandle.current().info().startInstant().orElse(null)
        if (started != null) System.err.println("jaiplayer-startup firstFrameMs=${java.time.Duration.between(started, java.time.Instant.now()).toMillis()}")
        firstFrameAt = System.nanoTime()
        var last = stats() ?: LongArray(8)
        while (true) {
            Thread.sleep(1000)
            val now = stats() ?: continue
            System.err.println("jaiplayer-perf decoded=${now[0] - last[0]} drawn=${now[1] - last[1]} failed=${now[2] - last[2]} " +
                "intervalMaxMs=${"%.1f".format(now[6] / 1000.0)} intervalSdMs=${"%.2f".format(now[7] / 1000.0)}")
            last = now
        }
    }, "jvl-stats").apply { isDaemon = true }.start()

    @Volatile private var firstFrameAt = 0L

    /**
     * Trial only: JAIPLAYER_TRIAL_STEPS runs player actions at seconds after the first frame, for
     * checks without a person, e.g. `5:speed=0.25;10:speed=4;15:seek=2000;18:pause;20:play`.
     */
    fun runSteps(engine: com.ks.jaiplayer.player.PlayerEngine) {
        val steps = System.getenv("JAIPLAYER_TRIAL_STEPS")?.split(';')?.mapNotNull { step ->
            val (at, action) = step.split(':', limit = 2).takeIf { it.size == 2 } ?: return@mapNotNull null
            at.toDouble() to action
        } ?: return
        Thread({
            while (firstFrameAt == 0L) Thread.sleep(20)
            for ((at, action) in steps) {
                val wait = firstFrameAt + (at * 1e9).toLong() - System.nanoTime()
                if (wait > 0) Thread.sleep(wait / 1_000_000)
                val (name, value) = action.split('=', limit = 2).let { it[0] to it.getOrNull(1) }
                when (name) {
                    "speed" -> engine.setSpeed(value!!.toFloat())
                    "seek" -> engine.seekTo(value!!.toLong())
                    "pause" -> engine.pause()
                    "play" -> engine.play()
                    "subtitle" -> engine.addSubtitle(java.io.File(value!!).toURI().toString())
                }
                System.err.println("jvl-step at=$at $action")
            }
        }, "jvl-steps").apply { isDaemon = true }.start()
    }

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

    /** Decoded, displayed, failed (route C: lost), width, height, frame intervals (count, longest, deviation in µs; not for route C). */
    fun stats(): LongArray? {
        val h = handle ?: return null
        if (libVlcDraws) return vlcStats()
        val out = Memory(8 * 8)
        fn("jvl_stats").invokeVoid(arrayOf(h, out))
        return LongArray(8) { out.getLong(it * 8L) }
    }

    /** Route C: libVLC's own counters (libvlc_media_stats_t) and video size. */
    private fun vlcStats(): LongArray? {
        val l = vlc ?: return null
        val mp = player ?: return null
        val out = LongArray(8)
        val media = l.getFunction("libvlc_media_player_get_media").invokePointer(arrayOf(mp))
        if (media != null) {
            val st = Memory(15 * 4)
            if (l.getFunction("libvlc_media_get_stats").invokeInt(arrayOf(media, st)) != 0) {
                out[0] = st.getInt(6 * 4L).toLong(); out[1] = st.getInt(8 * 4L).toLong(); out[2] = st.getInt(9 * 4L).toLong()
            }
            l.getFunction("libvlc_media_release").invokeVoid(arrayOf(media))
        }
        val w = Memory(4); val hh = Memory(4)
        if (l.getFunction("libvlc_video_get_size").invokeInt(arrayOf(mp, 0, w, hh)) == 0) { out[3] = w.getInt(0).toLong(); out[4] = hh.getInt(0).toLong() }
        return out
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
