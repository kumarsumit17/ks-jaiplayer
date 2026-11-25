package com.ks.jaiplayer.vlc

import com.ks.jaiplayer.desktop.mediaRefOf
import com.ks.jaiplayer.player.PlaybackStatus
import com.sun.jna.Callback
import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import java.lang.management.ManagementFactory
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.test.Test

/**
 * Engine comparison (specs/engine-benchmark.md): plays each file with one engine and prints CPU
 * use, pictures per second and time to the first picture. Both engines hand decoded pictures to
 * the app as BGRA in memory, the way the app draws them. Runs only when asked:
 *
 * JAIPLAYER_BENCH=vlc | mpv-sw | mpv-hw   which engine and decoding
 * JAIPLAYER_BENCH_MEDIA=<file>[,<file>…]
 * JAIPLAYER_TEST_VLC=<bundled vlc folder>  (vlc; default: the copy bundleVlc makes)
 * JAIPLAYER_MPV_LIB=<folder with libmpv.2.dylib and its libraries>  (mpv-*)
 */
class EngineBenchmarkTest {
    private val engine = System.getenv("JAIPLAYER_BENCH")
    private val files = System.getenv("JAIPLAYER_BENCH_MEDIA")?.split(',')?.map(::File).orEmpty()
    private val seconds = 15

    @Test fun benchmark(): Unit = runBlocking {
        if (engine == null || files.isEmpty()) return@runBlocking println("Skipped: JAIPLAYER_BENCH not set")
        for (file in files) {
            val result = when (engine) {
                "vlc" -> vlc(file)
                "mpv-sw" -> mpv(file, "no")
                "mpv-hw" -> mpv(file, "videotoolbox-copy")
                else -> error("unknown engine $engine")
            }
            println("BENCH $engine ${file.name}: $result")
        }
    }

    private fun cpuNanos(): Long =
        (ManagementFactory.getOperatingSystemMXBean() as com.sun.management.OperatingSystemMXBean).processCpuTime

    private fun report(firstMs: Long, frames: Int, cpu: Long, wall: Long, dropped: String) =
        "first picture ${firstMs} ms, ${"%.1f".format(frames * 1e9 / wall)} pictures/s, " +
            "CPU ${"%.0f".format(cpu * 100.0 / wall)}% of one core, dropped $dropped"

    // ---- libVLC, exactly as the macOS app configures it ----------------------------------------
    private suspend fun vlc(file: File): String {
        val dir = System.getenv("JAIPLAYER_TEST_VLC")?.let(::File)
            ?: File("../desktop/mac/build/vlc-resources/common/vlc").absoluteFile
        val engine = VlcPlayerEngine.create(
            listOf(VlcLocation(File(dir, "lib"), File(dir, "plugins"))),
            listOf("--codec=avcodec,all", "--avcodec-hw=none", VlcPlayerEngine.STATS_ARG),
        )
        try {
            val start = System.nanoTime()
            engine.open(mediaRefOf(file))
            withTimeout(30_000) { engine.state.first { it.status == PlaybackStatus.Playing && it.hasVideo } }
            val firstMs = (System.nanoTime() - start) / 1_000_000
            delay(3_000)
            val s0 = engine.stats()!!
            val c0 = cpuNanos(); val w0 = System.nanoTime()
            delay(seconds * 1_000L)
            val s1 = engine.stats()!!
            val cpu = cpuNanos() - c0; val wall = System.nanoTime() - w0
            return report(firstMs, s1.displayedPictures - s0.displayedPictures, cpu, wall, "${s1.lostPictures - s0.lostPictures}")
        } finally {
            engine.release()
        }
    }

    // ---- libmpv, software render API (vo=libmpv), hwdec as given ----------------------------------
    @Suppress("FunctionName")
    private interface Mpv : Library {
        fun mpv_create(): Pointer?
        fun mpv_set_option_string(h: Pointer, name: String, value: String): Int
        fun mpv_initialize(h: Pointer): Int
        fun mpv_command(h: Pointer, args: Array<String?>): Int
        fun mpv_get_property_string(h: Pointer, name: String): Pointer?
        fun mpv_free(p: Pointer)
        fun mpv_terminate_destroy(h: Pointer)
        fun mpv_render_context_create(res: Array<Pointer?>, h: Pointer, params: Pointer): Int
        fun mpv_render_context_set_update_callback(ctx: Pointer, cb: UpdateCallback, data: Pointer?)
        fun mpv_render_context_update(ctx: Pointer): Long
        fun mpv_render_context_render(ctx: Pointer, params: Pointer): Int
        fun mpv_render_context_free(ctx: Pointer)
    }

    fun interface UpdateCallback : Callback { fun invoke(data: Pointer?) }

