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

/**
 * The per-instance list widget (widgets v3): every incomplete task in ONE arbitrary list, chosen
 * per widget instance at configuration time — not restricted to a day window the way
 * [buildTodayWidgetSnapshot] is to "today". Content shape follows [listType] (the design decision
 * from the feature's rollout: a todo-list renders due-date-shaped, a floater-list renders
 * undated-shaped, matching the two existing fixed widgets — never a third shape):
 * - [WidgetListType.TODO]: sorted like Today (due date, then priority, pinned first), and each
 *   row's [WidgetSnapshotRow.overdue] is computed against [nowEpochMs] so the widget can tint a
 *   task whose due time has already passed — Today doesn't show this (its window is always
 *   "today", and adding it there is a separate, unrequested behavior change this PR deliberately
 *   left alone).
 * - [WidgetListType.FLOATER]: sorted like Floater (no due date at all).
 */
internal fun buildListWidgetSnapshot(
    state: OfflineSyncState,
    listId: String,
    listType: WidgetListType,
    workspaceConfigured: Boolean,
    nowEpochMs: Long = System.currentTimeMillis(),
    taskLimit: Int = LIST_TASKS_WIDGET_TASK_LIMIT,
): WidgetSnapshot {
    if (!workspaceConfigured) {
        return WidgetSnapshot(
            generatedAtEpochMs = nowEpochMs,
            status = WidgetSnapshotStatus.SETUP,
            taskCount = 0,
        )
    }

    return when (listType) {
        WidgetListType.TODO -> {
            val tasks = TaskSortEngine.sortedTodos(
                state.todos.filter { !it.completed && it.listId == listId },
            ) { task ->
                TaskSortKey(
                    id = task.id,
                    pinned = task.pinned,
                    dueEpochMs = task.dueEpochMs,
                    priorityRank = TaskSortEngine.priorityRank(task.priority),
                    updatedAtEpochMs = task.updatedAtEpochMs.takeIf { it > 0L },
                )
            }
            WidgetSnapshot(
                generatedAtEpochMs = nowEpochMs,
                status = if (tasks.isEmpty()) WidgetSnapshotStatus.EMPTY else WidgetSnapshotStatus.TASKS,
                taskCount = tasks.size,
                rows = tasks.take(taskLimit).map { it.toSnapshotRow(nowEpochMs) },
            )
        }

        WidgetListType.FLOATER -> {
            val tasks = TaskSortEngine.sortedFloaters(
                state.floaters.filter { !it.completed && it.listId == listId },
            ) { floater ->
                TaskSortKey(
                    id = floater.id,
                    pinned = floater.pinned,
                    priorityRank = TaskSortEngine.priorityRank(floater.priority),
                    updatedAtEpochMs = floater.updatedAtEpochMs.takeIf { it > 0L },
                )
            }
            WidgetSnapshot(
                generatedAtEpochMs = nowEpochMs,
                status = if (tasks.isEmpty()) WidgetSnapshotStatus.EMPTY else WidgetSnapshotStatus.TASKS,
                taskCount = tasks.size,
                rows = tasks.take(taskLimit).map { it.toSnapshotRow() },
            )
        }
    }
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

/**
 * [nowEpochMs] is opt-in and defaults to null (`overdue` stays false) so the existing Today call
 * site is byte-for-byte unchanged; only [buildListWidgetSnapshot] passes it.
 */
private fun CachedTodoRecord.toSnapshotRow(nowEpochMs: Long? = null) = WidgetSnapshotRow(
    id = id,
    key = id.hashCode().toLong(),
    title = title,
    priorityRing = widgetPriorityRingFor(priority),
    dueEpochMs = dueEpochMs,
    description = description,
    overdue = nowEpochMs != null && dueEpochMs != null && dueEpochMs < nowEpochMs,
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
