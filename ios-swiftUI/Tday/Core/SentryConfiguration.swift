import Foundation
import os
import Sentry
import UIKit

/// Starts Sentry, and only for someone who said yes.
///
/// Reports are opt-in and failures-only: the SDK is never started without a granted answer in
/// `TelemetryConsentStore`, so before that nothing is initialised, buffered or sent, and an error
/// during first run is lost on purpose. The options below are the other half. They are pinned one
/// by one rather than left to the SDK's defaults, because those defaults are made for a product
/// that wants analytics (session pings, sampled performance traces, breadcrumbs for every tap and
/// every request) and a self-hosted planner wants none of it. `docs/TELEMETRY.md` has the contract.
enum SentryConfiguration {
    /// The DSN this build was given. Empty in a fork, a debug run, or a build nobody injected one
    /// into, and then there is no card, no Settings row, and no SDK.
    static var dsn: String {
        TdayTelemetry.bundleString("SENTRY_DSN")
    }

    static var isConfigured: Bool {
        !dsn.isEmpty
    }

    /// Called once from `TdayApp.init`, and again when the person turns reports on mid-session.
    /// When the SDK is not going to run, what an earlier run left on disk is removed instead, so
    /// "off" never leaves a report waiting for the next time it is "on".
    static func start(consent: TelemetryConsentStore = TelemetryConsentStore()) {
        guard isConfigured, consent.isGranted else {
            TelemetryLifecycle.purge()
            return
        }

        // Open before the SDK starts: starting replays last launch's crash through `beforeSend`.
        TelemetryGate.shared.open()
        TdayTelemetry.observeMemoryWarnings()
        // A granted answer always has a time; if it somehow does not, now is the safe reading.
        SentrySDK.start(options: makeOptions(dsn: dsn, consentedAt: consent.consentedAt ?? Date()))
    }

    static func makeOptions(
        dsn: String,
        consentedAt: Date? = nil,
        gate: TelemetryGate = .shared
    ) -> Options {
        let options = Options()
        options.dsn = dsn
        options.environment = ProcessInfo.processInfo.environment["SENTRY_ENVIRONMENT"] ?? "production"

        let version = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "0.0.0"
        let build = Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? "0"
        options.releaseName = "tday-ios@\(version)"
        options.dist = build

        // Failures only. Every error event is sent; nothing else is: no sessions, no traces, no
        // client reports, and none of the automatic instrumentation that feeds them.
        options.sendDefaultPii = false
        options.sampleRate = 1
        options.tracesSampleRate = 0
        options.enableAutoSessionTracking = false
        options.sendClientReports = false
        options.enableAutoPerformanceTracing = false
        options.enableUIViewControllerTracing = false
        options.enableUserInteractionTracing = false
        options.enablePreWarmedAppStartTracing = false
        options.enableStandaloneAppStartTracing = false
        options.enableTimeToFullDisplayTracing = false
        options.enableFileIOTracing = false
        options.enableDataSwizzling = false
        options.enableFileManagerSwizzling = false
        options.enableCoreDataTracing = false

        // Nothing about the network. Swizzling off is what stops the SDK from reading every
        // request, so the self-hosted server's address never reaches a breadcrumb, a span or a
        // failed-request event, and no trace header is added to a request to it.
        options.enableSwizzling = false
        options.enableNetworkTracking = false
        options.enableNetworkBreadcrumbs = false
        options.enableCaptureFailedRequests = false
        options.tracePropagationTargets = []
        options.enablePropagateTraceparent = false

        // No pictures of the screen, and none of what is in memory.
        options.sessionReplay.sessionSampleRate = 0
        options.sessionReplay.onErrorSampleRate = 0
        options.attachScreenshot = false
        options.attachViewHierarchy = false
        options.reportAccessibilityIdentifier = false
        options.enableMemoryIntrospection = false
        options.enableMetricKit = false

        // What a report is for. App hang tracking is deprecated from 9.29 and gone in v10, where
        // MetricKit replaces it; until then it is how a freeze is reported.
        options.enableCrashHandler = true
        options.enableWatchdogTerminationTracking = true
        options.enableAppHangTracking = true
        options.appHangTimeoutInterval = 2

        // `close()` flushes for this long, and tries to send whatever is cached. Turning reports
        // off must not wait on that; `TelemetryLifecycle.revoke` has deleted the cache by then.
        options.shutdownTimeInterval = 0

        // The system's own breadcrumbs stay on and are filtered by what the allow-list keeps.
        options.enableAutoBreadcrumbTracking = true
        options.beforeBreadcrumb = { crumb in
            guard gate.isOpen else {
                return nil
            }
            return TelemetryScrubber.scrubBreadcrumb(crumb)
        }

        options.beforeSend = { event in
            guard gate.isOpen else {
                return nil
            }
            // The SDK stamps every event with the install UUID as `user.id`, and the scrubber
            // drops the user whole. This is the belt to that braces: no address survives either way.
            event.user?.ipAddress = nil
            return TelemetryScrubber.scrub(event, context: .current(consentedAt: consentedAt))
        }

        return options
    }
}

