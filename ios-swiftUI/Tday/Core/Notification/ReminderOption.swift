import Foundation

enum ReminderOption: String, CaseIterable, Codable, Sendable, Identifiable {
    case none
    case atTime
    case fiveMinutes
    case fifteenMinutes
    case oneHour
    case oneDay
    case twoDays

    var id: String { rawValue }

    var title: String {
        switch self {
        case .none:
            return "None"
        case .atTime:
            return "At time of task"
        case .fiveMinutes:
            return "5 minutes before"
        case .fifteenMinutes:
            return "15 minutes before"
        case .oneHour:
            return "1 hour before"
        case .oneDay:
            return "1 day before"
        case .twoDays:
            return "2 days before"
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
        case .fifteenMinutes:
            return 15 * 60
        case .oneHour:
            return 60 * 60
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
