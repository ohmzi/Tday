package com.ohmz.tday.compose.core.notification

import android.content.Context
import android.util.Log
import com.ohmz.tday.compose.core.observability.TdayTelemetry
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class ReminderRescheduleWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val scheduler: TaskReminderScheduler,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            scheduler.rescheduleAll()
            TdayTelemetry.addBreadcrumb("reminder.reschedule", data = mapOf("source" to "worker"))
            Log.d(LOG_TAG, "Periodic reschedule completed")
            Result.success()
        } catch (e: Exception) {
            TdayTelemetry.capture(e, "reminder.reschedule", data = mapOf("source" to "worker"))
            Log.e(LOG_TAG, "Periodic reschedule failed", e)
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "reminder_reschedule_periodic"
        private const val LOG_TAG = "ReminderRescheduleWork"
    }
}
