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

        if normalizedListID?.hasPrefix(LOCAL_FLOATER_LIST_PREFIX) == true {
            _ = await syncManager.syncCachedData(force: true, replayPendingMutations: true)
            return
        }

        do {
            let response = try await api.createTodo(
                payload: CreateTodoRequest(
                    title: normalizedTitle,
                    description: normalizedDescription,
                    priority: normalizedPriorityValue,
                    due: normalizedDue.ISO8601Format(),
                    rrule: payload.rrule,
                    listID: normalizedListID
                )
            )
            guard let createdDTO = response.todo else {
                return
            }
            let createdTodo = mapTodoDTO(createdDTO)
            _ = try await cacheManager.updateOfflineState { state in
                var nextState = self.replaceLocalTodoID(
                    state,
                    localTodoID: localTodoID,
                    serverTodoID: createdTodo.canonicalId
                )
                let createdRecord = todoToCache(createdTodo)
                nextState.todos = nextState.todos.map { todo in
                    guard todo.canonicalId == createdTodo.canonicalId else {
                        return todo
                    }
                    return createdRecord
                }
                nextState.pendingMutations.removeAll { $0.mutationId == mutationID }
                return nextState
            }
        } catch {
            // Keep the pending CREATE_TODO mutation so background sync can retry it.
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

        if normalizedListID?.hasPrefix(LOCAL_LIST_PREFIX) == true {
            _ = await syncManager.syncCachedData(force: true, replayPendingMutations: true)
            return
        }

        do {
            let response = try await api.createFloater(
                payload: CreateFloaterRequest(
                    title: normalizedTitle,
                    description: normalizedDescription,
                    priority: normalizedPriorityValue,
                    listID: normalizedListID
                )
            )
            guard let createdDTO = response.floater else {
                return
            }
            let createdFloater = mapFloaterDTO(createdDTO)
            _ = try await cacheManager.updateOfflineState { state in
                var nextState = self.replaceLocalFloaterID(
                    state,
                    localFloaterID: localFloaterID,
                    serverFloaterID: createdFloater.canonicalId
                )
                let createdRecord = floaterToCache(createdFloater)
                nextState.floaters = nextState.floaters.map { floater in
                    guard floater.canonicalId == createdFloater.canonicalId else {
                        return floater
                    }
                    return createdRecord
                }
                nextState.pendingMutations.removeAll { $0.mutationId == mutationID }
                return nextState
            }
        } catch {
            // Keep the pending CREATE_FLOATER mutation so background sync can retry it.
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
        if syncManager.isLocalMode {
            return nil
        }

        let timezoneOffsetMinutes = TimeZone.current.secondsFromGMT() / 60
        return try? await api.parseTodoTitleNlp(
            payload: TodoTitleNlpRequest(
                text: text,
                locale: Locale.current.identifier,
                referenceEpochMs: referenceDueEpochMs,
                timezoneOffsetMinutes: timezoneOffsetMinutes,
                defaultDurationMinutes: 60
            )
        )
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

    private func replaceLocalTodoID(_ state: OfflineSyncState, localTodoID: String, serverTodoID: String) -> OfflineSyncState {
        OfflineSyncState(
            lastSuccessfulSyncEpochMs: state.lastSuccessfulSyncEpochMs,
            lastSyncAttemptEpochMs: state.lastSyncAttemptEpochMs,
            todos: state.todos.map { todo in
                guard todo.canonicalId == localTodoID || todo.id == localTodoID else {
                    return todo
                }
                return CachedTodoRecord(
                    id: todo.instanceDateEpochMs == nil ? serverTodoID : todo.id,
                    canonicalId: serverTodoID,
                    title: todo.title,
                    description: todo.description,
                    priority: todo.priority,
                    dueEpochMs: todo.dueEpochMs,
                    rrule: todo.rrule,
                    instanceDateEpochMs: todo.instanceDateEpochMs,
                    pinned: todo.pinned,
                    completed: todo.completed,
                    listId: todo.listId,
                    updatedAtEpochMs: todo.updatedAtEpochMs
                )
            },
            floaters: state.floaters,
            completedItems: state.completedItems.map { item in
                guard item.originalTodoId == localTodoID else {
                    return item
                }
                return CachedCompletedRecord(
                    id: item.id,
                    originalTodoId: serverTodoID,
                    title: item.title,
                    description: item.description,
                    priority: item.priority,
                    dueEpochMs: item.dueEpochMs,
                    completedAtEpochMs: item.completedAtEpochMs,
                    rrule: item.rrule,
                    instanceDateEpochMs: item.instanceDateEpochMs,
                    listId: item.listId,
                    listName: item.listName,
                    listColor: item.listColor
                )
            },
            completedFloaters: state.completedFloaters,
            lists: state.lists,
            floaterLists: state.floaterLists,
            pendingMutations: state.pendingMutations.map { mutation in
                PendingMutationRecord(
                    mutationId: mutation.mutationId,
                    kind: mutation.kind,
                    targetId: mutation.targetId == localTodoID ? serverTodoID : mutation.targetId,
                    timestampEpochMs: mutation.timestampEpochMs,
                    title: mutation.title,
                    description: mutation.description,
                    priority: mutation.priority,
                    dueEpochMs: mutation.dueEpochMs,
                    rrule: mutation.rrule,
                    listId: mutation.listId,
                    pinned: mutation.pinned,
                    completed: mutation.completed,
                    instanceDateEpochMs: mutation.instanceDateEpochMs,
                    name: mutation.name,
                    color: mutation.color,
                    iconKey: mutation.iconKey
                )
            },
            aiSummaryEnabled: state.aiSummaryEnabled
        )
    }

    private func replaceLocalFloaterID(_ state: OfflineSyncState, localFloaterID: String, serverFloaterID: String) -> OfflineSyncState {
        OfflineSyncState(
            lastSuccessfulSyncEpochMs: state.lastSuccessfulSyncEpochMs,
            lastSyncAttemptEpochMs: state.lastSyncAttemptEpochMs,
            todos: state.todos,
            floaters: state.floaters.map { floater in
                guard floater.canonicalId == localFloaterID || floater.id == localFloaterID else {
                    return floater
                }
                return CachedFloaterRecord(
                    id: serverFloaterID,
                    canonicalId: serverFloaterID,
                    title: floater.title,
                    description: floater.description,
                    priority: floater.priority,
                    pinned: floater.pinned,
                    completed: floater.completed,
                    listId: floater.listId,
                    updatedAtEpochMs: floater.updatedAtEpochMs
                )
            },
            completedItems: state.completedItems,
            completedFloaters: state.completedFloaters.map { item in
                guard item.originalFloaterId == localFloaterID else {
                    return item
                }
                return CachedCompletedFloaterRecord(
                    id: item.id,
                    originalFloaterId: serverFloaterID,
                    title: item.title,
                    description: item.description,
                    priority: item.priority,
                    completedAtEpochMs: item.completedAtEpochMs,
                    listId: item.listId,
                    listName: item.listName,
                    listColor: item.listColor
                )
            },
            lists: state.lists,
            floaterLists: state.floaterLists,
            pendingMutations: state.pendingMutations.map { mutation in
                PendingMutationRecord(
                    mutationId: mutation.mutationId,
                    kind: mutation.kind,
                    targetId: mutation.targetId == localFloaterID ? serverFloaterID : mutation.targetId,
                    timestampEpochMs: mutation.timestampEpochMs,
                    title: mutation.title,
                    description: mutation.description,
                    priority: mutation.priority,
                    dueEpochMs: mutation.dueEpochMs,
                    rrule: mutation.rrule,
                    listId: mutation.listId,
                    pinned: mutation.pinned,
                    completed: mutation.completed,
                    instanceDateEpochMs: mutation.instanceDateEpochMs,
                    name: mutation.name,
                    color: mutation.color,
                    iconKey: mutation.iconKey
                )
            },
            aiSummaryEnabled: state.aiSummaryEnabled
        )
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
        switch priority.trimmingCharacters(in: .whitespacesAndNewlines).lowercased() {
        case "high":
            return "High"
        case "medium":
            return "Medium"
        default:
            return "Low"
        }
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