enum TdayTelemetry {
    private static let sensitiveLabelPattern = #"(?i)(https?://|wss?://|[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}|bearer\s+|token=|password=|session=|cookie=|csrf)"#
    private static let sensitiveDataKeyPattern = #"(?i)(authorization|cookie|csrf|token|password|session|secret|email|username|body|payload|header)"#
    private static let routeLikeDataKeys: Set<String> = ["route", "path", "url", "href", "from", "to", "endpoint"]
    private static let staticSegments: Set<String> = [
        "api", "app", "auth", "callback", "credentials", "credentials-key",
        "csrf", "logout", "register", "session", "todo", "todos", "today",
        "overdue", "scheduled", "all", "priority", "instance", "complete",
        "uncomplete", "prioritize", "reorder", "summary", "nlp", "list",
        "floater", "floaterList", "completedTodo", "completedFloater",
        "completed", "calendar", "settings", "latest-release", "app-settings",
        "preferences", "user", "profile", "change-password", "timezone",
        "mobile", "probe", "admin", "ws", "health"
    ]

    static func bundleString(_ key: String) -> String {
        let value = Bundle.main.infoDictionary?[key] as? String ?? ""
        let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.hasPrefix("$(") { return "" }
        return trimmed
    }

    static func sanitizePath(_ raw: String) -> String {
        let withoutQuery = raw.split(separator: "?", maxSplits: 1, omittingEmptySubsequences: false).first
            .map(String.init) ?? raw
        let path: String
        if let components = URLComponents(string: withoutQuery), components.scheme != nil {
            path = components.path.isEmpty ? "/" : components.path
        } else {
            path = withoutQuery.split(separator: "#", maxSplits: 1, omittingEmptySubsequences: false).first
                .map(String.init) ?? "/"
        }

        let segments = path.split(separator: "/").map(String.init)
        guard !segments.isEmpty else { return "/" }
        return "/" + segments.map(sanitizeSegment).joined(separator: "/")
    }

    static func addBreadcrumb(
        _ operation: String,
        category: String = "tday",
        level: SentryLevel = .info,
        data: [String: Any] = [:]
    ) {
        // No-op unless the SDK actually started (it doesn't when SENTRY_DSN is
        // empty, e.g. debug builds). Without this guard, every call still reaches
        // into SentrySDK, which lazily builds its hub/dependency-container on
        // whatever thread calls first — a background network thread inside
        // `performRequestRaw` — and that init touches `UIApplication.applicationState`,
        // a main-thread-only API. When connectivity drops, the burst of failing
        // requests/retries fires breadcrumbs from many threads at once (and the
        // @MainActor SyncManager logs `server.probe`/`sync.replay`), so the main
        // actor stalls behind Sentry's off-main init/locks — the 5-10s freeze.
        guard SentrySDK.isEnabled else { return }

        let breadcrumb = Breadcrumb(level: level, category: category)
        breadcrumb.message = safeLabel(operation)
        // The `data` setter is deprecated in sentry-cocoa 9 (becomes read-only); set per key.
        for (key, value) in data {
            breadcrumb.setData(value: safeDataValue(key: key, value: value), key: key)
        }
        SentrySDK.addBreadcrumb(breadcrumb)
    }

    static func capture(_ error: Error, operation: String, data: [String: Any] = [:]) {
        guard SentrySDK.isEnabled else { return }
        addBreadcrumb(operation, category: "error", level: .error, data: data)

        // Nothing to report for the network being the network: the breadcrumb above is all of it a
        // later report needs.
        guard let reportable = reportableError(error) else { return }
        SentrySDK.capture(error: reportable)
    }

