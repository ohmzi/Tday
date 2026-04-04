import Foundation

@MainActor
final class ListRepository {
    private let cacheManager: OfflineCacheManager
    private let syncManager: SyncManager

    init(cacheManager: OfflineCacheManager, syncManager: SyncManager) {
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
        let normalizedName = name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !normalizedName.isEmpty else {
            return
        }
        let now = Date().epochMilliseconds
        let localListID = LOCAL_LIST_PREFIX + UUID().uuidString.lowercased()
        cacheManager.updateOfflineState { state in
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
                    mutationId: UUID().uuidString,
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
        let result = await syncManager.syncCachedData(force: true, replayPendingMutations: true)
        if case let .failure(error) = result, isLikelyUnrecoverableMutationError(error) {
            throw error
        }
    }

    func updateList(listId: String, name: String, color: String? = nil, iconKey: String? = nil) async throws {
        let normalizedName = name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !listId.isEmpty, !normalizedName.isEmpty else {
            return
        }
        let now = Date().epochMilliseconds
        cacheManager.updateOfflineState { state in
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
}
