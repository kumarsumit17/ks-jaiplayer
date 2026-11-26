package com.ks.jaiplayer.plugin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.input.key.Key
import com.ks.jaiplayer.player.MediaRef
import com.ks.jaiplayer.player.PlaybackState
import com.ks.jaiplayer.player.PlayerController
import com.ks.jaiplayer.player.PlayerEngine
import com.ks.jaiplayer.settings.SettingsStore
import com.ks.jaiplayer.ui.PlatformHost

/**
 * A feature added to the player. The core app does only what every player must (open a file,
 * play, pause, seek, volume, full screen); everything else is a plugin that adds to it through
 * [PluginContext]. The core never knows which plugins exist: the entry point passes the list.
 *
 * Plugins are compiled into the app for now (first-party only). Loading third-party plugins at
 * run time is a later step; see specs/plugins.md.
 */
interface JaiPlugin {
    /** Stable and unique, e.g. `recent-files`. Also names the plugin's own settings. */
    val id: String

    /** The name shown in Settings › Plugins. */
    val name: String get() = id

    /**
     * The plugin API this plugin was built for. A plugin added as a jar (desktop) whose version
     * differs from [PLUGIN_API_VERSION] is not loaded; see specs/writing-plugins.md.
     */
    val apiVersion: Int get() = PLUGIN_API_VERSION

    /** Called once at start. Register everything the plugin adds here. */
    fun install(context: PluginContext)
}

/** The plugin API's version. It changes when [JaiPlugin] or [PluginContext] change incompatibly. */
const val PLUGIN_API_VERSION = 1

/** Where a plugin's control goes in the player's control bar. */
enum class ControlSlot {
    /** After the volume control. */
    Start,

    /** At the right end, before full screen. */
    End,
}

/** The desktop menu a plugin's entry goes in. Other platforms show no menu bar. */
enum class MenuGroup { File, Playback }

/** A menu entry; with [children] it is a submenu. Labels are composable, so they follow the language. */
class MenuEntry(
    val label: @Composable () -> String,
    val enabled: (PluginScreenState) -> Boolean = { true },
    val children: (@Composable () -> List<MenuEntry>)? = null,
    val onClick: () -> Unit = {},
)

/** What a plugin can see of the app when it draws or decides. */
class PluginScreenState(val inPlayer: Boolean, val playback: PlaybackState)

/** What a plugin may use and add to. */
class PluginContext(
    val engine: PlayerEngine,
    val controller: PlayerController,
    val host: PlatformHost,
    /** Settings of this plugin only: keys are stored as `plugin.<id>.<key>`. */
    val settings: SettingsStore,
    /** Opens a file in the player, as the core does. */
    val open: (MediaRef) -> Unit,
    internal val registry: PluginRegistry,
) {
    /** Adds a control to the player bar, drawn with the current playback state. */
    fun addControl(slot: ControlSlot, content: @Composable (PlaybackState) -> Unit) {
        registry.controls += slot to content
    }

    /** Adds a section to the home screen, under the Open button. */
    fun addHomeSection(content: @Composable () -> Unit) {
        registry.homeSections += content
    }

    /** Adds an entry to a desktop menu. */
    fun addMenuEntry(group: MenuGroup, entry: MenuEntry) {
        registry.menuEntries += group to entry
    }

    /**
     * Binds a key in the player. Core keys (space, arrows, F, M, Esc) cannot be taken over, so
     * the keyboard and the TV remote always behave the same.
     */
    fun addKeyBinding(key: Key, action: () -> Unit) {
        registry.keys[key] = action
    }

    /** Called each time a file is opened. */
    fun onMediaOpened(listener: (MediaRef) -> Unit) {
        registry.openListeners += listener
    }
}

/** Everything the installed plugins added. Read by the core interface. */
class PluginRegistry {
    val controls = mutableStateListOf<Pair<ControlSlot, @Composable (PlaybackState) -> Unit>>()
    val homeSections = mutableStateListOf<@Composable () -> Unit>()
    val menuEntries = mutableStateListOf<Pair<MenuGroup, MenuEntry>>()
    internal val keys = mutableMapOf<Key, () -> Unit>()
    internal val openListeners = mutableListOf<(MediaRef) -> Unit>()

    fun keyAction(key: Key): (() -> Unit)? = keys[key]
    fun mediaOpened(media: MediaRef) = openListeners.forEach { it(media) }
}

/** A plugin's view of the app's settings: every key is prefixed with `plugin.<id>.`. */
internal class PrefixedSettings(private val store: SettingsStore, private val prefix: String) : SettingsStore {
    override fun getString(key: String) = store.getString(prefix + key)
    override fun putString(key: String, value: String?) = store.putString(prefix + key, value)
}
