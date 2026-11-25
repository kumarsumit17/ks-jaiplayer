package com.ks.jaiplayer.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ks.jaiplayer.player.PlaybackState
import com.ks.jaiplayer.player.PlaybackStatus
import com.ks.jaiplayer.player.PlayerAction
import com.ks.jaiplayer.player.PlayerController
import com.ks.jaiplayer.player.PlayerError
import com.ks.jaiplayer.player.formatPlaybackTime
import com.ks.jaiplayer.resources.Res
import com.ks.jaiplayer.resources.common_pause
import com.ks.jaiplayer.resources.error_cannot_open
import com.ks.jaiplayer.resources.error_playback
import com.ks.jaiplayer.resources.error_vlc_missing_title
import com.ks.jaiplayer.resources.player_close
import com.ks.jaiplayer.resources.player_exit_full_screen
import com.ks.jaiplayer.resources.player_full_screen
import com.ks.jaiplayer.resources.player_mute
import com.ks.jaiplayer.resources.player_play
import com.ks.jaiplayer.resources.player_seek_back
import com.ks.jaiplayer.resources.player_seek_forward
import com.ks.jaiplayer.resources.player_unmute
import com.ks.jaiplayer.resources.player_volume
import kotlinx.coroutines.delay
import androidx.compose.runtime.CompositionLocalProvider
import com.ks.jaiplayer.player.Playlist
import com.ks.jaiplayer.player.formatSpeed
import com.ks.jaiplayer.player.playbackSpeeds
import com.ks.jaiplayer.plugin.ControlSlot
import com.ks.jaiplayer.resources.player_episode_of
import com.ks.jaiplayer.resources.player_episodes
import com.ks.jaiplayer.resources.player_next
import com.ks.jaiplayer.resources.player_previous
import com.ks.jaiplayer.resources.player_speed
import com.ks.jaiplayer.resources.player_speed_normal
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

private const val HIDE_AFTER_MS = 3_000L

@Composable
fun PlayerScreen(app: JaiPlayerState) {
    val state by app.engine.state.collectAsState()
    val form = LocalFormFactor.current
    val controller = app.controller

    // Every interaction bumps the counter; the controls hide 3 s after the last one while playing.
    var interaction by remember { mutableIntStateOf(0) }
    var controlsShown by remember { mutableStateOf(true) }
    fun poke() { controlsShown = true; interaction++ }
    var menuOpen by remember { mutableStateOf(false) }
    LaunchedEffect(interaction, state.isPlaying, menuOpen) {
        if (state.isPlaying && !menuOpen) {
            delay(HIDE_AFTER_MS)
            controlsShown = false
        } else {
            controlsShown = true
        }
    }

    val rootFocus = remember { FocusRequester() }
    val playFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { rootFocus.requestFocus() } }
    LaunchedEffect(controlsShown) {
        if (form == FormFactor.Tv) runCatching { if (controlsShown) playFocus.requestFocus() else rootFocus.requestFocus() }
    }

    fun handle(event: KeyEvent, tvControlsShown: Boolean): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        val action = actionForKey(event.key, event.isShiftPressed, tvControlsShown)
        if (action == null) {
            val pluginAction = if (tvControlsShown) null else app.plugins.keyAction(event.key)
            pluginAction?.invoke() ?: return false
            poke()
            return true
        }
        poke()
        when (action) {
            PlayerAction.NextItem -> app.next()
            PlayerAction.PreviousItem -> app.previous()
            PlayerAction.ToggleFullScreen -> app.fullScreen = !app.fullScreen
            PlayerAction.Back -> when {
                app.fullScreen && form == FormFactor.Desktop -> app.fullScreen = false
                form == FormFactor.Tv && controlsShown && state.isPlaying -> controlsShown = false
                else -> app.closePlayer()
            }
            else -> controller.perform(action)
        }
        return true
    }

    Box(
        Modifier.fillMaxSize()
            .background(Color.Black)
            // Desktop: keys always drive the player, whatever has focus. TV: while the controls
            // are shown, the remote moves focus between them first.
            .onPreviewKeyEvent { if (form != FormFactor.Tv || !controlsShown) handle(it, false) else false }
            .onKeyEvent { if (form == FormFactor.Tv) handle(it, true) else false }
            .focusRequester(rootFocus)
            .focusable()
            .mediaDropTarget { files -> app.openAll(files) }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val e = awaitPointerEvent()
                        if (e.type == PointerEventType.Move || e.type == PointerEventType.Press) poke()
                    }
                }
            }
            .pointerInput(form) {
                detectTapGestures(
                    onDoubleTap = { offset ->
                        when (form) {
                            FormFactor.Desktop -> app.fullScreen = !app.fullScreen
                            else -> controller.seekBy(
                                if (offset.x < size.width / 2) -PlayerController.SEEK_STEP_MS else PlayerController.SEEK_STEP_MS,
                            )
                        }
                    },
                )
            },
    ) {
        VideoSurface(app.engine, Modifier.fillMaxSize())
        if (!state.hasVideo && state.status != PlaybackStatus.Opening) AudioOnly(state)
        state.error?.let { ErrorBanner(it, state, Modifier.align(Alignment.Center)) }

        AnimatedVisibility(controlsShown, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
            CompositionLocalProvider(LocalMenuOpen provides { open: Boolean -> menuOpen = open; poke() }) {
                Controls(app, state, playFocus)
            }
        }
    }
}

