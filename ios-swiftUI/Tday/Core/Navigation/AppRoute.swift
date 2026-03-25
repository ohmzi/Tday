import Foundation

enum AppRoute: Hashable {
    case home
    case todayTodos
    case scheduledTodos
    case allTodos(highlightTodoId: String?)
    case priorityTodos
    case listTodos(listId: String, listName: String)
    case completed
    case calendar
    case settings

    var deepLinkPath: String {
        switch self {
        case .home:
            return "home"
        case .todayTodos:
            return "todos/today"
        case .scheduledTodos:
            return "todos/scheduled"
        case let .allTodos(highlightTodoId):
            if let highlightTodoId, !highlightTodoId.isEmpty {
                return "todos/all?highlightTodoId=\(highlightTodoId)"
            }
            return "todos/all"
        case .priorityTodos:
            return "todos/priority"
        case let .listTodos(listId, listName):
            return "todos/list/\(listId)/\(listName)"
        case .completed:
            return "completed"
        case .calendar:
            return "calendar"
        case .settings:
            return "settings"
        }
    }

    static func from(url: URL) -> AppRoute? {
        guard url.scheme?.lowercased() == "tday" else {
            return nil
        }

        let host = url.host?.lowercased()
        let pathComponents = url.pathComponents.filter { $0 != "/" }
        let components = ([host].compactMap { $0 } + pathComponents)

        guard let first = components.first else {
            return .home
        }

        switch first {
        case "home":
            return .home
        case "completed":
            return .completed
        case "calendar":
            return .calendar
        case "settings":
            return .settings
        case "todos":
            let second = components.dropFirst().first ?? ""
            switch second {
            case "today":
                return .todayTodos
            case "scheduled":
                return .scheduledTodos
            case "all":
                let highlightTodoId = URLComponents(url: url, resolvingAgainstBaseURL: false)?
                    .queryItems?
                    .first(where: { $0.name == "highlightTodoId" })?
                    .value
                return .allTodos(highlightTodoId: highlightTodoId)
            case "priority":
                return .priorityTodos
            case "list":
                let remaining = Array(components.dropFirst(2))
                guard remaining.count >= 2 else {
                    return nil
                }
                return .listTodos(listId: remaining[0], listName: remaining[1])
            default:
                return nil
            }
        default:
            return nil
        }
    }
}
