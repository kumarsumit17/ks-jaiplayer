package com.ks.jaiplayer.ui

import com.ks.jaiplayer.vlc.VideoColour
import com.ks.jaiplayer.vlc.VideoFrames
import com.ks.jaiplayer.vlc.VideoFrames.Chroma
import com.sun.jna.Memory
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Color
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Surface
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Both frame layouts asked of libVLC (I420 and P010) turn into the same colours on screen: limited-range white, black and BT.601 red, drawn through the real shader
 * onto a CPU surface. HDR (PQ and HLG) is tone-mapped to match [HdrReference] within 3 of 255.
 */
class VideoColourTest {
    private data class Yuv(val y: Int, val u: Int, val v: Int)

    // 8-bit limited-range values; P010 uses them times four.
    private val white = Yuv(235, 128, 128) to intArrayOf(255, 255, 255)
    private val black = Yuv(16, 128, 128) to intArrayOf(0, 0, 0)
    private val red = Yuv(81, 90, 240) to intArrayOf(255, 0, 0)

    @Test
    fun everyLayoutGivesTheSameColours() {
        for (chroma in Chroma.entries) {
            for ((yuv, rgb) in listOf(white, black, red)) {
                val got = render(chroma, yuv)
                for (i in 0..2) {
                    assertTrue(abs(got[i] - rgb[i]) <= 3, "$chroma $yuv: got ${got.toList()}, want ${rgb.toList()}")
                }
            }
        }
    }

    @Test
    fun p010FiltersLikeI420() {
        // A ramp in luma and both chroma planes, drawn scaled down and up: the P010 shader joins
        // bytes and filters itself, and must land where the GPU-filtered 8-bit path does.
        for (scale in listOf(0.5f, 1f, 1.75f)) {
            val reference = renderRamp(Chroma.I420, scale)
            val got = renderRamp(Chroma.P010, scale)
            val worst = reference.indices.maxOf { abs(reference[it] - got[it]) }
            assertTrue(worst <= 4, "P010 at ×$scale differs from I420 by up to $worst")
        }
    }

    @Test
    fun theLayoutFollowsTheDecoder() {
        assertEquals(Chroma.I420, Chroma.forProposed("CVPN"))
        assertEquals(Chroma.P010, Chroma.forProposed("CVPP"))
        assertEquals(Chroma.I420, Chroma.forProposed("I420"))
        assertEquals(Chroma.I420, Chroma.forProposed("I0AL"))
        assertEquals(Chroma.I420, Chroma.forProposed("RV32"))
    }

    private val hdr10 = VideoColour(primaries = 9, transfer = VideoColour.PQ, matrix = 9, peakNits = 1000f)

    /** 10-bit PQ code values for test colours: (Y, Cb, Cr) in BT.2020 limited range, 0–1023. */
    private val pqSamples = listOf(
        Triple(64, 512, 512), // black
        Triple(509, 512, 512), // grey at about 100 nits
        Triple(668, 512, 512), // about 1000 nits
        Triple(940, 512, 512), // 10,000 nits
        Triple(400, 420, 700), // a saturated red-orange
        Triple(500, 640, 470), // a blue
    )

    @Test
    fun hdr10MatchesTheReference() {
        for (colour in listOf(hdr10, hdr10.copy(peakNits = 0f), hdr10.copy(transfer = VideoColour.HLG, peakNits = 0f))) {
            for ((y, u, v) in pqSamples) {
                val want = HdrReference.toScreen(y, u, v, colour)
                // P010 carries the 10 bits exactly; I420 gets them rounded to 8 bits, as libVLC's conversion does.
                for (chroma in Chroma.entries) {
                    val got = render10(chroma, y, u, v, colour)
                    val tolerance = if (chroma == Chroma.P010) 3 else 6
                    for (i in 0..2) assertTrue(abs(got[i] - want[i]) <= tolerance, "$chroma ${colour.transfer} ($y,$u,$v): got ${got.toList()}, want ${want.toList()}")
                }
            }
        }
    }

    @Test
    fun hdrIsNoLongerWashedOutAndBrighterInputStaysBrighter() {
        // The bug: PQ drawn as if it were SDR comes out washed out. Mapped, a saturated colour keeps
        // much more of its saturation (the spread between its brightest and dimmest channel).
        fun spread(c: IntArray) = c.max() - c.min()
        val pale = spread(render10(Chroma.P010, 400, 420, 700, VideoColour.Unknown))
        val mapped = spread(render10(Chroma.P010, 400, 420, 700, hdr10))
        assertTrue(mapped > pale + 40, "mapped spread $mapped, pale spread $pale")
        val steps = listOf(64, 300, 509, 668, 800, 940).map { render10(Chroma.P010, it, 512, 512, hdr10)[1] }
        assertEquals(steps.sorted(), steps, "brightness must rise with the signal: $steps")
        assertEquals(0, steps.first())
    }

