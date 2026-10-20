import XCTest

#if SWIFT_PACKAGE
@testable import TdayCore
#else
@testable import Tday
#endif

final class CompletedSyncMergeTests: XCTestCase {
    func testDropsLocalCompletedRecordsMissingFromRemoteWithoutPendingMutation() {
        let local = completedRecord(id: "completed-local", originalTodoId: "todo-1", completedAtEpochMs: 2_000)

        let merged = mergeCompletedRecordsWithPendingOverrides(
            localRecords: [local],
            remoteRecords: [],
            pendingTodoTargets: []
        )

        XCTAssertTrue(merged.isEmpty)
    }

    func testRemoteCompletedRecordsWinWhenTodoHasNoPendingMutation() {
        let local = completedRecord(id: "completed-local", originalTodoId: "todo-1", completedAtEpochMs: 2_000)
        let remote = completedRecord(id: "completed-server", originalTodoId: "todo-1", completedAtEpochMs: 1_000)

        let merged = mergeCompletedRecordsWithPendingOverrides(
            localRecords: [local],
            remoteRecords: [remote],
            pendingTodoTargets: []
        )

        XCTAssertEqual(merged, [remote])
    }

    func testLocalCompletedRecordsOverrideRemoteForPendingTodoMutation() {
        let local = completedRecord(id: "local-completed-1", originalTodoId: "todo-1", completedAtEpochMs: 2_000)
        let remote = completedRecord(id: "completed-server", originalTodoId: "todo-1", completedAtEpochMs: 1_000)

        let merged = mergeCompletedRecordsWithPendingOverrides(
            localRecords: [local],
            remoteRecords: [remote],
            pendingTodoTargets: ["todo-1"]
        )

        XCTAssertEqual(merged, [local])
    }

    func testPendingDeletedListRemovesRemoteCompletedRecordsForThatList() {
        let kept = completedRecord(id: "kept", originalTodoId: "todo-1", completedAtEpochMs: 2_000, listId: "list-kept")
        let deleted = completedRecord(id: "deleted", originalTodoId: "todo-2", completedAtEpochMs: 1_000, listId: "list-deleted")

        let merged = mergeCompletedRecordsWithPendingOverrides(
            localRecords: [],
            remoteRecords: [kept, deleted],
            pendingTodoTargets: [],
            pendingDeletedListIds: ["list-deleted"]
        )

        XCTAssertEqual(merged, [kept])
    }

    /// The inverse of `testLocalCompletedRecordsOverrideRemoteForPendingTodoMutation`, and
    /// the half that was missing: the user has just restored this task, so the completion
    /// record is gone locally while the server snapshot still carries it. The loop above
    /// cannot cover this — it re-adds LOCAL completions, and there are none — so a stale
    /// read put the restored row straight back on screen, struck through.
    func testPendingUncompleteKeepsRemoteCompletedRecordOut() {
        let remote = completedRecord(id: "completed-server", originalTodoId: "todo-1", completedAtEpochMs: 1_000)

        let merged = mergeCompletedRecordsWithPendingOverrides(
            localRecords: [],
            remoteRecords: [remote],
            pendingTodoTargets: ["todo-1"],
            pendingUncompletedTodoTargets: ["todo-1"]
        )

        XCTAssertTrue(merged.isEmpty)
    }

    /// The guard is a window, not a permanent local override: with the mutation
    /// acknowledged there is nothing to protect, so a completion made on another device
    /// still lands here.
    func testRemoteCompletedRecordReturnsOnceUncompleteIsAcknowledged() {
        let remote = completedRecord(id: "completed-server", originalTodoId: "todo-1", completedAtEpochMs: 1_000)

        let merged = mergeCompletedRecordsWithPendingOverrides(
            localRecords: [],
            remoteRecords: [remote],
            pendingTodoTargets: [],
            pendingUncompletedTodoTargets: []
        )

        XCTAssertEqual(merged, [remote])
    }

    /// A restore stays unacknowledged for as long as its request takes, and a user who
    /// restores a task and immediately completes it again has both queued. The merge sees
    /// only the net set, so the completion wins — hiding the row would take it out of the
    /// list the user just put it in, which is the same bug mirrored.
    func testRecompletingWhileRestoreIsUnacknowledgedKeepsTheRowCompleted() {
        let remote = completedRecord(id: "completed-server", originalTodoId: "todo-1", completedAtEpochMs: 3_000)
        let uncompleted: Set<String> = ["todo-1"]
        let completed: Set<String> = ["todo-1"]

        let merged = mergeCompletedRecordsWithPendingOverrides(
            localRecords: [remote],
            remoteRecords: [remote],
            pendingTodoTargets: ["todo-1"],
            pendingUncompletedTodoTargets: uncompleted.subtracting(completed)
        )

        XCTAssertEqual(merged, [remote])
    }

