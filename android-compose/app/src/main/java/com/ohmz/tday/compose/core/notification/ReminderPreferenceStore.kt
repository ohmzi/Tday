package com.ohmz.tday.compose.core.notification

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReminderPreferenceStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun getDefaultReminder(): ReminderOption =
        ReminderOption.fromName(
            prefs.getString(KEY_DEFAULT_REMINDER, null) ?: ReminderOption.DEFAULT.name,
        )

    fun setDefaultReminder(option: ReminderOption) {
        prefs.edit().putString(KEY_DEFAULT_REMINDER, option.name).apply()
    }

    fun wasNotified(alarmKey: String): Boolean =
        prefs.getStringSet(KEY_NOTIFIED_SET, emptySet())?.contains(alarmKey) == true

    fun markNotified(alarmKey: String) {
        val current = prefs.getStringSet(KEY_NOTIFIED_SET, emptySet()).orEmpty().toMutableSet()
        current.add(alarmKey)
        prefs.edit().putStringSet(KEY_NOTIFIED_SET, current).apply()
    }

    fun clearNotifiedSet() {
        prefs.edit().remove(KEY_NOTIFIED_SET).apply()
    }

    internal fun getScheduledRequestCodes(): Set<Int> {
        return prefs.getStringSet(KEY_SCHEDULED_CODES, emptySet())
            .orEmpty()
            .mapNotNull { it.toIntOrNull() }
            .toSet()
    }

    internal fun saveScheduledRequestCodes(codes: Set<Int>) {
        prefs.edit()
            .putStringSet(KEY_SCHEDULED_CODES, codes.map { it.toString() }.toSet())
            .apply()
    }

    private companion object {
        const val PREF_NAME = "tday_reminder_prefs"
        const val KEY_DEFAULT_REMINDER = "default_reminder"
        const val KEY_NOTIFIED_SET = "notified_set"
        const val KEY_SCHEDULED_CODES = "scheduled_request_codes"
    }
}
