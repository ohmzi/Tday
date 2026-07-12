import Foundation
import Observation

@MainActor
@Observable
final class HomeViewModel {
    private let container: AppContainer
    private static let recentSuccessfulSyncSkipWindowMs: Int64 = 8_000

    var isLoading = true
    var summary = DashboardSummary(todayCount: 0, scheduledCount: 0, allCount: 0, priorityCount: 0, floaterCount: 0, completedCount: 0, lists: [])
    var searchableTodos: [TodoItem] = []
    var todayTodos: [TodoItem] = []
    var errorMessage: String?
    var aiSummaryEnabled = true
    var summaryText: String?
    var summarySource: String?
    var summaryGeneratedAt: String?
    var summaryError: String?
    var isSummarizing = false

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

        let loadCachedState: @MainActor () -> OfflineSyncState = container.cacheManager.loadOfflineState
        let cachedState = loadCachedState()
        if shouldUseRecentSuccessfulSync(cachedState) {
            refreshFromCache(snapshot: container.todoRepository.makeDashboardCacheSnapshot(from: cachedState))
            return
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
        refreshFromCache(snapshot: container.todoRepository.fetchDashboardCacheSnapshot())
    }

    private func refreshFromCache(snapshot: TodoDashboardCacheSnapshot) {
        summary = snapshot.summary
        searchableTodos = snapshot.searchableTodos
        todayTodos = snapshot.todayTodos
        aiSummaryEnabled = snapshot.aiSummaryEnabled
        isLoading = activeLoadingRefreshes > 0
        errorMessage = nil
    }

    private func shouldUseRecentSuccessfulSync(_ state: OfflineSyncState) -> Bool {
        guard state.pendingMutations.isEmpty, state.lastSuccessfulSyncEpochMs > 0 else {
            return false
        }
        return Date().epochMilliseconds - state.lastSuccessfulSyncEpochMs < Self.recentSuccessfulSyncSkipWindowMs
    }

    func summarizeToday() async {
        guard !isSummarizing else { return }
        guard aiSummaryEnabled else {
            summaryError = "Summary is turned off in Settings."
            return
        }
        isSummarizing = true
        summaryText = nil
        summarySource = nil
        summaryGeneratedAt = nil
        summaryError = nil
        do {
            let response = try await container.todoRepository.summarizeTodos(mode: .today)
            summaryText = response.summary
            summarySource = response.source
            summaryGeneratedAt = response.generatedAt
        } catch {
            if isLikelyConnectivityIssue(error) {
                summaryError = "No summary available while offline."
            } else {
                summaryError = userFacingMessage(for: error, fallback: "Could not summarize tasks.")
            }
        }
        isSummarizing = false
    }

    func createList(name: String, color: String?, iconKey: String?) async {
        do {
            try await container.listRepository.createList(name: name, color: color, iconKey: iconKey)
            refreshFromCache()
        } catch {
            container.snackbarManager.show(
                userFacingMessage(for: error, fallback: "Could not create list."),
                kind: .error
            )
        }
    }

    func createTask(_ payload: CreateTaskPayload) async {
        do {
            try await container.createTodo(payload)
            refreshFromCache()
        } catch {
            container.snackbarManager.show(
                userFacingMessage(for: error, fallback: "Could not create task."),
                kind: .error
            )
        }
    }

    /// Delayed-commit complete (see TodoListViewModel.complete): hide the row
    /// now, show an undoable toast, and only commit the real completion once the
    /// undo window expires. Undo re-reads the untouched cache to restore it.
    func complete(_ todo: TodoItem) async {
        let container = container
        todayTodos.removeAll { $0.id == todo.id }
        searchableTodos.removeAll { $0.id == todo.id }
        container.undoableDeleteScheduler.schedule(
            message: L("Task completed"),
            restore: { [weak self] in self?.refreshFromCache() },
            commit: { [weak self] in
                do {
                    try await container.completeTodo(todo)
                } catch {
                    container.snackbarManager.show(
                        userFacingMessage(for: error, fallback: "Could not complete task."),
                        kind: .error
                    )
                }
                self?.refreshFromCache()
            }
        )
    }

    /// Delayed-commit delete: the task is staged out of the local cache
    /// immediately, an undoable toast is shown, and the real (server) delete
    /// only commits once the undo window expires. The closures capture
    /// `container` rather than `self` so a pending commit survives this
    /// view model being deallocated.
    func delete(_ todo: TodoItem) async {
        todayTodos.removeAll { $0.id == todo.id }
        let container = container
        let staged = container.todoRepository.stageDeleteTodo(todo)
        refreshFromCache()
        container.undoableDeleteScheduler.schedule(
            message: L("Task deleted"),
            restore: {
                container.todoRepository.undoStagedTodo(staged)
            },
            commit: {
                do {
                    try await container.todoRepository.deleteTodo(todo)
                } catch {
                    container.snackbarManager.show(
                        userFacingMessage(for: error, fallback: "Could not delete task."),
                        kind: .error
                    )
                }
            }
        )
    }

    func updateTask(_ todo: TodoItem, payload: CreateTaskPayload) async {
        do {
            try await container.todoRepository.updateTodo(todo, payload: payload)
            refreshFromCache()
        } catch {
            container.snackbarManager.show(
                userFacingMessage(for: error, fallback: "Could not update task."),
                kind: .error
            )
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
