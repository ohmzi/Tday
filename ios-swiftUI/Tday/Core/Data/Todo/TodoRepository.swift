import Foundation

struct TodoDashboardCacheSnapshot {
    let summary: DashboardSummary
    let searchableTodos: [TodoItem]
    let todayTodos: [TodoItem]
    let aiSummaryEnabled: Bool
}

struct TodoListCacheSnapshot {
    let lists: [ListSummary]
    let items: [TodoItem]
    let aiSummaryEnabled: Bool
}

/// Snapshot of everything `stageDeleteTodo(_:)` pruned from the local cache,
/// so `undoStagedTodo(_:)` can restore the exact pre-delete state.
struct StagedTodoDeletion {
    let todos: [CachedTodoRecord]
    let pendingMutations: [PendingMutationRecord]
}

/// Snapshot of everything `stageDeleteFloater(_:)` pruned from the local
/// cache, so `undoStagedFloater(_:)` can restore the exact pre-delete state.
struct StagedFloaterDeletion {
    let floaters: [CachedFloaterRecord]
    let pendingMutations: [PendingMutationRecord]
}

@MainActor
final class TodoRepository {
    private let api: TdayAPIService
    private let cacheManager: OfflineCacheManager
    private let syncManager: SyncManager

    init(api: TdayAPIService, cacheManager: OfflineCacheManager, syncManager: SyncManager) {
        self.api = api
        self.cacheManager = cacheManager
        self.syncManager = syncManager
    }

    func fetchDashboardSummary() -> DashboardSummary {
        buildDashboardSummary(from: cacheManager.loadOfflineState())
    }

    func fetchDashboardSummarySnapshot() -> DashboardSummary {
        buildDashboardSummary(from: cacheManager.loadOfflineState())
    }

    func fetchTodos(mode: TodoListMode, listId: String? = nil) -> [TodoItem] {
        buildTodos(from: cacheManager.loadOfflineState(), mode: mode, listId: listId)
    }

    func fetchTodosSnapshot(mode: TodoListMode, listId: String? = nil) -> [TodoItem] {
        buildTodos(from: cacheManager.loadOfflineState(), mode: mode, listId: listId)
    }

    func fetchDashboardCacheSnapshot() -> TodoDashboardCacheSnapshot {
        makeDashboardCacheSnapshot(from: cacheManager.loadOfflineState())
    }

    func makeDashboardCacheSnapshot(from state: OfflineSyncState) -> TodoDashboardCacheSnapshot {
        buildDashboardCacheSnapshot(from: state)
    }

    func fetchTodoListCacheSnapshot(mode: TodoListMode, listId: String?) -> TodoListCacheSnapshot {
        makeTodoListCacheSnapshot(from: cacheManager.loadOfflineState(), mode: mode, listId: listId)
    }

    func makeTodoListCacheSnapshot(
        from state: OfflineSyncState,
        mode: TodoListMode,
        listId: String?
    ) -> TodoListCacheSnapshot {
        TodoListCacheSnapshot(
            lists: buildListSummaries(from: state, mode: mode),
            items: buildTodos(from: state, mode: mode, listId: listId),
            aiSummaryEnabled: syncManager.isLocalMode ? false : state.aiSummaryEnabled
        )
    }

    func createTodo(payload: CreateTaskPayload) async throws {
        let normalizedTitle = payload.title.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !normalizedTitle.isEmpty else {
            return
        }

        let now = Date().epochMilliseconds
        let localTodoID = LOCAL_TODO_PREFIX + UUID().uuidString.lowercased()
        let normalizedDescription = payload.description.nilIfBlank
        let normalizedListID = payload.listId.nilIfBlank
        let normalizedPriorityValue = normalizedPriority(payload.priority)
        let normalizedDue = payload.due ?? Date().addingTimeInterval(60 * 60)
        let mutationID = UUID().uuidString
        let mutation = PendingMutationRecord(
            mutationId: mutationID,
            kind: .createTodo,
            targetId: localTodoID,
            timestampEpochMs: now,
            title: normalizedTitle,
            description: normalizedDescription,
            priority: normalizedPriorityValue,
            dueEpochMs: normalizedDue.epochMilliseconds,
            rrule: payload.rrule,
            listId: normalizedListID,
            pinned: false,
            completed: false,
            instanceDateEpochMs: nil,
            name: nil,
            color: nil,
            iconKey: nil
        )

        _ = try await cacheManager.updateOfflineState { state in
            var nextState = state
            nextState.todos.append(
                CachedTodoRecord(
                    id: localTodoID,
                    canonicalId: localTodoID,
                    title: normalizedTitle,
                    description: normalizedDescription,
                    priority: normalizedPriorityValue,
                    dueEpochMs: normalizedDue.epochMilliseconds,
                    rrule: payload.rrule,
                    instanceDateEpochMs: nil,
                    pinned: false,
                    completed: false,
                    listId: normalizedListID,
                    updatedAtEpochMs: now
                )
            )
            nextState.pendingMutations.append(mutation)
            return nextState
        }

        if syncManager.isLocalMode {
            return
        }

        // Route the create through the single, sync-lock-protected replay path that updates and
        // local-list creates already use. Previously this also fired a direct `api.createTodo`
        // here while leaving a replayable CREATE_TODO pending mutation behind — two independent
        // server-write paths for one action. Any concurrent `replayPendingMutations` sync (e.g.
        // the realtime echo of this very create) could replay the mutation before the direct
        // call removed it, creating a second server-side todo that then synced to every device.
        let result = await syncManager.syncCachedData(force: true, replayPendingMutations: true)
        if case let .failure(error) = result, isLikelyUnrecoverableMutationError(error) {
            throw error
        }
    }

