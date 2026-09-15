import XCTest

#if SWIFT_PACKAGE
@testable import TdayCore
#else
@testable import Tday
#endif

/// The dismissal decision, pinned as a truth table the three clients share.
///
/// `TaskSwipeDismissPolicy.shouldClose` is two comparisons and it would be reasonable to ask why
/// it is tested at all. The answer is that the three clients each had to be told the same four
/// rows, in three languages, with no shared runtime and no device on any gate in this repository:
/// Android carries `shouldCloseSwipeRow` with `TaskSwipeDismissPolicyTest` beside it, web answers
/// it from `useSwipeRow`'s own `swipeX`, and this file is iOS's copy of the same table. A
/// decision written down three times drifts unless all three are held to the same four
/// assertions, and the first of them is one Android was getting WRONG in shipped code until the
/// change this arrived with.
///
/// The row that matters is `(nil, "a", open) → true`. Android's rows used to ask
/// `openSwipeTaskId != null && …`, so a free slot meant "nobody moves" rather than "everybody
/// closes" — and since every dismissal added in this change is a write of `nil`, all of them
/// would have been silent no-ops. iOS never had that clause, which is exactly why it is pinned
/// here: the absence is load-bearing and invisible.
final class TaskSwipeDismissPolicyTests: XCTestCase {

    /// A free slot closes every open row. Nothing holds it, so nothing is being worked.
    func testAFreeSlotClosesAnOpenRow() {
        XCTAssertTrue(
            TaskSwipeDismissPolicy.shouldClose(openRowID: nil, rowID: "a", isOpen: true)
        )
    }

    /// Another row holding the slot closes this one: one row open at a time, which is the rule
    /// on all three clients and the reason the slot is a single optional rather than a set.
    func testAnotherRowHoldingTheSlotClosesThisOne() {
        XCTAssertTrue(
            TaskSwipeDismissPolicy.shouldClose(openRowID: "b", rowID: "a", isOpen: true)
        )
    }

    /// The row holding the slot is the row the user is working. It never closes itself out from
    /// under its own finger, and this is the single most important negative case in the feature:
    /// no interceptor may take a row away from the hand that is holding it.
    func testTheRowHoldingTheSlotStaysOpen() {
        XCTAssertFalse(
            TaskSwipeDismissPolicy.shouldClose(openRowID: "a", rowID: "a", isOpen: true)
        )
    }

    /// A row already home has nothing to close. Without this term every dismissal would start a
    /// spring to where the row already is, once for every realized row on the screen — and a
    /// dismissal happens on every outside tap and at the start of every scroll.
    func testAClosedRowNeverCloses() {
        for openRowID in [nil, "a", "b"] as [String?] {
            XCTAssertFalse(
                TaskSwipeDismissPolicy.shouldClose(openRowID: openRowID, rowID: "a", isOpen: false),
                "a closed row was asked to close with the slot held by \(openRowID ?? "nobody")"
            )
        }
    }

    /// Identity is by id and by nothing else. The slot is written from four different places —
    /// a pan's `.began`, a tap recognizer on the window, a scroll's first frame, a screen's
    /// `.onDisappear` — and the first three of those write `nil` while the row is somewhere the
    /// writer cannot see. A near-miss id is a row that stays open with no way to shut it.
    func testOnlyAnExactIdMatchHoldsTheRowOpen() {
        for openRowID in ["a ", " a", "A", "ab", ""] {
            XCTAssertTrue(
                TaskSwipeDismissPolicy.shouldClose(openRowID: openRowID, rowID: "a", isOpen: true),
                "\"\(openRowID)\" was treated as the row \"a\""
            )
        }
    }
}
