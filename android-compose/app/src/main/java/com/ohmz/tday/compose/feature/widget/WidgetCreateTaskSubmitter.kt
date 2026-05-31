package com.ohmz.tday.compose.feature.widget

import com.ohmz.tday.compose.core.data.todo.TodoRepository
import com.ohmz.tday.compose.core.model.CreateTaskPayload
import com.ohmz.tday.compose.core.notification.TaskReminderScheduler
import com.ohmz.tday.compose.core.observability.TdayTelemetry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WidgetCreateTaskSubmitter @Inject constructor(
    private val todoRepository: TodoRepository,
    private val reminderScheduler: TaskReminderScheduler,
    private val todayTasksWidgetRefresher: TodayTasksWidgetRefresher,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun submitTodayTask(payload: CreateTaskPayload) {
        if (payload.title.isBlank()) return

        scope.launch {
            runCatching {
                todoRepository.createTodo(payload)
            }.onSuccess {
                reminderScheduler.rescheduleAll()
                todayTasksWidgetRefresher.requestRefresh()
            }.onFailure { error ->
                TdayTelemetry.capture(
                    error,
                    operation = "widget_create_task.submit",
                )
                todayTasksWidgetRefresher.requestRefresh()
            }
        }
    }
}
