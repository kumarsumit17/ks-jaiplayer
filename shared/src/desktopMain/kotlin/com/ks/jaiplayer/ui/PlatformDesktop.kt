package com.ks.jaiplayer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragData
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.ui.draganddrop.dragData
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import com.ks.jaiplayer.desktop.collectMedia
import com.ks.jaiplayer.desktop.mediaRefOf
import com.ks.jaiplayer.player.MediaRef
import com.ks.jaiplayer.player.PlayerEngine
import com.ks.jaiplayer.vlc.VlcPlayerEngine
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.FilterMipmap
import org.jetbrains.skia.FilterMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.MipmapMode
import org.jetbrains.skia.Rect
import java.io.File
import java.net.URI
import java.util.Locale
import kotlin.math.min

private val sampling = FilterMipmap(FilterMode.LINEAR, MipmapMode.NONE)

@Composable
actual fun VideoSurface(engine: PlayerEngine, modifier: Modifier) {
    val frames = (engine as? VlcPlayerEngine)?.frames ?: return
    Canvas(modifier) {
        frames.version.longValue // Read while drawing: a new frame redraws, without recomposing.
        synchronized(frames.lock) {
            val w = frames.width
            val h = frames.height
            if (w == 0 || h == 0 || frames.pixels.size != w * h * 4) return@Canvas
            val image = Image.makeRaster(ImageInfo(w, h, ColorType.BGRA_8888, ColorAlphaType.OPAQUE), frames.pixels, w * 4)
            val scale = min(size.width / w, size.height / h)
            val dw = w * scale
            val dh = h * scale
            val left = (size.width - dw) / 2
            val top = (size.height - dh) / 2
            drawIntoCanvas {
                it.nativeCanvas.drawImageRect(
                    image, Rect.makeWH(w.toFloat(), h.toFloat()), Rect.makeXYWH(left, top, dw, dh), sampling, null, true,
                )
            }
            image.close()
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
actual fun Modifier.mediaDropTarget(onDrop: (List<MediaRef>) -> Unit): Modifier = composed {
    val target = remember(onDrop) {
        object : DragAndDropTarget {
            override fun onDrop(event: DragAndDropEvent): Boolean {
                val dropped = (event.dragData() as? DragData.FilesList)?.readFiles().orEmpty()
                    .mapNotNull { runCatching { File(URI(it)) }.getOrNull() }
                // Dropped folders (a series, a season) are searched for their episodes.
                val files = collectMedia(dropped)
                if (files.isEmpty()) return false
                onDrop(files.map(::mediaRefOf))
                return true
            }
        }
    }
    dragAndDropTarget(shouldStartDragAndDrop = { it.dragData() is DragData.FilesList }, target = target)
}

@Composable
actual fun ProvideLanguage(language: String, content: @Composable () -> Unit) {
    // Compose resources follow the JVM's default locale on desktop; the subtree is rebuilt with
    // key() so every text is looked up again in the new language.
    remember(language) { Locale.setDefault(Locale.forLanguageTag(language)) }
    key(language) { content() }
}
