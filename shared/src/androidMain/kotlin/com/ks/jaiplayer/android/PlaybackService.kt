package com.ks.jaiplayer.android

import android.content.Context
import android.content.Intent
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * The phone app's background audio: while an audio file plays, the media session is hosted here
 * too, so the sound goes on with the screen off or the app in the background, with the system's
 * media notification and lock-screen controls. Media3 makes the service a foreground service
 * (type `mediaPlayback`) while playing and lets it go when paused.
 *
 * The player itself stays the activity's; when the activity is closed for good the session is
 * released and the service stops.
 */
class PlaybackService : MediaSessionService() {

    override fun onCreate() {
        super.onCreate()
        // The media notification shows KS JaiPlayer's own icon, not Media3's default one.
        setMediaNotificationProvider(
            androidx.media3.session.DefaultMediaNotificationProvider.Builder(this).build()
                .apply { setSmallIcon(com.ks.jaiplayer.shared.R.drawable.ic_notification) },
        )
        running = this
        session?.let(::addSession)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Swiped away from recent apps while paused: nothing to keep playing.
        val player = session?.player
        if (player == null || !player.playWhenReady) stopSelf()
    }

    override fun onDestroy() {
        session?.let(::removeSession)
        running = null
        super.onDestroy()
    }

    companion object {
        private var session: MediaSession? = null
        private var running: PlaybackService? = null

        /** Starts hosting [mediaSession] (call while the app is in the foreground). */
        fun attach(context: Context, mediaSession: MediaSession) {
            if (session === mediaSession && running != null) return
            session = mediaSession
            val service = running
            if (service != null) service.addSession(mediaSession)
            else context.startService(Intent(context, PlaybackService::class.java))
        }

        /** Stops background playback: video, playback stopped, the setting turned off, or the app closed. */
        fun detach() {
            val s = session ?: return
            running?.let { it.removeSession(s); it.stopSelf() }
            session = null
        }
    }
}
