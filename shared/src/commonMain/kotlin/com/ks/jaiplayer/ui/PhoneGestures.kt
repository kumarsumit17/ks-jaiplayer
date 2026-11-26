package com.ks.jaiplayer.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import com.ks.jaiplayer.player.PlaybackState
import com.ks.jaiplayer.player.PlayerController
import kotlin.math.abs

/** What a touch gesture on the video is doing, shown in the middle of the screen while it lasts. */
sealed interface GestureHint {
    data class Seek(val targetMs: Long, val deltaMs: Long) : GestureHint
    data class Volume(val level: Int) : GestureHint
    data class Brightness(val level: Float) : GestureHint
    data class Fill(val fill: Boolean) : GestureHint
    data object Fast : GestureHint
}

/**
 * The phone's gestures on the video, as in the players people know:
 * - drag sideways to seek (the whole width is 90 seconds, or the file's length if shorter),
 *   with the target time shown, seeking when the finger lifts;
 * - drag up or down on the right half for volume, on the left half for brightness;
 * - pinch out to fill the screen (edges cut off), pinch in to fit the whole picture.
 * Taps, double taps and the long press for 2× are handled by the player's tap detector; a
 * gesture here consumes its events, so a drag is never also a tap.
 */
fun Modifier.phoneGestures(
    state: () -> PlaybackState,
    controller: PlayerController,
    brightness: BrightnessControl?,
    fill: () -> Boolean,
    onFill: (Boolean) -> Unit,
    onHint: (GestureHint?) -> Unit,
): Modifier = pointerInput(controller, brightness) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val start = down.position
        val s0 = state()
        val startVolume = s0.volume
        val startBrightness = brightness?.current() ?: 0.5f
        var mode: Mode? = null
        var pinchStart = 0f
        var pinchDone = false
        var seekTarget = s0.positionMs
        do {
            val event = awaitPointerEvent()
            val pressed = event.changes.filter { it.pressed }
            if (pressed.size >= 2 && (mode == null || mode == Mode.Pinch)) {
                val d = (pressed[0].position - pressed[1].position).getDistance()
                if (mode == null) { mode = Mode.Pinch; pinchStart = d }
                if (!pinchDone && pinchStart > 0f) {
                    val ratio = d / pinchStart
                    val wanted = when {
                        ratio > 1.2f -> true
                        ratio < 0.83f -> false
                        else -> null
                    }
                    if (wanted != null) {
                        pinchDone = true
                        if (wanted != fill()) onFill(wanted)
                        onHint(GestureHint.Fill(wanted))
                    }
                }
            } else if (pressed.size == 1 && mode == null) {
                val d: Offset = pressed[0].position - start
                if (d.getDistance() > viewConfiguration.touchSlop) {
                    mode = when {
                        abs(d.x) > abs(d.y) -> if (s0.canSeek) Mode.Seek else Mode.Ignore
                        start.x > size.width / 2 -> Mode.Volume
                        brightness != null -> Mode.Brightness
                        else -> Mode.Ignore
                    }
                }
            }
            val now = pressed.firstOrNull()?.position
            if (now != null) when (mode) {
                Mode.Seek -> {
                    val span = minOf(SEEK_SPAN_MS, s0.durationMs)
                    val delta = ((now.x - start.x) / size.width * span).toLong()
                    seekTarget = (s0.positionMs + delta).coerceIn(0, s0.durationMs)
                    onHint(GestureHint.Seek(seekTarget, seekTarget - s0.positionMs))
                }
                Mode.Volume -> {
                    val level = (startVolume - (now.y - start.y) / size.height * 150f).toInt()
                        .coerceIn(0, PlayerController.MAX_VOLUME)
                    controller.setVolume(level)
                    onHint(GestureHint.Volume(level))
                }
                Mode.Brightness -> {
                    val level = (startBrightness - (now.y - start.y) / size.height * 1.5f).coerceIn(0.02f, 1f)
                    brightness?.set(level)
                    onHint(GestureHint.Brightness(level))
                }
                else -> Unit
            }
            if (mode != null && mode != Mode.Ignore) event.changes.forEach { it.consume() }
        } while (event.changes.any { it.pressed })
        if (mode == Mode.Seek) controller.seekTo(seekTarget)
        if (mode != null && mode != Mode.Ignore) onHint(null)
    }
}

private enum class Mode { Seek, Volume, Brightness, Pinch, Ignore }

private const val SEEK_SPAN_MS = 90_000L
