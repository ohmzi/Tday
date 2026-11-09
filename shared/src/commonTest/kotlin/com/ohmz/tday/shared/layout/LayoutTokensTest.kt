package com.ohmz.tday.shared.layout

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LayoutTokensTest {

    @Test
    fun theTokensAreOrderedAndInRange() {
        assertTrue(LayoutTokens.ContentGapMin < LayoutTokens.ContentGapMax)
        assertTrue(LayoutTokens.ContentStartRatio in 0.2..0.6)
        assertTrue(LayoutTokens.EmptySlotRatio in 0.2..0.6)
    }

    @Test
    fun theGapIsNeverBelowTheFadeBand() {
        // A title that ends below the shared line leaves nothing to spend; the floor holds.
        assertEquals(LayoutTokens.ContentGapMin, LayoutTokens.contentGap(screenHeight = 640.0, titleBottom = 400.0))
        assertEquals(LayoutTokens.ContentGapMin, LayoutTokens.contentGap(screenHeight = 667.0, titleBottom = 254.0))
    }

    @Test
    fun theGapIsCappedOnATallScreen() {
        assertEquals(LayoutTokens.ContentGapMax, LayoutTokens.contentGap(screenHeight = 1400.0, titleBottom = 280.0))
    }

    @Test
    fun theGapSpendsExactlyWhatReachesTheLine() {
        val gap = LayoutTokens.contentGap(screenHeight = 800.0, titleBottom = 250.0)
        assertEquals(LayoutTokens.ContentStartRatio * 800.0, 250.0 + gap, 1e-9)
    }

    /**
     * The point of the ratio: two clients whose fixed headers differ still start their
     * content on the same line. 874 is the logical height of the phone both were drawn
     * on. Android: status bar 48 + 64 bar + 162 hero block. iOS: safe area 62 + 56 bar
     * + 8 + 96 + 18 + 56 title box. The 12 between their `titleBottom`s is the 56-vs-40
     * title box plus the two insets, and it is the whole reason a fixed gap could not
     * line them up.
     */
    @Test
    fun androidAndIosStartTheirContentOnTheSameLine() {
        val height = 874.0
        val androidStart = androidTitleBottom + LayoutTokens.contentGap(height, androidTitleBottom)
        val iosStart = iosTitleBottom + LayoutTokens.contentGap(height, iosTitleBottom)
        assertTrue(abs(androidStart - iosStart) < 1.0, "android $androidStart vs ios $iosStart")
        assertEquals(332.0, iosStart, 1.0)
    }

    @Test
    fun theEmptySlotScalesWithTheScreen() {
        assertEquals(LayoutTokens.EmptySlotRatio * 800.0, LayoutTokens.emptySlotHeight(800.0), 1e-9)
    }

    private companion object {
        const val androidTitleBottom = 48.0 + 64.0 + 162.0
        const val iosTitleBottom = 62.0 + 56.0 + 8.0 + 96.0 + 18.0 + 56.0
    }
}
