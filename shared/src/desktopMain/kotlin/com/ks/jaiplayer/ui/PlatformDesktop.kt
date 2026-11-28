package com.ks.jaiplayer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import com.ks.jaiplayer.vlc.NativeVideoLayer
import com.ks.jaiplayer.vlc.VideoColour
import com.ks.jaiplayer.vlc.VideoFrames
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
private val exact = FilterMipmap(FilterMode.NEAREST, MipmapMode.NONE)

/**
 * The end of every frame shader: R'G'B' (still in the video's transfer) to screen colour. For SDR
 * in BT.709 the values go out as they are; [HDR_TO_SDR] replaces it for HDR and BT.2020 video.
 */
private const val SDR_OUT = "return half4(half3(saturate(rgb)), 1.0);"

/**
 * HDR to SDR, on the GPU: PQ or HLG to linear light (1.0 = 100 nits), highlights desaturated and
 * compressed with the Hable curve from [hdr].y down to SDR white, BT.2020 colours converted to
 * BT.709, then sRGB encoding. SDR video in BT.2020 colours (mode 3) is only converted. Uniforms:
 * hdr = (mode: 1 PQ, 2 HLG, 3 SDR BT.2020; peak in 100 nits; desaturation strength; exponent).
 * Chosen to look like VLC 3's tone mapping (specs/plan-macos-playback.md); HDRToSdrReference in
 * the tests is the same maths in double precision.
 */
private const val HDR_FUNCTIONS = """
uniform float4 hdr;
float3 pqToLinear(float3 e) {
    float3 p = pow(max(e, 0.0), float3(1.0 / 78.84375));
    return pow(max(p - 0.8359375, 0.0) / (18.8515625 - 18.6875 * p), float3(1.0 / 0.1593017578125)) * 100.0;
}
float hlgToScene(float e) {
    e = saturate(e);
    return e <= 0.5 ? e * e / 3.0 : (exp((e - 0.55991073) / 0.17883277) + 0.28466892) / 12.0;
}
float hable(float x) { return (x * (0.15 * x + 0.05) + 0.004) / (x * (0.15 * x + 0.5) + 0.06) - 0.02 / 0.3; }
float srgbEncode(float x) { return x <= 0.0031308 ? 12.92 * x : 1.055 * pow(x, 1.0 / 2.4) - 0.055; }
half4 toScreen(float3 e) {
    float3 lin;
    if (hdr.x == 1.0) {
        lin = pqToLinear(e);
    } else if (hdr.x == 2.0) {
        float3 s = float3(hlgToScene(e.r), hlgToScene(e.g), hlgToScene(e.b));
        lin = s * 10.0 * pow(max(dot(s, float3(0.2627, 0.6780, 0.0593)), 1e-6), 0.2);
    } else {
        lin = pow(saturate(e), float3(2.4));
    }
    if (hdr.x != 3.0) {
        float sig = max(max(lin.r, lin.g), lin.b);
        if (sig > 1e-6) {
            float luma = dot(lin, float3(0.2627, 0.6780, 0.0593));
            float coeff = pow(max(sig - 0.18, 1e-6) / sig, hdr.w) * hdr.z;
            lin = mix(lin, float3(luma), coeff);
            sig = max(mix(sig, luma, coeff), 1e-6);
            lin *= hable(sig) / hable(hdr.y) / sig;
        }
    }
    float3 o = saturate(float3(
        dot(float3(1.6605, -0.5876, -0.0728), lin),
        dot(float3(-0.1246, 1.1329, -0.0083), lin),
        dot(float3(-0.0182, -0.1006, 1.1187), lin)));
    if (hdr.x == 3.0) return half4(half3(pow(o, float3(1.0 / 2.4))), 1.0);
    return half4(srgbEncode(o.r), srgbEncode(o.g), srgbEncode(o.b), 1.0);
}
"""

private const val HDR_OUT = "return toScreen(rgb);"

/**
 * Turns an 8-bit I420 frame into colour on the GPU. Each plane is a GRAY_8 image (value in red),
 * filtered by the GPU. [range] maps the samples to video range, and [matrix] holds the YUV-to-RGB
 * weights of BT.601, BT.709 or BT.2020.
 */
private fun yuv8Source(hdr: Boolean) = """
uniform shader luma;
uniform shader cb;
uniform shader cr;
uniform float4 range;
uniform float4 matrix;
${if (hdr) HDR_FUNCTIONS else ""}
half4 main(float2 p) {
    float y = (luma.eval(p).r - range.x) * range.y;
    float u = (cb.eval(p * 0.5).r - range.z) * range.w;
    float v = (cr.eval(p * 0.5).r - range.z) * range.w;
    float3 rgb = float3(y + matrix.x * v, y - matrix.y * u - matrix.z * v, y + matrix.w * u);
    ${if (hdr) HDR_OUT else SDR_OUT}
}
"""

