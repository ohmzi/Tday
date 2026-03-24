package com.ohmz.tday.compose.core.notification

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import com.ohmz.tday.compose.MainActivity
import com.ohmz.tday.compose.R
import dagger.hilt.android.EntryPointAccessors

class TaskReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: return
        val title = intent.getStringExtra(EXTRA_TASK_TITLE) ?: "Task reminder"
        val dueMillis = intent.getLongExtra(EXTRA_TASK_DUE_MILLIS, -1L)
        val priority = intent.getStringExtra(EXTRA_TASK_PRIORITY) ?: "Low"
        val instanceDateMillis = intent.getLongExtra(EXTRA_INSTANCE_DATE_MILLIS, -1L)

        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            ReminderReceiverEntryPoint::class.java,
        )
        val preferenceStore = entryPoint.reminderPreferenceStore()

        val alarmKey = TaskReminderScheduler.alarmKeyFor(taskId, instanceDateMillis)
        if (preferenceStore.wasNotified(alarmKey)) return
        preferenceStore.markNotified(alarmKey)

        val body = formatDueBody(dueMillis)
        val notificationPriority = mapPriority(priority)

        val deepLinkIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            data = Uri.parse("tday://todos/all?highlightTodoId=${Uri.encode(taskId)}")
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            taskId.hashCode(),
            deepLinkIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(notificationPriority)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(taskId.hashCode(), notification)
    }

    private fun formatDueBody(dueMillis: Long): String {
        if (dueMillis <= 0) return "Task is due"

        val now = System.currentTimeMillis()
        val diffMs = dueMillis - now

        return when {
            diffMs <= 0 -> "Due now"
            diffMs < 60_000 -> "Due in less than a minute"
            diffMs < 3_600_000 -> {
                val minutes = diffMs / 60_000
                "Due in $minutes minute${if (minutes != 1L) "s" else ""}"
            }
            diffMs < 86_400_000 -> {
                val hours = diffMs / 3_600_000
                "Due in $hours hour${if (hours != 1L) "s" else ""}"
            }
            else -> {
                val days = diffMs / 86_400_000
                "Due in $days day${if (days != 1L) "s" else ""}"
            }
        }
    }

    private fun mapPriority(priority: String): Int = when (priority.lowercase()) {
        "high" -> NotificationCompat.PRIORITY_HIGH
        "medium" -> NotificationCompat.PRIORITY_DEFAULT
        else -> NotificationCompat.PRIORITY_LOW
    }

    companion object {
        const val CHANNEL_ID = "task_reminders"
        const val EXTRA_TASK_ID = "extra_task_id"
        const val EXTRA_TASK_TITLE = "extra_task_title"
        const val EXTRA_TASK_DUE_MILLIS = "extra_task_due_millis"
        const val EXTRA_TASK_PRIORITY = "extra_task_priority"
        const val EXTRA_INSTANCE_DATE_MILLIS = "extra_instance_date_millis"
    }
}
