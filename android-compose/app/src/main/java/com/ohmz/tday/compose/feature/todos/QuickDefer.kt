package com.ohmz.tday.compose.feature.todos

import androidx.annotation.StringRes
import com.ohmz.tday.compose.R
import java.time.DayOfWeek
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

/**
 * The Quick Defer instants, computed locally (same semantics as the web and
 * iOS helpers): +3h, today 19:00, tomorrow 09:00, next Monday 09:00.
 * "Tonight" hides once the evening is effectively here (18:30) so it can
 * never produce an instant in the past.
 */
enum class QuickDeferChoice(@StringRes val labelRes: Int) {
    LATER_TODAY(R.string.quick_defer_later_today),
    TONIGHT(R.string.quick_defer_tonight),
    TOMORROW(R.string.quick_defer_tomorrow),
    NEXT_WEEK(R.string.quick_defer_next_week),
}

data class QuickDeferOption(val choice: QuickDeferChoice, val dueEpochMs: Long)

fun quickDeferOptions(now: ZonedDateTime = ZonedDateTime.now()): List<QuickDeferOption> {
    val startOfDay = now.toLocalDate().atStartOfDay(now.zone)
    val options = mutableListOf(
        QuickDeferOption(
            QuickDeferChoice.LATER_TODAY,
            now.plusHours(3).withSecond(0).withNano(0).toInstant().toEpochMilli(),
        ),
    )
    val eveningCutoff = startOfDay.withHour(18).withMinute(30)
    if (now.isBefore(eveningCutoff)) {
        options += QuickDeferOption(
            QuickDeferChoice.TONIGHT,
            startOfDay.withHour(19).toInstant().toEpochMilli(),
        )
    }
    options += QuickDeferOption(
        QuickDeferChoice.TOMORROW,
        startOfDay.plusDays(1).withHour(9).toInstant().toEpochMilli(),
    )
    options += QuickDeferOption(
        QuickDeferChoice.NEXT_WEEK,
        startOfDay.with(TemporalAdjusters.next(DayOfWeek.MONDAY)).withHour(9)
            .toInstant().toEpochMilli(),
    )
    return options
}
