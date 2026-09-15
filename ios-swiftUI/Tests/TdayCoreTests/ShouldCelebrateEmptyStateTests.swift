import Foundation
import XCTest

#if SWIFT_PACKAGE
@testable import TdayCore
#else
@testable import Tday
#endif

/// `shouldCelebrateEmptyState` — when a finished list gets its payoff, and when
/// it stops having one.
///
/// This decision lived in a `private var` on `TodoListScreen` until the undo bug
/// was reported, which is most of the reason the bug survived review: a computed
/// property on a `View` is reachable from no test, and there is no Swift
/// toolchain on the machines this work happens on, so "it looked right" was the
/// whole of the evidence anyone could offer for it. Lifted out, it is a pure
/// function of eight values and every claim about it is an assertion — the same
/// shape, for the same reason, as Android's `internal` + JVM test.
///
/// Two blocks. The first is the behaviour that already existed and must not move:
/// nothing celebrates while work is left, this device's own tick celebrates
/// inside the window and not outside it, a remote emptying celebrates only on a
/// screen someone is actually looking at. The second is the cancel.
///
/// The cancel's FIRST case is the screenshot, and it is the one a naive fix
/// misses. `hasNoPendingItems` is true on BOTH sides of an undone OVERDUE task,
/// because that predicate excludes Earlier on purpose so requirement 4 can let a
/// finished today celebrate while overdue work waits. A fix written as "the scope
/// stopped being empty" passes every other assertion in this file and fails the
/// only one that was reported.
///
/// There is no Swift toolchain on the machine this was written on, so `xctest`
/// in CI is this file's gate and nothing here was run locally. What WAS done
/// locally is the half that does not need Swift: the function's eight lines were
/// transcribed, every case below evaluated against the transcription, and then
/// four perturbations applied to confirm the assertions go red rather than
/// passing by coincidence. `>=` weakened to `>` fails the same-instant case and
/// only that one; `.distantPast` swapped for `.distantFuture` fails three;
/// dropping either opening stamp out of the `max` fails the two re-opening cases
/// that exist to cover exactly that. That is ordering, not compilation — the
/// build is CI's half and nothing here claims otherwise.
///
/// One perturbation that does NOT go red, recorded because a silent hole is
/// worse than a known one: moving the cancel clause below the two window tests
/// changes no result in this file, and cannot, since both arrangements answer
/// false. It is placed above them because the cancel is a statement about the
/// celebration as a whole rather than about either trigger, and that is a
/// readability claim with no test behind it.
final class ShouldCelebrateEmptyStateTests: XCTestCase {

    private let now = Date(timeIntervalSince1970: 1_700_000_000)
    private let window: TimeInterval = 4

    /// The gate with the plain case pre-filled, so each test says only what it is
    /// about. `hasNoPendingItems` defaults true because every interesting case is
    /// downstream of it; the one test that is about it says so by passing false.
    private func celebrates(
        hasNoPendingItems: Bool = true,
        lastCompletionAt: Date? = nil,
        remoteEmptiedAt: Date? = nil,
        celebrationCancelledAt: Date? = nil,
        isVisible: Bool = true,
        isActive: Bool = true,
        at: Date
    ) -> Bool {
        shouldCelebrateEmptyState(
            hasNoPendingItems: hasNoPendingItems,
            lastCompletionAt: lastCompletionAt,
            remoteEmptiedAt: remoteEmptiedAt,
            celebrationCancelledAt: celebrationCancelledAt,
            isVisible: isVisible,
            isActive: isActive,
            now: at,
            window: window
        )
    }

    // MARK: - The payoff, unchanged

    func testNothingCelebratesWhileThereIsStillWorkInScope() {
        XCTAssertFalse(
            celebrates(
                hasNoPendingItems: false,
                lastCompletionAt: now,
                at: now
            ),
            "a completion with work still left in scope is not the end of anything"
        )
    }

    func testThisDevicesOwnTickCelebratesInsideTheWindowAndNotOutsideIt() {
        XCTAssertTrue(celebrates(lastCompletionAt: now, at: now.addingTimeInterval(0.5)))
        XCTAssertFalse(celebrates(lastCompletionAt: now, at: now.addingTimeInterval(window + 0.001)))
    }

    func testAnEmptyListNobodyFinishedGetsThePlainArrival() {
        // The case the whole gate exists for: opening a list that was already
        // empty, or deleting the last task, is an absence rather than a payoff.
        XCTAssertFalse(celebrates(at: now))
    }

    func testARemoteEmptyingCelebratesOnlyOnAScreenSomeoneIsLookingAt() {
        XCTAssertFalse(
            celebrates(remoteEmptiedAt: now, isVisible: false, at: now.addingTimeInterval(0.1)),
            "a transition nobody was looking at does not surface a burst retroactively"
        )
        XCTAssertFalse(
            celebrates(remoteEmptiedAt: now, isActive: false, at: now.addingTimeInterval(0.1)),
            "nor does one that landed while the app was backgrounded"
        )
        XCTAssertTrue(celebrates(remoteEmptiedAt: now, at: now.addingTimeInterval(0.1)))
    }

