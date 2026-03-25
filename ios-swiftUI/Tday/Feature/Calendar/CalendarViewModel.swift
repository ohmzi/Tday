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

    private var observationTask: Task<Void, Never>?

    init(container: AppContainer) {
        self.container = container
        hydrateFromCache()
        observeCacheChanges()
    }

    deinit {
        observationTask?.cancel()
    }

    func refresh() async {
        isLoading = true
        let result = await container.syncAndRefresh(force: true, replayPendingMutations: false)
        if case let .failure(error) = result, !isLikelyConnectivityIssue(error) {
            errorMessage = error.localizedDescription
        }
        hydrateFromCache()
        isLoading = false
    }

    func createTask(_ payload: CreateTaskPayload) async {
        do {
            try await container.createTodo(payload)
            hydrateFromCache()
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func complete(_ todo: TodoItem) async {
        do {
            try await container.completeTodo(todo)
            hydrateFromCache()
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func uncomplete(_ item: CompletedItem) async {
        do {
            try await container.completedRepository.uncomplete(item)
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

    func delete(_ todo: TodoItem) async {
        do {
            try await container.todoRepository.deleteTodo(todo)
            hydrateFromCache()
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func parseTaskTitleNlp(text: String, referenceStartEpochMs: Int64, referenceDueEpochMs: Int64) async -> TodoTitleNlpResponse? {
        await container.todoRepository.parseTodoTitleNlp(text: text, referenceStartEpochMs: referenceStartEpochMs, referenceDueEpochMs: referenceDueEpochMs)
    }

    private func hydrateFromCache() {
        items = container.todoRepository.fetchTodosSnapshot(mode: .scheduled)
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
}
