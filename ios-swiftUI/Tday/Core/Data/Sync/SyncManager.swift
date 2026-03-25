import Foundation

private struct RemoteSnapshot {
    let todos: [TodoItem]
    let completedItems: [CompletedItem]
    let lists: [ListSummary]
    let aiSummaryEnabled: Bool

    var todoUpdatedAtByCanonical: [String: Int64] {
        Dictionary(uniqueKeysWithValues: todos.map { ($0.canonicalId, $0.updatedAt?.epochMilliseconds ?? 0) })
    }

    var listUpdatedAtByID: [String: Int64] {
        Dictionary(uniqueKeysWithValues: lists.map { ($0.id, $0.updatedAt?.epochMilliseconds ?? 0) })
    }
}

@MainActor
final class SyncManager {
    private let api: TdayAPIService
    private let cacheManager: OfflineCacheManager

    private let offlineResyncIntervalMs: Int64 = 5 * 60 * 1_000
    private let minForceSyncIntervalMs: Int64 = 1_200

    init(api: TdayAPIService, cacheManager: OfflineCacheManager) {
        self.api = api
        self.cacheManager = cacheManager
    }

    func hasPendingMutations() -> Bool {
        !cacheManager.loadOfflineState().pendingMutations.isEmpty
    }

    func syncCachedData(force: Bool = false, replayPendingMutations: Bool = true) async -> Result<Void, Error> {
        do {
            try await cacheManager.withSyncLock {
                try await self.syncLocalCache(force: force, replayPendingMutations: replayPendingMutations)
            }
            return .success(())
        } catch {
            return .failure(error)
        }
    }

    private func syncLocalCache(force: Bool, replayPendingMutations: Bool) async throws {
        var state = cacheManager.loadOfflineState()
        let now = Date().epochMilliseconds
        if force && (now - state.lastSyncAttemptEpochMs) < minForceSyncIntervalMs {
            return
        }

        let shouldReplay = replayPendingMutations && !state.pendingMutations.isEmpty
        let shouldSync = force ||
            shouldReplay ||
            state.lastSuccessfulSyncEpochMs == 0 ||
            (now - state.lastSyncAttemptEpochMs) >= offlineResyncIntervalMs
        if !shouldSync {
            return
        }

        state.lastSyncAttemptEpochMs = now
        cacheManager.saveOfflineState(state)

        let initialRemote = try await fetchRemoteSnapshot()
        if shouldReplay {
            state = try await applyPendingMutations(initialState: state, remoteSnapshot: initialRemote)
            cacheManager.saveOfflineState(state)
        }

        let latestRemote = try await fetchRemoteSnapshot()
        var merged = mergeRemoteWithLocal(localState: state, remote: latestRemote)
        merged.lastSyncAttemptEpochMs = now
        merged.lastSuccessfulSyncEpochMs = now
        cacheManager.saveOfflineState(merged)
    }

    private func fetchRemoteSnapshot() async throws -> RemoteSnapshot {
        async let todosTask = api.getTodos(timeline: true)
        async let completedTask = api.getCompletedTodos()
        async let listsTask = api.getLists()
        async let appSettingsTask = api.getAppSettings()

        let todosResponse = try await todosTask
        let completedResponse = try await completedTask
        let listsResponse = try await listsTask
        let appSettingsResponse = try await appSettingsTask
        let todos = todosResponse.todos.map(mapTodoDTO)
        let completedItems = completedResponse.completedTodos.map(mapCompletedDTO)
        let lists = listsResponse.lists.map(mapListDTO)
        let aiSummaryEnabled = appSettingsResponse.aiSummaryEnabled
        return RemoteSnapshot(todos: todos, completedItems: completedItems, lists: lists, aiSummaryEnabled: aiSummaryEnabled)
    }

