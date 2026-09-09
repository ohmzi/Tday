package com.ohmz.tday.compose.ui.priority

import com.ohmz.tday.compose.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val LOWEST_WIRE_MIXED_CASE = "Lowest"
private const val LOWEST_WIRE_LOWERCASE = "lowest"
private const val NORMAL_WIRE = "Low"
private const val IMPORTANT_WIRE = "Medium"
private const val URGENT_WIRE = "High"
private const val GARBAGE_INPUT = "garbage"

class PriorityDisplayTest {

    @Test
    fun `canonical value recognizes the new Lowest tier in every spelling`() {
        assertEquals(PRIORITY_LOWEST_VALUE, canonicalPriorityValue(LOWEST_WIRE_MIXED_CASE))
        assertEquals(PRIORITY_LOWEST_VALUE, canonicalPriorityValue(LOWEST_WIRE_LOWERCASE))
        assertEquals(PRIORITY_LOWEST_VALUE, canonicalPriorityValue("  LOWEST  "))
    }

    @Test
    fun `canonical value keeps existing wire values and their default unchanged`() {
        assertEquals(PRIORITY_NORMAL_VALUE, canonicalPriorityValue(NORMAL_WIRE))
        assertEquals(PRIORITY_NORMAL_VALUE, canonicalPriorityValue("normal"))
        assertEquals(PRIORITY_IMPORTANT_VALUE, canonicalPriorityValue(IMPORTANT_WIRE))
        assertEquals(PRIORITY_IMPORTANT_VALUE, canonicalPriorityValue("important"))
        assertEquals(PRIORITY_URGENT_VALUE, canonicalPriorityValue(URGENT_WIRE))
        assertEquals(PRIORITY_URGENT_VALUE, canonicalPriorityValue("urgent"))
    }

    @Test
    fun `an unrecognized string degrades to Normal, never to Lowest`() {
        // A garbage/unrecognized priority string must keep degrading to the pre-existing
        // default (Normal) exactly as it always has -- it must never silently sort/land in
        // the new bottom tier, which is reserved for a task someone genuinely tagged Lowest.
        assertEquals(PRIORITY_NORMAL_VALUE, canonicalPriorityValue(null))
        assertEquals(PRIORITY_NORMAL_VALUE, canonicalPriorityValue(""))
        assertEquals(PRIORITY_NORMAL_VALUE, canonicalPriorityValue(GARBAGE_INPUT))
        assertEquals(PRIORITY_NORMAL_VALUE, canonicalPriorityValue("${LOWEST_WIRE_LOWERCASE}t"))
    }

    @Test
    fun `isLowestPriority is true only for the new tier`() {
        assertTrue(isLowestPriority(LOWEST_WIRE_MIXED_CASE))
        assertTrue(isLowestPriority(LOWEST_WIRE_LOWERCASE))
        assertFalse(isLowestPriority(NORMAL_WIRE))
        assertFalse(isLowestPriority(IMPORTANT_WIRE))
        assertFalse(isLowestPriority(URGENT_WIRE))
        assertFalse(isLowestPriority(null))
        assertFalse(isLowestPriority(GARBAGE_INPUT))
    }

    @Test
    fun `Lowest is not urgent or important`() {
        assertFalse(isUrgentPriority(LOWEST_WIRE_MIXED_CASE))
        assertFalse(isImportantPriority(LOWEST_WIRE_MIXED_CASE))
    }

    @Test
    fun `display label resource covers all four tiers`() {
        assertEquals(R.string.create_task_priority_low, priorityDisplayLabelRes(LOWEST_WIRE_MIXED_CASE))
        assertEquals(R.string.create_task_priority_normal, priorityDisplayLabelRes(NORMAL_WIRE))
        assertEquals(R.string.create_task_priority_important, priorityDisplayLabelRes(IMPORTANT_WIRE))
        assertEquals(R.string.create_task_priority_urgent, priorityDisplayLabelRes(URGENT_WIRE))
        // Unrecognized input falls back to the Normal label, matching canonicalPriorityValue.
        assertEquals(R.string.create_task_priority_normal, priorityDisplayLabelRes(GARBAGE_INPUT))
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
