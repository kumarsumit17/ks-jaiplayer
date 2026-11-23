package com.ks.jaiplayer.ui

import androidx.compose.ui.input.key.Key
import com.ks.jaiplayer.player.PlayerAction

/**
 * The one key map, shared by the desktop keyboard and the TV remote, so the same key does the
 * same thing everywhere. Plugins may bind other keys ([com.ks.jaiplayer.plugin.PluginContext.addKeyBinding]).
 *
 * On TV, while the controls bar is shown, the arrows and the centre button move focus and press
 * the focused control instead, so they are not mapped then ([tvControlsShown]).
 */
fun actionForKey(key: Key, shift: Boolean = false, tvControlsShown: Boolean = false): PlayerAction? {
    when (key) {
        Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause -> return PlayerAction.TogglePlay
        Key.MediaFastForward -> return PlayerAction.SeekForward
        Key.MediaRewind -> return PlayerAction.SeekBack
        Key.Escape, Key.Back -> return PlayerAction.Back
        Key.MediaNext -> return PlayerAction.NextItem
        Key.MediaPrevious -> return PlayerAction.PreviousItem
    }
    if (tvControlsShown) return null
    return when (key) {
        Key.Spacebar, Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> PlayerAction.TogglePlay
        Key.DirectionLeft -> if (shift) PlayerAction.SeekBackLong else PlayerAction.SeekBack
        Key.DirectionRight -> if (shift) PlayerAction.SeekForwardLong else PlayerAction.SeekForward
        Key.DirectionUp -> PlayerAction.VolumeUp
        Key.DirectionDown -> PlayerAction.VolumeDown
        Key.F -> PlayerAction.ToggleFullScreen
        Key.M -> PlayerAction.ToggleMute
        Key.LeftBracket -> PlayerAction.Slower
        Key.RightBracket -> PlayerAction.Faster
        Key.N -> PlayerAction.NextItem
        Key.P -> PlayerAction.PreviousItem
        else -> null
    }
}
