import XCTest

#if SWIFT_PACKAGE
@testable import TdayCore
#else
@testable import Tday
#endif

final class CacheMappersDateParsingTests: XCTestCase {
    func testParsesBackendMinutePrecisionLocalDateTimeAsUTC() {
        let parsed = parseOptionalDate("2026-05-20T21:00")

        XCTAssertNotNil(parsed)
        let calendar = Calendar(identifier: .iso8601)
        let components = calendar.dateComponents(in: TimeZone(secondsFromGMT: 0)!, from: parsed!)
        XCTAssertEqual(components.year, 2026)
        XCTAssertEqual(components.month, 5)
        XCTAssertEqual(components.day, 20)
        XCTAssertEqual(components.hour, 21)
        XCTAssertEqual(components.minute, 0)
    }

    func testMovedDuePreservingTimeKeepsOriginalLocalTime() throws {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "America/Toronto")!
        let due = Date(timeIntervalSince1970: 1_778_870_730)
        let target = try XCTUnwrap(calendar.date(from: DateComponents(year: 2026, month: 6, day: 3)))

        let moved = try XCTUnwrap(movedDuePreservingTime(due: due, targetDay: target, calendar: calendar))
        let components = calendar.dateComponents([.year, .month, .day, .hour, .minute, .second], from: moved)

        XCTAssertEqual(components.year, 2026)
        XCTAssertEqual(components.month, 6)
        XCTAssertEqual(components.day, 3)
        XCTAssertEqual(components.hour, 14)
        XCTAssertEqual(components.minute, 45)
        XCTAssertEqual(components.second, 30)
    }

    func testTimelineRescheduleTargetDateResolvesSectionTargets() throws {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "America/Toronto")!
        let today = try XCTUnwrap(calendar.date(from: DateComponents(year: 2026, month: 5, day: 24)))

        let dayTarget = try XCTUnwrap(timelineRescheduleTargetDate(sectionId: "priority-1779854400.0", today: today, calendar: calendar))
        XCTAssertEqual(calendar.component(.day, from: dayTarget), 27)

        let restTarget = try XCTUnwrap(timelineRescheduleTargetDate(sectionId: "rest-24317", today: today, calendar: calendar))
        XCTAssertEqual(calendar.component(.day, from: restTarget), 31)

        let monthTarget = try XCTUnwrap(timelineRescheduleTargetDate(sectionId: "month-24319", today: today, calendar: calendar))
        XCTAssertEqual(calendar.component(.year, from: monthTarget), 2026)
        XCTAssertEqual(calendar.component(.month, from: monthTarget), 7)
        XCTAssertEqual(calendar.component(.day, from: monthTarget), 1)

        XCTAssertNil(timelineRescheduleTargetDate(sectionId: "earlier", today: today, calendar: calendar))
        XCTAssertNil(timelineRescheduleTargetDate(sectionId: "month-24316", today: today, calendar: calendar))
    }
}
