package com.ohmz.tday.compose.feature.widget

import com.ohmz.tday.compose.core.data.todo.TodoRepository
import com.ohmz.tday.compose.core.model.CreateTaskPayload
import com.ohmz.tday.compose.core.notification.TaskReminderScheduler
import com.ohmz.tday.compose.core.observability.TdayTelemetry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Creates the task typed into the widget's own create sheet, then repaints.
 *
 * The repaint is deliberately NOT routed by which of the two methods below was called. It used to
 * be — `submitTodayTask` awaited the Today refresher and `submitFloaterTask` the Floater one — and
 * since the create target itself was guessed from a deep-link parameter that defaulted to "today",
 * an add could repaint a different widget than the one the user tapped, while the tapped instance
 * was left to a fire-and-forget request in a process that is backgrounded the moment the sheet
 * closes. [WidgetRefresher.refreshNow] repaints every placed instance with its own kind instead,
 * and [appWidgetId] only says which one to paint FIRST.
 */
@Singleton
class WidgetCreateTaskSubmitter @Inject constructor(
    private val todoRepository: TodoRepository,
    private val reminderScheduler: TaskReminderScheduler,
    private val widgetRefresher: WidgetRefresher,
) {
    suspend fun submitTodayTask(
        payload: CreateTaskPayload,
        appWidgetId: Int? = null,
    ) = withContext(Dispatchers.Default) {
        if (payload.title.isBlank()) return@withContext

        runCatching {
            todoRepository.createTodo(payload)
        }.onSuccess {
            reminderScheduler.rescheduleAll()
            widgetRefresher.refreshNow(firstAppWidgetId = appWidgetId)
        }.onFailure { error ->
            TdayTelemetry.capture(
                error,
                operation = "widget_create_task.submit",
            )
            widgetRefresher.refreshNow(firstAppWidgetId = appWidgetId)
        }
    }

    suspend fun submitFloaterTask(
        payload: CreateTaskPayload,
        appWidgetId: Int? = null,
    ) = withContext(Dispatchers.Default) {
        if (payload.title.isBlank()) return@withContext

        runCatching {
            todoRepository.createFloater(payload)
        }.onSuccess {
            widgetRefresher.refreshNow(firstAppWidgetId = appWidgetId)
        }.onFailure { error ->
            TdayTelemetry.capture(
                error,
                operation = "widget_create_floater.submit",
            )
            widgetRefresher.refreshNow(firstAppWidgetId = appWidgetId)
        }
    }
}
