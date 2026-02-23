package com.ohmz.tday.compose.core.model

import kotlinx.serialization.Serializable

@Serializable
data class MessageResponse(
    val message: String? = null,
)

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
data class MobileProbeResponse(
    val service: String,
    val auth: MobileProbeAuthContract,
    val version: String,
    val serverTime: String,
)

@Serializable
data class MobileProbeAuthContract(
    val csrfPath: String,
    val credentialsPath: String,
    val registerPath: String,
)

@Serializable
data class AppSettingsResponse(
    val aiSummaryEnabled: Boolean = true,
)

@Serializable
data class AdminSettingsResponse(
    val aiSummaryEnabled: Boolean = true,
    val updatedAt: String? = null,
)

@Serializable
data class UpdateAdminSettingsRequest(
    val aiSummaryEnabled: Boolean,
)

@Serializable
data class TodosResponse(
    val todos: List<TodoDto> = emptyList(),
)

@Serializable
data class TodoSummaryRequest(
    val mode: String,
    val listId: String? = null,
)

@Serializable
data class TodoSummaryResponse(
    val summary: String,
    val source: String? = null,
    val mode: String? = null,
    val taskCount: Int? = null,
    val generatedAt: String? = null,
    val fallbackReason: String? = null,
)

@Serializable
data class TodoTitleNlpRequest(
    val text: String,
    val locale: String? = null,
    val referenceEpochMs: Long? = null,
    val timezoneOffsetMinutes: Int? = null,
    val defaultDurationMinutes: Int? = null,
)

@Serializable
data class TodoTitleNlpResponse(
    val cleanTitle: String,
    val matchedText: String? = null,
    val matchStart: Int? = null,
    val startEpochMs: Long? = null,
    val dueEpochMs: Long? = null,
)

@Serializable
data class CreateTodoRequest(
    val title: String,
    val description: String? = null,
    val priority: String = "Low",
    val dtstart: String,
    val due: String,
    val rrule: String? = null,
    val listID: String? = null,
)

@Serializable
data class TodoDto(
    val id: String,
    val title: String = "",
    val description: String? = null,
    val pinned: Boolean = false,
    val priority: String = "Low",
    val dtstart: String,
    val due: String,
    val rrule: String? = null,
    val instanceDate: String? = null,
    val completed: Boolean = false,
    val listID: String? = null,
    val updatedAt: String? = null,
    val createdAt: String? = null,
)

@Serializable
data class CreateTodoResponse(
    val message: String? = null,
    val todo: TodoDto? = null,
)

@Serializable
data class UpdateTodoRequest(
    val id: String,
    val title: String? = null,
    val description: String? = null,
    val pinned: Boolean? = null,
    val priority: String? = null,
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
data class DeleteTodoRequest(
    val id: String,
    val instanceDate: Long? = null,
)

@Serializable
data class TodoInstanceUpdateRequest(
    val id: String,
    val title: String? = null,
    val description: String? = null,
    val priority: String? = null,
    val dtstart: String? = null,
    val due: String? = null,
    val rrule: String? = null,
    val instanceDate: String,
)

@Serializable
data class TodoCompleteRequest(
    val id: String,
    val instanceDate: Long? = null,
)

@Serializable
data class TodoUncompleteRequest(
    val id: String,
    val instanceDate: Long? = null,
)

@Serializable
data class TodoPrioritizeRequest(
    val id: String,
    val priority: String,
    val instanceDate: Long? = null,
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
data class NotesResponse(
    val notes: List<NoteDto> = emptyList(),
)

@Serializable
data class NoteDto(
    val id: String,
    val name: String,
    val content: String? = null,
    val createdAt: String? = null,
)

@Serializable
data class CreateNoteRequest(
    val name: String,
    val content: String? = null,
)

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
    val dtstart: String,
    val due: String,
    val completedAt: String? = null,
    val rrule: String? = null,
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
    val dtstart: String? = null,
    val due: String? = null,
    val rrule: String? = null,
    val listID: String? = null,
)

@Serializable
data class DeleteCompletedTodoRequest(
    val id: String,
)

@Serializable
data class PreferencesResponse(
    val userPreferences: PreferencesDto? = null,
)

@Serializable
data class PreferencesDto(
    val sortBy: String? = null,
    val groupBy: String? = null,
    val direction: String? = null,
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