    func createFloater(payload: CreateTaskPayload) async throws {
        let normalizedTitle = payload.title.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !normalizedTitle.isEmpty else {
            return
        }

        let now = Date().epochMilliseconds
        let localFloaterID = LOCAL_FLOATER_PREFIX + UUID().uuidString.lowercased()
        let normalizedDescription = payload.description.nilIfBlank
        let normalizedListID = payload.listId.nilIfBlank
        let normalizedPriorityValue = normalizedPriority(payload.priority)
        let mutationID = UUID().uuidString
        let mutation = PendingMutationRecord(
            mutationId: mutationID,
            kind: .createFloater,
            targetId: localFloaterID,
            timestampEpochMs: now,
            title: normalizedTitle,
            description: normalizedDescription,
            priority: normalizedPriorityValue,
            dueEpochMs: nil,
            rrule: nil,
            listId: normalizedListID,
            pinned: false,
            completed: false,
            instanceDateEpochMs: nil,
            name: nil,
            color: nil,
            iconKey: nil
        )

        _ = try await cacheManager.updateOfflineState { state in
            var nextState = state
            nextState.floaters.append(
                CachedFloaterRecord(
                    id: localFloaterID,
                    canonicalId: localFloaterID,
                    title: normalizedTitle,
                    description: normalizedDescription,
                    priority: normalizedPriorityValue,
                    pinned: false,
                    completed: false,
                    listId: normalizedListID,
                    updatedAtEpochMs: now
                )
            )
            nextState.pendingMutations.append(mutation)
            return nextState
        }

        if syncManager.isLocalMode {
            return
        }

        // See createTodo: route through the single, sync-lock-protected replay path instead of
        // also firing a direct `api.createFloater`. The redundant direct call raced the
        // replayable CREATE_FLOATER pending mutation and produced duplicate floaters that synced
        // to every device.
        let result = await syncManager.syncCachedData(force: true, replayPendingMutations: true)
        if case let .failure(error) = result, isLikelyUnrecoverableMutationError(error) {
            throw error
        }
    }

    func updateTodo(_ todo: TodoItem, payload: CreateTaskPayload) async throws {
        let normalizedTitle = payload.title.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !normalizedTitle.isEmpty else {
            return
        }

        let now = Date().epochMilliseconds
        let normalizedDescription = payload.description.nilIfBlank
        let normalizedListID = payload.listId.nilIfBlank
        let normalizedPriorityValue = normalizedPriority(payload.priority)
        let normalizedDue = payload.due ?? todo.due ?? Date().addingTimeInterval(60 * 60)
        _ = try await cacheManager.updateOfflineState { state in
            var nextState = state
            nextState.todos = state.todos.map { current in
                let sameTodo = current.canonicalId == todo.canonicalId && current.instanceDateEpochMs == todo.instanceDateEpochMilliseconds
                guard sameTodo else { return current }
                return CachedTodoRecord(
                    id: current.id,
                    canonicalId: current.canonicalId,
                    title: normalizedTitle,
                    description: normalizedDescription,
                    priority: normalizedPriorityValue,
                    dueEpochMs: normalizedDue.epochMilliseconds,
                    rrule: payload.rrule,
                    instanceDateEpochMs: current.instanceDateEpochMs,
                    pinned: current.pinned,
                    completed: current.completed,
                    listId: normalizedListID,
                    updatedAtEpochMs: now
                )
            }
            nextState.pendingMutations.removeAll { $0.kind == .updateTodo && $0.targetId == todo.canonicalId && $0.instanceDateEpochMs == todo.instanceDateEpochMilliseconds }
            nextState.pendingMutations.append(
                PendingMutationRecord(
                    mutationId: UUID().uuidString,
                    kind: .updateTodo,
                    targetId: todo.canonicalId,
                    timestampEpochMs: now,
                    title: normalizedTitle,
                    description: normalizedDescription,
                    priority: normalizedPriorityValue,
                    dueEpochMs: normalizedDue.epochMilliseconds,
                    rrule: payload.rrule,
                    listId: normalizedListID,
                    pinned: todo.pinned,
                    completed: todo.completed,
                    instanceDateEpochMs: todo.instanceDateEpochMilliseconds,
                    name: nil,
                    color: nil,
                    iconKey: nil
                )
            )
            return nextState
        }
        if syncManager.isLocalMode {
            return
        }
        let result = await syncManager.syncCachedData(force: true, replayPendingMutations: true)
        if case let .failure(error) = result, isLikelyUnrecoverableMutationError(error) {
            throw error
        }
    }

