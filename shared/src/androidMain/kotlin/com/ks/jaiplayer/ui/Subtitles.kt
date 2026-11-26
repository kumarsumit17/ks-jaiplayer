package com.ks.jaiplayer.ui

import android.content.Context
import android.view.accessibility.CaptioningManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.media3.common.text.Cue

/**
 * Draws the subtitles ExoPlayer decodes (Media3's Compose surface draws only the picture).
 * Text follows the viewer's caption settings in Android's accessibility options (size, colours,
 * background); picture subtitles (Blu-ray PGS, DVB) are placed where the cue says.
 * Laid out over the video's own area, so they scale with it.
 */
@Composable
internal fun Subtitles(cues: List<Cue>, modifier: Modifier = Modifier) {
    if (cues.isEmpty()) return
    val style = rememberCaptionStyle()
    BoxWithConstraints(modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val textSize = with(density) { (maxHeight * TEXT_FRACTION * style.scale).toSp() }
        cues.filter { it.bitmap != null }.forEach { cue ->
            val bitmap = cue.bitmap ?: return@forEach
            val w = if (cue.size != Cue.DIMEN_UNSET) maxWidth * cue.size else maxWidth * 0.5f
            val h = if (cue.bitmapHeight != Cue.DIMEN_UNSET) maxHeight * cue.bitmapHeight else w * bitmap.height / bitmap.width
            val x = if (cue.position != Cue.DIMEN_UNSET) maxWidth * cue.position else (maxWidth - w) / 2
            val y = if (cue.line != Cue.DIMEN_UNSET && cue.lineType == Cue.LINE_TYPE_FRACTION) maxHeight * cue.line else maxHeight - h - 24.dp
            Image(bitmap.asImageBitmap(), null, Modifier.offset(x, y).size(w, h))
        }
        val text = cues.mapNotNull { it.text?.toString()?.takeIf(String::isNotBlank) }
        if (text.isNotEmpty()) {
            Column(
                Modifier.align(Alignment.BottomCenter).padding(start = 32.dp, end = 32.dp, bottom = maxHeight * 0.06f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                text.forEach { line ->
                    Text(
                        line,
                        Modifier.background(style.background).padding(horizontal = 6.dp, vertical = 1.dp),
                        style = TextStyle(
                            color = style.foreground,
                            fontSize = textSize,
                            textAlign = TextAlign.Center,
                            shadow = if (style.background.alpha < 0.3f) Shadow(Color.Black, blurRadius = 6f) else null,
                        ),
                    )
                }
            }
        }
    }
}

private class CaptionStyle(val foreground: Color, val background: Color, val scale: Float)

@Composable
private fun rememberCaptionStyle(): CaptionStyle {
    val context = LocalContext.current
    return remember(context) { captionStyle(context) }
}

private fun captionStyle(context: Context): CaptionStyle {
    val manager = context.getSystemService(Context.CAPTIONING_SERVICE) as? CaptioningManager
    if (manager == null || !manager.isEnabled) return CaptionStyle(Color.White, Color.Transparent, 1f)
    val s = manager.userStyle
    return CaptionStyle(
        foreground = if (s.hasForegroundColor()) Color(s.foregroundColor) else Color.White,
        background = if (s.hasBackgroundColor()) Color(s.backgroundColor) else Color.Transparent,
        scale = manager.fontScale,
    )
}

/** Media3's default: text is 5.33 % of the video's height before the viewer's scale. */
private const val TEXT_FRACTION = 0.0533f
