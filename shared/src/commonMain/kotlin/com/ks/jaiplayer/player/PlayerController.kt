package com.ks.jaiplayer.player

/**
 * Turns user actions into engine calls. Holds no state of its own: every decision is made from
 * the engine's current [PlaybackState], so the controller is the same on every platform.
 */
class PlayerController(private val engine: PlayerEngine) {

    private val state get() = engine.state.value

    /** Performs [action]; returns false for actions the interface handles itself. */
    fun perform(action: PlayerAction): Boolean {
        when (action) {
            PlayerAction.TogglePlay -> togglePlay()
            PlayerAction.SeekBack -> seekBy(-SEEK_STEP_MS)
            PlayerAction.SeekForward -> seekBy(SEEK_STEP_MS)
            PlayerAction.SeekBackLong -> seekBy(-SEEK_LONG_STEP_MS)
            PlayerAction.SeekForwardLong -> seekBy(SEEK_LONG_STEP_MS)
            PlayerAction.VolumeUp -> changeVolume(VOLUME_STEP)
            PlayerAction.VolumeDown -> changeVolume(-VOLUME_STEP)
            PlayerAction.ToggleMute -> engine.setMuted(!state.muted)
            PlayerAction.Slower -> engine.setSpeed(nextSpeed(state.speed, faster = false))
            PlayerAction.Faster -> engine.setSpeed(nextSpeed(state.speed, faster = true))
            PlayerAction.ToggleFullScreen, PlayerAction.Back, PlayerAction.NextItem, PlayerAction.PreviousItem -> return false
        }
        return true
    }

    fun togglePlay() {
        when (state.status) {
            PlaybackStatus.Playing -> engine.pause()
            PlaybackStatus.Ended -> {
                engine.seekTo(0)
                engine.play()
            }
            PlaybackStatus.Idle, PlaybackStatus.Error -> state.media?.let(engine::open)
            else -> engine.play()
        }
    }

    fun seekBy(deltaMs: Long) {
        val s = state
        if (!s.canSeek) return
        seekTo(s.positionMs + deltaMs)
    }

    fun seekTo(positionMs: Long) {
        val s = state
        if (!s.canSeek) return
        engine.seekTo(positionMs.coerceIn(0, s.durationMs))
    }

    /** Changing the volume also unmutes, as on every player people know. */
    fun setVolume(volume: Int) {
        engine.setVolume(volume.coerceIn(0, MAX_VOLUME))
        if (state.muted) engine.setMuted(false)
    }

    fun changeVolume(delta: Int) = setVolume(state.volume + delta)

    companion object {
        const val SEEK_STEP_MS = 10_000L
        const val SEEK_LONG_STEP_MS = 60_000L
        const val VOLUME_STEP = 5
        const val MAX_VOLUME = 100
    }
}
