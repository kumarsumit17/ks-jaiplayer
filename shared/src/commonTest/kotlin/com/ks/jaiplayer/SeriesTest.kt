package com.ks.jaiplayer

import androidx.compose.ui.input.key.Key
import com.ks.jaiplayer.player.MediaRef
import com.ks.jaiplayer.player.PlaybackStatus
import com.ks.jaiplayer.player.Playlist
import com.ks.jaiplayer.player.formatSpeed
import com.ks.jaiplayer.player.naturalNameOrder
import com.ks.jaiplayer.player.nextSpeed
import com.ks.jaiplayer.player.stepSpeed
import com.ks.jaiplayer.player.clampSpeed
import com.ks.jaiplayer.plugin.JaiPlugin
import com.ks.jaiplayer.plugin.PluginContext
import com.ks.jaiplayer.settings.MemorySettingsStore
import com.ks.jaiplayer.ui.FormFactor
import com.ks.jaiplayer.ui.JaiPlayerState
import com.ks.jaiplayer.ui.PlatformHost
import com.ks.jaiplayer.ui.Screen
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun ep(n: Int) = MediaRef("file:///show/e$n.mkv", "Episode $n.mkv")

private object NoHost : PlatformHost {
    override val formFactor = FormFactor.Desktop
    override fun pickMedia(onPicked: (MediaRef) -> Unit) {}
    override fun pickFolder(onPicked: (List<MediaRef>) -> Unit) {}
    override fun pickSubtitle(onPicked: (String) -> Unit) {}
    override fun exists(media: MediaRef) = true
    override fun location(media: MediaRef) = ""
}

class PlaylistTest {
    @Test fun movesThroughTheEpisodes() {
        val p = Playlist(listOf(ep(1), ep(2), ep(3)))
        assertFalse(p.hasPrevious)
        assertEquals(ep(2), p.next()!!.current)
        assertNull(p.jumpTo(2)!!.next())
        assertNull(p.previous())
        assertNull(p.jumpTo(5))
        assertFailsWith<IllegalArgumentException> { Playlist(emptyList()) }
    }

    @Test fun naturalOrderSortsEpisodesByNumber() {
        val names = listOf("Episode 10.mkv", "Episode 2.mkv", "episode 1.mkv", "S01E10.mp4", "S01E09.mp4", "Season 10/E1", "Season 2/E1")
        assertEquals(
            listOf("episode 1.mkv", "Episode 2.mkv", "Episode 10.mkv", "S01E09.mp4", "S01E10.mp4", "Season 2/E1", "Season 10/E1"),
            names.sortedWith(naturalNameOrder),
        )
    }

    @Test fun speeds() {
        assertEquals(1.25f, nextSpeed(1f, faster = true))
        assertEquals(0.75f, nextSpeed(1f, faster = false))
        assertEquals(2.5f, nextSpeed(2f, faster = true))
        assertEquals(4f, nextSpeed(4f, faster = true))
        assertEquals(0.25f, nextSpeed(0.25f, faster = false))
        assertEquals(1.05f, stepSpeed(1f, faster = true))
        assertEquals(1.3f, stepSpeed(1.35f, faster = false))
        assertEquals(4f, stepSpeed(4f, faster = true))
        assertEquals(0.25f, stepSpeed(0.25f, faster = false))
        assertEquals(4f, clampSpeed(9f))
        assertEquals("1.35×", formatSpeed(stepSpeed(1.3f, faster = true)))
        assertEquals(1.25f, nextSpeed(1.1f, faster = true))
        assertEquals("1×", formatSpeed(1f))
        assertEquals("1.25×", formatSpeed(1.25f))
        assertEquals("0.5×", formatSpeed(0.5f))
    }
}

class AppStateTest {
    private fun app(engine: FakeEngine, plugins: List<JaiPlugin> = emptyList()) =
        JaiPlayerState(engine, NoHost, MemorySettingsStore(), listOf("en"), plugins)

    @Test fun aSeriesPlaysEpisodeAfterEpisode() {
        val engine = FakeEngine()
        val app = app(engine)
        app.openAll(listOf(ep(1), ep(2)))
        assertEquals(Screen.Player, app.screen)
        assertEquals(ep(1), engine.state.value.media)
        app.onEnded()
        assertEquals(ep(2), engine.state.value.media)
        app.onEnded() // last episode: stays on it
        assertEquals(ep(2), engine.state.value.media)
        app.previous()
        assertEquals(ep(1), engine.state.value.media)
        app.closePlayer()
        assertNull(app.playlist)
        assertEquals(PlaybackStatus.Idle, engine.state.value.status)
    }

    @Test fun pluginsAreInstalledAndHearOpens() {
        val opened = mutableListOf<MediaRef>()
        var keyPressed = false
        val plugin = object : JaiPlugin {
            override val id = "probe"
            override fun install(context: PluginContext) {
                context.onMediaOpened { opened += it }
                context.addKeyBinding(Key.K) { keyPressed = true }
                context.settings.putString("x", "1")
            }
        }
        val app = app(FakeEngine(), listOf(plugin))
        app.openAll(listOf(ep(1), ep(2)))
        app.next()
        assertEquals(listOf(ep(1), ep(2)), opened)
        app.plugins.keyAction(Key.K)!!.invoke()
        assertTrue(keyPressed)
    }

    @Test fun pluginIdsMustBeUnique() {
        val p = object : JaiPlugin { override val id = "same"; override fun install(context: PluginContext) {} }
        assertFailsWith<IllegalArgumentException> { app(FakeEngine(), listOf(p, p)) }
    }
}
