import XCTest

#if SWIFT_PACKAGE
@testable import TdayCore
#else
@testable import Tday
#endif

final class FloaterListVisibilityTests: XCTestCase {
    private let hiddenList = ListSummary(id: "hidden", name: "Someday", color: nil, iconKey: nil, todoCount: 0, updatedAt: nil, createdAt: nil, showOnMainScreen: false)
    private let shownList = ListSummary(id: "shown", name: "Errands", color: nil, iconKey: nil, todoCount: 0, updatedAt: nil, createdAt: nil)

    private func todo(_ id: String, listId: String?) -> TodoItem {
        TodoItem(
            id: id,
            canonicalId: id,
            title: id,
            description: nil,
            priority: "Low",
            due: nil,
            rrule: nil,
            instanceDate: nil,
            pinned: false,
            completed: false,
            listId: listId,
            updatedAt: nil
        )
    }

    func testNoListIsShown() {
        XCTAssertTrue(FloaterMainScreen.isShown(listId: nil, hiddenListIds: ["a"]))
    }

    func testUnknownListIsShown() {
        XCTAssertTrue(FloaterMainScreen.isShown(listId: "b", hiddenListIds: ["a"]))
    }

    func testHiddenListIsNotShown() {
        XCTAssertFalse(FloaterMainScreen.isShown(listId: "a", hiddenListIds: ["a"]))
    }

    func testFeedLeavesOutAHiddenListAndKeepsEverythingElse() {
        let items = [todo("a", listId: "hidden"), todo("b", listId: "shown"), todo("c", listId: nil)]

        let feed = FloaterMainScreen.feedItems(items, lists: [hiddenList, shownList])

        XCTAssertEqual(feed.map(\.id), ["b", "c"])
    }

    func testSearchResultForHiddenTaskOpensItsList() {
        let list = FloaterMainScreen.hiddenList(for: todo("a", listId: "hidden"), lists: [hiddenList, shownList])

        XCTAssertEqual(list?.id, "hidden")
    }

    func testSearchResultInTheFeedStaysInTheFeed() {
        XCTAssertNil(FloaterMainScreen.hiddenList(for: todo("b", listId: "shown"), lists: [hiddenList, shownList]))
    }

    func testNilStoredValueReadsAsShown() {
        let record = CachedFloaterListRecord(
            id: "old",
            name: "Packing",
            color: nil,
            iconKey: nil,
            todoCount: 0,
            updatedAtEpochMs: 0,
            createdAtEpochMs: 0
        )

        XCTAssertNil(record.showOnMainScreen)
        XCTAssertTrue(floaterListFromCache(record).showOnMainScreen)
    }

    func testOldJSONDecodesAsShown() throws {
        let data = Data(#"{"id":"1","name":"x","todoCount":0}"#.utf8)

        let list = try JSONDecoder().decode(ListSummary.self, from: data)

        XCTAssertTrue(list.showOnMainScreen)
    }

    func testQueuedVisibilityMutationSurvivesThePersistenceRoundTrip() {
        let record = PendingMutationRecord(
            mutationId: "m-1",
            kind: .updateFloaterList,
            targetId: "fl-1",
            timestampEpochMs: 1_000,
            title: nil,
            description: nil,
            priority: nil,
            dueEpochMs: nil,
            rrule: nil,
            listId: nil,
            pinned: nil,
            completed: nil,
            instanceDateEpochMs: nil,
            name: "Packing",
            color: nil,
            iconKey: nil,
            showOnMainScreen: false
        )

        let data = try? JSONEncoder().encode(record)
        let decoded = data.flatMap { try? JSONDecoder().decode(PendingMutationRecord.self, from: $0) }

        XCTAssertEqual(decoded?.showOnMainScreen, false)
    }
}
