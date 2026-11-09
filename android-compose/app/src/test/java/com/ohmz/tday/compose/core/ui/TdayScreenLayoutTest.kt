package com.ohmz.tday.compose.core.ui

import androidx.compose.ui.unit.dp
import com.ohmz.tday.shared.layout.LayoutTokens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TdayScreenLayoutTest {

    /**
     * The phone both clients were drawn on is 874 tall. Content must start on the
     * shared line there — 0.38 of the screen — with the status bar at 48.
     */
    @Test
    fun contentStartsOnTheSharedLineOnTheReferencePhone() {
        val m = TdayHeroTitleMetrics
        val statusBar = 48.dp
        val gap = tdayContentGapFor(screenHeight = 874.dp, statusBarTop = statusBar)

        val start = statusBar + m.ToolbarHeight + m.HeroHeight + gap
        assertEquals(LayoutTokens.ContentStartRatio * 874.0, start.value.toDouble(), 0.5)
    }

    @Test
    fun theGapNeverGoesUnderTheFadeBand() {
        val gap = tdayContentGapFor(screenHeight = 560.dp, statusBarTop = 24.dp)
        assertEquals(LayoutTokens.ContentGapMin, gap.value.toDouble(), 1e-6)
        assertTrue(gap > TdayHeroTitleMetrics.ContentFadeHeight)
    }

    @Test
    fun theGapIsCappedOnATallScreen() {
        val gap = tdayContentGapFor(screenHeight = 1400.dp, statusBarTop = 48.dp)
        assertEquals(LayoutTokens.ContentGapMax, gap.value.toDouble(), 1e-6)
    }
}
