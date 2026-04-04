package com.ohmz.tday.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class CompletedTodosResponse(
    val completedTodos: List<CompletedTodoDto> = emptyList(),
)

@Serializable
data class CompletedTodoDto(
    val id: String,
    val originalTodoID: String? = null,
    val title: String,
    val description: String? = null,
    val priority: String = "Low",
    val due: String,
    val completedAt: String? = null,
    val completedOnTime: Boolean? = null,
    val daysToComplete: Double? = null,
    val rrule: String? = null,
    val userID: String? = null,
    val instanceDate: String? = null,
    val listName: String? = null,
    val listColor: String? = null,
)

@Serializable
data class UpdateCompletedTodoRequest(
    val id: String,
    val title: String? = null,
    val description: String? = null,
    val priority: String? = null,
    val due: String? = null,
    val rrule: String? = null,
    val listID: String? = null,
)

@Serializable
data class DeleteCompletedTodoRequest(
    val id: String,
)
