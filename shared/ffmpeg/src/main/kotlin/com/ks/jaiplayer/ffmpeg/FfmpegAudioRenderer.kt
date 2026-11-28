// Ported to Kotlin for KS JaiPlayer from AndroidX Media3's decoder_ffmpeg (FfmpegAudioRenderer.java,
// Copyright 2016 The Android Open Source Project, Apache License 2.0).
package com.ks.jaiplayer.ffmpeg

import android.os.Handler
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.decoder.CryptoConfig
import androidx.media3.exoplayer.RendererCapabilities
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DecoderAudioRenderer

/** Decodes AC-3 and E-AC-3 with FFmpeg when the device has no working decoder for them. */
@androidx.annotation.OptIn(UnstableApi::class)
class FfmpegAudioRenderer(
    eventHandler: Handler?,
    eventListener: AudioRendererEventListener?,
    audioSink: AudioSink,
) : DecoderAudioRenderer<FfmpegAudioDecoder>(eventHandler, eventListener, audioSink) {

    override fun getName() = "FfmpegAudioRenderer"

    override fun supportsFormatInternal(format: Format): Int {
        val mimeType = format.sampleMimeType
        return when {
            !FfmpegLibrary.isAvailable() || !MimeTypes.isAudio(mimeType) -> C.FORMAT_UNSUPPORTED_TYPE
            !FfmpegLibrary.supportsFormat(mimeType) ||
                (!sinkSupports(format, C.ENCODING_PCM_16BIT) && !sinkSupports(format, C.ENCODING_PCM_FLOAT)) -> C.FORMAT_UNSUPPORTED_SUBTYPE
            format.cryptoType != C.CRYPTO_TYPE_NONE -> C.FORMAT_UNSUPPORTED_DRM
            else -> C.FORMAT_HANDLED
        }
    }

    override fun supportsMixedMimeTypeAdaptation() = RendererCapabilities.ADAPTIVE_NOT_SEAMLESS

    override fun createDecoder(format: Format, cryptoConfig: CryptoConfig?): FfmpegAudioDecoder {
        val inputSize = if (format.maxInputSize != Format.NO_VALUE) format.maxInputSize else DEFAULT_INPUT_BUFFER_SIZE
        // AC-3 and E-AC-3 decode to 16-bit; floating point would only double the buffers.
        val outputFloat = !sinkSupports(format, C.ENCODING_PCM_16BIT)
        return FfmpegAudioDecoder(format, NUM_BUFFERS, NUM_BUFFERS, inputSize, outputFloat)
    }

    override fun getOutputFormat(decoder: FfmpegAudioDecoder): Format = Format.Builder()
        .setSampleMimeType(MimeTypes.AUDIO_RAW)
        .setChannelCount(decoder.channelCount)
        .setSampleRate(decoder.sampleRate)
        .setPcmEncoding(decoder.encoding)
        .build()

    private fun sinkSupports(format: Format, pcmEncoding: Int) =
        sinkSupportsFormat(Util.getPcmFormat(pcmEncoding, format.channelCount, format.sampleRate))

    private companion object {
        const val NUM_BUFFERS = 16
        const val DEFAULT_INPUT_BUFFER_SIZE = 960 * 6
    }
}
