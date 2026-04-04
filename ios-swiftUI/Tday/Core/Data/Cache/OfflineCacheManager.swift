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
    private let syncLock = AsyncLock()
    private(set) var cacheDataVersion = 0
    private var lastState = OfflineSyncState()

    init(modelContainer: ModelContainer) {
        self.modelContainer = modelContainer
        modelContext = ModelContext(modelContainer)
        lastState = loadOfflineState()
    }

    func loadOfflineState() -> OfflineSyncState {
        let todos = (try? modelContext.fetch(FetchDescriptor<CachedTodoEntity>())) ?? []
        let lists = (try? modelContext.fetch(FetchDescriptor<CachedListEntity>())) ?? []
        let completed = (try? modelContext.fetch(FetchDescriptor<CachedCompletedEntity>())) ?? []
        let mutations = (try? modelContext.fetch(FetchDescriptor<PendingMutationEntity>())) ?? []
        let metadata = (try? modelContext.fetch(FetchDescriptor<SyncMetadataEntity>()))?.first

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
                    listName: $0.listName,
                    listColor: $0.listColor
                )
            },
            lists: lists.map {
                CachedListRecord(
                    id: $0.id,
                    name: $0.name,
                    color: $0.color,
                    iconKey: $0.iconKey,
                    todoCount: $0.todoCount,
                    updatedAtEpochMs: $0.updatedAtEpochMs
                )
            },
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
        if state == lastState {
            return
        }

        replaceAll(CachedTodoEntity.self)
        replaceAll(CachedListEntity.self)
        replaceAll(CachedCompletedEntity.self)
        replaceAll(PendingMutationEntity.self)
        replaceAll(SyncMetadataEntity.self)

        state.todos.forEach { modelContext.insert(CachedTodoEntity(from: $0)) }
        state.lists.forEach { modelContext.insert(CachedListEntity(from: $0)) }
        state.completedItems.forEach { modelContext.insert(CachedCompletedEntity(from: $0)) }
        state.pendingMutations.forEach { modelContext.insert(PendingMutationEntity(from: $0)) }
        modelContext.insert(
            SyncMetadataEntity(
                lastSuccessfulSyncEpochMs: state.lastSuccessfulSyncEpochMs,
                lastSyncAttemptEpochMs: state.lastSyncAttemptEpochMs,
                aiSummaryEnabled: state.aiSummaryEnabled
            )
        )

        try? modelContext.save()
        lastState = state
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
        return !state.todos.isEmpty || !state.completedItems.isEmpty || !state.lists.isEmpty || !state.pendingMutations.isEmpty
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
        loadOfflineState()
    }

    func saveOfflineState(_ state: OfflineSyncState) async throws {
        saveOfflineState(state)
    }

    @discardableResult
    func updateOfflineState(_ transform: @escaping (OfflineSyncState) -> OfflineSyncState) async throws -> OfflineSyncState {
        updateOfflineState(transform)
    }

    func hasCachedData() async throws -> Bool {
        hasCachedData()
    }

    func clearAllLocalData() async throws {
        clearAllLocalData()
    }

    func clearSessionOnly() async throws {
        clearSessionOnly()
    }

    private func replaceAll<T: PersistentModel>(_ modelType: T.Type) {
        let descriptor = FetchDescriptor<T>()
        let existing = (try? modelContext.fetch(descriptor)) ?? []
        existing.forEach { modelContext.delete($0) }
    }
}
