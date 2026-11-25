package com.ks.jaiplayer.player

/**
 * What plays one after another: the episodes of a series, a folder or several files. A single
 * file is a playlist of one. Immutable; moving returns a new playlist.
 */
data class Playlist(val items: List<MediaRef>, val index: Int = 0) {
    init {
        require(items.isNotEmpty()) { "A playlist needs at least one item" }
        require(index in items.indices) { "Index $index is outside the playlist" }
    }

    val current: MediaRef get() = items[index]
    val hasNext: Boolean get() = index < items.lastIndex
    val hasPrevious: Boolean get() = index > 0
    val size: Int get() = items.size

    fun next(): Playlist? = if (hasNext) copy(index = index + 1) else null
    fun previous(): Playlist? = if (hasPrevious) copy(index = index - 1) else null
    fun jumpTo(i: Int): Playlist? = if (i in items.indices) copy(index = i) else null
}

/**
 * Orders file names as people expect: numbers by value, so "Episode 2" comes before
 * "Episode 10" and "S01E09" before "S01E10"; letters ignoring case.
 */
val naturalNameOrder: Comparator<String> = Comparator { a, b ->
    val ca = chunks(a)
    val cb = chunks(b)
    for (i in 0 until minOf(ca.size, cb.size)) {
        val x = ca[i]
        val y = cb[i]
        val c = if (x[0].isDigit() && y[0].isDigit()) {
            val nx = x.trimStart('0')
            val ny = y.trimStart('0')
            if (nx.length != ny.length) nx.length - ny.length else nx.compareTo(ny)
        } else {
            x.lowercase().compareTo(y.lowercase())
        }
        if (c != 0) return@Comparator c
    }
    if (ca.size != cb.size) ca.size - cb.size else a.compareTo(b)
}

private fun chunks(s: String): List<String> {
    val out = mutableListOf<String>()
    var i = 0
    while (i < s.length) {
        val digit = s[i].isDigit()
        var j = i
        while (j < s.length && s[j].isDigit() == digit) j++
        out += s.substring(i, j)
        i = j
    }
    return out.ifEmpty { listOf("") }
}

/** The slowest and fastest speeds; both engines keep sound and picture going across this range. */
const val MIN_SPEED = 0.25f
const val MAX_SPEED = 4f

/** The fine step of the speed control, as in "1.35×". */
const val SPEED_STEP = 0.05f

/** The speeds offered, slowest first. */
val playbackSpeeds = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 2.5f, 3f, 4f)

/** [current] moved by one fine step, rounded to the step and kept within the range. */
fun stepSpeed(current: Float, faster: Boolean): Float {
    val steps = kotlin.math.round(current / SPEED_STEP) + if (faster) 1 else -1
    return clampSpeed(steps * SPEED_STEP)
}

/** [speed] kept within [MIN_SPEED]..[MAX_SPEED], rounded to hundredths. */
fun clampSpeed(speed: Float): Float = kotlin.math.round(speed.coerceIn(MIN_SPEED, MAX_SPEED) * 100) / 100

/** The next speed up or down the list from [current], staying at the ends. */
fun nextSpeed(current: Float, faster: Boolean): Float {
    val i = playbackSpeeds.indexOfFirst { it >= current - 0.001f }.let { if (it < 0) playbackSpeeds.lastIndex else it }
    val exact = kotlin.math.abs(playbackSpeeds[i] - current) < 0.001f
    val target = when {
        faster -> if (exact) i + 1 else i
        else -> i - 1
    }
    return playbackSpeeds[target.coerceIn(0, playbackSpeeds.lastIndex)]
}

/** "1×", "1.25×", "0.5×". */
fun formatSpeed(speed: Float): String {
    val hundredths = kotlin.math.round(speed * 100).toInt()
    val whole = hundredths / 100
    val frac = (hundredths % 100).toString().padStart(2, '0').trimEnd('0')
    return if (frac.isEmpty()) "$whole×" else "$whole.$frac×"
}
