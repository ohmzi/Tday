package com.ohmz.tday.models.response

import kotlinx.serialization.Serializable

@Serializable
data class TodoResponse(
    val id: String,
    val title: String,
    val description: String?,
    val priority: String,
    val dtstart: String,
    val due: String,
    val durationMinutes: Int,
    val rrule: String?,
    val timeZone: String,
    val completed: Boolean,
    val pinned: Boolean,
    val order: Int,
    val listID: String?,
    val userID: String,
    val createdAt: String,
    val updatedAt: String,
)

@Serializable
data class NlpParseResponse(
    val cleanTitle: String,
    val matchedText: String?,
    val matchStart: Int?,
    val startEpochMs: Long?,
    val dueEpochMs: Long?,
)
