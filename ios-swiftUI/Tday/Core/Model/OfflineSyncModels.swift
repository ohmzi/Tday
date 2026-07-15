import Foundation

let LOCAL_LIST_PREFIX = "local-list-"
let LOCAL_FLOATER_LIST_PREFIX = "local-floater-list-"
let LOCAL_TODO_PREFIX = "local-todo-"
let LOCAL_FLOATER_PREFIX = "local-floater-"
let LOCAL_COMPLETED_PREFIX = "local-completed-"
let LOCAL_COMPLETED_FLOATER_PREFIX = "local-completed-floater-"
let LOCAL_STEP_PREFIX = "local-step-"

struct OfflineSyncState: Equatable, Codable {
    var lastSuccessfulSyncEpochMs: Int64 = 0
    var lastSyncAttemptEpochMs: Int64 = 0
    var todos: [CachedTodoRecord] = []
    var floaters: [CachedFloaterRecord] = []
    var completedItems: [CachedCompletedRecord] = []
    var completedFloaters: [CachedCompletedFloaterRecord] = []
    var lists: [CachedListRecord] = []
    var floaterLists: [CachedFloaterListRecord] = []
    var pendingMutations: [PendingMutationRecord] = []
    var aiSummaryEnabled: Bool = true
}

struct MobileSyncStatus: Equatable {
    var dataMode: AppDataMode = .unset
    var isOffline = false
    var isManualSyncing = false
    var pendingMutationCount = 0
    var lastSuccessfulSyncEpochMs: Int64 = 0
    var lastSyncAttemptEpochMs: Int64 = 0

    var isLocalMode: Bool {
        dataMode == .local
    }

    var title: String {
        isLocalMode ? L("Local workspace") : L("Server sync")
    }

    var statusText: String {
        if isLocalMode {
            return L("On this device only")
        }
        if isManualSyncing {
            return L("Syncing now")
        }
        if isOffline {
            return L("Offline. Changes will sync when connection returns.")
        }
        if pendingMutationCount > 0 {
            return L("Waiting to sync")
        }
        if lastSuccessfulSyncEpochMs > 0 {
            return L("Synced")
        }
        return L("Ready to sync")
    }

    var pendingText: String {
        switch pendingMutationCount {
        case 0:
            return L("No changes waiting")
        case 1:
            return L("1 change waiting")
        default:
            return L("%lld changes waiting", Int64(pendingMutationCount))
        }
    }

    func lastSyncedText(now: Date = Date(), calendar: Calendar = .current) -> String {
        guard lastSuccessfulSyncEpochMs > 0 else {
            return L("Not yet")
        }
        return Self.timestampText(epochMs: lastSuccessfulSyncEpochMs, now: now, calendar: calendar)
    }

    func lastAttemptText(now: Date = Date(), calendar: Calendar = .current) -> String? {
        guard !isLocalMode,
              lastSyncAttemptEpochMs > 0,
              lastSyncAttemptEpochMs != lastSuccessfulSyncEpochMs else {
            return nil
        }
        return Self.timestampText(epochMs: lastSyncAttemptEpochMs, now: now, calendar: calendar)
    }

    static func timestampText(epochMs: Int64, now: Date = Date(), calendar: Calendar = .current) -> String {
        let date = Date(timeIntervalSince1970: TimeInterval(epochMs) / 1_000)
        let formatter = DateFormatter()
        formatter.locale = AppLocale.current
        formatter.timeZone = calendar.timeZone
        formatter.setLocalizedDateFormatFromTemplate(
            calendar.isDate(date, inSameDayAs: now) ? "jmm" : "MMMd jmm"
        )
        return formatter.string(from: date)
    }
}

extension MobileSyncStatus {
    init(
        dataMode: AppDataMode,
        isOffline: Bool = false,
        isManualSyncing: Bool = false,
        state: OfflineSyncState
    ) {
        if dataMode == .local {
            self.init(dataMode: dataMode, isOffline: false, isManualSyncing: false)
        } else {
            self.init(
                dataMode: dataMode,
                isOffline: isOffline,
                isManualSyncing: isManualSyncing,
                pendingMutationCount: state.pendingMutations.count,
                lastSuccessfulSyncEpochMs: state.lastSuccessfulSyncEpochMs,
                lastSyncAttemptEpochMs: state.lastSyncAttemptEpochMs
            )
        }
    }
}

struct CachedTodoRecord: Identifiable, Equatable, Codable {
    let id: String
    let canonicalId: String
    let title: String
    let description: String?
    let priority: String
    let dueEpochMs: Int64?
    let rrule: String?
    let instanceDateEpochMs: Int64?
    let pinned: Bool
    let completed: Bool
    let listId: String?
    let updatedAtEpochMs: Int64
}

