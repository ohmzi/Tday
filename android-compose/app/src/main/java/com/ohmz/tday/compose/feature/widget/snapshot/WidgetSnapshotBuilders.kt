package com.ohmz.tday.compose.feature.widget.snapshot

import com.ohmz.tday.compose.core.data.CachedFloaterRecord
import com.ohmz.tday.compose.core.data.CachedTodoRecord
import com.ohmz.tday.compose.core.data.OfflineSyncState
import com.ohmz.tday.compose.ui.priority.isImportantPriority
import com.ohmz.tday.compose.ui.priority.isLowestPriority
import com.ohmz.tday.compose.ui.priority.isUrgentPriority
import com.ohmz.tday.shared.sort.TaskSortEngine
import com.ohmz.tday.shared.sort.TaskSortKey
import java.time.LocalDate
import java.time.ZoneId

/**
 * Selection, ordering and capping for the Today snapshot — moved verbatim from the old
 * `TodayTasksWidgetModel.kt` (deleted). The write side, not the widget, now owns this: it runs
 * once per cache save, not once per render.
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
    val todayTasks = dueTodayFeed(state, dayStart, dayEnd)
    val overdueTasks = overdueFeed(state, dayStart)

    return WidgetSnapshot(
        generatedAtEpochMs = nowEpochMs,
        status = if (todayTasks.isEmpty()) WidgetSnapshotStatus.EMPTY else WidgetSnapshotStatus.TASKS,
        taskCount = todayTasks.size,
        dayStartEpochMs = dayStart,
        dayEndEpochMs = dayEnd,
        rows = todayTasks.take(taskLimit).map { it.toSnapshotRow() },
        // The same selection for each of the following days, so the widget turns over at midnight
        // from what is already on disk (see WidgetSnapshot.todayAt). Each end is one calendar day
        // after its start, so 23- and 25-hour DST days keep their real length.
        upcomingDays = (1..UPCOMING_DAY_COUNT).map { offset ->
            val start = today.plusDays(offset.toLong()).atStartOfDay(zoneId).toInstant().toEpochMilli()
            val end = today.plusDays(offset + 1L).atStartOfDay(zoneId).toInstant().toEpochMilli()
            val tasks = dueTodayFeed(state, start, end)
            // Anything still open from before this day — today's own tasks included — is overdue
            // by then. No write happening in between is what makes "still open" true.
            val overdue = overdueFeed(state, start)
            WidgetSnapshotDay(
                dayStartEpochMs = start,
                dayEndEpochMs = end,
                taskCount = tasks.size,
                rows = tasks.take(UPCOMING_DAY_TASK_LIMIT).map { it.toSnapshotRow() },
                overdueCount = overdue.size,
                overdueRows = overdue.take(OVERDUE_TASK_LIMIT).map { it.toSnapshotRow() },
                completedCount = completedDueIn(state, start, end),
            )
        },
        completedCount = completedDueIn(state, dayStart, dayEnd),
        overdueCount = overdueTasks.size,
        overdueRows = overdueTasks.take(OVERDUE_TASK_LIMIT).map { it.toSnapshotRow() },
    )
}

/** Incomplete tasks due in `[dayStart, dayEnd)`, in the Today feed's order. */
private fun dueTodayFeed(state: OfflineSyncState, dayStart: Long, dayEnd: Long): List<CachedTodoRecord> =
    sortedLikeToday(
        state.todos.filter { task ->
            val dueEpochMs = task.dueEpochMs ?: return@filter false
            !task.completed && dueEpochMs >= dayStart && dueEpochMs < dayEnd
        },
    )

/**
 * Completed tasks that were due in `[dayStart, dayEnd)` — the done half of that day's progress,
 * whenever they were checked off. A task due another day and finished today is not one of today's
 * tasks, so it must not fill today's ring: counting by completion time made a day with one open
 * task read "3 of 4 done".
 */
private fun completedDueIn(state: OfflineSyncState, dayStart: Long, dayEnd: Long): Int =
    state.completedItems.count { record ->
        val dueEpochMs = record.dueEpochMs ?: return@count false
        dueEpochMs >= dayStart && dueEpochMs < dayEnd
    }

/**
 * Incomplete tasks due before [dayStart]: the app's Today "Earlier" bucket, which is the overdue
 * set clipped to the day boundary (`todayEarlierItems`) so a task due earlier today stays today's.
 */
private fun overdueFeed(state: OfflineSyncState, dayStart: Long): List<CachedTodoRecord> =
    sortedLikeToday(
        state.todos.filter { task ->
            val dueEpochMs = task.dueEpochMs ?: return@filter false
            !task.completed && dueEpochMs < dayStart
        },
    )

