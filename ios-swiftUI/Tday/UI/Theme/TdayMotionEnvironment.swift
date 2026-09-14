import SwiftUI

/// Whether this app should be animating, and the one place that question is answered.
///
/// iOS is the client that is handed the question outright. `accessibilityReduceMotion`
/// is a per-user accessibility setting SwiftUI publishes into *every* environment, live,
/// so unlike Android — which has only a device-wide animator scale and had to grow a
/// switch of its own, see `TdayMotion.kt` — there is no answer to invent here and no
/// second switch to offer. What had to be built is the single place that answer is
/// applied, because before this type there were seven: seven views each reading the
/// setting and each spelling `reduceMotion ? nil : x` in their own hand, which is seven
/// places to forget and no place to fix it once.
///
/// There are two mechanisms below, and which one a surface gets is a judgement about
/// amplitude rather than a default. Where the travel was already nothing — a tint, a
/// crossfade, a tab hand-over drawn in the slot the last one had — the answer is `nil`,
/// which is `docs/motion.md`'s fifth idiom rule in one line: `withAnimation(nil)` and
/// `.animation(nil, value:)` still apply the state change, they just do not animate the
/// trip, so the surface is drawn finished in the frame the state changed. Where the
/// travel IS the animation — a card crossing the whole screen, a toast over a feed —
/// `nil` gives the eye nothing to follow and leaves a surface that blinks rather than
/// arrives, so the answer is a substitute the call site has to name: the `reduced:`
/// overloads further down. Neither one can leave anything held at the start of a fade
/// or any wait behind to sit through; what they differ on is whether there is anything
/// left to follow to the finished state.
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
    /// `callAsFunction` instead and leave the branch here; a `.transition` whose travel
    /// is the thing being refused has `transition(_:reduced:)` below, which keeps the
    /// crossfade rather than collapsing the whole leg.
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

    /// The animation for a motion whose **amplitude** is the thing being refused,
    /// rather than the motion itself.
    ///
    /// Reduce Motion is not a request for a static app, and reading it as one is how
    /// an accommodation turns into a worse experience than the thing it accommodates.
    /// Apple's guidance names large-amplitude travel as what triggers a vestibular
    /// response — a card crossing the whole screen, a grid paging sideways — and names
    /// a crossfade as the substitute. `nil` above is the right answer wherever the
    /// amplitude was already nothing: a tint, a fade, a tab hand-over drawn in the slot
    /// the last one had. It is the wrong answer where the travel IS the animation,
    /// because refusing it leaves a surface that appears and vanishes between two
    /// frames, and a surface with no context around it to explain the jump reads as a
    /// rendering fault rather than as the app doing what it was asked.
    ///
    /// So this overload makes the call site supply both halves — the motion as
    /// designed, and what stands in for it — which is the only form in which the
    /// judgement is reviewable. It returns a non-optional on purpose: a site that
    /// reaches for this one has already decided that *something* plays, and letting a
    /// `nil` back out here would be that decision silently reversed by whoever wrote
    /// the substitute.
    func callAsFunction(_ animation: Animation, reduced substitute: Animation) -> Animation {
        isEnabled ? animation : substitute
    }

    /// The `.transition` half of the same judgement: drop the travel, keep the fade.
    ///
    /// Deliberately not the same answer `TdayFeedItemMotion.row` gives, which collapses
    /// to `.identity`. A feed row has neighbours that close over the space it leaves,
    /// so its arrival and departure are legible with no transition at all; a modal card,
    /// a toast or a floating dock has nothing around it doing that work, and the fade is
    /// the only thing left saying the surface arrived rather than blinked.
    func transition(_ full: AnyTransition, reduced substitute: AnyTransition) -> AnyTransition {
        isEnabled ? full : substitute
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
    /// Applied in two places, and the second is not a duplicate of the first.
    /// `tdayAppTheme` carries it because that is the one wrapper both window roots go
    /// through — the main hierarchy and `AppLockWindowHost`'s separate window, which
    /// re-applies the theme for the same reason it would have to re-apply this; the same
    /// call `ProvideTdayMotionScale` makes inside Android's `TdayTheme`. But
    /// `AppRootView` applies that theme to its *own body*, and a view's `@Environment`
    /// resolves against the environment the view was placed in — so the theme's copy
    /// reaches every descendant of `AppRootView` and none of `AppRootView`'s own
    /// properties. `TdayApp`'s scene root installs it above the root view for exactly
    /// that gap: it is what puts the tab hand-over, the dock and the onboarding blur on
    /// the live answer rather than on the accessor's fallback below.
    func tdayResolvedMotion() -> some View {
        modifier(TdayResolvedMotionModifier())
    }
}
