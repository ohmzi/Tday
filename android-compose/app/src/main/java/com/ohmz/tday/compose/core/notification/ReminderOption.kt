package com.ohmz.tday.compose.core.notification

enum class ReminderOption(
    val offsetMillis: Long,
    val label: String,
) {
    NONE(0L, "None"),
    AT_TIME(0L, "At time of due"),
    MINUTES_5(5 * 60_000L, "5 minutes before"),
    MINUTES_10(10 * 60_000L, "10 minutes before"),
    MINUTES_15(15 * 60_000L, "15 minutes before"),
    MINUTES_30(30 * 60_000L, "30 minutes before"),
    HOURS_1(60 * 60_000L, "1 hour before"),
    HOURS_2(2 * 60 * 60_000L, "2 hours before"),
    DAYS_1(24 * 60 * 60_000L, "1 day before"),
    DAYS_2(2 * 24 * 60 * 60_000L, "2 days before"),
    ;

    val isEnabled: Boolean get() = this != NONE

    companion object {
        val DEFAULT = MINUTES_10

        fun fromName(name: String): ReminderOption =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
