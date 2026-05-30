import Foundation
import Sentry

enum SentryConfiguration {
    static func start() {
        let dsn = TdayTelemetry.bundleString("SENTRY_DSN")
        guard !dsn.isEmpty else { return }

        SentrySDK.start { options in
            options.dsn = dsn
            let environment = ProcessInfo.processInfo.environment["SENTRY_ENVIRONMENT"] ?? "production"
            options.environment = environment

            let version = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "0.0.0"
            let build = Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? "0"
            options.releaseName = "tday-ios@\(version)"
            options.dist = build

            options.sendDefaultPii = false
            options.enableAutoSessionTracking = true
            options.tracesSampleRate = TdayTelemetry.traceSampleRate(
                rawValue: TdayTelemetry.bundleString("SENTRY_TRACES_SAMPLE_RATE"),
                fallback: environment == "production" ? 0.2 : 1.0
            )

            options.beforeSend = { event in
                event.user?.ipAddress = nil
                return event
            }
        }
    }
}

enum TdayTelemetry {
    private static let sensitiveLabelPattern = #"(?i)(https?://|wss?://|[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}|bearer\s+|token=|password=|session=|cookie=|csrf)"#
    private static let sensitiveDataKeyPattern = #"(?i)(authorization|cookie|csrf|token|password|session|secret|email|body|payload|header)"#
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

    static func traceSampleRate(rawValue: String, fallback: Double) -> NSNumber {
        let parsed = Double(rawValue).map { min(1.0, max(0.0, $0)) } ?? fallback
        return NSNumber(value: parsed)
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
        let breadcrumb = Breadcrumb(level: level, category: category)
        breadcrumb.message = safeLabel(operation)
        breadcrumb.data = Dictionary(uniqueKeysWithValues: data.map { key, value in
            (key, safeDataValue(key: key, value: value))
        })
        SentrySDK.addBreadcrumb(breadcrumb)
    }

    static func capture(_ error: Error, operation: String, data: [String: Any] = [:]) {
        addBreadcrumb(operation, category: "error", level: .error, data: data)
        SentrySDK.capture(error: error)
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
