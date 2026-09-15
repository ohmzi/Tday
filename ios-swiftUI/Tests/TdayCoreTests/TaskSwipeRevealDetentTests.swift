import SwiftUI
import XCTest

#if SWIFT_PACKAGE
@testable import TdayCore
#else
@testable import Tday
#endif

/// The task row's reveal detent, driven the way a finger drives it: one update at a time,
/// each one landing on the state the one before it left.
///
/// A haptic cannot be felt by anything this repository runs — no device, no simulator, and no
/// Swift toolchain on the machine the app is written on — so what is provable is the decision
/// rather than the buzz, and the decision is the whole of the feature. `TaskSwipeRevealDetent`
/// answers *where*; the counting below is *how often*, which is the half that goes wrong.
///
/// Endpoint assertions cannot see the defect this exists to stop. "At 100 points of travel the
/// row is committed open" is true of every wrong version of this too, including the one that
/// asks the same question on every frame and buzzes on every frame it is true — a finger
/// resting a hair past the detent, or jiggling either side of it, turning a detent into a
/// rattle. Only a sequence catches that, so every case below is a sequence: `touchDown`, some
/// number of `drag`s, a `lift`, and one number at the end. The four-step hold in
/// `testCrossingTheDetentIsOneBuzzHoweverLongTheFingerRestsOnIt` is this file's
/// `RootFeedDockCollapseTests` third case — the one the test is for.
///
/// Expectations are pinned as literals rather than re-derived from `openThresholdFraction` and
/// `openVelocityThreshold`. A test that computes its inputs from the constants under test
/// agrees with them whatever they become, and the first of these two is a commit rule the user
/// can feel and the second is what opens the row on a flick.
///
/// **The seam, named.** The fire-once flag lives on
/// `HorizontalSwipePanObserver.Coordinator`, inside a `private struct`, behind an `@objc`
/// method that takes a `UIPanGestureRecognizer` — unreachable from here at any price short of
/// a UI test on a simulator. So `RowUnderAFinger` below transcribes the three lines of
/// `handlePan` that touch that flag, one method per gesture phase, and what this file proves
/// is the rule rather than the wiring. Change one of those three lines and these tests stay
/// green; they are three lines of one `switch`, sitting together, each with the comment saying
/// which arm it is.
final class TaskSwipeRevealDetentTests: XCTestCase {

    /// The narrowest row in the app: three pills at 76 points each. Its detent is 72.96
    /// points of travel, which is why every number below sits either side of 73 and not of 72.
    private let threePillRow: CGFloat = 228

    /// The row's reveal haptic, driven through the three gesture phases that decide it.
    ///
    /// `buzzes` is the only output that matters. The offset is carried because the rule is
    /// path-dependent: what a drag update does depends on where the previous one left the row
    /// and on whether this open-cycle has already spent its buzz.
    private final class RowUnderAFinger {
        private let revealWidth: CGFloat
        private var hasFiredRevealDetent = false
        private var dragStartOffsetX: CGFloat = 0

        private(set) var offsetX: CGFloat
        private(set) var buzzes = 0

        init(revealWidth: CGFloat, restingAt offsetX: CGFloat = 0) {
            self.revealWidth = revealWidth
            self.offsetX = offsetX
        }

        /// `.began` — the flag is seeded from wherever the row already is, which is what
        /// keeps a gesture that starts on an open row silent without any close path having to
        /// remember anything.
        func touchDown() {
            dragStartOffsetX = offsetX
            hasFiredRevealDetent = TaskSwipeRevealDetent.isCommittedOpen(
                offsetX: offsetX,
                revealWidth: revealWidth
            )
        }

        /// `.changed` — arm A. The clamp is the row's own overdrag limit, applied before the
        /// question is asked for the same reason it is in the app: where the finger has put
        /// the row is where the row is.
        ///
        /// The app's `.changed` splits a positive proposal into its own branch to keep the
        /// `openRowID` bookkeeping straight; both branches leave the row at the same offset
        /// and neither can fire from the closed side, so one method covers them.
        func drag(to proposed: CGFloat) {
            offsetX = max(-revealWidth * 1.12, min(0, proposed))
            guard !hasFiredRevealDetent,
                  TaskSwipeRevealDetent.isCommittedOpen(offsetX: offsetX, revealWidth: revealWidth)
            else {
                return
            }
            hasFiredRevealDetent = true
            buzzes += 1
        }

        /// `.changed`, driven the way the recognizer drives it: a translation measured from
        /// where the finger went DOWN, applied to where the row already WAS.
        ///
        /// The seam the round trip below is about. `.began` seeds `dragStartOffsetX` from
        /// `offsetX` for exactly this reason, and a seed of zero passes every endpoint assertion
        /// about closing while making the first update of every drag on an open row a
        /// `revealWidth`-sized jump to the right of home. `drag(to:)` above takes the composed
        /// offset because the haptic cases care about where the row ends up; this one takes the
        /// finger's own number because the close cares about how it got there.
        func dragBy(translationX: CGFloat) {
            drag(to: dragStartOffsetX + translationX)
        }

