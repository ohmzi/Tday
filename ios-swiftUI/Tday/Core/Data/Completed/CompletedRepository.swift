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

    /// Completed Floaters — synced into `OfflineSyncState.completedFloaters`
    /// by `SyncManager` since the sync pipeline shipped, but this is the first
    /// feature to actually read them back out for display.
    func fetchCompletedFloatersSnapshot() -> [CompletedItem] {
        cacheManager.loadOfflineState().completedFloaters.map(completedFloaterFromCache)
    }

    func uncomplete(_ item: CompletedItem) async throws {
        guard let originalTodoId = item.originalTodoId else {
            throw APIError(message: "Completed todo is missing original todo id", statusCode: nil)
        }
        let now = Date().epochMilliseconds
        _ = try await cacheManager.updateOfflineState { state in
            var nextState = state
            nextState.completedItems.removeAll { $0.id == item.id }
            nextState.todos.append(
                CachedTodoRecord(
                    id: item.instanceDate == nil ? originalTodoId : item.id,
                    canonicalId: originalTodoId,
                    title: item.title,
                    description: item.description,
                    priority: item.priority,
                    dueEpochMs: item.due?.epochMilliseconds,
                    rrule: item.due == nil ? nil : item.rrule,
                    instanceDateEpochMs: item.instanceDate?.epochMilliseconds,
                    pinned: false,
                    completed: false,
                    listId: item.listId ?? state.lists.first(where: { $0.name == item.listName })?.id,
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
        if syncManager.isLocalMode {
            return
        }
        let result = await syncManager.syncCachedData(force: true, replayPendingMutations: true)
        if case let .failure(error) = result, isLikelyUnrecoverableMutationError(error) {
            throw error
        }
    }

    func updateCompletedTodo(_ item: CompletedItem, payload: CreateTaskPayload) async throws {
        let previousState = try await cacheManager.loadOfflineState()
        let normalizedTitle = payload.title.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !normalizedTitle.isEmpty else {
            return
        }

        let normalizedDescription = payload.description.nilIfBlank
        let normalizedListID = payload.listId.nilIfBlank
        let normalizedPriorityValue = normalizedPriority(payload.priority)
        _ = try await cacheManager.updateOfflineState { state in
            var nextState = state
            nextState.completedItems = state.completedItems.map { current in
                guard current.id == item.id else { return current }
                return CachedCompletedRecord(
                    id: current.id,
                    originalTodoId: current.originalTodoId,
                    title: normalizedTitle,
                    description: normalizedDescription,
                    priority: normalizedPriorityValue,
                    dueEpochMs: payload.due?.epochMilliseconds,
                    completedAtEpochMs: current.completedAtEpochMs,
                    rrule: payload.due == nil ? nil : payload.rrule,
                    instanceDateEpochMs: current.instanceDateEpochMs,
                    listId: normalizedListID,
                    listName: state.lists.first(where: { $0.id == payload.listId })?.name,
                    listColor: state.lists.first(where: { $0.id == payload.listId })?.color
                )
            }
            return nextState
        }

        if syncManager.isLocalMode {
            return
        }

        do {
            _ = try await api.patchCompletedTodoByBody(
                payload: UpdateCompletedTodoRequest(
                    id: item.id,
                    title: normalizedTitle,
                    description: normalizedDescription,
                    priority: normalizedPriorityValue,
                    due: payload.due?.ISO8601Format(),
                    rrule: payload.due == nil ? nil : payload.rrule,
                    listID: normalizedListID
                )
            )
        } catch {
            try await cacheManager.saveOfflineState(previousState)
            throw error
        }
    }

    func deleteCompletedTodo(_ item: CompletedItem) async throws {
        let previousState = try await cacheManager.loadOfflineState()
        _ = try await cacheManager.updateOfflineState { state in
            var nextState = state
            nextState.completedItems.removeAll { $0.id == item.id }
            return nextState
        }

        if syncManager.isLocalMode {
            return
        }

        guard !item.id.hasPrefix(LOCAL_COMPLETED_PREFIX) else {
            return
        }

        do {
            _ = try await api.deleteCompletedTodoByBody(payload: DeleteCompletedTodoRequest(id: item.id))
        } catch {
            try await cacheManager.saveOfflineState(previousState)
            throw error
        }
    }

    /// Restores a completed Floater from the browsable Completed screen — a
    /// real, immediate round trip against an already-committed item. (Not to
    /// be confused with the 8.5s toast Undo, which stages the completion
    /// client-side and never calls this at all if pressed in time — see
    /// `UndoableDeleteScheduler`.) This is the only path that can hit the
    /// backend's list-recreation branch, so the response is returned as-is
    /// rather than reduced to `Void`: `response.listRecreated` tells the
    /// caller whether the floater landed back in its original list or a
    /// recreated one, which is a real "read this before you assume" signal —
    /// see `FloaterUncompleteResponse`'s doc comment.
    func uncompleteFloater(_ item: CompletedItem) async throws -> FloaterUncompleteResponse {
        guard let originalFloaterId = item.originalTodoId else {
            throw APIError(message: "Completed floater is missing original floater id", statusCode: nil)
        }
        let now = Date().epochMilliseconds

        if syncManager.isLocalMode {
            // No server round trip to ask "was the list recreated?" — Local
            // Mode has no correlation data cached to answer that itself
            // (there's no `originalListID` here the way the backend has),
            // so this can only restore into the list the floater still
            // remembers, same fidelity Local Mode already has everywhere
            // else. Known gap, not a regression: this screen could not
            // uncomplete a Floater at all before this change.
            _ = try await cacheManager.updateOfflineState { state in
                var nextState = state
                nextState.completedFloaters.removeAll { $0.id == item.id }
                nextState.floaters.append(
                    CachedFloaterRecord(
                        id: originalFloaterId,
                        canonicalId: originalFloaterId,
                        title: item.title,
                        description: item.description,
                        priority: item.priority,
                        pinned: false,
                        completed: false,
                        listId: item.listId ?? state.floaterLists.first(where: { $0.name == item.listName })?.id,
                        updatedAtEpochMs: now
                    )
                )
                return nextState
            }
            return FloaterUncompleteResponse(message: nil, floater: nil, listRecreated: false, listID: nil, listName: nil, listColor: nil)
        }

        let previousState = try await cacheManager.loadOfflineState()
        _ = try await cacheManager.updateOfflineState { state in
            var nextState = state
            nextState.completedFloaters.removeAll { $0.id == item.id }
            return nextState
        }

        do {
            let response = try await api.uncompleteFloaterByBody(payload: FloaterUncompleteRequest(id: originalFloaterId))
            // No optimistic floater/list insert above: which list (if any) it
            // lands in is exactly what this call decides, so a resync — not a
            // hand-rolled cache patch — is what picks up the (possibly brand
            // new) list and the floater in it correctly.
            let result = await syncManager.syncCachedData(force: true, replayPendingMutations: false)
            if case let .failure(error) = result, isLikelyUnrecoverableMutationError(error) {
                throw error
            }
            return response
        } catch {
            try await cacheManager.saveOfflineState(previousState)
            throw error
        }
    }

    func updateCompletedFloater(_ item: CompletedItem, payload: CreateTaskPayload) async throws {
        let previousState = try await cacheManager.loadOfflineState()
        let normalizedTitle = payload.title.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !normalizedTitle.isEmpty else {
            return
        }

        let normalizedDescription = payload.description.nilIfBlank
        let normalizedListID = payload.listId.nilIfBlank
        let normalizedPriorityValue = normalizedPriority(payload.priority)
        _ = try await cacheManager.updateOfflineState { state in
            var nextState = state
            nextState.completedFloaters = state.completedFloaters.map { current in
                guard current.id == item.id else { return current }
                return CachedCompletedFloaterRecord(
                    id: current.id,
                    originalFloaterId: current.originalFloaterId,
                    title: normalizedTitle,
                    description: normalizedDescription,
                    priority: normalizedPriorityValue,
                    completedAtEpochMs: current.completedAtEpochMs,
                    listId: normalizedListID,
                    listName: state.floaterLists.first(where: { $0.id == payload.listId })?.name,
                    listColor: state.floaterLists.first(where: { $0.id == payload.listId })?.color
                )
            }
            return nextState
        }

        if syncManager.isLocalMode {
            return
        }

        do {
            _ = try await api.patchCompletedFloaterByBody(
                payload: UpdateCompletedFloaterRequest(
                    id: item.id,
                    title: normalizedTitle,
                    description: normalizedDescription,
                    priority: normalizedPriorityValue,
                    listID: normalizedListID
                )
            )
        } catch {
            try await cacheManager.saveOfflineState(previousState)
            throw error
        }
    }

    func deleteCompletedFloater(_ item: CompletedItem) async throws {
        let previousState = try await cacheManager.loadOfflineState()
        _ = try await cacheManager.updateOfflineState { state in
            var nextState = state
            nextState.completedFloaters.removeAll { $0.id == item.id }
            return nextState
        }

        if syncManager.isLocalMode {
            return
        }

        guard !item.id.hasPrefix(LOCAL_COMPLETED_FLOATER_PREFIX) else {
            return
        }

        do {
            _ = try await api.deleteCompletedFloaterByBody(payload: DeleteCompletedFloaterRequest(id: item.id))
        } catch {
            try await cacheManager.saveOfflineState(previousState)
            throw error
        }
    }

    private func normalizedPriority(_ priority: String) -> String {
        TaskPriorityDisplay.canonicalValue(priority)
    }
}
