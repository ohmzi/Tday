import SwiftUI

/// The SwiftUI surface over `TdayMotionGenerated` — the hand-written half of the
/// motion vocabulary, and the half a view should name.
///
/// The generated file carries raw numbers because it has to: a Gradle task wrote
/// it and knows nothing about SwiftUI. Everything that turns those numbers into
/// something `withAnimation(_:)` will accept lives here, and nothing here types a
/// number of its own — every value below is read back out of
/// `TdayMotionGenerated`, so the drift gate (`./gradlew :shared:verifyMotionTokens`)
/// covers this file by covering that one. A literal appearing in this file is a
/// bug even when it is the right literal.
///
/// The shape of the easings is the part worth explaining. SwiftUI has no
/// standalone curve value: `Animation.timingCurve(_:_:_:_:duration:)` is its only
/// cubic-bezier constructor and it demands the duration in the same breath, so a
/// curve cannot be stored now and paired with a rung later the way CSS's
/// `transition-timing-function` or Compose's `Easing` can. Exposing the easings as
/// `Animation` constants would mean freezing one duration per curve and hiding it
/// inside the token layer; exposing them as duration-taking factories keeps the
/// pairing at the call site, where the rung is the thing actually being chosen.
enum TdayMotion {

    // MARK: - Durations

    /// How long a motion runs. See `MotionTokens.kt` for what each rung is for —
    /// the choice between them is a product decision, not a taste one.
    enum Durations {
        static let quick: TimeInterval = TdayMotionGenerated.Durations.quick
        static let enter: TimeInterval = TdayMotionGenerated.Durations.enter
        static let change: TimeInterval = TdayMotionGenerated.Durations.change
        static let emphasis: TimeInterval = TdayMotionGenerated.Durations.emphasis
        static let scene: TimeInterval = TdayMotionGenerated.Durations.scene
    }

    // MARK: - Delays

    /// How long something waits before it starts — kept apart from the durations
    /// for the reason the source of truth keeps them apart: `emphasis` and
    /// `celebrationLead` are both 320 ms today and are not the same number. One is
    /// how long a motion runs, the other is how long the burst owns the screen,
    /// and a single namespace would let a call site reach for either.
    enum Delays {
        static let placementLead: TimeInterval = TdayMotionGenerated.Delays.placementLead
        static let celebrationLead: TimeInterval = TdayMotionGenerated.Delays.celebrationLead
    }

    // MARK: - Easings

    /// Both ends eased — the unmarked curve.
    ///
    /// Not a drop-in for `.easeInOut`, which SwiftUI defines as (0.42, 0, 0.58, 1):
    /// a materially longer tail. Moving an existing `.easeInOut` site onto this is
    /// a visible change and belongs in a migration PR that says so.
    static func standard(duration: TimeInterval) -> Animation {
        curve(TdayMotionGenerated.Easings.standard, duration: duration)
    }

    /// Decelerate — something arriving, which should settle rather than stop.
    /// SwiftUI's `.easeOut` is (0, 0, 0.58, 1) and is not this curve either.
    static func enter(duration: TimeInterval) -> Animation {
        curve(TdayMotionGenerated.Easings.enter, duration: duration)
    }

    /// Accelerate — something leaving, which should commit rather than drift off.
    /// The one near-match of the three: `.easeIn` is (0.42, 0, 1, 1).
    static func exit(duration: TimeInterval) -> Animation {
        curve(TdayMotionGenerated.Easings.exit, duration: duration)
    }

    /// Material's emphasised decelerate. Pairs with `Durations.scene` on the
    /// empty-state illustration, and with nothing else.
    static func scene(duration: TimeInterval) -> Animation {
        curve(TdayMotionGenerated.Easings.scene, duration: duration)
    }

    // `Easings.gesture` is generated for every client but is deliberately not
    // surfaced here. It is the web's press-and-paging curve, and the source of
    // truth marks it web-only: iOS says that same thing with the Gesture *spring*
    // below. A factory for it would give a view two spellings for one intent, and
    // the tween one reads wrong on a finger that has already let go.

    // MARK: - Springs

    /// Confirmation dialogs, selector overlays, a control committing to a state.
    static let snappy: Animation = .spring(
        response: TdayMotionGenerated.Springs.snappyResponse,
        dampingFraction: TdayMotionGenerated.Springs.snappyDamping
    )

    /// A surface continuing under its own momentum after a finger lets go.
    static let gesture: Animation = .spring(
        response: TdayMotionGenerated.Springs.gestureResponse,
        dampingFraction: TdayMotionGenerated.Springs.gestureDamping
    )

    /// Something heavy coming to rest — a dock, a bar, a sheet finding its height.
    static let settle: Animation = .spring(
        response: TdayMotionGenerated.Springs.settleResponse,
        dampingFraction: TdayMotionGenerated.Springs.settleDamping
    )

    // MARK: - Press scales

    /// How far a surface squashes under a finger, by surface class. `CGFloat`
    /// rather than the generated `Double` because every consumer is
    /// `scaleEffect(_:)`, and Swift will not widen the one into the other for
    /// free — converting here beats converting at each call site.
    enum PressScales {
        static let bar = CGFloat(TdayMotionGenerated.PressScales.bar)
        static let card = CGFloat(TdayMotionGenerated.PressScales.card)
        static let row = CGFloat(TdayMotionGenerated.PressScales.row)
    }

    // MARK: -

    /// The one place the four control points go positional. `Bezier` names them so
    /// that a transposition is a compile error rather than a curve nobody notices;
    /// that guarantee is worth exactly as much as this function's argument order,
    /// so it is written once and read from everywhere.
    private static func curve(
        _ bezier: TdayMotionGenerated.Bezier,
        duration: TimeInterval
    ) -> Animation {
        .timingCurve(bezier.x1, bezier.y1, bezier.x2, bezier.y2, duration: duration)
    }
}
