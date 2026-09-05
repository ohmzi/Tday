package com.ohmz.tday.compose.feature.widget

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.NetworkType
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
 * displayed content actually changed (see [WidgetRefresher]). So a sync that finds
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
        // force = true so the 30-min cadence actually reaches the server (bypasses the
        // time-based throttle). notifyOfflineFailure = false so a background run is silent.
        // syncCachedData catches its own exceptions and encodes them in the returned Result, so
        // this only needs to guard the (very unlikely) case where something outside that —
        // e.g. the local-mode branch's own cache write — throws directly.
        val outcome = runCatching {
            syncManager.syncCachedData(
                force = true,
                replayPendingMutations = true,
                notifyOfflineFailure = false,
            )
        }.getOrElse { kotlin.Result.failure(it) }
        outcome.exceptionOrNull()?.let { Log.e(TAG, "Widget sync failed (attempt $runAttemptCount)", it) }
        return mapWidgetSyncOutcome(outcome, runAttemptCount)
    }

    companion object {
        private const val TAG = "WidgetSyncWorker"
        private const val WORK_NAME = "tday_widget_periodic_sync"
        private const val MAX_SYNC_ATTEMPTS = 3

        private val networkConstraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        /**
         * Maps the real sync outcome to a WorkManager [ListenableWorker.Result]. A pure function
         * (no logging, no side effects — see [doWork] for that) split out of [doWork] so the
         * retry/failure decision is unit-testable without a Worker, a Context, or mocking
         * [android.util.Log].
         *
         * Before this, `doWork` called [SyncManager.syncCachedData] and discarded its returned
         * [kotlin.Result] — [SyncManager.syncCachedData] never throws (it wraps its own work in
         * `runCatching` and returns the failure), so `doWork`'s own `runCatching` never saw
         * anything to catch, and this worker returned [ListenableWorker.Result.success]
         * unconditionally regardless of whether the sync actually reached the server. Retries only
         * ever engage in server mode: local mode returns [kotlin.Result.success] unconditionally
         * before attempting any network I/O, so don't "fix" this into retrying there.
         */
        internal fun mapWidgetSyncOutcome(
            outcome: kotlin.Result<Unit>,
            runAttemptCount: Int,
            maxAttempts: Int = MAX_SYNC_ATTEMPTS,
        ): ListenableWorker.Result {
            if (outcome.isSuccess) return ListenableWorker.Result.success()
            return if (runAttemptCount < maxAttempts) {
                ListenableWorker.Result.retry()
            } else {
                ListenableWorker.Result.failure()
            }
        }

        /**
         * Call from [TdayApplication.runDeferredStartup] (default [ExistingPeriodicWorkPolicy.UPDATE],
         * so an app update picks up the current 30-min network-sync definition — an older install
         * may still have the previous 15-min cache-only worker enqueued) and from
         * `BootRescheduleReceiver` (passing [ExistingPeriodicWorkPolicy.KEEP], since a boot is not
         * an app update and re-enqueueing with UPDATE on every boot would reset the periodic
         * window each time).
         */
        fun schedule(
            context: Context,
            existingPolicy: ExistingPeriodicWorkPolicy = ExistingPeriodicWorkPolicy.UPDATE,
        ) {
            val request = PeriodicWorkRequestBuilder<WidgetSyncWorker>(
                repeatInterval = 30,
                repeatIntervalTimeUnit = TimeUnit.MINUTES,
                flexTimeInterval = 5,
                flexTimeIntervalUnit = TimeUnit.MINUTES,
            )
                .setConstraints(networkConstraints)
                .setBackoffCriteria(
                    BackoffPolicy.LINEAR,
                    androidx.work.WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS,
                )
                .build()

            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    WORK_NAME,
                    existingPolicy,
                    request,
                )
        }

        /**
         * Immediate one-shot refresh via WorkManager. Use as a fallback alongside direct Glance
         * calls so the widget still updates if the process is killed before Glance finishes.
         *
         * Deliberately NOT network-constrained, unlike [schedule]: [SyncManager.syncCachedData] in
         * local mode does a cache write plus a widget refresh and returns success before any
         * network I/O, and `WidgetCompleteTaskSubmitter` uses this as the process-death fallback
         * for a widget check-off. A `NetworkType.CONNECTED` constraint here would mean that
         * fallback never fires for a local-mode or offline-by-choice user.
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
