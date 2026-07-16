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
import com.ohmz.tday.compose.core.data.sync.SyncManager
import com.ohmz.tday.compose.feature.widget.WidgetSyncWorker.Companion.schedule
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Periodic WorkManager worker that syncs with the server every ~30 minutes so both widgets
 * stay fresh even when the app process has been killed.
 *
 * It runs a full [SyncManager.syncCachedData] (network), then writes through the single cache
 * path. The reload itself is CONDITIONAL: the refreshers only re-render when the widget's
 * displayed content actually changed (see [TodayTasksWidgetRefresher]). So a sync that finds
 * nothing new for the widget leaves it untouched while the app still holds the latest data.
 * It runs quietly — no offline toast — because it's a background refresh, not user-initiated.
 *
 * Enqueue once at app start via [schedule] from [TdayApplication.runDeferredStartup].
 */
@HiltWorker
class WidgetSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val syncManager: SyncManager,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return runCatching {
            // force = true so the 30-min cadence actually reaches the server (bypasses the
            // time-based throttle). notifyOfflineFailure = false so a background run is silent.
            syncManager.syncCachedData(
                force = true,
                replayPendingMutations = true,
                notifyOfflineFailure = false,
            )
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
         * Uses UPDATE so an app update picks up the current 30-min network-sync definition
         * (an older install may still have the previous 15-min cache-only worker enqueued).
         */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<WidgetSyncWorker>(
                repeatInterval = 30,
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
                    ExistingPeriodicWorkPolicy.UPDATE,
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
