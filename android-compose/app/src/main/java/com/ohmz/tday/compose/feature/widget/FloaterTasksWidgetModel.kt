package com.ohmz.tday.compose.feature.widget

import com.ohmz.tday.compose.core.data.CachedFloaterRecord
import com.ohmz.tday.compose.core.data.OfflineSyncState
import com.ohmz.tday.shared.sort.TaskSortEngine
import com.ohmz.tday.shared.sort.TaskSortKey

enum class FloaterTasksWidgetStatus {
    SETUP,
    EMPTY,
    TASKS,
}

data class FloaterTasksWidgetModel(
    val title: String,
    val status: FloaterTasksWidgetStatus,
    val taskCount: Int,
    val tasks: List<CachedFloaterRecord>,
) {
    val overflowCount: Int
        get() = (taskCount - tasks.size).coerceAtLeast(0)
}

fun buildFloaterTasksWidgetModel(
    state: OfflineSyncState,
    title: String,
    workspaceConfigured: Boolean,
    taskLimit: Int = FLOATER_TASKS_WIDGET_TASK_LIMIT,
): FloaterTasksWidgetModel {
    if (!workspaceConfigured) {
        return FloaterTasksWidgetModel(
            title = title,
            status = FloaterTasksWidgetStatus.SETUP,
            taskCount = 0,
            tasks = emptyList(),
        )
    }

    val floaterTasks = TaskSortEngine.sortedFloaters(
        state.floaters.filter { !it.completed },
    ) { floater ->
        TaskSortKey(
            id = floater.id,
            pinned = floater.pinned,
            priorityRank = TaskSortEngine.priorityRank(floater.priority),
            updatedAtEpochMs = floater.updatedAtEpochMs.takeIf { it > 0L },
        )
    }

    return FloaterTasksWidgetModel(
        title = title,
        status = if (floaterTasks.isEmpty()) FloaterTasksWidgetStatus.EMPTY else FloaterTasksWidgetStatus.TASKS,
        taskCount = floaterTasks.size,
        tasks = floaterTasks.take(taskLimit),
    )
}

const val FLOATER_TASKS_WIDGET_TASK_LIMIT = 50
