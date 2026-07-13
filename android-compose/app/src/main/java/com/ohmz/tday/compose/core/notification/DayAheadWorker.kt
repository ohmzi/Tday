package com.ohmz.tday.compose.core.notification

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ohmz.tday.compose.MainActivity
import com.ohmz.tday.compose.R
import com.ohmz.tday.compose.core.data.todo.TodoRepository
import com.ohmz.tday.compose.core.model.TodoListMode
import com.ohmz.tday.compose.core.observability.TdayTelemetry
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * The Day Ahead digest: one quiet morning notification — "N tasks today,
 * M carried over" — deep-linking into Today, or straight into Morning Sweep
 * when something is carried over (the two features compound). Reads only the
 * local cache; works identically in Local Mode.
 */
@HiltWorker
class DayAheadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val todoRepository: TodoRepository,
    private val preferenceStore: DayAheadPreferenceStore,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val option = preferenceStore.getOption()
        if (option.hour == null) return Result.success()

        val todayCount = runCatching {
            todoRepository.fetchTodosSnapshot(TodoListMode.TODAY).count { !it.completed }
        }.getOrDefault(0)
        val overdueCount = runCatching {
            todoRepository.fetchTodosSnapshot(TodoListMode.OVERDUE).count { !it.completed }
        }.getOrDefault(0)

        if (todayCount > 0 || overdueCount > 0) {
            postDigest(todayCount, overdueCount)
        }
        TdayTelemetry.addBreadcrumb(
            "dayAhead.digest",
            data = mapOf("todayCount" to todayCount, "overdueCount" to overdueCount),
        )

        // Re-arm tomorrow's delivery.
        DayAheadScheduling.scheduleNext(applicationContext, option)
        return Result.success()
    }

    private fun postDigest(todayCount: Int, overdueCount: Int) {
        val context = applicationContext
        val text = if (overdueCount > 0) {
            context.getString(R.string.day_ahead_text_with_overdue, todayCount, overdueCount)
        } else {
            context.getString(R.string.day_ahead_text, todayCount)
        }
        val target = if (overdueCount > 0) "tday://morning-sweep" else "tday://todos/today"
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            data = Uri.parse(target)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, TaskReminderReceiver.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.day_ahead_title))
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private companion object {
        const val NOTIFICATION_ID = 0x0DA1
    }
}
