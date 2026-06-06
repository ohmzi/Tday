package com.ohmz.tday.compose.core.notification

import androidx.annotation.StringRes
import com.ohmz.tday.compose.R

enum class ReminderOption(
    val offsetMillis: Long,
    @StringRes val labelRes: Int,
) {
    NONE(0L, R.string.reminder_option_none),
    AT_TIME(0L, R.string.reminder_option_at_time),
    MINUTES_5(5 * 60_000L, R.string.reminder_option_minutes_5),
    MINUTES_10(10 * 60_000L, R.string.reminder_option_minutes_10),
    MINUTES_15(15 * 60_000L, R.string.reminder_option_minutes_15),
    MINUTES_30(30 * 60_000L, R.string.reminder_option_minutes_30),
    HOURS_1(60 * 60_000L, R.string.reminder_option_hours_1),
    HOURS_2(2 * 60 * 60_000L, R.string.reminder_option_hours_2),
    DAYS_1(24 * 60 * 60_000L, R.string.reminder_option_days_1),
    DAYS_2(2 * 24 * 60 * 60_000L, R.string.reminder_option_days_2),
    ;

    val isEnabled: Boolean get() = this != NONE

    companion object {
        val DEFAULT = MINUTES_10

        fun fromName(name: String): ReminderOption =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
