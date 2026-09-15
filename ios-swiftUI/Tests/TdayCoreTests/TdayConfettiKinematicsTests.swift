import Foundation
import XCTest

#if SWIFT_PACKAGE
@testable import TdayCore
#else
@testable import Tday
#endif

/// `TdayConfettiKinematics` — the burst's physics, checked without a screen.
///
/// The reason this file exists at all is that the confetti is the one piece of
/// motion in the app that nothing else can observe: there is no golden image, no
/// screenshot harness, and `xctest` has no opinion about whether a cloud of
/// paper reads as paper. What it CAN decide is whether the model underneath is
/// the one that was designed — and every property the design turns on happens to
/// be a statement about a pure function.
///
/// So the assertions below are the design, restated: a piece leaves from a
/// thumb-sized patch rather than from a point; the throw decelerates to a finite
/// reach instead of travelling forever in a straight line; the fall settles at a
/// terminal speed instead of accelerating off the bottom of the screen; nothing
/// leaves the box; the fade ends at exactly zero with no slope; and the edge-on
/// flip is not the in-plane turn wearing a different name. Each of those was
/// false in the version this replaces, and each would still compile and still
/// look plausible if it were reintroduced.
///
/// The same five checks, at the same tolerances, are in the Android and web test
/// files. Tolerances here are `Double`'s: sweeps at 1/100 or 1/200 of a flight,
/// `1e-9` on values. Monotonicity and concavity use a strict `<` with no
/// epsilon — the tail differences these sweeps produce are far above the noise
/// floor, and an epsilon would make the check pass for a model that had stopped
/// moving.
final class TdayConfettiKinematicsTests: XCTestCase {

    private let tol = 1e-9

    /// A piece with everything switched off but the one attribute under test.
    /// Hand-built rather than pulled out of `fan()`: an invariant proved on one
    /// seed's pieces is a fact about that seed, and the point of these checks is
    /// that they hold for every piece the ranges can produce.
    private func makePiece(
        angle: Double = 0,
        speed: Double = 1.95,
        spin: Double = 0,
        spinPhase: Double = 0,
        flipRate: Double = 0,
        flipPhase: Double = 0,
        delay: Double = 0,
        dragScale: Double = 1,
        muzzle: Double = 0,
        swayAmp: Double = 0,
        swayRate: Double = 0,
        swayPhase: Double = 0
    ) -> ConfettiPiece {
        ConfettiPiece(
            angle: angle,
            speed: speed,
            spin: spin,
            spinPhase: spinPhase,
            flipRate: flipRate,
            flipPhase: flipPhase,
            width: 7,
            height: 10,
            colorIndex: 0,
            delay: delay,
            dragScale: dragScale,
            muzzle: muzzle,
            swayAmp: swayAmp,
            swayRate: swayRate,
            swayPhase: swayPhase
        )
    }

    // MARK: - I1

    /// Nothing is in the air before its own launch, and what launches leaves from
    /// the muzzle patch.
    ///
    /// The staggered launch is the difference between confetti and a single
    /// expanding ring, so a piece drawn during its own delay is not a rounding
    /// error — it is the choreography collapsing back into one salvo. The second
    /// half is the new part: the burst starts from a patch a thumb could cover,
    /// which is what stops forty-six pieces from appearing to come out of the
    /// same pixel.
    func testNothingIsInTheAirBeforeItsOwnLaunch() throws {
        for (index, piece) in ConfettiPiece.fan().enumerated() {
            for t in [0, piece.delay / 2, piece.delay] {
                XCTAssertNil(
                    TdayConfettiKinematics.frame(piece, at: t),
                    "piece \(index) is being drawn at \(t), before its own launch at \(piece.delay)"
                )
            }
            XCTAssertNil(
                TdayConfettiKinematics.frame(piece, at: 1),
                "piece \(index) is still being drawn at the end of the flight"
            )

            let justLaunched = piece.delay + 1e-4 * (1 - piece.delay)
            let frame = try XCTUnwrap(
                TdayConfettiKinematics.frame(piece, at: justLaunched),
                "piece \(index) is not in the air just after its own launch"
            )
            // Within the patch — `muzzle` is at most 0.03 span, and a ten
            // thousandth of a flight of travel is worth another 2e-4 of it.
            XCTAssertLessThanOrEqual(hypot(frame.dx, frame.dy), 0.031)
            // And at the patch: a piece's first frame is its muzzle point, not
            // the origin, which is the whole reason the offset is cached.
            XCTAssertLessThanOrEqual(hypot(frame.dx - piece.mx, frame.dy - piece.my), 1e-3)
        }
    }

