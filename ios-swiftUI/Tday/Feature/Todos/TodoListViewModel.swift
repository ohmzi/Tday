import Foundation
import Observation
// `withAnimation` below is what gives a remote cache change an explicit
// SwiftUI transaction — see `hydrateFromExternalCacheChange()`.
import SwiftUI

@MainActor
@Observable
final class TodoListViewModel {
    private let container: AppContainer
    private static let recentSuccessfulSyncSkipWindowMs: Int64 = 8_000

    var isLoading = false
    var title: String
    var mode: TodoListMode
    var listId: String?
    var lists: [ListSummary] = []
    var items: [TodoItem] = []
    // Feeds the Day Done state: completed-today count from the local cache,
    // bumped optimistically on complete so the payoff shows immediately.
    var completedTodayCount = 0
    /// When the user last ticked something off. Feeds the confetti: an empty
    /// list that emptied under the user's own hand is a payoff, one that was
    /// already empty when they opened it is not. Set only by this device's own
    /// `complete`/`bulkComplete` — precise, but local-only. See
    /// `remoteEmptiedAt` for the sibling that covers everything else.
    var lastCompletionAt: Date?
    /// When a cache change this device did not itself just stage last emptied
    /// the viewed list — set only from `hydrateFromExternalCacheChange()`,
    /// never from a local mutation's own direct `hydrateFromCache()` call.
    ///
    /// This is `lastCompletionAt`'s broader, imprecise sibling: that field
    /// only ever fires for a completion this device made, because `complete`/
    /// `bulkComplete` are the only callers. A cache-changed notification
    /// carries no reason (it is "something changed, re-read the cache", not
    /// "task N was completed"), so this cannot tell a remote completion from
    /// a remote delete of the last task the way the local path tells complete
    /// from delete — it treats any observed non-empty-to-empty transition on
    /// this path as a payoff. That imprecision is deliberately scoped to just
    /// this externally-triggered path; every local mutation (including
    /// `delete`/`bulkDelete`) still never touches this field, so a local
    /// delete of the last task still gets the plain arrival, exactly as
    /// today. `TodoListScreen.celebratesEmptyState` also requires the screen
    /// to be visible before honouring this one, so a transition on a screen
    /// nobody is looking at does not surface a stale burst when the user
    /// returns to it later.
    var remoteEmptiedAt: Date?
    var errorMessage: String?
    var aiSummaryEnabled = true
    var summaryText: String?
    var summarySource: String?
    var summaryGeneratedAt: String?
    var summaryError: String?
    var summaryConnectivityError = false
    var isSummarizing = false

    private let listName: String?
    @ObservationIgnored nonisolated(unsafe) private var observationTask: Task<Void, Never>?

    init(container: AppContainer, mode: TodoListMode, listId: String?, listName: String?) {
        self.container = container
        self.mode = mode
        self.listId = listId
        self.listName = listName
        title = listName ?? mode.title
        TdayTelemetry.addBreadcrumb(
            "todo_list.load",
            data: modeTelemetryData(mode: mode, scopedList: listId?.isEmpty == false)
        )
        hydrateFromCache()
        observeCacheChanges()
    }

    deinit {
        observationTask?.cancel()
    }

    func refresh(userInitiated: Bool = false) async {
        TdayTelemetry.addBreadcrumb("todo_list.refresh", data: modeTelemetryData())
        isLoading = true
        defer {
            isLoading = false
        }

        let loadCachedState: @MainActor () -> OfflineSyncState = container.cacheManager.loadOfflineState
        let cachedState = loadCachedState()
        if shouldUseRecentSuccessfulSync(cachedState) {
            hydrateFromCache(snapshot: container.todoRepository.makeTodoListCacheSnapshot(
                from: cachedState,
                mode: mode,
                listId: listId
            ))
            return
        }

        let result = await container.syncAndRefresh(
            force: true,
            replayPendingMutations: true,
            userInitiated: userInitiated,
            connectionProbeTimeoutSeconds: SyncAndRefreshUseCase.userRefreshConnectionTimeoutSeconds
        )
        if case let .failure(error) = result, !isLikelyConnectivityIssue(error) {
            errorMessage = userFacingMessage(for: error, fallback: "Failed to load tasks.")
        }
        hydrateFromCache()
    }

