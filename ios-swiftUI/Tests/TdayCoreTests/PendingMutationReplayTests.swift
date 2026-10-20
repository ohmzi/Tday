import XCTest

#if SWIFT_PACKAGE
@testable import TdayCore
#else
@testable import Tday
#endif

/// `SyncManager.applyPendingMutations` replays the offline queue one mutation at a
/// time against the server, and what it does with a failure decides whether work
/// survives. `pendingMutationReplayOutcome` is that decision,
/// `applyPendingMutationReplayOutcome` is its effect on the queue that gets written
/// back to the cache, and `runPendingMutationReplay` is the loop that sequences the
/// two — the loop the app actually runs, with only the per-mutation network push
/// behind a closure.
///
/// The last part matters: the "…afterwards" section below exercises the two helpers
/// directly, but the loop tests drive `runPendingMutationReplay` itself, so reverting
/// the production control flow breaks them. A test that rebuilt the loop locally
/// would pass against a replay with no 429 handling at all.
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

    // MARK: - The loop the replay actually runs

    /// The whole point, driven through `runPendingMutationReplay` — the same
    /// function `SyncManager.applyPendingMutations` calls, with only the network
    /// push stubbed. Two mutations go through, the third draws a 429, nothing after
    /// it is attempted, and nothing after it is lost. Revert the 429 handling and
    /// this fails: without it a 429 is `keepAndContinue`, so `apply` would be called
    /// for all six and only the six failures would be queued.
    @MainActor
    func testARateLimitMidReplayLeavesEveryUnsentMutationQueued() async {
        let ordered = orderedMutations(count: 6)
        let firstRateLimitedIndex = 2
        var attempted: [String] = []

        let replay = await runPendingMutationReplay(ordered) { index, mutation in
            attempted.append(mutation.mutationId)
            if index >= firstRateLimitedIndex {
                throw APIError(message: "Rate limited", statusCode: 429)
            }
        }

        XCTAssertEqual(attempted, ["m0", "m1", "m2"], "the replay should stop at the first 429")
        XCTAssertTrue(replay.stoppedOnRateLimit, "the caller needs to know the stop was a rate limit")
        XCTAssertEqual(
            replay.remaining.map(\.mutationId),
            ["m2", "m3", "m4", "m5"],
            "the rate-limited mutation and every later one must stay queued for the next sync"
        )
        XCTAssertEqual(
            Set(replay.remaining.map(\.mutationId)).count,
            replay.remaining.count,
            "nothing should be queued twice"
        )
    }

    /// A staged delete is re-queued without ever being handed to `apply`, and a later
    /// 429 must not queue it a second time — the stop slices from the failing index,
    /// so the earlier keeps have to survive alongside it.
    @MainActor
    func testStagedMutationsAreNeverSentAndSurviveARateLimitLaterInTheReplay() async {
        var ordered = orderedMutations(count: 4)
        ordered[0] = stagedMutation(id: "staged", timestampEpochMs: -1)
        var attempted: [String] = []

        let replay = await runPendingMutationReplay(ordered) { index, mutation in
            attempted.append(mutation.mutationId)
            if index == 2 {
                throw APIError(message: "Rate limited", statusCode: 429)
            }
        }

        XCTAssertEqual(attempted, ["m1", "m2"], "a staged mutation must never reach the server")
        XCTAssertTrue(replay.stoppedOnRateLimit)
        XCTAssertEqual(replay.remaining.map(\.mutationId), ["staged", "m2", "m3"])
    }

    /// The two continuing outcomes, through the same loop: a 400 drops its mutation,
    /// an unclassified error keeps only itself, and neither stops the replay.
    @MainActor
    func testDroppedAndKeptFailuresBothLetTheReplayFinish() async {
        let ordered = orderedMutations(count: 4)
        var attempted: [String] = []

        let replay = await runPendingMutationReplay(ordered) { index, mutation in
            attempted.append(mutation.mutationId)
            if index == 1 {
                throw APIError(message: "Bad request", statusCode: 400)
            }
            if index == 2 {
                throw SampleFailure.boom
            }
        }

        XCTAssertEqual(attempted, ["m0", "m1", "m2", "m3"], "neither failure should stop the replay")
        XCTAssertFalse(replay.stoppedOnRateLimit)
        XCTAssertEqual(replay.remaining.map(\.mutationId), ["m2"])
    }

    /// A clean pass leaves nothing queued, so the success path cannot silently
    /// re-queue work the server already accepted.
    @MainActor
    func testAReplayWithNoFailuresEmptiesTheQueue() async {
        let ordered = orderedMutations(count: 3)

        let replay = await runPendingMutationReplay(ordered) { _, _ in }

        XCTAssertTrue(replay.remaining.isEmpty)
        XCTAssertFalse(replay.stoppedOnRateLimit)
    }

    /// Connectivity still stops the replay, but it is not a rate limit — the caller
    /// keys the "skip the rest of the cycle" back-off off this flag, and a dropped
    /// connection has its own handling further up.
    @MainActor
    func testAConnectivityStopIsNotReportedAsARateLimit() async {
        let ordered = orderedMutations(count: 4)

        let replay = await runPendingMutationReplay(ordered) { index, _ in
            if index == 1 {
                throw URLError(.notConnectedToInternet)
            }
        }

        XCTAssertFalse(replay.stoppedOnRateLimit)
        XCTAssertEqual(replay.remaining.map(\.mutationId), ["m1", "m2", "m3"])
    }

    // MARK: - Fixtures

    private func orderedMutations(count: Int) -> [PendingMutationRecord] {
        (0 ..< count).map { index in
            mutation(id: "m\(index)", targetId: "todo-\(index)", timestampEpochMs: Int64(index))
        }
    }

    private func stagedMutation(id: String, timestampEpochMs: Int64) -> PendingMutationRecord {
        mutation(id: id, targetId: "list-\(id)", timestampEpochMs: timestampEpochMs, staged: true)
    }

    private func mutation(
        id: String,
        targetId: String,
        timestampEpochMs: Int64,
        staged: Bool = false
    ) -> PendingMutationRecord {
        PendingMutationRecord(
            mutationId: id,
            kind: .completeTodo,
            targetId: targetId,
            timestampEpochMs: timestampEpochMs,
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
            iconKey: nil,
            staged: staged
        )
    }

    private func apiError(_ statusCode: Int?, message: String = "Rate limited") -> APIError {
        APIError(message: message, statusCode: statusCode)
    }
}

private enum SampleFailure: Error {
    case boom
}
