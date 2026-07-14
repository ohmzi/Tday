import Foundation

/// One text share captured by the share extension, waiting to become a task.
struct PendingShare: Codable, Equatable {
    let title: String
    let notes: String?
}

/// App-side reader of the share extension's queue. The extension can only
/// append `{title, notes}` descriptors to this app-group key (it has no cache
/// or repository access); the app pops one per activation and presents the
/// create-task sheet prefilled with it. Key and payload shape must stay in
/// lockstep with ShareViewController in TdayShareExtension.
enum PendingShareStore {
    static let queueKey = "tday.share.pendingShares"
    static let appGroupSuiteName = "group.com.ohmz.tday"

    /// Pops the oldest queued share. One per activation keeps the flow calm:
    /// a rare backlog surfaces one sheet per foreground rather than a stack.
    static func drainNext() -> PendingShare? {
        let store = UserDefaults(suiteName: appGroupSuiteName) ?? .standard
        guard let data = store.data(forKey: queueKey),
              var queue = try? JSONDecoder().decode([PendingShare].self, from: data),
              !queue.isEmpty else {
            return nil
        }
        let next = queue.removeFirst()
        if queue.isEmpty {
            store.removeObject(forKey: queueKey)
        } else if let remaining = try? JSONEncoder().encode(queue) {
            store.set(remaining, forKey: queueKey)
        }
        return next
    }
}