    // MARK: - The cancel: a pending row arrived, so the scope is not finished

    func testAnUndoneOverdueTaskCancelsEvenThoughTheScopeStillReadsEmpty() {
        // THE REPORTED BUG. The restored row is overdue, so it lands in Earlier —
        // which `hasNoPendingItems` excludes by design. Nothing about emptiness
        // moves on either side of the undo. The only thing that happened is that
        // a row arrived, and that is what has to end this.
        XCTAssertFalse(
            celebrates(
                lastCompletionAt: now,
                celebrationCancelledAt: now.addingTimeInterval(0.9),
                at: now.addingTimeInterval(1.0)
            ),
            "an undone overdue row leaves hasNoPendingItems true; only the arrival can end the burst"
        )
    }

    func testThePlainUndoCancelsTheSameWay() {
        // No overdue rows, so the restored task makes the scope non-empty too and
        // the gate has two reasons to be false. It is asserted separately because
        // the two reasons are not the same reason: this one still has to hold if
        // a future caller ever hands in a `hasNoPendingItems` that lags a frame
        // behind the cache, which is exactly what an optimistic local restore is.
        XCTAssertFalse(
            celebrates(
                hasNoPendingItems: false,
                lastCompletionAt: now,
                celebrationCancelledAt: now.addingTimeInterval(0.9),
                at: now.addingTimeInterval(1.0)
            )
        )
    }

    func testAnUndoInTheSameInstantAsItsOwnCompletionStillCancels() {
        // `>=`, not `>`. An undo always follows the completion it undoes, so a
        // stamp that ties with it has to win: on a clock read twice in a row, a
        // tie is the ordinary case rather than the strange one.
        XCTAssertFalse(
            celebrates(
                lastCompletionAt: now,
                celebrationCancelledAt: now,
                at: now
            )
        )
    }

    func testANewCompletionAfterACancelReOpensTheWindowWithoutClearingAnything() {
        // Why the cancel is a stamp compared against the opening stamps rather
        // than a reset of them: the later completion is simply the newer stamp,
        // so the window re-opens with no mutation, no effect, and no ordering
        // question left over for the next reader.
        XCTAssertTrue(
            celebrates(
                lastCompletionAt: now.addingTimeInterval(2),
                celebrationCancelledAt: now.addingTimeInterval(1),
                at: now.addingTimeInterval(2.1)
            )
        )
    }

    func testACancelEndsARemoteEmptyingJustAsItEndsThisScreensOwnTick() {
        // A collaborator emptying the list still celebrates (the case above); a
        // collaborator — or a sync, or a remote undo — putting something back
        // cancels, on the same comparison. The remote stamp is the later of the
        // two openings here, and the cancel still has to beat it.
        XCTAssertFalse(
            celebrates(
                remoteEmptiedAt: now,
                celebrationCancelledAt: now.addingTimeInterval(0.05),
                at: now.addingTimeInterval(0.1)
            )
        )
    }

    func testACancelOlderThanTheCompletionItPrecedesChangesNothing() {
        // The cancel is never read as "celebrations are forbidden from now on".
        // A stamp left behind by an earlier arrival is simply the older one, and
        // the completion that came after it celebrates exactly as before.
        XCTAssertTrue(
            celebrates(
                lastCompletionAt: now,
                celebrationCancelledAt: now.addingTimeInterval(-1),
                at: now.addingTimeInterval(0.5)
            )
        )
    }

    func testARemoteEmptyingAfterACancelReOpensTheWindowToo() {
        // The mirror of the case above, and not redundant with it: the cancel is
        // compared against the LATER of the two opening stamps, so a `max` that
        // quietly forgot one of them would still pass every other assertion here.
        // One case per stamp is what makes that comparison pinned rather than
        // assumed.
        XCTAssertTrue(
            celebrates(
                remoteEmptiedAt: now.addingTimeInterval(2),
                celebrationCancelledAt: now.addingTimeInterval(1),
                at: now.addingTimeInterval(2.1)
            )
        )
    }

    func testAScreenWhereNoRowHasEverArrivedIsTheCaseEveryTestAboveRan() {
        // A nil cancel is "no arrival has been seen on this screen", and it must
        // leave the gate byte for byte as it was — which is what the nil threaded
        // through the first block is asserting, once, here, on the one case that
        // would be a silent regression if the sentinel were ever read as a real
        // timestamp.
        XCTAssertTrue(celebrates(lastCompletionAt: now, celebrationCancelledAt: nil, at: now))
    }
}
