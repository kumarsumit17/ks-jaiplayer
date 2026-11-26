package com.ks.jaiplayer.plugins.tracks

import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.input.key.Key
import com.ks.jaiplayer.player.PlaybackState
import com.ks.jaiplayer.player.PlayerEngine
import com.ks.jaiplayer.player.Track
import com.ks.jaiplayer.plugin.ControlSlot
import com.ks.jaiplayer.plugin.JaiPlugin
import com.ks.jaiplayer.plugin.MenuEntry
import com.ks.jaiplayer.plugin.MenuGroup
import com.ks.jaiplayer.plugin.PluginContext
import com.ks.jaiplayer.resources.Res
import com.ks.jaiplayer.resources.player_audio
import com.ks.jaiplayer.resources.player_subtitle_add
import com.ks.jaiplayer.resources.player_subtitles
import com.ks.jaiplayer.resources.player_subtitles_off
import com.ks.jaiplayer.resources.player_track
import com.ks.jaiplayer.ui.IconControlMenu
import com.ks.jaiplayer.ui.JaiIcons
import com.ks.jaiplayer.ui.MenuChoice
import org.jetbrains.compose.resources.stringResource

/**
 * Built-in plugin: choose the subtitle track (or none) and the audio track, load an external
 * subtitle file. Keys S and A cycle subtitles and audio tracks.
 */
class TracksPlugin : JaiPlugin {
    override val id = "tracks"
    override val name = "Subtitles and audio tracks"

    override fun install(context: PluginContext) {
        val engine = context.engine
        val pickSubtitle = { context.host.pickSubtitle(engine::addSubtitle) }
        context.addControl(ControlSlot.End) { state -> SubtitleMenu(engine, state, pickSubtitle) }
        context.addControl(ControlSlot.End) { state -> if (state.audioTracks.isNotEmpty()) AudioMenu(engine, state) }
        context.addKeyBinding(Key.S) { nextSubtitle(engine) }
        context.addKeyBinding(Key.A) { nextAudio(engine) }
        context.addMenuEntry(MenuGroup.File, MenuEntry(
            label = { stringResource(Res.string.player_subtitle_add) },
            enabled = { it.inPlayer },
            onClick = pickSubtitle,
        ))
        context.addMenuEntry(MenuGroup.Playback, MenuEntry(
            label = { stringResource(Res.string.player_subtitles) },
            enabled = { it.inPlayer },
            onClick = { nextSubtitle(engine) },
        ))
        context.addMenuEntry(MenuGroup.Playback, MenuEntry(
            label = { stringResource(Res.string.player_audio) },
            enabled = { it.inPlayer && it.playback.audioTracks.size > 1 },
            onClick = { nextAudio(engine) },
        ))
    }

    companion object {
        /** Cycles: off → first track → … → last track → off. */
        fun nextSubtitle(engine: PlayerEngine) {
            val s = engine.state.value
            if (s.subtitleTracks.isEmpty()) return
            val i = s.subtitleTracks.indexOfFirst { it.id == s.subtitleTrackId }
            engine.selectSubtitleTrack(if (i == s.subtitleTracks.lastIndex) null else s.subtitleTracks[i + 1].id)
        }

        /** Cycles through the audio tracks; does nothing with fewer than two. */
        fun nextAudio(engine: PlayerEngine) {
            val s = engine.state.value
            if (s.audioTracks.size < 2) return
            val i = s.audioTracks.indexOfFirst { it.id == s.audioTrackId }
            engine.selectAudioTrack(s.audioTracks[(i + 1) % s.audioTracks.size].id)
        }
    }
}

@Composable
private fun SubtitleMenu(engine: PlayerEngine, state: PlaybackState, pickSubtitle: () -> Unit) {
    IconControlMenu(JaiIcons.Subtitles, stringResource(Res.string.player_subtitles)) { close ->
        MenuChoice(stringResource(Res.string.player_subtitles_off), state.subtitleTrackId == null) {
            engine.selectSubtitleTrack(null); close()
        }
        state.subtitleTracks.forEachIndexed { i, t ->
            MenuChoice(trackLabel(t, i), state.subtitleTrackId == t.id) { engine.selectSubtitleTrack(t.id); close() }
        }
        HorizontalDivider()
        MenuChoice(stringResource(Res.string.player_subtitle_add), false) { close(); pickSubtitle() }
    }
}

@Composable
private fun AudioMenu(engine: PlayerEngine, state: PlaybackState) {
    IconControlMenu(JaiIcons.Audio, stringResource(Res.string.player_audio)) { close ->
        state.audioTracks.forEachIndexed { i, t ->
            MenuChoice(trackLabel(t, i), state.audioTrackId == t.id) { engine.selectAudioTrack(t.id); close() }
        }
    }
}

@Composable
private fun trackLabel(track: Track, index: Int): String =
    track.name?.takeIf { it.isNotBlank() }
        ?: track.language?.takeIf { it.isNotBlank() }
        ?: stringResource(Res.string.player_track, (index + 1).toString())
