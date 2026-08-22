import Foundation

/// Mirrors pending scheduled tasks into T'Day's own device calendar.
///
/// One-way by design: T'Day is the source of truth and the calendar is a projection of it. Nothing
/// is ever read back into the app, so there is no conflict resolution and no chance of an edit loop.
///
/// Reconciles the whole calendar rather than tracking per-task mutations, mirroring
/// `TaskReminderScheduler.reschedule(tasks:defaultReminder:)` — which is also why this hangs off the
/// same `AppViewModel.rescheduleReminders()` pass instead of growing its own trigger points.
///
/// Floaters are excluded: they have no due date, and the calendar contract keeps them off calendar
/// surfaces.
@MainActor
final class CalendarSyncManager {
    private let deviceCalendarStore: DeviceCalendarStore
    private let preferenceStore: CalendarSyncPreferenceStore

    init(deviceCalendarStore: DeviceCalendarStore, preferenceStore: CalendarSyncPreferenceStore) {
        self.deviceCalendarStore = deviceCalendarStore
        self.preferenceStore = preferenceStore
    }

    var isEnabled: Bool {
        preferenceStore.isEnabled
    }

    /// Prompts for calendar access and, if granted, turns the mirror on. Returns whether it is on.
    func enable(tasks: [TodoItem]) async -> Bool {
        guard await deviceCalendarStore.requestAccess() else {
            TdayTelemetry.addBreadcrumb("calendar.sync.permission_denied")
            return false
        }
        preferenceStore.setEnabled(true)
        // Force a write even when the task set matches the stored fingerprint: the calendar may
        // have been deleted while the feature was off.
        preferenceStore.setLastSyncedFingerprint(nil)
        await sync(tasks: tasks)
        return true
    }

    /// Turns the mirror off and removes the calendar and every event in it.
    func disable() async {
        preferenceStore.setEnabled(false)
        do {
            try deviceCalendarStore.deleteCalendar()
        } catch {
            TdayTelemetry.capture(error, operation: "calendar.sync.delete_calendar")
        }
        TdayTelemetry.addBreadcrumb("calendar.sync.disabled")
    }

    /// Reconciles the device calendar against `tasks`. A no-op when the feature is off, when
    /// calendar access is missing, or when nothing this mirror renders has changed.
    func sync(tasks: [TodoItem]) async {
        guard preferenceStore.isEnabled, deviceCalendarStore.hasAccess else { return }

        let events = Self.events(from: tasks)
        let fingerprint = Self.fingerprint(of: events)
        guard fingerprint != preferenceStore.lastSyncedFingerprint else { return }

        do {
            let calendar = try deviceCalendarStore.ensureCalendar()
            try deviceCalendarStore.replaceEvents(events, in: calendar)
            preferenceStore.setLastSyncedFingerprint(fingerprint)
            TdayTelemetry.addBreadcrumb(
                "calendar.sync.reconcile",
                data: ["eventCount": events.count]
            )
        } catch {
            // Leave the fingerprint untouched so the next pass retries.
            TdayTelemetry.capture(error, operation: "calendar.sync.reconcile")
        }
    }

    // MARK: - Projection

    static func events(from tasks: [TodoItem]) -> [DeviceCalendarEvent] {
        tasks.compactMap { task in
            guard !task.completed, let due = task.due else { return nil }
            let rrule = task.rrule.flatMap { $0.isEmpty ? nil : $0 }
            return DeviceCalendarEvent(
                taskID: task.id,
                title: task.title,
                notes: task.description,
                start: due,
                rrule: rrule
            )
        }
    }

    /// Content fingerprint of the projected calendar. Cheap guard against rewriting the calendar
    /// for refreshes that touched nothing this mirror renders — a floater edit, a completed-item
    /// refresh, a preference sync.
    static func fingerprint(of events: [DeviceCalendarEvent]) -> String {
        if events.isEmpty { return "empty" }
        // Field separator that cannot appear in a task title, so fields cannot alias.
        let separator = "\u{1F}"
        return events
            .sorted { lhs, rhs in
                lhs.start == rhs.start ? lhs.taskID < rhs.taskID : lhs.start < rhs.start
            }
            .map { event in
                [
                    event.taskID,
                    String(Int(event.start.timeIntervalSince1970)),
                    event.title,
                    event.notes ?? "",
                    event.rrule ?? ""
                ].joined(separator: separator)
            }
            .joined(separator: "|")
            .stableHash()
    }
}

private extension String {
    /// FNV-1a. Swift's own `hashValue` is seeded per process launch, so it would produce a
    /// different fingerprint on every cold start and force a pointless calendar rewrite each time
    /// the app is relaunched. This has to stay stable across launches to be worth anything.
    func stableHash() -> String {
        var hash: UInt64 = 0xcbf2_9ce4_8422_2325
        for byte in utf8 {
            hash ^= UInt64(byte)
            hash = hash &* 0x0000_0100_0000_01b3
        }
        return String(hash, radix: 16)
    }
}
