import Foundation
import Observation

@MainActor
@Observable
final class TodoListViewModel {
    private let container: AppContainer

    var isLoading = false
    var title: String
    var mode: TodoListMode
    var listId: String?
    var lists: [ListSummary] = []
    var items: [TodoItem] = []
    var errorMessage: String?
    var aiSummaryEnabled = true
    var summaryText: String?
    var summarySource: String?
    var summaryGeneratedAt: String?
    var summaryError: String?
    var summaryConnectivityError = false
    var isSummarizing = false

    private let listName: String?
    @ObservationIgnored nonisolated(unsafe) private var observationTask: Task<Void, Never>?

    init(container: AppContainer, mode: TodoListMode, listId: String?, listName: String?) {
        self.container = container
        self.mode = mode
        self.listId = listId
        self.listName = listName
        title = listName ?? mode.title
        TdayTelemetry.addBreadcrumb(
            "todo_list.load",
            data: modeTelemetryData(mode: mode, scopedList: listId?.isEmpty == false)
        )
        hydrateFromCache()
        observeCacheChanges()
    }

    deinit {
        observationTask?.cancel()
    }

    func refresh() async {
        TdayTelemetry.addBreadcrumb("todo_list.refresh", data: modeTelemetryData())
        isLoading = true
        let result = await container.syncAndRefresh(
            force: true,
            replayPendingMutations: true,
            connectionProbeTimeoutSeconds: SyncAndRefreshUseCase.userRefreshConnectionTimeoutSeconds
        )
        if case let .failure(error) = result, !isLikelyConnectivityIssue(error) {
            errorMessage = userFacingMessage(for: error, fallback: "Failed to load tasks.")
        }
        hydrateFromCache()
        isLoading = false
    }

    func summarizeCurrentMode() async {
        guard aiSummaryEnabled else {
            summaryError = "AI summary is disabled by admin"
            return
        }
        guard mode != .list && mode != .overdue && mode != .floater else {
            summaryError = "Summary is available for Today, Scheduled, All, and Priority"
            return
        }

        isSummarizing = true
        summaryText = nil
        summarySource = nil
        summaryGeneratedAt = nil
        summaryError = nil
        summaryConnectivityError = false
        do {
            let response = try await container.todoRepository.summarizeTodos(mode: mode, listId: listId)
            summaryText = response.summary
            summarySource = response.source
            summaryGeneratedAt = response.generatedAt
        } catch {
            if isLikelyConnectivityIssue(error) {
                summaryConnectivityError = true
            } else {
                summaryError = userFacingMessage(for: error, fallback: "Could not summarize tasks.")
            }
        }
        isSummarizing = false
    }

    func dismissSummaryConnectivityError() {
        summaryConnectivityError = false
    }

    func addTask(_ payload: CreateTaskPayload) async {
        TdayTelemetry.addBreadcrumb("task.create", data: taskTelemetryData(mode: mode, payload: payload))
        do {
            if mode == .floater {
                try await container.todoRepository.createFloater(payload: payload)
            } else {
                try await container.createTodo(payload)
            }
            hydrateFromCache()
        } catch {
            errorMessage = userFacingMessage(for: error, fallback: "Could not create task.")
        }
    }

    func updateTask(_ todo: TodoItem, payload: CreateTaskPayload) async {
        TdayTelemetry.addBreadcrumb("task.update", data: taskTelemetryData(mode: mode, payload: payload))
        do {
            if mode == .floater {
                try await container.todoRepository.updateFloater(todo, payload: payload)
            } else {
                try await container.todoRepository.updateTodo(todo, payload: payload)
            }
            hydrateFromCache()
        } catch {
            errorMessage = userFacingMessage(for: error, fallback: "Could not update task.")
        }
    }

    func moveTask(_ todo: TodoItem, toDay targetDay: Date, scope: TaskRescheduleScope) async {
        let calendar = Calendar.current
        guard let due = todo.due,
              !calendar.isDate(due, inSameDayAs: targetDay) else {
            return
        }

        guard let movedDue = movedDuePreservingTime(due: due, targetDay: targetDay, calendar: calendar) else {
            return
        }

        var telemetryData = taskTelemetryData(mode: mode, scope: scope)
        telemetryData["source"] = "todo_list"
        TdayTelemetry.addBreadcrumb("task.reschedule", data: telemetryData)
        do {
            try await container.todoRepository.moveTodo(
                todo.repositoryTargetForReschedule(scope: scope),
                due: movedDue
            )
            hydrateFromCache()
        } catch {
            errorMessage = userFacingMessage(for: error, fallback: "Could not update task.")
        }
    }

