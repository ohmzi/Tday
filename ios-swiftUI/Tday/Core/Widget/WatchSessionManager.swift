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
    ///
    /// Not main-actor code, but it can run on the main thread. Most calls come from a
    /// `WidgetSnapshotWriter.submit` block on the writer's background queue, and the
    /// delegate callbacks below call it on WatchConnectivity's own queue. The two
    /// `saveTodayTasks` callers (the Focus filter intent and a background
    /// `refreshTodayWidgetSnapshot`) are main-actor code and reach it through `runNow`'s
    /// `queue.sync`, which GCD usually runs on the calling thread. There it runs on main
    /// with main blocked until it returns. So keep it free of main-actor work:
    /// `MainActor.assumeIsolated` would trap on the writer's queue, and
    /// `DispatchQueue.main.sync` would deadlock on the `runNow` path. The writer moved
    /// off main so that a check-off's snapshot write would not stall the list animation.
    func syncTodaySnapshot() {
        guard WCSession.isSupported() else { return }
        let session = WCSession.default
        guard session.activationState == .activated,
              session.isPaired,
              session.isWatchAppInstalled else { return }
        guard let snapshot = TodayTasksWidgetSnapshotStore.loadSnapshot(),
              let data = try? JSONEncoder().encode(snapshot.withoutUpcomingDays()) else { return }
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
