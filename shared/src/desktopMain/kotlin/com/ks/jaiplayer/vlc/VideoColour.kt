package com.ks.jaiplayer.vlc

import java.io.File
import java.io.RandomAccessFile

/**
 * A video's colour description, as the file states it (ITU-T H.273 code points), so HDR can be
 * tone-mapped to the screen: libVLC 3 does not pass it through the video callbacks, so it is read
 * from the container (MP4/MOV `colr`, `mdcv`, `clli`; Matroska `Colour`). 0 means not stated.
 */
internal data class VideoColour(
    val primaries: Int = 0,
    val transfer: Int = 0,
    val matrix: Int = 0,
    val fullRange: Boolean = false,
    /** Brightest light in the video, in nits (MaxCLL, else the mastering display's peak); 0 if not stated. */
    val peakNits: Float = 0f,
) {
    val isHdr: Boolean get() = transfer == PQ || transfer == HLG

    /** Needs the HDR shader: HDR, or SDR in BT.2020 colours that must be brought into BT.709. */
    val needsMapping: Boolean get() = isHdr || primaries == BT2020

    /**
     * The peak the tone curve maps from, in units of 100 nits. Without metadata PQ assumes its
     * full 10,000 nits (VLC 3 does the same) and HLG its nominal 1,000 nits.
     */
    val toneMapPeak: Float
        get() = when {
            peakNits > 0f -> maxOf(peakNits / 100f, 1f)
            transfer == HLG -> 10f
            else -> 100f
        }

    companion object {
        const val BT709 = 1
        const val BT2020 = 9
        const val PQ = 16
        const val HLG = 18

        val Unknown = VideoColour()

        /** The colour description of the local file at [path]; [Unknown] when none is found. */
        fun read(path: String): VideoColour = runCatching {
            RandomAccessFile(File(path), "r").use { f ->
                if (f.length() < 12) return@use Unknown
                val head = ByteArray(8).also { f.readFully(it) }
                when {
                    u32(head, 0) == EBML_HEADER -> Matroska.read(f)
                    String(head, 4, 4, Charsets.ISO_8859_1) in MP4_FIRST_BOXES -> Mp4.read(f)
                    else -> Unknown
                }
            }
        }.getOrDefault(Unknown)

        private const val EBML_HEADER = 0x1A45DFA3L
        private val MP4_FIRST_BOXES = setOf("ftyp", "moov", "mdat", "free", "skip", "wide", "pnot")

        /** At most this much of a file's header is read: a long film's index stays well below it. */
        internal const val MAX_HEADER = 64L shl 20

        internal fun u16(b: ByteArray, at: Int) = ((b[at].toInt() and 0xFF) shl 8) or (b[at + 1].toInt() and 0xFF)
        internal fun u32(b: ByteArray, at: Int): Long = (u16(b, at).toLong() shl 16) or u16(b, at + 2).toLong()
    }
}

/** MP4 and QuickTime: the video sample entry's `colr` (nclx/nclc), with `mdcv` and `clli` beside it. */
private object Mp4 {
    fun read(f: RandomAccessFile): VideoColour {
        val length = f.length()
        var pos = 0L
        val header = ByteArray(16)
        while (pos + 8 <= length) {
            f.seek(pos)
            f.readFully(header, 0, 8)
            var size = VideoColour.u32(header, 0)
            val type = String(header, 4, 4, Charsets.ISO_8859_1)
            var body = 8L
            if (size == 1L) {
                f.readFully(header, 8, 8)
                size = (VideoColour.u32(header, 8) shl 32) or VideoColour.u32(header, 12)
                body = 16L
            } else if (size == 0L) {
                size = length - pos
            }
            if (size < body) return VideoColour.Unknown
            if (type == "moov") {
                val bytes = ByteArray(minOf(size - body, VideoColour.MAX_HEADER).toInt())
                f.seek(pos + body)
                f.readFully(bytes)
                return fromMoov(bytes)
            }
            pos += size
        }
        return VideoColour.Unknown
    }

