package com.ohmz.tday.compose.feature.widget

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ohmz.tday.compose.feature.widget.WidgetSyncWorker.Companion.schedule
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Periodic WorkManager worker that refreshes both Today and Floater widgets
 * on a 15-minute schedule (Android's minimum for PeriodicWorkRequest).
 *
 * This ensures widgets stay reasonably fresh even when the app process has
 * been killed and SyncManager isn't running. The existing refreshers
 * ([TodayTasksWidgetRefresher] and [FloaterTasksWidgetRefresher]) handle
 * immediate updates on task mutations; this worker is the safety net.
 *
 * Enqueue once at app start via [schedule] from [TdayApplication.runDeferredStartup].
 */
@HiltWorker
class WidgetSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val todayRefresher: TodayTasksWidgetRefresher,
    private val floaterRefresher: FloaterTasksWidgetRefresher,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return runCatching {
            todayRefresher.refreshNow()
            floaterRefresher.refreshNow()
            Result.success()
        }.getOrElse { e ->
            Log.e(TAG, "Widget sync failed (attempt $runAttemptCount)", e)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val TAG = "WidgetSyncWorker"
        private const val WORK_NAME = "tday_widget_periodic_sync"

        /**
         * Call from [TdayApplication.runDeferredStartup].
         * Uses KEEP so re-installs after reboot without resetting the timer.
         */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<WidgetSyncWorker>(
                repeatInterval = 15,
                repeatIntervalTimeUnit = TimeUnit.MINUTES,
                flexTimeInterval = 5,
                flexTimeIntervalUnit = TimeUnit.MINUTES,
            )
                .setBackoffCriteria(
                    BackoffPolicy.LINEAR,
                    androidx.work.WorkRequest.MIN_BACKOFF_MILLIS,
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
         * Immediate one-shot refresh via WorkManager. Use as a fallback
         * alongside direct Glance calls so the widget still updates if
         * the process is killed before Glance finishes.
         */
        fun runOnce(context: Context) {
            val request = OneTimeWorkRequestBuilder<WidgetSyncWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    "${WORK_NAME}_immediate",
                    ExistingWorkPolicy.REPLACE,
                    request,
                )
        }
    }
}
