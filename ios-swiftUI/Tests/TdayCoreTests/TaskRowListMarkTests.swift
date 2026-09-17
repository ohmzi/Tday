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

/// `tdayResolvedRowList` — WHICH list a completed row's trailing mark stands for.
///
/// The sibling of the suite above, and the second half of the same user report. The mark on the
/// Completed screen was a constant `tray.fill` for every row, so it said "Inbox" whatever list
/// the task was in; the predicate above was never the problem there because the Completed screen
/// is a mixed feed and always wants a mark. What was missing is this: the row had no way to go
/// from its snapshotted `listId`/`listName` to the live list whose `iconKey` the glyph is drawn
/// from.
///
/// The cases below are grouped by what they pin, and two of them are the whole point of the
/// function existing rather than being two lines at the call site:
///  - a Floater and a scheduled list sharing a NAME must each resolve to their own list, which is
///    the case a namespace-blind lookup gets wrong;
///  - a completed Floater whose list was deleted (`listId` nil) must still find its list by name
///    inside the FLOATER namespace, not the scheduled one — the case a "resolve against
///    scheduledLists and fall back" implementation gets wrong by finding the wrong list.
///
/// The same caveat as the suite above applies: there is no Swift toolchain on the machine this
/// was written on, so `xctest` in CI is this file's gate and nothing here was run locally. What
/// WAS done locally is the half that needs no Swift — the resolver's body was transcribed and
/// every case below evaluated against the transcription, then perturbed. Dropping the
/// `isFloater` branch (searching only `scheduledLists`) fails
/// `testACompletedFloaterResolvesAgainstFloaterListsNotScheduledOnes` and
/// `testADeletedFloaterListStillResolvesByNameInsideTheFloaterNamespace`; dropping the id pass
/// fails `testTheIdWinsOverANameThatHasSinceChanged`; making the name match case-sensitive fails
/// `testTheNameMatchIgnoresCaseAndSurroundingWhitespace`.
final class CompletedRowListMarkTests: XCTestCase {

    // MARK: - Fixtures

    private func list(_ id: String, _ name: String, iconKey: String? = nil) -> ListSummary {
        ListSummary(
            id: id,
            name: name,
            color: nil,
            iconKey: iconKey,
            todoCount: 0,
            updatedAt: nil,
            createdAt: nil
        )
    }

    private func completed(
        listId: String?,
        listName: String?,
        isFloater: Bool
    ) -> CompletedItem {
        CompletedItem(
            id: "completed-1",
            originalTodoId: "todo-1",
            title: "Task",
            description: nil,
            priority: "none",
            due: nil,
            completedAt: nil,
            rrule: nil,
            instanceDate: nil,
            listId: listId,
            listName: listName,
            listColor: nil,
            isFloater: isFloater
        )
    }

    // MARK: - The namespace is the row's own

    func testAScheduledRowResolvesAgainstScheduledLists() {
        let resolved = tdayResolvedRowList(
            for: completed(listId: "scheduled-1", listName: "Scheduled list", isFloater: false),
            scheduledLists: [list("scheduled-1", "Scheduled list", iconKey: "briefcase")],
            floaterLists: [list("floater-1", "Floater list", iconKey: "leaf")]
        )
        XCTAssertEqual(resolved?.id, "scheduled-1")
        XCTAssertEqual(resolved?.iconKey, "briefcase")
    }

    func testACompletedFloaterResolvesAgainstFloaterListsNotScheduledOnes() {
        // The report, in one line: a completed Floater's mark must come from the Floater list,
        // so the glyph is that list's own `iconKey` rather than anything the scheduled side has.
        let resolved = tdayResolvedRowList(
            for: completed(listId: "floater-1", listName: "Shared name", isFloater: true),
            scheduledLists: [list("scheduled-1", "Shared name", iconKey: "briefcase")],
            floaterLists: [list("floater-1", "Shared name", iconKey: "leaf")]
        )
        XCTAssertEqual(resolved?.id, "floater-1")
        XCTAssertEqual(resolved?.iconKey, "leaf")
    }

