package com.ohmz.tday.compose.feature.widget

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class TaskWidgetDesignTest {
    @Test
    fun `layout bucket maps compact wide medium and tall boundaries`() {
        assertEquals(TaskWidgetLayout.COMPACT, taskWidgetLayoutFor(DpSize(110.dp, 110.dp)))
        assertEquals(TaskWidgetLayout.COMPACT, taskWidgetLayoutFor(DpSize(219.dp, 139.dp)))

        assertEquals(TaskWidgetLayout.WIDE, taskWidgetLayoutFor(DpSize(220.dp, 110.dp)))
        assertEquals(TaskWidgetLayout.WIDE, taskWidgetLayoutFor(DpSize(360.dp, 139.dp)))

        assertEquals(TaskWidgetLayout.MEDIUM, taskWidgetLayoutFor(DpSize(110.dp, 140.dp)))
        assertEquals(TaskWidgetLayout.MEDIUM, taskWidgetLayoutFor(DpSize(360.dp, 207.dp)))

        assertEquals(TaskWidgetLayout.TALL, taskWidgetLayoutFor(DpSize(110.dp, 208.dp)))
        assertEquals(TaskWidgetLayout.TALL, taskWidgetLayoutFor(DpSize(360.dp, 360.dp)))
    }

    @Test
    fun `due time detail stays off cramped buckets`() {
        assertEquals(false, taskWidgetShowsTrailingText(TaskWidgetLayout.COMPACT))
        assertEquals(false, taskWidgetShowsTrailingText(TaskWidgetLayout.WIDE))
        assertEquals(true, taskWidgetShowsTrailingText(TaskWidgetLayout.MEDIUM))
        assertEquals(true, taskWidgetShowsTrailingText(TaskWidgetLayout.TALL))
    }

    @Test
    fun `scroll items place overflow marker before remaining rows`() {
        val rows = (0 until 5).map { row("task-$it") }
        val items = taskWidgetScrollItems(
            rows = rows,
            overflowCount = 0,
            visibleRowCapacity = 3,
        )

        assertEquals(
            listOf(
                "task:task-0",
                "task:task-1",
                "more:3",
                "task:task-2",
                "task:task-3",
                "task:task-4",
            ),
            items.map(::scrollItemLabel),
        )
    }

    @Test
    fun `scroll overflow marker counts all hidden tasks`() {
        val rows = (0 until 50).map { row("task-$it") }
        val items = taskWidgetScrollItems(
            rows = rows,
            overflowCount = 10,
            visibleRowCapacity = 5,
        )

        assertEquals("more:56", scrollItemLabel(items[4]))
    }

    @Test
    fun `scroll items include terminal overflow only for capped tasks`() {
        val uncappedItems = taskWidgetScrollItems(
            rows = (0 until 8).map { row("task-$it") },
            overflowCount = 0,
            visibleRowCapacity = 3,
        )
        val cappedItems = taskWidgetScrollItems(
            rows = (0 until 50).map { row("task-$it") },
            overflowCount = 10,
            visibleRowCapacity = 5,
        )

        assertEquals(false, uncappedItems.any { it is TaskWidgetScrollItem.TerminalOverflow })
        assertEquals("terminal:10", scrollItemLabel(cappedItems.last()))
    }

    private fun row(id: String) = TaskWidgetRow(
        key = id.hashCode().toLong(),
        title = id,
        priority = "Low",
    )

    private fun scrollItemLabel(item: TaskWidgetScrollItem): String {
        return when (item) {
            is TaskWidgetScrollItem.Task -> "task:${item.row.title}"
            is TaskWidgetScrollItem.OverflowMarker -> "more:${item.hiddenCount}"
            is TaskWidgetScrollItem.TerminalOverflow -> "terminal:${item.hiddenCount}"
        }
    }
}
