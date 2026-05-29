import Foundation

@MainActor
final class FloaterListRepository {
    private let api: TdayAPIService
    private let cacheManager: OfflineCacheManager
    private let syncManager: SyncManager

    init(api: TdayAPIService, cacheManager: OfflineCacheManager, syncManager: SyncManager) {
        self.api = api
        self.cacheManager = cacheManager
        self.syncManager = syncManager
    }

    func fetchLists() -> [ListSummary] {
        buildLists(from: cacheManager.loadOfflineState())
    }

    func fetchListsSnapshot() -> [ListSummary] {
        buildLists(from: cacheManager.loadOfflineState())
    }

    func createList(name: String, color: String? = nil, iconKey: String? = nil) async throws {
        let normalizedName = capitalizeFirstListLetter(name).trimmingCharacters(in: .whitespacesAndNewlines)
        guard !normalizedName.isEmpty else {
            return
        }

        let now = Date().epochMilliseconds
        let localListID = LOCAL_FLOATER_LIST_PREFIX + UUID().uuidString.lowercased()
        let mutationID = UUID().uuidString
        _ = try await cacheManager.updateOfflineState { state in
            var nextState = state
            nextState.floaterLists.append(
                CachedFloaterListRecord(
                    id: localListID,
                    name: normalizedName,
                    color: color,
                    iconKey: iconKey,
                    todoCount: 0,
                    updatedAtEpochMs: now,
                    createdAtEpochMs: now
                )
            )
            nextState.pendingMutations.append(
                PendingMutationRecord(
                    mutationId: mutationID,
                    kind: .createFloaterList,
                    targetId: localListID,
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
                    name: normalizedName,
                    color: color,
                    iconKey: iconKey
                )
            )
            return nextState
        }

        do {
            let response = try await api.createFloaterList(
                payload: CreateFloaterListRequest(name: normalizedName, color: color, iconKey: iconKey)
            )
            guard let createdList = response.list else {
                return
            }
            let createdAt = parseOptionalDate(createdList.createdAt)?.epochMilliseconds ?? now
            let updatedAt = parseOptionalDate(createdList.updatedAt)?.epochMilliseconds ?? now
            _ = try await cacheManager.updateOfflineState { state in
                var nextState = self.replaceLocalFloaterListID(
                    state,
                    localListID: localListID,
                    serverListID: createdList.id
                )
                let todoCount = nextState.floaters.filter { !$0.completed && $0.listId == createdList.id }.count
                nextState.floaterLists = nextState.floaterLists.map { list in
                    guard list.id == createdList.id else {
                        return list
                    }
                    return CachedFloaterListRecord(
                        id: createdList.id,
                        name: createdList.name,
                        color: createdList.color,
                        iconKey: createdList.iconKey ?? list.iconKey,
                        todoCount: todoCount,
                        updatedAtEpochMs: updatedAt,
                        createdAtEpochMs: createdAt
                    )
                }
                nextState.pendingMutations.removeAll { $0.mutationId == mutationID }
                return nextState
            }
        } catch {
            // Keep the pending CREATE_FLOATER_LIST mutation so background sync can retry it.
        }
    }