    // MARK: - I2

    /// Outward travel decelerates and its reach is finite.
    ///
    /// This is the single change the whole model is for. The old burst multiplied
    /// a distance by elapsed time, so the fastest third of the fan left the sides
    /// of the box about two-thirds of the way through the flight and the eye read
    /// it as a diagram of a starburst. Under drag the same fan covers most of its
    /// reach in the first third of a second and then stops growing, which is the
    /// snap the design describes.
    func testOutwardTravelIsConcaveAndItsReachIsFinite() throws {
        let flat = makePiece(angle: 0, speed: 1.95, dragScale: 1)

        var travelled: [Double] = []
        for i in 1...99 {
            let frame = try XCTUnwrap(TdayConfettiKinematics.frame(flat, at: Double(i) / 100))
            travelled.append(frame.dx)
        }

        for i in 1..<travelled.count {
            XCTAssertLessThan(
                travelled[i - 1], travelled[i],
                "outward travel stopped increasing at sample \(i) — a piece that has come to rest"
            )
        }
        let steps = (1..<travelled.count).map { travelled[$0] - travelled[$0 - 1] }
        for i in 1..<steps.count {
            XCTAssertLessThan(
                steps[i], steps[i - 1],
                "outward travel stopped decelerating at sample \(i) — this is the linear throw again"
            )
        }

        // One time constant in: 1.95 · (1 − e⁻¹) / 6. Pinned to the seven digits
        // the spec quotes, which is what sets the accuracy here rather than the
        // file's own 1e-9 — the pin is the shared number, not a tighter claim
        // about this arithmetic.
        let atOneTimeConstant = try XCTUnwrap(TdayConfettiKinematics.frame(flat, at: 1.0 / 6.0))
        XCTAssertEqual(atOneTimeConstant.dx, 0.2054392, accuracy: 1e-7)

        // And the reach, which the fastest piece in the fan is still short of at
        // the last frame it is drawn on: speed / k = 0.325.
        let atTheEnd = try XCTUnwrap(TdayConfettiKinematics.frame(flat, at: 0.999))
        XCTAssertGreaterThanOrEqual(atTheEnd.dx, 0.32175)
        XCTAssertLessThanOrEqual(atTheEnd.dx, 0.325)
    }

    // MARK: - I3

    /// The fall approaches a terminal speed from both sides and never crosses it.
    ///
    /// Approached from BOTH sides is the part worth the setup. A piece thrown
    /// upward has to arrive at the terminal speed from below and a piece thrown
    /// downward from above, and neither may overshoot: an overshoot means the
    /// drag term has the wrong sign for one of them, which is the mistake that
    /// looks correct in every frame where the fan happens to throw upward.
    func testTheFallApproachesTerminalSpeedFromBothSidesAndNeverCrossesIt() throws {
        // y grows downward, so −π/2 is straight up and +π/2 straight down.
        let thrownUp = makePiece(angle: -Double.pi / 2, speed: 1.95, dragScale: 1)
        let thrownDown = makePiece(angle: Double.pi / 2, speed: 1.95, dragScale: 1)

        // gravity / drag, at dragScale 1. The constant the eye reads; `gravity`
        // on its own is not comparable with anything.
        XCTAssertEqual(thrownUp.vt, 0.85, accuracy: tol)
        XCTAssertEqual(thrownDown.vt, 0.85, accuracy: tol)
        let terminal = thrownUp.vt

        let rising = try verticalSpeeds(of: thrownUp)
        XCTAssertLessThan(rising[0], 0, "a piece thrown upward is not moving upward on its first frame")
        for i in 1..<rising.count {
            XCTAssertLessThan(rising[i - 1], rising[i], "the rise stopped slowing at sample \(i)")
        }
        for speed in rising {
            XCTAssertLessThanOrEqual(speed, terminal + tol, "the fall overshot its terminal speed")
        }
        XCTAssertGreaterThanOrEqual(
            rising[rising.count - 1], 0.985 * terminal,
            "the fall had not reached its terminal speed by the end of the flight"
        )

        let falling = try verticalSpeeds(of: thrownDown)
        XCTAssertGreaterThan(falling[0], terminal, "a piece thrown downward does not start above terminal")
        for i in 1..<falling.count {
            XCTAssertLessThan(falling[i], falling[i - 1], "the fall stopped slowing at sample \(i)")
        }
        for speed in falling {
            XCTAssertGreaterThanOrEqual(speed, terminal - tol, "the fall undershot its terminal speed")
        }
        XCTAssertLessThanOrEqual(
            falling[falling.count - 1], 1.015 * terminal,
            "the fall had not settled to its terminal speed by the end of the flight"
        )
    }

