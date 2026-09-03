import Foundation
import Observation
import UIKit

@MainActor
@Observable
final class CompletedViewModel {
    private let container: AppContainer

    var isLoading = false
    var items: [CompletedItem] = []
    var lists: [ListSummary] = []
    /// For the edit sheet's list picker when the item being edited is a
    /// completed Floater — `lists` above is scheduled (Todo) lists only.
    var floaterLists: [ListSummary] = []
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

    func refresh(userInitiated: Bool = false) async {
        isLoading = true
        let result = await container.syncAndRefresh(
            force: true,
            replayPendingMutations: false,
            userInitiated: userInitiated,
            connectionProbeTimeoutSeconds: SyncAndRefreshUseCase.userRefreshConnectionTimeoutSeconds
        )
        if case let .failure(error) = result, !isLikelyConnectivityIssue(error) {
            errorMessage = userFacingMessage(for: error, fallback: "Failed to load.")
        }
        hydrateFromCache()
        isLoading = false
    }

    /// Swipe-to-copy: writes the task's title/notes/due/priority as plain text
    /// to the system pasteboard. See TodoListViewModel.copyToClipboard.
    func copyToClipboard(_ item: CompletedItem) {
        UIPasteboard.general.string = ShareSheet.taskShareText(item)
        container.snackbarManager.show(L("Copied to clipboard"), kind: .success)
    }

    func delete(_ item: CompletedItem) async {
        do {
            if item.isFloater {
                try await container.completedRepository.deleteCompletedFloater(item)
            } else {
                try await container.completedRepository.deleteCompletedTodo(item)
            }
            hydrateFromCache()
        } catch {
            errorMessage = userFacingMessage(for: error, fallback: "Could not delete task.")
        }
    }

    /// Floaters and Todos both restore through here, but as two genuinely
    /// different round trips: a Floater's uncomplete can land it in a
    /// recreated list (its original one having been deleted since), which the
    /// Todo path has no equivalent of. `listRecreated` is announced with its
    /// own toast rather than folded into `errorMessage` — it's not a failure,
    /// just a "read this" for what "restored" actually means this time.
    func uncomplete(_ item: CompletedItem) async {
        do {
            if item.isFloater {
                let response = try await container.completedRepository.uncompleteFloater(item)
                hydrateFromCache()
                if response.listRecreated == true, let listName = response.listName {
                    container.snackbarManager.show(
                        L("Restored — \"%@\" was recreated.", listName),
                        kind: .info
                    )
                }
            } else {
                try await container.completedRepository.uncomplete(item)
                hydrateFromCache()
                await rescheduleReminders()
            }
        } catch {
            errorMessage = userFacingMessage(for: error, fallback: "Could not restore task.")
        }
    }

    func update(_ item: CompletedItem, payload: CreateTaskPayload) async {
        do {
            if item.isFloater {
                try await container.completedRepository.updateCompletedFloater(item, payload: payload)
            } else {
                try await container.completedRepository.updateCompletedTodo(item, payload: payload)
            }
            hydrateFromCache()
        } catch {
            errorMessage = userFacingMessage(for: error, fallback: "Could not update task.")
        }
    }

    private func hydrateFromCache() {
        let todoItems = container.completedRepository.fetchCompletedItemsSnapshot()
        let floaterItems = container.completedRepository.fetchCompletedFloatersSnapshot()
        items = todoItems + floaterItems
        lists = container.listRepository.fetchListsSnapshot()
        floaterLists = container.floaterListRepository.fetchListsSnapshot()
        errorMessage = nil
    }

    // `[weak self]` is load-bearing here, not style — see the identical note
    // on `TodoListViewModel.observeCacheChanges()`. Without it this view
    // model can never deinit, so `observationTask?.cancel()` never runs and
    // every discarded instance keeps reacting to every future cache write.
    private func observeCacheChanges() {
        observationTask = Task { [weak self] in
            for await _ in NotificationCenter.default.notifications(named: .offlineCacheDidChange) {
                guard let self else { return }
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
