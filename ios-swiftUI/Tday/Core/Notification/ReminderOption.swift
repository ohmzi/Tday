import Foundation

/// The reminder offsets the picker offers, in the order it offers them.
///
/// Ten choices, the same ten in the same order as Android's
/// `core/notification/ReminderOption.kt`: "30 minutes before" has to mean the same
/// thing on both phones, and a list that differs by platform is the kind of thing
/// nobody notices until they set it on one device and read it on the other.
///
/// The raw value is the case name, and `ReminderPreferenceStore` writes exactly that
/// into UserDefaults. Cases may be added — a stored value that predates them still
/// decodes — but never renamed, or every device holding the old spelling silently
/// falls back to the default.
enum ReminderOption: String, CaseIterable, Codable, Sendable, Identifiable {
    case none
    case atTime
    case fiveMinutes
    case tenMinutes
    case fifteenMinutes
    case thirtyMinutes
    case oneHour
    case twoHours
    case oneDay
    case twoDays

    var id: String { rawValue }

    var title: String {
        switch self {
        case .none:
            return L("None")
        case .atTime:
            return L("At time of task")
        case .fiveMinutes:
            return L("5 minutes before")
        case .tenMinutes:
            return L("10 minutes before")
        case .fifteenMinutes:
            return L("15 minutes before")
        case .thirtyMinutes:
            return L("30 minutes before")
        case .oneHour:
            return L("1 hour before")
        case .twoHours:
            return L("2 hours before")
        case .oneDay:
            return L("1 day before")
        case .twoDays:
            return L("2 days before")
        }
    }

    var label: String {
        title
    }

    var offsetSeconds: TimeInterval? {
        switch self {
        case .none:
            return nil
        case .atTime:
            return 0
        case .fiveMinutes:
            return 5 * 60
        case .tenMinutes:
            return 10 * 60
        case .fifteenMinutes:
            return 15 * 60
        case .thirtyMinutes:
            return 30 * 60
        case .oneHour:
            return 60 * 60
        case .twoHours:
            return 2 * 60 * 60
        case .oneDay:
            return 24 * 60 * 60
        case .twoDays:
            return 2 * 24 * 60 * 60
        }
    }

    var isEnabled: Bool {
        self != .none
    }
}
