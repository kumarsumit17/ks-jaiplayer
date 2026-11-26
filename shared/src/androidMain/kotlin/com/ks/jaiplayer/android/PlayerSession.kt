package com.ks.jaiplayer.android

import android.content.Context
import androidx.media3.common.ForwardingSimpleBasePlayer
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * The media session of both Android apps: Bluetooth and headset buttons, the TV remote's media
 * keys and the Google Assistant ("pause", "skip forward"), the play/pause button in
 * picture-in-picture and the TV's now-playing card reach the player through it.
 *
 * ExoPlayer holds one item; the series playlist is the app's, so "next" and "previous" go to
 * [onNext] and [onPrevious], offered only while [hasNext] / [hasPrevious].
 */
class PlayerSession(
    context: Context,
    engine: ExoPlayerEngine,
    private val hasNext: () -> Boolean,
    private val hasPrevious: () -> Boolean,
    private val onNext: () -> Unit,
    private val onPrevious: () -> Unit,
) {
    private val player = SeriesPlayer(engine.player)
    private val session = MediaSession.Builder(context, player).setId("jaiplayer").build()

    /** The Media3 session, also hosted by [PlaybackService] while audio plays in the background. */
    val mediaSession: MediaSession get() = session

    /** Call when the playlist changes, so the session offers next/previous correctly. */
    fun playlistChanged() = player.refresh()

    fun release() {
        session.release()
    }

    private inner class SeriesPlayer(player: Player) : ForwardingSimpleBasePlayer(player) {
        fun refresh() = invalidateState()

        override fun getState(): State {
            val state = super.getState()
            val commands = state.availableCommands.buildUpon()
                .removeAll(Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                .removeAll(Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            if (hasNext()) commands.addAll(Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            if (hasPrevious()) commands.addAll(Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            return state.buildUpon().setAvailableCommands(commands.build()).build()
        }

        override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> = when (seekCommand) {
            Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> done { onNext() }
            Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> done { onPrevious() }
            else -> super.handleSeek(mediaItemIndex, positionMs, seekCommand)
        }

        private fun done(action: () -> Unit): ListenableFuture<*> {
            action()
            return Futures.immediateVoidFuture()
        }
    }
}
