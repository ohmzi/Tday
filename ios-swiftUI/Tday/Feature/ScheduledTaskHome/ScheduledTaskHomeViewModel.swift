import Foundation
import Observation
import UIKit

@MainActor
@Observable
final class ScheduledTaskHomeViewModel {
    private let container: AppContainer
    private static let recentSuccessfulSyncSkipWindowMs: Int64 = 8_000

    var isLoading = true
    /// This screen's local cache read has landed in state — true from the first
    /// frame, because `refreshFromCache()` runs in `init`. Published here rather
    /// than derived in the view for the same reason as on `TodoListViewModel`:
    /// a computed property on a `View` is reachable from no test, and this is
    /// one of the terms `feedAnswer` decides the Today skeleton on.
    private(set) var hasHydratedFromCache = false
    /// `isLocalMode || lastSuccessfulSyncEpochMs > 0` — see
    /// `feedFirstAnswerLanded(in:)`. Re-read on every hydrate, so a first sync
    /// that failed and was retried is picked up in the pass that retried it.
    private(set) var firstAnswerLanded = false
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

    func refresh(userInitiated: Bool = false) async {
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
            userInitiated: userInitiated,
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
        // Settled on the same frame as the rows, for the reason given on
        // `TodoListViewModel.hydrateFromCache`: the two terms `feedAnswer` reads
        // besides the row count must never disagree with what was just loaded.
        firstAnswerLanded = feedFirstAnswerLanded(in: container)
        hasHydratedFromCache = true
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

    func createList(name: String, color: String?, iconKey: String?, defaultPriority: String? = nil) async {
        do {
            try await container.listRepository.createList(name: name, color: color, iconKey: iconKey, defaultPriority: defaultPriority)
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

    /// Delayed-commit complete (see TodoListViewModel.complete): stage the
    /// completion into the local cache now, show an undoable toast, and un-stage
    /// then flush the already-queued mutation once the undo window expires.
    ///
    /// Staging rather than hiding the row in memory is the whole point: this
    /// screen re-reads the cache on every `.offlineCacheDidChange`, so a row
    /// removed only from `todayTodos` comes straight back off the next sync and
    /// the deferred commit then takes it away again. Same discipline as
    /// `delete(_:)` below.
    func complete(_ todo: TodoItem) async {
        let container = container
        todayTodos.removeAll { $0.id == todo.id }
        searchableTodos.removeAll { $0.id == todo.id }
        let staged = await container.todoRepository.stageCompleteTodo(todo)
        refreshFromCache()
        container.undoableDeleteScheduler.schedule(
            message: L("Task completed"),
            restore: { [weak self] in
                container.todoRepository.undoStagedCompletion(staged)
                self?.refreshFromCache()
            },
            commit: { [weak self] in
                do {
                    try await container.todoRepository.commitStagedCompletion(staged)
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

    /// Swipe-to-copy: writes the task's title/notes/due/priority as plain text
    /// to the system pasteboard. See TodoListViewModel.copyToClipboard.
    func copyToClipboard(_ todo: TodoItem) {
        UIPasteboard.general.string = ShareSheet.taskShareText(todo)
        container.snackbarManager.show(L("Copied to clipboard"), kind: .success)
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

    /// "Make this repeat?" — a preset RRULE when the completed history shows a steady
    /// cadence for `title`, else nil. Feeds the create-sheet suggestion chip.
    func suggestRepeatRrule(title: String) async -> String? {
        let completions = container.completedRepository.fetchCompletedItemsSnapshot()
            .compactMap { item -> RepeatSuggestionEngine.Completion? in
                guard let completedAt = item.completedAt else { return nil }
                return RepeatSuggestionEngine.Completion(
                    title: item.title,
                    completedAtEpochMs: Int64(completedAt.timeIntervalSince1970 * 1000)
                )
            }
        return RepeatSuggestionEngine.suggest(currentTitle: title, completions: completions)
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
                    self.refreshFromCache()
                }
            }
        }
    }
}
