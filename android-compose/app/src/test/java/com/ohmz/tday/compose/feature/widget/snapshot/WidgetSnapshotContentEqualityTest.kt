package com.ohmz.tday.compose.feature.widget.snapshot

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetSnapshotContentEqualityTest {
    private val row = WidgetSnapshotRow(
        id = "a",
        key = 1L,
        title = "Alpha",
        priorityRing = WidgetPriorityRing.HIGH,
        dueEpochMs = 100L,
        description = "notes",
    )

    private val base = WidgetSnapshot(
        generatedAtEpochMs = 1_000L,
        status = WidgetSnapshotStatus.TASKS,
        taskCount = 1,
        dayStartEpochMs = 0L,
        dayEndEpochMs = 86_400_000L,
        rows = listOf(row),
    )

    @Test
    fun `identical content is equal regardless of generatedAtEpochMs`() {
        val later = base.copy(generatedAtEpochMs = base.generatedAtEpochMs + 60_000L)

        assertTrue(base.hasSameContent(later))
    }

    @Test
    fun `a status change is not equal`() {
        val changed = base.copy(status = WidgetSnapshotStatus.EMPTY)

        assertFalse(base.hasSameContent(changed))
    }

    @Test
    fun `a task count change is not equal`() {
        val changed = base.copy(taskCount = base.taskCount + 1)

        assertFalse(base.hasSameContent(changed))
    }

    @Test
    fun `a day window change is not equal`() {
        val changed = base.copy(dayStartEpochMs = (base.dayStartEpochMs ?: 0L) + 1L)

        assertFalse(base.hasSameContent(changed))
    }

    @Test
    fun `a row reorder is not equal`() {
        val second = row.copy(id = "b", key = 2L, title = "Beta")
        val forward = base.copy(taskCount = 2, rows = listOf(row, second))
        val reordered = base.copy(taskCount = 2, rows = listOf(second, row))

        assertFalse(forward.hasSameContent(reordered))
    }

    @Test
    fun `a row title change is not equal`() {
        val changed = base.copy(rows = listOf(row.copy(title = "Renamed")))

        assertFalse(base.hasSameContent(changed))
    }

    @Test
    fun `an identical copy is equal`() {
        assertTrue(base.hasSameContent(base.copy()))
    }
}
