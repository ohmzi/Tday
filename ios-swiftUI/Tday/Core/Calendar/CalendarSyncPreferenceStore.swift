import Foundation

/// Opt-in flag for mirroring scheduled tasks into the device calendar, plus the bookkeeping the
/// mirror needs to clean up after itself.
///
/// The stored event identifiers are what let a reconcile pass remove exactly the events T'Day
/// created, without scanning the user's calendar by date range — the same idea as
/// `ReminderPreferenceStore.saveScheduledRequestCodes` on Android.
///
/// The fingerprint keeps the pass cheap: reconciling rewrites every event, and it runs on the same
/// cadence as the reminder reschedule. Without it, a sync that only touched a floater would still
/// rewrite the whole calendar and make every calendar app re-render.
final class CalendarSyncPreferenceStore {
    private let defaults = UserDefaults.standard
    private let enabledKey = "calendar.sync.enabled"
    private let calendarIdentifierKey = "calendar.sync.calendar.identifier"
    private let eventIdentifiersKey = "calendar.sync.event.identifiers"
    private let fingerprintKey = "calendar.sync.fingerprint"

    var isEnabled: Bool {
        defaults.bool(forKey: enabledKey)
    }

    func setEnabled(_ enabled: Bool) {
        defaults.set(enabled, forKey: enabledKey)
    }

    var calendarIdentifier: String? {
        defaults.string(forKey: calendarIdentifierKey)
    }

    func setCalendarIdentifier(_ identifier: String?) {
        if let identifier {
            defaults.set(identifier, forKey: calendarIdentifierKey)
        } else {
            defaults.removeObject(forKey: calendarIdentifierKey)
        }
    }

    var eventIdentifiers: [String] {
        defaults.stringArray(forKey: eventIdentifiersKey) ?? []
    }

    func setEventIdentifiers(_ identifiers: [String]) {
        defaults.set(identifiers, forKey: eventIdentifiersKey)
    }

    var lastSyncedFingerprint: String? {
        defaults.string(forKey: fingerprintKey)
    }

    func setLastSyncedFingerprint(_ fingerprint: String?) {
        if let fingerprint {
            defaults.set(fingerprint, forKey: fingerprintKey)
        } else {
            defaults.removeObject(forKey: fingerprintKey)
        }
    }

    /// Clears everything except the opt-in flag itself, so the next pass rebuilds from scratch.
    func clearSyncState() {
        defaults.removeObject(forKey: calendarIdentifierKey)
        defaults.removeObject(forKey: eventIdentifiersKey)
        defaults.removeObject(forKey: fingerprintKey)
    }
}
