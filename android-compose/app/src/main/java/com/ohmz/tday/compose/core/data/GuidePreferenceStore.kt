package com.ohmz.tday.compose.core.data

import android.content.Context

/**
 * Persists which release the user last opened the How-To guide in, so NEW
 * badges clear once viewed instead of lingering for the whole release.
 * Plain (unencrypted) prefs: a version string is not sensitive.
 */
class GuidePreferenceStore(context: Context) {

    private val preferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun lastSeenGuideVersion(): String? =
        preferences.getString(KEY_LAST_SEEN_VERSION, null)

    fun setLastSeenGuideVersion(version: String) {
        preferences.edit().putString(KEY_LAST_SEEN_VERSION, version).apply()
    }

    private companion object {
        const val PREF_NAME = "tday_guide_preferences"
        const val KEY_LAST_SEEN_VERSION = "last_seen_guide_version"
    }
}