    func updateFloater(_ floater: TodoItem, payload: CreateTaskPayload) async throws {
        let normalizedTitle = payload.title.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !normalizedTitle.isEmpty else {
            return
        }

        let now = Date().epochMilliseconds
        let normalizedDescription = payload.description.nilIfBlank
        let normalizedListID = payload.listId.nilIfBlank
        let normalizedPriorityValue = normalizedPriority(payload.priority)
        _ = try await cacheManager.updateOfflineState { state in
            var nextState = state
            nextState.floaters = state.floaters.map { current in
                guard current.canonicalId == floater.canonicalId else { return current }
                return CachedFloaterRecord(
                    id: current.id,
                    canonicalId: current.canonicalId,
                    title: normalizedTitle,
                    description: normalizedDescription,
                    priority: normalizedPriorityValue,
                    pinned: current.pinned,
                    completed: current.completed,
                    listId: normalizedListID,
                    updatedAtEpochMs: now
                )
            }
            nextState.pendingMutations.removeAll { $0.kind == .updateFloater && $0.targetId == floater.canonicalId }
            nextState.pendingMutations.append(
                PendingMutationRecord(
                    mutationId: UUID().uuidString,
                    kind: .updateFloater,
                    targetId: floater.canonicalId,
                    timestampEpochMs: now,
                    title: normalizedTitle,
                    description: normalizedDescription,
                    priority: normalizedPriorityValue,
                    dueEpochMs: nil,
                    rrule: nil,
                    listId: normalizedListID,
                    pinned: floater.pinned,
                    completed: floater.completed,
                    instanceDateEpochMs: nil,
                    name: nil,
                    color: nil,
                    iconKey: nil
                )
            )
            return nextState
        }
        if syncManager.isLocalMode {
            return
        }
        let result = await syncManager.syncCachedData(force: true, replayPendingMutations: true)
        if case let .failure(error) = result, isLikelyUnrecoverableMutationError(error) {
            throw error
        }
    }

    /// Completed-today count from the local cache, for the Day Done state.
    func completedTodayCount() -> Int {
        let calendar = Calendar.current
        return cacheManager.loadOfflineState().completedItems.filter { record in
            calendar.isDateInToday(Date(epochMilliseconds: record.completedAtEpochMs))
        }.count
    }

    /// Notification "Tonight" action: move a task to today 19:00 local by id.
    /// Recurring tasks are skipped — their occurrences reschedule via the
    /// app's per-instance flow, not from a notification button.
    func moveTodoTonight(taskID: String) async throws {
        let state = try await cacheManager.loadOfflineState()
        guard let record = state.todos.first(where: {
            ($0.canonicalId == taskID || $0.id == taskID) && $0.instanceDateEpochMs == nil
        }), record.rrule == nil else {
            return
        }
        let startOfDay = Calendar.current.startOfDay(for: Date())
        let tonight = Calendar.current.date(bySettingHour: 19, minute: 0, second: 0, of: startOfDay) ?? Date()
        let todo = TodoItem(
            id: record.id,
            canonicalId: record.canonicalId,
            title: record.title,
            description: record.description,
            priority: record.priority,
            due: record.dueEpochMs.map { Date(epochMilliseconds: $0) },
            rrule: record.rrule,
            instanceDate: nil,
            pinned: record.pinned,
            completed: record.completed,
            listId: record.listId,
            updatedAt: nil
        )
        try await moveTodo(todo, due: tonight)
    }

    func moveTodo(_ todo: TodoItem, due: Date) async throws {
        let now = Date().epochMilliseconds
        let dueEpochMs = due.epochMilliseconds
        let isLocalOnly = todo.canonicalId.hasPrefix(LOCAL_TODO_PREFIX)

        _ = try await cacheManager.updateOfflineState { state in
            var nextState = state
            let hasExistingUpdateMutation = state.pendingMutations.contains { mutation in
                mutation.kind == .updateTodo &&
                    mutation.targetId == todo.canonicalId &&
                    mutation.instanceDateEpochMs == todo.instanceDateEpochMilliseconds
            }
            nextState.todos = state.todos.map { current in
                let sameTodo = current.canonicalId == todo.canonicalId && current.instanceDateEpochMs == todo.instanceDateEpochMilliseconds
                guard sameTodo else { return current }
                return CachedTodoRecord(
                    id: current.id,
                    canonicalId: current.canonicalId,
                    title: current.title,
                    description: current.description,
                    priority: current.priority,
                    dueEpochMs: dueEpochMs,
                    rrule: current.rrule,
                    instanceDateEpochMs: current.instanceDateEpochMs,
                    pinned: current.pinned,
                    completed: current.completed,
                    listId: current.listId,
                    updatedAtEpochMs: now
                )
            }
            nextState.pendingMutations = state.pendingMutations.map { mutation in
                let samePendingUpdate = mutation.kind == .updateTodo &&
                    mutation.targetId == todo.canonicalId &&
                    mutation.instanceDateEpochMs == todo.instanceDateEpochMilliseconds
                guard samePendingUpdate || (mutation.kind == .createTodo && mutation.targetId == todo.canonicalId) else {
                    return mutation
                }
                return PendingMutationRecord(
                    mutationId: mutation.mutationId,
                    kind: mutation.kind,
                    targetId: mutation.targetId,
                    timestampEpochMs: now,
                    title: mutation.title,
                    description: mutation.description,
                    priority: mutation.priority,
                    dueEpochMs: dueEpochMs,
                    rrule: mutation.rrule,
                    listId: mutation.listId,
                    pinned: mutation.pinned,
                    completed: mutation.completed,
                    instanceDateEpochMs: mutation.instanceDateEpochMs,
                    name: mutation.name,
                    color: mutation.color,
                    iconKey: mutation.iconKey
                )
            }
            if !isLocalOnly && !hasExistingUpdateMutation {
                nextState.pendingMutations.append(
                    PendingMutationRecord(
                        mutationId: UUID().uuidString,
                        kind: .updateTodo,
                        targetId: todo.canonicalId,
                        timestampEpochMs: now,
                        title: nil,
                        description: nil,
                        priority: nil,
                        dueEpochMs: dueEpochMs,
                        rrule: nil,
                        listId: nil,
                        pinned: nil,
                        completed: nil,
                        instanceDateEpochMs: todo.instanceDateEpochMilliseconds,
                        name: nil,
                        color: nil,
                        iconKey: nil
                    )
                )
            }
            return nextState
        }

        if syncManager.isLocalMode {
            return
        }

        let result = await syncManager.syncCachedData(force: true, replayPendingMutations: true)
        if case let .failure(error) = result, isLikelyUnrecoverableMutationError(error) {
            throw error
        }
    }

