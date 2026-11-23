package com.ks.jaiplayer.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ks.jaiplayer.player.MediaRef
import com.ks.jaiplayer.player.PlayerController
import com.ks.jaiplayer.player.PlayerEngine
import com.ks.jaiplayer.player.Playlist
import com.ks.jaiplayer.plugin.JaiPlugin
import com.ks.jaiplayer.plugin.PluginContext
import com.ks.jaiplayer.plugin.PluginRegistry
import com.ks.jaiplayer.plugin.PrefixedSettings
import com.ks.jaiplayer.settings.LanguageChoice
import com.ks.jaiplayer.settings.SettingsStore
import com.ks.jaiplayer.settings.languageChoice
import com.ks.jaiplayer.settings.resolveLanguage
import com.ks.jaiplayer.settings.setLanguageChoice
import com.ks.jaiplayer.update.UpdateService

enum class Screen { Home, Player }

/** The state of the whole app, the same on every platform. */
class JaiPlayerState(
    val engine: PlayerEngine,
    val host: PlatformHost,
    private val settings: SettingsStore,
    /** The system's preferred languages, most preferred first (BCP 47 tags). */
    private val systemLanguages: List<String>,
    plugins: List<JaiPlugin> = emptyList(),
    /** App updates from GitHub; null where the platform has none. */
    val updates: UpdateService? = null,
) {
    val controller = PlayerController(engine)
    val plugins = PluginRegistry()

    var screen by mutableStateOf(Screen.Home)
        private set
    /** What is playing and what comes next; null on the home screen. */
    var playlist by mutableStateOf<Playlist?>(null)
        private set
    var fullScreen by mutableStateOf(false)
    var languageChoice by mutableStateOf(settings.languageChoice())
        private set
    var showSettings by mutableStateOf(false)

    val language: String get() = resolveLanguage(languageChoice, systemLanguages)

    init {
        require(plugins.map { it.id }.toSet().size == plugins.size) { "Two plugins share an id" }
        plugins.forEach { plugin ->
            plugin.install(
                PluginContext(engine, controller, host, PrefixedSettings(settings, "plugin.${plugin.id}."), ::open, this.plugins),
            )
        }
    }

    /** Plays one file. */
    fun open(media: MediaRef) = play(Playlist(listOf(media)))

    /** Plays [items] one after another (a series, a folder, several dropped files), from [start]. */
    fun openAll(items: List<MediaRef>, start: Int = 0) {
        if (items.isNotEmpty()) play(Playlist(items, start.coerceIn(items.indices)))
    }

    fun next() { playlist?.next()?.let(::play) }
    fun previous() { playlist?.previous()?.let(::play) }
    fun jumpTo(index: Int) { playlist?.jumpTo(index)?.let(::play) }

    /** Called when the current item has played to the end: the next one starts, if there is one. */
    fun onEnded() { if (screen == Screen.Player) next() }

    private fun play(list: Playlist) {
        playlist = list
        engine.open(list.current)
        screen = Screen.Player
        plugins.mediaOpened(list.current)
    }

    fun pickAndOpen() = host.pickMedia(::open)

    fun pickFolderAndOpen() = host.pickFolder { openAll(it) }

    fun closePlayer() {
        engine.stop()
        playlist = null
        fullScreen = false
        screen = Screen.Home
    }

    fun chooseLanguage(choice: LanguageChoice) {
        settings.setLanguageChoice(choice)
        languageChoice = choice
    }
}
