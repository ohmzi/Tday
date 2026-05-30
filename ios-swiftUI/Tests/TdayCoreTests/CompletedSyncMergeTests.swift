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