    func complete(_ todo: TodoItem) async {
        TdayTelemetry.addBreadcrumb("task.complete", data: taskTelemetryData(mode: mode))
        do {
            if mode == .floater {
                try await container.todoRepository.completeFloater(todo)
            } else {
                try await container.completeTodo(todo)
            }
            hydrateFromCache()
        } catch {
            errorMessage = userFacingMessage(for: error, fallback: "Could not complete task.")
        }
    }

    func delete(_ todo: TodoItem) async {
        TdayTelemetry.addBreadcrumb("task.delete", data: taskTelemetryData(mode: mode))
        do {
            if mode == .floater {
                try await container.todoRepository.deleteFloater(todo)
            } else {
                try await container.todoRepository.deleteTodo(todo)
            }
            hydrateFromCache()
        } catch {
            errorMessage = userFacingMessage(for: error, fallback: "Could not delete task.")
        }
    }

    func updateListSettings(name: String, color: String?, iconKey: String?) async {
        guard let listId else { return }
        TdayTelemetry.addBreadcrumb("list.update", data: listTelemetryData(color: color, iconKey: iconKey))
        do {
            if mode == .floater {
                try await container.floaterListRepository.updateList(listId: listId, name: name, color: color, iconKey: iconKey)
            } else {
                try await container.listRepository.updateList(listId: listId, name: name, color: color, iconKey: iconKey)
            }
            hydrateFromCache()
            title = name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? (listName ?? mode.title) : name
        } catch {
            errorMessage = userFacingMessage(for: error, fallback: "Could not update list.")
        }
    }

    func createList(name: String, color: String?, iconKey: String?) async {
        TdayTelemetry.addBreadcrumb("list.create", data: listTelemetryData(color: color, iconKey: iconKey))
        do {
            if mode == .floater {
                try await container.floaterListRepository.createList(name: name, color: color, iconKey: iconKey)
            } else {
                try await container.listRepository.createList(name: name, color: color, iconKey: iconKey)
            }
            hydrateFromCache()
        } catch {
            errorMessage = userFacingMessage(for: error, fallback: "Could not create list.")
        }
    }

    func deleteList(onOptimisticDelete: @escaping () -> Void) async {
        guard let listId else { return }
        TdayTelemetry.addBreadcrumb("list.delete", data: listTelemetryData(color: nil, iconKey: nil))
        do {
            let optimisticDelete = {
                self.lists.removeAll { $0.id == listId }
                self.items.removeAll { $0.listId == listId }
                self.errorMessage = nil
                onOptimisticDelete()
            }
            if mode == .floater {
                try await container.floaterListRepository.deleteList(
                    listId: listId,
                    onOptimisticDelete: optimisticDelete
                )
            } else {
                try await container.listRepository.deleteList(
                    listId: listId,
                    onOptimisticDelete: optimisticDelete
                )
            }
        } catch {
            errorMessage = userFacingMessage(for: error, fallback: "Could not delete list.")
            hydrateFromCache()
        }
    }

    func parseTaskTitleNlp(text: String, referenceDueEpochMs: Int64) async -> TodoTitleNlpResponse? {
        await container.todoRepository.parseTodoTitleNlp(text: text, referenceDueEpochMs: referenceDueEpochMs)
    }

    private func hydrateFromCache() {
        if mode == .floater {
            lists = container.floaterListRepository.fetchListsSnapshot()
        } else {
            lists = container.listRepository.fetchListsSnapshot()
        }
        items = container.todoRepository.fetchTodosSnapshot(mode: mode, listId: listId)
        aiSummaryEnabled = container.settingsRepository.isAiSummaryEnabledSnapshot()
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

    private func modeTelemetryData(mode: TodoListMode? = nil, scopedList: Bool? = nil) -> [String: Any] {
        [
            "mode": (mode ?? self.mode).summaryMode,
            "scoped_list": scopedList ?? !(listId ?? "").isEmpty
        ]
    }

    private func taskTelemetryData(
        mode: TodoListMode,
        payload: CreateTaskPayload? = nil,
        scope: TaskRescheduleScope? = nil
    ) -> [String: Any] {
        var data = modeTelemetryData(mode: mode)
        if let payload {
            data["has_due"] = payload.due != nil
            data["has_repeat"] = !(payload.rrule ?? "").isEmpty
            data["has_list"] = !(payload.listId ?? "").isEmpty
            data["has_description"] = !(payload.description ?? "").isEmpty
        }
        if let scope {
            data["scope"] = scope.rawValue
        }
        return data
    }

    private func listTelemetryData(color: String?, iconKey: String?) -> [String: Any] {
        [
            "kind": mode == .floater ? "floater" : "scheduled",
            "scoped_list": !(listId ?? "").isEmpty,
            "has_color": !(color ?? "").isEmpty,
            "has_icon": !(iconKey ?? "").isEmpty
        ]
    }
}
