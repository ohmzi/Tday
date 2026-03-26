package com.ohmz.tday.models.response

import kotlinx.serialization.Serializable

@Serializable
data class ListResponse(
    val id: String,
    val name: String,
    val color: String?,
    val iconKey: String?,
    val userID: String,
    val createdAt: String,
    val updatedAt: String,
)

@Serializable
data class ListTodoResponse(
    val id: String,
    val title: String,
    val priority: String,
    val dtstart: String,
    val due: String,
    val completed: Boolean,
    val order: Int,
)
