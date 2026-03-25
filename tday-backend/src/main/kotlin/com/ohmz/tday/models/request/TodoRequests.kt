package com.ohmz.tday.models.request

import kotlinx.serialization.Serializable

@Serializable
data class TodoCreateRequest(
    val title: String,
    val description: String? = null,
    val priority: String = "Low",
    val dtstart: String,
    val due: String,
    val rrule: String? = null,
    val listID: String? = null,
)

@Serializable
data class TodoPatchRequest(
    val id: String,
    val title: String? = null,
    val description: String? = null,
    val priority: String? = null,
    val pinned: Boolean? = null,
    val completed: Boolean? = null,
    val dtstart: String? = null,
    val due: String? = null,
    val rrule: String? = null,
    val listID: String? = null,
    val dateChanged: Boolean? = null,
    val rruleChanged: Boolean? = null,
    val instanceDate: String? = null,
)

@Serializable
data class TodoDeleteRequest(val id: String)

@Serializable
data class TodoCompleteRequest(
    val id: String,
    val instanceDate: String? = null,
)

@Serializable
data class TodoPrioritizeRequest(val id: String, val priority: String)

@Serializable
data class TodoReorderRequest(val id: String, val order: Int)

@Serializable
data class TodoInstancePatchRequest(
    val todoId: String,
    val instanceDate: String,
    val title: String? = null,
    val description: String? = null,
    val priority: String? = null,
    val dtstart: String? = null,
    val due: String? = null,
    val durationMinutes: Int? = null,
)

@Serializable
data class TodoInstanceDeleteRequest(
    val todoId: String,
    val instanceDate: String,
)

@Serializable
data class TodoNlpRequest(
    val text: String,
    val locale: String? = null,
    val referenceEpochMs: Long? = null,
    val timezoneOffsetMinutes: Int? = null,
    val defaultDurationMinutes: Int? = null,
)

@Serializable
data class TodoSummaryRequest(
    val mode: String = "today",
    val timeZone: String? = null,
)
