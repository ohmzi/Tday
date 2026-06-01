import AppIntents
import Foundation

enum CarTaskIntentTarget: String, AppEnum {
    case today
    case floater

    static var typeDisplayRepresentation = TypeDisplayRepresentation(name: "T'Day Destination")
    static var caseDisplayRepresentations: [CarTaskIntentTarget: DisplayRepresentation] = [
        .today: "T'Day",
        .floater: "Floater"
    ]

    var mode: CarTaskMode {
        switch self {
        case .today:
            return .today
        case .floater:
            return .floater
        }
    }
}

struct CreateCarTaskIntent: AppIntent {
    static var title: LocalizedStringResource = "Add T'Day Task"
    static var description = IntentDescription("Adds a Today task or floater to T'Day by voice.")
    static var openAppWhenRun = false

    @Parameter(title: "Task")
    var taskTitle: String

    @Parameter(title: "Destination", default: .today)
    var destination: CarTaskIntentTarget

    init() {}

    init(taskTitle: String, destination: CarTaskIntentTarget) {
        self.taskTitle = taskTitle
        self.destination = destination
    }

    @MainActor
    func perform() async throws -> some IntentResult & ProvidesDialog {
        let trimmedTitle = taskTitle.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmedTitle.isEmpty else {
            TdayTelemetry.addBreadcrumb(
                "car_task.voice_create",
                data: ["platform": "ios", "mode": destination.mode.telemetryName, "result": "blank"]
            )
            return .result(dialog: "I did not hear a task title.")
        }

        let mode = destination.mode
        let payload = mode.createPayload(title: trimmedTitle)
        do {
            if mode == .floater {
                try await AppContainer.shared.todoRepository.createFloater(payload: payload)
            } else {
                try await AppContainer.shared.todoRepository.createTodo(payload: payload)
            }
            TdayTelemetry.addBreadcrumb(
                "car_task.voice_create",
                data: ["platform": "ios", "mode": mode.telemetryName, "result": "success"]
            )
            return .result(dialog: mode == .floater ? "Added to Floater." : "Added to T'Day.")
        } catch {
            TdayTelemetry.capture(
                error,
                operation: "car_task.voice_create",
                data: ["platform": "ios", "mode": mode.telemetryName]
            )
            throw error
        }
    }
}

struct TdayCarAppShortcuts: AppShortcutsProvider {
    static var appShortcuts: [AppShortcut] {
        AppShortcut(
            intent: CreateCarTaskIntent(),
            phrases: [
                "Add task in \(.applicationName)",
                "Add T'Day task in \(.applicationName)"
            ],
            shortTitle: "Add Task",
            systemImageName: "plus"
        )
        AppShortcut(
            intent: CreateCarTaskIntent(taskTitle: "", destination: .floater),
            phrases: [
                "Add floater in \(.applicationName)",
                "Add anytime task in \(.applicationName)"
            ],
            shortTitle: "Add Floater",
            systemImageName: "circle.dotted"
        )
    }
}
