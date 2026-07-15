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
    let username: String?
    let image: String?
    let timeZone: String?
    let role: String?
    let approvalStatus: String?
    let requireSecurityQuestions: Bool

    init(
        id: String?,
        name: String?,
        username: String?,
        image: String?,
        timeZone: String?,
        role: String?,
        approvalStatus: String?,
        requireSecurityQuestions: Bool = false
    ) {
        self.id = id
        self.name = name
        self.username = username
        self.image = image
        self.timeZone = timeZone
        self.role = role
        self.approvalStatus = approvalStatus
        self.requireSecurityQuestions = requireSecurityQuestions
    }

    private enum CodingKeys: String, CodingKey {
        case id
        case name
        case username
        case image
        case timeZone
        case role
        case approvalStatus
        case requireSecurityQuestions
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        id = try container.decodeIfPresent(String.self, forKey: .id)
        name = try container.decodeIfPresent(String.self, forKey: .name)
        username = try container.decodeIfPresent(String.self, forKey: .username)
        image = try container.decodeIfPresent(String.self, forKey: .image)
        timeZone = try container.decodeIfPresent(String.self, forKey: .timeZone)
        role = try container.decodeIfPresent(String.self, forKey: .role)
        approvalStatus = try container.decodeIfPresent(String.self, forKey: .approvalStatus)
        requireSecurityQuestions = try container.decodeIfPresent(Bool.self, forKey: .requireSecurityQuestions) ?? false
    }
}

struct SecurityQuestion: Codable, Equatable, Hashable, Identifiable {
    let id: Int
    let text: String
}

struct SecurityAnswerInput: Codable, Equatable, Hashable {
    let questionId: Int
    let answer: String
}

struct SecurityQuestionsResponse: Codable {
    let questions: [SecurityQuestion]
}

struct SecurityQuestionStatusResponse: Codable {
    let questionIds: [Int]
    let requireSecurityQuestions: Bool
}

struct SelfServiceResetRequest: Codable {
    let username: String
    let answers: [SecurityAnswerInput]
    let newPassword: String
}

struct VerifySecurityAnswersRequest: Codable {
    let username: String
    let answers: [SecurityAnswerInput]
}

struct SecurityAnswerResult: Codable, Equatable, Hashable {
    let questionId: Int
    let correct: Bool
}

struct VerifySecurityAnswersResponse: Codable {
    let valid: Bool
    let results: [SecurityAnswerResult]
}

struct RequestAdminResetRequest: Codable {
    let username: String
}

struct SetSecurityQuestionsRequest: Codable {
    let answers: [SecurityAnswerInput]
    // Required when changing already-configured questions from settings; the first-time
    // gate leaves this nil (omitted from the encoded body).
    var currentPassword: String? = nil
}

struct RegisterRequest: Codable {
    let fname: String
    let lname: String?
    let username: String
    let password: String
    let securityAnswers: [SecurityAnswerInput]?
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
    let appVersion: String?
    let encryptedCompatibility: String?
}

struct CredentialKeyResponse: Codable {
    let version: String
    let algorithm: String
    let keyId: String
    let publicKey: String
}

struct CredentialsCallbackRequest: Codable {
    let username: String?
    let password: String?
    let encryptedPayload: String?
    let encryptedKey: String?
    let encryptedIv: String?
    let credentialKeyId: String?
    let credentialEnvelopeVersion: String?
    let passwordProof: String?
    let passwordProofChallengeId: String?
    let passwordProofVersion: String?
    let csrfToken: String?
    let redirect: String?
    let callbackUrl: String?
}

struct TodosResponse: Codable {
    let todos: [TodoDTO]
}

struct FloatersResponse: Codable {
    let floaters: [FloaterDTO]
}

struct TodoSummaryRequest: Codable {
    let mode: String
    let listId: String?
    let timeZone: String?
}

