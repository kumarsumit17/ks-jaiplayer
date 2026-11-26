package com.ks.jaiplayer.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyShortcut
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.ks.jaiplayer.player.MediaRef
import com.ks.jaiplayer.player.PlayerAction
import com.ks.jaiplayer.resources.Res
import com.ks.jaiplayer.resources.app_name
import com.ks.jaiplayer.resources.common_pause
import com.ks.jaiplayer.resources.common_settings
import com.ks.jaiplayer.resources.home_open_file
import com.ks.jaiplayer.resources.menu_file
import com.ks.jaiplayer.resources.menu_playback
import com.ks.jaiplayer.resources.menu_quit
import com.ks.jaiplayer.resources.menu_view
import com.ks.jaiplayer.resources.menu_help_site
import com.ks.jaiplayer.resources.menu_help
import com.ks.jaiplayer.resources.menu_zoom
import com.ks.jaiplayer.resources.menu_minimize
import com.ks.jaiplayer.resources.menu_window
import com.ks.jaiplayer.resources.player_close
import com.ks.jaiplayer.resources.player_full_screen
import com.ks.jaiplayer.resources.player_mute
import com.ks.jaiplayer.resources.player_play
import com.ks.jaiplayer.resources.player_seek_back
import com.ks.jaiplayer.resources.player_seek_forward
import com.ks.jaiplayer.ui.FormFactor
import com.ks.jaiplayer.ui.JaiPlayerApp
import com.ks.jaiplayer.ui.JaiPlayerState
import com.ks.jaiplayer.ui.PlatformHost
import com.ks.jaiplayer.ui.ProvideLanguage
import com.ks.jaiplayer.ui.Screen
import com.ks.jaiplayer.vlc.VlcLocation
import com.ks.jaiplayer.vlc.VlcPlayerEngine
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.window.MenuScope
import com.ks.jaiplayer.player.formatSpeed
import com.ks.jaiplayer.player.playbackSpeeds
import com.ks.jaiplayer.plugin.JaiPlugin
import com.ks.jaiplayer.plugin.MenuEntry
import com.ks.jaiplayer.plugin.MenuGroup
import com.ks.jaiplayer.plugin.PluginScreenState
import com.ks.jaiplayer.plugins.builtInPlugins
import com.ks.jaiplayer.resources.home_open_series
import com.ks.jaiplayer.resources.player_next
import com.ks.jaiplayer.resources.player_previous
import com.ks.jaiplayer.resources.player_speed
import javax.swing.JFileChooser
import kotlin.math.abs

/** What differs between the macOS and Windows entry points. */
class DesktopConfig(
    /** Where libVLC is, in order: the copy bundled in the app (or a diagnosis override). */
    val vlcLocations: List<VlcLocation>,
    /** libVLC options this platform needs. */
    val vlcArgs: List<String> = emptyList(),
    /** The system's preferred languages, most preferred first. */
    val systemLanguages: List<String>,
    /** Files to open at start, e.g. from the command line. */
    val initialFiles: List<File> = emptyList(),
    /** macOS: the system menu holds Quit and Settings, so the File menu leaves them out. */
    val systemAppMenu: Boolean = false,
    /** Command (macOS) or Ctrl (Windows) shortcuts. */
    val useMetaShortcuts: Boolean = false,
    /** macOS: folders are chosen in the native file dialog; Windows uses a folder chooser. */
    val nativeFolderDialog: Boolean = false,
    /** The features added to the minimal core; see specs/plugins.md. */
    val plugins: List<JaiPlugin> = builtInPlugins(),
    /** This OS's Now Playing, media keys and stay-awake; null for none. */
    val integration: DesktopIntegration? = null,
)

/**
 * Files opened from outside while the app runs (Finder "Open With", a second launch). The entry
 * point sends them here; the window opens the latest.
 */
object ExternalOpen {
    internal val files = Channel<List<File>>(Channel.UNLIMITED)
    fun open(files: List<File>) { if (files.isNotEmpty()) this.files.trySend(files) }
}

