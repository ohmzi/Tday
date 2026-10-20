package com.ohmz.tday.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class FloatersResponse(
    val floaters: List<FloaterDto> = emptyList(),
)

@Serializable
data class FloaterDto(
    val id: String,
    val title: String = "",
    val description: String? = null,
    val pinned: Boolean = false,
    val priority: String = "Low",
    val completed: Boolean = false,
    val order: Int? = null,
    val listID: String? = null,
    val userID: String? = null,
    val updatedAt: String? = null,
    val createdAt: String? = null,
)

@Serializable
data class CreateFloaterRequest(
    val title: String,
    val description: String? = null,
    val priority: String = "Low",
    val listID: String? = null,
)

/**
 * Body of `POST /api/floater/{id}/promote` — schedules a floater into a real
 * Todo (the floater row is consumed). List membership does not carry across:
 * floater lists and todo lists are separate types.
 */
@Serializable
data class PromoteFloaterRequest(
    val due: String,
    val rrule: String? = null,
)

@Serializable
data class PromoteFloaterResponse(
    val message: String? = null,
    val todo: TodoDto? = null,
)

@Serializable
data class CreateFloaterResponse(
    val message: String? = null,
    val floater: FloaterDto? = null,
)

@Serializable
data class UpdateFloaterRequest(
    val id: String,
    val title: String? = null,
    val description: String? = null,
    val pinned: Boolean? = null,
    val priority: String? = null,
    val completed: Boolean? = null,
    val listID: String? = null,
)

@Serializable
data class DeleteFloaterRequest(
    val id: String,
)

@Serializable
data class FloaterCompleteRequest(
    val id: String,
)

@Serializable
data class FloaterUncompleteRequest(
    val id: String,
)

/**
 * Response of `PATCH /api/floater/uncomplete`.
 *
 * [listRecreated] tells a client whether [floater] landed back in the list it
 * was originally completed from ([listRecreated] = false, [listID] unchanged
 * from before) or in a fresh list Recreated because the original was deleted
 * in the meantime ([listRecreated] = true, [listID] is a NEW id -- clients
 * that cached the old list id should treat this as a different list, not the
 * same one reappearing). [listID]/[listName]/[listColor] are null when the
 * floater had no list either way.
 */
@Serializable
data class FloaterUncompleteResponse(
    val message: String? = null,
    val floater: FloaterDto? = null,
    val listRecreated: Boolean = false,
    val listID: String? = null,
    val listName: String? = null,
    val listColor: String? = null,
)

@Serializable
data class FloaterPrioritizeRequest(
    val id: String,
    val priority: String,
)

@Serializable
data class FloaterReorderRequest(
    val id: String,
    val order: Int,
)

@Serializable
data class CompletedFloatersResponse(
    val completedFloaters: List<CompletedFloaterDto> = emptyList(),
)

@Serializable
data class CompletedFloaterDto(
    val id: String,
    val originalFloaterID: String? = null,
    val title: String,
    val description: String? = null,
    val priority: String = "Low",
    val completedAt: String? = null,
    val daysToComplete: Double? = null,
    val userID: String? = null,
    val listID: String? = null,
    val listName: String? = null,
    val listColor: String? = null,
    /**
     * True when this item had a list at completion time (listName/listColor
     * are populated) but that list has since been deleted (listID is null
     * because of it, not because the floater was list-less to begin with).
     * A client can use this to warn before undo ("this will recreate
     * <listName>") instead of discovering it only from the uncomplete
     * response's listRecreated flag after the fact.
     */
    val listDeleted: Boolean = false,
)

@Serializable
data class UpdateCompletedFloaterRequest(
    val id: String,
    val title: String? = null,
    val description: String? = null,
    val priority: String? = null,
    val listID: String? = null,
)

@Serializable
data class DeleteCompletedFloaterRequest(
    val id: String,
)
