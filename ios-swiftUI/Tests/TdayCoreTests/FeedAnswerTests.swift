import Foundation
import XCTest

#if SWIFT_PACKAGE
@testable import TdayCore
#else
@testable import Tday
#endif

/// `feedAnswer` — the one decision every empty-state and every row-skeleton gate
/// on this client now defers to.
///
/// It is a free function for the reason `shouldCelebrateEmptyState` next door is
/// one: the gates that used to decide this were computed `private var`s on three
/// different `View`s, a computed property on a `View` is reachable from no test,
/// and there is no Swift toolchain on the machines this work happens on — so "it
/// looked right" was the whole of the evidence anyone could offer, and the shape
/// it must NOT have is easier to write by accident than the shape it must.
///
/// That wrong shape is `!isLoading`, and it was written by accident in six
/// places across three screens. The user's report is what it costs. Pull the
/// Anytime home down with no tasks in it and the illustration, the heading and
/// the body all go, the page collapses upward, three grey placeholder bars grow
/// in their place, and the whole block comes back when the refresh returns with
/// nothing new. A flash of absence for a refresh that changed nothing.
///
/// Both directions are asserted here, and the SECOND is the one to perturb first
/// when reading this file. Making the empty state survive a refresh is easy; the
/// way to overshoot it is to make the empty state unconditional, and telling
/// someone "you have no tasks" while their very first sync is still in flight is
/// a worse bug than the one being fixed.
///
/// There is no Swift toolchain on the machine this was written on, so `xctest`
/// in CI is this file's gate and nothing here was run locally. What WAS done
/// locally is the half that does not need Swift: `feedAnswer`'s three lines were
/// transcribed, every case below evaluated against the transcription, and then
/// each of the function's three terms removed in turn to confirm the assertions
/// go red rather than passing by coincidence. Dropping the `firstAnswerLanded`
/// guard — returning `.empty` whenever the rows are empty, which is the exact
/// overshoot this whole file is about — fails the two withholding assertions and
/// one row of the truth table, and nothing else. Dropping the `storeRead` guard
/// fails one of the two assertions in `testACacheReadThatHasNotLandedHasNothingToSayEither`
/// (the answered one; the unanswered one still lands on `.awaitingFirst` by the
/// other route) and three rows of the table, which is why the table is written
/// out rather than sampled. Inverting the `rowsEmpty` guard fails seven of the
/// nine assertions here, as a term that decides three quarters of the states
/// should. That is ordering and coverage, not compilation — the build is CI's
/// half and nothing here claims otherwise.
final class FeedAnswerTests: XCTestCase {

    // MARK: - The reported bug

    func testAnAnswerAlreadyOnScreenIsNotWithdrawnByAnythingARefreshCanMove() {
        // THE REPORTED BUG. There is no refresh parameter to vary — the signature
        // is the assertion — so what is stated instead is its consequence: a
        // hydrated, empty, answered feed is `.empty` and stays `.empty`, because
        // the only values a pull changes on any of these screens (`isLoading` on
        // all three view models, `isRefreshing` on the two pull containers) have
        // nowhere to enter.
        XCTAssertEqual(
            feedAnswer(storeRead: true, rowsEmpty: true, firstAnswerLanded: true),
            .empty
        )

        // Bound as a typed reference so that re-admitting a loading flag to the
        // signature stops COMPILING this file rather than quietly passing it.
        // Losing that is the only way this fix regresses without someone meaning
        // to, and it is how it regressed into existence in the first place.
        let decide: (Bool, Bool, Bool) -> FeedAnswer = feedAnswer(storeRead:rowsEmpty:firstAnswerLanded:)
        XCTAssertEqual(decide(true, true, true), .empty)
    }

    // MARK: - The overshoot, which is the half that is easy to lose

