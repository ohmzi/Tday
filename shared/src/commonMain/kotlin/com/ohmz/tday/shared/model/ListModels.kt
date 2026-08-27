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
    // Sharing metadata. myRole is null on responses from servers that predate
    // sharing; treat null as OWNER.
    val myRole: String? = null,
    val isShared: Boolean = false,
    val memberCount: Int = 0,
    val ownerUsername: String? = null,
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
    val description: String? = null,
    val priority: String,
    val due: String? = null,
    val completed: Boolean,
    val order: Int,
    /**
     * The recurrence rule, or null for a one-off.
     *
     * Load-bearing for bulk selection, not cosmetic: a client decides whether a
     * row is a repeating series by `rrule != null`, and delete / priority / move
     * have no per-occurrence route, so they must never touch one
     * (`BulkSelectionPolicy.appliesToRecurring`). While this field was missing
     * from the payload, every row on a list screen looked one-off, the guard
     * evaluated to "nothing here repeats", and a bulk delete took out whole
     * series. A guard that silently sees no recurrence is worse than no guard.
     */
    val rrule: String? = null,
    /**
     * The list this todo is assigned to. Always this endpoint's own list id, but
     * sent so a row carries its own source instead of the screen having to
     * remember it for them.
     */
    val listID: String? = null,
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
    val reusable: Boolean = false,
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
    /** A reusable list can be Reset (all its floaters un-completed) to run again. */
    val reusable: Boolean = false,
    // Sharing metadata. myRole is null on responses from servers that predate
    // sharing; treat null as OWNER.
    val myRole: String? = null,
    val isShared: Boolean = false,
    val memberCount: Int = 0,
    val ownerUsername: String? = null,
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
    val reusable: Boolean? = null,
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
    val description: String? = null,
    val priority: String,
    val completed: Boolean,
    val order: Int,
)
