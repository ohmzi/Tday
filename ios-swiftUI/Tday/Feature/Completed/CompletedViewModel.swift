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
            errorMessage = userFacingMessage(for: error, fallback: "Failed to load.")
        }
        hydrateFromCache()
        isLoading = false
    }

    func uncomplete(_ item: CompletedItem) async {
        do {
            try await container.completedRepository.uncomplete(item)
            hydrateFromCache()
        } catch {
            errorMessage = userFacingMessage(for: error, fallback: "Could not restore task.")
        }
    }

    func delete(_ item: CompletedItem) async {
        do {
            try await container.completedRepository.deleteCompletedTodo(item)
            hydrateFromCache()
        } catch {
            errorMessage = userFacingMessage(for: error, fallback: "Could not delete task.")
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
}
