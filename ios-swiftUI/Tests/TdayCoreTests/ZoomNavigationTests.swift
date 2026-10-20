import Foundation
import XCTest

#if SWIFT_PACKAGE
@testable import TdayCore
#else
@testable import Tday
#endif

/// `AppRoute.zoomSourceID` — the table both ends of the zoom read.
///
/// Nothing here can watch a transition play. The zoom is two iOS 18 modifiers on two
/// views two files apart, and what decides whether they find each other is a string:
/// the tile publishes one with `.matchedTransitionSource`, the destination asks for
/// one with `.navigationTransition(.zoom(sourceID:in:))`, and a mismatch is not an
/// error anywhere — SwiftUI simply falls back to the stock push. That is the failure
/// this file is for, because it has no symptom short of a device: no warning, no
/// crash, no log, just a transition that silently stopped being the feature.
///
/// The switch in `ZoomNavigation.swift` is exhaustive, so a case that is renamed or
/// removed is a compile error rather than a test failure. What a compiler cannot say
/// is that the right cases are the ones with ids, or that an origin is passed where a
/// tile was pressed and `nil` where nothing was — a new route that talks its way into
/// the table, or a tile re-pointed at a route that has none, both compile. So the ids
/// are asserted against a written-out list rather than against each other: the list is
/// the contract, and changing it has to be a deliberate edit in a file that says why.
final class ZoomNavigationTests: XCTestCase {

    /// Every case, with the argument-carrying ones sampled on both sides of whatever
    /// their argument decides: both highlight ids for `.allTodos`, both origins AND `nil`
    /// for the four routes that carry an origin (`nil` is the sidebar, the search results
    /// and the deep links — the arrivals that have no rectangle to zoom out of), and two
    /// different list ids so the per-list ids are genuinely two strings. Written out
    /// because `AppRoute` carries associated values and cannot be `CaseIterable`.
    private static let everyRoute: [AppRoute] = [
        .scheduledTaskHome,
        .todayTodos(origin: nil),
        .todayTodos(origin: .scheduledBoard),
        .createTodayTodo,
        .createFloaterTodo,
        .overdueTodos,
        .scheduledTodos,
        .allTodos(highlightTodoId: nil),
        .allTodos(highlightTodoId: "abc"),
        .priorityTodos,
        .floaterTaskHome,
        .floaterListTodos(listId: "l1", listName: "Groceries", origin: nil),
        .floaterListTodos(listId: "l1", listName: "Groceries", origin: .floaterFeed),
        .listTodos(listId: "l2", listName: "Work", origin: nil),
        .listTodos(listId: "l2", listName: "Work", origin: .scheduledBoard),
        .completed(origin: nil),
        .completed(origin: .scheduledBoard),
        .completed(origin: .floaterFeed),
        .calendar,
        .settings,
        .latestRelease,
        .helpGuide(topic: nil),
        .helpGuide(topic: "getting-started"),
        .morningSweep,
        .forgotPassword,
    ]

    // MARK: - The table

    /// The whole table, spelled out. A tile re-pointed at a neighbouring route keeps the
    /// same number of ids and still lands here, because the id it now hands over is the
    /// wrong one for the screen that arrives.
    func testTheHomeTileRoutesCarryTheIdsTheirTilesPublish() {
        XCTAssertEqual(AppRoute.scheduledTodos.zoomSourceID, "home-tile.scheduled")
        XCTAssertEqual(AppRoute.priorityTodos.zoomSourceID, "home-tile.priority")
        XCTAssertEqual(AppRoute.overdueTodos.zoomSourceID, "home-tile.overdue")
        XCTAssertEqual(AppRoute.allTodos(highlightTodoId: nil).zoomSourceID, "home-tile.all")
        XCTAssertEqual(AppRoute.calendar.zoomSourceID, "home-tile.calendar")

        // The four surfaces this table was extended to cover, each read through the origin
        // its own tile passes. The scheduled board's Completed tile keeps the id it always
        // had; the Anytime feed's is a second, distinct one.
        XCTAssertEqual(AppRoute.todayTodos(origin: .scheduledBoard).zoomSourceID, "home-tile.today")
        XCTAssertEqual(
            AppRoute.listTodos(listId: "l2", listName: "Work", origin: .scheduledBoard).zoomSourceID,
            "home-tile.list.l2"
        )
        XCTAssertEqual(
            AppRoute.floaterListTodos(listId: "l1", listName: "Groceries", origin: .floaterFeed).zoomSourceID,
            "floater-tile.list.l1"
        )
        XCTAssertEqual(AppRoute.completed(origin: .scheduledBoard).zoomSourceID, "home-tile.completed")
        XCTAssertEqual(AppRoute.completed(origin: .floaterFeed).zoomSourceID, "floater-tile.completed")
    }

