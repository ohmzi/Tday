import Foundation
import Observation

@MainActor
@Observable
final class SnackbarManager {
    var message: String?

    func show(_ message: String) {
        self.message = message
    }

    func show(message: String) {
        show(message)
    }

    func dismiss() {
        message = nil
    }
}
