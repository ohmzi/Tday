package com.ohmz.tday.compose.feature.widget

import com.ohmz.tday.compose.core.data.todo.TodoRepository
import com.ohmz.tday.compose.core.model.CreateTaskPayload
import com.ohmz.tday.compose.core.notification.TaskReminderScheduler
import com.ohmz.tday.compose.core.observability.TdayTelemetry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WidgetCreateTaskSubmitter @Inject constructor(
    private val todoRepository: TodoRepository,
    private val reminderScheduler: TaskReminderScheduler,
    private val todayTasksWidgetRefresher: TodayTasksWidgetRefresher,
    private val floaterTasksWidgetRefresher: FloaterTasksWidgetRefresher,
) {
    suspend fun submitTodayTask(payload: CreateTaskPayload) = withContext(Dispatchers.Default) {
        if (payload.title.isBlank()) return@withContext

        runCatching {
            todoRepository.createTodo(payload)
        }.onSuccess {
            reminderScheduler.rescheduleAll()
            todayTasksWidgetRefresher.refreshNow()
        }.onFailure { error ->
            TdayTelemetry.capture(
                error,
                operation = "widget_create_task.submit",
            )
            todayTasksWidgetRefresher.refreshNow()
        }
    }

    suspend fun submitFloaterTask(payload: CreateTaskPayload) = withContext(Dispatchers.Default) {
        if (payload.title.isBlank()) return@withContext

        runCatching {
            todoRepository.createFloater(payload)
        }.onSuccess {
            floaterTasksWidgetRefresher.refreshNow()
        }.onFailure { error ->
            TdayTelemetry.capture(
                error,
                operation = "widget_create_floater.submit",
            )
            floaterTasksWidgetRefresher.refreshNow()
        }
    }
}