    /// First half of a delayed-commit delete: prunes the todo (and its pending
    /// create/update mutations) from the local cache without queueing the
    /// server delete. Commit later by calling `deleteTodo(_:)` — its prune
    /// half re-runs as a no-op — or restore with `undoStagedTodo(_:)`.
    func stageDeleteTodo(_ todo: TodoItem) -> StagedTodoDeletion {
        var removedTodos: [CachedTodoRecord] = []
        var removedMutations: [PendingMutationRecord] = []
        cacheManager.updateOfflineState { state in
            var nextState = state
            removedTodos = state.todos.filter {
                $0.canonicalId == todo.canonicalId && $0.instanceDateEpochMs == todo.instanceDateEpochMilliseconds
            }
            removedMutations = state.pendingMutations.filter {
                $0.targetId == todo.canonicalId && ($0.kind == .createTodo || $0.kind == .updateTodo)
            }
            nextState.todos.removeAll { $0.canonicalId == todo.canonicalId && $0.instanceDateEpochMs == todo.instanceDateEpochMilliseconds }
            nextState.pendingMutations.removeAll { $0.targetId == todo.canonicalId && ($0.kind == .createTodo || $0.kind == .updateTodo) }
            return nextState
        }
        return StagedTodoDeletion(todos: removedTodos, pendingMutations: removedMutations)
    }

    /// Restores the local state captured by `stageDeleteTodo(_:)`. Idempotent:
    /// records that already exist again (e.g. re-added by a sync pull during
    /// the undo window) are left untouched.
    func undoStagedTodo(_ staged: StagedTodoDeletion) {
        cacheManager.updateOfflineState { state in
            var nextState = state
            for record in staged.todos where !nextState.todos.contains(where: {
                $0.canonicalId == record.canonicalId && $0.instanceDateEpochMs == record.instanceDateEpochMs
            }) {
                nextState.todos.append(record)
            }
            for mutation in staged.pendingMutations where !nextState.pendingMutations.contains(where: {
                $0.mutationId == mutation.mutationId
            }) {
                nextState.pendingMutations.append(mutation)
            }
            return nextState
        }
    }

    func deleteTodo(_ todo: TodoItem) async throws {
        let now = Date().epochMilliseconds
        _ = try await cacheManager.updateOfflineState { state in
            var nextState = state
            nextState.todos.removeAll { $0.canonicalId == todo.canonicalId && $0.instanceDateEpochMs == todo.instanceDateEpochMilliseconds }
            nextState.pendingMutations.removeAll { $0.targetId == todo.canonicalId && ($0.kind == .createTodo || $0.kind == .updateTodo) }
            if !todo.canonicalId.hasPrefix(LOCAL_TODO_PREFIX) {
                nextState.pendingMutations.append(
                    PendingMutationRecord(
                        mutationId: UUID().uuidString,
                        kind: .deleteTodo,
                        targetId: todo.canonicalId,
                        timestampEpochMs: now,
                        title: nil,
                        description: nil,
                        priority: nil,
                        dueEpochMs: nil,
                        rrule: nil,
                        listId: nil,
                        pinned: nil,
                        completed: nil,
                        instanceDateEpochMs: todo.instanceDateEpochMilliseconds,
                        name: nil,
                        color: nil,
                        iconKey: nil
                    )
                )
            }
            return nextState
        }
        if syncManager.isLocalMode {
            return
        }
        let result = await syncManager.syncCachedData(force: true, replayPendingMutations: true)
        if case let .failure(error) = result, isLikelyUnrecoverableMutationError(error) {
            throw error
        }
    }

    /// First half of a delayed-commit delete: prunes the floater (and its
    /// pending create/update mutations) from the local cache without queueing
    /// the server delete. Commit later by calling `deleteFloater(_:)` — its
    /// prune half re-runs as a no-op — or restore with `undoStagedFloater(_:)`.
    func stageDeleteFloater(_ floater: TodoItem) -> StagedFloaterDeletion {
        var removedFloaters: [CachedFloaterRecord] = []
        var removedMutations: [PendingMutationRecord] = []
        cacheManager.updateOfflineState { state in
            var nextState = state
            removedFloaters = state.floaters.filter { $0.canonicalId == floater.canonicalId }
            removedMutations = state.pendingMutations.filter {
                $0.targetId == floater.canonicalId && ($0.kind == .createFloater || $0.kind == .updateFloater)
            }
            nextState.floaters.removeAll { $0.canonicalId == floater.canonicalId }
            nextState.pendingMutations.removeAll { $0.targetId == floater.canonicalId && ($0.kind == .createFloater || $0.kind == .updateFloater) }
            return nextState
        }
        return StagedFloaterDeletion(floaters: removedFloaters, pendingMutations: removedMutations)
    }

