import Foundation

extension Notification.Name {
    static let offlineSyncAttemptFailed = Notification.Name("tday.offline-sync.attempt-failed")
    static let offlineSyncAttemptSucceeded = Notification.Name("tday.offline-sync.attempt-succeeded")
    /// A USER-INITIATED refresh (pull-to-refresh) hit a connectivity/backend failure. Unlike
    /// `offlineSyncAttemptFailed` (background/mutation-triggered, cooldown-gated), this always
    /// surfaces a toast. userInfo["serverDown"] Bool distinguishes a 5xx from a no-network state.
    static let userInitiatedSyncFailedOffline = Notification.Name("tday.user-sync.failed-offline")
}

private struct RemoteSnapshot {
    let todos: [TodoItem]
    let floaters: [TodoItem]
    let completedItems: [CompletedItem]
    let completedFloaters: [CompletedItem]
    let lists: [ListSummary]
    let floaterLists: [ListSummary]
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

    var floaterListUpdatedAtByID: [String: Int64] {
        floaterLists.reduce(into: [:]) { result, list in
            let updatedAt = list.updatedAt?.epochMilliseconds ?? 0
            result[list.id] = max(result[list.id] ?? 0, updatedAt)
        }
    }

    var floaterUpdatedAtByCanonical: [String: Int64] {
        floaters.reduce(into: [:]) { result, floater in
            let updatedAt = floater.updatedAt?.epochMilliseconds ?? 0
            result[floater.canonicalId] = max(result[floater.canonicalId] ?? 0, updatedAt)
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

func mergeCompletedFloaterRecordsWithPendingOverrides(
    localRecords: [CachedCompletedFloaterRecord],
    remoteRecords: [CachedCompletedFloaterRecord],
    pendingFloaterTargets: Set<String>,
    pendingDeletedListIds: Set<String> = []
) -> [CachedCompletedFloaterRecord] {
    var mergedRecords = remoteRecords.filter { record in
        guard let listId = record.listId else {
            return true
        }
        return !pendingDeletedListIds.contains(listId)
    }

    for canonicalID in pendingFloaterTargets {
        let localRecordsForFloater = localRecords.filter { $0.originalFloaterId == canonicalID }
        guard !localRecordsForFloater.isEmpty else {
            continue
        }
        mergedRecords.removeAll { $0.originalFloaterId == canonicalID }
        mergedRecords.append(contentsOf: localRecordsForFloater)
    }

    return mergedRecords
}

@MainActor
final class SyncManager {
    private let api: TdayAPIService
    private let cacheManager: OfflineCacheManager
    private let secureStore: SecureStore

    private let offlineResyncIntervalMs: Int64 = 5 * 60 * 1_000
    private let minForceSyncIntervalMs: Int64 = 1_200

    init(api: TdayAPIService, cacheManager: OfflineCacheManager, secureStore: SecureStore) {
        self.api = api
        self.cacheManager = cacheManager
        self.secureStore = secureStore
    }

    func hasPendingMutations() -> Bool {
        !isLocalMode && !cacheManager.loadOfflineState().pendingMutations.isEmpty
    }

    var isLocalMode: Bool {
        secureStore.isLocalMode()
    }

    func syncCachedData(
        force: Bool = false,
        replayPendingMutations: Bool = true,
        notifyOfflineFailure: Bool = true,
        userInitiated: Bool = false,
        connectionProbeTimeoutSeconds: TimeInterval? = nil
    ) async -> Result<Void, Error> {
        if isLocalMode {
            TdayTelemetry.addBreadcrumb("local_mode.sync_noop")
            if let localState = try? await cacheManager.updateOfflineState({ state in
                var nextState = state
                nextState.lastSuccessfulSyncEpochMs = 0
                nextState.lastSyncAttemptEpochMs = 0
                nextState.pendingMutations = []
                return nextState
            }) {
                TodayTasksWidgetSnapshotStore.saveTodayTasks(from: localState)
                FloaterTasksWidgetSnapshotStore.saveFloaterTasks(from: localState)
            }
            return .success(())
        }

        do {
            var contactedServer = false
            if let connectionProbeTimeoutSeconds {
                TdayTelemetry.addBreadcrumb("server.probe", data: ["phase": "sync"])
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
            if isLikelyConnectivityIssue(error) {
                if userInitiated {
                    // Pull-to-refresh: always surface a toast (bypasses the cooldown),
                    // carrying whether it's a backend 5xx vs a no-network state.
                    NotificationCenter.default.post(
                        name: .userInitiatedSyncFailedOffline,
                        object: nil,
                        userInfo: ["serverDown": isBackendUnavailableError(error)]
                    )
                } else if notifyOfflineFailure {
                    NotificationCenter.default.post(name: .offlineSyncAttemptFailed, object: nil)
                }
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
        if shouldReplay {
            TdayTelemetry.addBreadcrumb(
                "sync.replay",
                data: ["pendingMutationCount": state.pendingMutations.count]
            )
        }
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
        async let floatersTask = api.getFloaters()
        async let completedTask = api.getCompletedTodos()
        async let completedFloatersTask = api.getCompletedFloaters()
        async let listsTask = api.getLists()
        async let floaterListsTask = api.getFloaterLists()
        // AI summary is a per-user preference now (the old global app-settings flag is gone).
        async let preferencesTask = api.getPreferences()

        let todosResponse = try await todosTask
        let floatersResponse = try await floatersTask
        let completedResponse = try await completedTask
        let completedFloatersResponse = try await completedFloatersTask
        let listsResponse = try await listsTask
        let floaterListsResponse = try await floaterListsTask
        let preferencesResponse = try await preferencesTask
        let todos = todosResponse.todos.map(mapTodoDTO)
        let floaters = floatersResponse.floaters.map(mapFloaterDTO)
        let completedItems = completedResponse.completedTodos.map(mapCompletedDTO)
        let completedFloaters = completedFloatersResponse.completedFloaters.map(mapCompletedFloaterDTO)
        let lists = listsResponse.lists.map { mapListDTO($0) }
        let floaterLists = floaterListsResponse.lists.map { mapFloaterListDTO($0) }
        // NULL preference is treated as enabled (default ON), matching the backend.
        let aiSummaryEnabled = preferencesResponse.aiSummaryEnabled ?? true
        return RemoteSnapshot(
            todos: todos,
            floaters: floaters,
            completedItems: completedItems,
            completedFloaters: completedFloaters,
            lists: lists,
            floaterLists: floaterLists,
            aiSummaryEnabled: aiSummaryEnabled
        )
    }

    private func mergeRemoteWithLocal(localState: OfflineSyncState, remote: RemoteSnapshot) -> OfflineSyncState {
        let pendingTodoTargets = Set(
            localState.pendingMutations.compactMap { mutation -> String? in
                mutation.kind.affectsTodo ? mutation.targetId : nil
            }
        )
        let pendingFloaterTargets = Set(
            localState.pendingMutations.compactMap { mutation -> String? in
                mutation.kind.affectsFloater ? mutation.targetId : nil
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
        let pendingFloaterListTargets = Set(
            localState.pendingMutations.compactMap { mutation -> String? in
                switch mutation.kind {
                case .createFloaterList, .updateFloaterList, .deleteFloaterList, .resetFloaterList:
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
        let pendingDeletedFloaterListIds = Set(
            localState.pendingMutations.compactMap { mutation -> String? in
                mutation.kind == .deleteFloaterList ? mutation.targetId : nil
            }
        )
        let remoteTodos = remote.todos.filter { todo in
            guard let listId = todo.listId else {
                return true
            }
            return !pendingDeletedListIds.contains(listId)
        }
        let remoteFloaters = remote.floaters.filter { floater in
            guard let listId = floater.listId else {
                return true
            }
            return !pendingDeletedFloaterListIds.contains(listId)
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

        var remoteFloatersByID = Dictionary(uniqueKeysWithValues: remoteFloaters.map { ($0.canonicalId, floaterToCache($0)) })
        var mergedFloaters: [CachedFloaterRecord] = []
        for localFloater in localState.floaters {
            let remoteFloater = remoteFloatersByID[localFloater.canonicalId]
            if remoteFloater == nil,
               !localFloater.canonicalId.hasPrefix(LOCAL_FLOATER_PREFIX),
               !pendingFloaterTargets.contains(localFloater.canonicalId) {
                continue
            }
            let localWins = localFloater.canonicalId.hasPrefix(LOCAL_FLOATER_PREFIX) ||
                pendingFloaterTargets.contains(localFloater.canonicalId) ||
                localFloater.updatedAtEpochMs > (remoteFloater?.updatedAtEpochMs ?? 0)
            if localWins {
                mergedFloaters.append(localFloater)
                remoteFloatersByID.removeValue(forKey: localFloater.canonicalId)
            }
        }
        mergedFloaters.append(contentsOf: remoteFloatersByID.values)

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

        var remoteFloaterListsByID = Dictionary(uniqueKeysWithValues: remote.floaterLists.map { ($0.id, floaterListToCache($0)) })
        var mergedFloaterLists: [CachedFloaterListRecord] = []
        for localList in localState.floaterLists {
            let remoteList = remoteFloaterListsByID[localList.id]
            if remoteList == nil,
               !localList.id.hasPrefix(LOCAL_FLOATER_LIST_PREFIX),
               !pendingFloaterListTargets.contains(localList.id) {
                continue
            }
            let localWins = localList.id.hasPrefix(LOCAL_FLOATER_LIST_PREFIX) ||
                pendingFloaterListTargets.contains(localList.id) ||
                localList.updatedAtEpochMs > (remoteList?.updatedAtEpochMs ?? 0)
            if localWins {
                mergedFloaterLists.append(localList)
                remoteFloaterListsByID.removeValue(forKey: localList.id)
            }
        }
        mergedFloaterLists.append(
            contentsOf: remoteFloaterListsByID.values.filter { !pendingDeletedFloaterListIds.contains($0.id) }
        )

        let mergedCompleted = mergeCompletedRecordsWithPendingOverrides(
            localRecords: localState.completedItems,
            remoteRecords: remote.completedItems.map(completedToCache),
            pendingTodoTargets: pendingTodoTargets,
            pendingDeletedListIds: pendingDeletedListIds
        )
        let mergedCompletedFloaters = mergeCompletedFloaterRecordsWithPendingOverrides(
            localRecords: localState.completedFloaters,
            remoteRecords: remote.completedFloaters.map(completedFloaterToCache),
            pendingFloaterTargets: pendingFloaterTargets,
            pendingDeletedListIds: pendingDeletedFloaterListIds
        )

        let todoCountsByList = Dictionary(grouping: mergedTodos.filter { !$0.completed }, by: { $0.listId })
            .mapValues(\.count)
        let floaterCountsByList = Dictionary(grouping: mergedFloaters.filter { !$0.completed }, by: { $0.listId })
            .mapValues(\.count)

        let generatedMutations = buildLocalWinsMutations(localState: localState, remote: remote)
        let pendingMutations = dedupePendingMutations(localState.pendingMutations + generatedMutations)

        return OfflineSyncState(
            lastSuccessfulSyncEpochMs: localState.lastSuccessfulSyncEpochMs,
            lastSyncAttemptEpochMs: localState.lastSyncAttemptEpochMs,
            todos: mergedTodos.sorted(by: cachedTodoSortPrecedes),
            floaters: mergedFloaters.sorted(by: cachedFloaterSortPrecedes),
            completedItems: mergedCompleted.sorted { $0.completedAtEpochMs > $1.completedAtEpochMs },
            completedFloaters: mergedCompletedFloaters.sorted { $0.completedAtEpochMs > $1.completedAtEpochMs },
            lists: orderListsLikeWeb(
                mergedLists.map { list in
                    CachedListRecord(
                        id: list.id,
                        name: list.name,
                        color: list.color,
                        iconKey: list.iconKey,
                        todoCount: todoCountsByList[list.id] ?? 0,
                        updatedAtEpochMs: list.updatedAtEpochMs,
                        createdAtEpochMs: list.createdAtEpochMs
                    )
                }
            ),
            floaterLists: orderFloaterListsLikeWeb(
                mergedFloaterLists.map { list in
                    CachedFloaterListRecord(
                        id: list.id,
                        name: list.name,
                        color: list.color,
                        iconKey: list.iconKey,
                        todoCount: floaterCountsByList[list.id] ?? 0,
                        updatedAtEpochMs: list.updatedAtEpochMs,
                        createdAtEpochMs: list.createdAtEpochMs
                    )
                }
            ),
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
        let pendingFloaterTargets = Set(
            localState.pendingMutations.compactMap { mutation -> String? in
                mutation.kind.affectsFloater ? mutation.targetId : nil
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
        let pendingFloaterListTargets = Set(
            localState.pendingMutations.compactMap { mutation -> String? in
                switch mutation.kind {
                case .createFloaterList, .updateFloaterList, .deleteFloaterList, .resetFloaterList:
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

        for floater in localState.floaters
            where !floater.canonicalId.hasPrefix(LOCAL_FLOATER_PREFIX) &&
                !pendingFloaterTargets.contains(floater.canonicalId) {
            guard let remoteUpdatedAt = remote.floaterUpdatedAtByCanonical[floater.canonicalId], floater.updatedAtEpochMs > remoteUpdatedAt else {
                continue
            }
            let mutation = PendingMutationRecord(
                mutationId: UUID().uuidString,
                kind: .updateFloater,
                targetId: floater.canonicalId,
                timestampEpochMs: floater.updatedAtEpochMs,
                title: floater.title,
                description: floater.description,
                priority: floater.priority,
                dueEpochMs: nil,
                rrule: nil,
                listId: floater.listId,
                pinned: floater.pinned,
                completed: floater.completed,
                instanceDateEpochMs: nil,
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

        for list in localState.floaterLists where !list.id.hasPrefix(LOCAL_FLOATER_LIST_PREFIX) && !pendingFloaterListTargets.contains(list.id) {
            guard let remoteUpdatedAt = remote.floaterListUpdatedAtByID[list.id], list.updatedAtEpochMs > remoteUpdatedAt else {
                continue
            }
            let mutation = PendingMutationRecord(
                mutationId: UUID().uuidString,
                kind: .updateFloaterList,
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
        var resolvedFloaterIDs: [String: String] = [:]
        var resolvedListIDs: [String: String] = [:]
        var resolvedFloaterListIDs: [String: String] = [:]
        let orderedMutations = initialState.pendingMutations.sorted { $0.timestampEpochMs < $1.timestampEpochMs }

        for index in orderedMutations.indices {
            let mutation = orderedMutations[index]
            do {
                try await applyPendingMutation(
                    mutation,
                    state: &state,
                    remoteSnapshot: remoteSnapshot,
                    resolvedTodoIDs: &resolvedTodoIDs,
                    resolvedFloaterIDs: &resolvedFloaterIDs,
                    resolvedListIDs: &resolvedListIDs,
                    resolvedFloaterListIDs: &resolvedFloaterListIDs
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
        resolvedFloaterIDs: inout [String: String],
        resolvedListIDs: inout [String: String],
        resolvedFloaterListIDs: inout [String: String]
    ) async throws {
        let targetID = resolveTargetID(
            mutation.targetId,
            todoMap: resolvedTodoIDs,
            floaterMap: resolvedFloaterIDs,
            listMap: resolvedListIDs.merging(resolvedFloaterListIDs) { current, _ in current }
        )
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

        case .createFloaterList:
            guard let localListID = mutation.targetId else { return }
            if !localListID.hasPrefix(LOCAL_FLOATER_LIST_PREFIX) {
                return
            }
            let response = try await api.createFloaterList(
                payload: CreateFloaterListRequest(name: mutation.name ?? "Untitled", color: mutation.color, iconKey: mutation.iconKey)
            )
            guard let createdList = response.list else { return }
            resolvedFloaterListIDs[localListID] = createdList.id
            state = replaceLocalFloaterListID(state, localListID: localListID, serverListID: createdList.id)

        case .updateFloaterList:
            guard let targetID else { return }
            let remoteUpdatedAt = remoteSnapshot.floaterListUpdatedAtByID[targetID] ?? 0
            guard remoteUpdatedAt <= mutation.timestampEpochMs else { return }
            _ = try await api.patchFloaterListByBody(
                payload: UpdateFloaterListRequest(id: targetID, name: mutation.name, color: mutation.color, iconKey: mutation.iconKey)
            )

        case .deleteFloaterList:
            guard let targetID else { return }
            if targetID.hasPrefix(LOCAL_FLOATER_LIST_PREFIX) {
                return
            }
            _ = try await api.deleteFloaterListByBody(payload: DeleteFloaterListRequest(id: targetID))

        case .resetFloaterList:
            guard let targetID else { return }
            if targetID.hasPrefix(LOCAL_FLOATER_LIST_PREFIX) {
                return
            }
            _ = try await api.resetFloaterList(id: targetID)

        case .createTodo:
            guard let localTodoID = mutation.targetId else { return }
            if !localTodoID.hasPrefix(LOCAL_TODO_PREFIX) {
                return
            }
            guard let dueEpochMs = mutation.dueEpochMs else { return }
            let resolvedListID = mutation.listId.flatMap { resolvedListIDs[$0] ?? $0 }
            let response = try await api.createTodo(
                payload: CreateTodoRequest(
                    title: mutation.title ?? "Untitled",
                    description: mutation.description,
                    priority: mutation.priority ?? "Low",
                    due: Date(epochMilliseconds: dueEpochMs).ISO8601Format(),
                    rrule: mutation.rrule,
                    listID: resolvedListID
                )
            )
            guard let createdTodo = response.todo else { return }
            resolvedTodoIDs[localTodoID] = createdTodo.id
            state = replaceLocalTodoID(state, localTodoID: localTodoID, serverTodoID: createdTodo.id)

        case .createFloater:
            guard let localFloaterID = mutation.targetId else { return }
            if !localFloaterID.hasPrefix(LOCAL_FLOATER_PREFIX) {
                return
            }
            let resolvedListID = mutation.listId.flatMap { resolvedFloaterListIDs[$0] ?? $0 }
            let response = try await api.createFloater(
                payload: CreateFloaterRequest(
                    title: mutation.title ?? "Untitled",
                    description: mutation.description,
                    priority: mutation.priority ?? "Low",
                    listID: resolvedListID
                )
            )
            guard let createdFloater = response.floater else { return }
            resolvedFloaterIDs[localFloaterID] = createdFloater.id
            state = replaceLocalFloaterID(state, localFloaterID: localFloaterID, serverFloaterID: createdFloater.id)

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

        case .updateFloater:
            guard let targetID else { return }
            let remoteUpdatedAt = remoteSnapshot.floaterUpdatedAtByCanonical[targetID] ?? 0
            guard remoteUpdatedAt <= mutation.timestampEpochMs else { return }
            let resolvedListID = mutation.listId.flatMap { resolvedFloaterListIDs[$0] ?? $0 }
            _ = try await api.patchFloaterByBody(
                payload: UpdateFloaterRequest(
                    id: targetID,
                    title: mutation.title,
                    description: mutation.description,
                    pinned: mutation.pinned,
                    priority: mutation.priority,
                    completed: mutation.completed,
                    listID: resolvedListID
                )
            )

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

        case .deleteFloater:
            guard let targetID else { return }
            if targetID.hasPrefix(LOCAL_FLOATER_PREFIX) {
                return
            }
            _ = try await api.deleteFloaterByBody(payload: DeleteFloaterRequest(id: targetID))

        case .setPinned:
            guard let targetID else { return }
            if targetID.hasPrefix(LOCAL_FLOATER_PREFIX) || remoteSnapshot.floaterUpdatedAtByCanonical[targetID] != nil {
                _ = try await api.patchFloaterByBody(
                    payload: UpdateFloaterRequest(id: targetID, title: nil, description: nil, pinned: mutation.pinned, priority: nil, completed: nil, listID: nil)
                )
            } else {
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
            }

        case .setPriority:
            guard let targetID else { return }
            if targetID.hasPrefix(LOCAL_FLOATER_PREFIX) || remoteSnapshot.floaterUpdatedAtByCanonical[targetID] != nil {
                _ = try await api.prioritizeFloaterByBody(
                    payload: FloaterPrioritizeRequest(id: targetID, priority: mutation.priority ?? "Low")
                )
            } else {
                _ = try await api.prioritizeTodoByBody(
                    payload: TodoPrioritizeRequest(
                        id: targetID,
                        priority: mutation.priority ?? "Low",
                        instanceDate: mutation.instanceDateEpochMs.map { Date(epochMilliseconds: $0).ISO8601Format() }
                    )
                )
            }

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

        case .completeFloater:
            guard let targetID else { return }
            _ = try await api.completeFloaterByBody(payload: FloaterCompleteRequest(id: targetID))

        case .uncompleteFloater:
            guard let targetID else { return }
            _ = try await api.uncompleteFloaterByBody(payload: FloaterUncompleteRequest(id: targetID))

        case .promoteFloater:
            guard let targetID else { return }
            // A floater that never reached the server has nothing to promote yet;
            // its CREATE_FLOATER replays first and resolveTargetID remaps us.
            if targetID.hasPrefix(LOCAL_FLOATER_PREFIX) { return }
            guard let dueEpochMs = mutation.dueEpochMs else { return }
            let response = try await api.promoteFloater(
                id: targetID,
                payload: PromoteFloaterRequest(
                    due: Date(epochMilliseconds: dueEpochMs).ISO8601Format(),
                    rrule: mutation.rrule
                )
            )
            // Remap the optimistic local todo (minted at enqueue time, carried
            // in `name`) to the server row — CREATE_TODO-style reconciliation.
            if let promotedTodo = response.todo,
               let localTodoID = mutation.name,
               localTodoID.hasPrefix(LOCAL_TODO_PREFIX) {
                resolvedTodoIDs[localTodoID] = promotedTodo.id
                state = replaceLocalTodoID(state, localTodoID: localTodoID, serverTodoID: promotedTodo.id)
            }

        case .demoteTodo:
            guard let targetID else { return }
            if targetID.hasPrefix(LOCAL_TODO_PREFIX) { return }
            let response = try await api.demoteTodo(id: targetID)
            if let demotedFloater = response.floater,
               let localFloaterID = mutation.name,
               localFloaterID.hasPrefix(LOCAL_FLOATER_PREFIX) {
                resolvedFloaterIDs[localFloaterID] = demotedFloater.id
                state = replaceLocalFloaterID(state, localFloaterID: localFloaterID, serverFloaterID: demotedFloater.id)
            }

        case .createStep:
            guard let targetID else { return }
            // The parent todo must exist server-side before a step attaches.
            if targetID.hasPrefix(LOCAL_TODO_PREFIX) { return }
            let response = try await api.createTaskStep(
                payload: CreateTaskStepRequest(todoId: targetID, title: mutation.title ?? "")
            )
            // Remap the optimistic local step id (carried in `name`) so a later
            // TOGGLE/DELETE in this same batch resolves correctly.
            if let createdStep = response.step,
               let localStepID = mutation.name,
               localStepID.hasPrefix(LOCAL_STEP_PREFIX) {
                resolvedTodoIDs[localStepID] = createdStep.id
            }

        case .toggleStep:
            guard let targetID else { return }
            if targetID.hasPrefix(LOCAL_STEP_PREFIX) { return }
            _ = try await api.toggleTaskStep(
                payload: ToggleTaskStepRequest(id: targetID, completed: mutation.completed ?? false)
            )

        case .deleteStep:
            guard let targetID else { return }
            // A step that never synced has nothing to delete server-side.
            if targetID.hasPrefix(LOCAL_STEP_PREFIX) { return }
            _ = try await api.deleteTaskStep(payload: DeleteTaskStepRequest(id: targetID))

        case .reorderSteps:
            guard let targetID else { return }
            if targetID.hasPrefix(LOCAL_TODO_PREFIX) { return }
            let orderedIDs = (mutation.orderedIds ?? [])
                .map { resolvedTodoIDs[$0] ?? $0 }
                .filter { !$0.hasPrefix(LOCAL_STEP_PREFIX) }
            if orderedIDs.isEmpty { return }
            _ = try await api.reorderTaskSteps(
                payload: ReorderTaskStepsRequest(todoId: targetID, orderedIds: orderedIDs)
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
            floaters: state.floaters,
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
                if floater.canonicalId == localFloaterID || floater.id == localFloaterID {
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
                }
                return floater
            },
            completedItems: state.completedItems,
            completedFloaters: state.completedFloaters.map { item in
                if item.originalFloaterId == localFloaterID {
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
                }
                return item
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
            floaters: state.floaters,
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
            completedFloaters: state.completedFloaters,
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
            floaterLists: state.floaterLists,
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

    private func replaceLocalFloaterListID(_ state: OfflineSyncState, localListID: String, serverListID: String) -> OfflineSyncState {
        OfflineSyncState(
            lastSuccessfulSyncEpochMs: state.lastSuccessfulSyncEpochMs,
            lastSyncAttemptEpochMs: state.lastSyncAttemptEpochMs,
            todos: state.todos,
            floaters: state.floaters.map { floater in
                guard floater.listId == localListID else {
                    return floater
                }
                return CachedFloaterRecord(
                    id: floater.id,
                    canonicalId: floater.canonicalId,
                    title: floater.title,
                    description: floater.description,
                    priority: floater.priority,
                    pinned: floater.pinned,
                    completed: floater.completed,
                    listId: serverListID,
                    updatedAtEpochMs: floater.updatedAtEpochMs
                )
            },
            completedItems: state.completedItems,
            completedFloaters: state.completedFloaters.map { completed in
                guard completed.listId == localListID else {
                    return completed
                }
                return CachedCompletedFloaterRecord(
                    id: completed.id,
                    originalFloaterId: completed.originalFloaterId,
                    title: completed.title,
                    description: completed.description,
                    priority: completed.priority,
                    completedAtEpochMs: completed.completedAtEpochMs,
                    listId: serverListID,
                    listName: completed.listName,
                    listColor: completed.listColor
                )
            },
            lists: state.lists,
            floaterLists: state.floaterLists.map { list in
                guard list.id == localListID else {
                    return list
                }
                return CachedFloaterListRecord(
                    id: serverListID,
                    name: list.name,
                    color: list.color,
                    iconKey: list.iconKey,
                    todoCount: list.todoCount,
                    updatedAtEpochMs: list.updatedAtEpochMs,
                    createdAtEpochMs: list.createdAtEpochMs
                )
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

    private func resolveTargetID(_ original: String?, todoMap: [String: String], floaterMap: [String: String], listMap: [String: String]) -> String? {
        guard let original else {
            return nil
        }
        if let todoID = todoMap[original] {
            return todoID
        }
        if let floaterID = floaterMap[original] {
            return floaterID
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
             .uncompleteTodo,
             // Consumes a todo (its optimistic floater is local-prefixed and
             // therefore already merge-protected).
             .demoteTodo:
            return true
        case .createList,
             .updateList,
             .deleteList,
             .createFloaterList,
             .updateFloaterList,
             .deleteFloaterList,
             .resetFloaterList,
             .createFloater,
             .updateFloater,
             .deleteFloater,
             .completeFloater,
             .uncompleteFloater,
             .promoteFloater,
             // Steps live on their own table, not the todo row.
             .createStep,
             .toggleStep,
             .deleteStep,
             .reorderSteps:
            return false
        }
    }

    var affectsFloater: Bool {
        switch self {
        case .createFloater,
             .updateFloater,
             .deleteFloater,
             .completeFloater,
             .uncompleteFloater,
             // Un-completes every floater in the list.
             .resetFloaterList,
             // Consumes a floater (its optimistic todo is local-prefixed and
             // therefore already merge-protected).
             .promoteFloater:
            return true
        case .createList,
             .updateList,
             .deleteList,
             .createFloaterList,
             .updateFloaterList,
             .deleteFloaterList,
             .createTodo,
             .updateTodo,
             .deleteTodo,
             .setPinned,
             .setPriority,
             .completeTodo,
             .completeTodoInstance,
             .uncompleteTodo,
             .demoteTodo,
             .createStep,
             .toggleStep,
             .deleteStep,
             .reorderSteps:
            return false
        }
    }
}
