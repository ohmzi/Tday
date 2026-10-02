import Foundation

/// The operations whose slowness is reported as a failure, with how slow is too slow.
///
/// A report is only ever sent when something goes wrong, and "the app took eleven seconds to open"
/// is something going wrong that never crashes. The ids and thresholds are the same on every client
/// (`docs/TELEMETRY.md`), so one slow release reads as one issue across platforms.
enum SlowOperation: String, CaseIterable {
    case coldStart = "cold_start"
    case firstDataReady = "first_data_ready"
    case dbOpenMigrate = "db_open_migrate"
    case cacheHydrate = "cache_hydrate"
    case syncReplay = "sync_replay"
    case apiCall = "api_call"
    case widgetRefresh = "widget_refresh"
    case reminderReschedule = "reminder_reschedule"

    var thresholdMs: Int {
        switch self {
        case .coldStart:
            return 5_000
        case .firstDataReady:
            return 8_000
        case .dbOpenMigrate:
            return 3_000
        case .cacheHydrate:
            return 2_000
        case .syncReplay:
            return 15_000
        case .apiCall, .widgetRefresh, .reminderReschedule:
            return 10_000
        }
    }

    /// How slow, in the coarse steps a dashboard can group by: `<5s`, `5-10s`, `10-30s`,
    /// `30-60s`, `>60s`.
    static func durationBucket(ms: Int) -> String {
        switch ms {
        case ..<5_000:
            return "<5s"
        case ..<10_000:
            return "5-10s"
        case ..<30_000:
            return "10-30s"
        case ...60_000:
            return "30-60s"
        default:
            return ">60s"
        }
    }

    /// Reports `durationMs` if it is over the threshold, and the rate limits allow it.
    ///
    /// A no-op unless reporting is on, and checked first, so a slow start while the setting is off
    /// does not use up the day's one report.
    static func report(_ operation: SlowOperation, durationMs: Int) {
        guard TelemetryGate.shared.isOpen else {
            return
        }
        SlowOperationLimiter.shared.report(operation, durationMs: durationMs) {
            TdayTelemetry.captureSlowOperation($0, durationMs: $1)
        }
    }
}

/// How often a slow operation may be reported: once per operation per process, five in a process,
/// and not again for 24 hours (kept across launches) for an operation already reported. A device
/// that is slow once is usually slow every time, and one issue per device per day is the whole of
/// what that tells anyone.
final class SlowOperationLimiter: @unchecked Sendable {
    static let shared = SlowOperationLimiter()

    static let maxPerProcess = 5
    static let cooldown: TimeInterval = 24 * 60 * 60

    private static let lastReportedKey = "telemetry.slowOperation.lastReported"

    private let defaults: UserDefaults
    private let now: () -> Date
    private let lock = NSLock()
    private var reportedThisProcess: Set<SlowOperation> = []

    /// `defaults` and `now` are injectable so a test can drive a suite and a clock of its own.
    init(defaults: UserDefaults = .standard, now: @escaping () -> Date = Date.init) {
        self.defaults = defaults
        self.now = now
    }

    /// Calls `emit` when `durationMs` is over the operation's threshold and the limits allow it,
    /// and records that it did.
    func report(
        _ operation: SlowOperation,
        durationMs: Int,
        emit: (SlowOperation, Int) -> Void
    ) {
        guard durationMs >= operation.thresholdMs, reserve(operation) else {
            return
        }
        emit(operation, durationMs)
    }

    /// Claims the operation's slot, or says it has none.
    private func reserve(_ operation: SlowOperation) -> Bool {
        lock.lock()
        defer { lock.unlock() }

        guard !reportedThisProcess.contains(operation),
              reportedThisProcess.count < Self.maxPerProcess
        else {
            return false
        }

        var lastReported = defaults.dictionary(forKey: Self.lastReportedKey) as? [String: Double] ?? [:]
        let current = now().timeIntervalSince1970
        if let previous = lastReported[operation.rawValue], current - previous < Self.cooldown {
            return false
        }

        reportedThisProcess.insert(operation)
        lastReported[operation.rawValue] = current
        defaults.set(lastReported, forKey: Self.lastReportedKey)
        return true
    }
}
