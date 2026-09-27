import Foundation

/// Local-day arithmetic for the Today widget snapshot, shared by the app (which writes the
/// snapshot) and the TdayWidget extension (which reads it). This is the one source file both
/// targets compile, so the writer and the reader cannot disagree about where a day starts or
/// which pre-computed day an entry date lands in. Foundation only — the extension links nothing
/// else.
///
/// Why it exists: the snapshot is a picture of "due today" taken when the app writes it, and
/// only a cache write retakes it. Offline nothing writes, so after midnight the widget kept
/// rendering yesterday's picture ("No tasks due today", or yesterday's leftovers) while the
/// cache already held today's tasks. The snapshot now carries the next `upcomingDayCount` local
/// days as well, built the same way, and the widget renders whichever day contains its timeline
/// entry's date. Past the last one it asks for the app instead of showing a day it cannot vouch
/// for. Android solves the same bug by rebuilding from its cache (`WidgetHydrateWorker`); the
/// iOS extension cannot open the cache, so it carries the days ahead instead.
enum TodayWidgetDayWindow {
    /// Local days AFTER the snapshot's own day that it pre-computes — a week of cover in all.
    /// Launching the app, returning it to the foreground or a background refresh retakes the
    /// snapshot long before that runs out.
    static let upcomingDayCount = 6

    /// One local day, `[startEpochMs, endEpochMs)`.
    struct Day: Equatable {
        let startEpochMs: Int64
        let endEpochMs: Int64

        func contains(_ epochMs: Int64) -> Bool {
            epochMs >= startEpochMs && epochMs < endEpochMs
        }
    }

    /// The local day containing `date`, then the days after it, `count` days in all. Each end
    /// is one calendar day after its start, so 23- and 25-hour DST days keep their real length.
    static func days(from date: Date, count: Int, calendar: Calendar) -> [Day] {
        var days: [Day] = []
        var start = calendar.startOfDay(for: date)
        while days.count < count {
            let end = dayAfter(start, calendar: calendar)
            days.append(Day(startEpochMs: epochMs(start), endEpochMs: epochMs(end)))
            start = end
        }
        return days
    }

    /// Start of the local day after the one containing `date`: when "today" next changes, and
    /// so when the widget needs a timeline entry of its own.
    static func nextDayStart(after date: Date, calendar: Calendar) -> Date {
        dayAfter(calendar.startOfDay(for: date), calendar: calendar)
    }

    /// The days a snapshot covers: its own day, then its upcoming days. A snapshot written
    /// before the window was recorded (Today schema 2 and older) covers only the local day it
    /// was generated on — treating it as timeless is exactly the stale-widget bug.
    static func coveredDays(
        dayStartEpochMs: Int64?,
        dayEndEpochMs: Int64?,
        upcoming: [Day],
        generatedAtEpochMs: Int64,
        calendar: Calendar
    ) -> [Day] {
        guard let dayStartEpochMs, let dayEndEpochMs else {
            let generatedAt = Date(timeIntervalSince1970: TimeInterval(generatedAtEpochMs) / 1_000)
            return days(from: generatedAt, count: 1, calendar: calendar)
        }
        return [Day(startEpochMs: dayStartEpochMs, endEpochMs: dayEndEpochMs)] + upcoming
    }

    /// Which covered day `epochMs` falls in — 0 for the snapshot's own day, `n` for its `n`th
    /// upcoming day — or nil when it falls in none: the snapshot has run out of days (or the
    /// clock moved back before it) and must not be rendered as today.
    static func dayOffset(of epochMs: Int64, in days: [Day]) -> Int? {
        days.firstIndex { $0.contains(epochMs) }
    }

    private static func dayAfter(_ dayStart: Date, calendar: Calendar) -> Date {
        calendar.date(byAdding: .day, value: 1, to: dayStart) ?? dayStart.addingTimeInterval(86_400)
    }

    private static func epochMs(_ date: Date) -> Int64 {
        Int64(date.timeIntervalSince1970 * 1_000)
    }
}
