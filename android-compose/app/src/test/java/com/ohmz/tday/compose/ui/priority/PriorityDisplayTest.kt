package com.ohmz.tday.compose.ui.priority

import com.ohmz.tday.compose.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PriorityDisplayTest {

    @Test
    fun `canonical value recognizes the new Lowest tier in every spelling`() {
        assertEquals(PRIORITY_LOWEST_VALUE, canonicalPriorityValue("Lowest"))
        assertEquals(PRIORITY_LOWEST_VALUE, canonicalPriorityValue("lowest"))
        assertEquals(PRIORITY_LOWEST_VALUE, canonicalPriorityValue("  LOWEST  "))
    }

    @Test
    fun `canonical value keeps existing wire values and their default unchanged`() {
        assertEquals(PRIORITY_NORMAL_VALUE, canonicalPriorityValue("Low"))
        assertEquals(PRIORITY_NORMAL_VALUE, canonicalPriorityValue("normal"))
        assertEquals(PRIORITY_IMPORTANT_VALUE, canonicalPriorityValue("Medium"))
        assertEquals(PRIORITY_IMPORTANT_VALUE, canonicalPriorityValue("important"))
        assertEquals(PRIORITY_URGENT_VALUE, canonicalPriorityValue("High"))
        assertEquals(PRIORITY_URGENT_VALUE, canonicalPriorityValue("urgent"))
    }

    @Test
    fun `an unrecognized string degrades to Normal, never to Lowest`() {
        // A garbage/unrecognized priority string must keep degrading to the pre-existing
        // default (Normal) exactly as it always has -- it must never silently sort/land in
        // the new bottom tier, which is reserved for a task someone genuinely tagged Lowest.
        assertEquals(PRIORITY_NORMAL_VALUE, canonicalPriorityValue(null))
        assertEquals(PRIORITY_NORMAL_VALUE, canonicalPriorityValue(""))
        assertEquals(PRIORITY_NORMAL_VALUE, canonicalPriorityValue("garbage"))
        assertEquals(PRIORITY_NORMAL_VALUE, canonicalPriorityValue("lowestt"))
    }

    @Test
    fun `isLowestPriority is true only for the new tier`() {
        assertTrue(isLowestPriority("Lowest"))
        assertTrue(isLowestPriority("lowest"))
        assertFalse(isLowestPriority("Low"))
        assertFalse(isLowestPriority("Medium"))
        assertFalse(isLowestPriority("High"))
        assertFalse(isLowestPriority(null))
        assertFalse(isLowestPriority("garbage"))
    }

    @Test
    fun `Lowest is not urgent or important`() {
        assertFalse(isUrgentPriority("Lowest"))
        assertFalse(isImportantPriority("Lowest"))
    }

    @Test
    fun `display label resource covers all four tiers`() {
        assertEquals(R.string.create_task_priority_low, priorityDisplayLabelRes("Lowest"))
        assertEquals(R.string.create_task_priority_normal, priorityDisplayLabelRes("Low"))
        assertEquals(R.string.create_task_priority_important, priorityDisplayLabelRes("Medium"))
        assertEquals(R.string.create_task_priority_urgent, priorityDisplayLabelRes("High"))
        // Unrecognized input falls back to the Normal label, matching canonicalPriorityValue.
        assertEquals(R.string.create_task_priority_normal, priorityDisplayLabelRes("garbage"))
    }

    @Test
    fun `picker options lead with the new Lowest tier, low to high urgency`() {
        assertEquals(
            listOf(
                PRIORITY_LOWEST_VALUE,
                PRIORITY_NORMAL_VALUE,
                PRIORITY_IMPORTANT_VALUE,
                PRIORITY_URGENT_VALUE,
            ),
            PRIORITY_OPTIONS_LOW_TO_HIGH,
        )
    }
}
