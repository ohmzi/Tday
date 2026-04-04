import Foundation
import Observation

@MainActor
@Observable
final class HomeViewModel {
    private let container: AppContainer

    var isLoading = true
    var summary = DashboardSummary(todayCount: 0, scheduledCount: 0, allCount: 0, priorityCount: 0, completedCount: 0, lists: [])
    var searchableTodos: [TodoItem] = []
    var errorMessage: String?

    private var observationTask: Task<Void, Never>?

    init(container: AppContainer) {
        self.container = container
        refreshFromCache()
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
        refreshFromCache()
    }

    func refreshFromCache() {
        summary = container.todoRepository.fetchDashboardSummarySnapshot()
        searchableTodos = container.todoRepository.fetchTodosSnapshot(mode: .all)
        isLoading = false
        errorMessage = nil
    }

    func createList(name: String, color: String?, iconKey: String?) async {
        do {
            try await container.listRepository.createList(name: name, color: color, iconKey: iconKey)
            refreshFromCache()
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func createTask(_ payload: CreateTaskPayload) async {
        do {
            try await container.createTodo(payload)
            refreshFromCache()
        } catch {
            errorMessage = error.localizedDescription
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
