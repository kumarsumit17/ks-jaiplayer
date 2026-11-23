package com.ks.jaiplayer

import androidx.compose.ui.input.key.Key
import com.ks.jaiplayer.plugins.recent.RecentFiles
import com.ks.jaiplayer.player.MediaRef
import com.ks.jaiplayer.player.PlayerAction
import com.ks.jaiplayer.player.formatPlaybackTime
import com.ks.jaiplayer.settings.LanguageChoice
import com.ks.jaiplayer.settings.MemorySettingsStore
import com.ks.jaiplayer.settings.languageChoice
import com.ks.jaiplayer.settings.resolveLanguage
import com.ks.jaiplayer.settings.setLanguageChoice
import com.ks.jaiplayer.ui.actionForKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TimeFormatTest {
    @Test fun formats() {
        assertEquals("0:00", formatPlaybackTime(0))
        assertEquals("0:05", formatPlaybackTime(5_999))
        assertEquals("12:34", formatPlaybackTime((12 * 60 + 34) * 1000L))
        assertEquals("1:02:03", formatPlaybackTime((3600 + 2 * 60 + 3) * 1000L))
        assertEquals("0:00", formatPlaybackTime(-10))
    }
}

class RecentFilesTest {
    private fun m(n: Int) = MediaRef("file:///v$n.mp4", "v$n.mp4")

    @Test fun newestFirstWithoutDuplicates() {
        val r = RecentFiles(MemorySettingsStore())
        r.add(m(1)); r.add(m(2)); r.add(m(1))
        assertEquals(listOf(m(1), m(2)), r.load())
    }

    @Test fun keepsAtMostTheLimit() {
        val r = RecentFiles(MemorySettingsStore(), max = 3)
        (1..5).forEach { r.add(m(it)) }
        assertEquals(listOf(m(5), m(4), m(3)), r.load())
    }

    @Test fun removeAndClearForgetEntries() {
        val store = MemorySettingsStore()
        val r = RecentFiles(store)
        (1..3).forEach { r.add(m(it)) }
        r.remove(m(2).uri)
        assertEquals(listOf(m(3), m(1)), r.load())
        r.clear()
        assertEquals(emptyList(), r.load())
        assertNull(store.getString("recent.0.uri"))
    }

    @Test fun survivesAReload() {
        val store = MemorySettingsStore()
        RecentFiles(store).add(m(1))
        assertEquals(listOf(m(1)), RecentFiles(store).load())
    }
}

class LanguageTest {
    @Test fun followsTheSystemUntilAChoiceIsMade() {
        assertEquals("hi", resolveLanguage(LanguageChoice.System, listOf("hi-IN", "en-GB")))
        assertEquals("en", resolveLanguage(LanguageChoice.System, listOf("fr-FR", "en-GB", "hi")))
        assertEquals("hi", resolveLanguage(LanguageChoice.System, listOf("fr-FR", "hi_IN")))
        assertEquals("en", resolveLanguage(LanguageChoice.System, listOf("fr-FR")))
        assertEquals("en", resolveLanguage(LanguageChoice.System, emptyList()))
        assertEquals("en", resolveLanguage(LanguageChoice.English, listOf("hi-IN")))
        assertEquals("hi", resolveLanguage(LanguageChoice.Hindi, listOf("en-US")))
    }

    @Test fun rememberedAndUnknownMeansSystem() {
        val store = MemorySettingsStore()
        assertEquals(LanguageChoice.System, store.languageChoice())
        store.setLanguageChoice(LanguageChoice.Hindi)
        assertEquals(LanguageChoice.Hindi, store.languageChoice())
        store.putString("language", "klingon")
        assertEquals(LanguageChoice.System, store.languageChoice())
    }
}

class KeysTest {
    @Test fun desktopKeysAndRemoteDoTheSame() {
        assertEquals(PlayerAction.TogglePlay, actionForKey(Key.Spacebar))
        assertEquals(PlayerAction.TogglePlay, actionForKey(Key.DirectionCenter))
        assertEquals(PlayerAction.SeekBack, actionForKey(Key.DirectionLeft))
        assertEquals(PlayerAction.SeekForwardLong, actionForKey(Key.DirectionRight, shift = true))
        assertEquals(PlayerAction.VolumeUp, actionForKey(Key.DirectionUp))
        assertEquals(PlayerAction.ToggleFullScreen, actionForKey(Key.F))
        assertEquals(PlayerAction.Back, actionForKey(Key.Escape))
        assertEquals(PlayerAction.Back, actionForKey(Key.Back))
        assertNull(actionForKey(Key.Q))
    }

    @Test fun onTvTheShownControlsTakeTheArrows() {
        assertNull(actionForKey(Key.DirectionLeft, tvControlsShown = true))
        assertNull(actionForKey(Key.DirectionCenter, tvControlsShown = true))
        assertEquals(PlayerAction.TogglePlay, actionForKey(Key.MediaPlayPause, tvControlsShown = true))
        assertEquals(PlayerAction.Back, actionForKey(Key.Back, tvControlsShown = true))
    }
}
