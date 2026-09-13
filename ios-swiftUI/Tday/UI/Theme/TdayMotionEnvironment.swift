import SwiftUI

/// Whether this app should be animating, and the one place that question is answered.
///
/// iOS is the client that is handed the question outright. `accessibilityReduceMotion`
/// is a per-user accessibility setting SwiftUI publishes into *every* environment, live,
/// so unlike Android — which has only a device-wide animator scale and had to grow a
/// switch of its own, see `TdayMotion.kt` — there is no answer to invent here and no
/// second switch to offer. What had to be built is the single place that answer becomes
/// an `Animation?`, because before this type there were seven: seven views each reading
/// the setting and each spelling `reduceMotion ? nil : x` in their own hand, which is
/// seven places to forget and no place to fix it once.
///
/// `nil` is the whole mechanism, and it is `docs/motion.md`'s fifth idiom rule in one
/// line: `withAnimation(nil)` and `.animation(nil, value:)` still apply the state
/// change, they just do not animate the trip. The surface is drawn finished, in the
/// frame the state changed. Refusing the animation can never leave anything held at the
/// start of a fade, and there is no wait left behind to sit through.
///
/// `Equatable` over a single `Bool`, so a view reading it out of the environment is
/// diffed on the answer rather than on the identity of whatever produced it — the reason
/// this is a value type and not the closure it could just as easily have been.
struct TdayMotionResolution: Equatable {

    /// Animate. Everything the vocabulary says, at the amplitude it says it.
    static let full = TdayMotionResolution(isEnabled: true)

    /// Do not. Every surface still arrives at the state it was going to; it arrives
    /// there now.
    static let reduced = TdayMotionResolution(isEnabled: false)

    /// The boolean, for the questions that are not about an `Animation`.
    ///
    /// A `.transition` is the one that forces this to exist: `TdayFeedItemMotion.row`
    /// has to collapse to `.identity` rather than be handed a nil animation, because a
    /// transition does not open the transaction it plays in and a nil cannot reach back
    /// out and close one. Anything that can take an `Animation?` should go through
    /// `callAsFunction` instead and leave the branch here.
    let isEnabled: Bool

    /// The animation, or `nil` where the user has asked for less motion.
    ///
    /// Spelled as `callAsFunction` so the call site reads `tdayAnimation(TdayMotion.settle)`
    /// — the environment value and the verb are one token, which is what keeps the
    /// decision from being re-derived at each of the sites that used to hold it. A
    /// method with a name would read as a second thing to remember to call; this reads
    /// as the animation itself, resolved.
    ///
    /// Not `@autoclosure`: every argument is either a stored token or a `timingCurve`
    /// construction, so there is nothing to defer, and deferring it would make what runs
    /// under reduced motion depend on where the parentheses went.
    func callAsFunction(_ animation: Animation) -> Animation? {
        isEnabled ? animation : nil
    }
}

/// The app's own answer, where something upstream has resolved one.
///
/// `nil` means nobody has — and that is a third state rather than a default of "animate",
/// because a default is exactly what this file must not guess. The accessor below turns
/// the absence into the system's own answer, which is available in the same
/// `EnvironmentValues` and is never absent.
private struct TdayMotionOverrideKey: EnvironmentKey {
    static let defaultValue: Bool? = nil
}

extension EnvironmentValues {

    /// The resolved animation gate — read this, not `accessibilityReduceMotion`.
    ///
    /// Composed rather than stored, and the fallback is the point. A `UIHostingController`
    /// built by hand starts a fresh environment and inherits none of the values the app
    /// root provided — `CalendarPagingScrollView` hosts every month page in exactly one —
    /// while the *system* keys keep resolving, because UIKit feeds those from the trait
    /// environment the hosted view is already in. So a surface that escapes the provider
    /// still gets the user's real answer here; what it gives up is being told when that
    /// answer changes, which is the same trade `rememberTdayMotionScale` makes on Android
    /// for a screen drawn outside `ProvideTdayMotionScale`.
    ///
    /// That trade is also why `tdayResolvedMotion()` exists rather than this accessor
    /// being the whole story: an accessor reading a key it never declared a dependency on
    /// is a value that is correct at first draw and says nothing about the flip. The root
    /// modifier declares that dependency in the ordinary way and writes the answer down,
    /// so a user turning Reduce Motion on mid-session invalidates the subtree instead of
    /// waiting for something unrelated to.
    var tdayAnimation: TdayMotionResolution {
        get {
            TdayMotionResolution(
                isEnabled: self[TdayMotionOverrideKey.self] ?? !accessibilityReduceMotion
            )
        }
        set { self[TdayMotionOverrideKey.self] = newValue.isEnabled }
    }
}

/// Reads the accessibility setting and publishes the app's answer to everything below.
private struct TdayResolvedMotionModifier: ViewModifier {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    func body(content: Content) -> some View {
        content.environment(\.tdayAnimation, reduceMotion ? .reduced : .full)
    }
}

extension View {

    /// Installs the motion gate for this subtree.
    ///
    /// Applied inside `tdayAppTheme`, which is the one wrapper both of the app's window
    /// roots already go through — the main hierarchy and `AppLockWindowHost`'s separate
    /// window, which re-applies the theme for the same reason it would have to re-apply
    /// this. One read for the whole app, in the place the app already accepted as the
    /// place a root decision is made, is the same call `ProvideTdayMotionScale` makes
    /// inside Android's `TdayTheme`.
    func tdayResolvedMotion() -> some View {
        modifier(TdayResolvedMotionModifier())
    }
}
