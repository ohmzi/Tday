package com.ohmz.tday.compose.core.notification

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ohmz.tday.compose.core.model.TodoListMode
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/**
 * Handles the actions on a task-reminder notification: "Snooze 1h" re-arms
 * the same reminder an hour from now; "Tonight" moves the task's due to
 * today 19:00 local through the normal repository path (queued offline,
 * synced in Server Mode).
 */
class SnoozeActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra(TaskReminderReceiver.EXTRA_TASK_ID) ?: return
        val instanceDateMillis =
            intent.getLongExtra(TaskReminderReceiver.EXTRA_INSTANCE_DATE_MILLIS, -1L)
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            ReminderReceiverEntryPoint::class.java,
        )

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(taskId.hashCode())

        when (intent.action) {
            ACTION_SNOOZE_HOUR -> {
                entryPoint.taskReminderScheduler().snooze(
                    taskId = taskId,
                    title = intent.getStringExtra(TaskReminderReceiver.EXTRA_TASK_TITLE),
                    dueMillis = intent.getLongExtra(TaskReminderReceiver.EXTRA_TASK_DUE_MILLIS, -1L),
                    priority = intent.getStringExtra(TaskReminderReceiver.EXTRA_TASK_PRIORITY),
                    instanceDateMillis = instanceDateMillis,
                    delayMillis = SNOOZE_HOUR_MILLIS,
                )
            }

            ACTION_MOVE_TONIGHT -> {
                // Recurring instances reschedule via the app's per-instance flow.
                if (instanceDateMillis > 0) return
                entryPoint.reminderPreferenceStore()
                    .clearNotified(TaskReminderScheduler.alarmKeyFor(taskId, instanceDateMillis))
                val repository = entryPoint.todoRepository()
                val scheduler = entryPoint.taskReminderScheduler()
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val todo = runCatching { repository.fetchTodosSnapshot(TodoListMode.ALL) }
                            .getOrElse { emptyList() }
                            .firstOrNull { it.id == taskId && it.instanceDate == null }
                        if (todo != null && todo.rrule.isNullOrBlank()) {
                            val zone = ZoneId.systemDefault()
                            val tonight = LocalDate.now(zone).atTime(19, 0).atZone(zone).toInstant()
                            runCatching { repository.moveTodo(todo, tonight) }
                            runCatching { scheduler.rescheduleAll() }
                        }
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        }
    }

    companion object {
        const val ACTION_SNOOZE_HOUR = "com.ohmz.tday.compose.action.REMINDER_SNOOZE_HOUR"
        const val ACTION_MOVE_TONIGHT = "com.ohmz.tday.compose.action.REMINDER_MOVE_TONIGHT"
        const val SNOOZE_HOUR_MILLIS = 60L * 60L * 1000L
    }
}
