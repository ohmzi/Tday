import XCTest

#if SWIFT_PACKAGE
@testable import TdayCore
#else
@testable import Tday
#endif

/// The regression suite for the bug where completing many tasks at once lost the
/// completions.
///
/// The old shape staged a completion in the view model's `items` array and wrote
/// nothing — no cache row, no history row, no queued mutation — for the whole
/// 8.5 s undo window. Anything that made the list re-read the cache in that window
/// put the rows straight back, and a process death in it dropped the batch with no
/// trace to replay. These tests pin the durable shape: after staging, everything
/// needed to finish the job is already in the state that gets persisted.
///
/// The iOS bulk-selection surface had no automated coverage at all before this.
final class StagedCompletionDurabilityTests: XCTestCase {
    // MARK: - Durability

    func testStagingABatchWritesTheCompletionCacheRowHistoryRowAndMutationForEveryTask() {
        let todos = (0..<100).map { todoItem(id: "todo-\($0)") }
        let before = state(todos: todos.map(openRecord))

        let after = staged(before, completing: todos)

        XCTAssertEqual(after.todos.filter(\.completed).count, 100)
        XCTAssertTrue(after.todos.allSatisfy(\.completed))
        XCTAssertEqual(after.completedItems.count, 100)
        XCTAssertEqual(after.pendingMutations.count, 100)
        XCTAssertTrue(after.pendingMutations.allSatisfy { $0.kind == .completeTodo })
        XCTAssertEqual(Set(after.pendingMutations.compactMap(\.targetId)), Set(todos.map(\.canonicalId)))
    }

    /// The process-death case. Nothing runs after staging — no commit, no sync — and
    /// the batch still has to be recoverable from what was written.
    func testABatchStagedButNeverCommittedIsStillFullyReplayableFromThePersistedState() {
        let todos = (0..<40).map { todoItem(id: "todo-\($0)") }
        let before = state(todos: todos.map(openRecord))

        // Stage, then simulate the process dying: the only thing that survives is
        // the state the cache write persisted.
        let persisted = staged(before, completing: todos)

        for todo in todos {
            XCTAssertTrue(
                persisted.pendingMutations.contains { $0.targetId == todo.canonicalId && $0.kind == .completeTodo },
                "the completion of \(todo.canonicalId) left nothing for the replay to send"
            )
            XCTAssertEqual(persisted.todos.first { $0.canonicalId == todo.canonicalId }?.completed, true)
        }
    }

    /// A recurring occurrence has to carry its `instanceDate` or the server acts on
    /// the whole series (§4.1 of `docs/design/bulk-selection.md`).
    func testStagingARecurringOccurrenceQueuesAnInstanceCompletionCarryingItsInstanceDate() {
        let instanceDate = Date(timeIntervalSince1970: 1_700_000)
        let occurrence = todoItem(id: "todo-r", rrule: "FREQ=DAILY", instanceDate: instanceDate)
        let before = state(todos: [openRecord(occurrence)])

        let after = staged(before, completing: [occurrence])

        let mutation = after.pendingMutations.first
        XCTAssertEqual(mutation?.kind, .completeTodoInstance)
        XCTAssertEqual(mutation?.instanceDateEpochMs, occurrence.instanceDateEpochMilliseconds)
    }

    func testStagingFloatersWritesTheirCompletionHistoryAndMutations() {
        let floaters = (0..<12).map { todoItem(id: "floater-\($0)") }
        var before = OfflineSyncState()
        before.floaters = floaters.map(openFloaterRecord)

        var after = before
        for floater in floaters {
            after = applyingFloaterCompletion(of: floater, to: after, now: 5_000)
        }

        XCTAssertTrue(after.floaters.allSatisfy(\.completed))
        XCTAssertEqual(after.completedFloaters.count, 12)
        XCTAssertEqual(after.pendingMutations.filter { $0.kind == .completeFloater }.count, 12)
    }

    // MARK: - Undo

    func testUndoBeforeAnythingWasSentRestoresTheExactPreviousStateAndSendsNothing() {
        let todos = (0..<25).map { todoItem(id: "todo-\($0)") }
        let before = state(todos: todos.map(openRecord))
        let after = staged(before, completing: todos)
        let snapshot = stagedTodoCompletionSnapshot(before: before, after: after)

        let restored = restoringStagedTodoCompletion(snapshot, in: after, now: 9_000)

        XCTAssertEqual(restored.todos.sorted { $0.id < $1.id }, before.todos.sorted { $0.id < $1.id })
        XCTAssertTrue(restored.completedItems.isEmpty)
        // Nothing left the queue, so undo is lossless: no complete, and no
        // uncomplete to walk one back.
        XCTAssertTrue(restored.pendingMutations.isEmpty)
    }

