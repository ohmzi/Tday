import Foundation
import WatchConnectivity

/// Mirrors the phone's Today snapshot to a paired Apple Watch (R6-4).
///
/// The Watch shows the very same Today list the home-screen widget builds, so
/// this reuses `TodayTasksWidgetSnapshotStore` rather than re-deriving anything.
/// The snapshot is pushed as JSON over WatchConnectivity's application context
/// (last-value-wins, cheap, survives the watch being asleep). The watch app and
/// its complication decode it — see `TdayWatch/`.
final class WatchSessionManager: NSObject, WCSessionDelegate {
    static let shared = WatchSessionManager()

    private override init() {
        super.init()
    }

    /// Activate the session once, early in app launch. No-op on hardware without
    /// a paired-watch capability (iPad, Mac Catalyst).
    func activate() {
        guard WCSession.isSupported() else { return }
        let session = WCSession.default
        session.delegate = self
        session.activate()
    }

    /// Push the current Today snapshot to the watch. Safe to call often — it only
    /// sends when a watch app is actually installed and reachable-ish.
    func syncTodaySnapshot() {
        guard WCSession.isSupported() else { return }
        let session = WCSession.default
        guard session.activationState == .activated,
              session.isPaired,
              session.isWatchAppInstalled else { return }
        guard let snapshot = TodayTasksWidgetSnapshotStore.loadSnapshot(),
              let data = try? JSONEncoder().encode(snapshot) else { return }
        try? session.updateApplicationContext([Self.snapshotKey: data])
    }

    static let snapshotKey = "todaySnapshot"

    // MARK: - WCSessionDelegate

    func session(
        _ session: WCSession,
        activationDidCompleteWith activationState: WCSessionActivationState,
        error: Error?
    ) {
        if activationState == .activated {
            syncTodaySnapshot()
        }
    }

    func sessionDidBecomeInactive(_ session: WCSession) {}

    func sessionDidDeactivate(_ session: WCSession) {
        // Re-activate so a swapped watch keeps receiving updates.
        WCSession.default.activate()
    }

    /// The watch asks for a fresh push when it launches.
    func session(
        _ session: WCSession,
        didReceiveMessage message: [String: Any],
        replyHandler: @escaping ([String: Any]) -> Void
    ) {
        syncTodaySnapshot()
        replyHandler([:])
    }
}
