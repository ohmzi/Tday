import SwiftUI
import XCTest

#if SWIFT_PACKAGE
@testable import TdayCore
#else
@testable import Tday
#endif

/// `TdayFeedItemMotion` — the three-way split a task feed's rows move by.
///
/// The same boundary `TdayMotionTests` draws applies here: the rung *values* are
/// `MotionTokens.kt`'s and are guarded by `:shared:verifyMotionTokens`, so
/// re-typing 0.2 or 0.32 in this file would assert the generated Swift equals
/// itself. What is left is the part that is only ever correct relative to itself —
/// three events that must stay three, in a fixed order, on one curve — and that
/// part is the whole of why this type exists, since iOS shipped for a long time
/// with the three collapsed into one.
final class TdayFeedItemMotionTests: XCTestCase {

    /// The rule `docs/motion.md` states first, and the reason Android's object
    /// spells its departure out as shorter rather than reusing its arrival: a row
    /// that is gone has to stop being drawn before the gap it left finishes
    /// closing, or its ghost is still fading over neighbours that have already
    /// taken the space.
    func testAnItemLeavesFasterThanItArrives() {
        XCTAssertLessThan(TdayFeedItemMotion.Durations.departure, TdayFeedItemMotion.Durations.arrival)
        XCTAssertLessThan(TdayFeedItemMotion.Durations.departure, TdayFeedItemMotion.Durations.placement)
    }

    /// The travel outlasts the arrival, which is what lets a feed absorb a row
    /// appearing in the middle of it: the new row has finished fading in by the
    /// time everything below has finished sliding down, rather than the other way
    /// round, which reads as a gap opening under something already solid.
    func testTheTravelOutlastsTheArrival() {
        XCTAssertGreaterThan(TdayFeedItemMotion.Durations.placement, TdayFeedItemMotion.Durations.arrival)
    }

    /// The split is the whole point: three events, three lengths. Two of these
    /// pointing at one rung is the nearest form of the regression this type was
    /// written to undo — iOS had the travel and the two legs on two clocks that
    /// never agreed — and it is the one form of it that still compiles and still
    /// looks deliberate.
    func testTheThreeEventsAreThreeDifferentAnimations() {
        let animations = [
            TdayFeedItemMotion.arrival,
            TdayFeedItemMotion.placement,
            TdayFeedItemMotion.departure,
        ]
        for (index, animation) in animations.enumerated() {
            for other in animations[(index + 1)...] {
                XCTAssertNotEqual(animation, other)
            }
        }
    }

    /// One curve for all three, which is the decision web's `feedItemMotion.ts`
    /// argues for in the same words: an arrival, a travel and a departure timed on
    /// the same easing read as one feed behaving consistently, where three curves
    /// read as three animations that happen to share a list. Nothing else would
    /// catch a departure quietly moved onto `TdayMotion.exit` — it is the obvious
    /// thing to reach for, it is not wrong on its face, and it would leave three
    /// declarations that still look deliberate and still differ from each other.
    func testAllThreeEventsRunOnTheStandardCurve() {
        XCTAssertEqual(
            TdayFeedItemMotion.arrival,
            TdayMotion.standard(duration: TdayFeedItemMotion.Durations.arrival)
        )
        XCTAssertEqual(
            TdayFeedItemMotion.placement,
            TdayMotion.standard(duration: TdayFeedItemMotion.Durations.placement)
        )
        XCTAssertEqual(
            TdayFeedItemMotion.departure,
            TdayMotion.standard(duration: TdayFeedItemMotion.Durations.departure)
        )
    }

    // `row(reduceMotion:)` is deliberately not pinned here. `AnyTransition` is
    // opaque with no equality and no description worth reading, so the only
    // assertions available are on the shape of its own source text — a test that
    // passes for reasons unrelated to whether the feed actually stops moving.
    // What can be checked is checked in `motion-reachability-ios.test.ts`, which
    // reads the call sites rather than the value.
}
