import XCTest

#if SWIFT_PACKAGE
@testable import TdayCore
#else
@testable import Tday
#endif

/// `diffCachedRecords` is the pure algorithm behind `OfflineCacheManager.saveOfflineState`'s
/// upsert: it decides which cached records actually need writing (new-or-changed) and which
/// need deleting (present before, gone now), without touching SwiftData. These tests exercise
/// that decision directly; `OfflineCacheManagerPersistenceTests` separately proves the same
/// deletion case survives an actual round trip through the SwiftData store.
final class CachedRecordDiffTests: XCTestCase {
    func testNewRecordIsUpserted() {
        let diff = diffCachedRecords(old: [], new: [todo("a")])

        XCTAssertEqual(diff.upserts, [todo("a")])
        XCTAssertTrue(diff.deletedIDs.isEmpty)
        XCTAssertFalse(diff.isEmpty)
    }

    func testUnchangedRecordProducesNoWork() {
        let record = todo("a", updatedAtEpochMs: 5)

        let diff = diffCachedRecords(old: [record], new: [record])

        XCTAssertTrue(diff.isEmpty)
        XCTAssertTrue(diff.upserts.isEmpty)
        XCTAssertTrue(diff.deletedIDs.isEmpty)
    }

    func testChangedRecordIsUpserted() {
        let old = todo("a", updatedAtEpochMs: 1)
        let updated = todo("a", updatedAtEpochMs: 2)

        let diff = diffCachedRecords(old: [old], new: [updated])

        XCTAssertEqual(diff.upserts, [updated])
        XCTAssertTrue(diff.deletedIDs.isEmpty)
    }

    /// The correctness-critical case: a record present in the last-persisted state but absent
    /// from the next state must be reported as a deletion, not silently dropped or ignored — an
    /// upsert that only ever adds/updates and never deletes would leave stale rows behind
    /// forever (e.g. a task remote-deleted on another device would keep showing up locally).
    func testRecordMissingFromNewStateIsReportedAsDeleted() {
        let kept = todo("keep")
        let removed = todo("remove")

        let diff = diffCachedRecords(old: [kept, removed], new: [kept])

        XCTAssertTrue(diff.upserts.isEmpty)
        XCTAssertEqual(diff.deletedIDs, ["remove"])
    }

    func testEveryOldRecordMissingIsDeletedWhenNewStateIsEmpty() {
        let diff = diffCachedRecords(old: [todo("a"), todo("b"), todo("c")], new: [])

        XCTAssertTrue(diff.upserts.isEmpty)
        XCTAssertEqual(diff.deletedIDs, ["a", "b", "c"])
    }

    func testAdditionsChangesAndDeletionsAreAllReportedTogether() {
        let unchanged = todo("unchanged", updatedAtEpochMs: 1)
        let changedOld = todo("changed", updatedAtEpochMs: 1)
        let changedNew = todo("changed", updatedAtEpochMs: 2)
        let removed = todo("removed")
        let added = todo("added")

        let diff = diffCachedRecords(
            old: [unchanged, changedOld, removed],
            new: [unchanged, changedNew, added]
        )

        XCTAssertEqual(Set(diff.upserts.map(\.id)), ["changed", "added"])
        XCTAssertEqual(diff.deletedIDs, ["removed"])
    }

    /// Ordering must not matter — `saveOfflineState` diffs a freshly-loaded (DB fetch order)
    /// state against an in-memory one built by sorting/merging, and those orders are not
    /// guaranteed to match even when the content is identical.
    func testReorderingWithoutContentChangeProducesNoWork() {
        let a = todo("a")
        let b = todo("b")
        let c = todo("c")

        let diff = diffCachedRecords(old: [a, b, c], new: [c, a, b])

        XCTAssertTrue(diff.isEmpty)
    }
}

private func todo(_ id: String, updatedAtEpochMs: Int64 = 0) -> CachedTodoRecord {
    CachedTodoRecord(
        id: id,
        canonicalId: id,
        title: "Task \(id)",
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
