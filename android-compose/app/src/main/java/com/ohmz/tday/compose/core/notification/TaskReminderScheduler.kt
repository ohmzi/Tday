package com.ohmz.tday.compose.core.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.ohmz.tday.compose.core.data.todo.TodoRepository
import com.ohmz.tday.compose.core.model.TodoItem
import com.ohmz.tday.compose.core.model.TodoListMode
import com.ohmz.tday.compose.core.observability.TdayTelemetry
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaskReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val alarmManager: AlarmManager,
    private val repository: TodoRepository,
    private val preferenceStore: ReminderPreferenceStore,
) {
    fun rescheduleAll() {
        cancelAllInternal()

        val reminder = preferenceStore.getDefaultReminder()
        if (!reminder.isEnabled) return

        val allTasks = runCatching {
            repository.fetchTodosSnapshot(TodoListMode.ALL)
        }.getOrElse { emptyList() }

        val now = System.currentTimeMillis()
        val scheduledCodes = mutableSetOf<Int>()

        for (task in allTasks) {
            if (task.completed) continue
            val alarmTime = computeAlarmTime(task, reminder) ?: continue
            if (alarmTime <= now) continue

            val requestCode = requestCodeFor(task)
            scheduleAlarm(task, alarmTime, requestCode)
            scheduledCodes.add(requestCode)
        }

        preferenceStore.saveScheduledRequestCodes(scheduledCodes)
        TdayTelemetry.addBreadcrumb(
            "reminder.reschedule",
            data = mapOf("scheduledCount" to scheduledCodes.size),
        )
        Log.d(LOG_TAG, "Scheduled ${scheduledCodes.size} task reminders")
    }

    fun cancelAll() {
        cancelAllInternal()
        preferenceStore.clearNotifiedSet()
        TdayTelemetry.addBreadcrumb("reminder.cancel_all")
        Log.d(LOG_TAG, "Cancelled all task reminders")
    }

    /**
     * Re-arms a delivered reminder [delayMillis] from now (notification
     * snooze). Clears the notified marker first — the re-fire goes through
     * the same dedup as the original alarm. The alarm uses a salted request
     * code and stays out of the tracked set so a rescheduleAll pass can
     * neither cancel nor clobber it.
     */
    fun snooze(
        taskId: String,
        title: String?,
        dueMillis: Long,
        priority: String?,
        instanceDateMillis: Long,
        delayMillis: Long,
    ) {
        val alarmKey = alarmKeyFor(taskId, instanceDateMillis)
        preferenceStore.clearNotified(alarmKey)

        val intent = Intent(context, TaskReminderReceiver::class.java).apply {
            putExtra(TaskReminderReceiver.EXTRA_TASK_ID, taskId)
            putExtra(TaskReminderReceiver.EXTRA_TASK_TITLE, title)
            putExtra(TaskReminderReceiver.EXTRA_TASK_DUE_MILLIS, dueMillis)
            putExtra(TaskReminderReceiver.EXTRA_TASK_PRIORITY, priority)
            putExtra(TaskReminderReceiver.EXTRA_INSTANCE_DATE_MILLIS, instanceDateMillis)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarmKey.hashCode() xor SNOOZE_REQUEST_SALT,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val triggerAtMillis = System.currentTimeMillis() + delayMillis
        if (canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
        TdayTelemetry.addBreadcrumb("reminder.snooze", data = mapOf("delayMillis" to delayMillis))
    }

    private fun cancelAllInternal() {
        val previousCodes = preferenceStore.getScheduledRequestCodes()
        for (code in previousCodes) {
            val intent = Intent(context, TaskReminderReceiver::class.java)
            val pending = PendingIntent.getBroadcast(
                context,
                code,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            )
            if (pending != null) {
                alarmManager.cancel(pending)
                pending.cancel()
            }
        }
        preferenceStore.saveScheduledRequestCodes(emptySet())
    }

    private fun scheduleAlarm(task: TodoItem, triggerAtMillis: Long, requestCode: Int) {
        val intent = Intent(context, TaskReminderReceiver::class.java).apply {
            putExtra(TaskReminderReceiver.EXTRA_TASK_ID, task.id)
            putExtra(TaskReminderReceiver.EXTRA_TASK_TITLE, task.title)
            putExtra(TaskReminderReceiver.EXTRA_TASK_DUE_MILLIS, task.due?.toEpochMilli() ?: -1L)
            putExtra(TaskReminderReceiver.EXTRA_TASK_PRIORITY, task.priority)
            putExtra(
                TaskReminderReceiver.EXTRA_INSTANCE_DATE_MILLIS,
                task.instanceDate?.toEpochMilli() ?: -1L,
            )
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        if (canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent,
            )
        } else {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent,
            )
        }
    }

    private fun canScheduleExactAlarms(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    private fun computeAlarmTime(task: TodoItem, reminder: ReminderOption): Long? {
        val dueMillis = task.due?.toEpochMilli() ?: return null
        if (dueMillis <= 0) return null
        return dueMillis - reminder.offsetMillis
    }

    companion object {
        private const val LOG_TAG = "TaskReminderScheduler"
        private const val SNOOZE_REQUEST_SALT = 0x534E5A31 // "SNZ1"

        fun requestCodeFor(task: TodoItem): Int {
            val instanceHash = task.instanceDate?.toEpochMilli()?.hashCode() ?: 0
            return task.id.hashCode() xor instanceHash
        }

        fun alarmKeyFor(taskId: String, instanceDateMillis: Long): String {
            return if (instanceDateMillis > 0) "$taskId@$instanceDateMillis" else taskId
        }
    }
}