struct CachedFloaterRecord: Identifiable, Equatable, Codable {
    let id: String
    let canonicalId: String
    let title: String
    let description: String?
    let priority: String
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
    let createdAtEpochMs: Int64
    // Optional so state persisted before sharing existed still decodes.
    var myRole: String?
    var isShared: Bool?
    var memberCount: Int?
    var ownerUsername: String?

    init(
        id: String,
        name: String,
        color: String?,
        iconKey: String?,
        todoCount: Int,
        updatedAtEpochMs: Int64,
        createdAtEpochMs: Int64,
        myRole: String? = nil,
        isShared: Bool? = nil,
        memberCount: Int? = nil,
        ownerUsername: String? = nil
    ) {
        self.id = id
        self.name = name
        self.color = color
        self.iconKey = iconKey
        self.todoCount = todoCount
        self.updatedAtEpochMs = updatedAtEpochMs
        self.createdAtEpochMs = createdAtEpochMs
        self.myRole = myRole
        self.isShared = isShared
        self.memberCount = memberCount
        self.ownerUsername = ownerUsername
    }
}

struct CachedFloaterListRecord: Identifiable, Equatable, Codable {
    let id: String
    let name: String
    let color: String?
    let iconKey: String?
    let todoCount: Int
    let updatedAtEpochMs: Int64
    let createdAtEpochMs: Int64
    // Optional so state persisted before sharing existed still decodes.
    var myRole: String?
    var isShared: Bool?
    var memberCount: Int?
    var ownerUsername: String?

    init(
        id: String,
        name: String,
        color: String?,
        iconKey: String?,
        todoCount: Int,
        updatedAtEpochMs: Int64,
        createdAtEpochMs: Int64,
        myRole: String? = nil,
        isShared: Bool? = nil,
        memberCount: Int? = nil,
        ownerUsername: String? = nil
    ) {
        self.id = id
        self.name = name
        self.color = color
        self.iconKey = iconKey
        self.todoCount = todoCount
        self.updatedAtEpochMs = updatedAtEpochMs
        self.createdAtEpochMs = createdAtEpochMs
        self.myRole = myRole
        self.isShared = isShared
        self.memberCount = memberCount
        self.ownerUsername = ownerUsername
    }
}

struct CachedCompletedRecord: Identifiable, Equatable, Codable {
    let id: String
    let originalTodoId: String?
    let title: String
    let description: String?
    let priority: String
    let dueEpochMs: Int64?
    let completedAtEpochMs: Int64
    let rrule: String?
    let instanceDateEpochMs: Int64?
    let listId: String?
    let listName: String?
    let listColor: String?
}

struct CachedCompletedFloaterRecord: Identifiable, Equatable, Codable {
    let id: String
    let originalFloaterId: String?
    let title: String
    let description: String?
    let priority: String
    let completedAtEpochMs: Int64
    let listId: String?
    let listName: String?
    let listColor: String?
}

enum MutationKind: String, Codable, CaseIterable {
    case createList = "CREATE_LIST"
    case updateList = "UPDATE_LIST"
    case deleteList = "DELETE_LIST"
    case createFloaterList = "CREATE_FLOATER_LIST"
    case updateFloaterList = "UPDATE_FLOATER_LIST"
    case deleteFloaterList = "DELETE_FLOATER_LIST"
    case resetFloaterList = "RESET_FLOATER_LIST"
    case createTodo = "CREATE_TODO"
    case updateTodo = "UPDATE_TODO"
    case deleteTodo = "DELETE_TODO"
    case createFloater = "CREATE_FLOATER"
    case updateFloater = "UPDATE_FLOATER"
    case deleteFloater = "DELETE_FLOATER"
    case setPinned = "SET_PINNED"
    case setPriority = "SET_PRIORITY"
    case completeTodo = "COMPLETE_TODO"
    case completeTodoInstance = "COMPLETE_TODO_INSTANCE"
    case uncompleteTodo = "UNCOMPLETE_TODO"
    case completeFloater = "COMPLETE_FLOATER"
    case uncompleteFloater = "UNCOMPLETE_FLOATER"
    case promoteFloater = "PROMOTE_FLOATER"
    case demoteTodo = "DEMOTE_TODO"
    case createStep = "CREATE_STEP"
    case toggleStep = "TOGGLE_STEP"
    case deleteStep = "DELETE_STEP"
    case reorderSteps = "REORDER_STEPS"
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
    // Task-step ordering (REORDER_STEPS): the full ordered list of step ids.
    // Defaulted so the 30+ existing memberwise-init call sites keep compiling.
    var orderedIds: [String]? = nil

    var id: String {
        mutationId
    }
}
