package com.ks.jaiplayer.settings

/** The user's language choice. The suite rule: follow the system, allow a choice, remember it. */
enum class LanguageChoice(val saved: String) {
    System("system"),
    English("en"),
    Hindi("hi");

    companion object {
        /** Anything unknown in the saved setting counts as "Same as the system". */
        fun parse(saved: String?): LanguageChoice = entries.firstOrNull { it.saved == saved } ?: System
    }
}

/** The languages the app has text for. */
val supportedLanguages = listOf("en", "hi")

/**
 * The language to show: the user's choice, else the first of the system's preferred languages
 * ([preferred], as BCP 47 tags such as `hi-IN`) that the app supports, else English.
 */
fun resolveLanguage(choice: LanguageChoice, preferred: List<String>): String = when (choice) {
    LanguageChoice.English -> "en"
    LanguageChoice.Hindi -> "hi"
    LanguageChoice.System -> preferred
        .map { it.substringBefore('-').substringBefore('_').lowercase() }
        .firstOrNull { it in supportedLanguages }
        ?: "en"
}

private const val LANGUAGE_KEY = "language"

fun SettingsStore.languageChoice(): LanguageChoice = LanguageChoice.parse(getString(LANGUAGE_KEY))
fun SettingsStore.setLanguageChoice(choice: LanguageChoice) = putString(LANGUAGE_KEY, choice.saved)
