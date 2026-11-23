package com.ks.jaiplayer.player

/**
 * A playback time as people read it on a player: `0:05`, `12:34`, `1:02:03`. Always Latin digits,
 * in every language. Negative times count as zero.
 */
fun formatPlaybackTime(ms: Long): String {
    val totalSeconds = ms.coerceAtLeast(0) / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    fun two(n: Long) = n.toString().padStart(2, '0')
    return if (hours > 0) "$hours:${two(minutes)}:${two(seconds)}" else "$minutes:${two(seconds)}"
}
