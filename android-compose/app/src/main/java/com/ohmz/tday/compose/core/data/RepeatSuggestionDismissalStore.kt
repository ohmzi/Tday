package com.ohmz.tday.compose.core.data

import android.content.Context

/**
 * Remembers which normalized task titles the user dismissed the "Make this repeat?"
 * suggestion for, so it doesn't keep nagging. Not sensitive (just title keys), so plain
 * prefs — mirrors GuidePreferenceStore. Instantiated at the call site with a Context.
 */
class RepeatSuggestionDismissalStore(context: Context) {
    private val preferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun isDismissed(normalizedTitle: String): Boolean {
        if (normalizedTitle.isBlank()) return false
        return preferences.getStringSet(KEY_DISMISSED, emptySet())
            .orEmpty()
            .contains(normalizedTitle)
    }

    fun markDismissed(normalizedTitle: String) {
        if (normalizedTitle.isBlank()) return
        val current = preferences.getStringSet(KEY_DISMISSED, emptySet()).orEmpty()
        // Cap so the set can't grow without bound.
        val next = (current + normalizedTitle).toList().takeLast(MAX_ENTRIES).toSet()
        preferences.edit().putStringSet(KEY_DISMISSED, next).apply()
    }

    private companion object {
        const val PREF_NAME = "repeat_suggestion_prefs"
        const val KEY_DISMISSED = "dismissed_titles"
        const val MAX_ENTRIES = 200
    }
}