    /// Mean vertical speed over each hundredth of the flight. Differences rather
    /// than an analytic derivative on purpose: a test that rewrites the model's
    /// own formula checks the formula against itself.
    private func verticalSpeeds(of piece: ConfettiPiece) throws -> [Double] {
        var fallen: [Double] = []
        for i in 1...99 {
            fallen.append(try XCTUnwrap(TdayConfettiKinematics.frame(piece, at: Double(i) / 100)).dy)
        }
        return (1..<fallen.count).map { (fallen[$0] - fallen[$0 - 1]) * 100 }
    }

    // MARK: - I4

    /// The fade is opaque, then a smoothstep, and it ends at nothing.
    ///
    /// Asserted either side of `fadeStart` and never on it: `tau` is
    /// `(t − delay) / (1 − delay)`, so which side of 0.60 a given piece lands on
    /// at a given frame is a rounding accident. What is not an accident is that
    /// the burst is fully opaque for the first 1.2 s and reaches exactly zero
    /// with zero slope — a linear tail ends on a slope, and the eye reads that as
    /// the pieces being switched off rather than as paper leaving.
    func testTheFadeIsOpaqueThenSmoothAndEndsAtNothing() {
        XCTAssertGreaterThanOrEqual(TdayConfettiKinematics.alpha(0.599), 1 - 1e-6)
        XCTAssertLessThan(TdayConfettiKinematics.alpha(0.601), 1)
        // smoothstep(0.5) = 0.5: half gone at 1.6 s, which is the midpoint the
        // three clients are read against.
        XCTAssertEqual(TdayConfettiKinematics.alpha(0.80), 0.5, accuracy: tol)
        XCTAssertEqual(TdayConfettiKinematics.alpha(1.0), 0, accuracy: tol)
        // Everything above this line is also true of the linear tail the
        // smoothstep replaces: both are 1 below FadeStart, both cross 0.5 at the
        // midpoint, both reach 0 at the end, both only fall. What separates them
        // is the approach — a hundredth of a flight from the end the smoothstep
        // is at 0.0018 and the linear ramp is still at 0.025 — so this is the
        // assertion that says "smoothstep" and not just "a fade".
        XCTAssertLessThan(TdayConfettiKinematics.alpha(0.99), 0.005)

        var previous = Double.infinity
        for i in 0...200 {
            // i/200 rather than i·0.005, so the last sample is exactly 1.0 and
            // the check at the end is the check that was intended.
            let alpha = TdayConfettiKinematics.alpha(Double(i) / 200)
            XCTAssertLessThanOrEqual(alpha, previous, "the fade came back up at sample \(i)")
            XCTAssertGreaterThanOrEqual(alpha, 0)
            XCTAssertLessThanOrEqual(alpha, 1)
            previous = alpha
        }
    }

    // MARK: - Interruption

    /// The cancel envelope, which is the one term here that is not physics.
    ///
    /// A burst nobody cancels multiplies by 1 for its whole flight, so the three
    /// claims worth pinning are that an uncancelled burst is byte for byte the
    /// fade above it, that a spent envelope paints nothing, and that the way
    /// between the two only ever goes one way — paint that brightened on its way
    /// out would read as the burst flinching rather than bowing.
    ///
    /// The envelope is a NEW term and never a retune: nothing in this test
    /// touches `flightSeconds`, `fadeStart` or the scene lead, and every
    /// assertion above this line is unchanged.
    func testAnUncancelledBurstDrawsExactlyTheFadeItAlwaysDid() {
        for i in 0...50 {
            let tau = Double(i) / 50
            XCTAssertEqual(
                TdayConfettiKinematics.envelopedAlpha(TdayConfettiKinematics.alpha(tau), envelope: 1),
                TdayConfettiKinematics.alpha(tau),
                accuracy: 0
            )
        }
    }

