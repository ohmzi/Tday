import Foundation
import Observation

/// The in-app Reduce Motion preference — a second switch that can only ever subtract.
///
/// iOS is handed the platform's own answer through `accessibilityReduceMotion`, and for a long
/// time that was the whole story: `TdayMotionEnvironment`'s header said as much, and it was right
/// while the system setting was the only answer available. What that left is the same gap Android
/// closed in `PR 34b` — a user who wants *this app* quieter than their phone, or who has already
/// found the system toggle and does not want to change every app on the device to quiet one. The
/// two settings compose the way `TdayMotion.kt`'s `effectiveMotionScale` composes them: a system
/// that has already removed animation wins, and this switch can only ever remove more. It is
/// never able to hand motion back, which is why the Settings row shows itself already-on and
/// untappable in that case rather than offering a choice it would then ignore.
///
/// `@Observable` rather than the plain stored `load()`/`save()` pair `ThemeStore` uses, because
/// liveness is the entire point here. The ledger's own complaint about the old coverage is "on
/// every client the setting cannot be seen to change at runtime", and a value read once at launch
/// would reproduce it on the one screen that is about the setting. A view that reads `isEnabled`
/// in its body is registered on it, so the flip reaches the root `\.tdayAnimation` gate — and
/// through it every animation below — in the same frame the switch moves.
///
/// Local only, deliberately, and the same call Android made: this is a fact about the device the
/// reader is holding, not about their account, so it is not in `PreferencesDto` and never leaves
/// the phone. `ThemeStore`'s comment carries the same argument for the same reason.
///
/// Persisting is an explicit `persist()` rather than a `didSet` on the property. The two read the
/// same, and only one of them is a shape this codebase has already compiled: no `@Observable`
/// class here carries a property observer, and the macro rewrites stored properties on its way
/// through, so a `didSet` is the kind of thing this class should not be the first to discover.
/// The cost is that a future writer has to call `persist()` — which is why there is exactly one
/// writer, `AppViewModel.reduceMotion`, and it says so.
@Observable
final class MotionPreferenceStore {

    /// How much quieter than the phone's own setting this app should be. Default off.
    ///
    /// Written through `persist()`; the initial value is what the last launch left behind.
    var isEnabled: Bool

    private let defaults: UserDefaults

    private static let key = "motion.reduce"

    /// `defaults` is injectable so a test can drive a suite of its own; nothing in the app passes
    /// anything but the standard one.
    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        self.isEnabled = defaults.bool(forKey: Self.key)
    }

    /// Records the current value. Called by the one writer of `isEnabled`, immediately after it
    /// writes, so the store stays a value holder and the persistence sits with the intent.
    func persist() {
        defaults.set(isEnabled, forKey: Self.key)
    }

    /// Forgets the choice and leaves it unset — the state a fresh install is in.
    func clear() {
        defaults.removeObject(forKey: Self.key)
        isEnabled = false
    }
}