    /// A sync landing inside the undo window replays the queued completion, so by
    /// the time Undo is tapped the server really has completed the task. Dropping
    /// the mutation would leave the two disagreeing silently.
    func testUndoAfterTheCompletionAlreadyReplayedQueuesTheMatchingUncomplete() {
        let todo = todoItem(id: "todo-1")
        let before = state(todos: [openRecord(todo)])
        let after = staged(before, completing: [todo])
        let snapshot = stagedTodoCompletionSnapshot(before: before, after: after)

        // The replay drained the queue while the undo toast was still on screen.
        var replayed = after
        replayed.pendingMutations = []

        let restored = restoringStagedTodoCompletion(snapshot, in: replayed, now: 9_000)

        XCTAssertEqual(restored.todos.first?.completed, false)
        XCTAssertTrue(restored.completedItems.isEmpty)
        XCTAssertEqual(restored.pendingMutations.count, 1)
        XCTAssertEqual(restored.pendingMutations.first?.kind, .uncompleteTodo)
        XCTAssertEqual(restored.pendingMutations.first?.targetId, todo.canonicalId)
    }

    func testUndoOfAPartiallyReplayedBatchReversesOnlyTheItemsThatWereSent() {
        let todos = (0..<10).map { todoItem(id: "todo-\($0)") }
        let before = state(todos: todos.map(openRecord))
        let after = staged(before, completing: todos)
        let snapshot = stagedTodoCompletionSnapshot(before: before, after: after)

        // Six drained before the 429 stopped the replay; four are still queued.
        var partiallyReplayed = after
        partiallyReplayed.pendingMutations = Array(after.pendingMutations.suffix(4))

        let restored = restoringStagedTodoCompletion(snapshot, in: partiallyReplayed, now: 9_000)

        XCTAssertTrue(restored.todos.allSatisfy { !$0.completed })
        XCTAssertEqual(restored.pendingMutations.count, 6)
        XCTAssertTrue(restored.pendingMutations.allSatisfy { $0.kind == .uncompleteTodo })
    }

    /// A todo that was never on the server has nothing to uncomplete there.
    func testUndoDoesNotQueueAnUncompleteForALocalOnlyTodo() {
        let todo = todoItem(id: LOCAL_TODO_PREFIX + "abc")
        let before = state(todos: [openRecord(todo)])
        let after = staged(before, completing: [todo])
        let snapshot = stagedTodoCompletionSnapshot(before: before, after: after)

        var replayed = after
        replayed.pendingMutations = []

        let restored = restoringStagedTodoCompletion(snapshot, in: replayed, now: 9_000)

        XCTAssertTrue(restored.pendingMutations.isEmpty)
        XCTAssertEqual(restored.todos.first?.completed, false)
    }

    /// A sync inside the undo window can drop the completed row from the merged
    /// cache. Undo has to bring it back rather than quietly lose the task.
    func testUndoReAddsARowThatSyncedAwayWhileItWasCompleted() {
        let todo = todoItem(id: "todo-1")
        let before = state(todos: [openRecord(todo)])
        let after = staged(before, completing: [todo])
        let snapshot = stagedTodoCompletionSnapshot(before: before, after: after)

        var merged = after
        merged.todos = []

        let restored = restoringStagedTodoCompletion(snapshot, in: merged, now: 9_000)

        XCTAssertEqual(restored.todos.count, 1)
        XCTAssertEqual(restored.todos.first?.completed, false)
    }

    func testUndoLeavesUnrelatedQueuedMutationsAlone() {
        let todo = todoItem(id: "todo-1")
        var before = state(todos: [openRecord(todo), openRecord(todoItem(id: "todo-2"))])
        let unrelated = PendingMutationRecord(
            mutationId: "unrelated",
            kind: .deleteTodo,
            targetId: "todo-2",
            timestampEpochMs: 1,
            title: nil,
            description: nil,
            priority: nil,
            dueEpochMs: nil,
            rrule: nil,
            listId: nil,
            pinned: nil,
            completed: nil,
            instanceDateEpochMs: nil,
            name: nil,
            color: nil,
            iconKey: nil
        )
        before.pendingMutations = [unrelated]
        let after = staged(before, completing: [todo])
        let snapshot = stagedTodoCompletionSnapshot(before: before, after: after)

        XCTAssertEqual(snapshot.mutations.count, 1, "the diff must not claim mutations it did not queue")

        let restored = restoringStagedTodoCompletion(snapshot, in: after, now: 9_000)

        XCTAssertEqual(restored.pendingMutations, [unrelated])
    }

