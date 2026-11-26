package com.ks.jaiplayer.sample

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import com.ks.jaiplayer.player.PlaybackStatus
import com.ks.jaiplayer.plugin.JaiPlugin
import com.ks.jaiplayer.plugin.MenuEntry
import com.ks.jaiplayer.plugin.MenuGroup
import com.ks.jaiplayer.plugin.PluginContext
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

/**
 * A sample plugin: loops the file that is playing. The L key or Playback › Loop turns it on
 * and off; when the file ends, it starts again from the beginning.
 */
class LoopPlugin : JaiPlugin {
    // Must match the jar manifest's JaiPlayer-Plugin-Id.
    override val id = "sample.loop"
    override val name = "Loop (sample plugin)"

    private var on by mutableStateOf(false)

    override fun install(context: PluginContext) {
        context.addKeyBinding(Key.L) { on = !on }
        context.addMenuEntry(
            MenuGroup.Playback,
            MenuEntry(label = { if (on) "Loop: on (L)" else "Loop: off (L)" }, onClick = { on = !on }),
        )
        MainScope().launch {
            context.engine.state.collect { state ->
                if (on && state.status == PlaybackStatus.Ended) {
                    context.engine.seekTo(0)
                    context.engine.play()
                }
            }
        }
    }
}
