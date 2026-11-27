package com.ks.jaiplayer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
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
import com.ks.jaiplayer.vlc.FrameStats
import com.ks.jaiplayer.vlc.VlcPlayerEngine
import com.sun.jna.Pointer
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.FilterMipmap
import org.jetbrains.skia.FilterMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.MipmapMode
import org.jetbrains.skia.Pixmap
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Matrix33
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.RuntimeShaderBuilder
import org.jetbrains.skia.RuntimeEffect
import java.io.File
import java.net.URI
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

private val sampling = FilterMipmap(FilterMode.LINEAR, MipmapMode.NONE)

/**
 * Turns the three planes of a YUV 4:2:0 frame into colour on the GPU. Video is "limited range"
 * (black at 16, white at 235); HD and larger use the BT.709 colours, smaller video BT.601.
 */
private val yuvToRgb: RuntimeEffect by lazy {
    RuntimeEffect.makeForShader(
        """
        uniform shader luma;
        uniform shader cb;
        uniform shader cr;
        uniform float hd;
        half4 main(float2 p) {
            float y = (luma.eval(p).r - 16.0 / 255.0) * (255.0 / 219.0);
            float u = (cb.eval(p * 0.5).r - 128.0 / 255.0) * (255.0 / 224.0);
            float v = (cr.eval(p * 0.5).r - 128.0 / 255.0) * (255.0 / 224.0);
            float3 rgb = hd > 0.5
                ? float3(y + 1.5748 * v, y - 0.1873 * u - 0.4681 * v, y + 1.8556 * u)
                : float3(y + 1.402 * v, y - 0.3441 * u - 0.7141 * v, y + 1.772 * u);
            return half4(half3(saturate(rgb)), 1.0);
        }
        """.trimIndent(),
    )
}

@Composable
actual fun VideoSurface(engine: PlayerEngine, modifier: Modifier, fill: Boolean) {
    val frames = (engine as? VlcPlayerEngine)?.frames ?: return
    val paint = remember { Paint() }
    Canvas(modifier.clipToBounds()) {
        frames.version.longValue // Read while drawing: a new frame redraws, without recomposing.
        val start = System.nanoTime()
        val frame = frames.frameToDraw() ?: return@Canvas
        if (System.getenv("JAIPLAYER_GPU_CACHE") != "default") GpuCacheLimit.apply()
        val l = frame.layout
        val w = l.width
        val h = l.height
        // The planes are wrapped without copying; VideoFrames keeps them unchanged while shown.
        val base = Pointer.nativeValue(frame.memory)
        val planes = listOf(
            plane(base, w, h, l.lumaPitch),
            plane(base + l.uOffset, l.chromaWidth, l.chromaHeight, l.chromaPitch),
            plane(base + l.vOffset, l.chromaWidth, l.chromaHeight, l.chromaPitch),
        )
        val scale = if (fill) max(size.width / w, size.height / h) else min(size.width / w, size.height / h)
        val dw = w * scale
        val dh = h * scale
        val left = (size.width - dw) / 2
        val top = (size.height - dh) / 2
        val builder = RuntimeShaderBuilder(yuvToRgb)
        listOf("luma", "cb", "cr").forEachIndexed { i, name ->
            builder.child(name, planes[i].second.makeShader(FilterTileMode.CLAMP, FilterTileMode.CLAMP, sampling, null))
        }
        builder.uniform("hd", if (h >= 720 || w >= 1280) 1f else 0f)
        val shader = builder.makeShader(Matrix33(scale, 0f, left, 0f, scale, top, 0f, 0f, 1f))
        paint.shader = shader
        drawIntoCanvas { it.nativeCanvas.drawRect(Rect.makeXYWH(left, top, dw, dh), paint) }
        paint.shader = null
        shader.close()
        builder.close()
        planes.forEach { (pixmap, image) -> image.close(); pixmap.close() }
        FrameStats.frameDrawn(System.nanoTime() - start)
    }
}

/** One 8-bit plane of a frame as a Skia image over the native memory (no copy). */
private fun plane(address: Long, width: Int, height: Int, pitch: Int): Pair<Pixmap, Image> {
    val pixmap = Pixmap.make(ImageInfo(width, height, ColorType.GRAY_8, ColorAlphaType.OPAQUE), address, pitch)
    return pixmap to Image.makeFromPixmap(pixmap)
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
