import Foundation

@MainActor
final class CompletedRepository {
    private let api: TdayAPIService
    private let cacheManager: OfflineCacheManager
    private let syncManager: SyncManager

    init(api: TdayAPIService, cacheManager: OfflineCacheManager, syncManager: SyncManager) {
        self.api = api
        self.cacheManager = cacheManager
        self.syncManager = syncManager
    }

    func fetchCompletedItems() -> [CompletedItem] {
        cacheManager.loadOfflineState().completedItems.map(completedFromCache)
    }

    func fetchCompletedItemsSnapshot() -> [CompletedItem] {
        cacheManager.loadOfflineState().completedItems.map(completedFromCache)
    }

    func uncomplete(_ item: CompletedItem) async throws {
        guard let originalTodoId = item.originalTodoId else {
            throw APIError(message: "Completed todo is missing original todo id", statusCode: nil)
        }
        let now = Date().epochMilliseconds
        cacheManager.updateOfflineState { state in
            var nextState = state
            nextState.completedItems.removeAll { $0.id == item.id }
            nextState.todos.append(
                CachedTodoRecord(
                    id: item.instanceDate == nil ? originalTodoId : item.id,
                    canonicalId: originalTodoId,
                    title: item.title,
                    description: item.description,
                    priority: item.priority,
                    dtstartEpochMs: item.dtstart.epochMilliseconds,
                    dueEpochMs: item.due.epochMilliseconds,
                    rrule: item.rrule,
                    instanceDateEpochMs: item.instanceDate?.epochMilliseconds,
                    pinned: false,
                    completed: false,
                    listId: state.lists.first(where: { $0.name == item.listName })?.id,
                    updatedAtEpochMs: now
                )
            )
            if !originalTodoId.hasPrefix(LOCAL_TODO_PREFIX) {
                nextState.pendingMutations.append(
                    PendingMutationRecord(
                        mutationId: UUID().uuidString,
                        kind: .uncompleteTodo,
                        targetId: originalTodoId,
                        timestampEpochMs: now,
                        title: nil,
                        description: nil,
                        priority: nil,
                        dtstartEpochMs: nil,
                        dueEpochMs: nil,
                        rrule: nil,
                        listId: nil,
                        pinned: nil,
                        completed: false,
                        instanceDateEpochMs: item.instanceDate?.epochMilliseconds,
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

    func updateCompletedTodo(_ item: CompletedItem, payload: CreateTaskPayload) async throws {
        let previousState = cacheManager.loadOfflineState()
        let normalizedTitle = payload.title.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !normalizedTitle.isEmpty else {
            return
        }

        cacheManager.updateOfflineState { state in
            var nextState = state
            nextState.completedItems = state.completedItems.map { current in
                guard current.id == item.id else { return current }
                return CachedCompletedRecord(
                    id: current.id,
                    originalTodoId: current.originalTodoId,
                    title: normalizedTitle,
                    description: payload.description?.nilIfBlank,
                    priority: normalizedPriority(payload.priority),
                    dtstartEpochMs: payload.dtstart.epochMilliseconds,
                    dueEpochMs: payload.due.epochMilliseconds,
                    completedAtEpochMs: current.completedAtEpochMs,
                    rrule: payload.rrule,
                    instanceDateEpochMs: current.instanceDateEpochMs,
                    listName: state.lists.first(where: { $0.id == payload.listId })?.name,
                    listColor: state.lists.first(where: { $0.id == payload.listId })?.color
                )
            }
            return nextState
        }

        do {
            _ = try await api.patchCompletedTodoByBody(
                payload: UpdateCompletedTodoRequest(
                    id: item.id,
                    title: normalizedTitle,
                    description: payload.description?.nilIfBlank,
                    priority: normalizedPriority(payload.priority),
                    dtstart: payload.dtstart.ISO8601Format(),
                    due: payload.due.ISO8601Format(),
                    rrule: payload.rrule,
                    listID: payload.listId?.nilIfBlank
                )
            )
        } catch {
            cacheManager.saveOfflineState(previousState)
            throw error
        }
    }

    func deleteCompletedTodo(_ item: CompletedItem) async throws {
        let previousState = cacheManager.loadOfflineState()
        cacheManager.updateOfflineState { state in
            var nextState = state
            nextState.completedItems.removeAll { $0.id == item.id }
            return nextState
        }

        guard !item.id.hasPrefix(LOCAL_COMPLETED_PREFIX) else {
            return
        }

        do {
            _ = try await api.deleteCompletedTodoByBody(payload: DeleteCompletedTodoRequest(id: item.id))
        } catch {
            cacheManager.saveOfflineState(previousState)
            throw error
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
}
