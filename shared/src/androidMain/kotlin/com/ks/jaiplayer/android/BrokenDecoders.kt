package com.ks.jaiplayer.android

import android.content.Context
import android.os.Build
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector

/**
 * Platform decoders that the device lists but that fail when used (the Pixel 11's Dolby AC-3/E-AC-3
 * decoder rejects the app's first buffer). They are left out of Media3's decoder choice, so the
 * format goes to the next decoder, or to the app's FFmpeg decoder. Remembered per Android build: a
 * system update may fix the decoder, and it is then tried again.
 */
@androidx.annotation.OptIn(UnstableApi::class)
internal class BrokenDecoders(context: Context) {
    private val prefs = context.getSharedPreferences("broken-decoders", Context.MODE_PRIVATE)
    @Volatile private var names: Set<String> =
        if (prefs.getString(BUILD_KEY, null) == Build.FINGERPRINT) prefs.getStringSet(NAMES_KEY, emptySet()).orEmpty().toSet() else emptySet()

    val selector = MediaCodecSelector { mimeType, requiresSecureDecoder, requiresTunnelingDecoder ->
        MediaCodecSelector.DEFAULT.getDecoderInfos(mimeType, requiresSecureDecoder, requiresTunnelingDecoder).filter { it.name !in names }
    }

    fun add(name: String) {
        names = names + name
        prefs.edit().putString(BUILD_KEY, Build.FINGERPRINT).putStringSet(NAMES_KEY, names).apply()
    }

    private companion object {
        const val BUILD_KEY = "build"
        const val NAMES_KEY = "names"
    }
}
