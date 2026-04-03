import Foundation

struct MessageResponse: Codable {
    let message: String?
}

struct CsrfResponse: Codable {
    let csrfToken: String
}

struct AuthSession: Codable {
    let user: SessionUser?
    let expires: String?
}

struct SessionUser: Codable, Equatable, Hashable {
    let id: String?
    let name: String?
    let email: String?
    let image: String?
    let timeZone: String?
    let role: String?
    let approvalStatus: String?
}

struct RegisterRequest: Codable {
    let fname: String
    let lname: String?
    let email: String
    let password: String
}

struct RegisterResponse: Codable {
    let message: String?
    let requiresApproval: Bool
    let isBootstrapAdmin: Bool
}

struct MobileProbeResponse: Codable, Equatable {
    let service: String
    let probe: String?
    let version: String
    let serverTime: String
}

struct CredentialKeyResponse: Codable {
    let version: String
    let algorithm: String
    let keyId: String
    let publicKey: String
}

struct AppSettingsResponse: Codable, Equatable {
    let aiSummaryEnabled: Bool
}

struct AdminSettingsResponse: Codable, Equatable {
    let aiSummaryEnabled: Bool
    let validationError: String?
}

struct UpdateAdminSettingsRequest: Codable {
    let aiSummaryEnabled: Bool
}

struct TodosResponse: Codable {
    let todos: [TodoDTO]
}

struct TodoSummaryRequest: Codable {
    let mode: String
    let listId: String?
}

struct TodoSummaryResponse: Codable, Equatable {
    let summary: String
    let source: String?
    let mode: String?
    let taskCount: Int?
    let generatedAt: String?
    let fallbackReason: String?
}

struct TodoTitleNlpRequest: Codable {
    let text: String
    let locale: String?
    let referenceEpochMs: Int64?
    let timezoneOffsetMinutes: Int?
    let defaultDurationMinutes: Int?
}

struct TodoTitleNlpResponse: Codable, Equatable {
    let cleanTitle: String
    let matchedText: String?
    let matchStart: Int?
    let startEpochMs: Int64?
    let dueEpochMs: Int64?
}

struct CreateTodoRequest: Codable {
    let title: String
    let description: String?
    let priority: String
    let dtstart: String
    let due: String
    let rrule: String?
    let listID: String?
}

struct TodoDTO: Codable, Equatable {
    let id: String
    let title: String
    let description: String?
    let pinned: Bool
    let priority: String
    let dtstart: String
    let due: String
    let rrule: String?
    let instanceDate: String?
    let completed: Bool
    let listID: String?
    let updatedAt: String?
    let createdAt: String?
}

struct CreateTodoResponse: Codable {
    let message: String?
    let todo: TodoDTO?
}

struct UpdateTodoRequest: Codable {
    let id: String
    let title: String?
    let description: String?
    let pinned: Bool?
    let priority: String?
    let completed: Bool?
    let dtstart: String?
    let due: String?
    let rrule: String?
    let listID: String?
    let dateChanged: Bool?
    let rruleChanged: Bool?
    let instanceDate: String?
}

struct DeleteTodoRequest: Codable {
    let id: String
    let instanceDate: Int64?
}

struct TodoInstanceUpdateRequest: Codable {
    let todoId: String
    let title: String?
    let description: String?
    let priority: String?
    let dtstart: String?
    let due: String?
    let rrule: String?
    let instanceDate: String
    let durationMinutes: Int?
}

struct TodoCompleteRequest: Codable {
    let id: String
    let instanceDate: Int64?
}

struct TodoUncompleteRequest: Codable {
    let id: String
    let instanceDate: Int64?
}

struct TodoPrioritizeRequest: Codable {
    let id: String
    let priority: String
    let instanceDate: Int64?
}

struct ReorderItemRequest: Codable {
    let id: String
    let order: Int
}

struct ListsResponse: Codable {
    let lists: [ListDTO]
}

struct CreateListRequest: Codable {
    let name: String
    let color: String?
    let iconKey: String?
}

struct ListDTO: Codable, Equatable {
    let id: String
    let name: String
    let color: String?
    let todoCount: Int
    let iconKey: String?
    let updatedAt: String?
    let createdAt: String?
}

struct CreateListResponse: Codable {
    let message: String?
    let list: ListDTO?
}

struct UpdateListRequest: Codable {
    let id: String
    let name: String?
    let color: String?
    let iconKey: String?
}

struct DeleteListRequest: Codable {
    let id: String
}

struct CompletedTodosResponse: Codable {
    let completedTodos: [CompletedTodoDTO]
}

struct CompletedTodoDTO: Codable, Equatable {
    let id: String
    let originalTodoID: String?
    let title: String
    let description: String?
    let priority: String
    let dtstart: String
    let due: String
    let completedAt: String?
    let rrule: String?
    let instanceDate: String?
    let listName: String?
    let listColor: String?
}

struct UpdateCompletedTodoRequest: Codable {
    let id: String
    let title: String?
    let description: String?
    let priority: String?
    let dtstart: String?
    let due: String?
    let rrule: String?
    let listID: String?
}

struct DeleteCompletedTodoRequest: Codable {
    let id: String
}

struct PreferencesResponse: Codable {
    let preferences: PreferencesDTO?
}

struct PreferencesDTO: Codable, Equatable {
    let direction: String?
    let sortBy: String?
    let groupBy: String?
    let rrule: String?
}

struct UserResponse: Codable, Equatable {
    let user: SessionUser
}

struct UpdateProfileRequest: Codable {
    let fname: String?
    let lname: String?
    let email: String?
    let image: String?
    let timeZone: String?
}

struct ChangePasswordRequest: Codable {
    let currentPassword: String
    let newPassword: String
}

struct AuthRedirectResponse: Codable {
    let url: String?
}

typealias TodoDto = TodoDTO
typealias ListDto = ListDTO
typealias CompletedTodoDto = CompletedTodoDTO
typealias PreferencesDto = PreferencesDTO
typealias AuthCallbackResponse = AuthRedirectResponse

extension UpdateProfileRequest {
    init(name: String) {
        self.init(
            fname: name,
            lname: nil,
            email: nil,
            image: nil,
            timeZone: nil
        )
    }
}
