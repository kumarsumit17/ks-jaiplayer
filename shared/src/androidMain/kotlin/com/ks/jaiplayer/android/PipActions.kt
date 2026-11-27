package com.ks.jaiplayer.android

import android.app.PendingIntent
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Icon
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.ks.jaiplayer.player.PlayerAction
import com.ks.jaiplayer.resources.Res
import com.ks.jaiplayer.resources.common_pause
import com.ks.jaiplayer.resources.player_play
import com.ks.jaiplayer.resources.player_seek_back
import com.ks.jaiplayer.resources.player_seek_forward
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString

/**
 * The buttons in the picture-in-picture window on Android 8 to 12: back 10 seconds, play or
 * pause, forward 10 seconds. From Android 13 the system draws these from the media session
 * ([PlayerSession]), so [actions] is empty there and the two never show twice.
 *
 * A tap reaches [perform] through a broadcast that only this app can receive.
 */
class PipActions(private val context: Context, private val perform: (PlayerAction) -> Unit) {
    private val action = "${context.packageName}.PIP_ACTION"

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            val which = intent.getIntExtra(EXTRA, -1)
            PlayerAction.entries.getOrNull(which)?.let(perform)
        }
    }

    // Read once: the labels follow the app's language when the activity starts.
    private val labels by lazy {
        runBlocking {
            Labels(
                play = getString(Res.string.player_play),
                pause = getString(Res.string.common_pause),
                back = getPluralString(Res.plurals.player_seek_back, 10, "10"),
                forward = getPluralString(Res.plurals.player_seek_forward, 10, "10"),
            )
        }
    }

    fun register() {
        ContextCompat.registerReceiver(context, receiver, IntentFilter(action), ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    fun unregister() {
        runCatching { context.unregisterReceiver(receiver) }
    }

    /** The window's buttons for the current state; empty from Android 13. */
    fun actions(playing: Boolean): List<RemoteAction> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return emptyList()
        val l = labels
        return listOf(
            remote(PlayerAction.SeekBack, com.ks.jaiplayer.shared.R.drawable.ic_pip_back, l.back),
            if (playing) remote(PlayerAction.TogglePlay, com.ks.jaiplayer.shared.R.drawable.ic_pip_pause, l.pause)
            else remote(PlayerAction.TogglePlay, com.ks.jaiplayer.shared.R.drawable.ic_pip_play, l.play),
            remote(PlayerAction.SeekForward, com.ks.jaiplayer.shared.R.drawable.ic_pip_forward, l.forward),
        )
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun remote(what: PlayerAction, icon: Int, label: String): RemoteAction {
        val intent = Intent(action).setPackage(context.packageName).putExtra(EXTRA, what.ordinal)
        val pending = PendingIntent.getBroadcast(
            context, what.ordinal, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return RemoteAction(Icon.createWithResource(context, icon), label, label, pending)
    }

    private class Labels(val play: String, val pause: String, val back: String, val forward: String)

    private companion object {
        const val EXTRA = "action"
    }
}