    func updateList(listId: String, name: String, color: String? = nil, iconKey: String? = nil) async throws {
        let normalizedName = capitalizeFirstListLetter(name).trimmingCharacters(in: .whitespacesAndNewlines)
        guard !listId.isEmpty, !normalizedName.isEmpty else {
            return
        }

        let now = Date().epochMilliseconds
        if listId.hasPrefix(LOCAL_FLOATER_LIST_PREFIX) {
            _ = try await cacheManager.updateOfflineState { state in
                var nextState = state
                nextState.floaterLists = state.floaterLists.map { list in
                    guard list.id == listId else {
                        return list
                    }
                    return CachedFloaterListRecord(
                        id: list.id,
                        name: normalizedName,
                        color: color ?? list.color,
                        iconKey: iconKey ?? list.iconKey,
                        todoCount: list.todoCount,
                        updatedAtEpochMs: now,
                        createdAtEpochMs: list.createdAtEpochMs
                    )
                }
                nextState.pendingMutations = state.pendingMutations.compactMap { mutation in
                    if mutation.kind == .updateFloaterList && mutation.targetId == listId {
                        return nil
                    }
                    guard mutation.kind == .createFloaterList, mutation.targetId == listId else {
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
                        dueEpochMs: mutation.dueEpochMs,
                        rrule: mutation.rrule,
                        listId: mutation.listId,
                        pinned: mutation.pinned,
                        completed: mutation.completed,
                        instanceDateEpochMs: mutation.instanceDateEpochMs,
                        name: normalizedName,
                        color: color ?? mutation.color,
                        iconKey: iconKey ?? mutation.iconKey
                    )
                }
                return nextState
            }
            _ = await syncManager.syncCachedData(force: true, replayPendingMutations: true)
            return
        }

        _ = try await cacheManager.updateOfflineState { state in
            var nextState = state
            nextState.floaterLists = state.floaterLists.map { list in
                guard list.id == listId else { return list }
                return CachedFloaterListRecord(
                    id: list.id,
                    name: normalizedName,
                    color: color ?? list.color,
                    iconKey: iconKey ?? list.iconKey,
                    todoCount: list.todoCount,
                    updatedAtEpochMs: now,
                    createdAtEpochMs: list.createdAtEpochMs
                )
            }
            nextState.pendingMutations.removeAll { $0.kind == .updateFloaterList && $0.targetId == listId }
            nextState.pendingMutations.append(
                PendingMutationRecord(
                    mutationId: UUID().uuidString,
                    kind: .updateFloaterList,
                    targetId: listId,
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
                    name: normalizedName,
                    color: color,
                    iconKey: iconKey
                )
            )
            return nextState
        }
        let result = await syncManager.syncCachedData(force: true, replayPendingMutations: true)
        if case let .failure(error) = result, isLikelyUnrecoverableMutationError(error) {
            throw error
        }
    }

    func deleteList(listId: String, onOptimisticDelete: () -> Void = {}) async throws {
        let normalizedListID = listId.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !normalizedListID.isEmpty else {
            return
        }

        let now = Date().epochMilliseconds
        let mutationID = UUID().uuidString
        let isLocalOnly = normalizedListID.hasPrefix(LOCAL_FLOATER_LIST_PREFIX)
        _ = try await cacheManager.updateOfflineState { state in
            var nextState = state
            let deletedFloaterIDs = Set(state.floaters.filter { $0.listId == normalizedListID }.map(\.canonicalId))

            nextState.floaterLists.removeAll { $0.id == normalizedListID }
            nextState.floaters.removeAll { $0.listId == normalizedListID }
            nextState.completedFloaters.removeAll { completed in
                completed.listId == normalizedListID ||
                    completed.originalFloaterId.map { deletedFloaterIDs.contains($0) } == true
            }
            nextState.pendingMutations.removeAll { mutation in
                mutation.targetId == normalizedListID ||
                    mutation.listId == normalizedListID ||
                    mutation.targetId.map { deletedFloaterIDs.contains($0) } == true
            }
            if !isLocalOnly {
                nextState.pendingMutations.append(
                    PendingMutationRecord(
                        mutationId: mutationID,
                        kind: .deleteFloaterList,
                        targetId: normalizedListID,
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

        onOptimisticDelete()

        let result = await syncManager.syncCachedData(force: true, replayPendingMutations: true)
        if case let .failure(error) = result, isLikelyUnrecoverableMutationError(error) {
            throw error
        }
    }

    private func buildLists(from state: OfflineSyncState) -> [ListSummary] {
        let floaterCounts = Dictionary(grouping: state.floaters.filter { !$0.completed }, by: { $0.listId })
            .mapValues(\.count)
        return orderFloaterListsLikeWeb(state.floaterLists)
            .map { floaterListFromCache($0, todoCountOverride: floaterCounts[$0.id] ?? 0) }
    }

    private func replaceLocalFloaterListID(
        _ state: OfflineSyncState,
        localListID: String,
        serverListID: String
    ) -> OfflineSyncState {
        var nextState = state
        nextState.floaters = state.floaters.map { floater in
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
        }
        nextState.completedFloaters = state.completedFloaters.map { completed in
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
        }
        nextState.floaterLists = state.floaterLists.map { list in
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
        }
        nextState.pendingMutations = state.pendingMutations.map { mutation in
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
        }
        return nextState
    }

    private func capitalizeFirstListLetter(_ value: String) -> String {
        guard let firstLetterIndex = value.firstIndex(where: { $0.isLetter }) else {
            return value
        }
        let current = value[firstLetterIndex]
        let capitalized = String(current).capitalized
        guard String(current) != capitalized else {
            return value
        }

        var result = value
        result.replaceSubrange(firstLetterIndex...firstLetterIndex, with: capitalized)
        return result
    }
}