    private func mergeRemoteWithLocal(localState: OfflineSyncState, remote: RemoteSnapshot) -> OfflineSyncState {
        let pendingTargets = Set(localState.pendingMutations.compactMap(\.targetId))
        var remoteTodosByKey = Dictionary(uniqueKeysWithValues: remote.todos.map { (todoMergeKey(item: $0), todoToCache($0)) })
        var mergedTodos: [CachedTodoRecord] = []

        for localTodo in localState.todos {
            let key = todoMergeKey(record: localTodo)
            let remoteTodo = remoteTodosByKey[key]
            let localWins = localTodo.id.hasPrefix(LOCAL_TODO_PREFIX) ||
                pendingTargets.contains(localTodo.canonicalId) ||
                localTodo.updatedAtEpochMs > (remoteTodo?.updatedAtEpochMs ?? 0)
            if localWins {
                mergedTodos.append(localTodo)
                remoteTodosByKey.removeValue(forKey: key)
            }
        }
        mergedTodos.append(contentsOf: remoteTodosByKey.values)

        var remoteListsByID = Dictionary(uniqueKeysWithValues: remote.lists.map { ($0.id, listToCache($0)) })
        var mergedLists: [CachedListRecord] = []
        for localList in localState.lists {
            let remoteList = remoteListsByID[localList.id]
            let localWins = localList.id.hasPrefix(LOCAL_LIST_PREFIX) ||
                pendingTargets.contains(localList.id) ||
                localList.updatedAtEpochMs > (remoteList?.updatedAtEpochMs ?? 0)
            if localWins {
                mergedLists.append(localList)
                remoteListsByID.removeValue(forKey: localList.id)
            }
        }
        mergedLists.append(contentsOf: remoteListsByID.values)

        var remoteCompletedByID = Dictionary(uniqueKeysWithValues: remote.completedItems.map { ($0.id, completedToCache($0)) })
        var mergedCompleted: [CachedCompletedRecord] = []
        for localCompleted in localState.completedItems {
            let remoteCompleted = remoteCompletedByID[localCompleted.id]
            let localWins = localCompleted.id.hasPrefix(LOCAL_COMPLETED_PREFIX) ||
                pendingTargets.contains(localCompleted.originalTodoId ?? "") ||
                localCompleted.completedAtEpochMs > (remoteCompleted?.completedAtEpochMs ?? 0)
            if localWins {
                mergedCompleted.append(localCompleted)
                remoteCompletedByID.removeValue(forKey: localCompleted.id)
            }
        }
        mergedCompleted.append(contentsOf: remoteCompletedByID.values)

        let generatedMutations = buildLocalWinsMutations(localState: localState, remote: remote)
        let pendingMutations = dedupePendingMutations(localState.pendingMutations + generatedMutations)

        return OfflineSyncState(
            lastSuccessfulSyncEpochMs: localState.lastSuccessfulSyncEpochMs,
            lastSyncAttemptEpochMs: localState.lastSyncAttemptEpochMs,
            todos: mergedTodos.sorted { lhs, rhs in
                if lhs.pinned != rhs.pinned {
                    return lhs.pinned && !rhs.pinned
                }
                return lhs.dueEpochMs < rhs.dueEpochMs
            },
            completedItems: mergedCompleted.sorted { $0.completedAtEpochMs > $1.completedAtEpochMs },
            lists: mergedLists.sorted { $0.name.localizedCaseInsensitiveCompare($1.name) == .orderedAscending },
            pendingMutations: pendingMutations,
            aiSummaryEnabled: remote.aiSummaryEnabled
        )
    }

    private func buildLocalWinsMutations(localState: OfflineSyncState, remote: RemoteSnapshot) -> [PendingMutationRecord] {
        let existingKeys = Set(localState.pendingMutations.map { mutationKey(for: $0) })
        var generated: [PendingMutationRecord] = []

        for todo in localState.todos where !todo.id.hasPrefix(LOCAL_TODO_PREFIX) {
            guard let remoteUpdatedAt = remote.todoUpdatedAtByCanonical[todo.canonicalId], todo.updatedAtEpochMs > remoteUpdatedAt else {
                continue
            }
            let mutation = PendingMutationRecord(
                mutationId: UUID().uuidString,
                kind: .updateTodo,
                targetId: todo.canonicalId,
                timestampEpochMs: todo.updatedAtEpochMs,
                title: todo.title,
                description: todo.description,
                priority: todo.priority,
                dtstartEpochMs: todo.dtstartEpochMs,
                dueEpochMs: todo.dueEpochMs,
                rrule: todo.rrule,
                listId: todo.listId,
                pinned: todo.pinned,
                completed: todo.completed,
                instanceDateEpochMs: todo.instanceDateEpochMs,
                name: nil,
                color: nil,
                iconKey: nil
            )
            if !existingKeys.contains(mutationKey(for: mutation)) {
                generated.append(mutation)
            }
        }

        for list in localState.lists where !list.id.hasPrefix(LOCAL_LIST_PREFIX) {
            guard let remoteUpdatedAt = remote.listUpdatedAtByID[list.id], list.updatedAtEpochMs > remoteUpdatedAt else {
                continue
            }
            let mutation = PendingMutationRecord(
                mutationId: UUID().uuidString,
                kind: .updateList,
                targetId: list.id,
                timestampEpochMs: list.updatedAtEpochMs,
                title: nil,
                description: nil,
                priority: nil,
                dtstartEpochMs: nil,
                dueEpochMs: nil,
                rrule: nil,
                listId: nil,
                pinned: nil,
                completed: nil,
                instanceDateEpochMs: nil,
                name: list.name,
                color: list.color,
                iconKey: list.iconKey
            )
            if !existingKeys.contains(mutationKey(for: mutation)) {
                generated.append(mutation)
            }
        }

        return generated
    }

