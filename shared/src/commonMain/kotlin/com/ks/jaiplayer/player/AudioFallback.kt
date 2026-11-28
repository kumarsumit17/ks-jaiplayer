package com.ks.jaiplayer.player

/** What the player does about audio it cannot play; the picture always keeps going. */
enum class AudioNotice {
    /** One audio track failed; another one is playing. */
    OtherTrack,

    /** No audio track can be played; the picture plays alone. */
    NoAudio,
}

/**
 * Decides what to play after an audio decoder fails, so that a broken decoder (some phones' Dolby
 * decoder, for example) never stops a video. [T] is the engine's own identity of an audio track.
 *
 * The order: the same track with the app's software decoder (when it has one for the format and
 * the platform's decoder was the one that failed), then another playable track, then the picture
 * alone. A track that failed is not chosen again for the same file; call [reset] when a file is
 * opened.
 */
class AudioFallback<T> {
    sealed interface Next<out T> {
        /** Keep the track, decoded by the app's software decoder instead of the platform's. */
        data object SoftwareDecoder : Next<Nothing>
        data class Track<T>(val track: T) : Next<T>
        data object PictureOnly : Next<Nothing>

        /** Nothing is left to play: the engine reports an error. */
        data object Stop : Next<Nothing>
    }

    private val failed = mutableSetOf<T>()
    private val triedSoftware = mutableSetOf<T>()

    fun reset() {
        failed.clear()
        triedSoftware.clear()
    }

    /**
     * [broken] failed to play; [playable] are the file's audio tracks the device says it supports;
     * [softwareDecoder] says whether the app's software decoder can take over the broken track.
     */
    fun after(broken: T, playable: List<T>, hasVideo: Boolean, softwareDecoder: Boolean = false): Next<T> {
        if (softwareDecoder && triedSoftware.add(broken)) return Next.SoftwareDecoder
        failed += broken
        val other = playable.firstOrNull { it !in failed }
        return when {
            other != null -> Next.Track(other)
            hasVideo -> Next.PictureOnly
            else -> Next.Stop
        }
    }
}
