import Foundation
import Observation

/// Visual variant of a snackbar — drives the accent colour and the default icon.
enum SnackbarKind {
    case error
    case success
    case info
}

@MainActor
@Observable
final class SnackbarManager {
    struct Content: Identifiable {
        let id = UUID()
        let message: String
        let kind: SnackbarKind
        let actionLabel: String?
        let action: (() -> Void)?
    }

    private(set) var content: Content?

    func show(
        _ message: String,
        kind: SnackbarKind = .error,
        actionLabel: String? = nil,
        action: (() -> Void)? = nil
    ) {
        content = Content(
            message: message,
            kind: kind,
            actionLabel: actionLabel,
            action: action
        )
    }

    func show(message: String) {
        show(message, kind: .error)
    }

    func dismiss() {
        content = nil
    }
}

/// Coordinates delayed-commit deletes so their toasts can offer Undo.
///
/// The backend only has hard deletes (and server-generated IDs), so a deleted
/// item cannot be re-created faithfully after the fact. Instead each delete is
/// *staged* first — removed from the local cache only, nothing sent to the
/// server — while a toast with an Undo action is shown. The real delete
/// request *commits* only after the toast window has passed; tapping Undo
/// cancels the pending commit and restores the staged local state, so the
/// server row is never touched.
@MainActor
final class UndoableDeleteScheduler {
    /// Snackbars with an action stay visible for 8 seconds (see `AppSnackbar`);
    /// the commit fires slightly after so Undo can never be tapped for a
    /// delete that has already been committed.
    private static let commitDelayNanoseconds: UInt64 = 8_500_000_000

    private let snackbarManager: SnackbarManager
    private var pendingCommits: [UUID: Task<Void, Never>] = [:]

    init(snackbarManager: SnackbarManager) {
        self.snackbarManager = snackbarManager
    }

    /// Shows `message` with an Undo action. `commit` runs once the undo window
    /// expires; tapping Undo cancels the pending commit and runs `restore`
    /// instead. Every call gets its own independent staged state and timer, so
    /// rapid successive deletes all commit on their own schedule (the newest
    /// toast simply wins visually).
    func schedule(
        message: String,
        restore: @escaping @MainActor () async -> Void,
        commit: @escaping @MainActor () async -> Void
    ) {
        let id = UUID()
        pendingCommits[id] = Task { @MainActor [weak self] in
            try? await Task.sleep(nanoseconds: Self.commitDelayNanoseconds)
            guard !Task.isCancelled else { return }
            self?.pendingCommits[id] = nil
            await commit()
        }
        snackbarManager.show(
            message,
            kind: .success,
            actionLabel: L("Undo"),
            action: { [weak self] in
                Task { @MainActor in
                    guard let self,
                          let pending = self.pendingCommits.removeValue(forKey: id) else {
                        return
                    }
                    pending.cancel()
                    await restore()
                }
            }
        )
    }
}
