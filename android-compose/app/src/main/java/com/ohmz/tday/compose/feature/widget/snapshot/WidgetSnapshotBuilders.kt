package com.ohmz.tday.compose.feature.widget.snapshot

import com.ohmz.tday.compose.core.data.CachedFloaterRecord
import com.ohmz.tday.compose.core.data.CachedTodoRecord
import com.ohmz.tday.compose.core.data.OfflineSyncState
import com.ohmz.tday.compose.ui.priority.isImportantPriority
import com.ohmz.tday.compose.ui.priority.isUrgentPriority
import com.ohmz.tday.shared.sort.TaskSortEngine
import com.ohmz.tday.shared.sort.TaskSortKey
import java.time.LocalDate
import java.time.ZoneId

/**
 * Selection, ordering and capping for the Today snapshot — moved verbatim from the old
 * `TodayTasksWidgetModel.kt` (deleted). The write side, not the widget, now owns this: it runs
 * once per cache save, not once per `provideGlance`.
 */
internal fun buildTodayWidgetSnapshot(
    state: OfflineSyncState,
    workspaceConfigured: Boolean,
    nowEpochMs: Long = System.currentTimeMillis(),
    today: LocalDate = LocalDate.now(),
    zoneId: ZoneId = ZoneId.systemDefault(),
    taskLimit: Int = TODAY_TASKS_WIDGET_TASK_LIMIT,
): WidgetSnapshot {
    if (!workspaceConfigured) {
        return WidgetSnapshot(
            generatedAtEpochMs = nowEpochMs,
            status = WidgetSnapshotStatus.SETUP,
            taskCount = 0,
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

    return WidgetSnapshot(
        generatedAtEpochMs = nowEpochMs,
        status = if (todayTasks.isEmpty()) WidgetSnapshotStatus.EMPTY else WidgetSnapshotStatus.TASKS,
        taskCount = todayTasks.size,
        dayStartEpochMs = dayStart,
        dayEndEpochMs = dayEnd,
        rows = todayTasks.take(taskLimit).map { it.toSnapshotRow() },
    )
}

/** Moved verbatim from the old `FloaterTasksWidgetModel.kt` (deleted). */
internal fun buildFloaterWidgetSnapshot(
    state: OfflineSyncState,
    workspaceConfigured: Boolean,
    nowEpochMs: Long = System.currentTimeMillis(),
    taskLimit: Int = FLOATER_TASKS_WIDGET_TASK_LIMIT,
): WidgetSnapshot {
    if (!workspaceConfigured) {
        return WidgetSnapshot(
            generatedAtEpochMs = nowEpochMs,
            status = WidgetSnapshotStatus.SETUP,
            taskCount = 0,
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

    return WidgetSnapshot(
        generatedAtEpochMs = nowEpochMs,
        status = if (floaterTasks.isEmpty()) WidgetSnapshotStatus.EMPTY else WidgetSnapshotStatus.TASKS,
        taskCount = floaterTasks.size,
        rows = floaterTasks.take(taskLimit).map { it.toSnapshotRow() },
    )
}

private fun CachedTodoRecord.toSnapshotRow() = WidgetSnapshotRow(
    id = id,
    key = id.hashCode().toLong(),
    title = title,
    priorityRing = widgetPriorityRingFor(priority),
    dueEpochMs = dueEpochMs,
    description = description,
)

private fun CachedFloaterRecord.toSnapshotRow() = WidgetSnapshotRow(
    id = id,
    key = id.hashCode().toLong(),
    title = title,
    priorityRing = widgetPriorityRingFor(priority),
    description = description,
)

/**
 * Buckets a raw priority string at write time, the same three-way split
 * `taskWidgetPriorityRingResource` used to do per row on every render. Kept on the write side
 * deliberately: it is the only place in this feature that still needs `ui.priority`.
 */
internal fun widgetPriorityRingFor(priority: String): WidgetPriorityRing = when {
    isUrgentPriority(priority) -> WidgetPriorityRing.HIGH
    isImportantPriority(priority) -> WidgetPriorityRing.MEDIUM
    else -> WidgetPriorityRing.LOW
}
