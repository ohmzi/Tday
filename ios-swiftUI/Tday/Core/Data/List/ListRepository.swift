import Foundation

@MainActor
final class ListRepository {
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
        let localListID = LOCAL_LIST_PREFIX + UUID().uuidString.lowercased()
        let mutationID = UUID().uuidString
        _ = try await cacheManager.updateOfflineState { state in
            var nextState = state
            nextState.lists.append(
                CachedListRecord(
                    id: localListID,
                    name: normalizedName,
                    color: color,
                    iconKey: iconKey,
                    todoCount: 0,
                    updatedAtEpochMs: now
                )
            )
            nextState.pendingMutations.append(
                PendingMutationRecord(
                    mutationId: mutationID,
                    kind: .createList,
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
            let response = try await api.createList(
                payload: CreateListRequest(name: normalizedName, color: color, iconKey: iconKey)
            )
            guard let createdList = response.list else {
                return
            }
            let createdAt = parseOptionalDate(createdList.updatedAt)?.epochMilliseconds ?? now
            _ = try await cacheManager.updateOfflineState { state in
                var nextState = self.replaceLocalListID(state, localListID: localListID, serverListID: createdList.id)
                let todoCount = nextState.todos.filter { !$0.completed && $0.listId == createdList.id }.count
                nextState.lists = nextState.lists.map { list in
                    guard list.id == createdList.id else {
                        return list
                    }
                    return CachedListRecord(
                        id: createdList.id,
                        name: createdList.name,
                        color: createdList.color,
                        iconKey: createdList.iconKey ?? list.iconKey,
                        todoCount: todoCount,
                        updatedAtEpochMs: createdAt
                    )
                }
                nextState.pendingMutations.removeAll { $0.mutationId == mutationID }
                return nextState
            }
        } catch {
            // Keep the pending CREATE_LIST mutation so background sync can retry it.
        }
    }

    func updateList(listId: String, name: String, color: String? = nil, iconKey: String? = nil) async throws {
        let normalizedName = capitalizeFirstListLetter(name).trimmingCharacters(in: .whitespacesAndNewlines)
        guard !listId.isEmpty, !normalizedName.isEmpty else {
            return
        }
        let now = Date().epochMilliseconds
        if listId.hasPrefix(LOCAL_LIST_PREFIX) {
            _ = try await cacheManager.updateOfflineState { state in
                var nextState = state
                nextState.lists = state.lists.map { list in
                    guard list.id == listId else {
                        return list
                    }
                    return CachedListRecord(
                        id: list.id,
                        name: normalizedName,
                        color: color ?? list.color,
                        iconKey: iconKey ?? list.iconKey,
                        todoCount: list.todoCount,
                        updatedAtEpochMs: now
                    )
                }
                nextState.pendingMutations = state.pendingMutations.compactMap { mutation in
                    if mutation.kind == .updateList && mutation.targetId == listId {
                        return nil
                    }
                    guard mutation.kind == .createList, mutation.targetId == listId else {
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
            nextState.lists = state.lists.map { list in
                guard list.id == listId else { return list }
                return CachedListRecord(id: list.id, name: normalizedName, color: color ?? list.color, iconKey: iconKey ?? list.iconKey, todoCount: list.todoCount, updatedAtEpochMs: now)
            }
            nextState.pendingMutations.removeAll { $0.kind == .updateList && $0.targetId == listId }
            nextState.pendingMutations.append(
                PendingMutationRecord(
                    mutationId: UUID().uuidString,
                    kind: .updateList,
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

    private func buildLists(from state: OfflineSyncState) -> [ListSummary] {
        let todoCounts = Dictionary(grouping: state.todos.filter { !$0.completed }, by: { $0.listId })
            .mapValues(\.count)
        return state.lists
            .map { listFromCache($0, todoCountOverride: todoCounts[$0.id] ?? 0) }
            .sorted { $0.name.localizedCaseInsensitiveCompare($1.name) == .orderedAscending }
    }

    private func replaceLocalListID(_ state: OfflineSyncState, localListID: String, serverListID: String) -> OfflineSyncState {
        OfflineSyncState(
            lastSuccessfulSyncEpochMs: state.lastSuccessfulSyncEpochMs,
            lastSyncAttemptEpochMs: state.lastSyncAttemptEpochMs,
            todos: state.todos.map { todo in
                guard todo.listId == localListID else {
                    return todo
                }
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
            },
            completedItems: state.completedItems,
            lists: state.lists.map { list in
                guard list.id == localListID else {
                    return list
                }
                return CachedListRecord(
                    id: serverListID,
                    name: list.name,
                    color: list.color,
                    iconKey: list.iconKey,
                    todoCount: list.todoCount,
                    updatedAtEpochMs: list.updatedAtEpochMs
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
