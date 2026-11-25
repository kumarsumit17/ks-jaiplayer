package com.ks.jaiplayer

import com.ks.jaiplayer.player.MediaRef
import com.ks.jaiplayer.player.PlaybackState
import com.ks.jaiplayer.player.PlaybackStatus
import com.ks.jaiplayer.player.PlayerAction
import com.ks.jaiplayer.player.PlayerController
import com.ks.jaiplayer.player.Track
import com.ks.jaiplayer.plugins.tracks.TracksPlugin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlayerControllerTest {
    private val media = MediaRef("file:///a.mkv", "a.mkv")
    private fun playing(position: Long = 30_000, duration: Long = 100_000) =
        PlaybackState(status = PlaybackStatus.Playing, media = media, positionMs = position, durationMs = duration)

    @Test fun togglePlayPausesAndResumes() {
        val e = FakeEngine(playing())
        val c = PlayerController(e)
        c.togglePlay(); assertEquals(PlaybackStatus.Paused, e.state.value.status)
        c.togglePlay(); assertEquals(PlaybackStatus.Playing, e.state.value.status)
    }

    @Test fun togglePlayAtTheEndStartsAgain() {
        val e = FakeEngine(playing(position = 100_000).copy(status = PlaybackStatus.Ended))
        PlayerController(e).togglePlay()
        assertEquals(listOf("seek 0", "play"), e.calls)
    }

    @Test fun togglePlayAfterAnErrorOpensTheFileAgain() {
        val e = FakeEngine(playing().copy(status = PlaybackStatus.Error))
        PlayerController(e).togglePlay()
        assertEquals(listOf("open ${media.uri}"), e.calls)
    }

    @Test fun seekIsClampedToTheMedia() {
        val e = FakeEngine(playing(position = 5_000, duration = 60_000))
        val c = PlayerController(e)
        c.perform(PlayerAction.SeekBack); assertEquals(0, e.state.value.positionMs)
        c.perform(PlayerAction.SeekForwardLong); assertEquals(60_000, e.state.value.positionMs)
        c.perform(PlayerAction.SeekBack); assertEquals(50_000, e.state.value.positionMs)
    }

    @Test fun noSeekWithoutALength() {
        val e = FakeEngine(playing(duration = 0))
        PlayerController(e).perform(PlayerAction.SeekForward)
        assertTrue(e.calls.isEmpty())
    }

    @Test fun volumeStepsAreClampedAndUnmute() {
        val e = FakeEngine(playing().copy(volume = 97, muted = true))
        val c = PlayerController(e)
        c.perform(PlayerAction.VolumeUp)
        assertEquals(100, e.state.value.volume)
        assertFalse(e.state.value.muted)
        repeat(30) { c.perform(PlayerAction.VolumeDown) }
        assertEquals(0, e.state.value.volume)
    }

    @Test fun subtitlesCycleThroughOff() {
        val e = FakeEngine(playing().copy(subtitleTracks = listOf(Track(3), Track(7))))
        TracksPlugin.nextSubtitle(e); assertEquals(3, e.state.value.subtitleTrackId)
        TracksPlugin.nextSubtitle(e); assertEquals(7, e.state.value.subtitleTrackId)
        TracksPlugin.nextSubtitle(e); assertNull(e.state.value.subtitleTrackId)
    }

    @Test fun audioCyclesAndNeedsTwoTracks() {
        val e = FakeEngine(playing().copy(audioTracks = listOf(Track(1), Track(2)), audioTrackId = 1))
        TracksPlugin.nextAudio(e); assertEquals(2, e.state.value.audioTrackId)
        TracksPlugin.nextAudio(e); assertEquals(1, e.state.value.audioTrackId)

        val single = FakeEngine(playing().copy(audioTracks = listOf(Track(1)), audioTrackId = 1))
        TracksPlugin.nextAudio(single)
        assertTrue(single.calls.isEmpty())
    }

    @Test fun speedStepsThroughTheList() {
        val e = FakeEngine(playing())
        val c = PlayerController(e)
        c.perform(PlayerAction.Faster); assertEquals(1.25f, e.state.value.speed)
        repeat(20) { c.perform(PlayerAction.Faster) }; assertEquals(4f, e.state.value.speed)
        repeat(20) { c.perform(PlayerAction.Slower) }; assertEquals(0.25f, e.state.value.speed)
    }

    @Test fun fullScreenAndBackAreLeftToTheInterface() {
        val c = PlayerController(FakeEngine(playing()))
        assertFalse(c.perform(PlayerAction.ToggleFullScreen))
        assertFalse(c.perform(PlayerAction.Back))
    }
}