    func summarizeCurrentMode() async {
        guard aiSummaryEnabled else {
            summaryError = "Summary is disabled by admin"
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
                summaryError = "No summary available while offline."
            } else {
                summaryError = userFacingMessage(for: error, fallback: "Could not summarize tasks.")
            }
        }
        isSummarizing = false
    }

    func dismissSummaryConnectivityError() {
        summaryConnectivityError = false
    }

    func addTask(_ payload: CreateTaskPayload) async {
        TdayTelemetry.addBreadcrumb("task.create", data: taskTelemetryData(mode: mode, payload: payload))
        do {
            if mode == .floater {
                try await container.todoRepository.createFloater(payload: payload)
            } else {
                try await container.createTodo(payload)
            }
            hydrateFromCache()
        } catch {
            container.snackbarManager.show(
                userFacingMessage(for: error, fallback: "Could not create task."),
                kind: .error
            )
        }
    }

    func updateTask(_ todo: TodoItem, payload: CreateTaskPayload) async {
        TdayTelemetry.addBreadcrumb("task.update", data: taskTelemetryData(mode: mode, payload: payload))
        do {
            if mode == .floater {
                try await container.todoRepository.updateFloater(todo, payload: payload)
            } else {
                try await container.todoRepository.updateTodo(todo, payload: payload)
            }
            hydrateFromCache()
        } catch {
            container.snackbarManager.show(
                userFacingMessage(for: error, fallback: "Could not update task."),
                kind: .error
            )
        }
    }

    func moveTask(_ todo: TodoItem, toDay targetDay: Date, scope: TaskRescheduleScope) async {
        let calendar = Calendar.current
        guard let due = todo.due,
              !calendar.isDate(due, inSameDayAs: targetDay) else {
            return
        }

        guard let movedDue = movedDuePreservingTime(due: due, targetDay: targetDay, calendar: calendar) else {
            return
        }

        var telemetryData = taskTelemetryData(mode: mode, scope: scope)
        telemetryData["source"] = "todo_list"
        TdayTelemetry.addBreadcrumb("task.reschedule", data: telemetryData)
        do {
            try await container.todoRepository.moveTodo(
                todo.repositoryTargetForReschedule(scope: scope),
                due: movedDue
            )
            hydrateFromCache()
        } catch {
            container.snackbarManager.show(
                userFacingMessage(for: error, fallback: "Could not update task."),
                kind: .error
            )
        }
    }

    // Today screen: set a task's time-of-day (Morning / Afternoon / Tonight)
    // without changing its date. Sibling of `moveTask(toDay:)`.
    func moveTask(_ todo: TodoItem, toTimeOfDay hour: Int, scope: TaskRescheduleScope) async {
        let calendar = Calendar.current
        guard let due = todo.due,
              let movedDue = movedDueToTimeOfDay(due: due, hour: hour, calendar: calendar),
              movedDue != due else {
            return
        }

        var telemetryData = taskTelemetryData(mode: mode, scope: scope)
        telemetryData["source"] = "todo_list"
        TdayTelemetry.addBreadcrumb("task.reschedule", data: telemetryData)
        do {
            try await container.todoRepository.moveTodo(
                todo.repositoryTargetForReschedule(scope: scope),
                due: movedDue
            )
            hydrateFromCache()
        } catch {
            container.snackbarManager.show(
                userFacingMessage(for: error, fallback: "Could not update task."),
                kind: .error
            )
        }
    }

    /// Quick Defer: one tap moves the task to a locally computed instant.
    func deferTask(_ todo: TodoItem, due: Date) async {
        TdayTelemetry.addBreadcrumb("task.defer", data: taskTelemetryData(mode: mode))
        do {
            try await container.todoRepository.moveTodo(todo, due: due)
            hydrateFromCache()
        } catch {
            container.snackbarManager.show(
                userFacingMessage(for: error, fallback: "Could not update task."),
                kind: .error
            )
        }
    }

    /// Schedules a floater into a real Todo at the picked due instant.
    func promoteFloater(_ floater: TodoItem, due: Date) async {
        TdayTelemetry.addBreadcrumb("task.promote", data: taskTelemetryData(mode: mode))
        do {
            try await container.todoRepository.promoteFloater(floater, due: due, rrule: nil)
            hydrateFromCache()
        } catch {
            container.snackbarManager.show(
                userFacingMessage(for: error, fallback: "Could not schedule task."),
                kind: .error
            )
        }
    }

    /// "Let it float": demotes an overdue todo into an Anytime floater.
    func demoteTodo(_ todo: TodoItem) async {
        TdayTelemetry.addBreadcrumb("task.demote", data: taskTelemetryData(mode: mode))
        do {
            try await container.todoRepository.demoteTodo(todo)
            hydrateFromCache()
        } catch {
            container.snackbarManager.show(
                userFacingMessage(for: error, fallback: "Could not float task."),
                kind: .error
            )
        }
    }

    /// Delayed-commit complete: the completion is written to the local cache
    /// (and its mutation queued) immediately, an undoable toast is shown, and
    /// only the network replay is deferred until the undo window expires.
    /// Tapping Undo cancels the pending commit and reverses the staged write.
    ///
    /// This writes durably up front rather than staying in-memory-only until
    /// commit: a completion the user already saw must survive the app dying
    /// inside the undo window, not silently revert with no trace on relaunch.
    /// See `TodoRepository.stageCompleteTodo(_:)` for the full rationale.
    func complete(_ todo: TodoItem) async {
        TdayTelemetry.addBreadcrumb("task.complete", data: taskTelemetryData(mode: mode))
        let container = container
        let isFloater = mode == .floater
        lastCompletionAt = Date()
        if isFloater {
            let staged = container.todoRepository.stageCompleteFloater(todo)
            hydrateFromCache()
            container.undoableDeleteScheduler.schedule(
                message: L("Task completed"),
                restore: {
                    container.todoRepository.undoStagedFloaterCompletion(staged)
                },
                commit: {
                    do {
                        try await container.todoRepository.syncPendingMutations()
                    } catch {
                        container.snackbarManager.show(
                            userFacingMessage(for: error, fallback: "Could not complete task."),
                            kind: .error
                        )
                    }
                }
            )
        } else {
            let staged = container.todoRepository.stageCompleteTodo(todo)
            hydrateFromCache()
            container.undoableDeleteScheduler.schedule(
                message: L("Task completed"),
                restore: {
                    container.todoRepository.undoStagedCompletion(staged)
                },
                commit: {
                    do {
                        try await container.todoRepository.syncPendingMutations()
                    } catch {
                        container.snackbarManager.show(
                            userFacingMessage(for: error, fallback: "Could not complete task."),
                            kind: .error
                        )
                    }
                }
            )
        }
    }

    /// Delayed-commit delete: the task is staged out of the local cache
    /// immediately, an undoable toast is shown, and the real (server) delete
    /// only commits once the undo window expires. The closures capture
    /// `container` rather than `self` so a pending commit survives this
    /// view model being deallocated.
    func delete(_ todo: TodoItem) async {
        TdayTelemetry.addBreadcrumb("task.delete", data: taskTelemetryData(mode: mode))
        let container = container
        if mode == .floater {
            let staged = container.todoRepository.stageDeleteFloater(todo)
            hydrateFromCache()
            container.undoableDeleteScheduler.schedule(
                message: L("Task deleted"),
                restore: {
                    container.todoRepository.undoStagedFloater(staged)
                },
                commit: {
                    do {
                        try await container.todoRepository.deleteFloater(todo)
                    } catch {
                        container.snackbarManager.show(
                            userFacingMessage(for: error, fallback: "Could not delete task."),
                            kind: .error
                        )
                    }
                }
            )
        } else {
            let staged = container.todoRepository.stageDeleteTodo(todo)
            hydrateFromCache()
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
    }

    // MARK: - Bulk (multi-select) actions

    /// Delayed-commit bulk complete — the batch shape of `complete(_:)`. The
    /// whole selection is staged into the local cache (and its mutations
    /// queued) in ONE cache write, ONE undoable toast covers the batch, and
    /// only the network replay is deferred until the undo window closes. N
    /// toasts would mean N commit timers with only the last one visible, so the
    /// user could undo exactly one of them.
    ///
    /// Staging durably up front (rather than in-memory-only until commit)
    /// means a crash or kill during the up-to-100-request replay that commit
    /// triggers loses nothing: every completion in the batch already survived
    /// to disk before the network round-trips even began. See
    /// `TodoRepository.stageCompleteTodos(_:)`.
    ///
    /// Recurring occurrences stay in the batch: complete is the one action with a
    /// per-occurrence route, and the repository carries each row's `instanceDate`.
    func bulkComplete(_ todos: [TodoItem]) async {
        guard !todos.isEmpty else { return }
        TdayTelemetry.addBreadcrumb("task.bulk_complete", data: bulkTelemetryData(count: todos.count))
        let container = container
        let isFloater = mode == .floater
        let count = todos.count
        lastCompletionAt = Date()
        if isFloater {
            let staged = container.todoRepository.stageCompleteFloaters(todos)
            hydrateFromCache()
            container.undoableDeleteScheduler.schedule(
                message: BulkSelectionCopy.completedToast(count),
                restore: {
                    container.todoRepository.undoStagedFloaterCompletion(staged)
                },
                commit: {
                    do {
                        try await container.todoRepository.syncPendingMutations()
                    } catch {
                        container.snackbarManager.show(BulkSelectionCopy.updateFailed(count), kind: .error)
                    }
                }
            )
        } else {
            let staged = container.todoRepository.stageCompleteTodos(todos)
            hydrateFromCache()
            container.undoableDeleteScheduler.schedule(
                message: BulkSelectionCopy.completedToast(count),
                restore: {
                    container.todoRepository.undoStagedCompletion(staged)
                },
                commit: {
                    do {
                        try await container.todoRepository.syncPendingMutations()
                    } catch {
                        container.snackbarManager.show(BulkSelectionCopy.updateFailed(count), kind: .error)
                    }
                }
            )
        }
    }

    /// Delayed-commit bulk delete — the batch shape of `delete(_:)`. The screen
    /// has already taken an explicit confirmation before this runs; this is the
    /// second guard, not the only one. The whole selection is staged out of the
    /// local cache in one write, one undoable toast covers the batch, and the
    /// server is not told anything until the window closes. The closures capture
    /// `container` rather than `self` so a pending commit survives this view
    /// model being deallocated.
    func bulkDelete(_ todos: [TodoItem]) async {
        guard !todos.isEmpty else { return }
        TdayTelemetry.addBreadcrumb("task.bulk_delete", data: bulkTelemetryData(count: todos.count))
        let container = container
        let count = todos.count
        if mode == .floater {
            let staged = container.todoRepository.stageDeleteFloaters(todos)
            hydrateFromCache()
            container.undoableDeleteScheduler.schedule(
                message: BulkSelectionCopy.deletedToast(count),
                restore: {
                    container.todoRepository.undoStagedFloater(staged)
                },
                commit: {
                    do {
                        try await container.todoRepository.deleteFloaters(todos)
                    } catch {
                        container.snackbarManager.show(BulkSelectionCopy.deleteFailed(count), kind: .error)
                    }
                }
            )
        } else {
            let staged = container.todoRepository.stageDeleteTodos(todos)
            hydrateFromCache()
            container.undoableDeleteScheduler.schedule(
                message: BulkSelectionCopy.deletedToast(count),
                restore: {
                    container.todoRepository.undoStagedTodo(staged)
                },
                commit: {
                    do {
                        try await container.todoRepository.deleteTodos(todos)
                    } catch {
                        container.snackbarManager.show(BulkSelectionCopy.deleteFailed(count), kind: .error)
                    }
                }
            )
        }
    }

    /// Silent on success, by design: a priority change is an edit, and the
    /// unified toast policy gives edits no toast. The rows visibly change and
    /// selection mode closes — that is the feedback.
    func bulkSetPriority(_ todos: [TodoItem], priority: String) async {
        guard !todos.isEmpty else { return }
        TdayTelemetry.addBreadcrumb("task.bulk_priority", data: bulkTelemetryData(count: todos.count))
        do {
            if mode == .floater {
                try await container.todoRepository.setPriorityForFloaters(todos, priority: priority)
            } else {
                try await container.todoRepository.setPriorityForTodos(todos, priority: priority)
            }
        } catch {
            container.snackbarManager.show(BulkSelectionCopy.updateFailed(todos.count), kind: .error)
        }
        hydrateFromCache()
    }

    /// `listId == nil` means "No list". Silent on success for the same reason as
    /// `bulkSetPriority`. Scheduled tasks only ever move between scheduled lists
    /// and floaters between floater lists — `lists` is already the right silo for
    /// the current mode, so there is no cross-silo target to offer.
    func bulkMove(_ todos: [TodoItem], toListId listId: String?) async {
        guard !todos.isEmpty else { return }
        TdayTelemetry.addBreadcrumb("task.bulk_move", data: bulkTelemetryData(count: todos.count))
        do {
            if mode == .floater {
                try await container.todoRepository.moveFloatersToList(todos, listId: listId)
            } else {
                try await container.todoRepository.moveTodosToList(todos, listId: listId)
            }
        } catch {
            container.snackbarManager.show(BulkSelectionCopy.updateFailed(todos.count), kind: .error)
        }
        hydrateFromCache()
    }

    func updateListSettings(name: String, color: String?, iconKey: String?) async {
        guard let listId else { return }
        TdayTelemetry.addBreadcrumb("list.update", data: listTelemetryData(color: color, iconKey: iconKey))
        do {
            if mode == .floater {
                try await container.floaterListRepository.updateList(listId: listId, name: name, color: color, iconKey: iconKey)
            } else {
                try await container.listRepository.updateList(listId: listId, name: name, color: color, iconKey: iconKey)
            }
            hydrateFromCache()
            title = name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? (listName ?? mode.title) : name
        } catch {
            container.snackbarManager.show(
                userFacingMessage(for: error, fallback: "Could not update list."),
                kind: .error
            )
        }
    }

    func createList(name: String, color: String?, iconKey: String?) async {
        TdayTelemetry.addBreadcrumb("list.create", data: listTelemetryData(color: color, iconKey: iconKey))
        do {
            if mode == .floater {
                try await container.floaterListRepository.createList(name: name, color: color, iconKey: iconKey)
            } else {
                try await container.listRepository.createList(name: name, color: color, iconKey: iconKey)
            }
            hydrateFromCache()
        } catch {
            container.snackbarManager.show(
                userFacingMessage(for: error, fallback: "Could not create list."),
                kind: .error
            )
        }
    }

    /// Deletes the current list and returns `true` on success so the screen can
    /// drive navigation deterministically after the await completes, instead of
    /// from the repository's mid-await optimistic-delete callback (which raced
    /// with the delete-confirmation overlay dismissal and dropped navigation).
    ///
    /// Delayed-commit delete: `true` means *staging* succeeded — the list is
    /// already pruned from the local cache and the screen navigates away
    /// immediately, while the real (server) delete only commits once the undo
    /// window expires. Tapping Undo restores the list (and its tasks) even
    /// though the user has navigated away, so the closures capture `container`
    /// rather than `self`.
    func deleteList() async -> Bool {
        guard let listId else { return false }
        TdayTelemetry.addBreadcrumb("list.delete", data: listTelemetryData(color: nil, iconKey: nil))
        let container = container
        if mode == .floater {
            let staged = container.floaterListRepository.stageDeleteList(listId: listId)
            container.undoableDeleteScheduler.schedule(
                message: L("List deleted"),
                restore: {
                    container.floaterListRepository.undoStagedList(staged)
                },
                commit: {
                    do {
                        try await container.floaterListRepository.deleteList(listId: listId)
                    } catch {
                        container.snackbarManager.show(
                            userFacingMessage(for: error, fallback: "Could not delete list."),
                            kind: .error
                        )
                    }
                }
            )
        } else {
            let staged = container.listRepository.stageDeleteList(listId: listId)
            container.undoableDeleteScheduler.schedule(
                message: L("List deleted"),
                restore: {
                    container.listRepository.undoStagedList(staged)
                },
                commit: {
                    do {
                        try await container.listRepository.deleteList(listId: listId)
                    } catch {
                        container.snackbarManager.show(
                            userFacingMessage(for: error, fallback: "Could not delete list."),
                            kind: .error
                        )
                    }
                }
            )
        }
        lists.removeAll { $0.id == listId }
        items.removeAll { $0.listId == listId }
        errorMessage = nil
        return true
    }

    func parseTaskTitleNlp(text: String, referenceDueEpochMs: Int64) async -> TodoTitleNlpResponse? {
        await container.todoRepository.parseTodoTitleNlp(text: text, referenceDueEpochMs: referenceDueEpochMs)
    }

    private func hydrateFromCache() {
        hydrateFromCache(snapshot: container.todoRepository.fetchTodoListCacheSnapshot(mode: mode, listId: listId))
    }

    private func hydrateFromCache(snapshot: TodoListCacheSnapshot) {
        lists = snapshot.lists
        items = snapshot.items
        aiSummaryEnabled = snapshot.aiSummaryEnabled
        errorMessage = nil
        if mode == .today {
            completedTodayCount = container.todoRepository.completedTodayCount()
        }
    }

    private func shouldUseRecentSuccessfulSync(_ state: OfflineSyncState) -> Bool {
        guard state.pendingMutations.isEmpty, state.lastSuccessfulSyncEpochMs > 0 else {
            return false
        }
        return Date().epochMilliseconds - state.lastSuccessfulSyncEpochMs < Self.recentSuccessfulSyncSkipWindowMs
    }

    // `[weak self]` here is load-bearing, not style: SwiftUI's
    // `State(initialValue:)` (see `TodoListScreen.init`) re-runs this view
    // model's initializer on every parent body pass and discards the surplus
    // instance. Capturing `self` strongly would keep this Task (and the
    // NotificationCenter async sequence it iterates) alive forever, which
    // keeps `self` alive forever too — `deinit` could never run to cancel it.
    // Every discarded instance would go on reacting to every future cache
    // write for the life of the process, and a bulk completion posts four of
    // those writes, each a full cache reload on the main actor. That
    // unbounded, immortal observer set — not request concurrency, which this
    // path never has unbounded — is the confirmed mechanism behind the crash
    // under a large completion.
    private func observeCacheChanges() {
        observationTask = Task { [weak self] in
            for await _ in NotificationCenter.default.notifications(named: .offlineCacheDidChange) {
                guard let self else { return }
                await MainActor.run {
                    self.hydrateFromExternalCacheChange()
                }
            }
        }
    }

    /// Reacts to a cache write this instance did not itself just stage — most
    /// often a remote sync (another device, or a collaborator on a shared
    /// list) landing through `OfflineCacheManager.saveOfflineState`'s
    /// notification, but sometimes just the async echo of this device's own
    /// write (a staged local completion or delete already calls
    /// `hydrateFromCache()` directly; that same write's notification still
    /// reaches this loop a beat later).
    ///
    /// `saveOfflineState` posts that notification synchronously
    /// (`OfflineCacheManager.swift`), but `NotificationCenter.notifications`
    /// delivers it to this `for await` loop asynchronously, outside any
    /// SwiftUI transaction. Left alone, a remote completion that empties the
    /// last section of an open todo list changes both the List's section
    /// count and its row count in that one uncoordinated update — a known
    /// trigger for SwiftUI's List diffing engine to throw ("invalid number of
    /// rows/sections") or crash outright once it cannot reconcile a section
    /// disappearing at the same moment its last row does. `withAnimation`
    /// gives the mutation an explicit transaction, so List diffs the
    /// section-and-row change as one coordinated update instead of an
    /// external one arriving mid-flight — the same guarantee a user-initiated
    /// action already gets for free from SwiftUI's own gesture handling,
    /// which this async loop does not.
    ///
    /// Also where a remote-driven empty transition gets to celebrate: see
    /// `remoteEmptiedAt`.
    private func hydrateFromExternalCacheChange() {
        let wasNonEmpty = !items.isEmpty
        withAnimation(.easeInOut(duration: 0.22)) {
            hydrateFromCache()
        }
        if wasNonEmpty, items.isEmpty {
            remoteEmptiedAt = Date()
        }
    }

    private func modeTelemetryData(mode: TodoListMode? = nil, scopedList: Bool? = nil) -> [String: Any] {
        [
            "mode": (mode ?? self.mode).summaryMode,
            "scoped_list": scopedList ?? !(listId ?? "").isEmpty
        ]
    }

    private func taskTelemetryData(
        mode: TodoListMode,
        payload: CreateTaskPayload? = nil,
        scope: TaskRescheduleScope? = nil
    ) -> [String: Any] {
        var data = modeTelemetryData(mode: mode)
        if let payload {
            data["has_due"] = payload.due != nil
            data["has_repeat"] = !(payload.rrule ?? "").isEmpty
            data["has_list"] = !(payload.listId ?? "").isEmpty
            data["has_description"] = !(payload.description ?? "").isEmpty
        }
        if let scope {
            data["scope"] = scope.rawValue
        }
        return data
    }

    /// Structural only: how many rows the action covered and which screen it ran
    /// on. Never titles, list names or raw ids.
    private func bulkTelemetryData(count: Int) -> [String: Any] {
        var data = modeTelemetryData()
        data["count"] = count
        return data
    }

    private func listTelemetryData(color: String?, iconKey: String?) -> [String: Any] {
        [
            "kind": mode == .floater ? "floater" : "scheduled",
            "scoped_list": !(listId ?? "").isEmpty,
            "has_color": !(color ?? "").isEmpty,
            "has_icon": !(iconKey ?? "").isEmpty
        ]
    }
}