    private func applyPendingMutations(initialState: OfflineSyncState, remoteSnapshot: RemoteSnapshot) async throws -> OfflineSyncState {
        var state = initialState
        var remaining: [PendingMutationRecord] = []
        var resolvedTodoIDs: [String: String] = [:]
        var resolvedListIDs: [String: String] = [:]
        let orderedMutations = initialState.pendingMutations.sorted { $0.timestampEpochMs < $1.timestampEpochMs }

        for index in orderedMutations.indices {
            let mutation = orderedMutations[index]
            do {
                try await applyPendingMutation(
                    mutation,
                    state: &state,
                    remoteSnapshot: remoteSnapshot,
                    resolvedTodoIDs: &resolvedTodoIDs,
                    resolvedListIDs: &resolvedListIDs
                )
            } catch {
                if isLikelyConnectivityIssue(error) {
                    remaining.append(contentsOf: orderedMutations[index...])
                    break
                }
                if !isLikelyUnrecoverableMutationError(error) {
                    remaining.append(mutation)
                }
            }
        }

        state.pendingMutations = dedupePendingMutations(remaining)
        return state
    }

    private func applyPendingMutation(
        _ mutation: PendingMutationRecord,
        state: inout OfflineSyncState,
        remoteSnapshot: RemoteSnapshot,
        resolvedTodoIDs: inout [String: String],
        resolvedListIDs: inout [String: String]
    ) async throws {
        let targetID = resolveTargetID(mutation.targetId, todoMap: resolvedTodoIDs, listMap: resolvedListIDs)
        switch mutation.kind {
        case .createList:
            guard let localListID = mutation.targetId else { return }
            if !localListID.hasPrefix(LOCAL_LIST_PREFIX) {
                return
            }
            let response = try await api.createList(
                payload: CreateListRequest(name: mutation.name ?? "Untitled", color: mutation.color, iconKey: mutation.iconKey)
            )
            guard let createdList = response.list else { return }
            resolvedListIDs[localListID] = createdList.id
            state = replaceLocalListID(state, localListID: localListID, serverListID: createdList.id)

        case .updateList:
            guard let targetID else { return }
            let remoteUpdatedAt = remoteSnapshot.listUpdatedAtByID[targetID] ?? 0
            guard remoteUpdatedAt <= mutation.timestampEpochMs else { return }
            _ = try await api.patchListByBody(
                payload: UpdateListRequest(id: targetID, name: mutation.name, color: mutation.color, iconKey: mutation.iconKey)
            )

        case .createTodo:
            guard let localTodoID = mutation.targetId else { return }
            if !localTodoID.hasPrefix(LOCAL_TODO_PREFIX) {
                return
            }
            let resolvedListID = mutation.listId.flatMap { resolvedListIDs[$0] ?? $0 }
            let response = try await api.createTodo(
                payload: CreateTodoRequest(
                    title: mutation.title ?? "Untitled",
                    description: mutation.description,
                    priority: mutation.priority ?? "Low",
                    dtstart: Date(epochMilliseconds: mutation.dtstartEpochMs ?? Date().epochMilliseconds).ISO8601Format(),
                    due: Date(epochMilliseconds: mutation.dueEpochMs ?? Date().epochMilliseconds).ISO8601Format(),
                    rrule: mutation.rrule,
                    listID: resolvedListID
                )
            )
            guard let createdTodo = response.todo else { return }
            resolvedTodoIDs[localTodoID] = createdTodo.id
            state = replaceLocalTodoID(state, localTodoID: localTodoID, serverTodoID: createdTodo.id)

        case .updateTodo:
            guard let targetID else { return }
            let remoteUpdatedAt = remoteSnapshot.todoUpdatedAtByCanonical[targetID] ?? 0
            guard remoteUpdatedAt <= mutation.timestampEpochMs else { return }
            let resolvedListID = mutation.listId.flatMap { resolvedListIDs[$0] ?? $0 }
            _ = try await api.patchTodoByBody(
                payload: UpdateTodoRequest(
                    id: targetID,
                    title: mutation.title,
                    description: mutation.description,
                    pinned: mutation.pinned,
                    priority: mutation.priority,
                    completed: mutation.completed,
                    dtstart: mutation.dtstartEpochMs.map { Date(epochMilliseconds: $0).ISO8601Format() },
                    due: mutation.dueEpochMs.map { Date(epochMilliseconds: $0).ISO8601Format() },
                    rrule: mutation.rrule,
                    listID: resolvedListID,
                    dateChanged: true,
                    rruleChanged: true,
                    instanceDate: mutation.instanceDateEpochMs.map { Date(epochMilliseconds: $0).ISO8601Format() }
                )
            )

        case .deleteTodo:
            guard let targetID else { return }
            if let instanceDateEpochMs = mutation.instanceDateEpochMs {
                _ = try await api.deleteTodoInstanceByBody(payload: DeleteTodoRequest(id: targetID, instanceDate: instanceDateEpochMs))
            } else {
                _ = try await api.deleteTodoByBody(payload: DeleteTodoRequest(id: targetID, instanceDate: nil))
            }

        case .setPinned:
            guard let targetID else { return }
            _ = try await api.patchTodoByBody(
                payload: UpdateTodoRequest(
                    id: targetID,
                    title: nil,
                    description: nil,
                    pinned: mutation.pinned,
                    priority: nil,
                    completed: nil,
                    dtstart: nil,
                    due: nil,
                    rrule: nil,
                    listID: nil,
                    dateChanged: nil,
                    rruleChanged: nil,
                    instanceDate: mutation.instanceDateEpochMs.map { Date(epochMilliseconds: $0).ISO8601Format() }
                )
            )

        case .setPriority:
            guard let targetID else { return }
            _ = try await api.prioritizeTodoByBody(
                payload: TodoPrioritizeRequest(id: targetID, priority: mutation.priority ?? "Low", instanceDate: mutation.instanceDateEpochMs)
            )

        case .completeTodo, .completeTodoInstance:
            guard let targetID else { return }
            _ = try await api.completeTodoByBody(
                payload: TodoCompleteRequest(
                    id: targetID,
                    instanceDate: mutation.instanceDateEpochMs
                )
            )

        case .uncompleteTodo:
            guard let targetID else { return }
            _ = try await api.uncompleteTodoByBody(
                payload: TodoUncompleteRequest(id: targetID, instanceDate: mutation.instanceDateEpochMs)
            )
        }
    }

