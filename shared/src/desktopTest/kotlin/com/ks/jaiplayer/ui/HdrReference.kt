package com.ks.jaiplayer.ui

import com.ks.jaiplayer.vlc.VideoColour
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * The HDR-to-SDR conversion in double precision, written apart from the shader, to check it:
 * BT.2020 limited-range YUV (10 bits) → R'G'B' → PQ (ST 2084) or HLG (BT.2100) to linear light,
 * 1.0 being 100 nits → highlight desaturation and the Hable curve from the peak to SDR white →
 * BT.2020 to BT.709 → sRGB, 0–255.
 */
internal object HdrReference {
    fun toScreen(y: Int, u: Int, v: Int, colour: VideoColour): IntArray {
        val yy = (y - 64) / 876.0
        val cb = (u - 512) / 896.0
        val cr = (v - 512) / 896.0
        val e = doubleArrayOf(yy + 1.4746 * cr, yy - 0.16455 * cb - 0.57135 * cr, yy + 1.8814 * cb)
        var lin = when (colour.transfer) {
            VideoColour.PQ -> DoubleArray(3) { pq(e[it]) / 100.0 }
            else -> {
                val s = DoubleArray(3) { hlg(e[it]) }
                val ys = 0.2627 * s[0] + 0.6780 * s[1] + 0.0593 * s[2]
                DoubleArray(3) { s[it] * 10.0 * max(ys, 1e-6).pow(0.2) }
            }
        }
        val sig0 = lin.max()
        if (sig0 > 1e-6) {
            val luma = 0.2627 * lin[0] + 0.6780 * lin[1] + 0.0593 * lin[2]
            val coeff = (max(sig0 - 0.18, 1e-6) / sig0).pow(HDR_DESATURATION_EXPONENT.toDouble()) * HDR_DESATURATION
            lin = DoubleArray(3) { lin[it] + (luma - lin[it]) * coeff }
            val sig = max(sig0 + (luma - sig0) * coeff, 1e-6)
            val scale = hable(sig) / hable(colour.toneMapPeak.toDouble()) / sig
            lin = DoubleArray(3) { lin[it] * scale }
        }
        val m = arrayOf(doubleArrayOf(1.6605, -0.5876, -0.0728), doubleArrayOf(-0.1246, 1.1329, -0.0083), doubleArrayOf(-0.0182, -0.1006, 1.1187))
        return IntArray(3) { i -> (255 * srgb(m[i][0] * lin[0] + m[i][1] * lin[1] + m[i][2] * lin[2])).roundToInt() }
    }

    private fun pq(e: Double): Double {
        val p = max(e, 0.0).pow(1 / 78.84375)
        return (max(p - 0.8359375, 0.0) / (18.8515625 - 18.6875 * p)).pow(1 / 0.1593017578125) * 10_000.0
    }

    private fun hlg(e0: Double): Double {
        val e = min(max(e0, 0.0), 1.0)
        return if (e <= 0.5) e * e / 3 else (exp((e - 0.55991073) / 0.17883277) + 0.28466892) / 12
    }

    private fun hable(x: Double) = (x * (0.15 * x + 0.05) + 0.004) / (x * (0.15 * x + 0.5) + 0.06) - 0.02 / 0.3

    private fun srgb(x0: Double): Double {
        val x = min(max(x0, 0.0), 1.0)
        return if (x <= 0.0031308) 12.92 * x else 1.055 * x.pow(1 / 2.4) - 0.055
    }
}
