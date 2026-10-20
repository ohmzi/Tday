import CoreGraphics
import EventKit
import Foundation

/// One scheduled task rendered as a device-calendar event.
struct DeviceCalendarEvent {
    let taskID: String
    let title: String
    let notes: String?
    let start: Date
    /// RFC 5545 rule for a repeating task, or nil for a one-off.
    let rrule: String?
}

/// Owns T'Day's own calendar in EventKit.
///
/// Everything here is scoped to a single calendar this app creates. Writes never touch the user's
/// other calendars, and turning the mirror off removes the calendar wholesale.
///
/// Full access rather than write-only: the mirror has to read back the events it previously wrote
/// in order to replace them, and iOS 17's write-only mode cannot do that.
@MainActor
final class DeviceCalendarStore {
    /// T'Day tasks are point-in-time — the ICS feed drops durations entirely (`CalendarIcs.kt`) —
    /// but EventKit requires an end date, and a zero-length event is unreadable in a day grid.
    static let defaultEventMinutes = 30

    private let store = EKEventStore()
    private let preferenceStore: CalendarSyncPreferenceStore

    init(preferenceStore: CalendarSyncPreferenceStore) {
        self.preferenceStore = preferenceStore
    }

    var hasAccess: Bool {
        EKEventStore.authorizationStatus(for: .event) == .fullAccess
    }

    /// Prompts for calendar access. Returns whether the mirror can write.
    func requestAccess() async -> Bool {
        if hasAccess { return true }
        return (try? await store.requestFullAccessToEvents()) ?? false
    }

    // MARK: - Calendar

    private func existingCalendar() -> EKCalendar? {
        guard let identifier = preferenceStore.calendarIdentifier else { return nil }
        // Returns nil if the user deleted the calendar in the Calendar app; the caller recreates.
        return store.calendar(withIdentifier: identifier)
    }

    func ensureCalendar() throws -> EKCalendar {
        if let existing = existingCalendar() { return existing }

        let calendar = EKCalendar(for: .event, eventStore: store)
        calendar.title = Self.calendarTitle
        calendar.cgColor = Self.calendarColor
        // A local source keeps the mirror on-device: nothing is pushed to iCloud or any other
        // account, which matters for a privacy-first app and keeps this working in Local Mode.
        // Falling back to the default calendar's source covers devices with no local source at all.
        calendar.source = store.sources.first { $0.sourceType == .local }
            ?? store.defaultCalendarForNewEvents?.source

        try store.saveCalendar(calendar, commit: true)
        preferenceStore.setCalendarIdentifier(calendar.calendarIdentifier)
        return calendar
    }

    /// Drops the calendar and, with it, every event T'Day created.
    func deleteCalendar() throws {
        guard let calendar = existingCalendar() else {
            // Already gone — the user deleted it in the Calendar app. Nothing to remove, but the
            // stale identifiers should not survive.
            preferenceStore.clearSyncState()
            return
        }
        // Only after the removal succeeds: clearing first would discard the identifier needed to
        // retry, leaving an orphaned T'Day calendar the app can no longer find or clean up.
        try store.removeCalendar(calendar, commit: true)
        preferenceStore.clearSyncState()
    }

    // MARK: - Events

    /// Replaces the calendar's contents with `events`.
    ///
    /// Previously written events are removed by their stored identifiers rather than by scanning a
    /// date range, so a task far outside any window still gets cleaned up.
    /// Returns the identifiers of the events now in the calendar.
    @discardableResult
    func replaceEvents(_ events: [DeviceCalendarEvent], in calendar: EKCalendar) throws -> [String] {
        for identifier in preferenceStore.eventIdentifiers {
            guard let existing = store.event(withIdentifier: identifier) else { continue }
            // .futureEvents so a recurring task's whole series goes, not just its first occurrence.
            try? store.remove(existing, span: .futureEvents, commit: false)
        }

        var saved: [EKEvent] = []
        for event in events {
            let ekEvent = makeEvent(from: event, in: calendar)
            do {
                try store.save(ekEvent, span: .futureEvents, commit: false)
                saved.append(ekEvent)
            } catch {
                // One malformed task must not abort the whole mirror.
                TdayTelemetry.capture(error, operation: "calendar.sync.save_event")
            }
        }

        // Single commit: committing per event turns a routine sync into hundreds of writes.
        try store.commit()

        // Identifiers are read only after the commit. EventKit assigns `eventIdentifier` when the
        // event actually lands in the store, so collecting them from staged writes can yield
        // nothing — which would leave the next pass with no record of what to clean up and
        // silently accumulate duplicate events on every sync.
        let writtenIdentifiers = saved.compactMap { $0.eventIdentifier }
        preferenceStore.setEventIdentifiers(writtenIdentifiers)
        return writtenIdentifiers
    }

    private func makeEvent(from event: DeviceCalendarEvent, in calendar: EKCalendar) -> EKEvent {
        let ekEvent = EKEvent(eventStore: store)
        ekEvent.calendar = calendar
        ekEvent.title = event.title
        ekEvent.notes = event.notes
        ekEvent.startDate = event.start
        ekEvent.endDate = event.start.addingTimeInterval(TimeInterval(Self.defaultEventMinutes * 60))
        ekEvent.timeZone = TimeZone.current

        if let rrule = event.rrule,
           !rrule.isEmpty,
           let rule = RecurrenceRuleParser.parse(rrule) {
            ekEvent.recurrenceRules = [rule]
        }
        return ekEvent
    }

    private static let calendarTitle = "T'Day"
    private static let calendarColor = CGColor(red: 0.298, green: 0.431, blue: 0.961, alpha: 1)
}
