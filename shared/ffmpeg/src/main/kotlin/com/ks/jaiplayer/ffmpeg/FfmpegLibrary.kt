// Ported to Kotlin for KS JaiPlayer from AndroidX Media3's decoder_ffmpeg (FfmpegLibrary.java,
// Copyright 2017 The Android Open Source Project, Apache License 2.0). Only AC-3 and E-AC-3.
package com.ks.jaiplayer.ffmpeg

import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.LibraryLoader
import androidx.media3.common.util.UnstableApi

/** The native FFmpeg library (libffmpegJNI.so): loading, and which formats it decodes. */
@androidx.annotation.OptIn(UnstableApi::class)
object FfmpegLibrary {
    private val loader = object : LibraryLoader("ffmpegJNI") {
        override fun loadLibrary(name: String) = System.loadLibrary(name)
    }
    private var version: String? = null
    private var inputBufferPaddingSize = C.LENGTH_UNSET

    /** Whether the native library is present and loads (it is missing from builds for other ABIs). */
    @JvmStatic
    fun isAvailable(): Boolean = loader.isAvailable

    @JvmStatic
    fun getVersion(): String? = if (!isAvailable()) null else version ?: ffmpegGetVersion().also { version = it }

    @JvmStatic
    fun getInputBufferPaddingSize(): Int {
        if (!isAvailable()) return C.LENGTH_UNSET
        if (inputBufferPaddingSize == C.LENGTH_UNSET) inputBufferPaddingSize = ffmpegGetInputBufferPaddingSize()
        return inputBufferPaddingSize
    }

    /** Whether FFmpeg here can decode [mimeType]. */
    @JvmStatic
    fun supportsFormat(mimeType: String?): Boolean {
        val codec = codecNameOf(mimeType) ?: return false
        return isAvailable() && ffmpegHasDecoder(codec)
    }

    /** FFmpeg's decoder name for [mimeType], or null if this build has none for it. */
    internal fun codecNameOf(mimeType: String?): String? = when (mimeType) {
        MimeTypes.AUDIO_AC3 -> "ac3"
        MimeTypes.AUDIO_E_AC3, MimeTypes.AUDIO_E_AC3_JOC -> "eac3"
        else -> null
    }

    @JvmStatic private external fun ffmpegGetVersion(): String
    @JvmStatic private external fun ffmpegGetInputBufferPaddingSize(): Int
    @JvmStatic private external fun ffmpegHasDecoder(codecName: String): Boolean
}
