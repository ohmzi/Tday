package com.ohmz.tday.compose.feature.widget.snapshot

import com.ohmz.tday.compose.core.data.CachedFloaterListRecord
import com.ohmz.tday.compose.core.data.CachedFloaterRecord
import com.ohmz.tday.compose.core.data.CachedListRecord
import com.ohmz.tday.compose.core.data.CachedTodoRecord
import com.ohmz.tday.compose.core.data.OfflineSyncState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The `skipcq: KT-W1042` markers below are deliberate, the same rationale already recorded in
 * `BulkTaskCacheTest`'s class doc: `"list-1"` and `"in-list"` are per-test fixture identities, not
 * a shared value — each test builds its own small list of records and a shared constant would
 * imply a coupling between unrelated test cases that does not exist.
 */
class ListWidgetSnapshotBuilderTest {
    private val now = 10_000_000L

    @Test
    fun `todo snapshot includes only pending tasks from the chosen list`() {
        val snapshot = buildListWidgetSnapshot(
            state = OfflineSyncState(
                lists = LISTS,
                floaterLists = FLOATER_LISTS,
                todos = listOf(
                    todo(id = "in-list", title = "In list", listId = "list-1"),  // skipcq: KT-W1042
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
                lists = LISTS,
                floaterLists = FLOATER_LISTS,
                todos = listOf(
                    todo(id = "far-future", title = "Far future", listId = "list-1", dueEpochMs = now + 30L * 86_400_000L),
                    todo(id = "undated", title = "Undated", listId = "list-1", dueEpochMs = null),  // skipcq: KT-W1042
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
                lists = LISTS,
                floaterLists = FLOATER_LISTS,
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
                lists = LISTS,
                floaterLists = FLOATER_LISTS,
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
            state = OfflineSyncState(todos = todos, lists = LISTS),
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
            state = OfflineSyncState(lists = LISTS),
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
                lists = LISTS,
                floaterLists = FLOATER_LISTS,
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

    @Test
    fun `snapshot carries the list's current name`() {
        val snapshot = buildListWidgetSnapshot(
            state = OfflineSyncState(lists = listOf(CachedListRecord(id = "list-1", name = "Renamed"))),
            listId = "list-1",
            listType = WidgetListType.TODO,
            workspaceConfigured = true,
            nowEpochMs = now,
        )

        assertEquals("Renamed", snapshot.listName)
        assertEquals(false, snapshot.listMissing)
    }

    @Test
    fun `snapshot reports a list that no longer exists instead of an empty one`() {
        val snapshot = buildListWidgetSnapshot(
            state = OfflineSyncState(
                todos = listOf(todo(id = "orphan", title = "Orphan", listId = "gone")),
                lists = LISTS,
            ),
            listId = "gone",
            listType = WidgetListType.TODO,
            workspaceConfigured = true,
            nowEpochMs = now,
        )

        assertTrue(snapshot.listMissing)
        assertTrue(snapshot.rows.isEmpty())
    }

    @Test
    fun `a floater list is looked up among floater lists, not scheduled ones`() {
        val snapshot = buildListWidgetSnapshot(
            state = OfflineSyncState(lists = listOf(CachedListRecord(id = "shared-id", name = "Scheduled"))),
            listId = "shared-id",
            listType = WidgetListType.FLOATER,
            workspaceConfigured = true,
            nowEpochMs = now,
        )

        assertTrue(snapshot.listMissing)
    }

    @Test
    fun `scheduled pseudo list holds every open dated task, whatever list it is in`() {
        val snapshot = buildListWidgetSnapshot(
            state = OfflineSyncState(
                todos = listOf(
                    todo(id = "s-open", title = "Scheduled open", listId = "list-7", dueEpochMs = now + 1L),
                    todo(id = "s-unsorted", title = "Scheduled unsorted", listId = null, dueEpochMs = now + 2L),
                    todo(id = "s-clear", title = "Scheduled clear", listId = "list-7", dueEpochMs = null),
                    todo(id = "s-done", title = "Scheduled done", listId = "list-7", dueEpochMs = now + 3L, completed = true),
                ),
            ),
            listId = WidgetListType.SCHEDULED.pseudoSelectionId,
            listType = WidgetListType.SCHEDULED,
            workspaceConfigured = true,
            nowEpochMs = now,
        )

        assertEquals(WidgetSnapshotStatus.TASKS, snapshot.status)
        assertEquals(2, snapshot.taskCount)
        assertEquals(setOf("s-open", "s-unsorted"), snapshot.rows.map { it.id }.toSet())
        // Not a list, so there is nothing to look up and nothing that can go missing.
        assertFalse(snapshot.listMissing)
    }

    @Test
    fun `overdue pseudo list holds only the dated tasks already past due`() {
        val snapshot = buildListWidgetSnapshot(
            state = OfflineSyncState(
                todos = listOf(
                    todo(id = "o-late", title = "Overdue late", listId = "list-8", dueEpochMs = now - 1L),
                    todo(id = "o-soon", title = "Overdue soon", listId = "list-8", dueEpochMs = now + 1L),
                    todo(id = "o-clear", title = "Overdue clear", listId = "list-8", dueEpochMs = null),
                ),
            ),
            listId = WidgetListType.OVERDUE.pseudoSelectionId,
            listType = WidgetListType.OVERDUE,
            workspaceConfigured = true,
            nowEpochMs = now,
        )

        assertEquals(1, snapshot.taskCount)
        assertEquals(listOf("o-late"), snapshot.rows.map { it.id })
        // Every row here is by definition past its due time, so the widget tints them all.
        assertTrue(snapshot.rows.single().overdue)
    }

    @Test
    fun `a pseudo list ignores the stored list id and never reads as missing`() {
        val snapshot = buildListWidgetSnapshot(
            state = OfflineSyncState(
                todos = listOf(todo(id = "p-orphan", title = "Pseudo orphan", listId = "list-9", dueEpochMs = now + 1L)),
                lists = LISTS,
            ),
            listId = "list-9",
            listType = WidgetListType.SCHEDULED,
            workspaceConfigured = true,
            nowEpochMs = now,
        )

        assertFalse(snapshot.listMissing)
        assertEquals(listOf("p-orphan"), snapshot.rows.map { it.id })
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

    private companion object {
        val LISTS = listOf(CachedListRecord(id = "list-1", name = "Errands"))
        val FLOATER_LISTS = listOf(CachedFloaterListRecord(id = "list-1", name = "Someday"))
    }
}
