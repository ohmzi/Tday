package com.ohmz.tday.compose.core.notification

import android.content.Context
import androidx.annotation.StringRes
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.ohmz.tday.compose.R
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The Day Ahead digest setting: one quiet morning notification with today's
 * counts, or off. A single dropdown (off / hour) keeps the preference simple.
 */
enum class DayAheadOption(@StringRes val labelRes: Int, val hour: Int?) {
    OFF(R.string.day_ahead_off, null),
    H7(R.string.day_ahead_h7, 7),
    H8(R.string.day_ahead_h8, 8),
    H9(R.string.day_ahead_h9, 9),
    ;

    companion object {
        fun fromName(name: String?): DayAheadOption =
            entries.firstOrNull { it.name == name } ?: OFF
    }
}

@Singleton
class DayAheadPreferenceStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun getOption(): DayAheadOption =
        DayAheadOption.fromName(prefs.getString(KEY_OPTION, null))

    fun setOption(option: DayAheadOption) {
        prefs.edit().putString(KEY_OPTION, option.name).apply()
    }

    private companion object {
        const val PREF_NAME = "tday_day_ahead_prefs"
        const val KEY_OPTION = "day_ahead_option"
    }
}

/**
 * Arms the next Day Ahead delivery as a one-shot WorkManager job at the next
 * occurrence of the chosen hour; the worker re-arms itself after each run.
 * Fully on-device — nothing leaves the phone.
 */
object DayAheadScheduling {
    const val WORK_NAME = "day_ahead_digest"

    fun scheduleNext(context: Context, option: DayAheadOption) {
        val workManager = WorkManager.getInstance(context)
        val hour = option.hour
        if (hour == null) {
            workManager.cancelUniqueWork(WORK_NAME)
            return
        }
        val zone = ZoneId.systemDefault()
        val now = LocalDateTime.now(zone)
        var next = LocalDate.now(zone).atTime(hour, 0)
        if (!next.isAfter(now)) {
            next = next.plusDays(1)
        }
        val delay = Duration.between(now, next)
        val request = OneTimeWorkRequestBuilder<DayAheadWorker>()
            .setInitialDelay(delay)
            .build()
        workManager.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }
}