private fun sortedLikeToday(tasks: List<CachedTodoRecord>): List<CachedTodoRecord> =
    TaskSortEngine.sortedTodos(tasks) { task ->
        TaskSortKey(
            id = task.id,
            pinned = task.pinned,
            dueEpochMs = task.dueEpochMs,
            priorityRank = TaskSortEngine.priorityRank(task.priority),
            updatedAtEpochMs = task.updatedAtEpochMs.takeIf { it > 0L },
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

    // The two pseudo "lists" are the app's own scheduled views, not list records: there is nothing
    // to look up and nothing that can go missing, so they are answered before the catalog read and
    // never reach the "list deleted" path below.
    if (listType.isPseudoList) {
        return buildPseudoListSnapshot(state, listType, nowEpochMs, taskLimit)
    }

    // Found by id in the list catalog of its own type; a list that is gone is reported as such
    // rather than rendered as an empty list, so the widget can ask for another one. Name and icon
    // key are read as one pair from one record — the header takes the name and the watermark the
    // key, and reading them separately would let a rename and an icon change land out of step.
    val list = when (listType) {
        WidgetListType.TODO -> state.lists.firstOrNull { it.id == listId }
            ?.let { ListWidgetIdentity(it.name, it.iconKey, it.color) }
        WidgetListType.FLOATER -> state.floaterLists.firstOrNull { it.id == listId }
            ?.let { ListWidgetIdentity(it.name, it.iconKey, it.color) }
        WidgetListType.SCHEDULED, WidgetListType.OVERDUE -> null
    }
    val listName = list?.name
    val listIconKey = list?.iconKey
    val listColorKey = list?.colorKey
    if (listName == null) {
        return WidgetSnapshot(
            generatedAtEpochMs = nowEpochMs,
            status = WidgetSnapshotStatus.EMPTY,
            taskCount = 0,
            listMissing = true,
        )
    }

    return when (listType) {
        WidgetListType.TODO -> {
            val tasks = TaskSortEngine.sortedTodos(
                state.todos.filter { !it.completed && it.listId == listId },
            ) { it.toTodoSortKey() }
            WidgetSnapshot(
                generatedAtEpochMs = nowEpochMs,
                status = if (tasks.isEmpty()) WidgetSnapshotStatus.EMPTY else WidgetSnapshotStatus.TASKS,
                taskCount = tasks.size,
                rows = tasks.take(taskLimit).map { it.toSnapshotRow(nowEpochMs) },
                listName = listName,
                listIconKey = listIconKey,
                listColorKey = listColorKey,
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
                listName = listName,
                listIconKey = listIconKey,
                listColorKey = listColorKey,
            )
        }

        // Answered by the early return above; listed so the exhaustiveness check keeps this
        // `when` honest if a fifth type ever appears.
        WidgetListType.SCHEDULED, WidgetListType.OVERDUE ->
            error("pseudo lists are built before the catalog lookup")
    }
}

/**
 * The Today/list sort key for a cached todo — the one place the four fields are assembled, so a
 * pseudo list's ordering cannot drift from a real todo-list's.
 */
private fun CachedTodoRecord.toTodoSortKey(): TaskSortKey = TaskSortKey(
    id = id,
    pinned = pinned,
    dueEpochMs = dueEpochMs,
    priorityRank = TaskSortEngine.priorityRank(priority),
    updatedAtEpochMs = updatedAtEpochMs.takeIf { it > 0L },
)

/**
 * A pseudo list's snapshot: [WidgetListType.SCHEDULED] holds the open dated tasks still ahead,
 * and [WidgetListType.OVERDUE] the ones already past due. Both render in the todo-list shape.
 *
 * The split is the one the app's own Scheduled and Overdue screens draw, so the count on the
 * widget and the count on the screen it opens are the same number. "Every dated task" would have
 * made Scheduled a superset of Overdue and the two picker entries overlap.
 *
 * No name, icon key or colour are baked — a pseudo instance titles itself from a string resource
 * at render time (see `ListTasksWidget`), the same reason the Today and Floater titles are not.
 */
private fun buildPseudoListSnapshot(
    state: OfflineSyncState,
    listType: WidgetListType,
    nowEpochMs: Long,
    taskLimit: Int,
): WidgetSnapshot {
    val tasks = TaskSortEngine.sortedTodos(
        state.todos.filter { task ->
            if (task.completed) return@filter false
            val dueEpochMs = task.dueEpochMs ?: return@filter false
            if (listType == WidgetListType.OVERDUE) dueEpochMs < nowEpochMs
            else dueEpochMs >= nowEpochMs
        },
    ) { it.toTodoSortKey() }
    return WidgetSnapshot(
        generatedAtEpochMs = nowEpochMs,
        status = if (tasks.isEmpty()) WidgetSnapshotStatus.EMPTY else WidgetSnapshotStatus.TASKS,
        taskCount = tasks.size,
        rows = tasks.take(taskLimit).map { it.toSnapshotRow(nowEpochMs) },
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
 * Buckets a raw priority string at write time, the same four-way split
 * `taskWidgetPriorityRingResource` used to do per row on every render. Kept on the write side
 * deliberately: it is the only place in this feature that still needs `ui.priority`.
 */
internal fun widgetPriorityRingFor(priority: String): WidgetPriorityRing = when {
    isUrgentPriority(priority) -> WidgetPriorityRing.HIGH
    isImportantPriority(priority) -> WidgetPriorityRing.MEDIUM
    isLowestPriority(priority) -> WidgetPriorityRing.LOWEST
    else -> WidgetPriorityRing.LOW
}

/**
 * The identity half of a chosen list — what the widget's header, watermark and ACCENT come from, as
 * opposed to its tasks. One lookup in the catalog of the matching type serves all three: the two
 * cached record types (`CachedListRecord`, `CachedFloaterListRecord`) share no supertype, so
 * without it each field would need its own `firstOrNull` over the same list — and a rename, an icon
 * change and a recolour could land out of step with one another.
 */
private data class ListWidgetIdentity(val name: String, val iconKey: String?, val colorKey: String?)
