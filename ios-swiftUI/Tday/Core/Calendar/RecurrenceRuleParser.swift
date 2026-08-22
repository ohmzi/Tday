import EventKit
import Foundation

/// Translates the RFC 5545 RRULE strings T'Day stores on a task into EventKit's object model.
///
/// This exists because EventKit has no string initializer for a recurrence rule, while
/// `CalendarContract` on Android takes the raw RRULE as-is. Everything here is pure so it can be
/// tested without an `EKEventStore`.
///
/// Deliberately partial: it covers the rules T'Day's own repeat UI can produce, and returns nil for
/// anything else. A nil is not a failure — the caller writes the task as a single non-recurring
/// event, so an exotic rule still puts the task on the calendar on its due date instead of dropping
/// it silently.
enum RecurrenceRuleParser {
    static func parse(_ rrule: String) -> EKRecurrenceRule? {
        let parts = components(of: rrule)

        guard let frequency = parts["FREQ"].flatMap(frequency(from:)) else { return nil }

        // INTERVAL is optional and defaults to 1; a zero or negative interval is malformed, and
        // EventKit raises on it rather than returning nil.
        let interval = parts["INTERVAL"].flatMap { Int($0) } ?? 1
        guard interval >= 1 else { return nil }

        // EventKit rejects days-of-the-week on a daily rule. T'Day's "every weekday" is
        // FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR, so nothing valid is lost by dropping it here.
        let daysOfTheWeek = frequency == .daily ? nil : parts["BYDAY"].flatMap(daysOfTheWeek(from:))
        if parts["BYDAY"] != nil, frequency != .daily, daysOfTheWeek == nil { return nil }

        let daysOfTheMonth = parts["BYMONTHDAY"].flatMap { numbers(from: $0, in: -31 ... 31) }
        if parts["BYMONTHDAY"] != nil, daysOfTheMonth == nil { return nil }

        let monthsOfTheYear = parts["BYMONTH"].flatMap { numbers(from: $0, in: 1 ... 12) }
        if parts["BYMONTH"] != nil, monthsOfTheYear == nil { return nil }

        let end = recurrenceEnd(from: parts)
        if (parts["COUNT"] != nil || parts["UNTIL"] != nil), end == nil { return nil }

        return EKRecurrenceRule(
            recurrenceWith: frequency,
            interval: interval,
            daysOfTheWeek: daysOfTheWeek,
            daysOfTheMonth: daysOfTheMonth,
            monthsOfTheYear: monthsOfTheYear,
            weeksOfTheYear: nil,
            daysOfTheYear: nil,
            setPositions: nil,
            end: end
        )
    }

    // MARK: - Pieces

    /// Splits `FREQ=WEEKLY;BYDAY=MO,WE` into uppercased key/value pairs, tolerating a leading
    /// `RRULE:` prefix and surrounding whitespace.
    static func components(of rrule: String) -> [String: String] {
        var trimmed = rrule.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.uppercased().hasPrefix("RRULE:") {
            trimmed = String(trimmed.dropFirst("RRULE:".count))
        }

        var result: [String: String] = [:]
        for pair in trimmed.split(separator: ";") {
            let halves = pair.split(separator: "=", maxSplits: 1, omittingEmptySubsequences: false)
            guard halves.count == 2 else { continue }
            let key = halves[0].trimmingCharacters(in: .whitespaces).uppercased()
            let value = halves[1].trimmingCharacters(in: .whitespaces)
            guard !key.isEmpty, !value.isEmpty else { continue }
            result[key] = value
        }
        return result
    }

    private static func frequency(from raw: String) -> EKRecurrenceFrequency? {
        switch raw.uppercased() {
        case "DAILY": return .daily
        case "WEEKLY": return .weekly
        case "MONTHLY": return .monthly
        case "YEARLY": return .yearly
        default: return nil
        }
    }

    /// Parses `MO,WE,FR` and the ordinal form `2MO` / `-1FR` used by monthly rules.
    private static func daysOfTheWeek(from raw: String) -> [EKRecurrenceDayOfWeek]? {
        var days: [EKRecurrenceDayOfWeek] = []
        for token in raw.split(separator: ",") {
            let text = token.trimmingCharacters(in: .whitespaces).uppercased()
            guard text.count >= 2 else { return nil }

            let weekdayText = String(text.suffix(2))
            guard let weekday = weekday(from: weekdayText) else { return nil }

            let ordinalText = String(text.dropLast(2))
            if ordinalText.isEmpty {
                days.append(EKRecurrenceDayOfWeek(weekday))
            } else {
                // RFC 5545 allows ±1...53; EventKit uses the same range and traps outside it.
                guard let ordinal = Int(ordinalText), (-53 ... 53).contains(ordinal), ordinal != 0 else {
                    return nil
                }
                days.append(EKRecurrenceDayOfWeek(weekday, weekNumber: ordinal))
            }
        }
        return days.isEmpty ? nil : days
    }

    private static func weekday(from raw: String) -> EKWeekday? {
        switch raw {
        case "SU": return .sunday
        case "MO": return .monday
        case "TU": return .tuesday
        case "WE": return .wednesday
        case "TH": return .thursday
        case "FR": return .friday
        case "SA": return .saturday
        default: return nil
        }
    }

    private static func numbers(from raw: String, in range: ClosedRange<Int>) -> [NSNumber]? {
        var values: [NSNumber] = []
        for token in raw.split(separator: ",") {
            guard let value = Int(token.trimmingCharacters(in: .whitespaces)),
                  range.contains(value),
                  value != 0
            else {
                return nil
            }
            values.append(NSNumber(value: value))
        }
        return values.isEmpty ? nil : values
    }

    private static func recurrenceEnd(from parts: [String: String]) -> EKRecurrenceEnd? {
        // COUNT wins over UNTIL: RFC 5545 forbids both in one rule, and a malformed rule carrying
        // both is better treated as bounded than as endless.
        if let countText = parts["COUNT"], let count = Int(countText), count > 0 {
            return EKRecurrenceEnd(occurrenceCount: count)
        }
        if let untilText = parts["UNTIL"], let until = date(fromUntil: untilText) {
            return EKRecurrenceEnd(end: until)
        }
        return nil
    }

    /// RFC 5545 UNTIL: `yyyyMMdd'T'HHmmss'Z'`, `yyyyMMdd'T'HHmmss` (floating), or `yyyyMMdd`.
    static func date(fromUntil raw: String) -> Date? {
        let text = raw.trimmingCharacters(in: .whitespaces).uppercased()
        let formats = ["yyyyMMdd'T'HHmmss'Z'", "yyyyMMdd'T'HHmmss", "yyyyMMdd"]

        for format in formats {
            let formatter = DateFormatter()
            formatter.locale = Locale(identifier: "en_US_POSIX")
            formatter.calendar = Calendar(identifier: .gregorian)
            // A trailing Z is UTC by definition. The floating forms have no zone of their own, and
            // reading them as UTC keeps the parse deterministic instead of dependent on where the
            // device happens to be.
            formatter.timeZone = TimeZone(identifier: "UTC")
            formatter.dateFormat = format
            if let parsed = formatter.date(from: text) {
                return parsed
            }
        }
        return nil
    }
}
