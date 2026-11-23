package com.ks.jaiplayer.settings

/**
 * The app's saved settings, as plain strings. Desktop keeps them in `java.util.prefs`, Android in
 * SharedPreferences. Values are small (one value per key), so lists are stored one key per item.
 */
interface SettingsStore {
    fun getString(key: String): String?
    fun putString(key: String, value: String?)
}

/** A store in memory only, for tests and previews. */
class MemorySettingsStore : SettingsStore {
    private val values = mutableMapOf<String, String>()
    override fun getString(key: String): String? = values[key]
    override fun putString(key: String, value: String?) {
        if (value == null) values.remove(key) else values[key] = value
    }
}
