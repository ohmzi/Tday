import Foundation
import Observation

/// What the person has answered about crash reports, and what answering does.
///
/// The one place every surface that asks agrees: the wizard's last step, the standalone card and
/// the Settings row all read `state` and all write through `share()` and `decline()`, so answering
/// in any of them counts as answering and none of the others asks again. `@Observable` for the
/// reason `MotionPreferenceStore` is: the card disappears the frame the switch moves, and the
/// switch moves when the card is answered.
///
/// `grant` and `revoke` are what turn the SDK on and off. They are injected only so a test can
/// watch them without starting an SDK.
@MainActor
@Observable
final class TelemetryConsentModel {
    /// Whether this build can send reports at all. When it cannot (no DSN: a fork, a debug run),
    /// there is nothing to ask and nothing to switch, and neither the card nor the row appears.
    let isAvailable: Bool

    private(set) var state: TelemetryConsentStore.State

    /// Set by "Read the full FAQ". The card comes back on the next launch, not on the next screen.
    private var isDeferredThisSession = false

    private let store: TelemetryConsentStore
    private let grant: (TelemetryConsentStore) -> Void
    private let revoke: (TelemetryConsentStore) -> Void

    init(
        store: TelemetryConsentStore = TelemetryConsentStore(),
        isAvailable: Bool = SentryConfiguration.isConfigured,
        grant: @escaping (TelemetryConsentStore) -> Void = { TelemetryLifecycle.grant(store: $0) },
        revoke: @escaping (TelemetryConsentStore) -> Void = { TelemetryLifecycle.revoke(store: $0) }
    ) {
        self.store = store
        self.isAvailable = isAvailable
        self.grant = grant
        self.revoke = revoke
        self.state = store.state
    }

    /// The Settings switch.
    var isEnabled: Bool {
        state == .granted
    }

    /// Whether the card is due: asked once, after the workspace is open, and never under another
    /// gate (an update that is required, security questions, the app lock) or after the person has
    /// said "later" this session.
    func shouldPresentCard(workspaceAvailable: Bool, isCoveredByAnotherGate: Bool) -> Bool {
        isAvailable
            && state == .unanswered
            && !isDeferredThisSession
            && workspaceAvailable
            && !isCoveredByAnotherGate
    }

    /// Whether the wizard's last step is due: there is a DSN to send to, the person has not
    /// answered, and the wizard was actually the thing on screen for this session's flow.
    ///
    /// The workspace is deliberately not part of this, for the opposite reason it is part of
    /// [shouldPresentCard]: the step is what the flow ends on, so it is due in the same breath as the
    /// workspace opening rather than after it. An install that reaches the workspace without a wizard
    /// — already signed in at launch, or restarted mid-step — never has a step and falls back to the
    /// card.
    func shouldPresentWizardStep(wizardWasOnScreen: Bool) -> Bool {
        isAvailable
            && state == .unanswered
            && wizardWasOnScreen
    }

    /// "Share reports", or the switch turned on.
    func share() {
        guard isAvailable, state != .granted else {
            return
        }
        grant(store)
        state = store.state
    }

    /// "Not now", or the switch turned off. From "unanswered" this records the answer; from
    /// "granted" it also stops the SDK and deletes what it had not sent.
    func decline() {
        guard state != .denied else {
            return
        }
        revoke(store)
        state = store.state
    }

    /// "Read the full FAQ": not an answer, so nothing is stored and the card is only held back.
    func deferForSession() {
        isDeferredThisSession = true
    }
}
