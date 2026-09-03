package com.ohmz.tday.compose.feature.widget.snapshot

import com.ohmz.tday.compose.core.data.CachedFloaterRecord
import com.ohmz.tday.compose.core.data.CachedTodoRecord
import com.ohmz.tday.compose.core.data.OfflineSyncState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ListWidgetSnapshotBuilderTest {
    private val now = 10_000_000L

    @Test
    fun `todo snapshot includes only pending tasks from the chosen list`() {
        val snapshot = buildListWidgetSnapshot(
            state = OfflineSyncState(
                todos = listOf(
                    todo(id = "in-list", title = "In list", listId = "list-1"),
                    todo(id = "other-list", title = "Other list", listId = "list-2"),
                    todo(id = "no-list", title = "No list", listId = null),
                    todo(id = "completed", title = "Completed", listId = "list-1", completed = true),
                ),
            ),
            listId = "list-1",
            listType = WidgetListType.TODO,
            workspaceConfigured = true,
            nowEpochMs = now,
        )

        assertEquals(WidgetSnapshotStatus.TASKS, snapshot.status)
        assertEquals(1, snapshot.taskCount)
        assertEquals(listOf("in-list"), snapshot.rows.map { it.id })
    }

    @Test
    fun `todo snapshot is not restricted to a day window unlike Today`() {
        val snapshot = buildListWidgetSnapshot(
            state = OfflineSyncState(
                todos = listOf(
                    todo(id = "far-future", title = "Far future", listId = "list-1", dueEpochMs = now + 30L * 86_400_000L),
                    todo(id = "undated", title = "Undated", listId = "list-1", dueEpochMs = null),
                ),
            ),
            listId = "list-1",
            listType = WidgetListType.TODO,
            workspaceConfigured = true,
            nowEpochMs = now,
        )

        assertEquals(2, snapshot.taskCount)
        assertEquals(setOf("far-future", "undated"), snapshot.rows.map { it.id }.toSet())
    }

    @Test
    fun `todo snapshot flags a task overdue only when its due time has passed`() {
        val snapshot = buildListWidgetSnapshot(
            state = OfflineSyncState(
                todos = listOf(
                    todo(id = "past", title = "Past", listId = "list-1", dueEpochMs = now - 1L),
                    todo(id = "future", title = "Future", listId = "list-1", dueEpochMs = now + 1L),
                    todo(id = "undated", title = "Undated", listId = "list-1", dueEpochMs = null),
                ),
            ),
            listId = "list-1",
            listType = WidgetListType.TODO,
            workspaceConfigured = true,
            nowEpochMs = now,
        )

        assertEquals(
            mapOf("past" to true, "future" to false, "undated" to false),
            snapshot.rows.associate { it.id to it.overdue },
        )
    }

    @Test
    fun `floater snapshot includes only pending floaters from the chosen list`() {
        val snapshot = buildListWidgetSnapshot(
            state = OfflineSyncState(
                floaters = listOf(
                    floater(id = "in-list", title = "In list", listId = "list-1"),
                    floater(id = "other-list", title = "Other list", listId = "list-2"),
                    floater(id = "completed", title = "Completed", listId = "list-1", completed = true),
                ),
            ),
            listId = "list-1",
            listType = WidgetListType.FLOATER,
            workspaceConfigured = true,
            nowEpochMs = now,
        )

        assertEquals(WidgetSnapshotStatus.TASKS, snapshot.status)
        assertEquals(1, snapshot.taskCount)
        assertEquals(listOf("in-list"), snapshot.rows.map { it.id })
        // Floaters never carry a due date, so overdue can never be true for them.
        assertEquals(false, snapshot.rows.single().overdue)
    }

    @Test
    fun `snapshot caps display tasks but preserves total count`() {
        val todos = (0 until 55).map { index ->
            val suffix = index.toString().padStart(2, '0')
            todo(id = "task-$suffix", title = "Task $suffix", listId = "list-1", dueEpochMs = now + index * 60_000L)
        }

        val snapshot = buildListWidgetSnapshot(
            state = OfflineSyncState(todos = todos),
            listId = "list-1",
            listType = WidgetListType.TODO,
            workspaceConfigured = true,
            nowEpochMs = now,
        )

        assertEquals(55, snapshot.taskCount)
        assertEquals(50, snapshot.rows.size)
    }

    @Test
    fun `snapshot exposes empty state for a configured list with no pending tasks`() {
        val snapshot = buildListWidgetSnapshot(
            state = OfflineSyncState(),
            listId = "list-1",
            listType = WidgetListType.TODO,
            workspaceConfigured = true,
            nowEpochMs = now,
        )

        assertEquals(WidgetSnapshotStatus.EMPTY, snapshot.status)
        assertEquals(0, snapshot.taskCount)
        assertTrue(snapshot.rows.isEmpty())
    }

    @Test
    fun `snapshot exposes setup state before workspace configuration`() {
        val snapshot = buildListWidgetSnapshot(
            state = OfflineSyncState(
                todos = listOf(todo(id = "a", title = "A", listId = "list-1")),
            ),
            listId = "list-1",
            listType = WidgetListType.TODO,
            workspaceConfigured = false,
            nowEpochMs = now,
        )

        assertEquals(WidgetSnapshotStatus.SETUP, snapshot.status)
        assertEquals(0, snapshot.taskCount)
        assertTrue(snapshot.rows.isEmpty())
    }

    private fun todo(
        id: String,
        title: String,
        listId: String?,
        dueEpochMs: Long? = null,
        completed: Boolean = false,
        priority: String = "Low",
    ) = CachedTodoRecord(
        id = id,
        canonicalId = id,
        title = title,
        dueEpochMs = dueEpochMs,
        completed = completed,
        priority = priority,
        listId = listId,
    )

    private fun floater(
        id: String,
        title: String,
        listId: String?,
        completed: Boolean = false,
        priority: String = "Low",
    ) = CachedFloaterRecord(
        id = id,
        canonicalId = id,
        title = title,
        priority = priority,
        completed = completed,
        listId = listId,
    )
}
