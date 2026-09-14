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
/// is that the right SIX cases are the ones with ids — a new route that talks its way
/// into the table, or a tile re-pointed at a route that has none, both compile. So
/// the ids are asserted against a written-out list rather than against each other:
/// the list is the contract, and changing it has to be a deliberate edit in a file
/// that says why.
final class ZoomNavigationTests: XCTestCase {

    /// Every case, with the argument-carrying ones sampled on both sides of whatever
    /// their argument decides. Written out because `AppRoute` carries associated
    /// values and cannot be `CaseIterable`.
    private static let everyRoute: [AppRoute] = [
        .scheduledTaskHome,
        .todayTodos,
        .createTodayTodo,
        .createFloaterTodo,
        .overdueTodos,
        .scheduledTodos,
        .allTodos(highlightTodoId: nil),
        .allTodos(highlightTodoId: "abc"),
        .priorityTodos,
        .floaterTaskHome,
        .floaterListTodos(listId: "l1", listName: "Groceries"),
        .listTodos(listId: "l2", listName: "Work"),
        .completed,
        .calendar,
        .settings,
        .latestRelease,
        .helpGuide(topic: nil),
        .helpGuide(topic: "getting-started"),
        .morningSweep,
        .forgotPassword,
    ]

    // MARK: - The six

    /// The whole table, spelled out. A tile re-pointed at a neighbouring route keeps
    /// six ids and still lands here, because the id it now hands over is the wrong
    /// one for the screen that arrives.
    func testTheSixTileRoutesCarryTheIdsTheirTilesPublish() {
        XCTAssertEqual(AppRoute.scheduledTodos.zoomSourceID, "home-tile.scheduled")
        XCTAssertEqual(AppRoute.priorityTodos.zoomSourceID, "home-tile.priority")
        XCTAssertEqual(AppRoute.overdueTodos.zoomSourceID, "home-tile.overdue")
        XCTAssertEqual(AppRoute.allTodos(highlightTodoId: nil).zoomSourceID, "home-tile.all")
        XCTAssertEqual(AppRoute.completed.zoomSourceID, "home-tile.completed")
        XCTAssertEqual(AppRoute.calendar.zoomSourceID, "home-tile.calendar")
    }

    /// Six, and only six. A seventh route with an id is a destination that would try
    /// to grow out of a source no view in the tree publishes — which is the stock push
    /// with a `matchedTransitionSource` lookup in front of it, and looks on a device
    /// exactly like the feature not working.
    func testNothingButTheSixHomeTilesZooms() {
        let withIDs = Self.everyRoute.filter { $0.zoomSourceID != nil }
        XCTAssertEqual(withIDs.count, 6, "routes carrying a zoom source id")
    }

    /// Two tiles sharing an id would match the wrong rectangle, and which one wins is
    /// SwiftUI's business rather than this app's.
    func testTheSixIDsAreDistinct() {
        let ids = Self.everyRoute.compactMap(\.zoomSourceID)
        XCTAssertEqual(Set(ids).count, ids.count, "duplicate zoom source ids: \(ids)")
    }

    // MARK: - The arrivals that are not a tile press

    /// The one case whose argument changes the answer. A highlight id means the push
    /// came from the home screen's search results or from a deep link; the All tile is
    /// on screen either way, so a zoom here would grow the screen out of a rectangle
    /// nobody pressed — a transition telling the user something that did not happen.
    func testAHighlightedAllTodosArrivalDoesNotZoom() {
        XCTAssertNil(AppRoute.allTodos(highlightTodoId: "abc").zoomSourceID)
    }

    /// The routes reached from the header, the dock and the lists section. None of
    /// them is a tile, and none of them has a rectangle to come from.
    func testRoutesWithNoTileDoNotZoom() {
        XCTAssertNil(AppRoute.settings.zoomSourceID)
        XCTAssertNil(AppRoute.helpGuide(topic: nil).zoomSourceID)
        XCTAssertNil(AppRoute.helpGuide(topic: "getting-started").zoomSourceID)
        XCTAssertNil(AppRoute.listTodos(listId: "l2", listName: "Work").zoomSourceID)
        XCTAssertNil(AppRoute.floaterTaskHome.zoomSourceID)
    }
}
