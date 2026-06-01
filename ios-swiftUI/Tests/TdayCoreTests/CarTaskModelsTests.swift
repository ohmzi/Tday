import XCTest

#if SWIFT_PACKAGE
@testable import TdayCore
#else
@testable import Tday
#endif

final class CarTaskModelsTests: XCTestCase {
    func testTodayModeUsesScheduledTaskContract() {
        XCTAssertEqual(CarTaskMode.today.title, "T'Day")
        XCTAssertEqual(CarTaskMode.today.emptyTitle, "No tasks due today")
        XCTAssertEqual(CarTaskMode.today.todoListMode, .today)
        XCTAssertEqual(CarTaskMode.today.createDeepLink.absoluteString, "tday://todos/create?target=today")

        let payload = CarTaskMode.today.createPayload(
            title: "Oil change",
            now: Date(timeIntervalSince1970: 1_780_000_000)
        )

        XCTAssertEqual(payload.title, "Oil change")
        XCTAssertNotNil(payload.due)
    }

    func testFloaterModeUsesFloaterContract() {
        XCTAssertEqual(CarTaskMode.floater.title, "Floater")
        XCTAssertEqual(CarTaskMode.floater.emptyTitle, "No floater tasks")
        XCTAssertEqual(CarTaskMode.floater.todoListMode, .floater)
        XCTAssertEqual(CarTaskMode.floater.createDeepLink.absoluteString, "tday://todos/create?target=floater")

        let payload = CarTaskMode.floater.createPayload(title: "Someday")

        XCTAssertEqual(payload.title, "Someday")
        XCTAssertNil(payload.due)
    }

    func testBuildsPendingTaskRowsOnly() {
        let state = buildCarTaskSurfaceState(
            mode: .today,
            todos: [
                todo(id: "todo-1", title: "Buy milk", completed: false),
                todo(id: "todo-2", title: "Done", completed: true)
            ],
            dueLabelFor: { _ in "9:00 AM" }
        )

        XCTAssertFalse(state.isEmpty)
        XCTAssertEqual(state.title, "T'Day")
        XCTAssertEqual(state.items.count, 1)
        XCTAssertEqual(state.items.first?.title, "Buy milk")
        XCTAssertEqual(state.items.first?.detailText, "Low - 9:00 AM")
    }

    func testEmptyStateWhenRowsAreCompleted() {
        let state = buildCarTaskSurfaceState(
            mode: .floater,
            todos: [todo(id: "floater-1", completed: true)]
        )

        XCTAssertTrue(state.isEmpty)
        XCTAssertEqual(state.emptyTitle, "No floater tasks")
    }

    private func todo(
        id: String,
        title: String = "Task",
        completed: Bool = false
    ) -> TodoItem {
        TodoItem(
            id: id,
            canonicalId: id,
            title: title,
            description: nil,
            priority: "Low",
            due: Date(timeIntervalSince1970: 1_780_000_000),
            rrule: nil,
            instanceDate: nil,
            pinned: false,
            completed: completed,
            listId: nil,
            updatedAt: nil
        )
    }
}