/// Copy and limits for the multi-select flow, in one place so `TodoListScreen`
/// (labels, dialogs, the selection bar) and `TodoListViewModel` (toasts) cannot
/// drift apart.
///
/// The canonical English lives in `docs/design/bulk-selection.md` §9 and is the
/// same sentence on Android and web — translate it, do not reword it. iOS uses
/// the English string as its own key, per the `L(...)` convention, so a missing
/// translation silently falls back to English.
enum BulkSelectionCopy {
    /// Mirrors `BulkSelectionPolicy.MAX_SELECTION` in
    /// `shared/src/commonMain/kotlin/com/ohmz/tday/shared/bulk/BulkSelectionPolicy.kt`.
    ///
    /// Every bulk action is a fan-out of N single-item requests, and the
    /// `api_global` policy allows `API_RATE_LIMIT_MAX` (default 180) per minute
    /// per user — plus one realtime event, one webhook delivery and one push
    /// poke per mutation, none of it coalesced. The cap is what keeps a large
    /// selection from becoming a *partially applied* destructive action.
    static let maxSelection = 100

    static func selectedCount(_ count: Int, capped: Bool) -> String {
        capped
            ? L("%d selected — the most one action can cover", count)
            : L("%d selected", count)
    }

    static var selectAll: String { L("Select all") }
    static var deselectAll: String { L("Deselect all") }

