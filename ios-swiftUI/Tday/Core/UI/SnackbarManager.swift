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
