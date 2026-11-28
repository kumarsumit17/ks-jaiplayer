// Ported to Kotlin for KS JaiPlayer from AndroidX Media3's decoder_ffmpeg (FfmpegAudioDecoder.java,
// Copyright 2016 The Android Open Source Project, Apache License 2.0). AC-3 and E-AC-3 need no
// codec extra data, so that code is left out. The native methods and growOutputBuffer keep their
// names and signatures: ffmpeg_jni.cc binds them.
package com.ks.jaiplayer.ffmpeg

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi
import androidx.media3.decoder.DecoderInputBuffer
import androidx.media3.decoder.SimpleDecoder
import androidx.media3.decoder.SimpleDecoderOutputBuffer
import java.nio.ByteBuffer

/** Decodes AC-3 and E-AC-3 to PCM with FFmpeg. */
@androidx.annotation.OptIn(UnstableApi::class)
class FfmpegAudioDecoder(
    format: Format,
    numInputBuffers: Int,
    numOutputBuffers: Int,
    initialInputBufferSize: Int,
    outputFloat: Boolean,
) : SimpleDecoder<DecoderInputBuffer, SimpleDecoderOutputBuffer, FfmpegDecoderException>(
    // SimpleDecoder fills both arrays itself.
    @Suppress("UNCHECKED_CAST") (arrayOfNulls<DecoderInputBuffer>(numInputBuffers) as Array<DecoderInputBuffer>),
    @Suppress("UNCHECKED_CAST") (arrayOfNulls<SimpleDecoderOutputBuffer>(numOutputBuffers) as Array<SimpleDecoderOutputBuffer>),
) {
    private val codecName: String
    /** PCM encoding of the output. */
    val encoding: Int = if (outputFloat) C.ENCODING_PCM_FLOAT else C.ENCODING_PCM_16BIT
    private var outputBufferSize = if (outputFloat) INITIAL_OUTPUT_BUFFER_SIZE_16BIT * 2 else INITIAL_OUTPUT_BUFFER_SIZE_16BIT
    private var nativeContext: Long
    private var hasOutputFormat = false
    @Volatile var channelCount = 0; private set
    @Volatile var sampleRate = 0; private set

    init {
        if (!FfmpegLibrary.isAvailable()) throw FfmpegDecoderException("Failed to load decoder native libraries.")
        codecName = FfmpegLibrary.codecNameOf(format.sampleMimeType) ?: throw FfmpegDecoderException("Unsupported format ${format.sampleMimeType}")
        nativeContext = ffmpegInitialize(codecName, null, outputFloat, format.sampleRate, format.channelCount)
        if (nativeContext == 0L) throw FfmpegDecoderException("Initialization failed.")
        setInitialInputBufferSize(initialInputBufferSize)
    }

    override fun getName(): String = "ffmpeg" + FfmpegLibrary.getVersion() + "-" + codecName

    override fun createInputBuffer() =
        DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_DIRECT, FfmpegLibrary.getInputBufferPaddingSize())

    override fun createOutputBuffer() = SimpleDecoderOutputBuffer { releaseOutputBuffer(it) }

    override fun createUnexpectedDecodeException(error: Throwable) = FfmpegDecoderException("Unexpected decode error", error)

    override fun decode(inputBuffer: DecoderInputBuffer, outputBuffer: SimpleDecoderOutputBuffer, reset: Boolean): FfmpegDecoderException? {
        if (reset) {
            nativeContext = ffmpegReset(nativeContext, null)
            if (nativeContext == 0L) return FfmpegDecoderException("Error resetting (see logcat).")
        }
        val inputData = inputBuffer.data!!
        val inputSize = inputData.limit()
        val outputData = outputBuffer.init(inputBuffer.timeUs, outputBufferSize)
        val result = ffmpegDecode(nativeContext, inputData, inputSize, outputBuffer, outputData, outputBufferSize)
        when {
            result == AUDIO_DECODER_ERROR_OTHER -> return FfmpegDecoderException("Error decoding (see logcat).")
            // Invalid data is not fatal, as with MediaCodec; empty output need not be sent on.
            result == AUDIO_DECODER_ERROR_INVALID_DATA || result == 0 -> {
                outputBuffer.shouldBeSkipped = true
                return null
            }
        }
        if (!hasOutputFormat) {
            channelCount = ffmpegGetChannelCount(nativeContext)
            sampleRate = ffmpegGetSampleRate(nativeContext)
            hasOutputFormat = true
        }
        // The native decode may have grown (reallocated) the buffer.
        outputBuffer.data!!.apply { position(0); limit(result) }
        return null
    }

    /** Called from native code when the output needs more room. */
    @Suppress("unused")
    private fun growOutputBuffer(outputBuffer: SimpleDecoderOutputBuffer, requiredSize: Int): ByteBuffer {
        outputBufferSize = requiredSize
        return outputBuffer.grow(requiredSize)
    }

    override fun release() {
        super.release()
        ffmpegRelease(nativeContext)
        nativeContext = 0
    }

    private external fun ffmpegInitialize(codecName: String, extraData: ByteArray?, outputFloat: Boolean, rawSampleRate: Int, rawChannelCount: Int): Long
    private external fun ffmpegDecode(context: Long, inputData: ByteBuffer, inputSize: Int, decoderOutputBuffer: SimpleDecoderOutputBuffer, outputData: ByteBuffer, outputSize: Int): Int
    private external fun ffmpegGetChannelCount(context: Long): Int
    private external fun ffmpegGetSampleRate(context: Long): Int
    private external fun ffmpegReset(context: Long, extraData: ByteArray?): Long
    private external fun ffmpegRelease(context: Long)

    private companion object {
        const val INITIAL_OUTPUT_BUFFER_SIZE_16BIT = 65535
        const val AUDIO_DECODER_ERROR_INVALID_DATA = -1
        const val AUDIO_DECODER_ERROR_OTHER = -2
    }
}
