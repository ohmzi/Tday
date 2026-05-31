package com.ohmz.tday.compose.feature.widget

import com.ohmz.tday.compose.core.data.CachedTodoRecord
import com.ohmz.tday.compose.core.data.OfflineSyncState
import java.time.LocalDate
import java.time.ZoneId

enum class TodayTasksWidgetStatus {
    SETUP,
    EMPTY,
    TASKS,
}

data class TodayTasksWidgetModel(
    val title: String,
    val status: TodayTasksWidgetStatus,
    val taskCount: Int,
    val tasks: List<CachedTodoRecord>,
)

fun buildTodayTasksWidgetModel(
    state: OfflineSyncState,
    title: String,
    workspaceConfigured: Boolean,
    today: LocalDate = LocalDate.now(),
    zoneId: ZoneId = ZoneId.systemDefault(),
    taskLimit: Int = TODAY_TASKS_WIDGET_TASK_LIMIT,
): TodayTasksWidgetModel {
    if (!workspaceConfigured) {
        return TodayTasksWidgetModel(
            title = title,
            status = TodayTasksWidgetStatus.SETUP,
            taskCount = 0,
            tasks = emptyList(),
        )
    }

    val dayStart = today.atStartOfDay(zoneId).toInstant().toEpochMilli()
    val dayEnd = today.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
    val todayTasks = state.todos
        .asSequence()
        .filter { task ->
            val dueEpochMs = task.dueEpochMs ?: return@filter false
            !task.completed && dueEpochMs >= dayStart && dueEpochMs < dayEnd
        }
        .sortedWith(
            compareBy<CachedTodoRecord> { it.dueEpochMs ?: Long.MAX_VALUE }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title },
        )
        .toList()

    return TodayTasksWidgetModel(
        title = title,
        status = if (todayTasks.isEmpty()) TodayTasksWidgetStatus.EMPTY else TodayTasksWidgetStatus.TASKS,
        taskCount = todayTasks.size,
        tasks = todayTasks.take(taskLimit),
    )
}

const val TODAY_TASKS_WIDGET_TASK_LIMIT = 20
