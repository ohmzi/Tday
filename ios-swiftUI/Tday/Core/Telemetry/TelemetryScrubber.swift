import Foundation
import Sentry

/// What goes on every report besides the failure itself: the build, the mode, the clock's offset
/// from UTC and the language, because dates are central to a task planner. Each is as coarse as
/// it can be while still reproducing a date bug. Never the IANA zone, the full locale or the
/// device's name.
struct TelemetryEventContext {
    var appVersion: String
    var mode: String
    var secondsFromGMT: Int
    var languageCode: String?
    var consentedAt: Date?

    /// Read when the event is sent rather than when the SDK starts: the person can switch between
    /// Local Mode and a server, and move between time zones, inside one launch.
    static func current(consentedAt: Date?) -> TelemetryEventContext {
        TelemetryEventContext(
            appVersion: Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "0.0.0",
            mode: SecureStore().isLocalMode() ? "local" : "server",
            secondsFromGMT: TimeZone.current.secondsFromGMT(),
            languageCode: Locale.current.language.languageCode?.identifier,
            consentedAt: consentedAt
        )
    }

    var tags: [String: String] {
        var tags = [
            "client": "ios",
            "app_version": appVersion,
            "mode": mode,
            "tz_offset": TelemetryScrubber.tzOffsetLabel(secondsFromGMT: secondsFromGMT),
        ]
        if let language = TelemetryScrubber.languageTag(from: languageCode) {
            tags["locale_lang"] = language
        }
        return tags
    }
}

/// The only place a report is made to say less than the SDK would have said.
///
/// The SDK's defaults describe a person far more closely than a bug needs: an install UUID on
/// every event, the full locale and IANA zone, a hash that is stable per device and app, the
/// userInfo of any `NSError`. None of that is configurable on sentry-cocoa 9.x, so it is removed
/// here, on the way out, by an allow-list of what a report keeps rather than a list of what it
/// loses. The logic is plain values and strings so it is testable without the SDK running; the
/// `Event` and `Breadcrumb` entry points at the bottom only apply it.
enum TelemetryScrubber {
    static let maxMessageLength = 300

    /// How much of a message the patterns are run over. They are quadratic on a long run of
    /// lowercase letters, and what is cut to `maxMessageLength` afterwards is not worth reading.
    private static let maxRedactionInputLength = 2_000

    // MARK: - Text

    private static let uuidPattern = #"(?i)\b[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\b"#

