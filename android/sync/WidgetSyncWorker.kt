package com.ohmz.tday.widget

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Periodic WorkManager worker that refreshes widgets on a schedule.
 *
 * Schedule:
 *  • Repeats every 15 minutes (Android's minimum for PeriodicWorkRequest).
 *  • Requires network only when in Server Mode (use Constraints if needed).
 *  • Survives reboots via KEEP policy after the first schedule.
 *
 * Enqueue once at app start from your Application class or an initialiser:
 *
 *     WidgetSyncWorker.schedule(context)
 */
@HiltWorker
class WidgetSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val widgetUpdateManager: WidgetUpdateManager,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return runCatching {
            widgetUpdateManager.scheduleImmediateUpdate()
            Result.success()
        }.getOrElse {
            // Retry up to 3 times before giving up (won't stall periodic)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val WORK_NAME = "tday_widget_periodic_sync"

        /**
         * Call from Application.onCreate() or a Hilt initialiser.
         * Uses ExistingPeriodicWorkPolicy.KEEP so re-installs after reboot
         * without resetting the 15-minute timer.
         */
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                // Remove NETWORK if you want widget to refresh in local mode too
                // .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<WidgetSyncWorker>(
                repeatInterval = 15,
                repeatIntervalTimeUnit = TimeUnit.MINUTES,
                // Flex window: run any time in the last 5 min of the interval
                flexTimeInterval = 5,
                flexTimeIntervalUnit = TimeUnit.MINUTES,
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.LINEAR,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS,
                )
                .build()

            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request,
                )
        }

        /**
         * Call this when the user adds/edits/deletes a task so the widget
         * updates immediately rather than waiting for the next 15-min tick.
         *
         * This runs as a OneTimeWorkRequest and completes fast.
         */
        fun runOnce(context: Context) {
            val request = OneTimeWorkRequestBuilder<WidgetSyncWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    "${WORK_NAME}_immediate",
                    ExistingWorkPolicy.REPLACE,  // cancel any pending immediate run
                    request,
                )
        }
    }
}
