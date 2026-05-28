import Foundation

extension Notification.Name {
    static let offlineSyncAttemptFailed = Notification.Name("tday.offline-sync.attempt-failed")
    static let offlineSyncAttemptSucceeded = Notification.Name("tday.offline-sync.attempt-succeeded")
}

private struct RemoteSnapshot {
    let todos: [TodoItem]
    let completedItems: [CompletedItem]
    let lists: [ListSummary]
    let aiSummaryEnabled: Bool

    var todoUpdatedAtByCanonical: [String: Int64] {
        todos.reduce(into: [:]) { result, todo in
            let updatedAt = todo.updatedAt?.epochMilliseconds ?? 0
            result[todo.canonicalId] = max(result[todo.canonicalId] ?? 0, updatedAt)
        }
    }

    var listUpdatedAtByID: [String: Int64] {
        lists.reduce(into: [:]) { result, list in
            let updatedAt = list.updatedAt?.epochMilliseconds ?? 0
            result[list.id] = max(result[list.id] ?? 0, updatedAt)
        }
    }
}

func mergeCompletedRecordsWithPendingOverrides(
    localRecords: [CachedCompletedRecord],
    remoteRecords: [CachedCompletedRecord],
    pendingTodoTargets: Set<String>,
    pendingDeletedListIds: Set<String> = []
) -> [CachedCompletedRecord] {
    var mergedRecords = remoteRecords.filter { record in
        guard let listId = record.listId else {
            return true
        }
        return !pendingDeletedListIds.contains(listId)
    }

    for canonicalID in pendingTodoTargets {
        let localRecordsForTodo = localRecords.filter { $0.originalTodoId == canonicalID }
        guard !localRecordsForTodo.isEmpty else {
            continue
        }
        mergedRecords.removeAll { $0.originalTodoId == canonicalID }
        mergedRecords.append(contentsOf: localRecordsForTodo)
    }

    return mergedRecords
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

    func syncCachedData(
        force: Bool = false,
        replayPendingMutations: Bool = true,
        notifyOfflineFailure: Bool = true,
        connectionProbeTimeoutSeconds: TimeInterval? = nil
    ) async -> Result<Void, Error> {
        do {
            var contactedServer = false
            if let connectionProbeTimeoutSeconds {
                _ = try await api.probeConfiguredServer(timeoutInterval: connectionProbeTimeoutSeconds)
                contactedServer = true
            }
            let syncedRemoteData = try await cacheManager.withSyncLock {
                try await self.syncLocalCache(force: force, replayPendingMutations: replayPendingMutations)
            }
            if contactedServer || syncedRemoteData {
                NotificationCenter.default.post(name: .offlineSyncAttemptSucceeded, object: nil)
            }
            return .success(())
        } catch {
            if notifyOfflineFailure, isLikelyConnectivityIssue(error) {
                NotificationCenter.default.post(name: .offlineSyncAttemptFailed, object: nil)
            }
            return .failure(error)
        }
    }

    private func syncLocalCache(force: Bool, replayPendingMutations: Bool) async throws -> Bool {
        var state = try await cacheManager.loadOfflineState()
        let now = Date().epochMilliseconds
        if force && (now - state.lastSyncAttemptEpochMs) < minForceSyncIntervalMs {
            return false
        }

        let shouldReplay = replayPendingMutations && !state.pendingMutations.isEmpty
        let shouldSync = force ||
            shouldReplay ||
            state.lastSuccessfulSyncEpochMs == 0 ||
            (now - state.lastSyncAttemptEpochMs) >= offlineResyncIntervalMs
        if !shouldSync {
            return false
        }

        state.lastSyncAttemptEpochMs = now
        try await cacheManager.saveOfflineState(state)

        let initialRemote = try await fetchRemoteSnapshot()
        if shouldReplay {
            state = try await applyPendingMutations(initialState: state, remoteSnapshot: initialRemote)
            try await cacheManager.saveOfflineState(state)
        }

        let latestRemote = try await fetchRemoteSnapshot()
        var merged = mergeRemoteWithLocal(localState: state, remote: latestRemote)
        merged.lastSyncAttemptEpochMs = now
        merged.lastSuccessfulSyncEpochMs = now
        try await cacheManager.saveOfflineState(merged)
        return true
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
        let lists = listsResponse.lists.map { mapListDTO($0) }
        let aiSummaryEnabled = appSettingsResponse.aiSummaryEnabled
        return RemoteSnapshot(todos: todos, completedItems: completedItems, lists: lists, aiSummaryEnabled: aiSummaryEnabled)
    }

    private func mergeRemoteWithLocal(localState: OfflineSyncState, remote: RemoteSnapshot) -> OfflineSyncState {
        let pendingTodoTargets = Set(
            localState.pendingMutations.compactMap { mutation -> String? in
                mutation.kind.affectsTodo ? mutation.targetId : nil
            }
        )
        let pendingListTargets = Set(
            localState.pendingMutations.compactMap { mutation -> String? in
                switch mutation.kind {
                case .createList, .updateList, .deleteList:
                    return mutation.targetId
                default:
                    return nil
                }
            }
        )
        let pendingDeletedListIds = Set(
            localState.pendingMutations.compactMap { mutation -> String? in
                mutation.kind == .deleteList ? mutation.targetId : nil
            }
        )
        let remoteTodos = remote.todos.filter { todo in
            guard let listId = todo.listId else {
                return true
            }
            return !pendingDeletedListIds.contains(listId)
        }
        var remoteTodosByKey = Dictionary(uniqueKeysWithValues: remoteTodos.map { (todoMergeKey(item: $0), todoToCache($0)) })
        var mergedTodos: [CachedTodoRecord] = []

        for localTodo in localState.todos {
            let key = todoMergeKey(record: localTodo)
            let remoteTodo = remoteTodosByKey[key]

            if remoteTodo == nil,
               !localTodo.canonicalId.hasPrefix(LOCAL_TODO_PREFIX),
               !pendingTodoTargets.contains(localTodo.canonicalId) {
                continue
            }

            let localWins = localTodo.canonicalId.hasPrefix(LOCAL_TODO_PREFIX) ||
                pendingTodoTargets.contains(localTodo.canonicalId) ||
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
            if remoteList == nil,
               !localList.id.hasPrefix(LOCAL_LIST_PREFIX),
               !pendingListTargets.contains(localList.id) {
                continue
            }
            let localWins = localList.id.hasPrefix(LOCAL_LIST_PREFIX) ||
                pendingListTargets.contains(localList.id) ||
                localList.updatedAtEpochMs > (remoteList?.updatedAtEpochMs ?? 0)
            if localWins {
                mergedLists.append(localList)
                remoteListsByID.removeValue(forKey: localList.id)
            }
        }
        mergedLists.append(
            contentsOf: remoteListsByID.values.filter { !pendingDeletedListIds.contains($0.id) }
        )

        let mergedCompleted = mergeCompletedRecordsWithPendingOverrides(
            localRecords: localState.completedItems,
            remoteRecords: remote.completedItems.map(completedToCache),
            pendingTodoTargets: pendingTodoTargets,
            pendingDeletedListIds: pendingDeletedListIds
        )

        let generatedMutations = buildLocalWinsMutations(localState: localState, remote: remote)
        let pendingMutations = dedupePendingMutations(localState.pendingMutations + generatedMutations)

        return OfflineSyncState(
            lastSuccessfulSyncEpochMs: localState.lastSuccessfulSyncEpochMs,
            lastSyncAttemptEpochMs: localState.lastSyncAttemptEpochMs,
            todos: mergedTodos.sorted(by: cachedTodoSortPrecedes),
            completedItems: mergedCompleted.sorted { $0.completedAtEpochMs > $1.completedAtEpochMs },
            lists: orderListsLikeWeb(mergedLists),
            pendingMutations: pendingMutations,
            aiSummaryEnabled: remote.aiSummaryEnabled
        )
    }

    private func buildLocalWinsMutations(localState: OfflineSyncState, remote: RemoteSnapshot) -> [PendingMutationRecord] {
        let existingKeys = Set(localState.pendingMutations.map { mutationKey(for: $0) })
        let pendingTodoTargets = Set(
            localState.pendingMutations.compactMap { mutation -> String? in
                mutation.kind.affectsTodo ? mutation.targetId : nil
            }
        )
        let pendingListTargets = Set(
            localState.pendingMutations.compactMap { mutation -> String? in
                switch mutation.kind {
                case .createList, .updateList, .deleteList:
                    return mutation.targetId
                default:
                    return nil
                }
            }
        )
        var generated: [PendingMutationRecord] = []

        for todo in localState.todos
            where !todo.canonicalId.hasPrefix(LOCAL_TODO_PREFIX) &&
                !pendingTodoTargets.contains(todo.canonicalId) {
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

        for list in localState.lists where !list.id.hasPrefix(LOCAL_LIST_PREFIX) && !pendingListTargets.contains(list.id) {
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

        case .deleteList:
            guard let targetID else { return }
            if targetID.hasPrefix(LOCAL_LIST_PREFIX) {
                return
            }
            _ = try await api.deleteListByBody(payload: DeleteListRequest(id: targetID))

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
                    due: mutation.dueEpochMs.map { Date(epochMilliseconds: $0).ISO8601Format() },
                    rrule: mutation.dueEpochMs == nil ? nil : mutation.rrule,
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
            let isDueOnlyMove = mutation.dueEpochMs != nil &&
                mutation.title == nil &&
                mutation.description == nil &&
                mutation.priority == nil &&
                mutation.pinned == nil &&
                mutation.completed == nil &&
                mutation.rrule == nil &&
                mutation.listId == nil
            if let instanceDateEpochMs = mutation.instanceDateEpochMs {
                _ = try await api.patchTodoInstanceByBody(
                    payload: TodoInstancePatchRequest(
                        todoId: targetID,
                        instanceDate: Date(epochMilliseconds: instanceDateEpochMs).ISO8601Format(),
                        title: mutation.title,
                        description: mutation.description,
                        priority: mutation.priority,
                        due: mutation.dueEpochMs.map { Date(epochMilliseconds: $0).ISO8601Format() }
                    )
                )
            } else {
                _ = try await api.patchTodoByBody(
                    payload: UpdateTodoRequest(
                        id: targetID,
                        title: mutation.title,
                        description: mutation.description,
                        pinned: mutation.pinned,
                        priority: mutation.priority,
                        completed: mutation.completed,
                        due: mutation.dueEpochMs.map { Date(epochMilliseconds: $0).ISO8601Format() },
                        rrule: isDueOnlyMove ? nil : mutation.rrule,
                        listID: isDueOnlyMove ? nil : resolvedListID,
                        dateChanged: true,
                        rruleChanged: isDueOnlyMove ? nil : true,
                        instanceDate: nil
                    )
                )
            }

        case .deleteTodo:
            guard let targetID else { return }
            if let instanceDateEpochMs = mutation.instanceDateEpochMs {
                _ = try await api.deleteTodoInstanceByBody(
                    payload: TodoInstanceDeleteRequest(
                        todoId: targetID,
                        instanceDate: Date(epochMilliseconds: instanceDateEpochMs).ISO8601Format()
                    )
                )
            } else {
                _ = try await api.deleteTodoByBody(payload: DeleteTodoRequest(id: targetID))
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
                payload: TodoPrioritizeRequest(
                    id: targetID,
                    priority: mutation.priority ?? "Low",
                    instanceDate: mutation.instanceDateEpochMs.map { Date(epochMilliseconds: $0).ISO8601Format() }
                )
            )

        case .completeTodo, .completeTodoInstance:
            guard let targetID else { return }
            _ = try await api.completeTodoByBody(
                payload: TodoCompleteRequest(
                    id: targetID,
                    instanceDate: mutation.instanceDateEpochMs.map { Date(epochMilliseconds: $0).ISO8601Format() }
                )
            )

        case .uncompleteTodo:
            guard let targetID else { return }
            _ = try await api.uncompleteTodoByBody(
                payload: TodoUncompleteRequest(
                    id: targetID,
                    instanceDate: mutation.instanceDateEpochMs.map { Date(epochMilliseconds: $0).ISO8601Format() }
                )
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
                        dueEpochMs: item.dueEpochMs,
                        completedAtEpochMs: item.completedAtEpochMs,
                        rrule: item.rrule,
                        instanceDateEpochMs: item.instanceDateEpochMs,
                        listId: item.listId,
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
            completedItems: state.completedItems.map { completed in
                guard completed.listId == localListID else {
                    return completed
                }
                return CachedCompletedRecord(
                    id: completed.id,
                    originalTodoId: completed.originalTodoId,
                    title: completed.title,
                    description: completed.description,
                    priority: completed.priority,
                    dueEpochMs: completed.dueEpochMs,
                    completedAtEpochMs: completed.completedAtEpochMs,
                    rrule: completed.rrule,
                    instanceDateEpochMs: completed.instanceDateEpochMs,
                    listId: serverListID,
                    listName: completed.listName,
                    listColor: completed.listColor
                )
            },
            lists: state.lists.map { list in
                if list.id == localListID {
                    return CachedListRecord(
                        id: serverListID,
                        name: list.name,
                        color: list.color,
                        iconKey: list.iconKey,
                        todoCount: list.todoCount,
                        updatedAtEpochMs: list.updatedAtEpochMs,
                        createdAtEpochMs: list.createdAtEpochMs
                    )
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

private extension MutationKind {
    var affectsTodo: Bool {
        switch self {
        case .createTodo,
             .updateTodo,
             .deleteTodo,
             .setPinned,
             .setPriority,
             .completeTodo,
             .completeTodoInstance,
             .uncompleteTodo:
            return true
        case .createList,
             .updateList,
             .deleteList:
            return false
        }
    }
}