    func testASpentEnvelopePaintsNothingWhateverThePiecesOwnFadeSays() {
        // Including at the start of the flight, where `alpha` is a flat 1: this
        // is what lets the view leave the tree when the envelope lands, instead
        // of being torn out from over pieces that are still fully opaque.
        XCTAssertEqual(
            TdayConfettiKinematics.envelopedAlpha(TdayConfettiKinematics.alpha(0.1), envelope: 0),
            0,
            accuracy: 0
        )
        XCTAssertEqual(TdayConfettiKinematics.envelopedAlpha(1, envelope: 0), 0, accuracy: 0)
    }

    func testTheEnvelopeOnlyEverTakesPaintAway() {
        // Monotonic in the envelope at a fixed point in the flight, and never
        // brighter than the flight's own fade — a second term over the first,
        // not a replacement for it.
        let pieceAlpha = TdayConfettiKinematics.alpha(0.7)
        var previous = -Double.infinity
        for i in 0...50 {
            let painted = TdayConfettiKinematics.envelopedAlpha(pieceAlpha, envelope: Double(i) / 50)
            XCTAssertGreaterThanOrEqual(painted, previous, "the envelope brightened at sample \(i)")
            XCTAssertLessThanOrEqual(painted, pieceAlpha)
            previous = painted
        }
    }

    /// The envelope's own clock: full at the cancel frame, nothing by `Quick`,
    /// and only ever falling in between.
    ///
    /// It is read off a wall clock rather than animated, because the burst is
    /// drawn into a `Canvas` with no animatable property for SwiftUI to
    /// interpolate — so the bounds matter here in a way `alpha`'s do not. A tick
    /// can land on either side of the window's edge, and both ends are clamped
    /// for that reason rather than defensively.
    func testTheCancelEnvelopeRunsOneToZeroOverQuickAndStaysThere() {
        XCTAssertEqual(TdayConfettiKinematics.cancelEnvelope(elapsed: 0), 1, accuracy: tol)
        XCTAssertEqual(TdayConfettiKinematics.cancelEnvelope(elapsed: -1), 1, accuracy: tol)
        XCTAssertEqual(
            TdayConfettiKinematics.cancelEnvelope(elapsed: TdayMotion.Durations.quick),
            0,
            accuracy: tol
        )
        // A burst still on screen after the envelope has run is a burst nothing
        // takes back out, so the far side of the window has to stay at zero
        // rather than wrapping or going negative.
        XCTAssertEqual(
            TdayConfettiKinematics.cancelEnvelope(elapsed: TdayConfettiMetrics.flightSeconds),
            0,
            accuracy: tol
        )

        var previous = Double.infinity
        for i in 0...100 {
            let envelope = TdayConfettiKinematics.cancelEnvelope(
                elapsed: TdayMotion.Durations.quick * Double(i) / 100
            )
            XCTAssertLessThanOrEqual(envelope, previous, "the envelope came back up at sample \(i)")
            XCTAssertGreaterThanOrEqual(envelope, 0)
            XCTAssertLessThanOrEqual(envelope, 1)
            previous = envelope
        }
    }

    /// The rung is `Quick`, named rather than typed.
    ///
    /// `docs/motion.md` calls Quick "something leaving that nobody is meant to
    /// watch go", which is paint at the shortest rung and exactly what this is.
    /// Asserted because the alternative — a digit at the call site — is invisible
    /// to review and visible to `motion-parity.test.ts`'s `ios.easeDuration`
    /// counter, which has no headroom.
    func testTheEnvelopeIsOnTheQuickRung() {
        XCTAssertEqual(TdayMotion.Durations.quick, 0.15, accuracy: tol)
        // Half the rung is not half the envelope: `Exit` accelerates, so the
        // paint is still mostly there at the midpoint and leaves in a hurry at
        // the end. A linear ramp here would read as the burst being switched off
        // in even steps rather than committing to going.
        XCTAssertGreaterThan(
            TdayConfettiKinematics.cancelEnvelope(elapsed: TdayMotion.Durations.quick / 2),
            0.5
        )
    }

    // MARK: - I5

