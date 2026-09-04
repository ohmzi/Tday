import SwiftData
import XCTest

#if SWIFT_PACKAGE
@testable import TdayCore
#else
@testable import Tday
#endif

/// Exercises `OfflineCacheManager.saveOfflineState` against a real (in-memory) SwiftData store,
/// covering the three behaviors the sync-freeze fix depends on:
///  - a record dropped from the next state is actually deleted from the cache, not just
///    left behind (the diff/upsert rewrite must still delete, not only add/update);
///  - a resave whose content is identical to what's already persisted skips the fan-out
///    notification (the no-op short-circuit correctly recognizes an unchanged sync even when a
///    bookkeeping timestamp, like a sync-attempt stamp, differs);
///  - `notify: false` saves are silent, and a single explicit `notifyCacheChanged()` afterward
///    fires exactly once — the coalescing primitive `SyncManager.syncLocalCache` builds on so a
///    multi-write sync cycle notifies observers once, not once per internal write.
@MainActor
final class OfflineCacheManagerPersistenceTests: XCTestCase {
    private var secureStore: SecureStore!
    private var defaults: UserDefaults!
    private var defaultsSuiteName: String!
    private var modelContainer: ModelContainer!
    private var cacheManager: OfflineCacheManager!
    private var notificationObserver: NSObjectProtocol?
    private var notificationCount = 0

    override func setUp() {
        super.setUp()
        defaultsSuiteName = "com.ohmz.tday.tests.\(UUID().uuidString)"
        defaults = UserDefaults(suiteName: defaultsSuiteName)!
        defaults.removePersistentDomain(forName: defaultsSuiteName)
        secureStore = SecureStore(
            service: "com.ohmz.tday.tests.secure-store.\(UUID().uuidString)",
            defaults: defaults
        )
        // Server mode: local mode zeroes timestamps/pendingMutations on every save, which would
        // mask the timestamp-only no-op case these tests are specifically about.
        secureStore.setAppDataMode(.server)

        modelContainer = try! ModelContainer(
            for: CachedTodoEntity.self,
            CachedFloaterEntity.self,
            CachedListEntity.self,
            CachedFloaterListEntity.self,
            CachedCompletedEntity.self,
            CachedCompletedFloaterEntity.self,
            PendingMutationEntity.self,
            SyncMetadataEntity.self,
            configurations: ModelConfiguration(isStoredInMemoryOnly: true)
        )
        cacheManager = OfflineCacheManager(modelContainer: modelContainer, secureStore: secureStore)

        notificationCount = 0
        notificationObserver = NotificationCenter.default.addObserver(
            forName: .offlineCacheDidChange,
            object: nil,
            queue: nil
        ) { [weak self] _ in
            self?.notificationCount += 1
        }
    }

    override func tearDown() {
        if let notificationObserver {
            NotificationCenter.default.removeObserver(notificationObserver)
        }
        notificationObserver = nil
        cacheManager = nil
        modelContainer = nil
        secureStore.clearAllUserValues()
        defaults.removePersistentDomain(forName: defaultsSuiteName)
        secureStore = nil
        defaults = nil
        defaultsSuiteName = nil
        super.tearDown()
    }

    func testRecordDroppedFromStateIsDeletedFromTheCache() {
        let kept = todoRecord(id: "todo-keep")
        let removed = todoRecord(id: "todo-remove")
        cacheManager.saveOfflineState(OfflineSyncState(todos: [kept, removed]))
        XCTAssertEqual(Set(cacheManager.loadOfflineState().todos.map(\.id)), ["todo-keep", "todo-remove"])

        cacheManager.saveOfflineState(OfflineSyncState(todos: [kept]))

        XCTAssertEqual(cacheManager.loadOfflineState().todos.map(\.id), ["todo-keep"])
    }

    func testChangedRecordIsReflectedAfterResave() {
        cacheManager.saveOfflineState(OfflineSyncState(todos: [todoRecord(id: "todo-1", title: "Original")]))

        cacheManager.saveOfflineState(OfflineSyncState(todos: [todoRecord(id: "todo-1", title: "Changed")]))

        XCTAssertEqual(cacheManager.loadOfflineState().todos.first?.title, "Changed")
    }

