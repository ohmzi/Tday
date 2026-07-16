import BackgroundTasks
import Foundation

/// Drives a periodic (~30 min) background sync so the Today/Floater widgets stay fresh even
/// while the app is suspended. The widget itself has no network access — it renders the App
/// Group snapshot — so the only way to update it with new server data is to sync in the app
/// process. iOS schedules `BGAppRefreshTask` opportunistically, so 30 minutes is an
/// *earliest-begin* hint, not a guarantee; the OS may run it less often based on usage.
///
/// Reloading is still conditional: the sync writes through OfflineCacheManager →
/// `saveTodayTasks`/`saveFloaterTasks`, which skip the WidgetKit reload when the displayed
/// content is unchanged. So a background run that finds nothing new for the widget leaves it
/// untouched while the app still holds the latest state.
enum WidgetBackgroundRefresh {
    static let taskIdentifier = "com.ohmz.tday.ios.widgetRefresh"
    /// Earliest-begin hint. iOS decides the actual cadence.
    private static let refreshInterval: TimeInterval = 30 * 60

    /// Registers the task handler. Must run before the app finishes launching (TdayApp.init()).
    static func register() {
        BGTaskScheduler.shared.register(
            forTaskWithIdentifier: taskIdentifier,
            using: nil
        ) { task in
            guard let refreshTask = task as? BGAppRefreshTask else {
                task.setTaskCompleted(success: false)
                return
            }
            handle(refreshTask)
        }
    }

    /// Submits the next request. Safe to call repeatedly (submitting replaces any pending
    /// request for the same identifier). Call when the app backgrounds and after each run.
    static func scheduleNext() {
        let request = BGAppRefreshTaskRequest(identifier: taskIdentifier)
        request.earliestBeginDate = Date(timeIntervalSinceNow: refreshInterval)
        do {
            try BGTaskScheduler.shared.submit(request)
        } catch {
            // Simulator has no BGTaskScheduler; a denied/over-budget submit is non-fatal.
            TdayTelemetry.addBreadcrumb(
                "widget.bgrefresh",
                level: .warning,
                data: ["phase": "schedule_failed"]
            )
        }
    }

    private static func handle(_ task: BGAppRefreshTask) {
        // Re-arm first so the chain survives even if this run is cut short.
        scheduleNext()
        TdayTelemetry.addBreadcrumb("widget.bgrefresh", data: ["phase": "run"])

        let work = Task { @MainActor in
            // notifyOfflineFailure:false — no toast from a background run. force:true so the
            // 30-min cadence actually reaches the server. The reload stays conditional on the
            // displayed widget content changing (guard in the snapshot stores).
            let result = await AppContainer.shared.syncManager.syncCachedData(
                force: true,
                replayPendingMutations: true,
                notifyOfflineFailure: false
            )
            if case .success = result {
                task.setTaskCompleted(success: true)
            } else {
                task.setTaskCompleted(success: false)
            }
        }

        // On expiration only cancel; the work task's continuation calls setTaskCompleted
        // exactly once, avoiding a double-completion race.
        task.expirationHandler = {
            work.cancel()
        }
    }
}
