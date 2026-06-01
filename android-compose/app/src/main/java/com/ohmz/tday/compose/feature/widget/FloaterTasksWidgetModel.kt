package com.ohmz.tday.compose.feature.widget

import com.ohmz.tday.compose.core.data.CachedFloaterRecord
import com.ohmz.tday.compose.core.data.OfflineSyncState
import java.util.Locale

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

    val floaterTasks = state.floaters
        .asSequence()
        .filter { !it.completed }
        .sortedWith(floaterWidgetComparator)
        .toList()

    return FloaterTasksWidgetModel(
        title = title,
        status = if (floaterTasks.isEmpty()) FloaterTasksWidgetStatus.EMPTY else FloaterTasksWidgetStatus.TASKS,
        taskCount = floaterTasks.size,
        tasks = floaterTasks.take(taskLimit),
    )
}

private val floaterWidgetComparator = compareByDescending<CachedFloaterRecord> { it.pinned }
    .thenBy { floaterWidgetPriorityRank(it.priority) }
    .thenBy { it.title.lowercase(Locale.getDefault()) }
    .thenBy { it.id }

private fun floaterWidgetPriorityRank(priority: String): Int {
    return when (priority.trim().lowercase(Locale.getDefault())) {
        "high", "urgent", "important" -> 0
        "medium" -> 1
        else -> 2
    }
}

const val FLOATER_TASKS_WIDGET_TASK_LIMIT = 20