/** Runs the desktop app (macOS or Windows): one window, the shared interface and a menu bar. */
fun runDesktopApp(config: DesktopConfig) {
    ExternalOpen.open(config.initialFiles)
    application {
        val engine = remember { VlcPlayerEngine.create(config.vlcLocations, config.vlcArgs) }
        val windowState = rememberWindowState(size = DpSize(1120.dp, 720.dp))
        val windowRef = remember { arrayOfNulls<Frame>(1) }
        val host = remember { DesktopHost(config.nativeFolderDialog) { windowRef[0] } }
        val settings = remember { PrefsSettingsStore() }
        val updater = remember { desktopUpdater(settings) { engine.release() } }
        val app = remember { JaiPlayerState(engine, host, settings, config.systemLanguages, config.plugins, updater) }

        val quit = { config.integration?.stop(); engine.release(); exitApplication() }
        Window(
            onCloseRequest = quit,
            state = windowState,
            title = "KS JaiPlayer",
        ) {
            windowRef[0] = window
            config.integration?.let { integration ->
                LaunchedEffect(Unit) {
                    integration.start { action ->
                        when (action) {
                            PlayerAction.NextItem -> app.next()
                            PlayerAction.PreviousItem -> app.previous()
                            else -> app.controller.perform(action)
                        }
                    }
                    kotlinx.coroutines.flow.combine(engine.state, snapshotFlow { app.playlist }) { s, list ->
                        val now = s.media?.let { m ->
                            NowPlaying(m.title, s.durationMs, s.positionMs, s.isPlaying, s.speed, list?.hasNext == true, list?.hasPrevious == true)
                        }
                        now to s.hasVideo
                    }.collect { (now, video) -> integration.update(now, video) }
                }
            }
            LaunchedEffect(Unit) { ExternalOpen.files.receiveAsFlow().collect { app.openAll(collectMedia(it).map(::mediaRefOf)) } }
            LaunchedEffect(app.fullScreen) {
                windowState.placement = if (app.fullScreen) WindowPlacement.Fullscreen else WindowPlacement.Floating
            }
            LaunchedEffect(Unit) {
                snapshotFlow { windowState.placement }.collect { app.fullScreen = it == WindowPlacement.Fullscreen }
            }
            ProvideLanguage(app.language) {
                val appName = stringResource(Res.string.app_name)
                window.title = app.playlist?.current?.title?.takeIf { app.screen == Screen.Player } ?: appName
                AppMenuBar(app, config, quit)
                JaiPlayerApp(app)
            }
        }
    }
}

@Composable
private fun FrameWindowScope.AppMenuBar(app: JaiPlayerState, config: DesktopConfig, quit: () -> Unit) {
    fun shortcut(key: Key, shift: Boolean = false) =
        if (config.useMetaShortcuts) KeyShortcut(key, meta = true, shift = shift) else KeyShortcut(key, ctrl = true, shift = shift)
    val inPlayer = app.screen == Screen.Player
    val playback by app.engine.state.collectAsState()
    val screenState = PluginScreenState(inPlayer, playback)
    val list = app.playlist

    @Composable
    fun MenuScope.pluginEntries(group: MenuGroup) {
        app.plugins.menuEntries.filter { it.first == group }.forEach { (_, entry) -> PluginEntry(entry, screenState) }
    }

    MenuBar {
        Menu(stringResource(Res.string.menu_file)) {
            Item(stringResource(Res.string.home_open_file), shortcut = shortcut(Key.O), onClick = app::pickAndOpen)
            Item(stringResource(Res.string.home_open_series), shortcut = shortcut(Key.O, shift = true), onClick = app::pickFolderAndOpen)
            pluginEntries(MenuGroup.File)
            Separator()
            Item(stringResource(Res.string.player_close), enabled = inPlayer, shortcut = shortcut(Key.W), onClick = app::closePlayer)
            if (!config.systemAppMenu) {
                Item(stringResource(Res.string.common_settings), onClick = { app.showSettings = true })
                Separator()
                Item(stringResource(Res.string.menu_quit), shortcut = shortcut(Key.Q), onClick = quit)
            }
        }
        // Menu items carry no single-key shortcuts: the player window handles those keys itself.
        Menu(stringResource(Res.string.menu_playback), enabled = inPlayer) {
            Item(stringResource(if (playback.isPlaying) Res.string.common_pause else Res.string.player_play), onClick = app.controller::togglePlay)
            Item(pluralStringResource(Res.plurals.player_seek_back, 10, "10"), onClick = { app.controller.perform(PlayerAction.SeekBack) })
            Item(pluralStringResource(Res.plurals.player_seek_forward, 10, "10"), onClick = { app.controller.perform(PlayerAction.SeekForward) })
            Separator()
            Item(stringResource(Res.string.player_previous), enabled = list?.hasPrevious == true, onClick = app::previous)
            Item(stringResource(Res.string.player_next), enabled = list?.hasNext == true, onClick = app::next)
            Menu(stringResource(Res.string.player_speed)) {
                playbackSpeeds.forEach { speed ->
                    CheckboxItem(formatSpeed(speed), checked = abs(playback.speed - speed) < 0.001f, onCheckedChange = { app.engine.setSpeed(speed) })
                }
            }
            Separator()
            Item(stringResource(Res.string.player_mute), onClick = { app.controller.perform(PlayerAction.ToggleMute) })
            pluginEntries(MenuGroup.Playback)
        }
        Menu(stringResource(Res.string.menu_view)) {
            Item(stringResource(Res.string.player_full_screen), enabled = inPlayer,
                shortcut = if (config.useMetaShortcuts) KeyShortcut(Key.F, meta = true, ctrl = true) else KeyShortcut(Key.F11),
                onClick = { app.fullScreen = !app.fullScreen })
        }
        // macOS convention: a Window menu (minimize, zoom) and a Help menu.
        if (config.systemAppMenu) {
            Menu(stringResource(Res.string.menu_window)) {
                Item(stringResource(Res.string.menu_minimize), shortcut = shortcut(Key.M), onClick = { window.state = Frame.ICONIFIED })
                Item(stringResource(Res.string.menu_zoom), onClick = {
                    window.extendedState = if (window.extendedState and Frame.MAXIMIZED_BOTH != 0) Frame.NORMAL else Frame.MAXIMIZED_BOTH
                })
            }
        }
        Menu(stringResource(Res.string.menu_help)) {
            Item(stringResource(Res.string.menu_help_site), onClick = {
                runCatching { java.awt.Desktop.getDesktop().browse(java.net.URI(HELP_URL)) }
            })
        }
    }
}

