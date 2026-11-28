package com.ks.jaiplayer.vlc

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/** Native video layer trial: the raw HDR metadata handed to macOS is found in the file, and the trial is off by default. */
class NativeVideoLayerTest {
    private fun box(type: String, payload: ByteArray): ByteArray {
        val size = 8 + payload.size
        return byteArrayOf((size shr 24).toByte(), (size shr 16).toByte(), (size shr 8).toByte(), size.toByte()) + type.toByteArray() + payload
    }

    @Test
    fun findsMasteringAndLightLevelPayloads() {
        val mastering = ByteArray(24) { (it + 1).toByte() }
        val light = byteArrayOf(0x03, 0xE8.toByte(), 0x01, 0x90.toByte())
        val f = File.createTempFile("hdr", ".mp4")
        try {
            // "mdcv" also appears as stray bytes with the wrong size before the real box.
            f.writeBytes(box("ftyp", "isom".toByteArray()) + "xxmdcvxx".toByteArray() + box("mdat", ByteArray(4000)) +
                box("mdcv", mastering) + box("clli", light))
            val (m, l) = NativeVideoLayer.hdrBoxes(f.path)
            assertContentEquals(mastering, m)
            assertContentEquals(light, l)
        } finally {
            f.delete()
        }
    }

    @Test
    fun noBoxesNoMetadata() {
        val f = File.createTempFile("sdr", ".mp4")
        try {
            f.writeBytes(box("ftyp", "isom".toByteArray()) + box("mdat", ByteArray(100)))
            val (m, l) = NativeVideoLayer.hdrBoxes(f.path)
            assertNull(m)
            assertNull(l)
        } finally {
            f.delete()
        }
    }

    @Test
    fun offUnlessAskedFor() {
        if (System.getenv("JAIPLAYER_VIDEO_LAYER") == null) assertFalse(NativeVideoLayer.enabled)
    }
}
