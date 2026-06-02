package com.ohmz.tday.compose.ui.priority

import androidx.annotation.StringRes
import com.ohmz.tday.compose.R

const val PRIORITY_NORMAL_VALUE = "Low"
const val PRIORITY_IMPORTANT_VALUE = "Medium"
const val PRIORITY_URGENT_VALUE = "High"

const val PRIORITY_NORMAL_LABEL = "Normal"
const val PRIORITY_IMPORTANT_LABEL = "Important"
const val PRIORITY_URGENT_LABEL = "Urgent"

fun canonicalPriorityValue(value: String?): String {
    return when (value?.trim()?.lowercase()) {
        "normal", "low" -> PRIORITY_NORMAL_VALUE
        "important", "medium" -> PRIORITY_IMPORTANT_VALUE
        "urgent", "high" -> PRIORITY_URGENT_VALUE
        else -> PRIORITY_NORMAL_VALUE
    }
}

fun priorityDisplayLabel(priority: String?): String {
    return when (canonicalPriorityValue(priority)) {
        PRIORITY_IMPORTANT_VALUE -> PRIORITY_IMPORTANT_LABEL
        PRIORITY_URGENT_VALUE -> PRIORITY_URGENT_LABEL
        else -> PRIORITY_NORMAL_LABEL
    }
}

@StringRes
fun priorityDisplayLabelRes(priority: String?): Int {
    return when (canonicalPriorityValue(priority)) {
        PRIORITY_IMPORTANT_VALUE -> R.string.create_task_priority_important
        PRIORITY_URGENT_VALUE -> R.string.create_task_priority_urgent
        else -> R.string.create_task_priority_normal
    }
}

fun isUrgentPriority(priority: String?): Boolean =
    canonicalPriorityValue(priority) == PRIORITY_URGENT_VALUE

fun isImportantPriority(priority: String?): Boolean =
    canonicalPriorityValue(priority) == PRIORITY_IMPORTANT_VALUE
