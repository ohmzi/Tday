import Foundation
import Sentry

/// Whether reports may leave the device right now, as an answer any thread can ask.
///
/// Closed by default and opened only by `SentryConfiguration.start()` once consent is granted. It
/// is the first check in `beforeSend` and `beforeBreadcrumb`, and it is closed before anything
/// else on the way out, so nothing the SDK is still holding can slip through while it shuts down.
final class TelemetryGate: @unchecked Sendable {
    static let shared = TelemetryGate()

    private let lock = NSLock()
    private var opened = false

    var isOpen: Bool {
        lock.lock()
        defer { lock.unlock() }
        return opened
    }

    func open() {
        lock.lock()
        defer { lock.unlock() }
        opened = true
    }

    func close() {
        lock.lock()
        defer { lock.unlock() }
        opened = false
    }
}

/// Turning crash reports on and off while the app is running, and the clean-up that makes "off"
/// mean off.
///
/// `SentrySDK.close()` alone does not. It stops the integrations, but it leaves what the SDK
/// wrote to disk — queued envelopes, crash reports waiting for the next launch, the state a
/// watchdog report is built from — and then tries to send the queue on its way out. So revoking
/// removes those first, closes, and removes whatever the shutdown wrote. Whatever still slips
/// past, a launch with anything but a granted answer purges again before it does anything else
/// (`SentryConfiguration.start()`), and the SDK never starts to send it.
enum TelemetryLifecycle {
    /// Where the SDK keeps what it has not sent yet, under the caches directory: its own
    /// working directory, and the crash reporter's.
    private static let sdkDirectoryNames = ["io.sentry", "SentryCrash"]

    /// The person said yes: start from nothing, record it, and start the SDK.
    static func grant(store: TelemetryConsentStore = TelemetryConsentStore()) {
        purge()
        store.grant()
        SentryConfiguration.start(consent: store)
    }

    /// The person said no, or switched it off: record it first, so a launch that follows an
    /// interrupted shutdown still reads "denied".
    static func revoke(
        store: TelemetryConsentStore = TelemetryConsentStore(),
        gate: TelemetryGate = .shared
    ) {
        store.deny()
        gate.close()
        // Before the close, so there is nothing queued for it to flush.
        purge()
        // Never on an SDK that is not running: closing builds the hub it would have closed.
        if SentrySDK.isEnabled {
            SentrySDK.configureScope { scope in
                scope.clear()
            }
            SentrySDK.close()
        }
        purge()
    }

    /// Deletes everything the SDK keeps on disk. Safe to call at any time, and when there is
    /// nothing there.
    static func purge(cachesDirectory: URL? = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask).first) {
        guard let cachesDirectory else {
            return
        }
        for name in sdkDirectoryNames {
            try? FileManager.default.removeItem(at: cachesDirectory.appendingPathComponent(name, isDirectory: true))
        }
    }
}
