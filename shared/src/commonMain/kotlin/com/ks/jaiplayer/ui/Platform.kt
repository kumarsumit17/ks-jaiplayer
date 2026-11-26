package com.ks.jaiplayer.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ks.jaiplayer.player.MediaRef
import com.ks.jaiplayer.player.PlayerEngine
import org.jetbrains.compose.resources.StringResource

/** Which kind of device the interface is laid out for. */
enum class FormFactor { Desktop, Phone, Tv }

/** What the shared interface needs from the platform it runs on. */
interface PlatformHost {
    val formFactor: FormFactor

    /** Shows the platform's file picker for video and audio; calls [onPicked] if a file is chosen. */
    fun pickMedia(onPicked: (MediaRef) -> Unit)

    /**
     * Lets the user choose a folder (a series, a season) and returns its video and audio files,
     * subfolders included, in natural name order ([com.ks.jaiplayer.player.naturalNameOrder]).
     */
    fun pickFolder(onPicked: (List<MediaRef>) -> Unit)

    /** Shows the file picker for subtitle files; calls [onPicked] with the file's URI. */
    fun pickSubtitle(onPicked: (String) -> Unit)

    /** Whether the file is still there (recent files that were moved or deleted are greyed out). */
    fun exists(media: MediaRef): Boolean

    /** A short form of where the file is, shown under its name in the recent list. */
    fun location(media: MediaRef): String

    /** The device's media library, browsed in the app instead of a picker (the Google TV app); null elsewhere. */
    val library: MediaLibrary? get() = null

    /** The screen's brightness for the player (the phone's left-side swipe); null where it cannot be set. */
    val brightness: BrightnessControl? get() = null

    /** Switches that only make sense on this platform, shown under "This device" in Settings. */
    val deviceSettings: List<DeviceSetting> get() = emptyList()
}

/** Sets the brightness of the app's window only, 0 to 1; [reset] gives it back to the system setting. */
interface BrightnessControl {
    fun current(): Float
    fun set(level: Float)
    fun reset()
}

/** An on/off setting of one platform (frame-rate matching on TV, background audio on the phone). */
class DeviceSetting(
    val label: StringResource,
    val description: StringResource,
    val isOn: () -> Boolean,
    val set: (Boolean) -> Unit,
)

/**
 * Draws the engine's picture. Each platform draws its own engine's frames. With [fill] the
 * picture covers the whole area and its edges are cut off; otherwise it fits inside.
 */
@Composable
expect fun VideoSurface(engine: PlayerEngine, modifier: Modifier = Modifier, fill: Boolean = false)

/** Lets files be dropped on the element (desktop); no effect where there is no drag and drop. */
expect fun Modifier.mediaDropTarget(onDrop: (List<MediaRef>) -> Unit): Modifier

/** Shows [content] with interface text in [language] (`en` or `hi`). */
@Composable
expect fun ProvideLanguage(language: String, content: @Composable () -> Unit)