    /** Draws one 10-bit colour ([y], [u], [v] are 0–1023) in [chroma]'s layout, tagged with [colour]. */
    private fun render10(chroma: Chroma, y: Int, u: Int, v: Int, colour: VideoColour): IntArray {
        val size = 16
        val l = VideoFrames.Layout(size, size, chroma, colour)
        val mem = Memory(l.bytes)
        if (chroma == Chroma.P010) {
            for (row in 0 until l.lumaLines) for (x in 0 until size) mem.setShort(row.toLong() * l.lumaPitch + x * 2, (y shl 6).toShort())
            for (row in 0 until l.chromaLines) for (x in 0 until l.chromaWidth) {
                val at = l.uOffset + row.toLong() * l.chromaPitch + x * 4
                mem.setShort(at, (u shl 6).toShort()); mem.setShort(at + 2, (v shl 6).toShort())
            }
        } else {
            fun eight(x: Int) = ((x + 2) / 4).coerceAtMost(255).toByte()
            for (row in 0 until l.lumaLines) for (x in 0 until size) mem.setByte(row.toLong() * l.lumaPitch + x, eight(y))
            for (row in 0 until l.chromaLines) for (x in 0 until l.chromaWidth) {
                mem.setByte(l.uOffset + row.toLong() * l.chromaPitch + x, eight(u))
                mem.setByte(l.vOffset + row.toLong() * l.chromaPitch + x, eight(v))
            }
        }
        val surface = Surface.makeRasterN32Premul(size, size)
        drawVideoFrame(surface.canvas, VideoFrames.Frame(mem, l), 0f, 0f, 1f, Paint())
        val bitmap = Bitmap()
        bitmap.allocN32Pixels(size, size)
        surface.readPixels(bitmap, 0, 0)
        val p = bitmap.getColor(8, 8)
        return intArrayOf(Color.getR(p), Color.getG(p), Color.getB(p))
    }

    /** Fills a 16×16 frame with one colour in [chroma]'s layout, draws it, returns its RGB. */
    private fun render(chroma: Chroma, c: Yuv): IntArray {
        val px = draw(chroma, 16, 1f) { _, _, _ -> intArrayOf(c.y, c.u, c.v) }
        val i = (8 * 16 + 8) * 3
        return intArrayOf(px[i], px[i + 1], px[i + 2])
    }

    /** A 32×32 ramp (luma left to right, Cb top to bottom, Cr diagonal), every pixel's RGB. */
    private fun renderRamp(chroma: Chroma, scale: Float): IntArray =
        draw(chroma, 32, scale) { x, y, plane ->
            intArrayOf(16 + x * 219 / 31, 16 + y * 224 / 15, 16 + (x + y) * 224 / 46).also { if (plane == 0) it[1] = 0 }
        }

    /**
     * Fills a [size]×[size] frame in [chroma]'s layout ([yuv] gives 8-bit Y, Cb, Cr for a luma or
     * chroma position; plane 0 is luma), draws it scaled by [scale], returns RGB for every pixel.
     */
    private fun draw(chroma: Chroma, size: Int, scale: Float, yuv: (Int, Int, Int) -> IntArray): IntArray {
        val l = VideoFrames.Layout(size, size, chroma)
        val mem = Memory(l.bytes)
        val wide = chroma == Chroma.P010
        fun sample(offset: Long, value8: Int) {
            if (wide) mem.setShort(offset, ((value8 * 4) shl 6).toShort()) else mem.setByte(offset, value8.toByte())
        }
        val bps = chroma.bytesPerSample
        for (row in 0 until l.lumaLines) for (x in 0 until size) {
            sample(row.toLong() * l.lumaPitch + x * bps, yuv(x, minOf(row, size - 1), 0)[0])
        }
        for (row in 0 until l.chromaLines) for (x in 0 until l.chromaWidth) {
            val c = yuv(x, minOf(row, l.chromaHeight - 1), 1)
            if (wide) {
                val at = l.uOffset + row.toLong() * l.chromaPitch + x * 2 * bps
                sample(at, c[1])
                sample(at + bps, c[2])
            } else {
                sample(l.uOffset + row.toLong() * l.chromaPitch + x, c[1])
                sample(l.vOffset + row.toLong() * l.chromaPitch + x, c[2])
            }
        }
        val out = (size * scale).toInt()
        val surface = Surface.makeRasterN32Premul(out, out)
        drawVideoFrame(surface.canvas, VideoFrames.Frame(mem, l), 0f, 0f, scale, Paint())
        val bitmap = Bitmap()
        bitmap.allocN32Pixels(out, out)
        surface.readPixels(bitmap, 0, 0)
        return IntArray(out * out * 3) { i ->
            val p = bitmap.getColor((i / 3) % out, (i / 3) / out)
            when (i % 3) { 0 -> Color.getR(p); 1 -> Color.getG(p); else -> Color.getB(p) }
        }
    }
}
