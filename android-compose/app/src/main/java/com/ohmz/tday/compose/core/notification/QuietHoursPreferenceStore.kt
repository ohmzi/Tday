package com.ohmz.tday.compose.core.notification

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Local "hold reminders between HH:MM and HH:MM" setting. Times are minute-of-day
 * (0..1439). A reminder that would fire inside the window is re-armed for the window
 * end by [TaskReminderReceiver]. Off by default.
 */
@Singleton
class QuietHoursPreferenceStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun isEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, false)

    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun getStartMinute(): Int = prefs.getInt(KEY_START, DEFAULT_START)

    fun getEndMinute(): Int = prefs.getInt(KEY_END, DEFAULT_END)

    fun setWindow(startMinute: Int, endMinute: Int) {
        prefs.edit().putInt(KEY_START, startMinute).putInt(KEY_END, endMinute).apply()
    }

    private companion object {
        const val PREF_NAME = "quiet_hours_prefs"
        const val KEY_ENABLED = "enabled"
        const val KEY_START = "start_minute"
        const val KEY_END = "end_minute"
        const val DEFAULT_START = 22 * 60 // 22:00
        const val DEFAULT_END = 7 * 60 // 07:00
    }
}
