package com.ks.jaiplayer.vlc

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * For measuring smoothness: with JAIPLAYER_PERF=1 the app prints, once a second, how many frames
 * libVLC delivered, how many the window drew, and the slowest draw. Off (and free) otherwise.
 */
internal object FrameStats {
    val enabled = System.getenv("JAIPLAYER_PERF") == "1"
    private val decoded = AtomicInteger()
    private val drawn = AtomicInteger()
    private val slowestDrawNs = AtomicLong()
    @Volatile private var windowStart = System.nanoTime()

    fun frameDecoded() {
        if (enabled) decoded.incrementAndGet()
    }

    fun frameDrawn(drawNs: Long) {
        if (!enabled) return
        drawn.incrementAndGet()
        slowestDrawNs.accumulateAndGet(drawNs, ::maxOf)
        val now = System.nanoTime()
        if (now - windowStart >= 1_000_000_000L) {
            windowStart = now
            System.err.println(
                "jaiplayer-perf decoded=${decoded.getAndSet(0)} drawn=${drawn.getAndSet(0)} " +
                    "slowestDrawMs=${"%.2f".format(slowestDrawNs.getAndSet(0) / 1e6)}",
            )
        }
    }
}