struct TodoSummaryResponse: Codable, Equatable {
    let summary: String?
    let source: String?
    let mode: String?
    let taskCount: Int?
    let generatedAt: String?
    let fallbackReason: String?
    let reason: String?
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
    let dueEpochMs: Int64?
    /// A preset RRULE captured from a phrase like "every day" (nil when none).
    var rrule: String? = nil
    /// A priority captured from "!"/"!!"/"high|low|medium" (nil when none).
    var priority: String? = nil
}

struct CreateTodoRequest: Codable {
    let title: String
    let description: String?
    let priority: String
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
    let due: String?
    let rrule: String?
    let timeZone: String?
    let instanceDate: String?
    let completed: Bool
    let order: Int?
    let listID: String?
    let userID: String?
    let updatedAt: String?
    let createdAt: String?
}

struct CreateTodoResponse: Codable {
    let message: String?
    let todo: TodoDTO?
}

struct CreateFloaterRequest: Codable {
    let title: String
    let description: String?
    let priority: String
    let listID: String?
}

struct FloaterDTO: Codable, Equatable {
    let id: String
    let title: String
    let description: String?
    let pinned: Bool
    let priority: String
    let completed: Bool
    let order: Int?
    let listID: String?
    let userID: String?
    let updatedAt: String?
    let createdAt: String?
}

struct CreateFloaterResponse: Codable {
    let message: String?
    let floater: FloaterDTO?
}

/// Body of `POST /api/floater/{id}/promote` — schedules a floater into a real
/// Todo (the floater row is consumed). Mirrors the shared PromoteFloaterRequest.
struct PromoteFloaterRequest: Codable {
    let due: String
    let rrule: String?
}

struct PromoteFloaterResponse: Codable {
    let message: String?
    let todo: TodoDTO?
}

/// Response of `POST /api/todo/{id}/demote` — the todo row is consumed and an
/// Anytime floater takes its place. Mirrors the shared DemoteTodoResponse.
struct DemoteTodoResponse: Codable {
    let message: String?
    let floater: FloaterDTO?
}

struct UpdateFloaterRequest: Codable {
    let id: String
    let title: String?
    let description: String?
    let pinned: Bool?
    let priority: String?
    let completed: Bool?
    let listID: String?
}

struct DeleteFloaterRequest: Codable {
    let id: String
}

struct FloaterCompleteRequest: Codable {
    let id: String
}

struct FloaterUncompleteRequest: Codable {
    let id: String
}

struct FloaterPrioritizeRequest: Codable {
    let id: String
    let priority: String
}

struct UpdateTodoRequest: Codable {
    let id: String
    let title: String?
    let description: String?
    let pinned: Bool?
    let priority: String?
    let completed: Bool?
    let due: String?
    let rrule: String?
    let listID: String?
    let dateChanged: Bool?
    let rruleChanged: Bool?
    let instanceDate: String?
}

struct DeleteTodoRequest: Codable {
    let id: String
}

struct TodoInstancePatchRequest: Codable {
    let todoId: String
    let instanceDate: String
    let title: String?
    let description: String?
    let priority: String?
    let due: String?
}

struct TodoInstanceDeleteRequest: Codable {
    let todoId: String
    let instanceDate: String
}

struct TodoCompleteRequest: Codable {
    let id: String
    let instanceDate: String?
}

struct TodoUncompleteRequest: Codable {
    let id: String
    let instanceDate: String?
}

struct TodoPrioritizeRequest: Codable {
    let id: String
    let priority: String
    let instanceDate: String?
}

struct ReorderItemRequest: Codable {
    let id: String
    let order: Int
}

struct ListsResponse: Codable {
    let lists: [ListDTO]
}

struct FloaterListsResponse: Codable {
    let lists: [FloaterListDTO]
}

struct CreateListRequest: Codable {
    let name: String
    let color: String?
    let iconKey: String?
}

struct CreateFloaterListRequest: Codable {
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
    // Sharing metadata; nil on responses from servers that predate sharing.
    var myRole: String?
    var isShared: Bool?
    var memberCount: Int?
    var ownerUsername: String?
}

