import Foundation
import SwiftData

extension Notification.Name {
    static let offlineCacheDidChange = Notification.Name("tday.offline-cache.did-change")
}

/// Serialises sync so two callers can never interleave a load / transform / save.
///
/// This used to be `while locked { await Task.yield() }`. A yield loop does not
/// really suspend: the waiter stays runnable and keeps a cooperative-pool thread
/// hot for as long as the lock is held — and the lock is held across a sequential
/// replay of up to 100 pending mutations, i.e. a long time. With the pool sized to
/// the core count and the sync's own concurrent requests already in flight, the
/// spinners were competing with the very work they were waiting on. Waiters now
/// park on a continuation and cost nothing while queued; the lock is handed
/// straight to the next one in FIFO order.
///
/// Deliberately not cancellable, which matches the spin it replaces: a cancelled
/// waiter still takes its turn rather than abandoning a half-written cache.
actor AsyncLock {
    private var locked = false
    private var waiters: [CheckedContinuation<Void, Never>] = []

    /// How many callers are currently parked waiting for the lock. Additive and
    /// read-only — no caller of `withLock` is affected by it. It exists so tests
    /// can wait until a waiter has definitely queued before enqueueing the next
    /// one, which is what makes the FIFO assertion deterministic instead of a
    /// race against the scheduler.
    var waiterCount: Int {
        waiters.count
    }

    func withLock<T>(_ operation: () async throws -> T) async rethrows -> T {
        await acquire()
        defer { release() }
        return try await operation()
    }

    private func acquire() async {
        guard locked else {
            locked = true
            return
        }
        await withCheckedContinuation { continuation in
            waiters.append(continuation)
        }
    }

    private func release() {
        if waiters.isEmpty {
            locked = false
        } else {
            // Ownership passes straight to the next waiter, so `locked` stays
            // true. Clearing it first and then resuming would open a window in
            // which a brand-new caller sees an unlocked lock and proceeds
            // alongside the waiter that was just handed ownership.
            waiters.removeFirst().resume()
        }
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

    /// The same cheap read, for the stamp that answers "has this install ever heard back from its
    /// workspace at all" — see `feedFirstAnswerLanded(in:)`, which is its only caller and which is
    /// asked from inside the synchronous hydrates of three feed view models. Those hydrates run on
    /// every cache write, and `TodoListViewModel.hydrateFromCache` already records what a second
    /// `loadOfflineState()` would cost there: every write wakes every live feed, so a hydrate that
    /// re-reads the whole cache doubles the main-actor cost of every sync. This is one `Int64` that
    /// is already in memory.
    ///
    /// `lastState` mirrors it on both write paths, which is what makes the mirror safe to trust for
    /// this field in particular: the content-changed path assigns the whole normalized state, and
    /// the path that finds nothing observer-visible changed still copies this stamp across before
    /// returning — a first sync against an EMPTY account is exactly that second case, and it is the
    /// one case this accessor exists for.
    var lastSuccessfulSyncEpochMsSnapshot: Int64 {
        lastState.lastSuccessfulSyncEpochMs
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
                ownerUsername: $0.ownerUsername,
                defaultPriority: $0.defaultPriority
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
                ownerUsername: $0.ownerUsername,
                reusable: $0.reusable,
                defaultPriority: $0.defaultPriority
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
                    iconKey: $0.iconKey,
                    defaultPriority: $0.defaultPriority,
                    defaultPriorityChanged: $0.defaultPriorityChanged,
                    staged: $0.staged
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
                // The one timestamp move that IS observer-visible, and the only one: zero to
                // non-zero on the successful-sync stamp is a workspace answering for the very
                // first time. `feedFirstAnswerLanded(in:)` reads exactly this field, and the three
                // feed view models re-read it only inside their hydrates — which off the refresh
                // path run on `.offlineCacheDidChange` and nowhere else.
                //
                // Without this post, a first successful sync against an EMPTY account is silent:
                // no row moved, so `contentChanged` is false, so this branch returns false, so
                // `SyncManager` skips its own `notifyCacheChanged()`. The stamp lands and nothing
                // asks again. The screens keep drawing the row skeleton `feedAnswer` gives an
                // unanswered feed — three grey bars over a workspace that has now been counted and
                // found empty — until a pull or a re-navigation happens to re-hydrate. Reachable
                // on a fresh install whose bootstrap sync failed (offline launch) and whose later
                // background sync succeeded. Android hit the same wall and answered it with
                // `FirstAnswerSignal.version`; this is the same signal on the mechanism this
                // client already has.
                let firstAnswerJustLanded = lastState.lastSuccessfulSyncEpochMs == 0 &&
                    normalizedState.lastSuccessfulSyncEpochMs > 0
                upsertMetadata(normalizedState)
                try? modelContext.save()
                lastState.lastSuccessfulSyncEpochMs = normalizedState.lastSuccessfulSyncEpochMs
                lastState.lastSyncAttemptEpochMs = normalizedState.lastSyncAttemptEpochMs
                // Deliberately NOT behind `notify`. That flag is a batching contract — "the
                // caller will post once after its last save" — and the caller only keeps it when
                // something changed, which by definition is not this case. A once-per-install
                // transition is not a fan-out worth batching.
                if firstAnswerJustLanded {
                    NotificationCenter.default.post(name: .offlineCacheDidChange, object: nil)
                }
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