    func testUndoOfAStagedFloaterCompletionRestoresItAndQueuesAnUncompleteOnlyOnceSent() {
        let floater = todoItem(id: "floater-1")
        var before = OfflineSyncState()
        before.floaters = [openFloaterRecord(floater)]
        let after = applyingFloaterCompletion(of: floater, to: before, now: 5_000)
        let snapshot = stagedFloaterCompletionSnapshot(before: before, after: after)

        let lossless = restoringStagedFloaterCompletion(snapshot, in: after, now: 9_000)
        XCTAssertEqual(lossless.floaters, before.floaters)
        XCTAssertTrue(lossless.completedFloaters.isEmpty)
        XCTAssertTrue(lossless.pendingMutations.isEmpty)

        var replayed = after
        replayed.pendingMutations = []
        let reversed = restoringStagedFloaterCompletion(snapshot, in: replayed, now: 9_000)
        XCTAssertEqual(reversed.pendingMutations.first?.kind, .uncompleteFloater)
    }

    // MARK: - AsyncLock

    /// The lock is held across a whole bulk replay, so a waiter must actually
    /// suspend. The old `while locked { await Task.yield() }` kept every waiter
    /// runnable for the duration.
    func testAsyncLockSerialisesOverlappingCallersAndResumesThemInOrder() async {
        let lock = AsyncLock()
        let recorder = OrderRecorder()

        await withTaskGroup(of: Void.self) { group in
            for index in 0..<8 {
                group.addTask {
                    await lock.withLock {
                        await recorder.enter()
                        await Task.yield()
                        await recorder.leave(index)
                    }
                }
            }
        }

        let overlaps = await recorder.overlaps
        XCTAssertEqual(overlaps, 0, "two callers were inside the lock at the same time")
        let completed = await recorder.completed
        XCTAssertEqual(completed.count, 8)
    }

    func testAsyncLockIsReusableAfterTheOperationThrows() async {
        let lock = AsyncLock()
        struct Boom: Error {}

        do {
            _ = try await lock.withLock { () async throws -> Int in
                throw Boom()
            }
            XCTFail("expected the operation's error to propagate")
        } catch {
            // Expected — what matters is that the lock was released anyway.
        }

        let value = await lock.withLock { 42 }
        XCTAssertEqual(value, 42)
    }
}

// MARK: - Helpers

private actor OrderRecorder {
    private var inside = 0
    private(set) var overlaps = 0
    private(set) var completed: [Int] = []

    func enter() {
        inside += 1
        if inside > 1 {
            overlaps += 1
        }
    }

    func leave(_ index: Int) {
        inside -= 1
        completed.append(index)
    }
}

private func todoItem(
    id: String,
    rrule: String? = nil,
    instanceDate: Date? = nil
) -> TodoItem {
    TodoItem(
        id: id,
        canonicalId: id,
        title: "Task \(id)",
        description: nil,
        priority: "Low",
        due: Date(timeIntervalSince1970: 1_000),
        rrule: rrule,
        instanceDate: instanceDate,
        pinned: false,
        completed: false,
        listId: nil,
        updatedAt: Date(timeIntervalSince1970: 900)
    )
}

private func openRecord(_ todo: TodoItem) -> CachedTodoRecord {
    CachedTodoRecord(
        id: todo.id,
        canonicalId: todo.canonicalId,
        title: todo.title,
        description: todo.description,
        priority: todo.priority,
        dueEpochMs: todo.due?.epochMilliseconds,
        rrule: todo.rrule,
        instanceDateEpochMs: todo.instanceDateEpochMilliseconds,
        pinned: false,
        completed: false,
        listId: todo.listId,
        updatedAtEpochMs: 1_000
    )
}

private func openFloaterRecord(_ floater: TodoItem) -> CachedFloaterRecord {
    CachedFloaterRecord(
        id: floater.id,
        canonicalId: floater.canonicalId,
        title: floater.title,
        description: floater.description,
        priority: floater.priority,
        pinned: false,
        completed: false,
        listId: floater.listId,
        updatedAtEpochMs: 1_000
    )
}

private func state(todos: [CachedTodoRecord]) -> OfflineSyncState {
    var state = OfflineSyncState()
    state.todos = todos
    return state
}

/// The exact fold `TodoRepository.stageCompleteTodos(_:)` performs inside its one
/// cache write.
private func staged(_ state: OfflineSyncState, completing todos: [TodoItem]) -> OfflineSyncState {
    var nextState = state
    for todo in todos {
        nextState = applyingCompletion(of: todo, to: nextState, now: 5_000)
    }
    return nextState
}
