import Foundation

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
        let mutation = PendingMutationRecord(
            mutationId: UUID().uuidString,
            kind: .createTodo,
            targetId: localTodoID,
            timestampEpochMs: now,
            title: normalizedTitle,
            description: normalizedDescription,
            priority: normalizedPriorityValue,
            dueEpochMs: payload.due.epochMilliseconds,
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
                    dueEpochMs: payload.due.epochMilliseconds,
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
                    dueEpochMs: payload.due.epochMilliseconds,
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
                    dueEpochMs: payload.due.epochMilliseconds,
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
        let result = await syncManager.syncCachedData(force: true, replayPendingMutations: true)
        if case let .failure(error) = result, isLikelyUnrecoverableMutationError(error) {
            throw error
        }
    }

    func completeTodo(_ todo: TodoItem) async throws {
        let now = Date().epochMilliseconds
        _ = try await cacheManager.updateOfflineState { state in
            var nextState = state
            nextState.todos.removeAll { $0.canonicalId == todo.canonicalId && $0.instanceDateEpochMs == todo.instanceDateEpochMilliseconds }
            nextState.completedItems.insert(
                CachedCompletedRecord(
                    id: LOCAL_COMPLETED_PREFIX + UUID().uuidString.lowercased(),
                    originalTodoId: todo.canonicalId,
                    title: todo.title,
                    description: todo.description,
                    priority: todo.priority,
                    dueEpochMs: todo.due.epochMilliseconds,
                    completedAtEpochMs: now,
                    rrule: todo.rrule,
                    instanceDateEpochMs: todo.instanceDateEpochMilliseconds,
                    listName: state.lists.first(where: { $0.id == todo.listId })?.name,
                    listColor: state.lists.first(where: { $0.id == todo.listId })?.color
                ),
                at: 0
            )
            nextState.pendingMutations.removeAll { $0.targetId == todo.canonicalId && $0.kind == .createTodo }
            if !todo.canonicalId.hasPrefix(LOCAL_TODO_PREFIX) {
                nextState.pendingMutations.append(
                    PendingMutationRecord(
                        mutationId: UUID().uuidString,
                        kind: todo.instanceDate == nil ? .completeTodo : .completeTodoInstance,
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
                        instanceDateEpochMs: todo.instanceDateEpochMilliseconds,
                        name: nil,
                        color: nil,
                        iconKey: nil
                    )
                )
            }
            return nextState
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
        try await api.summarizeTodos(payload: TodoSummaryRequest(mode: mode.rawValue, listId: listId))
    }

    func parseTodoTitleNlp(text: String, referenceDueEpochMs: Int64) async -> TodoTitleNlpResponse? {
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
        let result = await syncManager.syncCachedData(force: true, replayPendingMutations: true)
        if case let .failure(error) = result, isLikelyUnrecoverableMutationError(error) {
            throw error
        }
    }

    private func buildDashboardSummary(from state: OfflineSyncState) -> DashboardSummary {
        let timelineTodos = state.todos.map(todoFromCache).filter { !$0.completed }
        let now = Date()
        let todayTodos = timelineTodos.filter { isTodayTodo($0, now: now) }
        let scheduledTodos = timelineTodos.filter { isScheduledTodo($0, now: now) }
        let todoCountsByList = Dictionary(grouping: timelineTodos, by: \.listId).mapValues(\.count)
        let lists = state.lists.map { list in
            listFromCache(list, todoCountOverride: todoCountsByList[list.id] ?? 0)
        }

        return DashboardSummary(
            todayCount: todayTodos.count,
            scheduledCount: scheduledTodos.count,
            allCount: timelineTodos.count,
            priorityCount: timelineTodos.filter { isPriorityTodo($0.priority) }.count,
            completedCount: state.completedItems.count,
            lists: lists
        )
    }

    private func buildTodos(from state: OfflineSyncState, mode: TodoListMode, listId: String?) -> [TodoItem] {
        let items = state.todos.map(todoFromCache).filter { !$0.completed }
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
        case .list:
            filtered = items.filter { $0.listId == listId }
        }

        return filtered.sorted { lhs, rhs in
            if lhs.pinned != rhs.pinned {
                return lhs.pinned && !rhs.pinned
            }
            return lhs.due < rhs.due
        }
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
        return todo.due >= now && todo.due >= startOfToday && todo.due < startOfTomorrow
    }

    private func isScheduledTodo(_ todo: TodoItem, now: Date = Date()) -> Bool {
        todo.due >= now
    }

    private func isOverdueTodo(_ todo: TodoItem, now: Date = Date()) -> Bool {
        todo.due < now
    }

    private func isPriorityTodo(_ priority: String?) -> Bool {
        guard let normalized = priority?.trimmingCharacters(in: .whitespacesAndNewlines).lowercased() else {
            return false
        }
        return normalized == "medium" || normalized == "high" || normalized == "important" || normalized == "urgent"
    }
}
