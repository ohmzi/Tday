package com.ohmz.tday.compose.feature.widget

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.ohmz.tday.compose.R
import com.ohmz.tday.compose.feature.widget.snapshot.WidgetPriorityRing
import com.ohmz.tday.compose.ui.priority.PRIORITY_LOWEST_VALUE
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
    fun `a two-column widget drops the due pill and header extras whatever its height`() {
        // A 2x2 on a tall-celled launcher reports ~160x160: MEDIUM by height, but too thin for a
        // pill beside a title or a date and ring beside the header.
        val twoColumn = taskWidgetShapeFor(DpSize(160.dp, 160.dp))
        assertEquals(TaskWidgetShape(TaskWidgetLayout.MEDIUM, narrow = true), twoColumn)
        assertEquals(false, taskWidgetShowsTrailingText(twoColumn))

        val threeColumn = taskWidgetShapeFor(DpSize(250.dp, 160.dp))
        assertEquals(TaskWidgetShape(TaskWidgetLayout.MEDIUM, narrow = false), threeColumn)
        assertEquals(true, taskWidgetShowsTrailingText(threeColumn))
    }

    @Test
    fun `priority check ring resource follows task priority`() {
        assertEquals(R.drawable.widget_priority_ring_high, taskWidgetPriorityRingResource("High"))
        assertEquals(R.drawable.widget_priority_ring_high, taskWidgetPriorityRingResource("urgent"))
        assertEquals(
            R.drawable.widget_priority_ring_medium,
            taskWidgetPriorityRingResource(" Important ")
        )
        assertEquals(R.drawable.widget_priority_ring_medium, taskWidgetPriorityRingResource("Medium"))
        assertEquals(R.drawable.widget_priority_ring_low, taskWidgetPriorityRingResource("Low"))
        assertEquals(R.drawable.widget_priority_ring_lowest, taskWidgetPriorityRingResource("Lowest"))
        assertEquals(R.drawable.widget_priority_ring_lowest, taskWidgetPriorityRingResource(" lowest "))
        assertEquals(R.drawable.widget_priority_ring_low, taskWidgetPriorityRingResource("unknown"))
    }

    @Test
    fun `widget priority ring round-trips through its wire value`() {
        assertEquals(PRIORITY_LOWEST_VALUE, WidgetPriorityRing.LOWEST.toPriorityValue())
        assertEquals(
            R.drawable.widget_priority_ring_lowest,
            taskWidgetPriorityRingResource(WidgetPriorityRing.LOWEST.toPriorityValue()),
        )
    }

    @Test
    fun `today watermark uses app day and night boundary`() {
        assertEquals(false, taskWidgetIsDaytime(5))
        assertEquals(true, taskWidgetIsDaytime(6))
        assertEquals(true, taskWidgetIsDaytime(17))
        assertEquals(false, taskWidgetIsDaytime(18))
        assertEquals(false, taskWidgetIsDaytime(23))
    }
}
