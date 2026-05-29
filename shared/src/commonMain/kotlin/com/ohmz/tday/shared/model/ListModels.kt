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
data class ListDetailResponse(
    val list: ListDto,
    val todos: List<ListTodoDto> = emptyList(),
)

@Serializable
data class DeleteListResponse(
    val message: String? = null,
    val deletedIds: List<String> = emptyList(),
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
    val id: String? = null,
    val ids: List<String> = emptyList(),
)

@Serializable
data class ListTodoDto(
    val id: String,
    val title: String,
    val priority: String,
    val due: String? = null,
    val completed: Boolean,
    val order: Int,
)

@Serializable
data class FloaterListsResponse(
    val lists: List<FloaterListDto> = emptyList(),
)

@Serializable
data class CreateFloaterListRequest(
    val name: String,
    val color: String? = null,
    val iconKey: String? = null,
)

@Serializable
data class FloaterListDto(
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
data class CreateFloaterListResponse(
    val message: String? = null,
    val list: FloaterListDto? = null,
)

@Serializable
data class FloaterListDetailResponse(
    val list: FloaterListDto,
    val floaters: List<FloaterListTodoDto> = emptyList(),
)

@Serializable
data class UpdateFloaterListRequest(
    val id: String,
    val name: String? = null,
    val color: String? = null,
    val iconKey: String? = null,
)

@Serializable
data class DeleteFloaterListRequest(
    val id: String? = null,
    val ids: List<String> = emptyList(),
)

@Serializable
data class DeleteFloaterListResponse(
    val message: String? = null,
    val deletedIds: List<String> = emptyList(),
)

@Serializable
data class FloaterListTodoDto(
    val id: String,
    val title: String,
    val priority: String,
    val completed: Boolean,
    val order: Int,
)
