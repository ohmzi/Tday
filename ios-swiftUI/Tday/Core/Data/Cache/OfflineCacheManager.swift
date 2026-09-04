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

/// Which records in a cached collection need writing (new-or-changed) and which previously
/// cached ids are no longer present in the next state (need deleting). Pure data, computed
/// with no SwiftData/IO involved — see `diffCachedRecords` below.
struct CachedRecordDiff<Record> {
    let upserts: [Record]
    let deletedIDs: Set<String>

    var isEmpty: Bool {
        upserts.isEmpty && deletedIDs.isEmpty
    }
}

/// Pure diff between what was persisted last (`old`, mirrored by `OfflineCacheManager.lastState`)
/// and what's about to be persisted (`new`): which records are new-or-changed and therefore need
/// writing, and which previously-known ids have disappeared and therefore need deleting.
/// Dictionary/id-keyed, so it is unaffected by array ordering on either side. No SwiftData
/// involved, so this — and by extension `OfflineCacheManager.saveOfflineState`'s decision to
/// skip or run a persistence pass — is covered directly by unit tests without a `ModelContainer`.
func diffCachedRecords<Record: Identifiable & Equatable>(
    old: [Record],
    new: [Record]
) -> CachedRecordDiff<Record> where Record.ID == String {
    let oldByID = Dictionary(old.map { ($0.id, $0) }, uniquingKeysWith: { first, _ in first })
    let newIDs = Set(new.map(\.id))
    let deletedIDs = Set(oldByID.keys).subtracting(newIDs)
    let upserts = new.filter { oldByID[$0.id] != $0 }
    return CachedRecordDiff(upserts: upserts, deletedIDs: deletedIDs)
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

    /// Cheap, already-in-memory read of just the default-home-screen preference, for callers
    /// (e.g. `AppRootView.init`) that need this one field synchronously without repeating
    /// `loadOfflineState()`'s full fetch across every cached SwiftData entity type. `lastState`
    /// is populated once in `init` and kept current by every `saveOfflineState` write below, so
    /// it always mirrors what a fresh `loadOfflineState()` would return for this field.
    var defaultHomeScreenSnapshot: String {
        lastState.defaultHomeScreen
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
                createdAtEpochMs: $0.createdAtEpochMs ?? 0,
                myRole: $0.myRole,
                isShared: $0.isShared,
                memberCount: $0.memberCount,
                ownerUsername: $0.ownerUsername
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
                createdAtEpochMs: $0.createdAtEpochMs ?? 0,
                myRole: $0.myRole,
                isShared: $0.isShared,
                memberCount: $0.memberCount,
                ownerUsername: $0.ownerUsername
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
            aiSummaryEnabled: metadata?.aiSummaryEnabled ?? true,
            defaultHomeScreen: metadata?.defaultHomeScreen ?? "scheduled"
        )
    }

    /// Persists `state`, touching only the rows that actually changed since the last save
    /// instead of rewriting every cached entity. Returns whether anything was actually written
    /// (`false` for a genuine no-op — same content as `lastState`, save perhaps for bookkeeping
    /// timestamps, which are still recorded but don't count as a "change").
    ///
    /// `notify` controls whether `.offlineCacheDidChange` is posted for this specific call. Pass
    /// `false` when the caller is about to make several `saveOfflineState` calls back-to-back as
    /// part of one logical operation (see `SyncManager.syncLocalCache`) and will post the
    /// notification itself once, via `notifyCacheChanged()`, after the last one — every other
    /// call site (single mutations) should keep the default so its one save still notifies.
    @discardableResult
    func saveOfflineState(_ state: OfflineSyncState, notify: Bool = true) -> Bool {
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

        let todoDiff = diffCachedRecords(old: lastState.todos, new: normalizedState.todos)
        let floaterDiff = diffCachedRecords(old: lastState.floaters, new: normalizedState.floaters)
        let listDiff = diffCachedRecords(old: lastState.lists, new: normalizedState.lists)
        let floaterListDiff = diffCachedRecords(old: lastState.floaterLists, new: normalizedState.floaterLists)
        let completedDiff = diffCachedRecords(old: lastState.completedItems, new: normalizedState.completedItems)
        let completedFloaterDiff = diffCachedRecords(old: lastState.completedFloaters, new: normalizedState.completedFloaters)
        let mutationDiff = diffCachedRecords(old: lastState.pendingMutations, new: normalizedState.pendingMutations)
        let metadataContentChanged = normalizedState.aiSummaryEnabled != lastState.aiSummaryEnabled ||
            normalizedState.defaultHomeScreen != lastState.defaultHomeScreen
        let timestampsChanged = normalizedState.lastSyncAttemptEpochMs != lastState.lastSyncAttemptEpochMs ||
            normalizedState.lastSuccessfulSyncEpochMs != lastState.lastSuccessfulSyncEpochMs

        let contentChanged = metadataContentChanged ||
            !todoDiff.isEmpty ||
            !floaterDiff.isEmpty ||
            !listDiff.isEmpty ||
            !floaterListDiff.isEmpty ||
            !completedDiff.isEmpty ||
            !completedFloaterDiff.isEmpty ||
            !mutationDiff.isEmpty

        guard contentChanged else {
            // Genuinely nothing changed except perhaps bookkeeping timestamps (e.g. the
            // pre-network-call sync-attempt stamp in `SyncManager.syncLocalCache`, or a passive
            // resync that found the remote already matching the cache). Record the timestamp
            // move alone — a single-row metadata write — and skip the entity rewrite, the widget
            // snapshot re-serialization, and the fan-out notification entirely, since nothing
            // observer-visible changed.
            if timestampsChanged {
                upsertMetadata(normalizedState)
                try? modelContext.save()
                lastState.lastSuccessfulSyncEpochMs = normalizedState.lastSuccessfulSyncEpochMs
                lastState.lastSyncAttemptEpochMs = normalizedState.lastSyncAttemptEpochMs
            }
            return false
        }

        apply(
            todoDiff,
            fetchExisting: { ids in
                (try? modelContext.fetch(FetchDescriptor<CachedTodoEntity>(predicate: #Predicate { ids.contains($0.id) }))) ?? []
            },
            makeEntity: CachedTodoEntity.init(from:)
        )
        apply(
            floaterDiff,
            fetchExisting: { ids in
                (try? modelContext.fetch(FetchDescriptor<CachedFloaterEntity>(predicate: #Predicate { ids.contains($0.id) }))) ?? []
            },
            makeEntity: CachedFloaterEntity.init(from:)
        )
        apply(
            listDiff,
            fetchExisting: { ids in
                (try? modelContext.fetch(FetchDescriptor<CachedListEntity>(predicate: #Predicate { ids.contains($0.id) }))) ?? []
            },
            makeEntity: CachedListEntity.init(from:)
        )
        apply(
            floaterListDiff,
            fetchExisting: { ids in
                (try? modelContext.fetch(FetchDescriptor<CachedFloaterListEntity>(predicate: #Predicate { ids.contains($0.id) }))) ?? []
            },
            makeEntity: CachedFloaterListEntity.init(from:)
        )
        apply(
            completedDiff,
            fetchExisting: { ids in
                (try? modelContext.fetch(FetchDescriptor<CachedCompletedEntity>(predicate: #Predicate { ids.contains($0.id) }))) ?? []
            },
            makeEntity: CachedCompletedEntity.init(from:)
        )
        apply(
            completedFloaterDiff,
            fetchExisting: { ids in
                (try? modelContext.fetch(FetchDescriptor<CachedCompletedFloaterEntity>(predicate: #Predicate { ids.contains($0.id) }))) ?? []
            },
            makeEntity: CachedCompletedFloaterEntity.init(from:)
        )
        apply(
            mutationDiff,
            fetchExisting: { ids in
                (try? modelContext.fetch(FetchDescriptor<PendingMutationEntity>(predicate: #Predicate { ids.contains($0.mutationId) }))) ?? []
            },
            makeEntity: PendingMutationEntity.init(from:)
        )
        upsertMetadata(normalizedState)

        try? modelContext.save()
        lastState = normalizedState
        TodayTasksWidgetSnapshotStore.saveTodayTasks(from: normalizedState)
        FloaterTasksWidgetSnapshotStore.saveFloaterTasks(from: normalizedState)
        cacheDataVersion += 1
        if notify {
            NotificationCenter.default.post(name: .offlineCacheDidChange, object: nil)
        }
        return true
    }

    /// Posts the `.offlineCacheDidChange` fan-out once. Pair with one or more
    /// `saveOfflineState(_:notify: false)` calls so a multi-write operation (a full sync cycle,
    /// say) notifies every observer exactly once, however many internal saves it took.
    func notifyCacheChanged() {
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

    @discardableResult
    func saveOfflineState(_ state: OfflineSyncState, notify: Bool = true) async throws -> Bool {
        let save: @MainActor (OfflineSyncState, Bool) -> Bool = self.saveOfflineState
        return save(state, notify)
    }

    func notifyCacheChanged() async throws {
        let notify: @MainActor () -> Void = self.notifyCacheChanged
        notify()
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

    /// Applies one entity type's diff: deletes rows for ids that are gone or about to be
    /// replaced, then inserts the new-or-changed records. `fetchExisting` is supplied per entity
    /// type by the caller (its `#Predicate` has to reference that type's concrete id property —
    /// `id` for every cached-record entity except `PendingMutationEntity`, which uses
    /// `mutationId`) so this stays a single generic implementation despite that. Entities whose
    /// record didn't change are never fetched, deleted, or reinserted.
    private func apply<Record: Identifiable, Entity: PersistentModel>(
        _ diff: CachedRecordDiff<Record>,
        fetchExisting: (Set<String>) -> [Entity],
        makeEntity: (Record) -> Entity
    ) where Record.ID == String {
        guard !diff.isEmpty else {
            return
        }
        // Changed records must drop their stale row before the fresh one is inserted (both
        // share the same unique id); pure deletions and pure additions only need one side of
        // that, but including every upsert id here too is a harmless no-op fetch for additions.
        let idsToClear = diff.deletedIDs.union(diff.upserts.map(\.id))
        if !idsToClear.isEmpty {
            fetchExisting(idsToClear).forEach { modelContext.delete($0) }
        }
        diff.upserts.forEach { modelContext.insert(makeEntity($0)) }
    }

    private func upsertMetadata(_ state: OfflineSyncState) {
        let descriptor = FetchDescriptor<SyncMetadataEntity>()
        if let existing = (try? modelContext.fetch(descriptor))?.first {
            existing.lastSuccessfulSyncEpochMs = state.lastSuccessfulSyncEpochMs
            existing.lastSyncAttemptEpochMs = state.lastSyncAttemptEpochMs
            existing.aiSummaryEnabled = state.aiSummaryEnabled
            existing.defaultHomeScreen = state.defaultHomeScreen
        } else {
            modelContext.insert(
                SyncMetadataEntity(
                    lastSuccessfulSyncEpochMs: state.lastSuccessfulSyncEpochMs,
                    lastSyncAttemptEpochMs: state.lastSyncAttemptEpochMs,
                    aiSummaryEnabled: state.aiSummaryEnabled,
                    defaultHomeScreen: state.defaultHomeScreen
                )
            )
        }
    }
}
