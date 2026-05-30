import Foundation
import SwiftData

extension Notification.Name {
    static let offlineCacheDidChange = Notification.Name("tday.offline-cache.did-change")
}

actor AsyncLock {
    private var locked = false

    func withLock<T>(_ operation: () async throws -> T) async rethrows -> T {
        while locked {
            await Task.yield()
        }
        locked = true
        defer { locked = false }
        return try await operation()
    }
}

@MainActor
final class OfflineCacheManager {
    let modelContainer: ModelContainer
    private let modelContext: ModelContext
    private let secureStore: SecureStore
    private let syncLock = AsyncLock()
    private(set) var cacheDataVersion = 0
    private var lastState = OfflineSyncState()

    init(modelContainer: ModelContainer, secureStore: SecureStore) {
        self.modelContainer = modelContainer
        self.secureStore = secureStore
        modelContext = ModelContext(modelContainer)
        lastState = loadOfflineState()
    }

    func loadOfflineState() -> OfflineSyncState {
        let todos = (try? modelContext.fetch(FetchDescriptor<CachedTodoEntity>())) ?? []
        let floaters = (try? modelContext.fetch(FetchDescriptor<CachedFloaterEntity>())) ?? []
        let lists = (try? modelContext.fetch(FetchDescriptor<CachedListEntity>())) ?? []
        let floaterLists = (try? modelContext.fetch(FetchDescriptor<CachedFloaterListEntity>())) ?? []
        let completed = (try? modelContext.fetch(FetchDescriptor<CachedCompletedEntity>())) ?? []
        let completedFloaters = (try? modelContext.fetch(FetchDescriptor<CachedCompletedFloaterEntity>())) ?? []
        let mutations = (try? modelContext.fetch(FetchDescriptor<PendingMutationEntity>())) ?? []
        let metadata = (try? modelContext.fetch(FetchDescriptor<SyncMetadataEntity>()))?.first

        let listRecords = lists.map {
            CachedListRecord(
                id: $0.id,
                name: $0.name,
                color: $0.color,
                iconKey: $0.iconKey,
                todoCount: $0.todoCount,
                updatedAtEpochMs: $0.updatedAtEpochMs,
                createdAtEpochMs: $0.createdAtEpochMs ?? 0
            )
        }

        let floaterListRecords = floaterLists.map {
            CachedFloaterListRecord(
                id: $0.id,
                name: $0.name,
                color: $0.color,
                iconKey: $0.iconKey,
                todoCount: $0.todoCount,
                updatedAtEpochMs: $0.updatedAtEpochMs,
                createdAtEpochMs: $0.createdAtEpochMs ?? 0
            )
        }

        return OfflineSyncState(
            lastSuccessfulSyncEpochMs: metadata?.lastSuccessfulSyncEpochMs ?? 0,
            lastSyncAttemptEpochMs: metadata?.lastSyncAttemptEpochMs ?? 0,
            todos: todos.map {
                CachedTodoRecord(
                    id: $0.id,
                    canonicalId: $0.canonicalId,
                    title: $0.title,
                    description: $0.itemDescription,
                    priority: $0.priority,
                    dueEpochMs: $0.dueEpochMs,
                    rrule: $0.rrule,
                    instanceDateEpochMs: $0.instanceDateEpochMs,
                    pinned: $0.pinned,
                    completed: $0.completed,
                    listId: $0.listId,
                    updatedAtEpochMs: $0.updatedAtEpochMs
                )
            },
            floaters: floaters.map {
                CachedFloaterRecord(
                    id: $0.id,
                    canonicalId: $0.canonicalId,
                    title: $0.title,
                    description: $0.itemDescription,
                    priority: $0.priority,
                    pinned: $0.pinned,
                    completed: $0.completed,
                    listId: $0.listId,
                    updatedAtEpochMs: $0.updatedAtEpochMs
                )
            },
            completedItems: completed.map {
                CachedCompletedRecord(
                    id: $0.id,
                    originalTodoId: $0.originalTodoId,
                    title: $0.title,
                    description: $0.itemDescription,
                    priority: $0.priority,
                    dueEpochMs: $0.dueEpochMs,
                    completedAtEpochMs: $0.completedAtEpochMs,
                    rrule: $0.rrule,
                    instanceDateEpochMs: $0.instanceDateEpochMs,
                    listId: $0.listId,
                    listName: $0.listName,
                    listColor: $0.listColor
                )
            },
            completedFloaters: completedFloaters.map {
                CachedCompletedFloaterRecord(
                    id: $0.id,
                    originalFloaterId: $0.originalFloaterId,
                    title: $0.title,
                    description: $0.itemDescription,
                    priority: $0.priority,
                    completedAtEpochMs: $0.completedAtEpochMs,
                    listId: $0.listId,
                    listName: $0.listName,
                    listColor: $0.listColor
                )
            },
            lists: orderListsLikeWeb(listRecords),
            floaterLists: orderFloaterListsLikeWeb(floaterListRecords),
            pendingMutations: mutations.map {
                PendingMutationRecord(
                    mutationId: $0.mutationId,
                    kind: MutationKind(rawValue: $0.kindRawValue) ?? .updateTodo,
                    targetId: $0.targetId,
                    timestampEpochMs: $0.timestampEpochMs,
                    title: $0.title,
                    description: $0.itemDescription,
                    priority: $0.priority,
                    dueEpochMs: $0.dueEpochMs,
                    rrule: $0.rrule,
                    listId: $0.listId,
                    pinned: $0.pinned,
                    completed: $0.completed,
                    instanceDateEpochMs: $0.instanceDateEpochMs,
                    name: $0.name,
                    color: $0.color,
                    iconKey: $0.iconKey
                )
            },
            aiSummaryEnabled: metadata?.aiSummaryEnabled ?? true
        )
    }

