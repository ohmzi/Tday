import CarPlay
import Foundation
import UIKit

@MainActor
final class CarPlayCoordinator {
    private let interfaceController: CPInterfaceController
    private let container: AppContainer
    private var mode: CarTaskMode = .today
    private let dueFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.dateStyle = .none
        formatter.timeStyle = .short
        return formatter
    }()

    init(interfaceController: CPInterfaceController, container: AppContainer) {
        self.interfaceController = interfaceController
        self.container = container
    }

    func start() {
        TdayTelemetry.addBreadcrumb(
            "car_surface.open",
            data: ["platform": "ios", "mode": mode.telemetryName]
        )
        reload(animated: false)
    }

    private func selectMode(_ nextMode: CarTaskMode) {
        guard mode != nextMode else { return }
        mode = nextMode
        TdayTelemetry.addBreadcrumb(
            "car_surface.switch_mode",
            data: ["platform": "ios", "mode": mode.telemetryName]
        )
        reload(animated: true)
    }

    private func reload(animated: Bool) {
        let todos = container.todoRepository.fetchTodosSnapshot(mode: mode.todoListMode)
        let state = buildCarTaskSurfaceState(
            mode: mode,
            todos: todos,
            dueLabelFor: { [dueFormatter] todo in
                todo.due.map { dueFormatter.string(from: $0) }
            }
        )
        let template = CPListTemplate(
            title: state.title,
            sections: [makeSection(for: state)]
        )
        configureNavigationButtons(on: template)
        interfaceController.setRootTemplate(template, animated: animated)
    }

    private func makeSection(for state: CarTaskSurfaceState) -> CPListSection {
        if state.items.isEmpty {
            let item = CPListItem(text: state.emptyTitle, detailText: nil)
            item.isEnabled = false
            return CPListSection(items: [item])
        }

        return CPListSection(
            items: state.items.map { task in
                let item = CPListItem(text: task.title, detailText: task.detailText)
                item.handler = { [weak self] _, completion in
                    Task { @MainActor in
                        self?.presentTaskActions(for: task)
                        completion()
                    }
                }
                return item
            }
        )
    }

    private func configureNavigationButtons(on template: CPListTemplate) {
        let todayButton = CPBarButton(image: UIImage(systemName: "house.fill") ?? UIImage()) { [weak self] _ in
            self?.selectMode(.today)
        }
        todayButton.isEnabled = mode != .today

        let floaterButton = CPBarButton(image: UIImage(systemName: "leaf") ?? UIImage()) { [weak self] _ in
            self?.selectMode(.floater)
        }
        floaterButton.isEnabled = mode != .floater

        let plusButton = CPBarButton(image: UIImage(systemName: "plus") ?? UIImage()) { [weak self] _ in
            self?.presentCreateOptions()
        }

        template.leadingNavigationBarButtons = [todayButton, floaterButton]
        template.trailingNavigationBarButtons = [plusButton]
    }

    private func presentTaskActions(for task: CarTaskItem) {
        let completeAction = CPAlertAction(title: "Complete", style: .default) { [weak self] _ in
            Task { @MainActor in
                if let interfaceController = self?.interfaceController {
                    _ = try? await interfaceController.dismissTemplate(animated: true)
                }
                await self?.complete(task)
            }
        }
        let cancelAction = CPAlertAction(title: "Cancel", style: .cancel) { [weak self] _ in
            Task { @MainActor in
                if let interfaceController = self?.interfaceController {
                    _ = try? await interfaceController.dismissTemplate(animated: true)
                }
            }
        }
        let template = CPActionSheetTemplate(
            title: "Complete this task?",
            message: task.detailText,
            actions: [completeAction, cancelAction]
        )
        interfaceController.presentTemplate(template, animated: true)
    }

    private func complete(_ task: CarTaskItem) async {
        let result = await Result {
            if task.mode == .floater {
                try await container.todoRepository.completeFloater(task.source)
            } else {
                try await container.completeTodo(task.source)
            }
        }
        TdayTelemetry.addBreadcrumb(
            "car_task.complete",
            data: [
                "platform": "ios",
                "mode": task.mode.telemetryName,
                "result": result.isSuccess ? "success" : "failure"
            ]
        )
        if case let .failure(error) = result {
            TdayTelemetry.capture(
                error,
                operation: "car_task.complete",
                data: ["platform": "ios", "mode": task.mode.telemetryName]
            )
        }
        reload(animated: true)
    }

    private func presentCreateOptions() {
        let continueAction = CPAlertAction(title: "Continue on iPhone", style: .default) { [weak self] _ in
            guard let self else { return }
            Task { @MainActor in
                _ = try? await self.interfaceController.dismissTemplate(animated: true)
                await UIApplication.shared.open(self.mode.createDeepLink)
            }
        }
        let cancelAction = CPAlertAction(title: "Cancel", style: .cancel) { [weak self] _ in
            Task { @MainActor in
                if let interfaceController = self?.interfaceController {
                    _ = try? await interfaceController.dismissTemplate(animated: true)
                }
            }
        }
        let template = CPActionSheetTemplate(
            title: mode == .today ? "Add T'Day task" : "Add floater",
            message: "Use Siri with the T'Day shortcuts, or continue on iPhone.",
            actions: [continueAction, cancelAction]
        )
        interfaceController.presentTemplate(template, animated: true)
    }
}

private extension Result where Success == Void, Failure == Error {
    init(_ operation: () async throws -> Void) async {
        do {
            try await operation()
            self = .success(())
        } catch {
            self = .failure(error)
        }
    }

    var isSuccess: Bool {
        if case .success = self { return true }
        return false
    }
}
