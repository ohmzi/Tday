package com.ohmz.tday.compose.feature.widget.snapshot

import com.ohmz.tday.compose.core.data.CachedFloaterRecord
import com.ohmz.tday.compose.core.data.OfflineSyncState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FloaterWidgetSnapshotBuilderTest {
    @Test
    fun `snapshot includes only pending floater tasks`() {
        val snapshot = buildFloaterWidgetSnapshot(
            state = OfflineSyncState(
                floaters = listOf(
                    floater(id = "open", title = "Open"),
                    floater(id = "listed", title = "Listed", listId = "list-1"),
                    floater(id = "completed", title = "Completed", completed = true),
                ),
            ),
            workspaceConfigured = true,
        )

        assertEquals(WidgetSnapshotStatus.TASKS, snapshot.status)
        assertEquals(2, snapshot.taskCount)
        assertEquals(listOf("listed", "open"), snapshot.rows.map { it.id })
    }

    @Test
    fun `snapshot sorts by pinned priority title and id`() {
        val snapshot = buildFloaterWidgetSnapshot(
            state = OfflineSyncState(
                floaters = listOf(
                    floater(id = "low-a", title = "Alpha", priority = "Low"),
                    floater(id = "high-b", title = "Beta", priority = "High"),
                    floater(id = "medium-a", title = "Alpha", priority = "Medium"),
                    floater(id = "pinned-low", title = "Zulu", priority = "Low", pinned = true),
                    floater(id = "urgent-a", title = "Alpha", priority = "Urgent"),
                    floater(id = "urgent-b", title = "Alpha", priority = "Important"),
                ),
            ),
            workspaceConfigured = true,
        )

        assertEquals(
            listOf("pinned-low", "urgent-a", "high-b", "medium-a", "urgent-b", "low-a"),
            snapshot.rows.map { it.id },
        )
    }

    @Test
    fun `snapshot caps display tasks but preserves total count`() {
        val floaters = (0 until 55).map { index ->
            floater(id = "task-$index", title = "Task ${index.toString().padStart(2, '0')}")
        }

        val snapshot = buildFloaterWidgetSnapshot(
            state = OfflineSyncState(floaters = floaters),
            workspaceConfigured = true,
        )

        assertEquals(55, snapshot.taskCount)
        assertEquals(50, snapshot.rows.size)
        assertEquals(5, snapshot.overflowCount)
        assertEquals("task-0", snapshot.rows.first().id)
        assertEquals("task-49", snapshot.rows.last().id)
    }

    @Test
    fun `snapshot exposes empty state for configured workspaces without floater tasks`() {
        val snapshot = buildFloaterWidgetSnapshot(
            state = OfflineSyncState(),
            workspaceConfigured = true,
        )

        assertEquals(WidgetSnapshotStatus.EMPTY, snapshot.status)
        assertEquals(0, snapshot.taskCount)
        assertTrue(snapshot.rows.isEmpty())
    }

    @Test
    fun `snapshot exposes setup state before workspace configuration`() {
        val snapshot = buildFloaterWidgetSnapshot(
            state = OfflineSyncState(
                floaters = listOf(floater(id = "floater", title = "Floater")),
            ),
            workspaceConfigured = false,
        )

        assertEquals(WidgetSnapshotStatus.SETUP, snapshot.status)
        assertEquals(0, snapshot.taskCount)
        assertTrue(snapshot.rows.isEmpty())
    }

    private fun floater(
        id: String,
        title: String,
        priority: String = "Low",
        pinned: Boolean = false,
        completed: Boolean = false,
        listId: String? = null,
    ) = CachedFloaterRecord(
        id = id,
        canonicalId = id,
        title = title,
        priority = priority,
        pinned = pinned,
        completed = completed,
        listId = listId,
        updatedAtEpochMs = 0L,
    )
}
