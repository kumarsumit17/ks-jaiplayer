package com.ks.jaiplayer.player

import kotlinx.coroutines.flow.StateFlow

/**
 * One playback engine per platform, behind one interface: libVLC on macOS and Windows
 * (desktopMain), Media3 ExoPlayer on Android and Google TV (androidMain).
 *
 * Calls return at once; the result shows up in [state]. An engine never throws for a file it
 * cannot play: it reports [PlaybackStatus.Error] with a [PlayerError].
 */
interface PlayerEngine {
    val state: StateFlow<PlaybackState>

    /** Opens [media] and starts playing it, replacing whatever was playing. */
    fun open(media: MediaRef)

    fun play()
    fun pause()

    /** Moves to [positionMs], clamped by the engine to the media's length. */
    fun seekTo(positionMs: Long)

    /** Volume from 0 to 100. */
    fun setVolume(volume: Int)
    fun setMuted(muted: Boolean)

    /** Playback speed, 1 being normal; see [playbackSpeeds]. */
    fun setSpeed(speed: Float)

    fun selectAudioTrack(id: Int)

    /** Shows the subtitle track [id], or none when [id] is null. */
    fun selectSubtitleTrack(id: Int?)

    /** Adds an external subtitle file ([uri]) and shows it. */
    fun addSubtitle(uri: String)

    /** Stops playback and forgets the media; the engine can open another file afterwards. */
    fun stop()

    /** Frees the engine. It cannot be used again. */
    fun release()
}

/** A file to play. [uri] is a `file:` URI on desktop and a `file:` or `content:` URI on Android. */
data class MediaRef(val uri: String, val title: String) {
    /** An http(s) stream link (handed over by ks-streamflix, for example), not a file on the device. */
    val isStream: Boolean get() = uri.startsWith("http://", ignoreCase = true) || uri.startsWith("https://", ignoreCase = true)

    /** For a stream: the server's host name, shown where a file shows its folder. */
    val streamHost: String get() = uri.substringAfter("://").substringBefore('/').substringBefore('?').substringAfterLast('@')
}