        /// `.ended` — arm B, then the settle. Returns whether the row opened, so a case can
        /// assert that a buzz and an open went together rather than asserting only one of them.
        @discardableResult
        func lift(velocityX: CGFloat) -> Bool {
            let opens = TaskSwipeRevealDetent.shouldOpen(
                offsetX: offsetX,
                velocityX: velocityX,
                revealWidth: revealWidth
            )
            if opens && !hasFiredRevealDetent {
                hasFiredRevealDetent = true
                buzzes += 1
            }
            offsetX = opens ? -revealWidth : 0
            return opens
        }
    }

    private func makeRow(restingAt offsetX: CGFloat = 0) -> RowUnderAFinger {
        RowUnderAFinger(revealWidth: threePillRow, restingAt: offsetX)
    }

    func testTheTwoNumbersAreTheOnesTheRowAlreadyOpenedOn() {
        XCTAssertEqual(TaskSwipeRevealDetent.openThresholdFraction, 0.32)
        XCTAssertEqual(TaskSwipeRevealDetent.openVelocityThreshold, -180)
    }

    /// The claim that makes the buzz honest: what fires it is the release rule with the
    /// velocity term at zero, not a threshold of its own. Swept rather than spot-checked,
    /// because the two could agree at one offset and disagree by a point either side of the
    /// boundary — which is exactly where a second, nearly-equal constant would hide.
    func testTheDetentIsTheReleaseRuleWithTheFingerStandingStill() {
        for offsetX in stride(from: CGFloat(0), through: -260, by: -1) {
            XCTAssertEqual(
                TaskSwipeRevealDetent.shouldOpen(
                    offsetX: offsetX,
                    velocityX: 0,
                    revealWidth: threePillRow
                ),
                TaskSwipeRevealDetent.isCommittedOpen(
                    offsetX: offsetX,
                    revealWidth: threePillRow
                ),
                "at \(offsetX) points the detent and a standing-still release disagree"
            )
        }
    }

    /// The case this file exists for. One crossing, then a second of resting on the boundary
    /// and a jiggle either side of it, then the rest of the travel — and one buzz for all of
    /// it. A per-update comparison with no memory answers this with seven: one for every
    /// update spent past the boundary, and one more at the release.
    func testCrossingTheDetentIsOneBuzzHoweverLongTheFingerRestsOnIt() {
        let row = makeRow()
        row.touchDown()

        row.drag(to: -20)
        row.drag(to: -72)
        XCTAssertEqual(row.buzzes, 0, "72 points is short of the 72.96 the release opens on")

        row.drag(to: -74)
        XCTAssertEqual(row.buzzes, 1)

        row.drag(to: -74)
        row.drag(to: -73)
        row.drag(to: -74)
        row.drag(to: -120)
        row.drag(to: -228)
        XCTAssertEqual(row.buzzes, 1)

        XCTAssertTrue(row.lift(velocityX: 0))
        XCTAssertEqual(row.buzzes, 1, "the release adds nothing to a detent that already fired")
    }

    /// Past, back inside the row's own edge, and past again — all without the finger leaving
    /// the glass. One gesture, one reveal. The flag is the only thing that answers this: the
    /// second crossing is indistinguishable from the first by offset alone.
    func testPastAndBackAndPastInsideOneGestureIsStillOneReveal() {
        let row = makeRow()
        row.touchDown()

        row.drag(to: -100)
        row.drag(to: -10)
        row.drag(to: 40)
        row.drag(to: -100)

        XCTAssertEqual(row.buzzes, 1)
        XCTAssertTrue(row.lift(velocityX: 0))
        XCTAssertEqual(row.buzzes, 1)
    }

    /// Arm B, and the reason there is one. A 20-point flick at 1500 points a second opens the
    /// row without coming within 50 points of the detent, so a detent-only haptic would leave
    /// the fastest and most deliberate swipe in the app as the only silent one.
    func testAFlickThatOpensTheRowWithoutReachingTheDetentBuzzesOnceAtTheRelease() {
        let row = makeRow()
        row.touchDown()

        row.drag(to: -20)
        XCTAssertEqual(row.buzzes, 0, "the detent is 73 points away and the finger is at 20")

        XCTAssertTrue(row.lift(velocityX: -1500))
        XCTAssertEqual(row.buzzes, 1)
        XCTAssertEqual(row.offsetX, -threePillRow)
    }

    /// A drag that gives up, and a flick too slow to carry: the row comes home and nothing is
    /// uncovered, so nothing is announced. -100 points a second is a finger drifting to a
    /// stop, on the open side of zero and still short of the -180 that commits.
    func testAReleaseThatLandsTheRowClosedSaysNothing() {
        let drifted = makeRow()
        drifted.touchDown()
        drifted.drag(to: -20)
        XCTAssertFalse(drifted.lift(velocityX: -100))
        XCTAssertEqual(drifted.buzzes, 0)
        XCTAssertEqual(drifted.offsetX, 0)

        let abandoned = makeRow()
        abandoned.touchDown()
        abandoned.drag(to: -60)
        abandoned.drag(to: -4)
        XCTAssertFalse(abandoned.lift(velocityX: 0))
        XCTAssertEqual(abandoned.buzzes, 0)
    }