    /// Applied in order, and the order matters: a URL goes before the host and the digits inside
    /// it, an e-mail address before the host after its `@`.
    private static let redactions: [(pattern: String, replacement: String)] = [
        (#"(?i)\b(?:https?|wss?|ftp|file)://[^\s"'<>)\]]+"#, "[url]"),
        (#"(?i)jdbc:\S+"#, "[jdbc]"),
        (#"Key \([^)]*\)=\([^)]*\)"#, "Key ([column])=([value])"),
        (#"[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}"#, "[email]"),
        (uuidPattern, "[id]"),
        (#"\bc[a-z0-9]{24}\b"#, "[id]"),
        (#"\b(?:\d{1,3}\.){3}\d{1,3}(?::\d{1,5})?\b"#, "[ip]"),
        (#"(?i)\b(?:[0-9a-f]{1,4}:){7}[0-9a-f]{1,4}\b"#, "[ip]"),
        (#"(?i)\b(?:[0-9a-f]{1,4}:){1,6}:(?:[0-9a-f]{1,4}(?::[0-9a-f]{1,4}){0,5})?(?![0-9a-z])"#, "[ip]"),
        // Written with capitals (`NAS.Example.com`, `Alexs-iPhone.local`): only when it ends in a
        // private or well-known suffix, because without that a Swift type path is a host too. Ahead
        // of the lowercase rule, which would otherwise take the lowercase tail of `Mac.Tail.ts.net`
        // and leave the front of the name.
        (#"(?i)\b(?:[a-z0-9-]+\.)+(?:local|lan|home|internal|localdomain|ts\.net|co\.uk|com|net|org|io|dev|app|co|me|info|xyz|cloud)\b"#, "[host]"),
        // Lowercase only, so `Tday.APIError` and `Array.swift` survive and `nas.example.com`
        // does not.
        (#"\b(?:[a-z0-9-]+\.)+[a-z]{2,}\b"#, "[host]"),
        (#"\d{6,}"#, "[num]"),
    ]

    /// Addresses, identifiers and long numbers replaced by a label; everything else untouched.
    static func redact(_ text: String) -> String {
        redactions.reduce(text) { partial, rule in
            partial.replacingOccurrences(of: rule.pattern, with: rule.replacement, options: .regularExpression)
        }
    }

    /// For an exception or event message: redacted, then cut to a length that cannot carry a
    /// pasted document. Cut after, not before, so an address is never left half-redacted at the
    /// end.
    static func scrubMessage(_ text: String) -> String {
        String(redact(String(text.prefix(maxRedactionInputLength))).prefix(maxMessageLength))
    }

    /// For the path of a binary image: only the app container's id goes. The rest of the path
    /// names the framework a frame is in, and `redact` would take `libdispatch.dylib` for a host.
    static func redactContainerID(_ path: String) -> String {
        path.replacingOccurrences(of: uuidPattern, with: "[id]", options: .regularExpression)
    }

    // MARK: - Tags

    /// `UTC`, `UTC+2`, `UTC-5`, `UTC+5:30`.
    static func tzOffsetLabel(secondsFromGMT: Int) -> String {
        guard secondsFromGMT != 0 else {
            return "UTC"
        }
        let sign = secondsFromGMT < 0 ? "-" : "+"
        let totalMinutes = abs(secondsFromGMT) / 60
        let hours = totalMinutes / 60
        let minutes = totalMinutes % 60
        guard minutes != 0 else {
            return "UTC\(sign)\(hours)"
        }
        let paddedMinutes = minutes < 10 ? "0\(minutes)" : "\(minutes)"
        return "UTC\(sign)\(hours):\(paddedMinutes)"
    }

    /// A two-letter lowercase language, or nil. A three-letter code is not trimmed to fit: `fil`
    /// is not `fi`, and a tag that is wrong is worse than one that is absent.
    static func languageTag(from code: String?) -> String? {
        guard let code = code?.lowercased(),
              code.count == 2,
              code.allSatisfy({ $0.isASCII && $0.isLetter })
        else {
            return nil
        }
        return code
    }

    // MARK: - What is sent at all

    /// A report is a failure, and a failure the person had already agreed to share.
    ///
    /// `timestamp` is the clock for the second half. The SDK replays a crash, an app hang and a
    /// watchdog kill on the next launch, stamped with when they happened, and what happened
    /// before consent is not theirs to have sent.
    static func shouldSend(timestamp: Date?, type: String?, consentedAt: Date?) -> Bool {
        if type == "transaction" {
            return false
        }
        if let timestamp, let consentedAt, timestamp < consentedAt {
            return false
        }
        return true
    }

    // MARK: - Breadcrumbs

    /// The structural trail: the app's own steps (`tday`, `api`, `error`), the app moving between
    /// foreground and background, and whether the device was online. Everything else the SDK
    /// records on its own is a UI or system event, and those carry titles and identifiers.
    private static let allowedBreadcrumbCategories: Set<String> = [
        "tday", "api", "error", "app.lifecycle", "device.connectivity",
    ]

    static func isAllowedBreadcrumbCategory(_ category: String) -> Bool {
        allowedBreadcrumbCategories.contains(category)
    }

    // MARK: - Contexts

    /// The SDK builds `culture` from the system locale and zone, and `user info` from
    /// `NSError.userInfo` or `NSException.userInfo`; neither is wanted whole. `device_app_hash`
    /// is stable per device and app, which makes it an identifier in everything but name.
    static func scrubContexts(_ contexts: [String: [String: Any]]) -> [String: [String: Any]] {
        var scrubbed = contexts
        scrubbed["culture"] = nil
        scrubbed["user info"] = nil
        for key in ["name", "locale", "timezone"] {
            scrubbed["device"]?[key] = nil
        }
        scrubbed["app"]?["device_app_hash"] = nil
        return scrubbed
    }

    // MARK: - Errors

    /// A transport failure is the network being the network, not a bug in T'Day: offline, a
    /// timeout, a dropped connection, a certificate the device does not trust. It stays in the
    /// breadcrumb trail of a later report and is never a report of its own.
    private static let connectivityErrors: Set<URLError.Code> = [
        .notConnectedToInternet, .networkConnectionLost, .timedOut, .cannotConnectToHost,
        .cannotFindHost, .dnsLookupFailed, .cancelled, .dataNotAllowed, .internationalRoamingOff,
        .callIsActive, .secureConnectionFailed, .serverCertificateUntrusted,
    ]

    static func isConnectivityNoise(domain: String, code: Int) -> Bool {
        domain == NSURLErrorDomain && connectivityErrors.contains(URLError.Code(rawValue: code))
    }

    // MARK: - Applying it to the SDK's types

    /// The event to send, or nil when it must not be sent.
    static func scrub(_ event: Event, context: TelemetryEventContext) -> Event? {
        guard shouldSend(timestamp: event.timestamp, type: event.type, consentedAt: context.consentedAt) else {
            return nil
        }

        event.user = nil
        event.serverName = nil
        event.request = nil
        event.message = event.message.map { SentryMessage(formatted: scrubMessage($0.formatted)) }
        for exception in event.exceptions ?? [] {
            exception.value = exception.value.map(scrubMessage)
        }
        event.extra = event.extra?.mapValues { value -> Any in
            if let text = value as? String {
                return scrubMessage(text)
            }
            return value
        }
        event.context = event.context.map(scrubContexts)
        event.breadcrumbs = event.breadcrumbs?.compactMap { scrubBreadcrumb($0) }

        // The path of a binary image carries the app's container id, which changes with every
        // install of every version: another way to tell one device from the next.
        let stacktraces = (event.threads ?? []).compactMap { $0.stacktrace }
            + (event.exceptions ?? []).compactMap { $0.stacktrace }
        for frame in stacktraces.flatMap({ $0.frames }) {
            frame.package = frame.package.map(redactContainerID)
        }
        for image in event.debugMeta ?? [] {
            image.codeFile = image.codeFile.map(redactContainerID)
        }

        var tags = event.tags ?? [:]
        tags.merge(context.tags) { _, ours in ours }
        event.tags = tags
        return event
    }

    /// The breadcrumb to keep, or nil. Whatever its category, a `title` is the name of something
    /// on screen, which here can be a task. The message is left as it is: the app's own are
    /// already reduced to a label by `TdayTelemetry.safeLabel`, and a dotted name like
    /// `sync.replay` is exactly what `redact` would take for a host.
    static func scrubBreadcrumb(_ crumb: Breadcrumb) -> Breadcrumb? {
        guard isAllowedBreadcrumbCategory(crumb.category) else {
            return nil
        }
        crumb.setData(value: nil, key: "title")
        return crumb
    }
}
