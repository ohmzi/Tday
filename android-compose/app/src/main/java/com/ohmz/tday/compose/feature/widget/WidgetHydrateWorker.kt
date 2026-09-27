package com.ohmz.tday.compose.feature.widget

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ohmz.tday.compose.core.data.cache.OfflineCacheManager
import com.ohmz.tday.compose.feature.widget.snapshot.WidgetSnapshotWriter
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * The only widget-flow component allowed to open the encrypted cache — and never on a render
 * path. A widget's `provideGlance` enqueues this when it finds no snapshot on disk (the LOADING
 * state): a fresh install, or an upgrade that rebooted before the app was ever opened for the
 * first time. The Today widget also enqueues it when its snapshot has run out of the days it
 * carries ahead (see `WidgetSnapshot.todayAt`) — a week with the app never opened. Only a
 * fallback: WorkManager can be deferred for hours in the background, so nothing day-to-day may
 * depend on it (that is what the carried days and `WidgetSnapshotWriter.ensureCurrent` are for). Writes
 * every snapshot from the current cache, then repaints — exactly what the app process's own first
 * cache load would have done anyway, just triggered from the widget side instead of waiting for
 * the user to open the app. Deliberately not network-constrained: it only reads the local cache.
 */
@HiltWorker
class WidgetHydrateWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val cacheManager: OfflineCacheManager,
    private val snapshotWriter: WidgetSnapshotWriter,
    private val widgetRefresher: WidgetRefresher,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return runCatching {
            val state = cacheManager.loadOfflineState()
            if (!snapshotWriter.write(state)) {
                // Every write failed (the store already logged why). Repainting now would compose
                // the same missing or earlier-day snapshot, which enqueues this worker again — a
                // loop of encrypted-cache opens. Stop here; the next onUpdate or app open retries.
                Log.w(TAG, "Widget hydrate wrote no snapshot; skipping the repaint")
                return Result.failure()
            }
            widgetRefresher.refreshNow()
            Result.success()
        }.getOrElse { e ->
            // Best-effort: if this fails, the widget simply stays in LOADING until the app is
            // opened (the normal write chokepoints then seed it) or another provideGlance
            // session retries. Not worth WorkManager retry/backoff machinery for that.
            Log.e(TAG, "Widget hydrate failed", e)
            Result.failure()
        }
    }

    companion object {
        private const val TAG = "WidgetHydrateWorker"
        private const val WORK_NAME = "tday_widget_hydrate"

        /**
         * Fire-and-forget from a widget's `provideGlance`. Six widget instances (three sizes x
         * two widgets) can all discover a missing snapshot in the same cold-start window, so this
         * is uniqued with KEEP: whichever request lands first wins and the rest collapse into it
         * instead of five redundant cache opens.
         */
        fun runOnce(context: Context) {
            val request = OneTimeWorkRequestBuilder<WidgetHydrateWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
        }
    }
}
