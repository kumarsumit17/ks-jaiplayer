package com.ks.jaiplayer.plugins.resume

import com.ks.jaiplayer.player.MediaRef
import com.ks.jaiplayer.player.PlaybackState
import com.ks.jaiplayer.player.PlaybackStatus
import com.ks.jaiplayer.plugin.JaiPlugin
import com.ks.jaiplayer.plugin.PluginContext
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlin.time.Clock

/**
 * Built-in plugin: a file opens where it was left last time (long files only, not near the end).
 * Positions are kept every few seconds while playing and whenever playback pauses, ends, or
 * moves to another file.
 *
 * [onChanged] gets the list of unfinished files each time it changes; the Google TV app shows
 * it on the home screen's Continue watching row.
 */
class ResumePlugin(
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    private val onChanged: (List<ResumeEntry>) -> Unit = {},
) : JaiPlugin {
    override val id = "resume"

    override fun install(context: PluginContext) {
        val store = ResumePositions(context.settings)
        onChanged(store.load())

        var current: MediaRef? = null
        var pendingSeek: Long? = null
        var last: PlaybackState? = null
        var lastSavedMs = -1L

        fun save(state: PlaybackState) {
            val media = state.media ?: return
            if (state.durationMs <= 0) return
            val position = if (state.status == PlaybackStatus.Ended) state.durationMs else state.positionMs
            onChanged(store.record(media.uri, media.title, position, state.durationMs, now()))
            lastSavedMs = position
        }

        context.onMediaOpened { media ->
            last?.let(::save)
            last = null
            current = media
            lastSavedMs = -1L
            pendingSeek = store.get(media.uri)?.positionMs
        }

        MainScope().launch {
            context.engine.state.collect { state ->
                val media = current
                if (media == null || state.media?.uri != media.uri) {
                    // The player closed (the state is reset) or moved on: keep where it was.
                    if (state.media == null) last?.let(::save)
                    if (state.media == null) last = null
                    return@collect
                }
                pendingSeek?.let { target ->
                    if (state.isPlaying && state.durationMs > 0) {
                        pendingSeek = null
                        if (target < state.durationMs) context.controller.seekTo(target)
                        return@collect
                    }
                }
                if (pendingSeek == null) {
                    val previous = last
                    last = state
                    val paused = previous?.status == PlaybackStatus.Playing && state.status != PlaybackStatus.Playing
                    val moved = kotlin.math.abs(state.positionMs - lastSavedMs) >= SAVE_EVERY_MS
                    if (paused || state.status == PlaybackStatus.Ended || (state.isPlaying && moved)) save(state)
                }
            }
        }
    }

    private companion object {
        const val SAVE_EVERY_MS = 5_000L
    }
}
