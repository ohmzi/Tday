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