    /// Restores the local state captured by `stageDeleteFloater(_:)`.
    /// Idempotent: records that already exist again (e.g. re-added by a sync
    /// pull during the undo window) are left untouched.
    func undoStagedFloater(_ staged: StagedFloaterDeletion) {
        cacheManager.updateOfflineState { state in
            var nextState = state
            for record in staged.floaters where !nextState.floaters.contains(where: {
                $0.canonicalId == record.canonicalId
            }) {
                nextState.floaters.append(record)
            }
            for mutation in staged.pendingMutations where !nextState.pendingMutations.contains(where: {
                $0.mutationId == mutation.mutationId
            }) {
                nextState.pendingMutations.append(mutation)
            }
            return nextState
        }
    }

    func deleteFloater(_ floater: TodoItem) async throws {
        let now = Date().epochMilliseconds
        _ = try await cacheManager.updateOfflineState { state in
            var nextState = state
            nextState.floaters.removeAll { $0.canonicalId == floater.canonicalId }
            nextState.pendingMutations.removeAll { $0.targetId == floater.canonicalId && ($0.kind == .createFloater || $0.kind == .updateFloater) }
            if !floater.canonicalId.hasPrefix(LOCAL_FLOATER_PREFIX) {
                nextState.pendingMutations.append(
                    PendingMutationRecord(
                        mutationId: UUID().uuidString,
                        kind: .deleteFloater,
                        targetId: floater.canonicalId,
                        timestampEpochMs: now,
                        title: nil,
                        description: nil,
                        priority: nil,
                        dueEpochMs: nil,
                        rrule: nil,
                        listId: nil,
                        pinned: nil,
                        completed: nil,
                        instanceDateEpochMs: nil,
                        name: nil,
                        color: nil,
                        iconKey: nil
                    )
                )
            }
            return nextState
        }
        if syncManager.isLocalMode {
            return
        }
        let result = await syncManager.syncCachedData(force: true, replayPendingMutations: true)
        if case let .failure(error) = result, isLikelyUnrecoverableMutationError(error) {
            throw error
        }
    }

    func completeTodo(_ todo: TodoItem) async throws {
        let now = Date().epochMilliseconds
        let mutationID = UUID().uuidString
        let instanceDateEpochMs = todo.instanceDateEpochMilliseconds
        let mutationKind: MutationKind = todo.isRecurring && instanceDateEpochMs != nil ? .completeTodoInstance : .completeTodo
        _ = try await cacheManager.updateOfflineState { state in
            var nextState = state
            nextState.todos = state.todos.map { current in
                guard current.canonicalId == todo.canonicalId else {
                    return current
                }
                if todo.isRecurring && instanceDateEpochMs != nil && current.instanceDateEpochMs != instanceDateEpochMs {
                    return current
                }
                return self.withCompletion(current, completed: true, updatedAtEpochMs: now)
            }
            nextState.completedItems.insert(
                CachedCompletedRecord(
                    id: LOCAL_COMPLETED_PREFIX + UUID().uuidString.lowercased(),
                    originalTodoId: todo.canonicalId,
                    title: todo.title,
                    description: todo.description,
                    priority: todo.priority,
                    dueEpochMs: todo.due?.epochMilliseconds,
                    completedAtEpochMs: now,
                    rrule: todo.rrule,
                    instanceDateEpochMs: todo.instanceDateEpochMilliseconds,
                    listId: todo.listId,
                    listName: state.lists.first(where: { $0.id == todo.listId })?.name,
                    listColor: state.lists.first(where: { $0.id == todo.listId })?.color
                ),
                at: 0
            )
            nextState.pendingMutations.append(
                PendingMutationRecord(
                    mutationId: mutationID,
                    kind: mutationKind,
                    targetId: todo.canonicalId,
                    timestampEpochMs: now,
                    title: nil,
                    description: nil,
                    priority: nil,
                    dueEpochMs: nil,
                    rrule: nil,
                    listId: nil,
                    pinned: nil,
                    completed: true,
                    instanceDateEpochMs: instanceDateEpochMs,
                    name: nil,
                    color: nil,
                    iconKey: nil
                )
            )
            return nextState
        }
        if syncManager.isLocalMode {
            return
        }
        let result = await syncManager.syncCachedData(force: true, replayPendingMutations: true)
        if case let .failure(error) = result, isLikelyUnrecoverableMutationError(error) {
            throw error
        }
    }