private const val HELP_URL = "https://github.com/kumasumit/ks-jaiplayer"

@Composable
private fun MenuScope.PluginEntry(entry: MenuEntry, state: PluginScreenState) {
    val children = entry.children
    if (children == null) {
        Item(entry.label(), enabled = entry.enabled(state), onClick = entry.onClick)
    } else {
        Menu(entry.label(), enabled = entry.enabled(state)) {
            children().forEach { PluginEntry(it, state) }
        }
    }
}

/** File dialogs and file checks for macOS and Windows. */
private class DesktopHost(private val nativeFolderDialog: Boolean, private val window: () -> Frame?) : PlatformHost {
    override val formFactor = FormFactor.Desktop

    override fun pickMedia(onPicked: (MediaRef) -> Unit) {
        choose(mediaExtensions)?.let { onPicked(mediaRefOf(it)) }
    }

    override fun pickSubtitle(onPicked: (String) -> Unit) {
        choose(subtitleExtensions)?.let { onPicked(fileUri(it)) }
    }

    override fun pickFolder(onPicked: (List<MediaRef>) -> Unit) {
        val folder = chooseFolder() ?: return
        val files = collectMedia(listOf(folder))
        if (files.isNotEmpty()) onPicked(files.map(::mediaRefOf))
    }

    private fun chooseFolder(): File? {
        if (nativeFolderDialog) {
            // macOS: the native dialog chooses folders while this property is set.
            System.setProperty("apple.awt.fileDialogForDirectories", "true")
            try {
                val dialog = FileDialog(window(), null as String?, FileDialog.LOAD)
                dialog.isVisible = true
                val name = dialog.file ?: return null
                return File(dialog.directory, name)
            } finally {
                System.setProperty("apple.awt.fileDialogForDirectories", "false")
            }
        }
        val chooser = JFileChooser().apply { fileSelectionMode = JFileChooser.DIRECTORIES_ONLY }
        return if (chooser.showOpenDialog(window()) == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
    }

    override fun exists(media: MediaRef) = fileOf(media)?.isFile == true

    override fun location(media: MediaRef) = fileOf(media)?.let(::shortLocation).orEmpty()

    private fun choose(extensions: Set<String>): File? {
        val dialog = FileDialog(window(), null as String?, FileDialog.LOAD)
        dialog.setFilenameFilter { _, name -> name.substringAfterLast('.', "").lowercase() in extensions }
        dialog.isVisible = true
        val name = dialog.file ?: return null
        return File(dialog.directory, name)
    }
}
