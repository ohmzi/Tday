import Foundation
import SwiftUI

enum TodoListMode: String, Codable, CaseIterable, Hashable {
    case today = "TODAY"
    case overdue = "OVERDUE"
    case scheduled = "SCHEDULED"
    case all = "ALL"
    case priority = "PRIORITY"
    case floater = "FLOATER"
    case list = "LIST"

    var title: String {
        switch self {
        case .today:
            return L("Today")
        case .overdue:
            return L("Overdue")
        case .scheduled:
            return L("Scheduled")
        case .all:
            return L("All Tasks")
        case .priority:
            return L("Priority")
        case .floater:
            return L("Floater")
        case .list:
            return L("List")
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
        case .floater:
            return "floater"
        case .list:
            return "list"
        }
    }
}

enum TaskRescheduleScope: String, Codable, Hashable {
    case occurrence
    case series
}

struct CreateTaskPayload: Equatable, Hashable, Codable {
    let title: String
    let description: String?
    let priority: String
    let due: Date?
    let rrule: String?
    let listId: String?
}

enum TaskPriorityDisplay {
    static let normalValue = "Low"
    static let importantValue = "Medium"
    static let urgentValue = "High"

    static var normalLabel: String { L("Normal") }
    static var importantLabel: String { L("Important") }
    static var urgentLabel: String { L("Urgent") }

    static var options: [(label: String, value: String)] {
        [
            (normalLabel, normalValue),
            (importantLabel, importantValue),
            (urgentLabel, urgentValue),
        ]
    }

    static func canonicalValue(_ priority: String?) -> String {
        switch priority?.trimmingCharacters(in: .whitespacesAndNewlines).lowercased() {
        case "normal", "low":
            return normalValue
        case "important", "medium":
            return importantValue
        case "urgent", "high":
            return urgentValue
        default:
            return normalValue
        }
    }

    static func label(for priority: String?) -> String {
        switch canonicalValue(priority) {
        case importantValue:
            return importantLabel
        case urgentValue:
            return urgentLabel
        default:
            return normalLabel
        }
    }

    static func isUrgent(_ priority: String?) -> Bool {
        canonicalValue(priority) == urgentValue
    }

    static func isImportant(_ priority: String?) -> Bool {
        canonicalValue(priority) == importantValue
    }
}

struct TodoItem: Identifiable, Equatable, Hashable, Codable {
    let id: String
    let canonicalId: String
    let title: String
    let description: String?
    let priority: String
    let due: Date?
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

extension TodoListMode {
    var supportsTaskReschedule: Bool {
        switch self {
        case .scheduled, .all, .priority, .list:
            return true
        case .today, .overdue, .floater:
            return false
        }
    }
}

extension TodoItem {
    func repositoryTargetForReschedule(scope: TaskRescheduleScope) -> TodoItem {
        switch scope {
        case .occurrence:
            return self
        case .series:
            return TodoItem(
                id: canonicalId,
                canonicalId: canonicalId,
                title: title,
                description: description,
                priority: priority,
                due: due,
                rrule: rrule,
                instanceDate: nil,
                pinned: pinned,
                completed: completed,
                listId: listId,
                updatedAt: updatedAt
            )
        }
    }
}

func movedDuePreservingTime(
    due: Date,
    targetDay: Date,
    calendar: Calendar = .current
) -> Date? {
    let dueComponents = calendar.dateComponents([.hour, .minute, .second, .nanosecond], from: due)
    var targetComponents = calendar.dateComponents([.year, .month, .day], from: targetDay)
    targetComponents.timeZone = calendar.timeZone
    targetComponents.hour = dueComponents.hour
    targetComponents.minute = dueComponents.minute
    targetComponents.second = dueComponents.second
    targetComponents.nanosecond = dueComponents.nanosecond
    return calendar.date(from: targetComponents)
}

func movedTaskPayload(
    todo: TodoItem,
    targetDay: Date,
    calendar: Calendar = .current
) -> CreateTaskPayload? {
    guard let due = todo.due,
          let movedDue = movedDuePreservingTime(due: due, targetDay: targetDay, calendar: calendar) else {
        return nil
    }
    return CreateTaskPayload(
        title: todo.title,
        description: todo.description,
        priority: todo.priority,
        due: movedDue,
        rrule: todo.rrule,
        listId: todo.listId
    )
}

func timelineRescheduleTargetDate(
    sectionId: String,
    today: Date = Date(),
    calendar: Calendar = .current
) -> Date? {
    let startOfToday = calendar.startOfDay(for: today)
    let currentMonthStart = rescheduleMonthStart(for: startOfToday, calendar: calendar)

    if sectionId == "earlier" {
        return calendar.date(byAdding: .day, value: -1, to: startOfToday)
    }

    if sectionId.hasPrefix("scheduled-") || sectionId.hasPrefix("priority-") {
        guard let suffix = sectionId.split(separator: "-").last,
              let interval = TimeInterval(String(suffix)) else {
            return nil
        }
        let date = calendar.startOfDay(for: Date(timeIntervalSince1970: interval))
        return rescheduleMonthStart(for: date, calendar: calendar) >= currentMonthStart ? date : nil
    }

    if sectionId.hasPrefix("rest-") {
        guard let monthIndexValue = Int(sectionId.replacingOccurrences(of: "rest-", with: "")) else {
            return nil
        }
        let horizonStart = calendar.startOfDay(for: calendar.date(byAdding: .day, value: 7, to: startOfToday) ?? startOfToday)
        return rescheduleMonthIndex(for: horizonStart, calendar: calendar) == monthIndexValue &&
            rescheduleMonthStart(for: horizonStart, calendar: calendar) == currentMonthStart ? horizonStart : nil
    }

    if sectionId.hasPrefix("month-") {
        guard let monthIndexValue = Int(sectionId.replacingOccurrences(of: "month-", with: "")) else {
            return nil
        }
        let currentMonthIndex = rescheduleMonthIndex(for: startOfToday, calendar: calendar)
        guard monthIndexValue >= currentMonthIndex else {
            return nil
        }
        let year = (monthIndexValue - 1) / 12
        let month = ((monthIndexValue - 1) % 12) + 1
        return calendar.date(from: DateComponents(year: year, month: month, day: 1))
    }

    return nil
}

private func rescheduleMonthStart(for date: Date, calendar: Calendar) -> Date {
    let components = calendar.dateComponents([.year, .month], from: date)
    return calendar.date(from: components).map(calendar.startOfDay) ?? calendar.startOfDay(for: date)
}

private func rescheduleMonthIndex(for date: Date, calendar: Calendar) -> Int {
    let year = calendar.component(.year, from: date)
    let month = calendar.component(.month, from: date)
    return year * 12 + month
}

struct ListSummary: Identifiable, Equatable, Hashable, Codable {
    let id: String
    let name: String
    let color: String?
    let iconKey: String?
    let todoCount: Int
    let updatedAt: Date?
    let createdAt: Date?
}

struct DashboardSummary: Equatable, Hashable, Codable {
    let todayCount: Int
    let scheduledCount: Int
    let allCount: Int
    let priorityCount: Int
    let floaterCount: Int
    let completedCount: Int
    let lists: [ListSummary]
}

struct CompletedItem: Identifiable, Equatable, Hashable, Codable {
    let id: String
    let originalTodoId: String?
    let title: String
    let description: String?
    let priority: String
    let due: Date?
    let completedAt: Date?
    let rrule: String?
    let instanceDate: Date?
    let listId: String?
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
            return L("System")
        case .light:
            return L("Light")
        case .dark:
            return L("Dark")
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
