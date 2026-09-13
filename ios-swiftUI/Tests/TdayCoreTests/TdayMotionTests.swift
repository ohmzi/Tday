import SwiftUI
import XCTest

#if SWIFT_PACKAGE
@testable import TdayCore
#else
@testable import Tday
#endif

/// `TdayMotion` — the SwiftUI surface over the generated motion tokens.
///
/// What a test can hold here is narrow, and it is worth being exact about the
/// boundary. The *values* are not this suite's job: they come from
/// `MotionTokens.kt` and are guarded by `./gradlew :shared:verifyMotionTokens`,
/// which fails the build if the generated Swift drifts from the source of truth.
/// Asserting them again here would only assert that the generated file equals
/// itself, and would have to re-type every number to do it — the one thing the
/// token layer exists to stop.
///
/// What is left is everything the hand-written half can get wrong on its own: a
/// literal creeping back into a wrapper, a control point transposed on the way
/// into `timingCurve`, a duration argument that never reaches the animation, or a
/// spring quietly acquiring a `blendDuration` and no longer matching the call
/// sites that read it. Those are this suite.
final class TdayMotionTests: XCTestCase {

    /// Two rungs that are far enough apart that no rounding could make a factory
    /// look duration-sensitive when it is not.
    private let shortest = TdayMotionGenerated.Durations.quick
    private let longest = TdayMotionGenerated.Durations.scene

    // MARK: - Pass-through

    /// The wrappers must be conduits, not copies. A hand-typed `0.32` here would
    /// be invisible to the drift gate, because the gate compares the generated
    /// file with the Kotlin and never looks at this layer.
    func testDurationsAndDelaysAreTheGeneratedValues() {
        XCTAssertEqual(TdayMotion.Durations.quick, TdayMotionGenerated.Durations.quick)
        XCTAssertEqual(TdayMotion.Durations.enter, TdayMotionGenerated.Durations.enter)
        XCTAssertEqual(TdayMotion.Durations.change, TdayMotionGenerated.Durations.change)
        XCTAssertEqual(TdayMotion.Durations.emphasis, TdayMotionGenerated.Durations.emphasis)
        XCTAssertEqual(TdayMotion.Durations.scene, TdayMotionGenerated.Durations.scene)

        XCTAssertEqual(TdayMotion.Delays.placementLead, TdayMotionGenerated.Delays.placementLead)
        XCTAssertEqual(TdayMotion.Delays.celebrationLead, TdayMotionGenerated.Delays.celebrationLead)
    }

    func testPressScalesAreTheGeneratedValues() {
        XCTAssertEqual(TdayMotion.PressScales.bar, CGFloat(TdayMotionGenerated.PressScales.bar))
        XCTAssertEqual(TdayMotion.PressScales.card, CGFloat(TdayMotionGenerated.PressScales.card))
        XCTAssertEqual(TdayMotion.PressScales.row, CGFloat(TdayMotionGenerated.PressScales.row))
    }

    // MARK: - Easings

    /// The duration argument has to reach the curve. `timingCurve` has a default
    /// duration, so a factory that dropped its parameter on the floor would still
    /// compile and still return a perfectly good animation.
    func testEveryEasingFactoryHonoursTheDurationItIsGiven() {
        XCTAssertNotEqual(TdayMotion.standard(duration: shortest), TdayMotion.standard(duration: longest))
        XCTAssertNotEqual(TdayMotion.enter(duration: shortest), TdayMotion.enter(duration: longest))
        XCTAssertNotEqual(TdayMotion.exit(duration: shortest), TdayMotion.exit(duration: longest))
        XCTAssertNotEqual(TdayMotion.scene(duration: shortest), TdayMotion.scene(duration: longest))
    }

