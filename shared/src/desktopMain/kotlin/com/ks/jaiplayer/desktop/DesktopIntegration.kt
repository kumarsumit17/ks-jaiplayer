package com.ks.jaiplayer.desktop

import com.ks.jaiplayer.player.PlayerAction

/** What the desktop OS is told about playback (the system's Now Playing, media keys, sleep). */
data class NowPlaying(
    /** The file or link being played (`file:` URIs go to the Windows jump list). */
    val uri: String?,
    val title: String?,
    val durationMs: Long,
    val positionMs: Long,
    val playing: Boolean,
    val speed: Float,
    val hasNext: Boolean,
    val hasPrevious: Boolean,
)

/**
 * One desktop OS's integrations, given by the entry point (desktop/mac, desktop/windows):
 * keeping the display awake while a video plays, the system's Now Playing and its media keys.
 * Every call comes on the window's thread; an implementation never throws (a missing OS feature
 * simply does nothing).
 */
interface DesktopIntegration {
    /** Called once the [window] is shown; [perform] runs a player action on the window's thread. */
    fun start(window: java.awt.Window, perform: (PlayerAction) -> Unit)

    /** The playback now; [nowPlaying] is null when nothing is open. */
    fun update(nowPlaying: NowPlaying?, hasVideo: Boolean)

    /** Called when the app quits. */
    fun stop()
}
