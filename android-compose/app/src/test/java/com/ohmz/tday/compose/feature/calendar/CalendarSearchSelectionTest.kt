package com.ohmz.tday.compose.feature.calendar

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarSearchSelectionTest {

    @Test
    fun dayTapClosesSearch() {
        assertTrue(closesSearchOn(DateSelectionSource.DayTap))
    }

    @Test
    fun pagerSettleKeepsSearchOpen() {
        assertFalse(closesSearchOn(DateSelectionSource.PagerSettle))
    }

}
