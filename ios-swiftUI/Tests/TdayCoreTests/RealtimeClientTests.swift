import XCTest

#if SWIFT_PACKAGE
@testable import TdayCore
#else
@testable import Tday
#endif

final class RealtimeClientTests: XCTestCase {
    func testParsesBackendSerializedTodoDomainEvents() {
        let payload = #"{"type":"com.ohmz.tday.domain.DomainEvent.TodoUpdated","todo":{"id":"todo-1"}}"#
        let event = RealtimeEvent(name: RealtimeClient.eventName(from: payload), rawPayload: payload)

        XCTAssertEqual(event.name, "com.ohmz.tday.domain.DomainEvent.TodoUpdated")
        XCTAssertTrue(event.requiresRefresh)
    }

    func testParsesBackendSerializedListDomainEvents() {
        let payload = #"{"type":"com.ohmz.tday.domain.DomainEvent.ListChanged","list":{"id":"list-1"}}"#
        let event = RealtimeEvent(name: RealtimeClient.eventName(from: payload), rawPayload: payload)

        XCTAssertEqual(event.name, "com.ohmz.tday.domain.DomainEvent.ListChanged")
        XCTAssertTrue(event.requiresRefresh)
    }

    func testKeepsOldPlainEventNamesCompatible() {
        let event = RealtimeEvent(name: RealtimeClient.eventName(from: "todo.created"), rawPayload: "todo.created")

        XCTAssertEqual(event.name, "todo.created")
        XCTAssertTrue(event.requiresRefresh)
    }
}