/**
 * Turns a P010 frame (10-bit, 16 bits a sample) into colour on the GPU. This Skia build passes
 * its 16-bit colour types to the native library as other types, so each plane is wrapped as
 * GRAY_8 bytes, twice as wide, read without filtering: the shader joins each sample's low and
 * high byte and filters bilinearly itself.
 */
private fun p010Source(hdr: Boolean) = """
uniform shader luma;
uniform shader chroma;
uniform float2 lumaSize;
uniform float2 chromaSize;
uniform float4 range;
uniform float4 matrix;
${if (hdr) HDR_FUNCTIONS else ""}
float word(float lo, float hi) { return (lo * 255.0 + hi * 65280.0) / 65535.0; }
float lumaAt(float2 i) {
    i = clamp(i, float2(0.0), lumaSize - 1.0);
    float2 b = float2(i.x * 2.0 + 0.5, i.y + 0.5);
    return word(luma.eval(b).r, luma.eval(b + float2(1.0, 0.0)).r);
}
float2 chromaAt(float2 i) {
    i = clamp(i, float2(0.0), chromaSize - 1.0);
    float2 b = float2(i.x * 4.0 + 0.5, i.y + 0.5);
    return float2(word(chroma.eval(b).r, chroma.eval(b + float2(1.0, 0.0)).r),
                  word(chroma.eval(b + float2(2.0, 0.0)).r, chroma.eval(b + float2(3.0, 0.0)).r));
}
half4 main(float2 p) {
    float2 t = p - 0.5;
    float2 i = floor(t);
    float2 f = t - i;
    float yRaw = mix(mix(lumaAt(i), lumaAt(i + float2(1.0, 0.0)), f.x),
                     mix(lumaAt(i + float2(0.0, 1.0)), lumaAt(i + float2(1.0, 1.0)), f.x), f.y);
    float2 ct = p * 0.5 - 0.5;
    float2 ci = floor(ct);
    float2 cf = ct - ci;
    float2 c = mix(mix(chromaAt(ci), chromaAt(ci + float2(1.0, 0.0)), cf.x),
                   mix(chromaAt(ci + float2(0.0, 1.0)), chromaAt(ci + float2(1.0, 1.0)), cf.x), cf.y);
    float y = (yRaw - range.x) * range.y;
    float u = (c.x - range.z) * range.w;
    float v = (c.y - range.z) * range.w;
    float3 rgb = float3(y + matrix.x * v, y - matrix.y * u - matrix.z * v, y + matrix.w * u);
    ${if (hdr) HDR_OUT else SDR_OUT}
}
"""

/** The four frame shaders (layout × SDR/HDR), each compiled the first time it is needed. */
private val yuv8Sdr: RuntimeEffect by lazy { RuntimeEffect.makeForShader(yuv8Source(hdr = false)) }
private val yuv8Hdr: RuntimeEffect by lazy { RuntimeEffect.makeForShader(yuv8Source(hdr = true)) }
private val p010Sdr: RuntimeEffect by lazy { RuntimeEffect.makeForShader(p010Source(hdr = false)) }
private val p010Hdr: RuntimeEffect by lazy { RuntimeEffect.makeForShader(p010Source(hdr = true)) }

/** Highlight desaturation of the tone mapping: strength and exponent (as VLC 3 / mpv). */
internal const val HDR_DESATURATION = 0.5f
internal const val HDR_DESATURATION_EXPONENT = 2.0f

/**
 * Range mapping (black, luma scale, chroma centre, chroma scale) for each layout: limited range
 * (black at 16, white at 235 in 8 bits), or full range when the file says so.
 */
private fun rangeOf(chroma: VideoFrames.Chroma, full: Boolean): FloatArray = when (chroma) {
    VideoFrames.Chroma.I420 -> if (full) floatArrayOf(0f, 1f, 128f / 255f, 1f) else floatArrayOf(16f / 255f, 255f / 219f, 128f / 255f, 255f / 224f)
    VideoFrames.Chroma.P010 -> if (full) floatArrayOf(0f, 65535f / 65472f, 32768f / 65535f, 65535f / 65472f)
    else floatArrayOf(4096f / 65535f, 65535f / (876f * 64f), 32768f / 65535f, 65535f / (896f * 64f))
}

private val BT2020_WEIGHTS = floatArrayOf(1.4746f, 0.16455f, 0.57135f, 1.8814f)
private val BT709_WEIGHTS = floatArrayOf(1.5748f, 0.18732f, 0.46812f, 1.8556f)
private val BT601_WEIGHTS = floatArrayOf(1.402f, 0.34414f, 0.71414f, 1.772f)

/**
 * YUV-to-RGB weights (R from Cr, G from Cb, G from Cr, B from Cb): the file's own matrix when it
 * states one ([VideoColour], read from the container since libVLC 3 does not pass it on);
 * otherwise by size, as players do without it: 10-bit UHD is BT.2020, HD and larger BT.709,
 * smaller BT.601.
 */