    /// Mirrors `SyncManager.syncLocalCache`'s pre-network-call stamp: same content, only the
    /// attempt timestamp moved forward. Must not trigger the full rewrite or the notification.
    func testResaveWithOnlyATimestampChangeSkipsTheNotification() {
        let todo = todoRecord(id: "todo-1")
        cacheManager.saveOfflineState(OfflineSyncState(lastSyncAttemptEpochMs: 100, todos: [todo]))
        notificationCount = 0

        cacheManager.saveOfflineState(OfflineSyncState(lastSyncAttemptEpochMs: 200, todos: [todo]))

        XCTAssertEqual(notificationCount, 0)
        // The timestamp itself is still recorded, just via the lightweight metadata-only path.
        XCTAssertEqual(cacheManager.loadOfflineState().lastSyncAttemptEpochMs, 200)
    }

    /// Sibling to the pure-diff `testReorderingWithoutContentChangeProducesNoWork`: a freshly
    /// loaded state's array order need not match the order it was last saved in.
    func testResaveWithReorderedButOtherwiseIdenticalRecordsSkipsTheNotification() {
        let a = todoRecord(id: "todo-a")
        let b = todoRecord(id: "todo-b")
        cacheManager.saveOfflineState(OfflineSyncState(todos: [a, b]))
        notificationCount = 0

        cacheManager.saveOfflineState(OfflineSyncState(todos: [b, a]))

        XCTAssertEqual(notificationCount, 0)
    }

    func testDefaultNotifyStillFiresPerCallForSingleMutationCallers() {
        cacheManager.saveOfflineState(OfflineSyncState(todos: [todoRecord(id: "todo-1")]))
        XCTAssertEqual(notificationCount, 1)

        cacheManager.saveOfflineState(OfflineSyncState(todos: [todoRecord(id: "todo-1"), todoRecord(id: "todo-2")]))
        XCTAssertEqual(notificationCount, 2)
    }

    /// The coalescing mechanism `SyncManager.syncLocalCache` relies on: several `notify: false`
    /// saves stay silent, and one explicit `notifyCacheChanged()` afterward fires exactly once
    /// — not zero (the changes must still reach observers) and not once per save.
    func testSuppressedSavesFollowedByOneExplicitNotifyFireExactlyOnce() {
        let saved = cacheManager.saveOfflineState(OfflineSyncState(todos: [todoRecord(id: "todo-1")]), notify: false)
        let savedAgain = cacheManager.saveOfflineState(
            OfflineSyncState(todos: [todoRecord(id: "todo-1"), todoRecord(id: "todo-2")]),
            notify: false
        )
        XCTAssertTrue(saved)
        XCTAssertTrue(savedAgain)
        XCTAssertEqual(notificationCount, 0)

        cacheManager.notifyCacheChanged()

        XCTAssertEqual(notificationCount, 1)
    }

    /// If every internal save in a sync cycle turned out to be a genuine no-op, the cycle
    /// should not notify at all — this is what actually saves the 5 observers from reloading on
    /// a passive resync that found nothing new.
    func testNoNotifyWhenNoSuppressedSaveActuallyChangedAnything() {
        cacheManager.saveOfflineState(OfflineSyncState(lastSyncAttemptEpochMs: 1, todos: [todoRecord(id: "todo-1")]))
        notificationCount = 0

        let changed = cacheManager.saveOfflineState(
            OfflineSyncState(lastSyncAttemptEpochMs: 2, todos: [todoRecord(id: "todo-1")]),
            notify: false
        )

        XCTAssertFalse(changed)
        XCTAssertEqual(notificationCount, 0)
    }
}

private func todoRecord(id: String, title: String = "Task", updatedAtEpochMs: Int64 = 0) -> CachedTodoRecord {
    CachedTodoRecord(
        id: id,
        canonicalId: id,
        title: title,
        description: nil,
        priority: "Low",
        dueEpochMs: nil,
        rrule: nil,
        instanceDateEpochMs: nil,
        pinned: false,
        completed: false,
        listId: nil,
        updatedAtEpochMs: updatedAtEpochMs
    )
}
