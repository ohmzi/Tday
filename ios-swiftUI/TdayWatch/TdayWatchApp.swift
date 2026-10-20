import SwiftUI
import WatchConnectivity

// MARK: - Snapshot mirror
//
// A self-contained mirror of the phone's TodayTasksWidgetSnapshot. The phone
// sends this as JSON over WatchConnectivity; keeping an independent copy here
// avoids pulling the iOS-only snapshot store into the watch target. Field names
// and coding keys must stay in sync with the phone's structs.

struct WatchTodayTask: Codable, Identifiable, Equatable {
    let id: String
    let title: String
    let dueEpochMs: Int64
    let priority: String
    let description: String?
}

enum WatchTodayStatus: String, Codable {
    case setup
    case empty
    case tasks
}

struct WatchTodaySnapshot: Codable, Equatable {
    let generatedAtEpochMs: Int64
    let title: String
    let status: WatchTodayStatus
    let taskCount: Int
    let tasks: [WatchTodayTask]

    static let empty = WatchTodaySnapshot(
        generatedAtEpochMs: 0,
        title: "T'Day",
        status: .empty,
        taskCount: 0,
        tasks: []
    )
}

/// Shared App Group key the complication reads from. The watch app persists the
/// latest snapshot here whenever the phone pushes one.
enum WatchSnapshotStore {
    static let suiteName = "group.com.ohmz.tday"
    static let key = "tday.watch.todaySnapshot"

    private static var defaults: UserDefaults {
        UserDefaults(suiteName: suiteName) ?? .standard
    }

    static func save(_ snapshot: WatchTodaySnapshot) {
        guard let data = try? JSONEncoder().encode(snapshot) else { return }
        defaults.set(data, forKey: key)
    }

    static func load() -> WatchTodaySnapshot? {
        guard let data = defaults.data(forKey: key),
              let snapshot = try? JSONDecoder().decode(WatchTodaySnapshot.self, from: data) else {
            return nil
        }
        return snapshot
    }
}

// MARK: - Connectivity

/// Receives the Today snapshot pushed by the phone and republishes it to the UI
/// and the complication. Requests a fresh push on launch in case the phone
/// hasn't sent anything since the watch woke.
final class WatchConnectivityReceiver: NSObject, ObservableObject, WCSessionDelegate {
    @Published var snapshot: WatchTodaySnapshot

    private static let snapshotKey = "todaySnapshot"

    override init() {
        snapshot = WatchSnapshotStore.load() ?? .empty
        super.init()
    }

    func activate() {
        guard WCSession.isSupported() else { return }
        let session = WCSession.default
        session.delegate = self
        session.activate()
    }

    /// Nudge the phone to send its current snapshot.
    func requestRefresh() {
        let session = WCSession.default
        guard session.activationState == .activated, session.isReachable else { return }
        session.sendMessage(["request": "todaySnapshot"], replyHandler: nil, errorHandler: nil)
    }

    private func apply(_ context: [String: Any]) {
        guard let data = context[Self.snapshotKey] as? Data,
              let decoded = try? JSONDecoder().decode(WatchTodaySnapshot.self, from: data) else {
            return
        }
        WatchSnapshotStore.save(decoded)
        DispatchQueue.main.async { self.snapshot = decoded }
    }

    // MARK: WCSessionDelegate

    func session(
        _ session: WCSession,
        activationDidCompleteWith activationState: WCSessionActivationState,
        error: Error?
    ) {
        if activationState == .activated {
            DispatchQueue.main.async { self.requestRefresh() }
        }
    }

    func session(_ session: WCSession, didReceiveApplicationContext applicationContext: [String: Any]) {
        apply(applicationContext)
    }

    func session(_ session: WCSession, didReceiveMessage message: [String: Any]) {
        apply(message)
    }
}

// MARK: - App

@main
struct TdayWatchApp: App {
    @StateObject private var receiver = WatchConnectivityReceiver()

    var body: some Scene {
        WindowGroup {
            WatchTodayListView(snapshot: receiver.snapshot)
                .onAppear {
                    receiver.activate()
                    receiver.requestRefresh()
                }
        }
    }
}
