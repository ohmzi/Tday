import Foundation

enum CarTaskMode: String, CaseIterable, Codable, Hashable {
    case today
    case floater

    var title: String {
        switch self {
        case .today:
            return "T'Day"
        case .floater:
            return "Floater"
        }
    }

    var emptyTitle: String {
        switch self {
        case .today:
            return "No tasks due today"
        case .floater:
            return "No floater tasks"
        }
    }

    var todoListMode: TodoListMode {
        switch self {
        case .today:
            return .today
        case .floater:
            return .floater
        }
    }

    var telemetryName: String {
        rawValue
    }

    var createDeepLink: URL {
        switch self {
        case .today:
            return URL(string: "tday://todos/create?target=today")!
        case .floater:
            return URL(string: "tday://todos/create?target=floater")!
        }
    }

    func createPayload(title: String, now: Date = Date()) -> CreateTaskPayload {
        CreateTaskPayload(
            title: title,
            description: nil,
            priority: "Low",
            due: self == .today ? now.addingTimeInterval(60 * 60) : nil,
            rrule: nil,
            listId: nil
        )
    }
}

struct CarTaskItem: Identifiable, Equatable, Hashable {
    let id: String
    let title: String
    let detailText: String
    let mode: CarTaskMode
    let source: TodoItem
}

struct CarTaskSurfaceState: Equatable {
    let mode: CarTaskMode
    let title: String
    let emptyTitle: String
    let items: [CarTaskItem]

    var isEmpty: Bool {
        items.isEmpty
    }
}

func buildCarTaskSurfaceState(
    mode: CarTaskMode,
    todos: [TodoItem],
    dueLabelFor: (TodoItem) -> String? = { _ in nil }
) -> CarTaskSurfaceState {
    let items = todos
        .filter { !$0.completed }
        .map { todo in
            CarTaskItem(
                id: "\(mode.telemetryName):\(todo.id):\(todo.instanceDateEpochMilliseconds.map(String.init) ?? "series")",
                title: todo.title,
                detailText: carTaskDetailText(todo: todo, dueLabel: dueLabelFor(todo)),
                mode: mode,
                source: todo
            )
        }
    return CarTaskSurfaceState(
        mode: mode,
        title: mode.title,
        emptyTitle: mode.emptyTitle,
        items: items
    )
}

private func carTaskDetailText(todo: TodoItem, dueLabel: String?) -> String {
    [todo.priority, dueLabel]
        .compactMap { value in
            let trimmed = value?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
            return trimmed.isEmpty ? nil : trimmed
        }
        .joined(separator: " - ")
}
