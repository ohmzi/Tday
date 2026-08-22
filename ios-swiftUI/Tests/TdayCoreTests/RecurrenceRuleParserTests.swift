import EventKit
import XCTest
#if SWIFT_PACKAGE
@testable import TdayCore
#else
@testable import Tday
#endif

/// EventKit has no string initializer for a recurrence rule, so the device-calendar mirror has to
/// translate T'Day's stored RRULE itself. These cases pin that translation — including the
/// deliberate nil returns, which make the caller fall back to a single non-recurring event rather
/// than dropping the task from the calendar.
final class RecurrenceRuleParserTests: XCTestCase {

    // MARK: - Frequencies

    func testParsesDaily() {
        let rule = RecurrenceRuleParser.parse("FREQ=DAILY")
        XCTAssertEqual(rule?.frequency, .daily)
        XCTAssertEqual(rule?.interval, 1)
        XCTAssertNil(rule?.recurrenceEnd)
    }

    func testParsesWeeklyMonthlyYearly() {
        XCTAssertEqual(RecurrenceRuleParser.parse("FREQ=WEEKLY")?.frequency, .weekly)
        XCTAssertEqual(RecurrenceRuleParser.parse("FREQ=MONTHLY")?.frequency, .monthly)
        XCTAssertEqual(RecurrenceRuleParser.parse("FREQ=YEARLY")?.frequency, .yearly)
    }

    func testHonoursInterval() {
        XCTAssertEqual(RecurrenceRuleParser.parse("FREQ=DAILY;INTERVAL=3")?.interval, 3)
    }

    // MARK: - Tolerated input shapes

    func testAcceptsRrulePrefixAndLowercase() {
        let rule = RecurrenceRuleParser.parse("RRULE:freq=weekly;interval=2")
        XCTAssertEqual(rule?.frequency, .weekly)
        XCTAssertEqual(rule?.interval, 2)
    }

    // MARK: - BYDAY

    func testParsesWeeklyByDay() {
        let rule = RecurrenceRuleParser.parse("FREQ=WEEKLY;BYDAY=MO,WE,FR")
        let days = rule?.daysOfTheWeek?.map(\.dayOfTheWeek)
        XCTAssertEqual(days, [.monday, .wednesday, .friday])
    }

    func testParsesOrdinalByDayForMonthlyRules() {
        let rule = RecurrenceRuleParser.parse("FREQ=MONTHLY;BYDAY=2TU")
        XCTAssertEqual(rule?.daysOfTheWeek?.first?.dayOfTheWeek, .tuesday)
        XCTAssertEqual(rule?.daysOfTheWeek?.first?.weekNumber, 2)
    }

    func testParsesNegativeOrdinalByDay() {
        let rule = RecurrenceRuleParser.parse("FREQ=MONTHLY;BYDAY=-1FR")
        XCTAssertEqual(rule?.daysOfTheWeek?.first?.dayOfTheWeek, .friday)
        XCTAssertEqual(rule?.daysOfTheWeek?.first?.weekNumber, -1)
    }

    /// EventKit rejects days-of-the-week on a daily rule, so the rule is kept and the clause
    /// dropped — the task still repeats daily, which is what the RRULE meant.
    func testDropsByDayOnDailyRules() {
        let rule = RecurrenceRuleParser.parse("FREQ=DAILY;BYDAY=MO")
        XCTAssertEqual(rule?.frequency, .daily)
        XCTAssertNil(rule?.daysOfTheWeek)
    }

    func testRejectsUnknownWeekday() {
        XCTAssertNil(RecurrenceRuleParser.parse("FREQ=WEEKLY;BYDAY=XX"))
    }

    // MARK: - BYMONTHDAY / BYMONTH

    func testParsesByMonthDay() {
        let rule = RecurrenceRuleParser.parse("FREQ=MONTHLY;BYMONTHDAY=1,15")
        XCTAssertEqual(rule?.daysOfTheMonth, [1, 15])
    }

    func testRejectsOutOfRangeMonthDay() {
        XCTAssertNil(RecurrenceRuleParser.parse("FREQ=MONTHLY;BYMONTHDAY=32"))
    }

    func testParsesByMonth() {
        let rule = RecurrenceRuleParser.parse("FREQ=YEARLY;BYMONTH=3")
        XCTAssertEqual(rule?.monthsOfTheYear, [3])
    }

    // MARK: - Ends

    func testParsesCount() {
        let rule = RecurrenceRuleParser.parse("FREQ=DAILY;COUNT=10")
        XCTAssertEqual(rule?.recurrenceEnd?.occurrenceCount, 10)
    }

    func testParsesUntilInUtc() {
        let rule = RecurrenceRuleParser.parse("FREQ=DAILY;UNTIL=20260101T000000Z")
        var components = DateComponents()
        components.year = 2026
        components.month = 1
        components.day = 1
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "UTC")!
        XCTAssertEqual(rule?.recurrenceEnd?.endDate, calendar.date(from: components))
    }

    func testParsesDateOnlyUntil() {
        XCTAssertNotNil(RecurrenceRuleParser.parse("FREQ=WEEKLY;UNTIL=20260101")?.recurrenceEnd)
    }

    /// RFC 5545 forbids both in one rule; a malformed rule carrying both is treated as bounded by
    /// COUNT rather than as endless.
    func testCountWinsOverUntil() {
        let rule = RecurrenceRuleParser.parse("FREQ=DAILY;COUNT=4;UNTIL=20260101T000000Z")
        XCTAssertEqual(rule?.recurrenceEnd?.occurrenceCount, 4)
    }

    func testRejectsUnparseableUntil() {
        XCTAssertNil(RecurrenceRuleParser.parse("FREQ=DAILY;UNTIL=not-a-date"))
    }

    // MARK: - Rejections

    func testRejectsMissingFrequency() {
        XCTAssertNil(RecurrenceRuleParser.parse("INTERVAL=2"))
    }

    func testRejectsUnknownFrequency() {
        XCTAssertNil(RecurrenceRuleParser.parse("FREQ=FORTNIGHTLY"))
    }

    /// EventKit raises on a zero interval rather than returning nil, so it has to be caught here.
    func testRejectsZeroInterval() {
        XCTAssertNil(RecurrenceRuleParser.parse("FREQ=DAILY;INTERVAL=0"))
    }

    func testRejectsEmptyString() {
        XCTAssertNil(RecurrenceRuleParser.parse(""))
    }

    // MARK: - Component splitting

    func testComponentsIgnoreMalformedPairs() {
        let parts = RecurrenceRuleParser.components(of: "FREQ=DAILY;;JUNK;INTERVAL=2")
        XCTAssertEqual(parts["FREQ"], "DAILY")
        XCTAssertEqual(parts["INTERVAL"], "2")
        XCTAssertNil(parts["JUNK"])
    }
}