    /// Ten, and only ten. A route with an id no tile in the tree publishes is a destination
    /// that would try to grow out of a source that is not on screen — which is the stock
    /// push with a `matchedTransitionSource` lookup in front of it, and looks on a device
    /// exactly like the feature not working.
    func testNothingButTheHomeTileSurfacesZoom() {
        let withIDs = Self.everyRoute.filter { $0.zoomSourceID != nil }
        XCTAssertEqual(withIDs.count, 10, "routes carrying a zoom source id")
    }

    /// Two tiles sharing an id would match the wrong rectangle, and which one wins is
    /// SwiftUI's business rather than this app's. The two Completed ids are the pair that
    /// makes this rule load-bearing rather than incidental: both root feeds are mounted
    /// together during a tab crossfade, so one id shared there is two views in one
    /// namespace. The two list ids are sampled with different list ids on purpose — the
    /// same id on both sides would still pass on the differing prefixes and would hide a
    /// prefix typo in exactly the string this rule exists to keep apart.
    func testTheZoomIDsAreDistinct() {
        let ids = Self.everyRoute.compactMap(\.zoomSourceID)
        XCTAssertEqual(Set(ids).count, ids.count, "duplicate zoom source ids: \(ids)")
    }

    // MARK: - The arrivals that are not a tile press

    /// The one case whose argument is not an origin and changes the answer. A highlight id
    /// means the push came from the home screen's search results or from a deep link; the
    /// All tile is on screen either way, so a zoom here would grow the screen out of a
    /// rectangle nobody pressed — a transition telling the user something that did not happen.
    func testAHighlightedAllTodosArrivalDoesNotZoom() {
        XCTAssertNil(AppRoute.allTodos(highlightTodoId: "abc").zoomSourceID)
    }

    /// The arrivals that came from somewhere with no rectangle at all: the header, the dock,
    /// the sidebar and the home screen's search results, and the deep links, which reach the
    /// same four screens the tiles do. Every one of the origin-carrying routes says so by
    /// passing `nil`, and `nil` has to answer `nil` — an id here would grow the screen out of
    /// a tile on a screen the user never touched, the same lie `allTodos(highlightTodoId:)`
    /// already refuses.
    func testRoutesWithNoTileDoNotZoom() {
        XCTAssertNil(AppRoute.settings.zoomSourceID)
        XCTAssertNil(AppRoute.helpGuide(topic: nil).zoomSourceID)
        XCTAssertNil(AppRoute.helpGuide(topic: "getting-started").zoomSourceID)
        XCTAssertNil(AppRoute.floaterTaskHome.zoomSourceID)

        XCTAssertNil(AppRoute.todayTodos(origin: nil).zoomSourceID)
        XCTAssertNil(AppRoute.listTodos(listId: "l2", listName: "Work", origin: nil).zoomSourceID)
        XCTAssertNil(AppRoute.floaterListTodos(listId: "l1", listName: "Groceries", origin: nil).zoomSourceID)
        XCTAssertNil(AppRoute.completed(origin: nil).zoomSourceID)
    }
}
