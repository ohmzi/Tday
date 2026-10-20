import Foundation
import Observation

/// What the person has answered about crash reports, and what answering does.
///
/// The one place every surface that asks agrees: the wizard's last step, the standalone card and
/// the Settings row all read `state` and all write through `share()` and `decline()`, so answering in
/// any of them counts as answering for the connect flow in progress and none of the others asks
/// again for it. `@Observable` for the reason `MotionPreferenceStore` is: the card disappears the
/// frame the switch moves, and the switch moves when the card is answered.
///
/// `grant` and `revoke` are what turn the SDK on and off. They are injected only so a test can
/// watch them without starting an SDK.
@MainActor
@Observable
final class TelemetryConsentModel {
    /// Whether this build can send reports at all. When it cannot (no DSN: a fork, a debug run),
    /// there is nothing to ask and nothing to switch, and neither the card nor the row appears.
    let isAvailable: Bool

    /// The device's answer: what the Settings switch shows, and what the SDK obeys. It outlives
    /// sign-out on purpose, so it is not an answer for a new connect flow.
    private(set) var state: TelemetryConsentStore.State

    /// Whether the connect flow now on screen has been answered.
    ///
    /// Separate from `state` because the two answer different questions. `state` is the device's
    /// answer, so a person who answered on a previous sign-in still carries it; this is the answer
    /// for the flow in progress, and every flow starts without one. That is what makes the wizard's
    /// last step due again on every sign-in, and what stops a flow that opens a workspace without an
    /// answer from sending: it asks, and an unanswered device still reads as off.
    ///
    /// In memory only, and never reported. [beginConnectFlow] resets it when a flow begins rather
    /// than when one ends.
    private(set) var answeredInConnectFlow = false

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

    /// Whether the card is due: the fallback for a device nobody has ever asked, after the workspace
    /// is open, and never under another gate (an update that is required, security questions, the app
    /// lock) or after the person has said "later" this session.
    func shouldPresentCard(workspaceAvailable: Bool, isCoveredByAnotherGate: Bool) -> Bool {
        isAvailable
            && state == .unanswered
            && !isDeferredThisSession
            && workspaceAvailable
            && !isCoveredByAnotherGate
    }

    /// Whether the wizard's last step is due: there is a DSN to send to, this connect flow has not
    /// been answered, and the wizard was actually the thing on screen for it.
    ///
    /// The device's stored answer is deliberately not part of this. It outlives sign-out, but it is
    /// not an answer for a new flow: every sign-in asks again, and only an answer given in this flow
    /// (either button on the step, or the Settings switch) takes the step away.
    ///
    /// The workspace is deliberately not part of this either, for the opposite reason it is part of
    /// [shouldPresentCard]: the step is what the flow ends on, so it is due in the same breath as the
    /// workspace opening rather than after it. An install that reaches the workspace without a wizard
    /// — already signed in at launch, or restarted mid-step — never has a step and falls back to the
    /// card.
    func shouldPresentWizardStep(wizardWasOnScreen: Bool) -> Bool {
        isAvailable
            && !answeredInConnectFlow
            && wizardWasOnScreen
    }

    /// A new connect flow has begun — the sign-in wizard is on screen — so the question is its to ask
    /// again. Nothing else changes: the stored answer, and the SDK obeying it, are left exactly as
    /// they were, so this makes the step due, never granted.
    func beginConnectFlow() {
        answeredInConnectFlow = false
    }

    /// "Share reports", or the switch turned on. Either is an answer for the flow in progress, which
    /// is why the flag moves before the early return: saying yes to a device that already said yes is
    /// still this flow's answer, and the step must not stay up because the store had nothing to
    /// write.
    func share() {
        guard isAvailable else {
            return
        }
        answeredInConnectFlow = true
        guard state != .granted else {
            return
        }
        grant(store)
        state = store.state
    }

    /// "Not now", or the switch turned off. From "unanswered" this records the answer; from
    /// "granted" it also stops the SDK and deletes what it had not sent.
    func decline() {
        answeredInConnectFlow = true
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
