package com.ohmz.tday.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class ListsResponse(
    val lists: List<ListDto> = emptyList(),
)

@Serializable
data class CreateListRequest(
    val name: String,
    val color: String? = null,
    val iconKey: String? = null,
)

@Serializable
data class ListDto(
    val id: String,
    val name: String,
    val color: String? = null,
    val todoCount: Int = 0,
    val iconKey: String? = null,
    val userID: String? = null,
    val updatedAt: String? = null,
    val createdAt: String? = null,
)

@Serializable
data class CreateListResponse(
    val message: String? = null,
    val list: ListDto? = null,
)

@Serializable
data class UpdateListRequest(
    val id: String,
    val name: String? = null,
    val color: String? = null,
    val iconKey: String? = null,
)

@Serializable
data class DeleteListRequest(
    val id: String,
)

@Serializable
data class ListTodoDto(
    val id: String,
    val title: String,
    val priority: String,
    val due: String,
    val completed: Boolean,
    val order: Int,
)
