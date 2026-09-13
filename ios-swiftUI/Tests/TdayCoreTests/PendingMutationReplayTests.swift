import XCTest

#if SWIFT_PACKAGE
@testable import TdayCore
#else
@testable import Tday
#endif

/// `SyncManager.applyPendingMutations` replays the offline queue one mutation at a
/// time against the server, and what it does with a failure decides whether work
/// survives. `pendingMutationReplayOutcome` is that decision and
/// `applyPendingMutationReplayOutcome` is its effect on the queue that gets written
/// back to the cache.
///
/// The case these tests exist for is 429. The replay used to treat a rate limit as
/// an ordinary per-item failure: it re-queued that one mutation and carried on,
/// firing the remaining requests into a window the server had already closed. It
/// now stops at the first 429 — and *stopping* has to mean leaving the rest QUEUED
/// for the next sync. A rate limit that quietly emptied the queue would be a far
/// worse bug than the one being fixed, so the retention is asserted directly, not
/// inferred from the classification.
final class PendingMutationReplayTests: XCTestCase {
    // MARK: - Classification

    func testRateLimitStopsTheReplay() {
        XCTAssertEqual(pendingMutationReplayOutcome(for: apiError(429)), .stopAndKeepRemaining)
    }

    func testRateLimitIsRecognisedFromTheStatusCodeNotTheMessage() {
        XCTAssertTrue(isRateLimitedError(apiError(429)))
        XCTAssertFalse(isRateLimitedError(apiError(400, message: "429 Too Many Requests")))
        XCTAssertFalse(isRateLimitedError(apiError(nil, message: "Too Many Requests")))
        XCTAssertFalse(isRateLimitedError(URLError(.timedOut)))
    }

    /// The 429 branch is only reachable, and only correct, because a 429 is neither
    /// of the two things the replay already classified. If it were "connectivity"
    /// the old code would already have stopped; if it were "unrecoverable" the old
    /// code would have DROPPED the mutation instead of re-queueing it.
    func testRateLimitIsNeitherConnectivityNorUnrecoverable() {
        XCTAssertFalse(isLikelyConnectivityIssue(apiError(429)))
        XCTAssertFalse(isLikelyUnrecoverableMutationError(apiError(429)))
    }

    func testConnectivityAndBackendOutagesStillStopTheReplay() {
        XCTAssertEqual(pendingMutationReplayOutcome(for: URLError(.notConnectedToInternet)), .stopAndKeepRemaining)
        XCTAssertEqual(pendingMutationReplayOutcome(for: apiError(nil)), .stopAndKeepRemaining)
        XCTAssertEqual(pendingMutationReplayOutcome(for: apiError(408)), .stopAndKeepRemaining)
        XCTAssertEqual(pendingMutationReplayOutcome(for: apiError(503)), .stopAndKeepRemaining)
    }

    func testUnrecoverableClientErrorsAreDroppedAndTheReplayCarriesOn() {
        XCTAssertEqual(pendingMutationReplayOutcome(for: apiError(400)), .dropAndContinue)
        XCTAssertEqual(pendingMutationReplayOutcome(for: apiError(404)), .dropAndContinue)
        XCTAssertEqual(pendingMutationReplayOutcome(for: apiError(409)), .dropAndContinue)
    }

    func testUnclassifiedFailuresKeepOnlyTheirOwnMutation() {
        XCTAssertEqual(pendingMutationReplayOutcome(for: SampleFailure.boom), .keepAndContinue)
    }

    // MARK: - What the queue looks like afterwards

    func testStoppingKeepsTheFailedMutationAndEveryLaterOne() {
        let ordered = orderedMutations(count: 5)
        var remaining: [PendingMutationRecord] = []

        let stopped = applyPendingMutationReplayOutcome(
            pendingMutationReplayOutcome(for: apiError(429)),
            at: 2,
            of: ordered,
            keeping: &remaining
        )

        XCTAssertTrue(stopped)
        XCTAssertEqual(remaining, Array(ordered[2...]))
    }

