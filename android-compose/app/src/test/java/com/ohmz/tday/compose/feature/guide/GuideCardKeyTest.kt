package com.ohmz.tday.compose.feature.guide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class GuideCardKeyTest {
    @Test
    fun `a deep link opens the section card only, never the What's New copy of the same topic`() {
        // `expandedId` starts as the deep link's plain topic id, so only the card whose key is the
        // plain id opens.
        val deepLinkedId = "crash-reports"

        assertEquals(deepLinkedId, guideCardKey(deepLinkedId, inWhatsNew = false))
        assertNotEquals(deepLinkedId, guideCardKey(deepLinkedId, inWhatsNew = true))
    }
}