    /// Shown when the recurring rule (§4.1) narrowed the selection: a repeating
    /// occurrence can be bulk-completed, but never bulk-deleted, re-prioritized
    /// or moved, because those three would silently act on the whole series.
    static func appliesTo(effective: Int, total: Int) -> String {
        L("Applies to %1$d of %2$d — repeating tasks are skipped.", effective, total)
    }

    static func completedToast(_ count: Int) -> String {
        count == 1 ? L("Task completed") : L("%d tasks completed", count)
    }

    static func deletedToast(_ count: Int) -> String {
        count == 1 ? L("Task deleted") : L("%d tasks deleted", count)
    }

    static func deleteTitle(_ count: Int) -> String {
        count == 1 ? L("Delete this task?") : L("Delete %d tasks?", count)
    }

    static var deleteBody: String {
        L("This removes them from every list, along with their completed history. You'll have a few seconds to undo.")
    }

    static func deleteConfirm(_ count: Int) -> String {
        count == 1 ? L("Delete task") : L("Delete %d tasks", count)
    }

    static func moveTitle(_ count: Int) -> String {
        L("Move %d tasks?", count)
    }

    static var moveBody: String {
        L("These tasks come from different lists. Moving them can't be undone in one step — you'd have to put each one back by hand.")
    }

    static func moveConfirm(_ count: Int) -> String {
        L("Move %d tasks", count)
    }

    // Singular branch like every sibling above, so one failed row does not read
    // "1 tasks couldn't be deleted". Android already says this correctly through
    // <plurals>; web now does too.
    static func deleteFailed(_ count: Int) -> String {
        count == 1 ? L("A task couldn't be deleted") : L("%d tasks couldn't be deleted", count)
    }

    static func updateFailed(_ count: Int) -> String {
        count == 1 ? L("A task couldn't be updated") : L("%d tasks couldn't be updated", count)
    }
}
