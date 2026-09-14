import SwiftUI
import XCTest

#if SWIFT_PACKAGE
@testable import TdayCore
#else
@testable import Tday
#endif

/// `TdayMotionResolution` and the environment value that carries it.
///
/// What is testable here is narrow but it is the whole of the decision. The
/// amplitude of any given animation is a device question and belongs in
/// `docs/verification/phase-8-device-pass.md`; what a machine can hold is that
/// refusing an animation yields `nil` rather than a shorter one, that an absent
/// override resolves to the system's own answer rather than to a guess, and that
/// an override — once written — beats that answer in both directions.
///
/// The last of those is the one worth a suite of its own. The accessor composes
/// two sources, and a composition can fail silently in a way a single stored value
/// cannot: read the override and forget the fallback and every surface outside the
/// app root animates through Reduce Motion; read the fallback and forget the
/// override and the root modifier that exists to make the setting live at runtime
/// writes into a value nobody reads.
///
/// Only the second of those two failures is reachable from here.
/// `EnvironmentValues.accessibilityReduceMotion` is declared get-only — which is
/// exactly why `TdayMotionEnvironment.swift` had to mint an override key of its own
/// rather than write the system one — so no test can stage a system answer of
/// "reduce" and watch the fallback carry it. That half is a device row rather than a
/// missing assertion: `docs/verification/phase-8-device-pass.md`'s calendar line
/// flips the real setting on the one surface built from hand-made
/// `UIHostingController` pages, which inherits no override and therefore exercises
/// the fallback and nothing else.
@MainActor
final class TdayMotionEnvironmentTests: XCTestCase {

    // MARK: - The two answers

    /// `nil`, and not a shortened animation. `docs/motion.md`'s fifth idiom rule
    /// turns on this: `withAnimation(nil)` still applies the state change, so the
    /// finished state is drawn in the frame the state changed — whereas any real
    /// `Animation`, however brief, is still a trip the user did not ask for.
    func testReducedResolvesEveryAnimationToNil() {
        XCTAssertNil(TdayMotionResolution.reduced(TdayMotion.settle))
        XCTAssertNil(TdayMotionResolution.reduced(TdayMotion.snappy))
        XCTAssertNil(TdayMotionResolution.reduced(TdayMotion.standard(duration: TdayMotion.Durations.quick)))
    }

    /// The other half, and not a formality: a gate that returned `nil` both ways
    /// would pass the test above and take the app's motion with it.
    func testFullHandsBackTheAnimationItWasGiven() {
        XCTAssertEqual(TdayMotionResolution.full(TdayMotion.settle), TdayMotion.settle)
        XCTAssertEqual(
            TdayMotionResolution.full(TdayMotion.scene(duration: TdayMotion.Durations.scene)),
            TdayMotion.scene(duration: TdayMotion.Durations.scene)
        )
    }

    /// The boolean the `.transition` sites read has to agree with the animation
    /// the rest of the screen reads, or one feed refuses its travel and plays its
    /// row legs anyway.
    func testTheBooleanAgreesWithTheAnimation() {
        XCTAssertTrue(TdayMotionResolution.full.isEnabled)
        XCTAssertFalse(TdayMotionResolution.reduced.isEnabled)
    }

    // MARK: - The environment value

    /// No override and no reduce request: the app animates. Stated as a test
    /// because the alternative was a real candidate — defaulting an unanswered
    /// question to "do not animate" fails safe for the fifth idiom rule — and the
    /// file chose the fallback instead, which only works while the absence of an
    /// override means "ask the system" rather than "assume the worst". A fresh
    /// `EnvironmentValues` reports no reduce request, so this is the fallback being
    /// read, in the one direction a test can put it in.
    func testAnUnresolvedEnvironmentAnimates() {
        XCTAssertTrue(EnvironmentValues().tdayAnimation.isEnabled)
        XCTAssertNotNil(EnvironmentValues().tdayAnimation(TdayMotion.settle))
    }

    /// And an override, once written, is what the accessor gives back — in both
    /// directions, so a getter that dropped the key on one of them is caught.
    /// `tdayResolvedMotion()` writes the same answer the fallback would compute
    /// today; what it adds is the dependency that makes the answer *live*, and it
    /// would be doing nothing at all if the accessor read past it.
    ///
    /// Only `.reduced` actually disagrees with the fallback here, and that is the
    /// direction worth having: an accessor that ignored the override and asked the
    /// system would still answer `true` on this value. `.full` pins the round trip
    /// rather than the precedence, because staging a system answer that disagrees
    /// with it would mean writing a get-only key.
    func testAnOverrideIsWhatTheAccessorGivesBack() {
        var values = EnvironmentValues()

        values.tdayAnimation = .reduced
        XCTAssertFalse(values.tdayAnimation.isEnabled)
        XCTAssertNil(values.tdayAnimation(TdayMotion.settle))

        values.tdayAnimation = .full
        XCTAssertTrue(values.tdayAnimation.isEnabled)
        XCTAssertEqual(values.tdayAnimation(TdayMotion.settle), TdayMotion.settle)
    }
}
