package com.ohmz.tday.compose.ui.priority

import androidx.annotation.StringRes
import com.ohmz.tday.compose.R

/**
 * The newest, least-urgent tier — wire value "Lowest" (UI label "Low"), added BELOW the
 * original [PRIORITY_NORMAL_VALUE] (wire "Low", UI label "Normal", still the default). See
 * shared `TaskSortEngine` for the matching rank split and `canonicalPriorityValue` below for
 * why an unrecognized string must still canonicalize to Normal, never to this tier.
 */
const val PRIORITY_LOWEST_VALUE = "Lowest"
const val PRIORITY_NORMAL_VALUE = "Low"
const val PRIORITY_IMPORTANT_VALUE = "Medium"
const val PRIORITY_URGENT_VALUE = "High"

/**
 * Low-to-high urgency — the display order every picker renders in, with the new [PRIORITY_LOWEST_VALUE]
 * tier leading, ahead of Normal. Mirrors iOS's `TaskPriorityDisplay.options` and web's picker order.
 */
val PRIORITY_OPTIONS_LOW_TO_HIGH: List<String> =
    listOf(PRIORITY_LOWEST_VALUE, PRIORITY_NORMAL_VALUE, PRIORITY_IMPORTANT_VALUE, PRIORITY_URGENT_VALUE)

fun canonicalPriorityValue(value: String?): String {
    return when (value?.trim()?.lowercase()) {
        "lowest" -> PRIORITY_LOWEST_VALUE
        "normal", "low" -> PRIORITY_NORMAL_VALUE
        "important", "medium" -> PRIORITY_IMPORTANT_VALUE
        "urgent", "high" -> PRIORITY_URGENT_VALUE
        else -> PRIORITY_NORMAL_VALUE
    }
}

@StringRes
fun priorityDisplayLabelRes(priority: String?): Int {
    return when (canonicalPriorityValue(priority)) {
        PRIORITY_LOWEST_VALUE -> R.string.create_task_priority_low
        PRIORITY_IMPORTANT_VALUE -> R.string.create_task_priority_important
        PRIORITY_URGENT_VALUE -> R.string.create_task_priority_urgent
        else -> R.string.create_task_priority_normal
    }
}

fun isUrgentPriority(priority: String?): Boolean =
    canonicalPriorityValue(priority) == PRIORITY_URGENT_VALUE

fun isImportantPriority(priority: String?): Boolean =
    canonicalPriorityValue(priority) == PRIORITY_IMPORTANT_VALUE

fun isLowestPriority(priority: String?): Boolean =
    canonicalPriorityValue(priority) == PRIORITY_LOWEST_VALUE
