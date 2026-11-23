package com.ks.jaiplayer

import com.ks.jaiplayer.player.MediaRef
import com.ks.jaiplayer.player.PlaybackState
import com.ks.jaiplayer.player.PlaybackStatus
import com.ks.jaiplayer.player.PlayerEngine
import kotlinx.coroutines.flow.MutableStateFlow

/** An engine that only records state, for testing the shared logic. */
class FakeEngine(initial: PlaybackState = PlaybackState()) : PlayerEngine {
    override val state = MutableStateFlow(initial)
    val calls = mutableListOf<String>()

    private fun update(call: String, f: PlaybackState.() -> PlaybackState) {
        calls += call
        state.value = state.value.f()
    }

    override fun open(media: MediaRef) = update("open ${media.uri}") { copy(media = media, status = PlaybackStatus.Playing) }
    override fun play() = update("play") { copy(status = PlaybackStatus.Playing) }
    override fun pause() = update("pause") { copy(status = PlaybackStatus.Paused) }
    override fun seekTo(positionMs: Long) = update("seek $positionMs") { copy(positionMs = positionMs) }
    override fun setVolume(volume: Int) = update("volume $volume") { copy(volume = volume) }
    override fun setMuted(muted: Boolean) = update("muted $muted") { copy(muted = muted) }
    override fun setSpeed(speed: Float) = update("speed $speed") { copy(speed = speed) }
    override fun selectAudioTrack(id: Int) = update("audio $id") { copy(audioTrackId = id) }
    override fun selectSubtitleTrack(id: Int?) = update("subtitle $id") { copy(subtitleTrackId = id) }
    override fun addSubtitle(uri: String) = update("addSubtitle $uri") { this }
    override fun stop() = update("stop") { PlaybackState() }
    override fun release() = update("release") { this }
}
