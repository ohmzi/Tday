import Foundation
import SwiftUI

enum TodoListMode: String, Codable, CaseIterable, Hashable {
    case today = "TODAY"
    case overdue = "OVERDUE"
    case scheduled = "SCHEDULED"
    case all = "ALL"
    case priority = "PRIORITY"
    case list = "LIST"

    var title: String {
        switch self {
        case .today:
            return "Today"
        case .overdue:
            return "Overdue"
        case .scheduled:
            return "Scheduled"
        case .all:
            return "All Tasks"
        case .priority:
            return "Priority"
        case .list:
            return "List"
        }
    }

    var summaryMode: String {
        switch self {
        case .today:
            return "today"
        case .overdue:
            return "overdue"
        case .scheduled:
            return "scheduled"
        case .all:
            return "all"
        case .priority:
            return "priority"
        case .list:
            return "list"
        }
    }
}

struct CreateTaskPayload: Equatable, Hashable, Codable {
    let title: String
    let description: String?
    let priority: String
    let dtstart: Date
    let due: Date
    let rrule: String?
    let listId: String?
}

struct TodoItem: Identifiable, Equatable, Hashable, Codable {
    let id: String
    let canonicalId: String
    let title: String
    let description: String?
    let priority: String
    let dtstart: Date
    let due: Date
    let rrule: String?
    let instanceDate: Date?
    let pinned: Bool
    let completed: Bool
    let listId: String?
    let updatedAt: Date?

    var isRecurring: Bool {
        !(rrule ?? "").isEmpty
    }

    var instanceDateEpochMilliseconds: Int64? {
        instanceDate.map { Int64($0.timeIntervalSince1970 * 1_000) }
    }

    var instanceDateEpochMillis: Int64? {
        instanceDateEpochMilliseconds
    }
}

struct ListSummary: Identifiable, Equatable, Hashable, Codable {
    let id: String
    let name: String
    let color: String?
    let iconKey: String?
    let todoCount: Int
    let updatedAt: Date?
}

struct DashboardSummary: Equatable, Hashable, Codable {
    let todayCount: Int
    let scheduledCount: Int
    let allCount: Int
    let priorityCount: Int
    let completedCount: Int
    let lists: [ListSummary]
}

struct CompletedItem: Identifiable, Equatable, Hashable, Codable {
    let id: String
    let originalTodoId: String?
    let title: String
    let description: String?
    let priority: String
    let dtstart: Date
    let due: Date
    let completedAt: Date?
    let rrule: String?
    let instanceDate: Date?
    let listName: String?
    let listColor: String?
}

struct RegisterOutcome: Equatable, Hashable {
    let success: Bool
    let requiresApproval: Bool
    let message: String
}

enum AuthResult: Equatable, Hashable {
    case success
    case pendingApproval
    case error(String)
}

enum AppThemeMode: String, CaseIterable, Codable, Hashable {
    case system
    case light
    case dark

    var label: String {
        switch self {
        case .system:
            return "System"
        case .light:
            return "Light"
        case .dark:
            return "Dark"
        }
    }

    var colorScheme: ColorScheme? {
        switch self {
        case .system:
            return nil
        case .light:
            return .light
        case .dark:
            return .dark
        }
    }
}

enum CalendarDisplayMode: String, CaseIterable, Codable, Hashable {
    case month
    case week
    case day
}

struct TimelineSection<Item: Identifiable & Hashable>: Identifiable, Hashable {
    let id: String
    let title: String
    let items: [Item]
    let isCollapsible: Bool
}

typealias TodoSection = TimelineSection<TodoItem>

extension Date {
    var epochMilliseconds: Int64 {
        Int64((timeIntervalSince1970 * 1_000).rounded())
    }

    init(epochMilliseconds: Int64) {
        self = Date(timeIntervalSince1970: TimeInterval(epochMilliseconds) / 1_000)
    }
}
