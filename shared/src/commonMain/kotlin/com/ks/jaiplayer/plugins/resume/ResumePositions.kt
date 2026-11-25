package com.ks.jaiplayer.plugins.resume

import com.ks.jaiplayer.settings.SettingsStore

/** Where a file was left: its position and length, when that was, and the title to show. */
data class ResumeEntry(
    val uri: String,
    val title: String,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAtMs: Long,
)

/**
 * The positions of unfinished files, most recently watched first, kept in the app's settings.
 * Short clips, files barely started and files watched to (nearly) the end are not kept.
 */
class ResumePositions(private val store: SettingsStore, private val max: Int = MAX_ENTRIES) {

    fun load(): List<ResumeEntry> {
        val count = store.getString(COUNT_KEY)?.toIntOrNull()?.coerceIn(0, max) ?: 0
        return (0 until count).mapNotNull { i ->
            val uri = store.getString("$PREFIX$i.uri") ?: return@mapNotNull null
            val fields = store.getString("$PREFIX$i.at")?.split(',')?.mapNotNull { it.toLongOrNull() }
            if (fields == null || fields.size != 3) return@mapNotNull null
            ResumeEntry(uri, store.getString("$PREFIX$i.title") ?: uri, fields[0], fields[1], fields[2])
        }
    }

    fun get(uri: String): ResumeEntry? = load().firstOrNull { it.uri == uri }

    /** Keeps where [uri] is now, or forgets it when there is nothing worth resuming. */
    fun record(uri: String, title: String, positionMs: Long, durationMs: Long, nowMs: Long): List<ResumeEntry> {
        val others = load().filterNot { it.uri == uri }
        val list = if (worthResuming(positionMs, durationMs)) {
            (listOf(ResumeEntry(uri, title, positionMs, durationMs, nowMs)) + others).take(max)
        } else {
            others
        }
        return save(list)
    }

    fun remove(uri: String): List<ResumeEntry> = save(load().filterNot { it.uri == uri })

    private fun save(list: List<ResumeEntry>): List<ResumeEntry> {
        val old = store.getString(COUNT_KEY)?.toIntOrNull() ?: 0
        list.forEachIndexed { i, e ->
            store.putString("$PREFIX$i.uri", e.uri)
            store.putString("$PREFIX$i.title", e.title)
            store.putString("$PREFIX$i.at", "${e.positionMs},${e.durationMs},${e.updatedAtMs}")
        }
        for (i in list.size until old) {
            store.putString("$PREFIX$i.uri", null)
            store.putString("$PREFIX$i.title", null)
            store.putString("$PREFIX$i.at", null)
        }
        store.putString(COUNT_KEY, list.size.toString())
        return list
    }

    companion object {
        const val MAX_ENTRIES = 50
        private const val PREFIX = "pos."
        private const val COUNT_KEY = "pos.count"

        /** Files shorter than this play from the start every time. */
        const val MIN_LENGTH_MS = 3 * 60_000L

        /** A file left before this point starts again from the beginning. */
        const val MIN_POSITION_MS = 15_000L

        /**
         * Whether a file left at [positionMs] of [durationMs] should resume there: long enough,
         * started, and not in its last 5 % (at least the last minute, the credits).
         */
        fun worthResuming(positionMs: Long, durationMs: Long): Boolean {
            if (durationMs < MIN_LENGTH_MS || positionMs < MIN_POSITION_MS) return false
            val end = maxOf(60_000L, durationMs / 20)
            return positionMs < durationMs - end
        }
    }
}
