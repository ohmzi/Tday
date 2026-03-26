package com.ohmz.tday.compose.core.model

import kotlinx.serialization.Serializable

typealias MessageResponse = com.ohmz.tday.shared.model.MessageResponse
typealias MobileProbeResponse = com.ohmz.tday.shared.model.MobileProbeResponse
typealias AppSettingsResponse = com.ohmz.tday.shared.model.AppSettingsResponse
typealias AdminSettingsResponse = com.ohmz.tday.shared.model.AdminSettingsResponse
typealias UpdateAdminSettingsRequest = com.ohmz.tday.shared.model.UpdateAdminSettingsRequest
typealias TodosResponse = com.ohmz.tday.shared.model.TodosResponse
typealias TodoSummaryRequest = com.ohmz.tday.shared.model.TodoSummaryRequest
typealias TodoSummaryResponse = com.ohmz.tday.shared.model.TodoSummaryResponse
typealias TodoTitleNlpRequest = com.ohmz.tday.shared.model.TodoTitleNlpRequest
typealias TodoTitleNlpResponse = com.ohmz.tday.shared.model.TodoTitleNlpResponse
typealias CreateTodoRequest = com.ohmz.tday.shared.model.CreateTodoRequest
typealias TodoDto = com.ohmz.tday.shared.model.TodoDto
typealias CreateTodoResponse = com.ohmz.tday.shared.model.CreateTodoResponse
typealias UpdateTodoRequest = com.ohmz.tday.shared.model.UpdateTodoRequest
typealias DeleteTodoRequest = com.ohmz.tday.shared.model.DeleteTodoRequest
typealias TodoInstanceUpdateRequest = com.ohmz.tday.shared.model.TodoInstancePatchRequest
typealias TodoInstanceDeleteRequest = com.ohmz.tday.shared.model.TodoInstanceDeleteRequest
typealias TodoCompleteRequest = com.ohmz.tday.shared.model.TodoCompleteRequest
typealias TodoUncompleteRequest = com.ohmz.tday.shared.model.TodoUncompleteRequest
typealias TodoPrioritizeRequest = com.ohmz.tday.shared.model.TodoPrioritizeRequest
typealias ListsResponse = com.ohmz.tday.shared.model.ListsResponse
typealias CreateListRequest = com.ohmz.tday.shared.model.CreateListRequest
typealias ListDto = com.ohmz.tday.shared.model.ListDto
typealias CreateListResponse = com.ohmz.tday.shared.model.CreateListResponse
typealias UpdateListRequest = com.ohmz.tday.shared.model.UpdateListRequest
typealias DeleteListRequest = com.ohmz.tday.shared.model.DeleteListRequest
typealias CompletedTodosResponse = com.ohmz.tday.shared.model.CompletedTodosResponse
typealias CompletedTodoDto = com.ohmz.tday.shared.model.CompletedTodoDto
typealias UpdateCompletedTodoRequest = com.ohmz.tday.shared.model.UpdateCompletedTodoRequest
typealias DeleteCompletedTodoRequest = com.ohmz.tday.shared.model.DeleteCompletedTodoRequest
typealias PreferencesResponse = com.ohmz.tday.shared.model.PreferencesResponse
typealias PreferencesDto = com.ohmz.tday.shared.model.PreferencesDto

@Serializable
data class CsrfResponse(
    val csrfToken: String,
)

@Serializable
data class AuthSession(
    val user: SessionUser? = null,
    val expires: String? = null,
)

@Serializable
data class SessionUser(
    val id: String? = null,
    val name: String? = null,
    val email: String? = null,
    val image: String? = null,
    val timeZone: String? = null,
    val role: String? = null,
    val approvalStatus: String? = null,
)

@Serializable
data class RegisterRequest(
    val fname: String,
    val lname: String? = null,
    val email: String,
    val password: String,
)

@Serializable
data class RegisterResponse(
    val message: String? = null,
    val requiresApproval: Boolean = false,
    val isBootstrapAdmin: Boolean = false,
)

@Serializable
data class CredentialKeyResponse(
    val version: String,
    val algorithm: String,
    val keyId: String,
    val publicKey: String,
)

@Serializable
data class TodoInstanceRequest(
    val instanceDate: String? = null,
)

@Serializable
data class ReorderItemRequest(
    val id: String,
    val order: Int,
)

@Serializable
data class UserResponse(
    val message: String? = null,
    val queriedUser: QueriedUser? = null,
)

@Serializable
data class QueriedUser(
    val maxStorage: String? = null,
    val usedStoraged: String? = null,
    val enableEncryption: Boolean = true,
    val protectedSymmetricKey: String? = null,
)

@Serializable
data class UpdateProfileRequest(
    val name: String,
)

@Serializable
data class ChangePasswordRequest(
    val currentPassword: String,
    val newPassword: String,
)
