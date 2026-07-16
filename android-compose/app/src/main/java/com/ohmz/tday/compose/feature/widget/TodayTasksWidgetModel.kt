package com.ohmz.tday.compose.feature.widget

import com.ohmz.tday.compose.core.data.CachedTodoRecord
import com.ohmz.tday.compose.core.data.OfflineSyncState
import com.ohmz.tday.shared.sort.TaskSortEngine
import com.ohmz.tday.shared.sort.TaskSortKey
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
) {
    val overflowCount: Int
        get() = (taskCount - tasks.size).coerceAtLeast(0)
}

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
    val todayTasks = TaskSortEngine.sortedTodos(
        state.todos.filter { task ->
            val dueEpochMs = task.dueEpochMs ?: return@filter false
            !task.completed && dueEpochMs >= dayStart && dueEpochMs < dayEnd
        },
    ) { task ->
        TaskSortKey(
            id = task.id,
            pinned = task.pinned,
            dueEpochMs = task.dueEpochMs,
            priorityRank = TaskSortEngine.priorityRank(task.priority),
            updatedAtEpochMs = task.updatedAtEpochMs.takeIf { it > 0L },
        )
    }

    return TodayTasksWidgetModel(
        title = title,
        status = if (todayTasks.isEmpty()) TodayTasksWidgetStatus.EMPTY else TodayTasksWidgetStatus.TASKS,
        taskCount = todayTasks.size,
        tasks = todayTasks.take(taskLimit),
    )
}

const val TODAY_TASKS_WIDGET_TASK_LIMIT = 50