@Composable
private fun AudioOnly(state: PlaybackState) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(JaiIcons.Audio, null, Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.size(16.dp))
            Text(state.media?.title.orEmpty(), fontSize = 22.sp, color = Color.White)
        }
    }
}

@Composable
private fun ErrorBanner(error: PlayerError, state: PlaybackState, modifier: Modifier) {
    val text = when (error) {
        PlayerError.EngineMissing -> stringResource(Res.string.error_vlc_missing_title)
        PlayerError.CannotOpen -> stringResource(Res.string.error_cannot_open, state.media?.title.orEmpty())
        PlayerError.Playback -> stringResource(Res.string.error_playback)
    }
    Surface(modifier, color = Color(0xCC1D2025), shape = RoundedCornerShape(12.dp)) {
        Text(text, Modifier.padding(horizontal = 20.dp, vertical = 14.dp), color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun Controls(app: JaiPlayerState, state: PlaybackState, playFocus: FocusRequester) {
    val m = LocalMetrics.current
    val form = LocalFormFactor.current
    val controller = app.controller
    val scrim = Color.Black.copy(alpha = 0.65f)

    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        // Top: back and the title.
        Row(
            Modifier.align(Alignment.TopCenter).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(scrim, Color.Transparent)))
                .padding(m.safe),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (form != FormFactor.Tv) {
                ControlButton(JaiIcons.ArrowBack, stringResource(Res.string.player_close), app::closePlayer)
                Spacer(Modifier.width(8.dp))
            }
            Text(
                state.media?.title.orEmpty(),
                color = Color.White,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            val list = app.playlist
            if (list != null && list.size > 1) {
                Spacer(Modifier.width(12.dp))
                Text(
                    stringResource(Res.string.player_episode_of, (list.index + 1).toString(), list.size.toString()),
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        // Bottom: seek bar and buttons.
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, scrim)))
                .padding(m.safe),
        ) {
            SeekBar(state, controller)
            val list = app.playlist
            val series = list != null && list.size > 1
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (series) {
                    ControlButton(JaiIcons.Previous, stringResource(Res.string.player_previous), app::previous, enabled = list.hasPrevious)
                }
                ControlButton(
                    if (state.isPlaying) JaiIcons.Pause else JaiIcons.Play,
                    stringResource(if (state.isPlaying) Res.string.common_pause else Res.string.player_play),
                    controller::togglePlay,
                    Modifier.focusRequester(playFocus),
                )
                if (series) {
                    ControlButton(JaiIcons.Next, stringResource(Res.string.player_next), app::next, enabled = list.hasNext)
                }
                ControlButton(
                    JaiIcons.Back10,
                    pluralStringResource(Res.plurals.player_seek_back, 10, "10"),
                    { controller.seekBy(-PlayerController.SEEK_STEP_MS) },
                )
                ControlButton(
                    JaiIcons.Forward10,
                    pluralStringResource(Res.plurals.player_seek_forward, 10, "10"),
                    { controller.seekBy(PlayerController.SEEK_STEP_MS) },
                )
                Spacer(Modifier.width(m.gap))
                ControlButton(
                    if (state.muted || state.volume == 0) JaiIcons.Muted else JaiIcons.Volume,
                    stringResource(if (state.muted) Res.string.player_unmute else Res.string.player_mute),
                    { app.engine.setMuted(!state.muted) },
                )
                if (form == FormFactor.Desktop) VolumeSlider(state, controller)
                PluginControls(app, ControlSlot.Start, state)
                Spacer(Modifier.weight(1f))
                SpeedMenu(app, state)
                if (series) EpisodeMenu(app, list)
                PluginControls(app, ControlSlot.End, state)
                if (form == FormFactor.Desktop) {
                    ControlButton(
                        if (app.fullScreen) JaiIcons.ExitFullScreen else JaiIcons.FullScreen,
                        stringResource(if (app.fullScreen) Res.string.player_exit_full_screen else Res.string.player_full_screen),
                        { app.fullScreen = !app.fullScreen },
                    )
                }
            }
        }
    }
}

