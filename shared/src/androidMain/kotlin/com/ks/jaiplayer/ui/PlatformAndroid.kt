package com.ks.jaiplayer.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_SURFACE_VIEW
import com.ks.jaiplayer.android.ExoPlayerEngine
import com.ks.jaiplayer.player.MediaRef
import com.ks.jaiplayer.player.PlayerEngine
import java.util.Locale

@Composable
actual fun VideoSurface(engine: PlayerEngine, modifier: Modifier) {
    val player = (engine as? ExoPlayerEngine)?.player ?: return
    var aspect by remember { mutableFloatStateOf(0f) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    aspect = videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
                }
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        PlayerSurface(
            player = player,
            surfaceType = SURFACE_TYPE_SURFACE_VIEW,
            modifier = if (aspect > 0f) Modifier.aspectRatio(aspect) else Modifier,
        )
    }
}

/** Android has no drag and drop of files into a full-screen player. */
actual fun Modifier.mediaDropTarget(onDrop: (List<MediaRef>) -> Unit): Modifier = this

@Composable
actual fun ProvideLanguage(language: String, content: @Composable () -> Unit) {
    // Compose resources follow LocalConfiguration on Android, so the chosen language is applied
    // to this subtree on every Android version, without changing the system setting.
    val base = LocalConfiguration.current
    val configuration = remember(base, language) {
        Configuration(base).apply { setLocale(Locale.forLanguageTag(language)) }
    }
    CompositionLocalProvider(LocalConfiguration provides configuration, content = content)
}