struct FloaterListDTO: Codable, Equatable {
    let id: String
    let name: String
    let color: String?
    let todoCount: Int
    let iconKey: String?
    let userID: String?
    let updatedAt: String?
    let createdAt: String?
    /// A reusable list can be Reset (all floaters un-completed) to run again.
    var reusable: Bool?
    // Sharing metadata; nil on responses from servers that predate sharing.
    var myRole: String?
    var isShared: Bool?
    var memberCount: Int?
    var ownerUsername: String?
}

struct ListMemberDTO: Codable, Equatable, Identifiable {
    let userId: String
    let username: String
    let name: String?
    let role: String
    let addedAt: String?

    var id: String { userId }
}

struct ListMembersResponse: Codable {
    let owner: ListMemberDTO
    let members: [ListMemberDTO]

    private enum CodingKeys: String, CodingKey {
        case owner
        case members
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        owner = try container.decode(ListMemberDTO.self, forKey: .owner)
        members = try container.decodeIfPresent([ListMemberDTO].self, forKey: .members) ?? []
    }
}

struct AddMemberRequest: Codable {
    let username: String
    let role: String
}

struct AddMemberResponse: Codable {
    let message: String?
    let member: ListMemberDTO?
}

struct UpdateMemberRoleRequest: Codable {
    let userId: String
    let role: String
}

struct RemoveMemberRequest: Codable {
    let userId: String
}

struct UserSearchResultDTO: Codable, Equatable, Identifiable {
    let id: String
    let username: String
    let name: String?
}

struct UserSearchResponse: Codable {
    let users: [UserSearchResultDTO]

    private enum CodingKeys: String, CodingKey {
        case users
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        users = try container.decodeIfPresent([UserSearchResultDTO].self, forKey: .users) ?? []
    }
}

struct CreateListResponse: Codable {
    let message: String?
    let list: ListDTO?
}

struct CreateFloaterListResponse: Codable {
    let message: String?
    let list: FloaterListDTO?
}

struct UpdateListRequest: Codable {
    let id: String
    let name: String?
    let color: String?
    let iconKey: String?
}

struct UpdateFloaterListRequest: Codable {
    let id: String
    let name: String?
    let color: String?
    let iconKey: String?
}

struct DeleteListRequest: Codable {
    let id: String?
    let ids: [String]

    init(id: String? = nil, ids: [String] = []) {
        self.id = id
        self.ids = ids
    }
}

struct DeleteFloaterListRequest: Codable {
    let id: String?
    let ids: [String]

    init(id: String? = nil, ids: [String] = []) {
        self.id = id
        self.ids = ids
    }
}

struct ListDetailResponse: Codable {
    let list: ListDTO
    let todos: [ListTodoDTO]

    init(list: ListDTO, todos: [ListTodoDTO] = []) {
        self.list = list
        self.todos = todos
    }

    private enum CodingKeys: String, CodingKey {
        case list
        case todos
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        list = try container.decode(ListDTO.self, forKey: .list)
        todos = try container.decodeIfPresent([ListTodoDTO].self, forKey: .todos) ?? []
    }
}

struct FloaterListDetailResponse: Codable {
    let list: FloaterListDTO
    let floaters: [FloaterListTodoDTO]

    init(list: FloaterListDTO, floaters: [FloaterListTodoDTO] = []) {
        self.list = list
        self.floaters = floaters
    }

    private enum CodingKeys: String, CodingKey {
        case list
        case floaters
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        list = try container.decode(FloaterListDTO.self, forKey: .list)
        floaters = try container.decodeIfPresent([FloaterListTodoDTO].self, forKey: .floaters) ?? []
    }
}

struct DeleteListResponse: Codable {
    let message: String?
    let deletedIds: [String]

    init(message: String? = nil, deletedIds: [String] = []) {
        self.message = message
        self.deletedIds = deletedIds
    }

    private enum CodingKeys: String, CodingKey {
        case message
        case deletedIds
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        message = try container.decodeIfPresent(String.self, forKey: .message)
        deletedIds = try container.decodeIfPresent([String].self, forKey: .deletedIds) ?? []
    }
}

