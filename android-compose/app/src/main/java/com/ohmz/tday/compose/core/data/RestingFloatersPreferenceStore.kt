package com.ohmz.tday.compose.core.data

import android.content.Context

/**
 * On/off toggle for the "resting floaters" display cue (dim Anytime tasks untouched for
 * 30d+). Default on. Plain prefs (not sensitive) — mirrors GuidePreferenceStore.
 */
class RestingFloatersPreferenceStore(context: Context) {
    private val preferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun isEnabled(): Boolean = preferences.getBoolean(KEY_ENABLED, true)

    fun setEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    private companion object {
        const val PREF_NAME = "resting_floaters_prefs"
        const val KEY_ENABLED = "enabled"
    }
}