private fun matrixOf(layout: VideoFrames.Layout): FloatArray = when (layout.colour.matrix) {
    9, 10 -> BT2020_WEIGHTS
    1 -> BT709_WEIGHTS
    5, 6 -> BT601_WEIGHTS
    else -> when {
        layout.chroma == VideoFrames.Chroma.P010 && (layout.width >= 3840 || layout.height >= 2160) -> BT2020_WEIGHTS
        layout.height >= 720 || layout.width >= 1280 -> BT709_WEIGHTS
        else -> BT601_WEIGHTS
    }
}

@Composable
actual fun VideoSurface(engine: PlayerEngine, modifier: Modifier, fill: Boolean) {
    val frames = (engine as? VlcPlayerEngine)?.frames ?: return
    if (NativeVideoLayer.enabled) {
        // Trial only: the video is a native layer under the window; leave a see-through hole.
        DisposableEffect(fill) {
            NativeVideoLayer.show(visible = true, fill = fill)
            onDispose { NativeVideoLayer.show(visible = false, fill = fill) }
        }
        Canvas(modifier) { drawRect(androidx.compose.ui.graphics.Color.Transparent, blendMode = androidx.compose.ui.graphics.BlendMode.Clear) }
        return
    }
    val paint = remember { Paint() }
    Canvas(modifier.clipToBounds()) {
        frames.version.longValue // Read while drawing: a new frame redraws, without recomposing.
        val start = System.nanoTime()
        val frame = frames.frameToDraw() ?: return@Canvas
        if (System.getenv("JAIPLAYER_GPU_CACHE") != "default") GpuCacheLimit.apply()
        val l = frame.layout
        val scale = if (fill) max(size.width / l.width, size.height / l.height) else min(size.width / l.width, size.height / l.height)
        val left = (size.width - l.width * scale) / 2
        val top = (size.height - l.height * scale) / 2
        drawIntoCanvas { drawVideoFrame(it.nativeCanvas, frame, left, top, scale, paint) }
        FrameStats.frameDrawn(System.nanoTime() - start)
    }
}

/**
 * Draws [frame] at ([left], [top]) scaled by [scale], turning YUV into colour on the GPU. The
 * planes are wrapped without copying; VideoFrames keeps them unchanged while shown.
 */
internal fun drawVideoFrame(canvas: org.jetbrains.skia.Canvas, frame: VideoFrames.Frame, left: Float, top: Float, scale: Float, paint: Paint) {
    val l = frame.layout
    val w = l.width
    val h = l.height
    val base = Pointer.nativeValue(frame.memory)
    val c = l.chroma
    val wide = c == VideoFrames.Chroma.P010
    val planes = if (wide) {
        // Twice as many 8-bit bytes (see p010Source); the chroma plane holds both Cb and Cr.
        listOf(plane(base, w * 2, h, l.lumaPitch), plane(base + l.uOffset, l.chromaWidth * 4, l.chromaHeight, l.chromaPitch))
    } else {
        listOf(
            plane(base, w, h, l.lumaPitch),
            plane(base + l.uOffset, l.chromaWidth, l.chromaHeight, l.chromaPitch),
            plane(base + l.vOffset, l.chromaWidth, l.chromaHeight, l.chromaPitch),
        )
    }
    val colour = l.colour
    val hdr = colour.needsMapping
    val builder = RuntimeShaderBuilder(if (wide) (if (hdr) p010Hdr else p010Sdr) else (if (hdr) yuv8Hdr else yuv8Sdr))
    val filter = if (wide) exact else sampling
    val shaders = planes.map { it.second.makeShader(FilterTileMode.CLAMP, FilterTileMode.CLAMP, filter, null) }
    val names = if (wide) listOf("luma", "chroma") else listOf("luma", "cb", "cr")
    names.forEachIndexed { i, name -> builder.child(name, shaders[i]) }
    if (wide) {
        builder.uniform("lumaSize", w.toFloat(), h.toFloat())
        builder.uniform("chromaSize", l.chromaWidth.toFloat(), l.chromaHeight.toFloat())
    }
    rangeOf(c, colour.fullRange).let { builder.uniform("range", it[0], it[1], it[2], it[3]) }
    if (hdr) {
        val mode = when (colour.transfer) { VideoColour.PQ -> 1f; VideoColour.HLG -> 2f; else -> 3f }
        builder.uniform("hdr", mode, colour.toneMapPeak, HDR_DESATURATION, HDR_DESATURATION_EXPONENT)
    }
    matrixOf(l).let { builder.uniform("matrix", it[0], it[1], it[2], it[3]) }
    val shader = builder.makeShader(Matrix33(scale, 0f, left, 0f, scale, top, 0f, 0f, 1f))
    paint.shader = shader
    canvas.drawRect(Rect.makeXYWH(left, top, w * scale, h * scale), paint)
    paint.shader = null
    shader.close()
    builder.close()
    shaders.forEach { it.close() }
    planes.forEach { (pixmap, image) -> image.close(); pixmap.close() }
}

/** One plane of a frame as an 8-bit Skia image over the native memory (no copy). */
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
