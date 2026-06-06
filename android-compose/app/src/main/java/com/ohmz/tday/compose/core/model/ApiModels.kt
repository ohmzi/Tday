package com.ohmz.tday.compose.core.model

import kotlinx.serialization.Serializable

typealias MessageResponse = com.ohmz.tday.shared.model.MessageResponse
typealias MobileProbeResponse = com.ohmz.tday.shared.model.MobileProbeResponse
typealias AppSettingsResponse = com.ohmz.tday.shared.model.AppSettingsResponse
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
typealias FloatersResponse = com.ohmz.tday.shared.model.FloatersResponse
typealias FloaterDto = com.ohmz.tday.shared.model.FloaterDto
typealias CreateFloaterRequest = com.ohmz.tday.shared.model.CreateFloaterRequest
typealias CreateFloaterResponse = com.ohmz.tday.shared.model.CreateFloaterResponse
typealias UpdateFloaterRequest = com.ohmz.tday.shared.model.UpdateFloaterRequest
typealias DeleteFloaterRequest = com.ohmz.tday.shared.model.DeleteFloaterRequest
typealias FloaterCompleteRequest = com.ohmz.tday.shared.model.FloaterCompleteRequest
typealias FloaterUncompleteRequest = com.ohmz.tday.shared.model.FloaterUncompleteRequest
typealias FloaterPrioritizeRequest = com.ohmz.tday.shared.model.FloaterPrioritizeRequest
typealias FloaterReorderRequest = com.ohmz.tday.shared.model.FloaterReorderRequest
typealias TodoInstanceUpdateRequest = com.ohmz.tday.shared.model.TodoInstancePatchRequest
typealias TodoInstanceDeleteRequest = com.ohmz.tday.shared.model.TodoInstanceDeleteRequest
typealias TodoCompleteRequest = com.ohmz.tday.shared.model.TodoCompleteRequest
typealias TodoUncompleteRequest = com.ohmz.tday.shared.model.TodoUncompleteRequest
typealias TodoPrioritizeRequest = com.ohmz.tday.shared.model.TodoPrioritizeRequest
typealias ListsResponse = com.ohmz.tday.shared.model.ListsResponse
typealias CreateListRequest = com.ohmz.tday.shared.model.CreateListRequest
typealias ListDto = com.ohmz.tday.shared.model.ListDto
typealias CreateListResponse = com.ohmz.tday.shared.model.CreateListResponse
typealias ListDetailResponse = com.ohmz.tday.shared.model.ListDetailResponse
typealias UpdateListRequest = com.ohmz.tday.shared.model.UpdateListRequest
typealias DeleteListRequest = com.ohmz.tday.shared.model.DeleteListRequest
typealias DeleteListResponse = com.ohmz.tday.shared.model.DeleteListResponse
typealias FloaterListsResponse = com.ohmz.tday.shared.model.FloaterListsResponse
typealias CreateFloaterListRequest = com.ohmz.tday.shared.model.CreateFloaterListRequest
typealias FloaterListDto = com.ohmz.tday.shared.model.FloaterListDto
typealias CreateFloaterListResponse = com.ohmz.tday.shared.model.CreateFloaterListResponse
typealias FloaterListDetailResponse = com.ohmz.tday.shared.model.FloaterListDetailResponse
typealias UpdateFloaterListRequest = com.ohmz.tday.shared.model.UpdateFloaterListRequest
typealias DeleteFloaterListRequest = com.ohmz.tday.shared.model.DeleteFloaterListRequest
typealias DeleteFloaterListResponse = com.ohmz.tday.shared.model.DeleteFloaterListResponse
typealias CompletedTodosResponse = com.ohmz.tday.shared.model.CompletedTodosResponse
typealias CompletedTodoDto = com.ohmz.tday.shared.model.CompletedTodoDto
typealias UpdateCompletedTodoRequest = com.ohmz.tday.shared.model.UpdateCompletedTodoRequest
typealias DeleteCompletedTodoRequest = com.ohmz.tday.shared.model.DeleteCompletedTodoRequest
typealias CompletedFloatersResponse = com.ohmz.tday.shared.model.CompletedFloatersResponse
typealias CompletedFloaterDto = com.ohmz.tday.shared.model.CompletedFloaterDto
typealias UpdateCompletedFloaterRequest = com.ohmz.tday.shared.model.UpdateCompletedFloaterRequest
typealias DeleteCompletedFloaterRequest = com.ohmz.tday.shared.model.DeleteCompletedFloaterRequest
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
    val username: String? = null,
    val image: String? = null,
    val timeZone: String? = null,
    val role: String? = null,
    val approvalStatus: String? = null,
    val requireSecurityQuestions: Boolean = false,
)

@Serializable
data class SecurityQuestion(
    val id: Int,
    val text: String,
)

@Serializable
data class SecurityAnswerInput(
    val questionId: Int,
    val answer: String,
)

@Serializable
data class SecurityQuestionsResponse(
    val questions: List<SecurityQuestion> = emptyList(),
)

@Serializable
data class SecurityQuestionStatusResponse(
    val questionIds: List<Int> = emptyList(),
    val requireSecurityQuestions: Boolean = false,
)

@Serializable
data class SelfServiceResetRequest(
    val username: String,
    val answers: List<SecurityAnswerInput> = emptyList(),
    val newPassword: String,
)

@Serializable
data class RequestAdminResetRequest(
    val username: String,
)

@Serializable
data class SetSecurityQuestionsRequest(
    val answers: List<SecurityAnswerInput> = emptyList(),
)

@Serializable
data class RegisterRequest(
    val fname: String,
    val lname: String? = null,
    val username: String,
    val password: String,
    val securityAnswers: List<SecurityAnswerInput>? = null,
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
data class CredentialsCallbackRequest(
    val username: String? = null,
    val password: String? = null,
    val encryptedPayload: String? = null,
    val encryptedKey: String? = null,
    val encryptedIv: String? = null,
    val credentialKeyId: String? = null,
    val credentialEnvelopeVersion: String? = null,
    val passwordProof: String? = null,
    val passwordProofChallengeId: String? = null,
    val passwordProofVersion: String? = null,
    val captchaToken: String? = null,
    val csrfToken: String? = null,
    val redirect: String? = null,
    val callbackUrl: String? = null,
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
