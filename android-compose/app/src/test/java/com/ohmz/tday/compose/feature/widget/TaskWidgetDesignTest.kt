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
}