    func completeFloater(_ floater: TodoItem) async throws {
        let now = Date().epochMilliseconds
        let mutationID = UUID().uuidString
        _ = try await cacheManager.updateOfflineState { state in
            var nextState = state
            nextState.floaters = state.floaters.map { current in
                guard current.canonicalId == floater.canonicalId else {
                    return current
                }
                return CachedFloaterRecord(
                    id: current.id,
                    canonicalId: current.canonicalId,
                    title: current.title,
                    description: current.description,
                    priority: current.priority,
                    pinned: current.pinned,
                    completed: true,
                    listId: current.listId,
                    updatedAtEpochMs: now
                )
            }
            nextState.completedFloaters.insert(
                CachedCompletedFloaterRecord(
                    id: LOCAL_COMPLETED_FLOATER_PREFIX + UUID().uuidString.lowercased(),
                    originalFloaterId: floater.canonicalId,
                    title: floater.title,
                    description: floater.description,
                    priority: floater.priority,
                    completedAtEpochMs: now,
                    listId: floater.listId,
                    listName: state.floaterLists.first(where: { $0.id == floater.listId })?.name,
                    listColor: state.floaterLists.first(where: { $0.id == floater.listId })?.color
                ),
                at: 0
            )
            nextState.pendingMutations.append(
                PendingMutationRecord(
                    mutationId: mutationID,
                    kind: .completeFloater,
                    targetId: floater.canonicalId,
                    timestampEpochMs: now,
                    title: nil,
                    description: nil,
                    priority: nil,
                    dueEpochMs: nil,
                    rrule: nil,
                    listId: nil,
                    pinned: nil,
                    completed: true,
                    instanceDateEpochMs: nil,
                    name: nil,
                    color: nil,
                    iconKey: nil
                )
            )
            return nextState
        }
        if syncManager.isLocalMode {
            return
        }
        let result = await syncManager.syncCachedData(force: true, replayPendingMutations: true)
        if case let .failure(error) = result, isLikelyUnrecoverableMutationError(error) {
            throw error
        }
    }

    /// Schedules a floater into a real Todo. Optimistically moves the row
    /// between the cached silos; the replay case remaps the interim
    /// `local-todo-` id (carried in the mutation's spare `name` field) to the
    /// server id, exactly like CREATE_TODO reconciliation.
    func promoteFloater(_ floater: TodoItem, due: Date, rrule: String?) async throws {
        let now = Date().epochMilliseconds
        let mutationID = UUID().uuidString
        let localTodoID = LOCAL_TODO_PREFIX + UUID().uuidString.lowercased()
        _ = try await cacheManager.updateOfflineState { state in
            var nextState = state
            nextState.floaters = state.floaters.filter { $0.canonicalId != floater.canonicalId }
            nextState.todos.append(
                CachedTodoRecord(
                    id: localTodoID,
                    canonicalId: localTodoID,
                    title: floater.title,
                    description: floater.description,
                    priority: floater.priority,
                    dueEpochMs: due.epochMilliseconds,
                    rrule: rrule,
                    instanceDateEpochMs: nil,
                    pinned: floater.pinned,
                    completed: false,
                    // Floater lists and todo lists are separate types; membership stays behind.
                    listId: nil,
                    updatedAtEpochMs: now
                )
            )
            nextState.pendingMutations.append(
                PendingMutationRecord(
                    mutationId: mutationID,
                    kind: .promoteFloater,
                    targetId: floater.canonicalId,
                    timestampEpochMs: now,
                    title: nil, description: nil, priority: nil,
                    dueEpochMs: due.epochMilliseconds,
                    rrule: rrule,
                    listId: nil, pinned: nil, completed: nil,
                    instanceDateEpochMs: nil,
                    name: localTodoID,
                    color: nil, iconKey: nil
                )
            )
            return nextState
        }
        if syncManager.isLocalMode {
            return
        }
        let result = await syncManager.syncCachedData(force: true, replayPendingMutations: true)
        if case let .failure(error) = result, isLikelyUnrecoverableMutationError(error) {
            throw error
        }
    }

    /// "Let it float": demotes a todo into an Anytime floater. Recurring todos
    /// are rejected server-side (their series would be silently destroyed), so
    /// callers hide the action for them; this guards anyway.
    func demoteTodo(_ todo: TodoItem) async throws {
        guard !todo.isRecurring else { return }
        let now = Date().epochMilliseconds
        let mutationID = UUID().uuidString
        let localFloaterID = LOCAL_FLOATER_PREFIX + UUID().uuidString.lowercased()
        _ = try await cacheManager.updateOfflineState { state in
            var nextState = state
            nextState.todos = state.todos.filter { $0.canonicalId != todo.canonicalId }
            nextState.floaters.append(
                CachedFloaterRecord(
                    id: localFloaterID,
                    canonicalId: localFloaterID,
                    title: todo.title,
                    description: todo.description,
                    priority: todo.priority,
                    pinned: todo.pinned,
                    completed: false,
                    // Todo lists and floater lists are separate types; membership stays behind.
                    listId: nil,
                    updatedAtEpochMs: now
                )
            )
            nextState.pendingMutations.append(
                PendingMutationRecord(
                    mutationId: mutationID,
                    kind: .demoteTodo,
                    targetId: todo.canonicalId,
                    timestampEpochMs: now,
                    title: nil, description: nil, priority: nil,
                    dueEpochMs: nil, rrule: nil,
                    listId: nil, pinned: nil, completed: nil,
                    instanceDateEpochMs: nil,
                    name: localFloaterID,
                    color: nil, iconKey: nil
                )
            )
            return nextState
        }
        if syncManager.isLocalMode {
            return
        }
        let result = await syncManager.syncCachedData(force: true, replayPendingMutations: true)
        if case let .failure(error) = result, isLikelyUnrecoverableMutationError(error) {
            throw error
        }
    }

    func setPinned(_ todo: TodoItem, pinned: Bool) async throws {
        try await updateSimpleTodoMutation(todo, kind: .setPinned, pinned: pinned, priority: nil)
    }

    func setPriority(_ todo: TodoItem, priority: String) async throws {
        try await updateSimpleTodoMutation(todo, kind: .setPriority, pinned: nil, priority: normalizedPriority(priority))
    }