    private fun fromMoov(b: ByteArray): VideoColour {
        var colour = VideoColour.Unknown
        // colr: colour type, then primaries, transfer and matrix (u16 each); nclx adds the range bit.
        val colr = find(b, "colr") { at -> at + 10 <= b.size && String(b, at, 4, Charsets.ISO_8859_1) in setOf("nclx", "nclc") }
        if (colr >= 0) {
            val nclx = String(b, colr, 4, Charsets.ISO_8859_1) == "nclx"
            colour = VideoColour(
                primaries = VideoColour.u16(b, colr + 4),
                transfer = VideoColour.u16(b, colr + 6),
                matrix = VideoColour.u16(b, colr + 8),
                fullRange = nclx && colr + 11 <= b.size && (b[colr + 10].toInt() and 0x80) != 0,
            )
        }
        // mdcv: 3 primaries and the white point (8 × u16), then the peak in 0.0001 nits.
        val mdcv = find(b, "mdcv") { at -> at + 20 <= b.size }
        val mastering = if (mdcv >= 0) VideoColour.u32(b, mdcv + 16) / 10_000f else 0f
        // clli: the brightest pixel (MaxCLL) in nits.
        val clli = find(b, "clli") { at -> at + 2 <= b.size }
        val maxCll = if (clli >= 0) VideoColour.u16(b, clli).toFloat() else 0f
        return colour.copy(peakNits = if (maxCll > 0f) maxCll else mastering)
    }

    /** The offset just past box type [type] (where its payload starts) that also satisfies [ok]; -1 if none. */
    private inline fun find(b: ByteArray, type: String, ok: (Int) -> Boolean): Int {
        val t = type.toByteArray(Charsets.ISO_8859_1)
        var i = 4
        while (i + 4 <= b.size) {
            if (b[i] == t[0] && b[i + 1] == t[1] && b[i + 2] == t[2] && b[i + 3] == t[3] && ok(i + 4)) return i + 4
            i++
        }
        return -1
    }
}

/** Matroska and WebM: the first video track's `Colour` element. */
private object Matroska {
    private const val SEGMENT = 0x18538067L
    private const val TRACKS = 0x1654AE6BL
    private const val CLUSTER = 0x1F43B675L
    private const val TRACK_ENTRY = 0xAEL
    private const val TRACK_TYPE = 0x83L
    private const val VIDEO = 0xE0L
    private const val COLOUR = 0x55B0L
    private const val MATRIX = 0x55B1L
    private const val RANGE = 0x55B9L
    private const val TRANSFER = 0x55BAL
    private const val PRIMARIES = 0x55BBL
    private const val MAX_CLL = 0x55BCL
    private const val MASTERING = 0x55D0L
    private const val LUMINANCE_MAX = 0x55D9L
    private const val UNKNOWN_SIZE = -1L

    fun read(f: RandomAccessFile): VideoColour {
        val length = f.length()
        f.seek(0)
        val (_, headerSize) = element(f) ?: return VideoColour.Unknown
        f.seek(f.filePointer + headerSize)
        val (segment, segmentSize) = element(f) ?: return VideoColour.Unknown
        if (segment != SEGMENT) return VideoColour.Unknown
        val end = if (segmentSize == UNKNOWN_SIZE) length else minOf(length, f.filePointer + segmentSize)
        while (f.filePointer < end && f.filePointer < VideoColour.MAX_HEADER) {
            val (id, size) = element(f) ?: return VideoColour.Unknown
            if (id == CLUSTER || size == UNKNOWN_SIZE) return VideoColour.Unknown
            if (id == TRACKS) {
                val bytes = ByteArray(minOf(size, 4L shl 20).toInt())
                f.readFully(bytes)
                return tracks(bytes)
            }
            f.seek(f.filePointer + size)
        }
        return VideoColour.Unknown
    }

