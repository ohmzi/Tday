import Foundation
import Observation

@MainActor
@Observable
final class CalendarViewModel {
    private let container: AppContainer

    var isLoading = false
    var items: [TodoItem] = []
    var completedItems: [CompletedItem] = []
    var lists: [ListSummary] = []
    var errorMessage: String?

    @ObservationIgnored nonisolated(unsafe) private var observationTask: Task<Void, Never>?

    init(container: AppContainer) {
        self.container = container
        TdayTelemetry.addBreadcrumb("calendar.load", data: calendarTelemetryData())
        hydrateFromCache()
        observeCacheChanges()
    }

    deinit {
        observationTask?.cancel()
    }

    func refresh() async {
        TdayTelemetry.addBreadcrumb("calendar.refresh", data: calendarTelemetryData())
        isLoading = true
        let result = await container.syncAndRefresh(
            force: true,
            replayPendingMutations: false,
            connectionProbeTimeoutSeconds: SyncAndRefreshUseCase.userRefreshConnectionTimeoutSeconds
        )
        if case let .failure(error) = result, !isLikelyConnectivityIssue(error) {
            errorMessage = userFacingMessage(for: error, fallback: "Failed to load calendar.")
        }
        hydrateFromCache()
        isLoading = false
    }

    func createTask(_ payload: CreateTaskPayload) async {
        TdayTelemetry.addBreadcrumb("calendar.task.create", data: calendarTelemetryData(payload: payload))
        do {
            try await container.createTodo(payload)
            hydrateFromCache()
        } catch {
            container.snackbarManager.show(userFacingMessage(for: error, fallback: "Could not create task."), kind: .error)
        }
    }

    func complete(_ todo: TodoItem) async {
        TdayTelemetry.addBreadcrumb("calendar.task.complete", data: calendarTelemetryData())
        do {
            try await container.completeTodo(todo)
            hydrateFromCache()
        } catch {
            container.snackbarManager.show(userFacingMessage(for: error, fallback: "Could not complete task."), kind: .error)
        }
    }

    func uncomplete(_ item: CompletedItem) async {
        TdayTelemetry.addBreadcrumb("calendar.task.restore", data: calendarTelemetryData())
        do {
            try await container.completedRepository.uncomplete(item)
            hydrateFromCache()
        } catch {
            container.snackbarManager.show(userFacingMessage(for: error, fallback: "Could not restore task."), kind: .error)
        }
    }

    func updateTask(_ todo: TodoItem, payload: CreateTaskPayload) async {
        TdayTelemetry.addBreadcrumb("calendar.task.update", data: calendarTelemetryData(payload: payload))
        do {
            try await container.todoRepository.updateTodo(todo, payload: payload)
            hydrateFromCache()
        } catch {
            container.snackbarManager.show(userFacingMessage(for: error, fallback: "Could not update task."), kind: .error)
        }
    }

    func moveTask(_ todo: TodoItem, toDay targetDay: Date, scope: TaskRescheduleScope) async {
        let calendar = Calendar.current
        guard let due = todo.due,
              !calendar.isDate(due, inSameDayAs: targetDay),
              let movedDue = movedDuePreservingTime(due: due, targetDay: targetDay, calendar: calendar) else {
            return
        }

        var telemetryData = calendarTelemetryData()
        telemetryData["scope"] = scope.rawValue
        TdayTelemetry.addBreadcrumb("calendar.task.reschedule", data: telemetryData)
        do {
            try await container.todoRepository.moveTodo(
                todo.repositoryTargetForReschedule(scope: scope),
                due: movedDue
            )
            hydrateFromCache()
        } catch {
            container.snackbarManager.show(userFacingMessage(for: error, fallback: "Could not update task."), kind: .error)
        }
    }

    func delete(_ todo: TodoItem) async {
        TdayTelemetry.addBreadcrumb("calendar.task.delete", data: calendarTelemetryData())
        do {
            try await container.todoRepository.deleteTodo(todo)
            hydrateFromCache()
            container.snackbarManager.show(L("Task deleted"), kind: .success)
        } catch {
            container.snackbarManager.show(userFacingMessage(for: error, fallback: "Could not delete task."), kind: .error)
        }
    }

    func parseTaskTitleNlp(text: String, referenceDueEpochMs: Int64) async -> TodoTitleNlpResponse? {
        await container.todoRepository.parseTodoTitleNlp(text: text, referenceDueEpochMs: referenceDueEpochMs)
    }

    private func hydrateFromCache() {
        items = container.todoRepository.fetchTodosSnapshot(mode: .all).filter { $0.due != nil }
        completedItems = container.completedRepository.fetchCompletedItemsSnapshot()
        lists = container.listRepository.fetchListsSnapshot()
        errorMessage = nil
    }

    private func observeCacheChanges() {
        observationTask = Task {
            for await _ in NotificationCenter.default.notifications(named: .offlineCacheDidChange) {
                await MainActor.run {
                    self.hydrateFromCache()
                }
            }
        }
    }

    private func calendarTelemetryData(payload: CreateTaskPayload? = nil) -> [String: Any] {
        var data: [String: Any] = [
            "surface": "calendar",
            "scheduled_items": items.count,
            "completed_items": completedItems.count
        ]
        if let payload {
            data["has_due"] = payload.due != nil
            data["has_repeat"] = !(payload.rrule ?? "").isEmpty
            data["has_list"] = !(payload.listId ?? "").isEmpty
            data["has_description"] = !(payload.description ?? "").isEmpty
        }
        return data
    }
}