    func summarizeTodos(mode: TodoListMode, listId: String? = nil) async throws -> TodoSummaryResponse {
        if syncManager.isLocalMode {
            throw APIError(message: "Summary is unavailable in local mode", statusCode: nil)
        }

        return try await api.summarizeTodos(
            payload: TodoSummaryRequest(
                mode: mode.rawValue,
                listId: listId,
                timeZone: TimeZone.current.identifier
            )
        )
    }

    func parseTodoTitleNlp(text: String, referenceDueEpochMs: Int64) async -> TodoTitleNlpResponse? {
        // Parsed entirely on-device (offline, no AI/network), so it also works in
        // local mode. `text` is passed raw so the matched span lines up with what's
        // shown in the title field for the highlight.
        OnDeviceTitleNlpParser.parse(text: text)
    }

    private func withCompletion(_ record: CachedTodoRecord, completed: Bool, updatedAtEpochMs: Int64) -> CachedTodoRecord {
        CachedTodoRecord(
            id: record.id,
            canonicalId: record.canonicalId,
            title: record.title,
            description: record.description,
            priority: record.priority,
            dueEpochMs: record.dueEpochMs,
            rrule: record.rrule,
            instanceDateEpochMs: record.instanceDateEpochMs,
            pinned: record.pinned,
            completed: completed,
            listId: record.listId,
            updatedAtEpochMs: updatedAtEpochMs
        )
    }

    private func updateSimpleTodoMutation(_ todo: TodoItem, kind: MutationKind, pinned: Bool?, priority: String?) async throws {
        let now = Date().epochMilliseconds
        _ = try await cacheManager.updateOfflineState { state in
            var nextState = state
            nextState.todos = state.todos.map { current in
                guard current.canonicalId == todo.canonicalId && current.instanceDateEpochMs == todo.instanceDateEpochMilliseconds else {
                    return current
                }
                return CachedTodoRecord(
                    id: current.id,
                    canonicalId: current.canonicalId,
                    title: current.title,
                    description: current.description,
                    priority: priority ?? current.priority,
                    dueEpochMs: current.dueEpochMs,
                    rrule: current.rrule,
                    instanceDateEpochMs: current.instanceDateEpochMs,
                    pinned: pinned ?? current.pinned,
                    completed: current.completed,
                    listId: current.listId,
                    updatedAtEpochMs: now
                )
            }
            nextState.pendingMutations.removeAll { $0.kind == kind && $0.targetId == todo.canonicalId && $0.instanceDateEpochMs == todo.instanceDateEpochMilliseconds }
            nextState.pendingMutations.append(
                PendingMutationRecord(
                    mutationId: UUID().uuidString,
                    kind: kind,
                    targetId: todo.canonicalId,
                    timestampEpochMs: now,
                    title: nil,
                    description: nil,
                    priority: priority,
                    dueEpochMs: nil,
                    rrule: nil,
                    listId: nil,
                    pinned: pinned,
                    completed: nil,
                    instanceDateEpochMs: todo.instanceDateEpochMilliseconds,
                    name: nil,
                    color: nil,
                    iconKey: nil
                )
            )
            return nextState
        }
        if syncManager.isLocalMode {
            return
        }
        let result = await syncManager.syncCachedData(force: true, replayPendingMutations: true)
        if case let .failure(error) = result, isLikelyUnrecoverableMutationError(error) {
            throw error
        }
    }

    private func buildDashboardSummary(from state: OfflineSyncState) -> DashboardSummary {
        let timelineTodos = state.todos.map(todoFromCache).filter { !$0.completed && $0.due != nil }
        let floaters = state.floaters.map(floaterFromCache).filter { !$0.completed }
        let now = Date()
        let todayTodos = timelineTodos.filter { isTodayTodo($0, now: now) }
        let scheduledTodos = timelineTodos.filter { isScheduledTodo($0, now: now) }
        let todoCountsByList = Dictionary(grouping: timelineTodos, by: \.listId).mapValues(\.count)
        let lists = orderListsLikeWeb(state.lists).map { list in
            listFromCache(list, todoCountOverride: todoCountsByList[list.id] ?? 0)
        }

        return DashboardSummary(
            todayCount: todayTodos.count,
            scheduledCount: scheduledTodos.count,
            allCount: timelineTodos.count,
            priorityCount: timelineTodos.filter { isPriorityTodo($0.priority) }.count,
            floaterCount: floaters.count,
            completedCount: state.completedItems.count,
            lists: lists
        )
    }

    private func buildDashboardCacheSnapshot(from state: OfflineSyncState) -> TodoDashboardCacheSnapshot {
        let timelineTodos = state.todos.map(todoFromCache).filter { !$0.completed && $0.due != nil }
        let floaters = state.floaters.map(floaterFromCache).filter { !$0.completed }
        let now = Date()
        let todayTodos = timelineTodos.filter { isTodayTodo($0, now: now) }
        let scheduledTodos = timelineTodos.filter { isScheduledTodo($0, now: now) }
        let todoCountsByList = Dictionary(grouping: timelineTodos, by: \.listId).mapValues(\.count)
        let lists = orderListsLikeWeb(state.lists).map { list in
            listFromCache(list, todoCountOverride: todoCountsByList[list.id] ?? 0)
        }
        let summary = DashboardSummary(
            todayCount: todayTodos.count,
            scheduledCount: scheduledTodos.count,
            allCount: timelineTodos.count,
            priorityCount: timelineTodos.filter { isPriorityTodo($0.priority) }.count,
            floaterCount: floaters.count,
            completedCount: state.completedItems.count,
            lists: lists
        )

        return TodoDashboardCacheSnapshot(
            summary: summary,
            searchableTodos: timelineTodos.sorted(by: todoSortPrecedes),
            todayTodos: todayTodos.sorted(by: todoSortPrecedes),
            aiSummaryEnabled: syncManager.isLocalMode ? false : state.aiSummaryEnabled
        )
    }

