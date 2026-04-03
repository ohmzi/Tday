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
    private var observationTask: Task<Void, Never>?

    init(container: AppContainer, mode: TodoListMode, listId: String?, listName: String?) {
        self.container = container
        self.mode = mode
        self.listId = listId
        self.listName = listName
        title = listName ?? mode.title
        hydrateFromCache()
        observeCacheChanges()
    }

    deinit {
        observationTask?.cancel()
    }

    func refresh() async {
        isLoading = true
        let result = await container.syncAndRefresh(force: true, replayPendingMutations: true)
        if case let .failure(error) = result, !isLikelyConnectivityIssue(error) {
            errorMessage = error.localizedDescription
        }
        hydrateFromCache()
        isLoading = false
    }

    func summarizeCurrentMode() async {
        guard aiSummaryEnabled else {
            summaryError = "AI summary is disabled by admin"
            return
        }
        guard mode != .list && mode != .overdue else {
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
                summaryError = error.localizedDescription
            }
        }
        isSummarizing = false
    }

    func dismissSummaryConnectivityError() {
        summaryConnectivityError = false
    }

    func addTask(_ payload: CreateTaskPayload) async {
        do {
            try await container.createTodo(payload)
            hydrateFromCache()
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func updateTask(_ todo: TodoItem, payload: CreateTaskPayload) async {
        do {
            try await container.todoRepository.updateTodo(todo, payload: payload)
            hydrateFromCache()
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func moveTask(_ todo: TodoItem, toDay targetDay: Date) async {
        let calendar = Calendar.current
        guard !calendar.isDate(todo.due, inSameDayAs: targetDay) else {
            return
        }

        let duration = max(todo.due.timeIntervalSince(todo.dtstart), 60)
        let dueTimeComponents = calendar.dateComponents([.hour, .minute, .second, .nanosecond], from: todo.due)
        var targetComponents = calendar.dateComponents([.year, .month, .day], from: targetDay)
        targetComponents.timeZone = calendar.timeZone
        targetComponents.hour = dueTimeComponents.hour
        targetComponents.minute = dueTimeComponents.minute
        targetComponents.second = dueTimeComponents.second
        targetComponents.nanosecond = dueTimeComponents.nanosecond

        guard let movedDue = calendar.date(from: targetComponents) else {
            return
        }

        let movedStart = movedDue.addingTimeInterval(-duration)
        await updateTask(
            todo,
            payload: CreateTaskPayload(
                title: todo.title,
                description: todo.description,
                priority: todo.priority,
                dtstart: movedStart,
                due: movedDue,
                rrule: todo.rrule,
                listId: todo.listId
            )
        )
    }

    func complete(_ todo: TodoItem) async {
        do {
            try await container.completeTodo(todo)
            hydrateFromCache()
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func delete(_ todo: TodoItem) async {
        do {
            try await container.todoRepository.deleteTodo(todo)
            hydrateFromCache()
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func updateListSettings(name: String, color: String?, iconKey: String?) async {
        guard let listId else { return }
        do {
            try await container.listRepository.updateList(listId: listId, name: name, color: color, iconKey: iconKey)
            hydrateFromCache()
            title = name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? (listName ?? mode.title) : name
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func parseTaskTitleNlp(text: String, referenceStartEpochMs: Int64, referenceDueEpochMs: Int64) async -> TodoTitleNlpResponse? {
        await container.todoRepository.parseTodoTitleNlp(text: text, referenceStartEpochMs: referenceStartEpochMs, referenceDueEpochMs: referenceDueEpochMs)
    }

    private func hydrateFromCache() {
        lists = container.listRepository.fetchListsSnapshot()
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
}