@Composable
private fun PluginControls(app: JaiPlayerState, slot: ControlSlot, state: PlaybackState) {
    app.plugins.controls.filter { it.first == slot }.forEach { (_, content) -> content(state) }
}

@Composable
private fun SpeedMenu(app: JaiPlayerState, state: PlaybackState) {
    TextControlMenu(formatSpeed(state.speed), stringResource(Res.string.player_speed)) { close ->
        playbackSpeeds.forEach { speed ->
            val text = if (speed == 1f) "${formatSpeed(speed)}  ·  ${stringResource(Res.string.player_speed_normal)}" else formatSpeed(speed)
            MenuChoice(text, kotlin.math.abs(state.speed - speed) < 0.001f) { app.engine.setSpeed(speed); close() }
        }
    }
}

@Composable
private fun EpisodeMenu(app: JaiPlayerState, list: Playlist) {
    IconControlMenu(JaiIcons.Episodes, stringResource(Res.string.player_episodes)) { close ->
        list.items.forEachIndexed { i, media ->
            MenuChoice("${i + 1}.  ${media.title}", i == list.index) { app.jumpTo(i); close() }
        }
    }
}

@Composable
private fun SeekBar(state: PlaybackState, controller: PlayerController) {
    val form = LocalFormFactor.current
    val focusManager = LocalFocusManager.current
    // While the thumb is dragged the bar shows the drag position, and seeks when it is let go.
    var dragging by remember { mutableStateOf<Float?>(null) }
    val duration = state.durationMs.coerceAtLeast(1)
    val fraction = dragging ?: (state.positionMs.toFloat() / duration).coerceIn(0f, 1f)
    val shownMs = dragging?.let { (it * duration).toLong() } ?: state.positionMs
    Row(verticalAlignment = Alignment.CenterVertically) {
        TimeText(formatPlaybackTime(shownMs))
        Slider(
            value = fraction,
            onValueChange = { dragging = it },
            onValueChangeFinished = {
                dragging?.let { controller.seekTo((it * duration).toLong()) }
                dragging = null
            },
            enabled = state.canSeek,
            colors = SliderDefaults.colors(inactiveTrackColor = Color.White.copy(alpha = 0.25f)),
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp).desktopNoFocus().tvFocusRing(RoundedCornerShape(12.dp))
                .onPreviewKeyEvent { e -> if (form == FormFactor.Tv) tvSeekBarKey(e, controller, focusManager) else false },
        )
        TimeText(formatPlaybackTime(state.durationMs))
    }
}

@Composable
private fun TimeText(text: String) {
    Text(text, color = Color.White, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(64.dp))
}

@Composable
private fun VolumeSlider(state: PlaybackState, controller: PlayerController) {
    val volumeLabel = stringResource(Res.string.player_volume)
    var value by remember { mutableFloatStateOf(state.volume.toFloat()) }
    LaunchedEffect(state.volume) { value = state.volume.toFloat() }
    Slider(
        value = if (state.muted) 0f else value,
        onValueChange = { value = it; controller.setVolume(it.toInt()) },
        valueRange = 0f..PlayerController.MAX_VOLUME.toFloat(),
        modifier = Modifier.width(110.dp).desktopNoFocus().semantics { contentDescription = volumeLabel },
        colors = SliderDefaults.colors(inactiveTrackColor = Color.White.copy(alpha = 0.25f)),
    )
    Spacer(Modifier.width(4.dp))
    Text("${if (state.muted) 0 else state.volume}", color = Color.White, style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.width(28.dp))
}

/**
 * TV remote on the seek bar: left/right jump 10 s (the slider's own step would be a tiny fraction
 * of a long film), up/down leave the bar, so focus is never trapped in it.
 */
private fun tvSeekBarKey(e: KeyEvent, controller: PlayerController, focusManager: FocusManager): Boolean {
    if (e.type != KeyEventType.KeyDown) return e.key in setOf(Key.DirectionLeft, Key.DirectionRight, Key.DirectionUp, Key.DirectionDown)
    when (e.key) {
        Key.DirectionLeft -> controller.seekBy(-PlayerController.SEEK_STEP_MS)
        Key.DirectionRight -> controller.seekBy(PlayerController.SEEK_STEP_MS)
        Key.DirectionUp -> focusManager.moveFocus(FocusDirection.Up)
        Key.DirectionDown -> focusManager.moveFocus(FocusDirection.Down)
        else -> return false
    }
    return true
}