    private fun tracks(b: ByteArray): VideoColour {
        for ((id, s, e) in children(b, 0, b.size)) {
            if (id != TRACK_ENTRY) continue
            val entry = children(b, s, e)
            if (entry.firstOrNull { it.first == TRACK_TYPE }?.let { uint(b, it.second, it.third) } != 1L) continue
            val video = entry.firstOrNull { it.first == VIDEO } ?: return VideoColour.Unknown
            val colour = children(b, video.second, video.third).firstOrNull { it.first == COLOUR } ?: return VideoColour.Unknown
            var c = VideoColour.Unknown
            var mastering = 0f
            var maxCll = 0f
            for ((cid, cs, ce) in children(b, colour.second, colour.third)) {
                when (cid) {
                    MATRIX -> c = c.copy(matrix = uint(b, cs, ce).toInt())
                    RANGE -> c = c.copy(fullRange = uint(b, cs, ce) == 2L)
                    TRANSFER -> c = c.copy(transfer = uint(b, cs, ce).toInt())
                    PRIMARIES -> c = c.copy(primaries = uint(b, cs, ce).toInt())
                    MAX_CLL -> maxCll = uint(b, cs, ce).toFloat()
                    MASTERING -> children(b, cs, ce).firstOrNull { it.first == LUMINANCE_MAX }?.let { mastering = float(b, it.second, it.third) }
                }
            }
            return c.copy(peakNits = if (maxCll > 0f) maxCll else mastering)
        }
        return VideoColour.Unknown
    }

    /** The elements in b[start, end): id, payload start, payload end. */
    private fun children(b: ByteArray, start: Int, end: Int): List<Triple<Long, Int, Int>> {
        val out = mutableListOf<Triple<Long, Int, Int>>()
        var i = start
        while (i < end) {
            val (id, idLen) = vint(b, i, end, keepMarker = true) ?: break
            val (size, sizeLen) = vint(b, i + idLen, end, keepMarker = false) ?: break
            val s = i + idLen + sizeLen
            val e = if (size == UNKNOWN_SIZE) end else minOf(end.toLong(), s + size).toInt()
            out += Triple(id, s, e)
            i = e
        }
        return out
    }

    private fun uint(b: ByteArray, s: Int, e: Int): Long = (s until e).fold(0L) { acc, i -> (acc shl 8) or (b[i].toLong() and 0xFF) }

    private fun float(b: ByteArray, s: Int, e: Int): Float = when (e - s) {
        4 -> java.lang.Float.intBitsToFloat(uint(b, s, e).toInt())
        8 -> java.lang.Double.longBitsToDouble(uint(b, s, e)).toFloat()
        else -> 0f
    }

    /** An EBML variable-length integer at [at]: its value and length; ids keep their marker bit. */
    private fun vint(b: ByteArray, at: Int, end: Int, keepMarker: Boolean): Pair<Long, Int>? {
        if (at >= end) return null
        val first = b[at].toInt() and 0xFF
        if (first == 0) return null
        val len = Integer.numberOfLeadingZeros(first) - 23
        if (at + len > end) return null
        var value = (if (keepMarker) first else first and (0xFF shr len)).toLong()
        var allOnes = value == (0xFFL shr len)
        for (k in 1 until len) {
            val next = b[at + k].toLong() and 0xFF
            allOnes = allOnes && next == 0xFFL
            value = (value shl 8) or next
        }
        return (if (!keepMarker && allOnes) UNKNOWN_SIZE else value) to len
    }

    /** Reads an element header from the file: id and payload size, leaving the file at the payload. */
    private fun element(f: RandomAccessFile): Pair<Long, Long>? {
        val buf = ByteArray(12)
        val start = f.filePointer
        val n = f.read(buf)
        if (n <= 0) return null
        val (id, idLen) = vint(buf, 0, n, keepMarker = true) ?: return null
        val (size, sizeLen) = vint(buf, idLen, n, keepMarker = false) ?: return null
        f.seek(start + idLen + sizeLen)
        return id to size
    }
}
