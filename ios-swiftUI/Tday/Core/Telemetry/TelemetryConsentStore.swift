import Foundation

/// The per-device answer to "may T'Day send a short technical report when something fails?".
///
/// Three answers rather than a Bool, because "no answer yet" is a state of its own: an `off` that
/// could mean either would either re-ask everyone who said no or never ask anyone. `unanswered`
/// behaves as off everywhere except in that one question.
///
/// UserDefaults and not the keychain, on purpose. This is a statement about the install, so it
/// should reset with one, and the keychain outlives an uninstall (see
/// `SecureStore.clearInstallScopedValuesIfAppReinstalled`). It also has to be readable in
/// `TdayApp.init`, before `AppContainer` exists, which is the moment the SDK either starts or
/// never does. Signing out, leaving a workspace and deleting local data all leave it alone: they
/// are about an account or a workspace, and this is about the device.
///
/// What a sign-in changes is when the question is asked, not what the answer means: the connect flow
/// asks again on every sign-in, so this stored answer is not treated as an answer for the flow in
/// progress. That per-flow half lives in memory, on `TelemetryConsentModel`; this store stays the one
/// place the answer itself lives, and what the SDK obeys.
struct TelemetryConsentStore {
    enum State: Equatable {
        case unanswered
        case granted
        case denied
    }

    private let defaults: UserDefaults

    private static let consentKey = "telemetry.consent"
    private static let consentedAtKey = "telemetry.consentAt"

    /// `defaults` is injectable so a test can drive a suite of its own; the app passes the
    /// standard one.
    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
    }

    var state: State {
        guard let answer = defaults.object(forKey: Self.consentKey) as? Bool else {
            return .unanswered
        }
        return answer ? .granted : .denied
    }

    var isGranted: Bool {
        state == .granted
    }

    /// When the person said yes, and nil unless they currently have.
    ///
    /// Nothing the SDK finds on disk or replays from the system may predate it: it re-sends
    /// crashes, hangs and watchdog kills on the next launch, and a failure that happened while
    /// the setting was off is one they never agreed to share.
    var consentedAt: Date? {
        guard isGranted, let seconds = defaults.object(forKey: Self.consentedAtKey) as? Double else {
            return nil
        }
        return Date(timeIntervalSince1970: seconds)
    }

    func grant(at date: Date = Date()) {
        defaults.set(true, forKey: Self.consentKey)
        defaults.set(date.timeIntervalSince1970, forKey: Self.consentedAtKey)
    }

    func deny() {
        defaults.set(false, forKey: Self.consentKey)
        defaults.removeObject(forKey: Self.consentedAtKey)
    }
}