    func testTwoListsSharingANameResolveToTheirOwnNamespace() {
        // The case a namespace-blind name lookup cannot get right, stated as a pair so that a
        // fix which resolves everything against one collection fails one of the two halves.
        let scheduled = list("scheduled-1", "Reading", iconKey: "book")
        let floater = list("floater-1", "Reading", iconKey: "leaf")

        let scheduledRow = tdayResolvedRowList(
            for: completed(listId: nil, listName: "Reading", isFloater: false),
            scheduledLists: [scheduled],
            floaterLists: [floater]
        )
        let floaterRow = tdayResolvedRowList(
            for: completed(listId: nil, listName: "Reading", isFloater: true),
            scheduledLists: [scheduled],
            floaterLists: [floater]
        )

        XCTAssertEqual(scheduledRow?.iconKey, "book")
        XCTAssertEqual(floaterRow?.iconKey, "leaf")
    }

    // MARK: - Which key wins

    func testTheIdWinsOverANameThatHasSinceChanged() {
        // The list was renamed after the task was completed, so the snapshot's name no longer
        // matches anything. The id still does, and the mark must follow the list.
        let resolved = tdayResolvedRowList(
            for: completed(listId: "list-1", listName: "Old name", isFloater: false),
            scheduledLists: [list("list-1", "New name", iconKey: "briefcase")],
            floaterLists: []
        )
        XCTAssertEqual(resolved?.id, "list-1")
    }

    func testADeletedFloaterListStillResolvesByNameInsideTheFloaterNamespace() {
        // `ON DELETE SET NULL` nulls a completed Floater's listID but keeps the denormalised
        // name. The row must fall through to that name — and only inside the Floater namespace,
        // which is what the assertion on the icon key proves.
        let resolved = tdayResolvedRowList(
            for: completed(listId: nil, listName: "Errands", isFloater: true),
            scheduledLists: [list("scheduled-1", "Errands", iconKey: "briefcase")],
            floaterLists: [list("floater-1", "Errands", iconKey: "leaf")]
        )
        XCTAssertEqual(resolved?.iconKey, "leaf")
    }

    func testTheNameMatchIgnoresCaseAndSurroundingWhitespace() {
        // Android's `resolveListSummary` trims and lowercases, and the two clients must agree on
        // which lists a snapshot can resolve against.
        let resolved = tdayResolvedRowList(
            for: completed(listId: nil, listName: "  eRrAnDs\n", isFloater: true),
            scheduledLists: [],
            floaterLists: [list("floater-1", "Errands", iconKey: "leaf")]
        )
        XCTAssertEqual(resolved?.id, "floater-1")
    }

    func testAnIdThatMatchesNothingFallsThroughToTheName() {
        // A stale id — the list was deleted and recreated, so the snapshot's id is gone but its
        // name is current. The id pass must not veto the name pass.
        let resolved = tdayResolvedRowList(
            for: completed(listId: "list-gone", listName: "Errands", isFloater: true),
            scheduledLists: [],
            floaterLists: [list("floater-1", "Errands", iconKey: "leaf")]
        )
        XCTAssertEqual(resolved?.id, "floater-1")
    }

    // MARK: - Nothing to resolve

    func testARowWithNeitherIdNorNameResolvesToNothing() {
        XCTAssertNil(
            tdayResolvedRowList(
                for: completed(listId: nil, listName: nil, isFloater: false),
                scheduledLists: [list("list-1", "Errands")],
                floaterLists: []
            )
        )
        XCTAssertNil(
            tdayResolvedRowList(
                for: completed(listId: "", listName: "   ", isFloater: false),
                scheduledLists: [list("list-1", "Errands")],
                floaterLists: []
            )
        )
    }

    func testANameThatIsInNoListResolvesToNothing() {
        // The glyph then falls back to the inbox exactly as `tdayLucideListAsset` decides —
        // the resolver's job is to say "no list", not to invent one.
        XCTAssertNil(
            tdayResolvedRowList(
                for: completed(listId: nil, listName: "Errands", isFloater: false),
                scheduledLists: [],
                floaterLists: [list("floater-1", "Errands")]
            )
        )
    }
}