    /// The one honest cost, pinned rather than left for a device to discover: cross the
    /// detent, drag back, release closed, and a reveal was felt that did not happen. That is
    /// what a detent on a physical control does, and the alternative — silence until the row
    /// settles — costs the feature its point. Written down here so that a change which
    /// "fixes" it has to come and delete a test that says it is deliberate.
    func testCrossingTheDetentAndThenReleasingClosedKeepsTheBuzzItAlreadySpent() {
        let row = makeRow()
        row.touchDown()

        row.drag(to: -100)
        XCTAssertEqual(row.buzzes, 1)

        row.drag(to: -8)
        XCTAssertFalse(row.lift(velocityX: 0))
        XCTAssertEqual(row.buzzes, 1, "the buzz was already felt; the release cannot take it back")
        XCTAssertEqual(row.offsetX, 0)
    }

    /// A row that has come home is a row that can uncover its actions again, so the next
    /// swipe is a new reveal. This is what the seed at `.began` buys: nothing on any of the
    /// four close paths has to remember to re-arm anything, because a shut row begins its
    /// next gesture at 0.
    func testTheNextSwipeOnAClosedRowRevealsAgain() {
        let row = makeRow()

        row.touchDown()
        row.drag(to: -100)
        row.drag(to: -6)
        XCTAssertFalse(row.lift(velocityX: 0))
        XCTAssertEqual(row.buzzes, 1)

        row.touchDown()
        row.drag(to: -100)
        XCTAssertEqual(row.buzzes, 2)
        XCTAssertTrue(row.lift(velocityX: 0))
        XCTAssertEqual(row.buzzes, 2)
    }

    /// Dragging a row whose actions are already out uncovers nothing — further open, partway
    /// back and out again, or flicked at the end. All of it is silent, and the seed is the
    /// whole mechanism: the gesture starts committed, so it starts spent.
    func testAGestureThatBeginsOnAnOpenRowUncoversNothing() {
        let row = makeRow(restingAt: -threePillRow)
        row.touchDown()

        row.drag(to: -260)
        row.drag(to: -150)
        row.drag(to: -20)
        row.drag(to: -240)

        XCTAssertEqual(row.buzzes, 0)
        XCTAssertTrue(row.lift(velocityX: -1500))
        XCTAssertEqual(row.buzzes, 0)
    }

    /// The round trip the user actually asked for: a row whose actions are out, slid back to the
    /// right until it shuts.
    ///
    /// Asserted per update rather than at the endpoints, because the endpoints are true of the
    /// broken version too. A `.changed` that used the translation alone — rather than
    /// `dragStartOffsetX + translation.x` — would send an open row a full reveal width to the
    /// right of home on the first frame and snap back; the row would still close, and the only
    /// thing that would tell you is the jump. So the numbers below are where the row is after
    /// each movement of the finger, and the first one is the assertion that matters.
    func testDraggingAnOpenRowBackToTheRightClosesItWithoutJumping() {
        let row = makeRow(restingAt: -threePillRow)
        row.touchDown()

        row.dragBy(translationX: 10)
        XCTAssertEqual(row.offsetX, -218, "the row follows the thumb from where it was, not from home")

        row.dragBy(translationX: 100)
        XCTAssertEqual(row.offsetX, -128)

        row.dragBy(translationX: 160)
        XCTAssertEqual(row.offsetX, -68, "inside the 72.96-point detent, so a release here shuts it")

        XCTAssertFalse(row.lift(velocityX: 400))
        XCTAssertEqual(row.offsetX, 0)
        XCTAssertEqual(row.buzzes, 0, "a close uncovers nothing, so it announces nothing")
    }

    /// The other half of the same gesture, and the reason the close is asymmetric: the row has to
    /// come 68% of the reveal back before position alone will shut it. A finger that gives up
    /// part-way leaves the actions out rather than half-out.
    func testDraggingAnOpenRowPartWayBackLeavesItOpen() {
        let row = makeRow(restingAt: -threePillRow)
        row.touchDown()

        row.dragBy(translationX: 120)
        XCTAssertEqual(row.offsetX, -108, "still 35 points past the detent")

        XCTAssertTrue(row.lift(velocityX: 0))
        XCTAssertEqual(row.offsetX, -threePillRow)
        XCTAssertEqual(row.buzzes, 0, "the gesture began on an open row, so it began spent")
    }

    /// The tap hint travels 28 points and springs back. That is a tease rather than a
    /// commitment, and this pins the inequality that makes it one on the narrowest row there
    /// is: 28 against a 72.96-point detent, two numbers written in different files and
    /// load-bearing by coincidence. The tap's own `HapticManager.reveal()` is its whole
    /// haptic; were the hint ever to travel past the detent, a pan landing mid-hint would
    /// begin already counted as open and the swipe that followed it would go silent.
    func testTheTapHintStopsShortOfCommittingTheRow() {
        XCTAssertFalse(
            TaskSwipeRevealDetent.isCommittedOpen(offsetX: -28, revealWidth: threePillRow)
        )
    }
}
