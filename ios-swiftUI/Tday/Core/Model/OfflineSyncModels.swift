import Foundation

let LOCAL_LIST_PREFIX = "local-list-"
let LOCAL_TODO_PREFIX = "local-todo-"
let LOCAL_COMPLETED_PREFIX = "local-completed-"

struct OfflineSyncState: Equatable, Codable {
    var lastSuccessfulSyncEpochMs: Int64 = 0
    var lastSyncAttemptEpochMs: Int64 = 0
    var todos: [CachedTodoRecord] = []
    var completedItems: [CachedCompletedRecord] = []
    var lists: [CachedListRecord] = []
    var pendingMutations: [PendingMutationRecord] = []
    var aiSummaryEnabled: Bool = true
}

struct CachedTodoRecord: Identifiable, Equatable, Codable {
    let id: String
    let canonicalId: String
    let title: String
    let description: String?
    let priority: String
    let dueEpochMs: Int64
    let rrule: String?
    let instanceDateEpochMs: Int64?
    let pinned: Bool
    let completed: Bool
    let listId: String?
    let updatedAtEpochMs: Int64
}

struct CachedListRecord: Identifiable, Equatable, Codable {
    let id: String
    let name: String
    let color: String?
    let iconKey: String?
    let todoCount: Int
    let updatedAtEpochMs: Int64
}

struct CachedCompletedRecord: Identifiable, Equatable, Codable {
    let id: String
    let originalTodoId: String?
    let title: String
    let description: String?
    let priority: String
    let dueEpochMs: Int64
    let completedAtEpochMs: Int64
    let rrule: String?
    let instanceDateEpochMs: Int64?
    let listName: String?
    let listColor: String?
}

enum MutationKind: String, Codable, CaseIterable {
    case createList = "CREATE_LIST"
    case updateList = "UPDATE_LIST"
    case createTodo = "CREATE_TODO"
    case updateTodo = "UPDATE_TODO"
    case deleteTodo = "DELETE_TODO"
    case setPinned = "SET_PINNED"
    case setPriority = "SET_PRIORITY"
    case completeTodo = "COMPLETE_TODO"
    case completeTodoInstance = "COMPLETE_TODO_INSTANCE"
    case uncompleteTodo = "UNCOMPLETE_TODO"
}

struct PendingMutationRecord: Identifiable, Equatable, Codable {
    let mutationId: String
    let kind: MutationKind
    let targetId: String?
    let timestampEpochMs: Int64
    let title: String?
    let description: String?
    let priority: String?
    let dueEpochMs: Int64?
    let rrule: String?
    let listId: String?
    let pinned: Bool?
    let completed: Bool?
    let instanceDateEpochMs: Int64?
    let name: String?
    let color: String?
    let iconKey: String?

    var id: String {
        mutationId
    }
}