    /// Scoping this to the un-complete kind matters: an ordinary field edit is not a
    /// statement about where the row belongs, and letting it hide a genuine completion
    /// would be a second bug rather than a wider fix.
    func testOtherPendingTodoMutationsDoNotHideARemoteCompletion() {
        let remote = completedRecord(id: "completed-server", originalTodoId: "todo-1", completedAtEpochMs: 1_000)

        let merged = mergeCompletedRecordsWithPendingOverrides(
            localRecords: [],
            remoteRecords: [remote],
            pendingTodoTargets: ["todo-1"],
            pendingUncompletedTodoTargets: []
        )

        XCTAssertEqual(merged, [remote])
    }

    // MARK: - Floater side (mergeCompletedFloaterRecordsWithPendingOverrides)
    //
    // Unlike the Todo side above, this has no `pendingDeletedListIds` parameter at all:
    // completedFloaters are no longer pruned locally on floater-list delete (see
    // docs/design/completed-floaters-durability.md and FloaterListRepository), so the merge
    // must not re-hide a remote completed-floater row just because its list has a pending
    // (including staged/undoable) delete mutation — doing so would reintroduce, at the sync
    // layer, exactly the data loss the local-pruning removal fixed.

    func testDropsLocalCompletedFloaterRecordsMissingFromRemoteWithoutPendingMutation() {
        let local = completedFloaterRecord(id: "completed-local", originalFloaterId: "floater-1", completedAtEpochMs: 2_000)

        let merged = mergeCompletedFloaterRecordsWithPendingOverrides(
            localRecords: [local],
            remoteRecords: [],
            pendingFloaterTargets: []
        )

        XCTAssertTrue(merged.isEmpty)
    }

    func testLocalCompletedFloaterRecordsOverrideRemoteForPendingFloaterMutation() {
        let local = completedFloaterRecord(id: "local-completed-1", originalFloaterId: "floater-1", completedAtEpochMs: 2_000)
        let remote = completedFloaterRecord(id: "completed-server", originalFloaterId: "floater-1", completedAtEpochMs: 1_000)

        let merged = mergeCompletedFloaterRecordsWithPendingOverrides(
            localRecords: [local],
            remoteRecords: [remote],
            pendingFloaterTargets: ["floater-1"]
        )

        XCTAssertEqual(merged, [local])
    }

    func testPendingDeletedFloaterListDoesNotRemoveRemoteCompletedFloaterRecordsForThatList() {
        let kept = completedFloaterRecord(id: "kept", originalFloaterId: "floater-1", completedAtEpochMs: 2_000, listId: "list-kept")
        let fromDeletedList = completedFloaterRecord(
            id: "from-deleted-list",
            originalFloaterId: "floater-2",
            completedAtEpochMs: 1_000,
            listId: "list-deleted"
        )

        let merged = mergeCompletedFloaterRecordsWithPendingOverrides(
            localRecords: [],
            remoteRecords: [kept, fromDeletedList],
            pendingFloaterTargets: []
        )

        XCTAssertEqual(Set(merged.map(\.id)), Set([kept.id, fromDeletedList.id]))
    }
}

private func completedRecord(
    id: String,
    originalTodoId: String,
    completedAtEpochMs: Int64,
    listId: String? = nil
) -> CachedCompletedRecord {
    CachedCompletedRecord(
        id: id,
        originalTodoId: originalTodoId,
        title: "Task",
        description: nil,
        priority: "Low",
        dueEpochMs: 1_000,
        completedAtEpochMs: completedAtEpochMs,
        rrule: nil,
        instanceDateEpochMs: nil,
        listId: listId,
        listName: nil,
        listColor: nil
    )
}

private func completedFloaterRecord(
    id: String,
    originalFloaterId: String,
    completedAtEpochMs: Int64,
    listId: String? = nil
) -> CachedCompletedFloaterRecord {
    CachedCompletedFloaterRecord(
        id: id,
        originalFloaterId: originalFloaterId,
        title: "Floater",
        description: nil,
        priority: "Low",
        completedAtEpochMs: completedAtEpochMs,
        listId: listId,
        listName: nil,
        listColor: nil
    )
}