    func testTheEmptyStateIsStillWithheldBeforeTheFirstAnswerExists() {
        // A fresh install's cache read lands immediately and lands EMPTY, so
        // `storeRead` on its own says "hydrated" about a device that has never
        // heard from its workspace. Without the second term the fix would ship
        // "No tasks" over a first sync still in flight, which is worse than the
        // flicker it replaced: the flicker was wrong for half a second, this
        // would be wrong and look settled.
        XCTAssertEqual(
            feedAnswer(storeRead: true, rowsEmpty: true, firstAnswerLanded: false),
            .awaitingFirst
        )
        XCTAssertNotEqual(
            feedAnswer(storeRead: true, rowsEmpty: true, firstAnswerLanded: false),
            .empty
        )
    }

    func testACacheReadThatHasNotLandedHasNothingToSayEither() {
        // The other half of `.awaitingFirst`, and the one this client does not
        // currently reach: all three feed view models hydrate synchronously in
        // `init`, so `hasHydratedFromCache` is true from the first frame here.
        // It is a term anyway, and asserted anyway, because it is the same
        // sentence Android and the web ask (`hasHydratedSnapshot`, TanStack's
        // `status === "success"`), and because a view model that ever moves its
        // hydrate off the initializer must not have to rediscover this rule.
        XCTAssertEqual(
            feedAnswer(storeRead: false, rowsEmpty: true, firstAnswerLanded: true),
            .awaitingFirst
        )
        XCTAssertEqual(
            feedAnswer(storeRead: false, rowsEmpty: true, firstAnswerLanded: false),
            .awaitingFirst
        )
    }

    // MARK: - Local Mode

    func testAnEmptyLocalWorkspaceIsAnsweredFromItsFirstFrame() {
        // The regression a sync-stamp-only first-answer term would have caused,
        // and the correction this change had to make to the audit it came from.
        // Local Mode has no server and never records a successful sync — three
        // separate places zero `lastSuccessfulSyncEpochMs` while it is on — so a
        // stamp used alone is false there for the life of the install: an empty
        // local workspace would show a row skeleton that never resolves and never
        // show "No tasks" at all. `feedFirstAnswerLanded(in:)` reads the mode
        // FIRST, which makes `firstAnswerLanded` true from the first frame, which
        // is this case.
        //
        // Stated here as the value that function must produce, since the function
        // itself reads an `AppContainer` and is not a pure one.
        XCTAssertEqual(
            feedAnswer(storeRead: true, rowsEmpty: true, firstAnswerLanded: true),
            .empty
        )
    }

    // MARK: - Arrival

    func testAnArrivalStillTakesTheEmptyStateAway() {
        // The fix must not be "hold the picture no matter what". `rowsEmpty` is a
        // term, so a task landing moves the answer on the frame it lands — both
        // before a first sync has been recorded and after one.
        XCTAssertEqual(
            feedAnswer(storeRead: true, rowsEmpty: false, firstAnswerLanded: true),
            .populated
        )
        XCTAssertEqual(
            feedAnswer(storeRead: true, rowsEmpty: false, firstAnswerLanded: false),
            .populated
        )
    }

    // MARK: - The whole of it

    func testEveryCombinationResolvesToExactlyOneOfTheThreeStates() {
        // A truth table rather than a sampling, because eight rows is small
        // enough to write out and the whole claim of this function is that there
        // are only three answers — with no fourth "we are busy" one hiding in the
        // middle, which is what `isLoading` was smuggling in.
        let expected: [(storeRead: Bool, rowsEmpty: Bool, firstAnswerLanded: Bool, answer: FeedAnswer)] = [
            (false, false, false, .awaitingFirst),
            (false, false, true, .awaitingFirst),
            (false, true, false, .awaitingFirst),
            (false, true, true, .awaitingFirst),
            (true, false, false, .populated),
            (true, false, true, .populated),
            (true, true, false, .awaitingFirst),
            (true, true, true, .empty),
        ]
        for row in expected {
            XCTAssertEqual(
                feedAnswer(
                    storeRead: row.storeRead,
                    rowsEmpty: row.rowsEmpty,
                    firstAnswerLanded: row.firstAnswerLanded
                ),
                row.answer,
                "storeRead=\(row.storeRead) rowsEmpty=\(row.rowsEmpty) firstAnswerLanded=\(row.firstAnswerLanded)"
            )
        }
    }
}
