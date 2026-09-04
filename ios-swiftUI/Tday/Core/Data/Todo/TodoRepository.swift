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

/// Snapshot of everything `stageCompleteTodo(s)` wrote into the local cache —
/// the pre-completion row(s), the new completed-history record(s), and the new
/// queued mutation(s) — so `undoStagedCompletion(_:)` can restore the exact
/// pre-completion state.
struct StagedTodoCompletion {
    let todos: [CachedTodoRecord]
    let completedItems: [CachedCompletedRecord]
    let pendingMutations: [PendingMutationRecord]
}

/// Floater sibling of `StagedTodoCompletion`.
struct StagedFloaterCompletion {
    let floaters: [CachedFloaterRecord]
    let completedFloaters: [CachedCompletedFloaterRecord]
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
        let normalizedDue = (payload.due ?? Date().addingTimeInterval(60 * 60)).flooredToMinute()
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
        let normalizedDue = (payload.due ?? todo.due ?? Date().addingTimeInterval(60 * 60)).flooredToMinute()
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
        let dueEpochMs = due.flooredToMinute().epochMilliseconds
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
        stageDeleteTodos([todo])
    }

    /// Bulk sibling of `stageDeleteTodo(_:)`: prunes the whole selection inside
    /// ONE cache write and returns one combined snapshot. `undoStagedTodo(_:)`
    /// already restores arrays and is already idempotent, so undoing a batch
    /// needs no new code — and one snapshot means one undo toast for the batch
    /// rather than N toasts racing N commit timers.
    func stageDeleteTodos(_ todos: [TodoItem]) -> StagedTodoDeletion {
        guard !todos.isEmpty else {
            return StagedTodoDeletion(todos: [], pendingMutations: [])
        }
        var removedTodos: [CachedTodoRecord] = []
        var removedMutations: [PendingMutationRecord] = []
        cacheManager.updateOfflineState { state in
            var nextState = state
            for todo in todos {
                removedTodos.append(contentsOf: nextState.todos.filter {
                    $0.canonicalId == todo.canonicalId && $0.instanceDateEpochMs == todo.instanceDateEpochMilliseconds
                })
                removedMutations.append(contentsOf: nextState.pendingMutations.filter {
                    $0.targetId == todo.canonicalId && ($0.kind == .createTodo || $0.kind == .updateTodo)
                })
                nextState.todos.removeAll { $0.canonicalId == todo.canonicalId && $0.instanceDateEpochMs == todo.instanceDateEpochMilliseconds }
                nextState.pendingMutations.removeAll { $0.targetId == todo.canonicalId && ($0.kind == .createTodo || $0.kind == .updateTodo) }
            }
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
            self.applyingDeletion(of: todo, to: state, now: now)
        }
        try await syncAfterMutation()
    }

    /// First half of a delayed-commit delete: prunes the floater (and its
    /// pending create/update mutations) from the local cache without queueing
    /// the server delete. Commit later by calling `deleteFloater(_:)` — its
    /// prune half re-runs as a no-op — or restore with `undoStagedFloater(_:)`.
    func stageDeleteFloater(_ floater: TodoItem) -> StagedFloaterDeletion {
        stageDeleteFloaters([floater])
    }

    /// Bulk sibling of `stageDeleteFloater(_:)` — see `stageDeleteTodos(_:)`.
    func stageDeleteFloaters(_ floaters: [TodoItem]) -> StagedFloaterDeletion {
        guard !floaters.isEmpty else {
            return StagedFloaterDeletion(floaters: [], pendingMutations: [])
        }
        var removedFloaters: [CachedFloaterRecord] = []
        var removedMutations: [PendingMutationRecord] = []
        cacheManager.updateOfflineState { state in
            var nextState = state
            for floater in floaters {
                removedFloaters.append(contentsOf: nextState.floaters.filter { $0.canonicalId == floater.canonicalId })
                removedMutations.append(contentsOf: nextState.pendingMutations.filter {
                    $0.targetId == floater.canonicalId && ($0.kind == .createFloater || $0.kind == .updateFloater)
                })
                nextState.floaters.removeAll { $0.canonicalId == floater.canonicalId }
                nextState.pendingMutations.removeAll { $0.targetId == floater.canonicalId && ($0.kind == .createFloater || $0.kind == .updateFloater) }
            }
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
            self.applyingFloaterDeletion(of: floater, to: state, now: now)
        }
        try await syncAfterMutation()
    }

    func completeTodo(_ todo: TodoItem) async throws {
        let now = Date().epochMilliseconds
        _ = try await cacheManager.updateOfflineState { state in
            self.applyingCompletion(of: todo, to: state, now: now)
        }
        try await syncAfterMutation()
    }

    func completeFloater(_ floater: TodoItem) async throws {
        let now = Date().epochMilliseconds
        _ = try await cacheManager.updateOfflineState { state in
            self.applyingFloaterCompletion(of: floater, to: state, now: now)
        }
        try await syncAfterMutation()
    }

    /// First half of a delayed-commit complete: writes the completion (row
    /// flipped to `completed: true`, a new `CachedCompletedRecord`, and a
    /// queued `COMPLETE_TODO`/`COMPLETE_TODO_INSTANCE` mutation) straight to the
    /// local cache, durably, at the moment of the tap — not deferred to commit.
    ///
    /// This differs from `stageDeleteTodo(_:)` on purpose. A staged delete can
    /// safely defer queuing its mutation to commit because leaving it queued
    /// costs nothing (the row is already gone from the screen either way and a
    /// hard delete can't be replayed faithfully after the fact per the
    /// scheduler's own doc comment). A staged complete cannot: if the process
    /// dies inside the undo window with nothing durable yet, the completion the
    /// user already saw silently reverts on next launch with no trace, because
    /// there is nothing to replay. Writing it now means a crash or kill inside
    /// the window loses nothing — `applyPendingMutations` replays the queued
    /// mutation on next launch like any other.
    ///
    /// Commit later with `syncPendingMutations()` (it only needs to flush the
    /// already-queued mutation, not re-run this transform) or restore with
    /// `undoStagedCompletion(_:)`.
    func stageCompleteTodo(_ todo: TodoItem) -> StagedTodoCompletion {
        stageCompleteTodos([todo])
    }

    /// Bulk sibling of `stageCompleteTodo(_:)`: folds the whole selection into
    /// ONE cache write and returns one combined snapshot, so completing 100
    /// rows costs one full-cache rewrite here instead of 100 — see the note
    /// above `completeTodos(_:)` for why that matters.
    func stageCompleteTodos(_ todos: [TodoItem]) -> StagedTodoCompletion {
        guard !todos.isEmpty else {
            return StagedTodoCompletion(todos: [], completedItems: [], pendingMutations: [])
        }
        var previousTodos: [CachedTodoRecord] = []
        var addedCompletedItems: [CachedCompletedRecord] = []
        var addedMutations: [PendingMutationRecord] = []
        let now = Date().epochMilliseconds
        cacheManager.updateOfflineState { state in
            let beforeCompletedIds = Set(state.completedItems.map(\.id))
            let beforeMutationIds = Set(state.pendingMutations.map(\.mutationId))
            var nextState = state
            for todo in todos {
                previousTodos.append(contentsOf: nextState.todos.filter {
                    $0.canonicalId == todo.canonicalId && $0.instanceDateEpochMs == todo.instanceDateEpochMilliseconds
                })
                nextState = self.applyingCompletion(of: todo, to: nextState, now: now)
            }
            addedCompletedItems = nextState.completedItems.filter { !beforeCompletedIds.contains($0.id) }
            addedMutations = nextState.pendingMutations.filter { !beforeMutationIds.contains($0.mutationId) }
            return nextState
        }
        return StagedTodoCompletion(todos: previousTodos, completedItems: addedCompletedItems, pendingMutations: addedMutations)
    }

    /// Restores the local state captured by `stageCompleteTodo(s)`: puts the
    /// pre-completion row(s) back and removes exactly the completed-history
    /// record(s) and queued mutation(s) that staging added — idempotent the
    /// same way `undoStagedTodo(_:)` is. Known edge case, shared with every
    /// other delayed-commit action in this scheduler: if an unrelated sync
    /// drains the queued mutation before Undo is tapped, the completion has
    /// already reached the server and this only reverts the local copy — the
    /// same trade-off `undoStagedTodo(_:)` already makes for delete.
    func undoStagedCompletion(_ staged: StagedTodoCompletion) {
        guard !staged.todos.isEmpty || !staged.completedItems.isEmpty || !staged.pendingMutations.isEmpty else {
            return
        }
        cacheManager.updateOfflineState { state in
            var nextState = state
            for record in staged.todos {
                nextState.todos.removeAll {
                    $0.canonicalId == record.canonicalId && $0.instanceDateEpochMs == record.instanceDateEpochMs
                }
                nextState.todos.append(record)
            }
            let completedIdsToRemove = Set(staged.completedItems.map(\.id))
            nextState.completedItems.removeAll { completedIdsToRemove.contains($0.id) }
            let mutationIdsToRemove = Set(staged.pendingMutations.map(\.mutationId))
            nextState.pendingMutations.removeAll { mutationIdsToRemove.contains($0.mutationId) }
            return nextState
        }
    }

    /// Floater sibling of `stageCompleteTodo(_:)` — see its doc comment.
    func stageCompleteFloater(_ floater: TodoItem) -> StagedFloaterCompletion {
        stageCompleteFloaters([floater])
    }

    /// Floater sibling of `stageCompleteTodos(_:)`.
    func stageCompleteFloaters(_ floaters: [TodoItem]) -> StagedFloaterCompletion {
        guard !floaters.isEmpty else {
            return StagedFloaterCompletion(floaters: [], completedFloaters: [], pendingMutations: [])
        }
        var previousFloaters: [CachedFloaterRecord] = []
        var addedCompletedFloaters: [CachedCompletedFloaterRecord] = []
        var addedMutations: [PendingMutationRecord] = []
        let now = Date().epochMilliseconds
        cacheManager.updateOfflineState { state in
            let beforeCompletedIds = Set(state.completedFloaters.map(\.id))
            let beforeMutationIds = Set(state.pendingMutations.map(\.mutationId))
            var nextState = state
            for floater in floaters {
                previousFloaters.append(contentsOf: nextState.floaters.filter { $0.canonicalId == floater.canonicalId })
                nextState = self.applyingFloaterCompletion(of: floater, to: nextState, now: now)
            }
            addedCompletedFloaters = nextState.completedFloaters.filter { !beforeCompletedIds.contains($0.id) }
            addedMutations = nextState.pendingMutations.filter { !beforeMutationIds.contains($0.mutationId) }
            return nextState
        }
        return StagedFloaterCompletion(floaters: previousFloaters, completedFloaters: addedCompletedFloaters, pendingMutations: addedMutations)
    }

    /// Floater sibling of `undoStagedCompletion(_:)`.
    func undoStagedFloaterCompletion(_ staged: StagedFloaterCompletion) {
        guard !staged.floaters.isEmpty || !staged.completedFloaters.isEmpty || !staged.pendingMutations.isEmpty else {
            return
        }
        cacheManager.updateOfflineState { state in
            var nextState = state
            for record in staged.floaters {
                nextState.floaters.removeAll { $0.canonicalId == record.canonicalId }
                nextState.floaters.append(record)
            }
            let completedIdsToRemove = Set(staged.completedFloaters.map(\.id))
            nextState.completedFloaters.removeAll { completedIdsToRemove.contains($0.id) }
            let mutationIdsToRemove = Set(staged.pendingMutations.map(\.mutationId))
            nextState.pendingMutations.removeAll { mutationIdsToRemove.contains($0.mutationId) }
            return nextState
        }
    }

    /// Commit half of a staged complete: `stageCompleteTodo(s)` /
    /// `stageCompleteFloater(s)` already wrote the completion and queued its
    /// mutation at the moment of the tap, so committing only needs to flush the
    /// queue — re-running a transform here would double the completed-history
    /// record and send the completion twice.
    func syncPendingMutations() async throws {
        try await syncAfterMutation()
    }

    /// Applies completions tapped on the home-screen widgets (widgets v2).
    /// The widget process cannot reach the cache, so each check-ring tap only
    /// queued a `{kind, id}` descriptor in the app group; here every
    /// descriptor resolves to its cached record and rides the normal complete
    /// path — optimistic cache write, queued mutation, sync in Server Mode,
    /// widget snapshot refresh.
    func drainWidgetCompletions() async {
        let entries = WidgetPendingCompletionQueue.drain()
        guard !entries.isEmpty else {
            return
        }
        guard let state = try? await cacheManager.loadOfflineState() else {
            return
        }
        for entry in entries {
            switch entry.kind {
            case WidgetPendingCompletionQueue.todoKind:
                guard let record = state.todos.first(where: { $0.id == entry.id && !$0.completed }) else {
                    continue
                }
                let todo = TodoItem(
                    id: record.id,
                    canonicalId: record.canonicalId,
                    title: record.title,
                    description: record.description,
                    priority: record.priority,
                    due: record.dueEpochMs.map { Date(epochMilliseconds: $0) },
                    rrule: record.rrule,
                    instanceDate: record.instanceDateEpochMs.map { Date(epochMilliseconds: $0) },
                    pinned: record.pinned,
                    completed: record.completed,
                    listId: record.listId,
                    updatedAt: nil
                )
                try? await completeTodo(todo)
            case WidgetPendingCompletionQueue.floaterKind:
                guard let record = state.floaters.first(where: { $0.id == entry.id && !$0.completed }) else {
                    continue
                }
                let floater = TodoItem(
                    id: record.id,
                    canonicalId: record.canonicalId,
                    title: record.title,
                    description: record.description,
                    priority: record.priority,
                    due: nil,
                    rrule: nil,
                    instanceDate: nil,
                    pinned: record.pinned,
                    completed: record.completed,
                    listId: record.listId,
                    updatedAt: nil
                )
                try? await completeFloater(floater)
            default:
                continue
            }
        }
    }

    /// Schedules a floater into a real Todo. Optimistically moves the row
    /// between the cached silos; the replay case remaps the interim
    /// `local-todo-` id (carried in the mutation's spare `name` field) to the
    /// server id, exactly like CREATE_TODO reconciliation.
    func promoteFloater(_ floater: TodoItem, due: Date, rrule: String?) async throws {
        let now = Date().epochMilliseconds
        let dueEpochMs = due.flooredToMinute().epochMilliseconds
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
                    dueEpochMs: dueEpochMs,
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
                    dueEpochMs: dueEpochMs,
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

    // MARK: - Bulk (multi-select) actions

    // Multi-select fans out to the same single-item endpoints everything else
    // already uses — see `docs/design/bulk-selection.md` §1, which settles that
    // there is deliberately no batch route. It must NOT fan out to the same
    // single-item *repository* calls, though: each of those runs its own
    // `updateOfflineState` (load → transform → save → widget snapshot →
    // cache-version bump) and its own `syncCachedData`, and the list view model
    // re-hydrates on every bump. A hundred of those is a hundred full cache
    // rewrites and a hundred syncs.
    //
    // So every method below folds the whole selection into ONE cache write and
    // triggers ONE sync. The queued pending mutations stay per-item, which is
    // what keeps replay, Local Mode and the offline queue untouched.
    //
    // Recurring rows are filtered out by the caller for delete/priority/move
    // (§4.1): those three have no per-occurrence route and would silently act on
    // the whole series. Bulk complete keeps them and carries each occurrence's
    // `instanceDate`, which the backend needs or it writes a phantom history row.

    func completeTodos(_ todos: [TodoItem]) async throws {
        guard !todos.isEmpty else { return }
        let now = Date().epochMilliseconds
        _ = try await cacheManager.updateOfflineState { state in
            var nextState = state
            for todo in todos {
                nextState = self.applyingCompletion(of: todo, to: nextState, now: now)
            }
            return nextState
        }
        try await syncAfterMutation()
    }

    func completeFloaters(_ floaters: [TodoItem]) async throws {
        guard !floaters.isEmpty else { return }
        let now = Date().epochMilliseconds
        _ = try await cacheManager.updateOfflineState { state in
            var nextState = state
            for floater in floaters {
                nextState = self.applyingFloaterCompletion(of: floater, to: nextState, now: now)
            }
            return nextState
        }
        try await syncAfterMutation()
    }

    /// Commit half of a staged bulk delete. The prune re-runs as a no-op on rows
    /// `stageDeleteTodos(_:)` already removed; what it adds is the queued
    /// `.deleteTodo` mutation per row.
    func deleteTodos(_ todos: [TodoItem]) async throws {
        guard !todos.isEmpty else { return }
        let now = Date().epochMilliseconds
        _ = try await cacheManager.updateOfflineState { state in
            var nextState = state
            for todo in todos {
                nextState = self.applyingDeletion(of: todo, to: nextState, now: now)
            }
            return nextState
        }
        try await syncAfterMutation()
    }

    func deleteFloaters(_ floaters: [TodoItem]) async throws {
        guard !floaters.isEmpty else { return }
        let now = Date().epochMilliseconds
        _ = try await cacheManager.updateOfflineState { state in
            var nextState = state
            for floater in floaters {
                nextState = self.applyingFloaterDeletion(of: floater, to: nextState, now: now)
            }
            return nextState
        }
        try await syncAfterMutation()
    }

    func setPriorityForTodos(_ todos: [TodoItem], priority: String) async throws {
        guard !todos.isEmpty else { return }
        let now = Date().epochMilliseconds
        let normalized = normalizedPriority(priority)
        _ = try await cacheManager.updateOfflineState { state in
            var nextState = state
            for todo in todos {
                nextState = self.applyingTodoPriority(of: todo, priority: normalized, to: nextState, now: now)
            }
            return nextState
        }
        try await syncAfterMutation()
    }

    func setPriorityForFloaters(_ floaters: [TodoItem], priority: String) async throws {
        guard !floaters.isEmpty else { return }
        let now = Date().epochMilliseconds
        let normalized = normalizedPriority(priority)
        _ = try await cacheManager.updateOfflineState { state in
            var nextState = state
            for floater in floaters {
                nextState = self.applyingFloaterPriority(of: floater, priority: normalized, to: nextState, now: now)
            }
            return nextState
        }
        try await syncAfterMutation()
    }

    /// `listId == nil` means "No list". Only scheduled lists are valid targets
    /// here; a floater moves between floater lists via `moveFloatersToList`.
    func moveTodosToList(_ todos: [TodoItem], listId: String?) async throws {
        guard !todos.isEmpty else { return }
        let now = Date().epochMilliseconds
        _ = try await cacheManager.updateOfflineState { state in
            var nextState = state
            for todo in todos {
                nextState = self.applyingTodoListMove(of: todo, toListId: listId, to: nextState, now: now)
            }
            return nextState
        }
        try await syncAfterMutation()
    }

    func moveFloatersToList(_ floaters: [TodoItem], listId: String?) async throws {
        guard !floaters.isEmpty else { return }
        let now = Date().epochMilliseconds
        _ = try await cacheManager.updateOfflineState { state in
            var nextState = state
            for floater in floaters {
                nextState = self.applyingFloaterListMove(of: floater, toListId: listId, to: nextState, now: now)
            }
            return nextState
        }
        try await syncAfterMutation()
    }

    // MARK: - Cache transforms
    //
    // Pure `state -> state` halves of the mutating methods above, so the
    // single-task path and the bulk path cannot drift apart. Each one queues its
    // own per-item pending mutation; only the surrounding cache write and sync
    // are shared when a batch folds them.

    /// The tail every mutating method shares: nothing to send in Local Mode, and
    /// only a genuinely unrecoverable server answer is worth surfacing — offline
    /// is absorbed by the pending-mutation queue and announced separately.
    private func syncAfterMutation() async throws {
        if syncManager.isLocalMode {
            return
        }
        let result = await syncManager.syncCachedData(force: true, replayPendingMutations: true)
        if case let .failure(error) = result, isLikelyUnrecoverableMutationError(error) {
            throw error
        }
    }

    private func applyingCompletion(of todo: TodoItem, to state: OfflineSyncState, now: Int64) -> OfflineSyncState {
        let instanceDateEpochMs = todo.instanceDateEpochMilliseconds
        let mutationKind: MutationKind = todo.isRecurring && instanceDateEpochMs != nil ? .completeTodoInstance : .completeTodo
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
                mutationId: UUID().uuidString,
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

    private func applyingFloaterCompletion(of floater: TodoItem, to state: OfflineSyncState, now: Int64) -> OfflineSyncState {
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
                mutationId: UUID().uuidString,
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

    private func applyingDeletion(of todo: TodoItem, to state: OfflineSyncState, now: Int64) -> OfflineSyncState {
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

    private func applyingFloaterDeletion(of floater: TodoItem, to state: OfflineSyncState, now: Int64) -> OfflineSyncState {
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

    /// Scheduled tasks take the dedicated prioritize route (`.setPriority` →
    /// `PATCH /api/todo/prioritize`) rather than a whole-record PATCH.
    private func applyingTodoPriority(of todo: TodoItem, priority: String, to state: OfflineSyncState, now: Int64) -> OfflineSyncState {
        var nextState = state
        nextState.todos = state.todos.map { current in
            guard current.canonicalId == todo.canonicalId,
                  current.instanceDateEpochMs == todo.instanceDateEpochMilliseconds else {
                return current
            }
            return CachedTodoRecord(
                id: current.id,
                canonicalId: current.canonicalId,
                title: current.title,
                description: current.description,
                priority: priority,
                dueEpochMs: current.dueEpochMs,
                rrule: current.rrule,
                instanceDateEpochMs: current.instanceDateEpochMs,
                pinned: current.pinned,
                completed: current.completed,
                listId: current.listId,
                updatedAtEpochMs: now
            )
        }
        nextState.pendingMutations.removeAll {
            $0.kind == .setPriority && $0.targetId == todo.canonicalId && $0.instanceDateEpochMs == todo.instanceDateEpochMilliseconds
        }
        nextState.pendingMutations.append(
            PendingMutationRecord(
                mutationId: UUID().uuidString,
                kind: .setPriority,
                targetId: todo.canonicalId,
                timestampEpochMs: now,
                title: nil,
                description: nil,
                priority: priority,
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
        return nextState
    }

    /// Floaters go through `.updateFloater` rather than `.setPriority`: replay
    /// only recognises a floater target for `.setPriority` when the remote
    /// snapshot already knows that id, so a floater created in the same session
    /// would be prioritized down the *todo* route.
    private func applyingFloaterPriority(of floater: TodoItem, priority: String, to state: OfflineSyncState, now: Int64) -> OfflineSyncState {
        applyingFloaterEdit(of: floater, to: state, now: now, clearsList: false) { current in
            CachedFloaterRecord(
                id: current.id,
                canonicalId: current.canonicalId,
                title: current.title,
                description: current.description,
                priority: priority,
                pinned: current.pinned,
                completed: current.completed,
                listId: current.listId,
                updatedAtEpochMs: now
            )
        }
    }

    private func applyingTodoListMove(of todo: TodoItem, toListId listId: String?, to state: OfflineSyncState, now: Int64) -> OfflineSyncState {
        let normalizedListID = listId.nilIfBlank
        var nextState = state
        var movedRecord: CachedTodoRecord?
        nextState.todos = state.todos.map { current in
            guard current.canonicalId == todo.canonicalId,
                  current.instanceDateEpochMs == todo.instanceDateEpochMilliseconds else {
                return current
            }
            let moved = CachedTodoRecord(
                id: current.id,
                canonicalId: current.canonicalId,
                title: current.title,
                description: current.description,
                priority: current.priority,
                dueEpochMs: current.dueEpochMs,
                rrule: current.rrule,
                instanceDateEpochMs: current.instanceDateEpochMs,
                pinned: current.pinned,
                completed: current.completed,
                listId: normalizedListID,
                updatedAtEpochMs: now
            )
            movedRecord = moved
            return moved
        }
        guard let movedRecord else {
            return state
        }
        nextState.pendingMutations.removeAll {
            $0.kind == .updateTodo && $0.targetId == todo.canonicalId && $0.instanceDateEpochMs == todo.instanceDateEpochMilliseconds
        }
        nextState.pendingMutations.append(
            PendingMutationRecord(
                mutationId: UUID().uuidString,
                kind: .updateTodo,
                targetId: todo.canonicalId,
                timestampEpochMs: now,
                title: movedRecord.title,
                description: movedRecord.description,
                priority: movedRecord.priority,
                dueEpochMs: movedRecord.dueEpochMs,
                rrule: movedRecord.rrule,
                // Blank, not nil, for "No list": replay reads a nil `listId` as
                // "leave the list alone", so nil could never clear one. The
                // backend maps a blank `listID` to null.
                listId: normalizedListID ?? "",
                pinned: movedRecord.pinned,
                completed: movedRecord.completed,
                instanceDateEpochMs: todo.instanceDateEpochMilliseconds,
                name: nil,
                color: nil,
                iconKey: nil
            )
        )
        return nextState
    }

    private func applyingFloaterListMove(of floater: TodoItem, toListId listId: String?, to state: OfflineSyncState, now: Int64) -> OfflineSyncState {
        let normalizedListID = listId.nilIfBlank
        return applyingFloaterEdit(of: floater, to: state, now: now, clearsList: normalizedListID == nil) { current in
            CachedFloaterRecord(
                id: current.id,
                canonicalId: current.canonicalId,
                title: current.title,
                description: current.description,
                priority: current.priority,
                pinned: current.pinned,
                completed: current.completed,
                listId: normalizedListID,
                updatedAtEpochMs: now
            )
        }
    }

    /// Rewrites one cached floater and queues the matching `.updateFloater`, so
    /// bulk priority and bulk move share one code path. `clearsList` is the only
    /// difference between them: a nil `listId` on the mutation means "leave the
    /// list alone", so clearing one has to send a blank instead.
    private func applyingFloaterEdit(
        of floater: TodoItem,
        to state: OfflineSyncState,
        now: Int64,
        clearsList: Bool,
        edit: (CachedFloaterRecord) -> CachedFloaterRecord
    ) -> OfflineSyncState {
        var nextState = state
        var editedRecord: CachedFloaterRecord?
        nextState.floaters = state.floaters.map { current in
            guard current.canonicalId == floater.canonicalId else {
                return current
            }
            let edited = edit(current)
            editedRecord = edited
            return edited
        }
        guard let editedRecord else {
            return state
        }
        // Blank, not nil, for "No list": replay reads a nil `listId` as "leave
        // the list alone", so nil could never clear one. The backend maps a
        // blank `listID` to null.
        var mutationListID: String? = editedRecord.listId
        if mutationListID == nil, clearsList {
            mutationListID = ""
        }
        nextState.pendingMutations.removeAll { $0.kind == .updateFloater && $0.targetId == floater.canonicalId }
        nextState.pendingMutations.append(
            PendingMutationRecord(
                mutationId: UUID().uuidString,
                kind: .updateFloater,
                targetId: floater.canonicalId,
                timestampEpochMs: now,
                title: editedRecord.title,
                description: editedRecord.description,
                priority: editedRecord.priority,
                dueEpochMs: nil,
                rrule: nil,
                listId: mutationListID,
                pinned: editedRecord.pinned,
                completed: editedRecord.completed,
                instanceDateEpochMs: nil,
                name: nil,
                color: nil,
                iconKey: nil
            )
        )
        return nextState
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
            // An active iOS Focus filter (R6-3) narrows Today to its chosen lists.
            filtered = items.filter { isTodayTodo($0, now: now) && TdayFocusFilterStore.allows(listId: $0.listId) }
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
        guard !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return nil }

        // Try a date phrase first (keeps the highlight span aligned with the raw field).
        if let detector {
            let fullRange = NSRange(text.startIndex ..< text.endIndex, in: text)
            if let match = detector.firstMatch(in: text, options: [], range: fullRange),
               match.resultType == .date,
               let date = match.date,
               let matchedRange = Range(match.range, in: text) {
                // `match.date` already resolves wall-clock phrases ("8pm", "tomorrow") in
                // the device's local zone, so `epochMilliseconds` is the correct UTC
                // instant.
                let matchedText = String(text[matchedRange])
                var clean = text
                clean.removeSubrange(matchedRange)
                let dateStripped = clean
                    .replacingOccurrences(of: "\\s{2,}", with: " ", options: .regularExpression)
                    .trimmingCharacters(in: .whitespacesAndNewlines)
                // Recurrence/priority are stripped from the date-cleaned title; the
                // highlight span still points at the date phrase in the raw text.
                let grammar = RecurrencePriorityGrammar.parse(dateStripped)
                return TodoTitleNlpResponse(
                    cleanTitle: grammar.cleanTitle,
                    matchedText: matchedText,
                    matchStart: match.range.location,
                    dueEpochMs: date.flooredToMinute().epochMilliseconds,
                    rrule: grammar.rrule,
                    priority: grammar.priority
                )
            }
        }

        // No date phrase: still capture recurrence/priority so "gym every day !" works.
        let grammar = RecurrencePriorityGrammar.parse(
            text.trimmingCharacters(in: .whitespacesAndNewlines)
        )
        guard grammar.rrule != nil || grammar.priority != nil else { return nil }
        return TodoTitleNlpResponse(
            cleanTitle: grammar.cleanTitle,
            matchedText: nil,
            matchStart: 0,
            dueEpochMs: nil,
            rrule: grammar.rrule,
            priority: grammar.priority
        )
    }
}

/// Deterministic recurrence + priority capture — the Swift twin of the shared
/// `RecurrencePriorityGrammar` (Kotlin) used on web/Android. Kept in sync by hand.
enum RecurrencePriorityGrammar {
    struct Result {
        let cleanTitle: String
        let rrule: String?
        let priority: String?
    }

    private static let recurrenceRules: [(pattern: String, rrule: String)] = [
        // "weekday(s)" is tried before "week", which it contains.
        (#"\b(?:every\s+weekday|weekdays?)\b"#, "RRULE:FREQ=WEEKLY;INTERVAL=1;BYDAY=MO,TU,WE,TH,FR"),
        (#"\b(?:every\s*day|everyday|daily)\b"#, "RRULE:FREQ=DAILY;INTERVAL=1"),
        (#"\b(?:every\s+week|weekly)\b"#, "RRULE:FREQ=WEEKLY;INTERVAL=1"),
        (#"\b(?:every\s+month|monthly)\b"#, "RRULE:FREQ=MONTHLY;INTERVAL=1"),
        (#"\b(?:every\s+year|yearly|annually)\b"#, "RRULE:FREQ=YEARLY;INTERVAL=1"),
    ]

    static func parse(_ text: String) -> Result {
        var working = text
        var rrule: String?

        for rule in recurrenceRules {
            if let range = working.range(of: rule.pattern, options: [.regularExpression, .caseInsensitive]) {
                rrule = rule.rrule
                working.removeSubrange(range)
                break
            }
        }

        var priority: String?
        if let range = working.range(of: "!!") {
            priority = "High"
            working.removeSubrange(range)
        } else if let range = working.range(of: "!") {
            priority = "Medium"
            working.removeSubrange(range)
        } else if let range = working.range(
            of: #"\b(high|medium|low)\s+priority\b"#,
            options: [.regularExpression, .caseInsensitive]
        ) {
            priority = capitalizePriority(String(working[range]))
            working.removeSubrange(range)
        } else if let range = working.range(
            of: #"\s+(high|medium|low)\s*$"#,
            options: [.regularExpression, .caseInsensitive]
        ) {
            priority = capitalizePriority(String(working[range]))
            working.removeSubrange(range)
        }

        let cleanTitle = working
            .replacingOccurrences(of: "\\s{2,}", with: " ", options: .regularExpression)
            .trimmingCharacters(in: .whitespacesAndNewlines)
        return Result(cleanTitle: cleanTitle, rrule: rrule, priority: priority)
    }

    private static func capitalizePriority(_ raw: String) -> String {
        let lowered = raw.lowercased()
        if lowered.contains("high") { return "High" }
        if lowered.contains("low") { return "Low" }
        return "Medium"
    }
}

/// "Make this repeat?" engine — the Swift twin of the shared Kotlin
/// RepeatSuggestionEngine. Given completed history + the title being created, returns a
/// preset RRULE when the same task shows a steady cadence, else nil.
enum RepeatSuggestionEngine {
    struct Completion {
        let title: String
        let completedAtEpochMs: Int64
    }

    private struct Target {
        let days: Double
        let rrule: String
        let tolerance: Double
    }

    private static let targets = [
        Target(days: 1, rrule: "RRULE:FREQ=DAILY;INTERVAL=1", tolerance: 0.5),
        Target(days: 7, rrule: "RRULE:FREQ=WEEKLY;INTERVAL=1", tolerance: 2),
        Target(days: 30, rrule: "RRULE:FREQ=MONTHLY;INTERVAL=1", tolerance: 7),
        Target(days: 365, rrule: "RRULE:FREQ=YEARLY;INTERVAL=1", tolerance: 45),
    ]

    static let minCompletions = 3
    private static let msPerDay = 86_400_000.0

    static func normalize(_ title: String) -> String {
        RecurrencePriorityGrammar.parse(title).cleanTitle
            .lowercased()
            .replacingOccurrences(of: "\\s{2,}", with: " ", options: .regularExpression)
            .trimmingCharacters(in: .whitespacesAndNewlines)
    }

    static func suggest(currentTitle: String, completions: [Completion]) -> String? {
        let norm = normalize(currentTitle)
        if norm.isEmpty { return nil }

        let times = completions
            .filter { normalize($0.title) == norm }
            .map { $0.completedAtEpochMs }
            .sorted()
        if times.count < minCompletions { return nil }

        var intervals: [Double] = []
        for index in 1..<times.count {
            let days = Double(times[index] - times[index - 1]) / msPerDay
            if days > 0.25 { intervals.append(days) }
        }
        if intervals.count < minCompletions - 1 { return nil }

        let median = medianOf(intervals)
        guard let target = targets.first(where: { abs(median - $0.days) <= $0.tolerance }) else { return nil }

        let consistent = intervals.filter { abs($0 - median) <= target.tolerance }.count
        if consistent < intervals.count - intervals.count / 3 { return nil }

        return target.rrule
    }

    private static func medianOf(_ values: [Double]) -> Double {
        let sorted = values.sorted()
        let mid = sorted.count / 2
        return sorted.count % 2 == 1 ? sorted[mid] : (sorted[mid - 1] + sorted[mid]) / 2
    }
}