struct DeleteFloaterListResponse: Codable {
    let message: String?
    let deletedIds: [String]

    init(message: String? = nil, deletedIds: [String] = []) {
        self.message = message
        self.deletedIds = deletedIds
    }

    private enum CodingKeys: String, CodingKey {
        case message
        case deletedIds
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        message = try container.decodeIfPresent(String.self, forKey: .message)
        deletedIds = try container.decodeIfPresent([String].self, forKey: .deletedIds) ?? []
    }
}

struct ListTodoDTO: Codable, Equatable {
    let id: String
    let title: String
    let priority: String
    let due: String?
    let completed: Bool
    let order: Int
}

struct FloaterListTodoDTO: Codable, Equatable {
    let id: String
    let title: String
    let priority: String
    let completed: Bool
    let order: Int
}

struct CompletedTodosResponse: Codable {
    let completedTodos: [CompletedTodoDTO]
}

struct CompletedFloatersResponse: Codable {
    let completedFloaters: [CompletedFloaterDTO]
}

struct CompletedTodoDTO: Codable, Equatable {
    let id: String
    let originalTodoID: String?
    let title: String
    let description: String?
    let priority: String
    let due: String?
    let completedAt: String?
    let completedOnTime: Bool?
    let daysToComplete: Double?
    let rrule: String?
    let userID: String?
    let instanceDate: String?
    let listID: String?
    let listName: String?
    let listColor: String?
}

struct UpdateCompletedTodoRequest: Codable {
    let id: String
    let title: String?
    let description: String?
    let priority: String?
    let due: String?
    let rrule: String?
    let listID: String?
}

struct DeleteCompletedTodoRequest: Codable {
    let id: String
}

struct CompletedFloaterDTO: Codable, Equatable {
    let id: String
    let originalFloaterID: String?
    let title: String
    let description: String?
    let priority: String
    let completedAt: String?
    let daysToComplete: Double?
    let userID: String?
    let listID: String?
    let listName: String?
    let listColor: String?
}

struct UpdateCompletedFloaterRequest: Codable {
    let id: String
    let title: String?
    let description: String?
    let priority: String?
    let listID: String?
}

struct DeleteCompletedFloaterRequest: Codable {
    let id: String
}

// GET/PATCH /api/preferences returns the canonical fields at the TOP LEVEL
// (sortBy/groupBy/direction) — the server does not nest them under a
// `preferences`/`userPreferences` key. See PreferenceModels.kt in shared.
struct PreferencesResponse: Codable {
    let sortBy: String?
    let groupBy: String?
    let direction: String?
    let aiSummaryEnabled: Bool?
}

struct PreferencesDTO: Codable, Equatable {
    let direction: String?
    let sortBy: String?
    let groupBy: String?
    let rrule: String?
    var aiSummaryEnabled: Bool? = nil
}

struct UserResponse: Codable, Equatable {
    let user: SessionUser
}

struct UpdateProfileRequest: Codable {
    // The backend `/user/profile` endpoint only reads `name` and `image`
    // (UserProfilePatchRequest). Synthesized Codable omits nil optionals, so
    // `UpdateProfileRequest(name:)` serializes to exactly `{"name":"..."}`.
    let name: String?
    let image: String?
}

struct ChangePasswordRequest: Codable {
    let currentPassword: String
    let newPassword: String
}

struct AuthRedirectResponse: Codable {
    let url: String?
    let message: String?
    let code: String?
}

typealias TodoDto = TodoDTO
typealias FloaterDto = FloaterDTO
typealias ListDto = ListDTO
typealias FloaterListDto = FloaterListDTO
typealias CompletedTodoDto = CompletedTodoDTO
typealias CompletedFloaterDto = CompletedFloaterDTO
typealias PreferencesDto = PreferencesDTO
typealias AuthCallbackResponse = AuthRedirectResponse

extension UpdateProfileRequest {
    init(name: String) {
        self.init(name: name, image: nil)
    }
}
