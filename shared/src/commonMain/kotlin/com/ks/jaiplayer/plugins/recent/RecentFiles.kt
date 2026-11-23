package com.ks.jaiplayer.plugins.recent

import com.ks.jaiplayer.player.MediaRef
import com.ks.jaiplayer.settings.SettingsStore

/** The recently played files, newest first, kept in the app's settings. */
class RecentFiles(private val store: SettingsStore, private val max: Int = MAX_RECENT) {

    fun load(): List<MediaRef> {
        val count = store.getString(COUNT_KEY)?.toIntOrNull()?.coerceIn(0, max) ?: 0
        return (0 until count).mapNotNull { i ->
            val uri = store.getString("$PREFIX$i.uri") ?: return@mapNotNull null
            MediaRef(uri, store.getString("$PREFIX$i.title") ?: uri)
        }
    }

    /** Puts [media] first; an entry for the same file moves rather than repeats. */
    fun add(media: MediaRef): List<MediaRef> = save(withAdded(load(), media, max))

    fun remove(uri: String): List<MediaRef> = save(load().filterNot { it.uri == uri })

    fun clear(): List<MediaRef> = save(emptyList())

    private fun save(list: List<MediaRef>): List<MediaRef> {
        val old = store.getString(COUNT_KEY)?.toIntOrNull() ?: 0
        list.forEachIndexed { i, m ->
            store.putString("$PREFIX$i.uri", m.uri)
            store.putString("$PREFIX$i.title", m.title)
        }
        for (i in list.size until old) {
            store.putString("$PREFIX$i.uri", null)
            store.putString("$PREFIX$i.title", null)
        }
        store.putString(COUNT_KEY, list.size.toString())
        return list
    }

    companion object {
        const val MAX_RECENT = 20
        private const val PREFIX = "recent."
        private const val COUNT_KEY = "recent.count"

        /** The list after playing [media]: it goes first, without duplicates, at most [max] long. */
        fun withAdded(list: List<MediaRef>, media: MediaRef, max: Int = MAX_RECENT): List<MediaRef> =
            (listOf(media) + list.filterNot { it.uri == media.uri }).take(max)
    }
}
