package com.ks.jaiplayer

import com.ks.jaiplayer.plugins.resume.ResumePositions
import com.ks.jaiplayer.plugins.resume.ResumePositions.Companion.worthResuming
import com.ks.jaiplayer.settings.MemorySettingsStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ResumeTest {
    private val hour = 60 * 60_000L

    @Test fun onlyLongStartedUnfinishedFilesResume() {
        assertTrue(worthResuming(20 * 60_000L, hour))
        assertFalse(worthResuming(10_000, hour), "barely started")
        assertFalse(worthResuming(hour - 60_000, hour), "in the credits")
        assertFalse(worthResuming(60_000, 2 * 60_000L), "a short clip")
        assertTrue(worthResuming(hour - 4 * 60_000, hour), "before the last 5 %")
    }

    @Test fun recordsNewestFirstAndForgetsFinishedFiles() {
        val p = ResumePositions(MemorySettingsStore())
        p.record("a", "A", 20 * 60_000L, hour, nowMs = 1)
        p.record("b", "B", 30 * 60_000L, hour, nowMs = 2)
        assertEquals(listOf("b", "a"), p.load().map { it.uri })
        assertEquals(20 * 60_000L, p.get("a")?.positionMs)

        p.record("a", "A", 25 * 60_000L, hour, nowMs = 3)
        assertEquals(listOf("a", "b"), p.load().map { it.uri })

        p.record("a", "A", hour, hour, nowMs = 4) // watched to the end
        assertNull(p.get("a"))
        assertEquals(listOf("b"), p.load().map { it.uri })
    }

    @Test fun keepsAtMostTheLimit() {
        val p = ResumePositions(MemorySettingsStore(), max = 2)
        listOf("a", "b", "c").forEachIndexed { i, uri -> p.record(uri, uri, 20 * 60_000L, hour, nowMs = i.toLong()) }
        assertEquals(listOf("c", "b"), p.load().map { it.uri })
    }
}

class SeekHoldTest {
    @Test fun holdingSeeksFurtherAndAPauseStartsAgain() {
        var t = 0L
        val hold = com.ks.jaiplayer.player.SeekHold { t }
        val steps = (0 until 20).map { t += 50; hold.step(forward = true) }
        assertEquals(10_000L, steps[0])
        assertEquals(10_000L, steps[5])
        assertEquals(30_000L, steps[6])
        assertEquals(60_000L, steps[19])
        t += 1_000
        assertEquals(10_000L, hold.step(forward = true), "a new press")
        t += 50
        assertEquals(10_000L, hold.step(forward = false), "the other direction")
    }
}
