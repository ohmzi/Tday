import SwiftUI

/// iOS's reading of `TdayFeedItemMotion` — the one set of specs a task feed
/// moves by.
///
/// The Android object
/// (`android-compose/app/src/main/java/com/ohmz/tday/compose/core/ui/TdayFeedItemMotion.kt`)
/// is the original and carries the argument in full: a feed's rows and everything
/// a row's departure displaces have to travel on the same clock, or the screen
/// comes apart at the moment it should feel finished. Web restates it in
/// `src/lib/feedItemMotion.ts`. This is the third reading, and the one with the
/// most to correct — iOS timed the timeline's three events against two clocks
/// that had nothing to say to each other: a `.easeInOut(duration: 0.22)` travel on
/// the List, and the drag placeholder's 0.28s spring, which both legs of the row
/// transition were pinned to for no reason beyond proximity. The spring outlasted
/// the travel, so a row that had already gone was still fading while its
/// neighbours had finished taking its space. The Completed screen had the
/// asymmetry in the right shape already, in three lengths no other feed shared
/// (0.24 travel, 0.16 in, 0.1 out).
///
/// Both screens change curve family in joining this, which is a visible change and
/// is meant: the timeline's legs were a spring and Completed's were `.easeOut`,
/// and `TdayMotion.standard` is not SwiftUI's `.easeInOut` either — see that
/// method's own note. Rows that used to bounce very slightly on arrival no longer
/// do; the feed is one curve now.
///
/// SwiftUI splits those three events across two unrelated mechanisms, which is why
/// this is a triple and not one value:
///
/// - The travel has no transition of its own. It runs on whatever transaction is
///   open when the feed's identity changes, so [placement] belongs on the List's
///   `.animation(_:value:)` and governs every row that merely moves — and every
///   header, tile and divider moved by one.
/// - The arrival and the departure are legs of a `.transition`, and a leg that
///   names no animation silently inherits that same transaction. [row] pins both,
///   which is the only way the three can differ from each other at all.
///
/// One curve for all three, deliberately, and the same call web's module argues
/// for: an arrival, a travel and a departure on one easing read as one feed
/// behaving consistently, where three curves read as three animations that happen
/// to share a list. Only the lengths differ.
enum TdayFeedItemMotion {

    /// The rung each event sits on, kept nameable rather than inlined.
    ///
    /// `Animation` is opaque — nothing can ask one how long it runs — so the
    /// relationships that make this set correct (an exit never outlasts the enter
    /// it undoes; a departing row stops being drawn before the gap it left has
    /// closed) are only assertable if the lengths have names. See
    /// `TdayFeedItemMotionTests`, which is the iOS half of Android's
    /// `TdayFeedItemMotionTest`.
    enum Durations {
        /// Enter — one element arriving, which is what a new row is and nothing
        /// more. Android's own constant is 190; `docs/motion.md` settled that
        /// difference in favour of the rung, and it is the Android literal that
        /// owes the move.
        static let arrival: TimeInterval = TdayMotion.Durations.enter

        /// Emphasis — the boundary with Change is geometry, not importance, and a
        /// slot is a position.
        static let placement: TimeInterval = TdayMotion.Durations.emphasis

        /// Quick — something leaving that nobody is meant to watch go. Shorter
        /// than [arrival] because an absence should not linger.
        static let departure: TimeInterval = TdayMotion.Durations.quick
    }

    /// An item arriving.
    static let arrival: Animation = TdayMotion.standard(duration: Durations.arrival)

    /// An item taking a new slot — and everything moved by that.
    static let placement: Animation = TdayMotion.standard(duration: Durations.placement)

    /// An item leaving.
    static let departure: Animation = TdayMotion.standard(duration: Durations.departure)

    /// The `.transition` a feed row, and anything a feed adds and removes
    /// alongside its rows, is given.
    ///
    /// Built rather than stored because the two legs have to be pinned
    /// separately: an `AnyTransition` with no `.animation` of its own takes the
    /// enclosing transaction, which here is [placement], and that is precisely the
    /// conflation this type exists to undo.
    ///
    /// `reduceMotion` collapses the whole thing to `.identity`, which is not an
    /// absence of motion so much as the finished state drawn directly: the row is
    /// there, or it is not. The travel is refused separately, by handing `nil` to
    /// the List's `.animation(_:value:)` — a transition cannot turn off a
    /// transaction it did not open, and an accessibility setting that silenced
    /// only the fades would leave the feed sliding.
    static func row(reduceMotion: Bool) -> AnyTransition {
        guard !reduceMotion else {
            return .identity
        }
        let leg = AnyTransition.opacity.combined(with: .move(edge: .top))
        return .asymmetric(
            insertion: leg.animation(arrival),
            removal: leg.animation(departure)
        )
    }
}
