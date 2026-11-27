package com.ks.jaiplayer.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.ks.jaiplayer.player.PlaybackStatus
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** The whole app, the same on every platform; the platform only supplies [JaiPlayerState]. */
@Composable
fun JaiPlayerApp(app: JaiPlayerState) {
    // When an episode ends, the next one starts.
    LaunchedEffect(app) {
        app.engine.state.map { it.status to it.media }.distinctUntilChanged().collect { (status, _) ->
            if (status == PlaybackStatus.Ended) app.onEnded()
        }
    }
    ProvideLanguage(app.language) {
        // The player keeps the app's own dark colours; other screens may follow the system's.
        JaiTheme(app.host.formFactor, app.host.systemColors.takeIf { app.screen != Screen.Player }) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                when (app.screen) {
                    Screen.Home -> HomeScreen(app)
                    Screen.Player -> PlayerScreen(app)
                }
                val library = app.host.library
                val mode = app.browse
                if (library != null && mode != null && app.screen == Screen.Home) {
                    MediaBrowser(library, mode, onFile = { app.browsed(listOf(it)) }, onFolder = app::browsed, onClose = { app.browse = null })
                }
                if (app.showSettings) SettingsDialog(app)
            }
        }
    }
}