    private func buildListSummaries(from state: OfflineSyncState, mode: TodoListMode) -> [ListSummary] {
        if mode == .floater {
            let floaterCounts = Dictionary(grouping: state.floaters.filter { !$0.completed }, by: { $0.listId })
                .mapValues(\.count)
            return orderFloaterListsLikeWeb(state.floaterLists)
                .map { floaterListFromCache($0, todoCountOverride: floaterCounts[$0.id] ?? 0) }
        }

        let scheduledCounts = Dictionary(grouping: state.todos.filter { !$0.completed && $0.dueEpochMs != nil }, by: { $0.listId })
            .mapValues(\.count)
        return orderListsLikeWeb(state.lists)
            .map { listFromCache($0, todoCountOverride: scheduledCounts[$0.id] ?? 0) }
    }

    private func buildTodos(from state: OfflineSyncState, mode: TodoListMode, listId: String?) -> [TodoItem] {
        let items = state.todos.map(todoFromCache).filter { !$0.completed && $0.due != nil }
        let floaters = state.floaters.map(floaterFromCache).filter { !$0.completed }
        let now = Date()

        let filtered: [TodoItem]
        switch mode {
        case .today:
            filtered = items.filter { isTodayTodo($0, now: now) }
        case .overdue:
            filtered = items.filter { isOverdueTodo($0, now: now) }
        case .scheduled:
            filtered = items.filter { isScheduledTodo($0, now: now) }
        case .all:
            filtered = items
        case .priority:
            filtered = items.filter { isPriorityTodo($0.priority) }
        case .floater:
            filtered = listId.nilIfBlank.map { id in
                floaters.filter { $0.listId == id }
            } ?? floaters
        case .list:
            filtered = items.filter { $0.listId == listId }
        }

        return filtered.sorted(by: todoSortPrecedes)
    }

    private func normalizedPriority(_ priority: String) -> String {
        TaskPriorityDisplay.canonicalValue(priority)
    }

    private func isTodayTodo(_ todo: TodoItem, now: Date = Date()) -> Bool {
        let calendar = Calendar.current
        let startOfToday = calendar.startOfDay(for: now)
        guard let startOfTomorrow = calendar.date(byAdding: .day, value: 1, to: startOfToday) else {
            return false
        }
        guard let due = todo.due else { return false }
        return due >= startOfToday && due < startOfTomorrow
    }

    private func isScheduledTodo(_ todo: TodoItem, now: Date = Date()) -> Bool {
        guard let due = todo.due else { return false }
        return due >= now
    }

    private func isOverdueTodo(_ todo: TodoItem, now: Date = Date()) -> Bool {
        guard let due = todo.due else { return false }
        return due < now
    }

    private func isPriorityTodo(_ priority: String?) -> Bool {
        guard let normalized = priority?.trimmingCharacters(in: .whitespacesAndNewlines).lowercased() else {
            return false
        }
        return normalized == "medium" || normalized == "high" || normalized == "important" || normalized == "urgent"
    }
}

/// On-device natural-language date parser for the new-task title field.
///
/// Fully offline — no AI model, no network. Uses Foundation's `NSDataDetector`
/// (Apple's built-in date detector) to mirror the web's chrono-node behaviour:
/// it detects a phrase like "July 29 2030 at 8pm", returning the matched span
/// (for the in-field highlight), the cleaned title (phrase removed), and the
/// absolute due instant. `NSDataDetector` resolves relative phrases ("tomorrow")
/// against the current date. The resulting instant is converted to UTC on save.
enum OnDeviceTitleNlpParser {
    private static let detector: NSDataDetector? = {
        try? NSDataDetector(types: NSTextCheckingResult.CheckingType.date.rawValue)
    }()

    static func parse(text: String) -> TodoTitleNlpResponse? {
        guard !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
              let detector else {
            return nil
        }
        let fullRange = NSRange(text.startIndex ..< text.endIndex, in: text)
        guard let match = detector.firstMatch(in: text, options: [], range: fullRange),
              match.resultType == .date,
              let date = match.date,
              let matchedRange = Range(match.range, in: text) else {
            return nil
        }

        // `match.date` already resolves wall-clock phrases ("8pm", "tomorrow") in the
        // device's local zone, so "3pm" maps to 3pm local and `epochMilliseconds`
        // is the correct UTC instant. (An earlier version assumed NSDataDetector
        // returned GMT and re-shifted by the UTC offset — that double-shifted the
        // time, e.g. 3pm → 7pm in EDT.)

        let matchedText = String(text[matchedRange])
        var clean = text
        clean.removeSubrange(matchedRange)
        let cleanTitle = clean
            .replacingOccurrences(of: "\\s{2,}", with: " ", options: .regularExpression)
            .trimmingCharacters(in: .whitespacesAndNewlines)

        return TodoTitleNlpResponse(
            cleanTitle: cleanTitle,
            matchedText: matchedText,
            matchStart: match.range.location,
            dueEpochMs: date.epochMilliseconds
        )
    }
}
