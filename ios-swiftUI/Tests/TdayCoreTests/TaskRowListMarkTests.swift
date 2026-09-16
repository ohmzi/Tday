import Foundation
import XCTest

#if SWIFT_PACKAGE
@testable import TdayCore
#else
@testable import Tday
#endif

/// `shouldShowListMark` — where a task row's trailing list glyph earns its place.
///
/// The rule it replaced was `listMeta != nil && viewModel.mode != .list`, and it lived as a
/// `let` inside a `View` method, reachable from no test. That is most of why the bug the user
/// reported survived: `.floater` serves both the Anytime HOME feed and one Anytime list's
/// DETAIL, a mode test cannot separate them, and nothing could ever have said so out loud.
/// Lifted out, it is a pure function of two strings and every claim about it is an assertion —
/// the same shape, for the same reason, as `shouldCelebrateEmptyState` in this directory.
///
/// The cases are grouped by the SCREEN each one stands for, because the argument is about
/// screens rather than about strings. Two of them are the whole point:
///  - the Anytime list detail (scope == the row's list) must go dark, which is the report;
///  - the moved row (scope != the row's list) must keep its mark, which is the case a fix
///    written as "suppress the mark on any scoped screen" would lose. That frame — after a
///    task is moved to another list, before the list query catches up — is the one frame
///    where the mark is the most informative thing on the row.
///
/// There is no Swift toolchain on the machine this was written on, so `xctest` in CI is this
/// file's gate and nothing here was run locally. What WAS done locally is the half that needs
/// no Swift: the function's three lines were transcribed and every case below evaluated
/// against the transcription, then perturbed. Dropping the empty-string check on `scopedListId`
/// fails only `mixedFeedWithBlankScopeShowsTheMark`; swapping the final `!=` for `==` fails
/// four; returning true for a nil `rowListId` fails both unfiled cases and nothing else.
final class TaskRowListMarkTests: XCTestCase {

    // MARK: - The screens that already say which list this is

    func testAnytimeListDetailHidesTheMark() {
        // The report, in one line. `.floater` with a list id is one Anytime list's detail;
        // every row was filtered to `list-anytime` before it was composed, so the glyph
        // repeats the screen's own title once per row and says nothing either time.
        XCTAssertFalse(shouldShowListMark(rowListId: "list-anytime", scopedListId: "list-anytime"))
    }

    func testScheduledListDetailHidesTheMark() {
        // `.list` behaved correctly before this change and must still. It is not a second
        // case in the new rule — it is the same one, which is the point of expressing the
        // rule as scope rather than as a mode table.
        XCTAssertFalse(shouldShowListMark(rowListId: "list-scheduled", scopedListId: "list-scheduled"))
    }

    // MARK: - The screens that do not

    func testAnytimeHomeFeedShowsTheMark() {
        // `.floater` with no list id is the Anytime home feed: rows come from every Anytime
        // list and from unfiled floaters, so the mark is the only thing that says which.
        XCTAssertTrue(shouldShowListMark(rowListId: "list-anytime", scopedListId: nil))
    }

    func testMixedFeedWithBlankScopeShowsTheMark() {
        // Today, Overdue, Scheduled, Priority and All are all built with `listId: nil`, but a
        // blank string is the same claim as nil and must not be read as "the screen is scoped
        // to a list whose id is empty" — which would silently blank the mark on five feeds.
        XCTAssertTrue(shouldShowListMark(rowListId: "list-anytime", scopedListId: ""))
    }

    func testARowFromAnotherListKeepsItsMarkOnAScopedScreen() {
        // Mid-move: the task now belongs to `list-b`, the screen is still showing `list-a`.
        // Here the mark is not redundant decoration, it is the explanation.
        XCTAssertTrue(shouldShowListMark(rowListId: "list-b", scopedListId: "list-a"))
    }

    // MARK: - Nothing to mark

    func testAnUnfiledTaskNeverMarks() {
        // No list, no glyph — on a scoped screen and on a mixed feed alike. The old rule got
        // this from `listMeta != nil` at the call site; the new one owns it, so the call site
        // cannot forget it.
        XCTAssertFalse(shouldShowListMark(rowListId: nil, scopedListId: nil))
        XCTAssertFalse(shouldShowListMark(rowListId: nil, scopedListId: "list-a"))
        XCTAssertFalse(shouldShowListMark(rowListId: "", scopedListId: nil))
    }
}
