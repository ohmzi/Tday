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

        let earlierTarget = try XCTUnwrap(timelineRescheduleTargetDate(sectionId: "earlier", today: today, calendar: calendar))
        XCTAssertEqual(calendar.component(.day, from: earlierTarget), 23)
        XCTAssertNil(timelineRescheduleTargetDate(sectionId: "month-24316", today: today, calendar: calendar))
    }
}

final class RecurrencePriorityGrammarTests: XCTestCase {
    func testCapturesTheFiveRecurrencePresets() {
        XCTAssertEqual(RecurrencePriorityGrammar.parse("water plants every day").rrule, "RRULE:FREQ=DAILY;INTERVAL=1")
        XCTAssertEqual(RecurrencePriorityGrammar.parse("standup weekly").rrule, "RRULE:FREQ=WEEKLY;INTERVAL=1")
        XCTAssertEqual(
            RecurrencePriorityGrammar.parse("gym every weekday").rrule,
            "RRULE:FREQ=WEEKLY;INTERVAL=1;BYDAY=MO,TU,WE,TH,FR"
        )
        XCTAssertEqual(RecurrencePriorityGrammar.parse("rent monthly").rrule, "RRULE:FREQ=MONTHLY;INTERVAL=1")
        XCTAssertEqual(RecurrencePriorityGrammar.parse("taxes annually").rrule, "RRULE:FREQ=YEARLY;INTERVAL=1")
    }

    func testWeekdayWinsOverWeekSubstring() {
        XCTAssertEqual(
            RecurrencePriorityGrammar.parse("report weekdays").rrule,
            "RRULE:FREQ=WEEKLY;INTERVAL=1;BYDAY=MO,TU,WE,TH,FR"
        )
    }

    func testCapturesPriority() {
        let bang = RecurrencePriorityGrammar.parse("call mom !!")
        XCTAssertEqual(bang.priority, "High")
        XCTAssertEqual(bang.cleanTitle, "call mom")
        XCTAssertEqual(RecurrencePriorityGrammar.parse("email boss !").priority, "Medium")
        XCTAssertEqual(RecurrencePriorityGrammar.parse("fix bug high priority").priority, "High")
        XCTAssertEqual(RecurrencePriorityGrammar.parse("buy milk low").priority, "Low")
    }

    func testIgnoresNonTrailingBareWords() {
        XCTAssertNil(RecurrencePriorityGrammar.parse("buy low-fat milk").priority)
        XCTAssertNil(RecurrencePriorityGrammar.parse("review highlights").priority)
    }

    func testCapturesRecurrenceAndPriorityTogether() {
        let result = RecurrencePriorityGrammar.parse("gym every day !!")
        XCTAssertEqual(result.rrule, "RRULE:FREQ=DAILY;INTERVAL=1")
        XCTAssertEqual(result.priority, "High")
        XCTAssertEqual(result.cleanTitle, "gym")
    }

    func testLeavesPlainTitlesUntouched() {
        let result = RecurrencePriorityGrammar.parse("buy groceries")
        XCTAssertEqual(result.cleanTitle, "buy groceries")
        XCTAssertNil(result.rrule)
        XCTAssertNil(result.priority)
    }
}
