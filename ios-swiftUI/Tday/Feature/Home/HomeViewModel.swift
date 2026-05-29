import Foundation
import Observation

@MainActor
@Observable
final class HomeViewModel {
    private let container: AppContainer

    var isLoading = true
    var summary = DashboardSummary(todayCount: 0, scheduledCount: 0, allCount: 0, priorityCount: 0, floaterCount: 0, completedCount: 0, lists: [])
    var searchableTodos: [TodoItem] = []
    var todayTodos: [TodoItem] = []
    var errorMessage: String?

    var lists: [ListSummary] { summary.lists }

    @ObservationIgnored nonisolated(unsafe) private var observationTask: Task<Void, Never>?
    @ObservationIgnored private var activeLoadingRefreshes = 0

    init(container: AppContainer) {
        self.container = container
        refreshFromCache()
        observeCacheChanges()
    }

    deinit {
        observationTask?.cancel()
    }

    func refresh() async {
        activeLoadingRefreshes += 1
        isLoading = true
        defer {
            activeLoadingRefreshes = max(activeLoadingRefreshes - 1, 0)
            if activeLoadingRefreshes == 0 {
                isLoading = false
            }
        }
        let result = await container.syncAndRefresh(
            force: true,
            replayPendingMutations: true,
            connectionProbeTimeoutSeconds: SyncAndRefreshUseCase.userRefreshConnectionTimeoutSeconds
        )
        if case let .failure(error) = result, !isLikelyConnectivityIssue(error) {
            errorMessage = userFacingMessage(for: error, fallback: "Failed to load dashboard.")
        }
        refreshFromCache()
    }

    func refreshFromCache() {
        summary = container.todoRepository.fetchDashboardSummarySnapshot()
        searchableTodos = container.todoRepository.fetchTodosSnapshot(mode: .all)
        todayTodos = container.todoRepository.fetchTodosSnapshot(mode: .today)
        isLoading = activeLoadingRefreshes > 0
        errorMessage = nil
    }

    func createList(name: String, color: String?, iconKey: String?) async {
        do {
            try await container.listRepository.createList(name: name, color: color, iconKey: iconKey)
            refreshFromCache()
        } catch {
            errorMessage = userFacingMessage(for: error, fallback: "Could not create list.")
        }
    }

    func createTask(_ payload: CreateTaskPayload) async {
        do {
            try await container.createTodo(payload)
            refreshFromCache()
        } catch {
            errorMessage = userFacingMessage(for: error, fallback: "Could not create task.")
        }
    }

    func complete(_ todo: TodoItem) async {
        todayTodos.removeAll { $0.id == todo.id }
        do {
            try await container.completeTodo(todo)
            refreshFromCache()
        } catch {
            errorMessage = userFacingMessage(for: error, fallback: "Could not complete task.")
            refreshFromCache()
        }
    }

    func delete(_ todo: TodoItem) async {
        todayTodos.removeAll { $0.id == todo.id }
        do {
            try await container.todoRepository.deleteTodo(todo)
            refreshFromCache()
        } catch {
            errorMessage = userFacingMessage(for: error, fallback: "Could not delete task.")
            refreshFromCache()
        }
    }

    func updateTask(_ todo: TodoItem, payload: CreateTaskPayload) async {
        do {
            try await container.todoRepository.updateTodo(todo, payload: payload)
            refreshFromCache()
        } catch {
            errorMessage = userFacingMessage(for: error, fallback: "Could not update task.")
        }
    }

    func parseTaskTitleNlp(text: String, referenceDueEpochMs: Int64) async -> TodoTitleNlpResponse? {
        await container.todoRepository.parseTodoTitleNlp(text: text, referenceDueEpochMs: referenceDueEpochMs)
    }

    private func observeCacheChanges() {
        observationTask = Task {
            for await _ in NotificationCenter.default.notifications(named: .offlineCacheDidChange) {
                await MainActor.run {
                    self.refreshFromCache()
                }
            }
        }
    }
}