    /// Every piece stays in the box, and the fan is choreography rather than
    /// chance.
    ///
    /// The bounds below are derived from the PARAMETER RANGES, never from what
    /// this seed happens to do — a bound fitted to one fan passes for every model
    /// that produces that fan and says nothing about the next one.
    ///
    /// Sideways: `|mx| + |vx0| · D∞ + swayAmp`, worst case
    /// 0.03 + (1.95 · 0.9397) / 5.4 + 0.04 = 0.406, where D∞ = 1/k and k is
    /// smallest at dragScale 0.90. Down: `vt · (1 − D∞) + vy0 · D∞ + my` with the
    /// shallowest throw, 0.944 · 0.816 − 0.376 · 0.184 = 0.70. Up: the apex of
    /// the steepest throw, −0.165 span, plus a muzzle of −0.03 = −0.195. Rounded
    /// out to ±0.44 and −0.22…0.72, which leaves margin for the ranges to be
    /// retuned without rewriting the test, and is still far inside the box.
    func testEveryPieceStaysInTheBoxAndTheFanIsChoreography() {
        let fan = ConfettiPiece.fan()

        XCTAssertEqual(fan.count, 46)
        // The seed is the choreography: a fan that differs between two calls is a
        // burst nobody designed, however good it looks once.
        XCTAssertEqual(fan, ConfettiPiece.fan())

        var previousAngle = -Double.infinity
        for (index, piece) in fan.enumerated() {
            XCTAssertGreaterThanOrEqual(piece.angle, TdayConfettiMetrics.fanStart)
            XCTAssertLessThan(piece.angle, TdayConfettiMetrics.fanStart + TdayConfettiMetrics.fanSweep)
            // Stratified, so the fan sweeps once rather than clustering: a
            // shuffled fan is the same set of pieces and a different effect.
            XCTAssertGreaterThanOrEqual(piece.angle, previousAngle, "the fan doubled back at piece \(index)")
            previousAngle = piece.angle

            XCTAssertGreaterThanOrEqual(piece.speed, 1.10)
            XCTAssertLessThanOrEqual(piece.speed, 1.95)
            XCTAssertGreaterThanOrEqual(abs(piece.spin), 1.5)
            XCTAssertLessThanOrEqual(abs(piece.spin), 4.5)
            XCTAssertGreaterThanOrEqual(piece.dragScale, 0.90)
            XCTAssertLessThanOrEqual(piece.dragScale, 1.10)
            XCTAssertGreaterThanOrEqual(piece.delay, 0)
            XCTAssertLessThan(piece.delay, 0.16)
        }

        for (index, piece) in fan.enumerated() {
            for i in 0...200 {
                let t = Double(i) / 200
                guard let frame = TdayConfettiKinematics.frame(piece, at: t) else { continue }
                let site = "piece \(index) at t \(t)"
                XCTAssertGreaterThanOrEqual(frame.dx, -0.44, "\(site) left the box to the left")
                XCTAssertLessThanOrEqual(frame.dx, 0.44, "\(site) left the box to the right")
                XCTAssertGreaterThanOrEqual(frame.dy, -0.22, "\(site) flew off the top")
                XCTAssertLessThanOrEqual(frame.dy, 0.72, "\(site) fell out of the bottom")
                XCTAssertGreaterThanOrEqual(frame.widthScale, TdayConfettiMetrics.minFlip)
                XCTAssertLessThanOrEqual(frame.widthScale, 1)
                XCTAssertGreaterThanOrEqual(frame.alpha, 0)
                XCTAssertLessThanOrEqual(frame.alpha, 1)
            }
        }
    }

    // MARK: - I6

    /// The edge-on flip really does run on an axis of its own.
    ///
    /// This is the defect the old burst had that nobody could name: `widthScale`
    /// was `|cos(spin)|`, so a piece turned edge-on exactly twice per half
    /// revolution of its in-plane turn, forever, in lockstep. Held at flipRate 0
    /// and quarter phase, the piece below must stay pinned edge-on for the whole
    /// flight while its rotation goes on turning — which it cannot do if the two
    /// are one angle.
    func testTheFlipRunsOnItsOwnAxis() throws {
        let pinnedEdgeOn = makePiece(spin: 4.5, flipRate: 0, flipPhase: Double.pi / 2)

        for i in 1...99 {
            let frame = try XCTUnwrap(TdayConfettiKinematics.frame(pinnedEdgeOn, at: Double(i) / 100))
            XCTAssertEqual(frame.widthScale, TdayConfettiMetrics.minFlip, accuracy: tol)
        }

        let early = try XCTUnwrap(TdayConfettiKinematics.frame(pinnedEdgeOn, at: 0.1)).rot
        let late = try XCTUnwrap(TdayConfettiKinematics.frame(pinnedEdgeOn, at: 0.5)).rot
        XCTAssertGreaterThan(late - early, 1.5, "the turn is following the flip instead of its own spin")
    }
}
