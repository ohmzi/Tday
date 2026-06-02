package com.ohmz.tday.compose.feature.widget

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.ohmz.tday.compose.R
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
    fun `priority dot resource follows task priority`() {
        assertEquals(R.drawable.widget_priority_dot_high, taskWidgetPriorityDotResource("High"))
        assertEquals(R.drawable.widget_priority_dot_high, taskWidgetPriorityDotResource("urgent"))
        assertEquals(
            R.drawable.widget_priority_dot_medium,
            taskWidgetPriorityDotResource(" Important ")
        )
        assertEquals(R.drawable.widget_priority_dot_medium, taskWidgetPriorityDotResource("Medium"))
        assertEquals(R.drawable.widget_priority_dot_low, taskWidgetPriorityDotResource("Low"))
        assertEquals(R.drawable.widget_priority_dot_low, taskWidgetPriorityDotResource("unknown"))
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
