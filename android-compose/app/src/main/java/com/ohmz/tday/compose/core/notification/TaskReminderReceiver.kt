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
        val title = intent.getStringExtra(EXTRA_TASK_TITLE)
            ?: context.getString(R.string.reminder_notification_default_title)
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

        val body = formatDueBody(context, dueMillis)
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

    private fun formatDueBody(context: Context, dueMillis: Long): String {
        if (dueMillis <= 0) return context.getString(R.string.reminder_due_unknown)

        val now = System.currentTimeMillis()
        val diffMs = dueMillis - now

        return when {
            diffMs <= 0 -> context.getString(R.string.reminder_due_now)
            diffMs < 60_000 -> context.getString(R.string.reminder_due_less_than_minute)
            diffMs < 3_600_000 -> {
                val minutes = diffMs / 60_000
                val resId = if (minutes == 1L) {
                    R.string.reminder_due_in_minutes_one
                } else {
                    R.string.reminder_due_in_minutes_other
                }
                context.getString(resId, minutes)
            }
            diffMs < 86_400_000 -> {
                val hours = diffMs / 3_600_000
                val resId = if (hours == 1L) {
                    R.string.reminder_due_in_hours_one
                } else {
                    R.string.reminder_due_in_hours_other
                }
                context.getString(resId, hours)
            }
            else -> {
                val days = diffMs / 86_400_000
                val resId = if (days == 1L) {
                    R.string.reminder_due_in_days_one
                } else {
                    R.string.reminder_due_in_days_other
                }
                context.getString(resId, days)
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
