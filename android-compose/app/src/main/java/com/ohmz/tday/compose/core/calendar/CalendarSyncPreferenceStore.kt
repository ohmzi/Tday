package com.ohmz.tday.compose.core.calendar

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Opt-in flag for mirroring scheduled tasks into the device calendar, plus the fingerprint of
 * the last mirrored task set.
 *
 * The fingerprint exists to keep [CalendarSyncManager] cheap: it reconciles by wiping and
 * rewriting the T'Day calendar, and it runs on every cache-data change. Without a fingerprint a
 * routine sync that touched a floater would still rewrite every scheduled event, which churns the
 * calendar provider and makes every calendar app re-render.
 */
@Singleton
class CalendarSyncPreferenceStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun isEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, false)

    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun getLastSyncedFingerprint(): String? = prefs.getString(KEY_FINGERPRINT, null)

    fun setLastSyncedFingerprint(fingerprint: String?) {
        prefs.edit().apply {
            if (fingerprint == null) remove(KEY_FINGERPRINT) else putString(KEY_FINGERPRINT, fingerprint)
        }.apply()
    }

    private companion object {
        const val PREF_NAME = "tday_calendar_sync_prefs"
        const val KEY_ENABLED = "calendar_sync_enabled"
        const val KEY_FINGERPRINT = "calendar_sync_fingerprint"
    }
}
