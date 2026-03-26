package com.ohmz.tday.models.response

import kotlinx.serialization.Serializable

@Serializable
data class CompletedTodoResponse(
    val id: String,
    val originalTodoID: String,
    val title: String,
    val description: String?,
    val priority: String,
    val completedAt: String,
    val dtstart: String,
    val due: String,
    val completedOnTime: Boolean,
    val daysToComplete: Double,
    val rrule: String?,
    val userID: String,
    val instanceDate: String?,
    val listName: String?,
    val listColor: String?,
)
