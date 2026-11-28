// Ported to Kotlin for KS JaiPlayer from AndroidX Media3's decoder_ffmpeg (Apache License 2.0).
package com.ks.jaiplayer.ffmpeg

import androidx.media3.common.util.UnstableApi
import androidx.media3.decoder.DecoderException

/** An FFmpeg decoder error. */
@androidx.annotation.OptIn(UnstableApi::class)
class FfmpegDecoderException(message: String, cause: Throwable? = null) : DecoderException(message, cause)