    /// What of an error is reported: its domain and code, and nothing else it carries.
    /// `SentrySDK.capture(error:)` attaches the whole of `NSError.userInfo` and the error's
    /// description, and a failed URLSession call keeps the URL it was requesting in there
    /// (`NSErrorFailingURLStringKey`). Nil for a transport failure, which is not a bug in T'Day.
    static func reportableError(_ error: Error) -> NSError? {
        let nsError = error as NSError
        guard !TelemetryScrubber.isConnectivityNoise(domain: nsError.domain, code: nsError.code) else {
            return nil
        }
        return NSError(domain: nsError.domain, code: nsError.code)
    }

    /// An operation that took longer than it should have, as an event of its own rather than a
    /// span: traces are off, and a slow start is a failure of the same kind as a crash. Grouped by
    /// operation, so one bad release is one issue and not one per device. Called only through
    /// `SlowOperation.report`, which holds the thresholds and the rate limits.
    static func captureSlowOperation(_ operation: SlowOperation, durationMs: Int) {
        guard SentrySDK.isEnabled else { return }
        let event = Event(level: .warning)
        event.message = SentryMessage(formatted: "slow_operation")
        event.fingerprint = ["slow_operation", operation.rawValue]
        event.tags = [
            "operation": operation.rawValue,
            "duration_bucket": SlowOperation.durationBucket(ms: durationMs),
        ]
        event.extra = ["duration_ms": durationMs, "threshold_ms": operation.thresholdMs]
        SentrySDK.capture(event: event)
    }

    /// A memory warning is the last thing the system says before it ends an app for using too
    /// much, and the watchdog report that follows carries no figures; this is where the number
    /// comes from. Registered once for the process and inert while reporting is off, so there is
    /// nothing to take down again.
    private static let memoryWarningObserver: NSObjectProtocol = NotificationCenter.default.addObserver(
        forName: UIApplication.didReceiveMemoryWarningNotification,
        object: nil,
        queue: nil
    ) { _ in
        guard TelemetryGate.shared.isOpen else { return }
        TdayTelemetry.addBreadcrumb(
            "memory.warning",
            level: .warning,
            data: ["available_mb": os_proc_available_memory() / 1_048_576]
        )
    }

    static func observeMemoryWarnings() {
        _ = memoryWarningObserver
    }

    static func safeLabel(_ value: Any?) -> String {
        guard let raw = value.map({ String(describing: $0) })?.trimmingCharacters(in: .whitespacesAndNewlines),
              !raw.isEmpty
        else {
            return "unknown"
        }
        if raw.range(of: sensitiveLabelPattern, options: .regularExpression) != nil {
            return "redacted"
        }
        if raw.count > 24,
           raw.rangeOfCharacter(from: .decimalDigits) != nil,
           raw.range(of: #"^[A-Za-z0-9_.:-]+$"#, options: .regularExpression) != nil {
            return "id"
        }
        let allowed = CharacterSet(charactersIn: "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789_.:-")
        let normalized = String(raw.unicodeScalars.map { allowed.contains($0) ? Character($0) : Character("_") })
        return String(normalized.prefix(64))
    }

    static func safeDataValue(key: String, value: Any?) -> Any {
        if key.range(of: sensitiveDataKeyPattern, options: .regularExpression) != nil {
            return "redacted"
        }
        guard let value else { return "null" }
        switch value {
        case let bool as Bool:
            return bool
        case let number as NSNumber:
            return number
        case let string as String where routeLikeDataKeys.contains(key.lowercased()):
            return sanitizePath(string)
        default:
            return safeLabel(value)
        }
    }

    private static func sanitizeSegment(_ segment: String) -> String {
        let decoded = segment.removingPercentEncoding ?? segment
        let trimmed = decoded.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty { return ":value" }
        if trimmed.range(of: #"^:[A-Za-z][A-Za-z0-9_]*$"#, options: .regularExpression) != nil {
            return trimmed
        }
        if staticSegments.contains(trimmed) { return trimmed }
        if trimmed.range(of: #"^[a-z]{2}(-[A-Z]{2})?$"#, options: .regularExpression) != nil {
            return ":locale"
        }
        if trimmed.contains("@") || trimmed.contains("=") { return ":redacted" }
        if trimmed.count > 24 { return ":id" }
        if trimmed.rangeOfCharacter(from: .decimalDigits) != nil { return ":id" }
        if trimmed.contains("-") || trimmed.contains("_") || trimmed.contains(":") { return ":id" }
        return ":value"
    }

}