    func saveOfflineState(_ state: OfflineSyncState) {
        let normalizedState: OfflineSyncState
        if secureStore.isLocalMode() {
            var localState = state
            localState.lastSuccessfulSyncEpochMs = 0
            localState.lastSyncAttemptEpochMs = 0
            localState.pendingMutations = []
            normalizedState = localState
        } else {
            normalizedState = state
        }

        if normalizedState == lastState {
            return
        }

        replaceAll(CachedTodoEntity.self)
        replaceAll(CachedFloaterEntity.self)
        replaceAll(CachedListEntity.self)
        replaceAll(CachedFloaterListEntity.self)
        replaceAll(CachedCompletedEntity.self)
        replaceAll(CachedCompletedFloaterEntity.self)
        replaceAll(PendingMutationEntity.self)
        replaceAll(SyncMetadataEntity.self)

        normalizedState.todos.forEach { modelContext.insert(CachedTodoEntity(from: $0)) }
        normalizedState.floaters.forEach { modelContext.insert(CachedFloaterEntity(from: $0)) }
        normalizedState.lists.forEach { modelContext.insert(CachedListEntity(from: $0)) }
        normalizedState.floaterLists.forEach { modelContext.insert(CachedFloaterListEntity(from: $0)) }
        normalizedState.completedItems.forEach { modelContext.insert(CachedCompletedEntity(from: $0)) }
        normalizedState.completedFloaters.forEach { modelContext.insert(CachedCompletedFloaterEntity(from: $0)) }
        normalizedState.pendingMutations.forEach { modelContext.insert(PendingMutationEntity(from: $0)) }
        modelContext.insert(
            SyncMetadataEntity(
                lastSuccessfulSyncEpochMs: normalizedState.lastSuccessfulSyncEpochMs,
                lastSyncAttemptEpochMs: normalizedState.lastSyncAttemptEpochMs,
                aiSummaryEnabled: normalizedState.aiSummaryEnabled
            )
        )

        try? modelContext.save()
        lastState = normalizedState
        TodayTasksWidgetSnapshotStore.saveTodayTasks(from: normalizedState)
        cacheDataVersion += 1
        NotificationCenter.default.post(name: .offlineCacheDidChange, object: nil)
    }

    @discardableResult
    func updateOfflineState(_ transform: (OfflineSyncState) -> OfflineSyncState) -> OfflineSyncState {
        let nextState = transform(loadOfflineState())
        saveOfflineState(nextState)
        return nextState
    }

    func hasCachedData() -> Bool {
        let state = loadOfflineState()
        return !state.todos.isEmpty ||
            !state.floaters.isEmpty ||
            !state.completedItems.isEmpty ||
            !state.completedFloaters.isEmpty ||
            !state.lists.isEmpty ||
            !state.floaterLists.isEmpty ||
            !state.pendingMutations.isEmpty
    }

    func clearAllLocalData() {
        saveOfflineState(OfflineSyncState())
    }

    func clearSessionOnly() {
        saveOfflineState(OfflineSyncState())
    }

    func withSyncLock<T>(_ operation: () async throws -> T) async rethrows -> T {
        try await syncLock.withLock(operation)
    }

    func loadOfflineState() async throws -> OfflineSyncState {
        let load: @MainActor () -> OfflineSyncState = self.loadOfflineState
        return load()
    }

    func saveOfflineState(_ state: OfflineSyncState) async throws {
        let save: @MainActor (OfflineSyncState) -> Void = self.saveOfflineState
        save(state)
    }

    @discardableResult
    func updateOfflineState(_ transform: @escaping (OfflineSyncState) -> OfflineSyncState) async throws -> OfflineSyncState {
        let update: @MainActor ((OfflineSyncState) -> OfflineSyncState) -> OfflineSyncState = self.updateOfflineState
        return update(transform)
    }

    func hasCachedData() async throws -> Bool {
        let hasData: @MainActor () -> Bool = self.hasCachedData
        return hasData()
    }

    func clearAllLocalData() async throws {
        let clear: @MainActor () -> Void = self.clearAllLocalData
        clear()
    }

    func clearSessionOnly() async throws {
        let clear: @MainActor () -> Void = self.clearSessionOnly
        clear()
    }

    private func replaceAll<T: PersistentModel>(_ modelType: T.Type) {
        let descriptor = FetchDescriptor<T>()
        let existing = (try? modelContext.fetch(descriptor)) ?? []
        existing.forEach { modelContext.delete($0) }
    }
}
