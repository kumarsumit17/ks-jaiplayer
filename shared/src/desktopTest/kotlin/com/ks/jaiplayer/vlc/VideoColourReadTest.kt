package com.ks.jaiplayer.vlc

import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** HDR is recognised from what MP4 and Matroska files state about their colours. */
class VideoColourReadTest {
    private val files = mutableListOf<File>()

    @AfterTest
    fun cleanUp() = files.forEach { it.delete() }

    private fun file(bytes: ByteArray, ext: String) = File.createTempFile("colour", ".$ext").also { it.writeBytes(bytes); files += it }.path

    private fun u16(v: Int) = byteArrayOf((v shr 8).toByte(), v.toByte())
    private fun u32(v: Long) = byteArrayOf((v shr 24).toByte(), (v shr 16).toByte(), (v shr 8).toByte(), v.toByte())

    private fun box(type: String, vararg body: ByteArray): ByteArray {
        val payload = body.fold(ByteArray(0)) { a, b -> a + b }
        return u32(8L + payload.size) + type.toByteArray() + payload
    }

    /** ftyp, a large mdat, then moov at the end (as many encoders write them). */
    private fun mp4(vararg sampleEntryBoxes: ByteArray): ByteArray =
        box("ftyp", "isom".toByteArray(), u32(0)) + box("mdat", ByteArray(5000)) +
            box("moov", box("trak", box("mdia", box("minf", box("stbl", box("stsd", u32(0), u32(1), box("hvc1", ByteArray(78), *sampleEntryBoxes)))))))

    @Test
    fun mp4Hdr10WithLightLevels() {
        val colr = box("colr", "nclx".toByteArray(), u16(9), u16(16), u16(9), byteArrayOf(0))
        val mdcv = box("mdcv", ByteArray(16), u32(10_000_000L), u32(50))
        val clli = box("clli", u16(800), u16(400))
        val c = VideoColour.read(file(mp4(colr, mdcv, clli), "mp4"))
        assertEquals(VideoColour(primaries = 9, transfer = 16, matrix = 9, fullRange = false, peakNits = 800f), c)
        assertTrue(c.isHdr)
        assertEquals(8f, c.toneMapPeak)
    }

    @Test
    fun mp4MasteringPeakWhenNoContentLightLevel() {
        val colr = box("colr", "nclx".toByteArray(), u16(9), u16(18), u16(9), byteArrayOf(0x80.toByte()))
        val mdcv = box("mdcv", ByteArray(16), u32(10_000_000L), u32(50))
        val c = VideoColour.read(file(mp4(colr, mdcv), "mp4"))
        assertEquals(VideoColour(9, 18, 9, fullRange = true, peakNits = 1000f), c)
    }

    @Test
    fun mp4WithoutColourIsUnknown() {
        val c = VideoColour.read(file(mp4(), "mp4"))
        assertEquals(VideoColour.Unknown, c)
        assertFalse(c.needsMapping)
    }

    // ---- Matroska ----

    private fun id(v: Long): ByteArray = when {
        v > 0xFFFFFF -> u32(v)
        v > 0xFFFF -> byteArrayOf((v shr 16).toByte(), (v shr 8).toByte(), v.toByte())
        v > 0xFF -> u16(v.toInt())
        else -> byteArrayOf(v.toByte())
    }

    /** An element with an 8-byte size, or an unknown size. */
    private fun el(idValue: Long, vararg body: ByteArray, unknownSize: Boolean = false): ByteArray {
        val payload = body.fold(ByteArray(0)) { a, b -> a + b }
        val size = if (unknownSize) ByteArray(8) { 0xFF.toByte() }.also { it[0] = 0x01 }
        else ByteArray(8).also { s -> s[0] = 0x01; for (k in 1..7) s[k] = (payload.size.toLong() shr (8 * (7 - k))).toByte() }
        return id(idValue) + size + payload
    }

    private fun uint(idValue: Long, v: Long) = el(idValue, u32(v))
    private fun float(idValue: Long, v: Float) = el(idValue, u32(java.lang.Float.floatToIntBits(v).toLong() and 0xFFFFFFFFL))

    private fun mkv(colour: ByteArray?, unknownSegment: Boolean = false): ByteArray {
        val audio = el(0xAE, uint(0x83, 2))
        val video = el(0xAE, uint(0x83, 1), el(0xE0, uint(0xB0, 3840), *(colour?.let { arrayOf(it) } ?: emptyArray())))
        val out = ByteArrayOutputStream()
        out.write(el(0x1A45DFA3, uint(0x4282, 0)))
        out.write(el(0x18538067, el(0x1549A966, uint(0x2AD7B1, 1_000_000)), el(0x1654AE6B, audio, video), el(0x1F43B675, ByteArray(100)), unknownSize = unknownSegment))
        return out.toByteArray()
    }

    @Test
    fun matroskaHdr10() {
        val colour = el(0x55B0, uint(0x55B1, 9), uint(0x55B9, 1), uint(0x55BA, 16), uint(0x55BB, 9), el(0x55D0, float(0x55D9, 4000f)))
        assertEquals(VideoColour(9, 16, 9, fullRange = false, peakNits = 4000f), VideoColour.read(file(mkv(colour), "mkv")))
    }

    @Test
    fun matroskaUnknownSizeSegmentAndMaxCll() {
        val colour = el(0x55B0, uint(0x55BA, 18), uint(0x55BB, 9), uint(0x55B9, 2), uint(0x55BC, 600), el(0x55D0, float(0x55D9, 1000f)))
        assertEquals(VideoColour(9, 18, 0, fullRange = true, peakNits = 600f), VideoColour.read(file(mkv(colour, unknownSegment = true), "mkv")))
    }

    @Test
    fun matroskaWithoutColourAndOtherFilesAreUnknown() {
        assertEquals(VideoColour.Unknown, VideoColour.read(file(mkv(null), "mkv")))
        assertEquals(VideoColour.Unknown, VideoColour.read(file("not a video at all".toByteArray(), "txt")))
        assertEquals(VideoColour.Unknown, VideoColour.read("/no/such/file.mkv"))
    }

    @Test
    fun bt2020SdrIsConvertedButNotToneMapped() {
        val c = VideoColour(primaries = 9, transfer = 14, matrix = 9)
        assertTrue(c.needsMapping)
        assertFalse(c.isHdr)
        assertFalse(VideoColour(primaries = 1, transfer = 1, matrix = 1).needsMapping)
    }
}