    private func replaceLocalTodoID(_ state: OfflineSyncState, localTodoID: String, serverTodoID: String) -> OfflineSyncState {
        OfflineSyncState(
            lastSuccessfulSyncEpochMs: state.lastSuccessfulSyncEpochMs,
            lastSyncAttemptEpochMs: state.lastSyncAttemptEpochMs,
            todos: state.todos.map { todo in
                if todo.canonicalId == localTodoID || todo.id == localTodoID {
                    return CachedTodoRecord(
                        id: todo.instanceDateEpochMs == nil ? serverTodoID : todo.id,
                        canonicalId: serverTodoID,
                        title: todo.title,
                        description: todo.description,
                        priority: todo.priority,
                        dtstartEpochMs: todo.dtstartEpochMs,
                        dueEpochMs: todo.dueEpochMs,
                        rrule: todo.rrule,
                        instanceDateEpochMs: todo.instanceDateEpochMs,
                        pinned: todo.pinned,
                        completed: todo.completed,
                        listId: todo.listId,
                        updatedAtEpochMs: todo.updatedAtEpochMs
                    )
                }
                return todo
            },
            completedItems: state.completedItems.map { item in
                if item.originalTodoId == localTodoID {
                    return CachedCompletedRecord(
                        id: item.id,
                        originalTodoId: serverTodoID,
                        title: item.title,
                        description: item.description,
                        priority: item.priority,
                        dtstartEpochMs: item.dtstartEpochMs,
                        dueEpochMs: item.dueEpochMs,
                        completedAtEpochMs: item.completedAtEpochMs,
                        rrule: item.rrule,
                        instanceDateEpochMs: item.instanceDateEpochMs,
                        listName: item.listName,
                        listColor: item.listColor
                    )
                }
                return item
            },
            lists: state.lists,
            pendingMutations: state.pendingMutations.map { mutation in
                PendingMutationRecord(
                    mutationId: mutation.mutationId,
                    kind: mutation.kind,
                    targetId: mutation.targetId == localTodoID ? serverTodoID : mutation.targetId,
                    timestampEpochMs: mutation.timestampEpochMs,
                    title: mutation.title,
                    description: mutation.description,
                    priority: mutation.priority,
                    dtstartEpochMs: mutation.dtstartEpochMs,
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

    private func replaceLocalListID(_ state: OfflineSyncState, localListID: String, serverListID: String) -> OfflineSyncState {
        OfflineSyncState(
            lastSuccessfulSyncEpochMs: state.lastSuccessfulSyncEpochMs,
            lastSyncAttemptEpochMs: state.lastSyncAttemptEpochMs,
            todos: state.todos.map { todo in
                if todo.listId == localListID {
                    return CachedTodoRecord(
                        id: todo.id,
                        canonicalId: todo.canonicalId,
                        title: todo.title,
                        description: todo.description,
                        priority: todo.priority,
                        dtstartEpochMs: todo.dtstartEpochMs,
                        dueEpochMs: todo.dueEpochMs,
                        rrule: todo.rrule,
                        instanceDateEpochMs: todo.instanceDateEpochMs,
                        pinned: todo.pinned,
                        completed: todo.completed,
                        listId: serverListID,
                        updatedAtEpochMs: todo.updatedAtEpochMs
                    )
                }
                return todo
            },
            completedItems: state.completedItems,
            lists: state.lists.map { list in
                if list.id == localListID {
                    return CachedListRecord(id: serverListID, name: list.name, color: list.color, iconKey: list.iconKey, todoCount: list.todoCount, updatedAtEpochMs: list.updatedAtEpochMs)
                }
                return list
            },
            pendingMutations: state.pendingMutations.map { mutation in
                PendingMutationRecord(
                    mutationId: mutation.mutationId,
                    kind: mutation.kind,
                    targetId: mutation.targetId == localListID ? serverListID : mutation.targetId,
                    timestampEpochMs: mutation.timestampEpochMs,
                    title: mutation.title,
                    description: mutation.description,
                    priority: mutation.priority,
                    dtstartEpochMs: mutation.dtstartEpochMs,
                    dueEpochMs: mutation.dueEpochMs,
                    rrule: mutation.rrule,
                    listId: mutation.listId == localListID ? serverListID : mutation.listId,
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

    private func resolveTargetID(_ original: String?, todoMap: [String: String], listMap: [String: String]) -> String? {
        guard let original else {
            return nil
        }
        if let todoID = todoMap[original] {
            return todoID
        }
        if let listID = listMap[original] {
            return listID
        }
        return original
    }

    private func dedupePendingMutations(_ mutations: [PendingMutationRecord]) -> [PendingMutationRecord] {
        var seen = Set<String>()
        var deduped: [PendingMutationRecord] = []
        for mutation in mutations.sorted(by: { $0.timestampEpochMs < $1.timestampEpochMs }) {
            let key = mutationKey(for: mutation)
            if seen.contains(key) {
                continue
            }
            seen.insert(key)
            deduped.append(mutation)
        }
        return deduped
    }

    private func mutationKey(for mutation: PendingMutationRecord) -> String {
        [
            mutation.kind.rawValue,
            mutation.targetId ?? "",
            String(mutation.instanceDateEpochMs ?? -1),
            mutation.listId ?? "",
            mutation.name ?? "",
            mutation.title ?? "",
        ].joined(separator: "::")
    }
}
