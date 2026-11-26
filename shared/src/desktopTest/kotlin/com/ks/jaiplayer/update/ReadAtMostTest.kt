package com.ks.jaiplayer.update

import java.io.ByteArrayInputStream
import kotlin.test.Test
import kotlin.test.assertEquals

class ReadAtMostTest {
    @Test fun readsAllWhenShorterAndStopsAtTheLimit() {
        val data = ByteArray(20_000) { (it % 251).toByte() }
        assertEquals(data.toList(), readAtMost(ByteArrayInputStream(data), 50_000).toList())
        assertEquals(data.take(12_345), readAtMost(ByteArrayInputStream(data), 12_345).toList())
        assertEquals(0, readAtMost(ByteArrayInputStream(ByteArray(0)), 10).size)
    }
}
