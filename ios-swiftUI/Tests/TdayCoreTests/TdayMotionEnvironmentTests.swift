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
    /// override means "ask the system" rather than "assume the worst".
    func testAnUnresolvedEnvironmentAnimates() {
        XCTAssertTrue(EnvironmentValues().tdayAnimation.isEnabled)
    }

    /// The fallback is the part that covers every surface the app root cannot
    /// reach — a hand-built `UIHostingController` inherits none of the app's own
    /// environment and keeps resolving the system keys from its traits, so this is
    /// what makes those surfaces correct rather than merely unbroken.
    func testWithoutAnOverrideTheSystemSettingDecides() {
        var values = EnvironmentValues()
        values.accessibilityReduceMotion = true
        XCTAssertFalse(values.tdayAnimation.isEnabled)
        XCTAssertNil(values.tdayAnimation(TdayMotion.settle))
    }

    /// And the override wins once it is written — in the direction that matters,
    /// which is over a system answer that disagrees. `tdayResolvedMotion()` writes
    /// the same answer the fallback would compute today; it is what makes the
    /// answer live, and it would be doing nothing at all if the fallback shadowed
    /// it.
    func testAnOverrideBeatsTheSystemSettingBothWays() {
        var values = EnvironmentValues()
        values.accessibilityReduceMotion = true
        values.tdayAnimation = .full
        XCTAssertTrue(values.tdayAnimation.isEnabled)

        values.accessibilityReduceMotion = false
        values.tdayAnimation = .reduced
        XCTAssertFalse(values.tdayAnimation.isEnabled)
    }
}
