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
}

private func completedRecord(id: String, originalTodoId: String, completedAtEpochMs: Int64) -> CachedCompletedRecord {
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
        listName: nil,
        listColor: nil
    )
}