    /// Four distinct curves at one duration. Catches the copy-paste that points
    /// two factories at the same `Easings` member — the failure mode the named
    /// `Bezier` fields cannot help with, because both spellings are correct.
    func testTheFourEasingsAreFourDifferentCurves() {
        let curves = [
            TdayMotion.standard(duration: longest),
            TdayMotion.enter(duration: longest),
            TdayMotion.exit(duration: longest),
            TdayMotion.scene(duration: longest),
        ]
        for (index, curve) in curves.enumerated() {
            for other in curves[(index + 1)...] {
                XCTAssertNotEqual(curve, other)
            }
        }
    }

    /// Control points go into `timingCurve` positionally — the one place in this
    /// vocabulary where x and y can swap with no compile error and no wrong-looking
    /// line. Each of these asserts the factory is NOT its own transpose, which is
    /// only true while the order is (x1, y1, x2, y2).
    ///
    /// Worth knowing how much `enter` contributes: it is (0, 0, 0.2, 1), so its
    /// first control point is symmetric and a swap confined to that pair would be
    /// invisible here. The second pair still carries the check, and the other three
    /// curves cover the first — a green on this test is not a green on `enter`'s
    /// leading pair alone.
    func testControlPointsAreNotTransposed() {
        assertNotTransposed(TdayMotionGenerated.Easings.standard, TdayMotion.standard(duration: longest))
        assertNotTransposed(TdayMotionGenerated.Easings.enter, TdayMotion.enter(duration: longest))
        assertNotTransposed(TdayMotionGenerated.Easings.exit, TdayMotion.exit(duration: longest))
        assertNotTransposed(TdayMotionGenerated.Easings.scene, TdayMotion.scene(duration: longest))
    }

    private func assertNotTransposed(
        _ bezier: TdayMotionGenerated.Bezier,
        _ animation: Animation,
        file: StaticString = #filePath,
        line: UInt = #line
    ) {
        XCTAssertNotEqual(
            animation,
            Animation.timingCurve(bezier.y1, bezier.x1, bezier.y2, bezier.x2, duration: longest),
            "control points are reaching timingCurve in (y, x) order",
            file: file,
            line: line
        )
    }

    // MARK: - Springs

    /// The springs must stay `spring(response:dampingFraction:)` with nothing else
    /// attached. This is not pedantry about spelling: `TdaySheetChrome`'s `cardIn`
    /// reads `TdayMotion.settle` on the promise that it is byte-identical to the
    /// literal spring it replaced, and a `blendDuration` appearing here would break
    /// that promise silently, in a file that does not mention springs.
    func testSpringsCarryNothingBeyondResponseAndDamping() {
        XCTAssertEqual(
            TdayMotion.snappy,
            Animation.spring(
                response: TdayMotionGenerated.Springs.snappyResponse,
                dampingFraction: TdayMotionGenerated.Springs.snappyDamping
            )
        )
        XCTAssertEqual(
            TdayMotion.gesture,
            Animation.spring(
                response: TdayMotionGenerated.Springs.gestureResponse,
                dampingFraction: TdayMotionGenerated.Springs.gestureDamping
            )
        )
        XCTAssertEqual(
            TdayMotion.settle,
            Animation.spring(
                response: TdayMotionGenerated.Springs.settleResponse,
                dampingFraction: TdayMotionGenerated.Springs.settleDamping
            )
        )
    }

    func testTheThreeSpringsAreThreeDifferentSprings() {
        XCTAssertNotEqual(TdayMotion.snappy, TdayMotion.gesture)
        XCTAssertNotEqual(TdayMotion.gesture, TdayMotion.settle)
        XCTAssertNotEqual(TdayMotion.snappy, TdayMotion.settle)
    }

    // MARK: - Re-exports

    /// The confetti's scene lead is the one island constant this target can see —
    /// the rest are `private` to their files. It is here because the re-export is
    /// load-bearing in a way the others are not: `TdayEmptyState` delays the
    /// scene's arrival by reading this number out of the confetti's metrics, so
    /// the burst and the scene behind it stay one sequence.
    func testTheConfettiSceneLeadIsTheCelebrationLeadToken() {
        XCTAssertEqual(TdayConfettiMetrics.sceneLead, TdayMotionGenerated.Delays.celebrationLead)
    }
}
