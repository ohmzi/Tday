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

    /**
     * The widget mirrors the shared [com.ohmz.tday.shared.sort.TaskSortEngine] floater order —
     * pinned first, then priority (High/Urgent → Low), then most-recently-modified, then id.
     * Title plays no part: two floaters of equal priority are separated by their update time,
     * not alphabetically.
     */
    @Test
    fun `snapshot sorts by pinned priority modified and id`() {
        val snapshot = buildFloaterWidgetSnapshot(
            state = OfflineSyncState(
                floaters = listOf(
                    floater(id = "low-a", title = "Alpha", priority = "Low"),
                    floater(id = "high-b", title = "Beta", priority = "High"),
                    floater(id = "medium-a", title = "Alpha", priority = "Medium"),
                    floater(id = "pinned-low", title = "Zulu", priority = "Low", pinned = true),
                    // Server vocabulary: "Urgent" ranks as High, "Important" as Medium. Both
                    // carry the same (absent) update time as their rank-mate, so id decides.
                    floater(id = "urgent-a", title = "Alpha", priority = "Urgent"),
                    floater(id = "urgent-b", title = "Alpha", priority = "Important"),
                    // Same rank as "high-b"/"urgent-a" but modified, so it leads them both.
                    floater(id = "zz-high-recent", title = "Zeta", priority = "High", updatedAtEpochMs = 500L),
                ),
            ),
            workspaceConfigured = true,
        )

        assertEquals(
            listOf("pinned-low", "zz-high-recent", "high-b", "urgent-a", "medium-a", "urgent-b", "low-a"),
            snapshot.rows.map { it.id },
        )
    }

    @Test
    fun `snapshot caps display tasks but preserves total count`() {
        // Zero-padded ids: every floater ties on pinned/priority/modified, so the id tiebreak
        // decides, and padding keeps that lexicographic order the same as the numeric one.
        val floaters = (0 until 55).map { index ->
            val suffix = index.toString().padStart(2, '0')
            floater(id = "task-$suffix", title = "Task $suffix")
        }

        val snapshot = buildFloaterWidgetSnapshot(
            state = OfflineSyncState(floaters = floaters),
            workspaceConfigured = true,
        )

        assertEquals(55, snapshot.taskCount)
        assertEquals(50, snapshot.rows.size)
        assertEquals("task-00", snapshot.rows.first().id)
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
        updatedAtEpochMs: Long = 0L,
    ) = CachedFloaterRecord(
        id = id,
        canonicalId = id,
        title = title,
        priority = priority,
        pinned = pinned,
        completed = completed,
        listId = listId,
        updatedAtEpochMs = updatedAtEpochMs,
    )
}