    func testStoppingPreservesMutationsAlreadyQueuedEarlierInTheReplay() {
        let ordered = orderedMutations(count: 4)
        // e.g. a staged delete the loop re-queued without attempting it.
        var remaining = [ordered[0]]

        _ = applyPendingMutationReplayOutcome(
            .stopAndKeepRemaining,
            at: 2,
            of: ordered,
            keeping: &remaining
        )

        XCTAssertEqual(remaining, [ordered[0], ordered[2], ordered[3]])
    }

    func testStoppingOnTheLastMutationKeepsExactlyThatOne() {
        let ordered = orderedMutations(count: 3)
        var remaining: [PendingMutationRecord] = []

        let stopped = applyPendingMutationReplayOutcome(
            pendingMutationReplayOutcome(for: apiError(429)),
            at: 2,
            of: ordered,
            keeping: &remaining
        )

        XCTAssertTrue(stopped)
        XCTAssertEqual(remaining, [ordered[2]])
    }

    func testKeepAndContinueQueuesOnlyTheFailedMutation() {
        let ordered = orderedMutations(count: 4)
        var remaining: [PendingMutationRecord] = []

        let stopped = applyPendingMutationReplayOutcome(.keepAndContinue, at: 1, of: ordered, keeping: &remaining)

        XCTAssertFalse(stopped)
        XCTAssertEqual(remaining, [ordered[1]])
    }

    func testDropAndContinueQueuesNothing() {
        let ordered = orderedMutations(count: 4)
        var remaining: [PendingMutationRecord] = []

        let stopped = applyPendingMutationReplayOutcome(.dropAndContinue, at: 1, of: ordered, keeping: &remaining)

        XCTAssertFalse(stopped)
        XCTAssertTrue(remaining.isEmpty)
    }

    /// The whole point, in the shape the replay loop actually runs: two mutations
    /// go through, the third draws a 429, and nothing after it is attempted — but
    /// nothing after it is lost either.
    func testARateLimitMidReplayLeavesEveryUnsentMutationQueued() {
        let ordered = orderedMutations(count: 6)
        let firstRateLimitedIndex = 2
        var remaining: [PendingMutationRecord] = []
        var attempted: [String] = []

        for index in ordered.indices {
            attempted.append(ordered[index].mutationId)
            guard index >= firstRateLimitedIndex else {
                continue
            }
            let outcome = pendingMutationReplayOutcome(for: apiError(429))
            if applyPendingMutationReplayOutcome(outcome, at: index, of: ordered, keeping: &remaining) {
                break
            }
        }

        XCTAssertEqual(attempted, ["m0", "m1", "m2"], "the replay should stop at the first 429")
        XCTAssertEqual(
            remaining.map(\.mutationId),
            ["m2", "m3", "m4", "m5"],
            "the rate-limited mutation and every later one must stay queued for the next sync"
        )
        XCTAssertEqual(
            Set(remaining.map(\.mutationId)).count,
            remaining.count,
            "nothing should be queued twice"
        )
    }

    // MARK: - Fixtures

    private func orderedMutations(count: Int) -> [PendingMutationRecord] {
        (0 ..< count).map { index in
            PendingMutationRecord(
                mutationId: "m\(index)",
                kind: .completeTodo,
                targetId: "todo-\(index)",
                timestampEpochMs: Int64(index),
                title: nil,
                description: nil,
                priority: nil,
                dueEpochMs: nil,
                rrule: nil,
                listId: nil,
                pinned: nil,
                completed: true,
                instanceDateEpochMs: nil,
                name: nil,
                color: nil,
                iconKey: nil
            )
        }
    }

    private func apiError(_ statusCode: Int?, message: String = "Rate limited") -> APIError {
        APIError(message: message, statusCode: statusCode)
    }
}

private enum SampleFailure: Error {
    case boom
}
