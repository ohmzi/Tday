import Foundation
import Observation

@MainActor
@Observable
final class CompletedViewModel {
    private let container: AppContainer

    var isLoading = false
    var items: [CompletedItem] = []
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
        let result = await container.syncAndRefresh(
            force: true,
            replayPendingMutations: false,
            connectionProbeTimeoutSeconds: SyncAndRefreshUseCase.userRefreshConnectionTimeoutSeconds
        )
        if case let .failure(error) = result, !isLikelyConnectivityIssue(error) {
            errorMessage = userFacingMessage(for: error, fallback: "Failed to load.")
        }
        hydrateFromCache()
        isLoading = false
    }

    func delete(_ item: CompletedItem) async {
        do {
            try await container.completedRepository.deleteCompletedTodo(item)
            hydrateFromCache()
        } catch {
            errorMessage = userFacingMessage(for: error, fallback: "Could not delete task.")
        }
    }

    func uncomplete(_ item: CompletedItem) async {
        do {
            try await container.completedRepository.uncomplete(item)
            hydrateFromCache()
            await rescheduleReminders()
        } catch {
            errorMessage = userFacingMessage(for: error, fallback: "Could not restore task.")
        }
    }

    func update(_ item: CompletedItem, payload: CreateTaskPayload) async {
        do {
            try await container.completedRepository.updateCompletedTodo(item, payload: payload)
            hydrateFromCache()
        } catch {
            errorMessage = userFacingMessage(for: error, fallback: "Could not update task.")
        }
    }

    private func hydrateFromCache() {
        items = container.completedRepository.fetchCompletedItemsSnapshot()
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

    private func rescheduleReminders() async {
        let tasks = container.todoRepository.fetchTodosSnapshot(mode: .all)
        let defaultReminder = container.reminderPreferenceStore.getDefaultReminder()
        await container.reminderScheduler.reschedule(tasks: tasks, defaultReminder: defaultReminder)
    }
}
