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
            errorMessage = userFacingMessage(for: error, fallback: "Failed to load calendar.")
        }
        hydrateFromCache()
        isLoading = false
    }

    func createTask(_ payload: CreateTaskPayload) async {
        do {
            try await container.createTodo(payload)
            hydrateFromCache()
        } catch {
            errorMessage = userFacingMessage(for: error, fallback: "Could not create task.")
        }
    }

    func complete(_ todo: TodoItem) async {
        do {
            try await container.completeTodo(todo)
            hydrateFromCache()
        } catch {
            errorMessage = userFacingMessage(for: error, fallback: "Could not complete task.")
        }
    }

    func uncomplete(_ item: CompletedItem) async {
        do {
            try await container.completedRepository.uncomplete(item)
            hydrateFromCache()
        } catch {
            errorMessage = userFacingMessage(for: error, fallback: "Could not restore task.")
        }
    }

    func updateTask(_ todo: TodoItem, payload: CreateTaskPayload) async {
        do {
            try await container.todoRepository.updateTodo(todo, payload: payload)
            hydrateFromCache()
        } catch {
            errorMessage = userFacingMessage(for: error, fallback: "Could not update task.")
        }
    }

    func delete(_ todo: TodoItem) async {
        do {
            try await container.todoRepository.deleteTodo(todo)
            hydrateFromCache()
        } catch {
            errorMessage = userFacingMessage(for: error, fallback: "Could not delete task.")
        }
    }

    func parseTaskTitleNlp(text: String, referenceDueEpochMs: Int64) async -> TodoTitleNlpResponse? {
        await container.todoRepository.parseTodoTitleNlp(text: text, referenceDueEpochMs: referenceDueEpochMs)
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
