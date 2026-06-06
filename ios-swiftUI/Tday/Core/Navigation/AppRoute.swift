import Foundation

enum AppRoute: Hashable {
    case home
    case todayTodos
    case createTodayTodo
    case createFloaterTodo
    case overdueTodos
    case scheduledTodos
    case allTodos(highlightTodoId: String?)
    case priorityTodos
    case floaterTodos
    case floaterListTodos(listId: String, listName: String)
    case listTodos(listId: String, listName: String)
    case completed
    case calendar
    case settings
    case latestRelease
    case forgotPassword

    var deepLinkPath: String {
        switch self {
        case .home:
            return "home"
        case .todayTodos:
            return "todos/today"
        case .createTodayTodo:
            return "todos/create?target=today"
        case .createFloaterTodo:
            return "todos/create?target=floater"
        case .overdueTodos:
            return "todos/overdue"
        case .scheduledTodos:
            return "todos/scheduled"
        case let .allTodos(highlightTodoId):
            if let highlightTodoId, !highlightTodoId.isEmpty {
                return "todos/all?highlightTodoId=\(highlightTodoId)"
            }
            return "todos/all"
        case .priorityTodos:
            return "todos/priority"
        case .floaterTodos:
            return "floater"
        case let .floaterListTodos(listId, listName):
            return "floater/list/\(listId)/\(listName)"
        case let .listTodos(listId, listName):
            return "todos/list/\(listId)/\(listName)"
        case .completed:
            return "completed"
        case .calendar:
            return "calendar"
        case .settings:
            return "settings"
        case .latestRelease:
            return "latest-release"
        case .forgotPassword:
            return "forgot-password"
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
        case "latest-release":
            return .latestRelease
        case "forgot-password":
            return .forgotPassword
        case "todos":
            let second = components.dropFirst().first ?? ""
            switch second {
            case "today":
                return .todayTodos
            case "create":
                let target = URLComponents(url: url, resolvingAgainstBaseURL: false)?
                    .queryItems?
                    .first(where: { $0.name == "target" })?
                    .value
                switch target {
                case "today":
                    return .createTodayTodo
                case "floater":
                    return .createFloaterTodo
                default:
                    return nil
                }
            case "overdue":
                return .overdueTodos
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
            case "floater":
                let remaining = Array(components.dropFirst(2))
                if remaining.first == "list", remaining.count >= 3 {
                    return .floaterListTodos(listId: remaining[1], listName: remaining[2])
                }
                return .floaterTodos
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
            if first == "floater" {
                let remaining = Array(components.dropFirst())
                if remaining.first == "list", remaining.count >= 3 {
                    return .floaterListTodos(listId: remaining[1], listName: remaining[2])
                }
                return .floaterTodos
            }
            return nil
        }
    }
}