    private fun Mpv.property(h: Pointer, name: String): String? =
        mpv_get_property_string(h, name)?.let { p -> p.getString(0).also { mpv_free(p) } }

    /** An mpv_render_param array: {int type; void *data} pairs, ending with type 0. */
    private fun params(vararg entries: Pair<Int, Pointer?>): Memory {
        val mem = Memory(16L * (entries.size + 1)).apply { clear() }
        entries.forEachIndexed { i, (type, data) -> mem.setInt(16L * i, type); mem.setPointer(16L * i + 8, data) }
        return mem
    }

    private fun loadMpv(): Mpv {
        val dir = File(checkNotNull(System.getenv("JAIPLAYER_MPV_LIB")) { "JAIPLAYER_MPV_LIB not set" })
        // Its libraries refer to each other by @rpath; load them by path until all are in.
        var pending = dir.listFiles { f -> f.name.endsWith(".dylib") }!!.toMutableList()
        while (pending.isNotEmpty()) {
            val next = pending.filter { runCatching { NativeLibrary.getInstance(it.path) }.isFailure }
            check(next.size < pending.size) { "cannot load ${next.map { it.name }}" }
            pending = next.toMutableList()
        }
        return Native.load(File(dir, "libmpv.2.dylib").path, Mpv::class.java)
    }

    private fun mpv(file: File, hwdec: String): String {
        val mpv = loadMpv()
        val h = checkNotNull(mpv.mpv_create())
        for ((k, v) in listOf("vo" to "libmpv", "hwdec" to hwdec, "terminal" to "no", "idle" to "yes")) {
            mpv.mpv_set_option_string(h, k, v)
        }
        check(mpv.mpv_initialize(h) >= 0)
        val apiType = Memory(3).apply { setString(0, "sw") }
        val ctxOut = arrayOfNulls<Pointer>(1)
        check(mpv.mpv_render_context_create(ctxOut, h, params(1 to apiType)) >= 0) { "render context" }
        val ctx = ctxOut[0]!!
        val wake = Semaphore(0)
        val callback = UpdateCallback { wake.release() }
        mpv.mpv_render_context_set_update_callback(ctx, callback, null)

        val frames = AtomicInteger()
        val firstNs = java.util.concurrent.atomic.AtomicLong()
        val running = java.util.concurrent.atomic.AtomicBoolean(true)
        val start = System.nanoTime()
        mpv.mpv_command(h, arrayOf("loadfile", file.path, null))
        val renderer = thread(name = "mpv-render") {
            var buffer: Memory? = null
            var w = 0; var hgt = 0
            val format = Memory(5).apply { setString(0, "bgr0") }
            while (running.get()) {
                if (!wake.tryAcquire(100, TimeUnit.MILLISECONDS)) continue
                if (mpv.mpv_render_context_update(ctx) and 1L == 0L) continue
                val vw = mpv.property(h, "dwidth")?.toIntOrNull() ?: continue
                val vh = mpv.property(h, "dheight")?.toIntOrNull() ?: continue
                if (vw != w || vh != hgt) { w = vw; hgt = vh; buffer = Memory(w.toLong() * hgt * 4) }
                val size = Memory(8).apply { setInt(0, w); setInt(4, hgt) }
                val stride = Memory(8).apply { setLong(0, w * 4L) }
                mpv.mpv_render_context_render(ctx, params(17 to size, 18 to format, 19 to stride, 20 to buffer))
                if (frames.incrementAndGet() == 1) firstNs.set(System.nanoTime())
            }
        }
        try {
            while (frames.get() == 0 && System.nanoTime() - start < 30_000_000_000L) Thread.sleep(10)
            val firstMs = (firstNs.get() - start) / 1_000_000
            Thread.sleep(3_000)
            val f0 = frames.get(); val d0 = drops(mpv, h)
            val c0 = cpuNanos(); val w0 = System.nanoTime()
            Thread.sleep(seconds * 1_000L)
            val f1 = frames.get(); val d1 = drops(mpv, h)
            val cpu = cpuNanos() - c0; val wall = System.nanoTime() - w0
            val used = mpv.property(h, "hwdec-current") ?: "?"
            return report(firstMs, f1 - f0, cpu, wall, "${d1 - d0} (hwdec in use: $used)")
        } finally {
            running.set(false)
            renderer.join()
            mpv.mpv_render_context_free(ctx)
            mpv.mpv_terminate_destroy(h)
            callback.hashCode() // keep the callback reachable until the context is gone
        }
    }

    private fun drops(mpv: Mpv, h: Pointer): Int =
        (mpv.property(h, "frame-drop-count")?.toIntOrNull() ?: 0) + (mpv.property(h, "decoder-frame-drop-count")?.toIntOrNull() ?: 0)
}
