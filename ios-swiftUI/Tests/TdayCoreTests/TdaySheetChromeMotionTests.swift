import SwiftUI
import UIKit
import XCTest

#if SWIFT_PACKAGE
@testable import TdayCore
#else
@testable import Tday
#endif

/// The centred selector overlay's one presentation spec.
///
/// There is not much a compiler-side test can say about a curve, and nothing at
/// all it can say about how one reads. What it can hold is the shape of the
/// spec: that the entrance and the exit are different curves, and that the
/// direction argument picks the one it says it picks. Both matter because the
/// alternative this replaced — a single `.animation(_:value:)` on the sheet
/// body — cannot express an asymmetric spec at all, so a later "simplification"
/// back to one would quietly halve it. Whether the overlay reads as one move
/// with the sheet settling under it is a TestFlight eye-check
/// (`docs/verification/phase-5-device-pass.md`), not this.
final class TdayCenteredSelectorMotionTests: XCTestCase {
    func testTheEntranceAndTheExitAreDifferentCurves() {
        XCTAssertNotEqual(
            TdayCenteredSelectorMotion.presentation,
            TdayCenteredSelectorMotion.dismissal
        )
    }

    func testTheDirectionArgumentPicksTheMatchingCurve() {
        XCTAssertEqual(
            TdayCenteredSelectorMotion.animation(presenting: true),
            TdayCenteredSelectorMotion.presentation
        )
        XCTAssertEqual(
            TdayCenteredSelectorMotion.animation(presenting: false),
            TdayCenteredSelectorMotion.dismissal
        )
    }
}

/// `TdayKeyboardFrameProbe` — the sheet's answer to "is the keyboard up, and
/// where".
///
/// Split into a pure geometry rule and the scene lookup that feeds it precisely
/// so it can be asserted without a view. One caveat worth stating: the failure
/// that motivated the split cannot be staged here. It needs a window on a screen
/// that is not the device's built-in one — an external display, CarPlay, a
/// second window under Stage Manager — and a single-screen simulator has no such
/// thing, so `UIScreen.main` and the scene's own screen agree on this runner and
/// would agree for both the old implementation and the new one. What these hold
/// is the contract: the bottom edge is taken from the scene the app is actually
/// showing on, and the hidden/visible decision is made against that edge alone.
final class TdayKeyboardFrameProbeTests: XCTestCase {
    /// An iPhone 15 Pro's height in points; any bottom edge would do.
    private let screenMaxY: CGFloat = 852
    private let keyboard = CGRect(x: 0, y: 516, width: 393, height: 336)

    func testAKeyboardOverlappingTheScreenIsTheFrameToInsetFor() {
        XCTAssertEqual(
            TdayKeyboardFrameProbe.visibleFrame(endFrame: keyboard, screenMaxY: screenMaxY),
            keyboard
        )
    }

    func testAKeyboardParkedAtTheBottomEdgeIsGone() {
        // `keyboardWillHide` reports the frame the keyboard is travelling to,
        // whose top edge is exactly the screen's bottom edge. Off by one point
        // in the wrong direction and the sheet stays insetted for a keyboard
        // that is no longer there.
        let parked = CGRect(x: 0, y: screenMaxY, width: 393, height: 336)
        XCTAssertNil(TdayKeyboardFrameProbe.visibleFrame(endFrame: parked, screenMaxY: screenMaxY))
    }

    func testAKeyboardBelowTheBottomEdgeIsGone() {
        let below = CGRect(x: 0, y: screenMaxY + 40, width: 393, height: 336)
        XCTAssertNil(TdayKeyboardFrameProbe.visibleFrame(endFrame: below, screenMaxY: screenMaxY))
    }

    func testNoSceneMeansNoInset() {
        // No window scene answered, so nothing can be measured against the
        // reported frame — and there is no keyboard to inset for either.
        XCTAssertNil(TdayKeyboardFrameProbe.visibleFrame(endFrame: keyboard, screenMaxY: nil))
    }

    @MainActor
    func testTheBottomEdgeComesFromTheSceneTheAppIsShowingOn() throws {
        let scenes = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
        try XCTSkipIf(
            scenes.isEmpty,
            "No window scene is attached to this test host, so there is no scene to read a "
                + "screen from. Skipped rather than failed: the pure rules above are the part "
                + "of this probe that has to hold on every runner."
        )

        let expected = scenes.first { $0.activationState == .foregroundActive }?.screen.bounds.maxY
            ?? scenes.first { $0.activationState != .unattached }?.screen.bounds.maxY
            ?? scenes.first?.screen.bounds.maxY

        XCTAssertEqual(TdayKeyboardFrameProbe.activeScreenMaxY(), expected)
        XCTAssertGreaterThan(try XCTUnwrap(TdayKeyboardFrameProbe.activeScreenMaxY()), 0)
    }
}

/// `TdaySheetDragToDismiss` — what a released drag on the sheet card means.
///
/// The rule is split off the view for the reason the probe above is: a decision
/// inside a `body` is a decision nothing can ask a question of, and every one of
/// the cases below is a gesture that cannot be staged in a unit test but can be
/// stated as four numbers. What these hold is the one property the rule exists
/// for — that a release is read as a *projection* and not as a position, which
/// is the same argument `tday-web/src/lib/swipeGesture.ts` makes for
/// `projectedRest` on the other client. A position-only rule passes the first
/// three of these and fails the fourth, which is precisely how the toast's
/// `translation > 30 || predictedEnd > 90` behaves and why this surface does not
/// copy it.
///
/// What they cannot see is whether a committed drag settles on the same curve
/// the scrim tap leaves on, or whether the grabber reads as a second header.
/// Both are TestFlight eye-checks (`docs/verification/phase-9-device-pass.md`).
final class TdaySheetDragToDismissTests: XCTestCase {
    /// A create-task sheet on an iPhone 15 Pro: 86 % of an 852 pt screen.
    private let sheetHeight: CGFloat = 730

    func testAShortSlowDragIsNotADismissal() {
        // 40 pt of finger with nothing behind it — a hand steadying on the card,
        // or a pull that thought better of itself.
        XCTAssertFalse(
            TdaySheetDragToDismiss.shouldDismiss(
                translation: 40,
                predictedEndTranslation: 48,
                sheetHeight: sheetHeight
            )
        )
    }

    func testAShortFastFlickIs() {
        // The same 40 pt, thrown. It never reached a quarter of the card and is
        // not going to be dragged there either; what commits it is that it was
        // still travelling when the finger left.
        XCTAssertTrue(
            TdaySheetDragToDismiss.shouldDismiss(
                translation: 40,
                predictedEndTranslation: 320,
                sheetHeight: sheetHeight
            )
        )
    }

    func testALongSlowDragPastTheFractionIs() {
        // Carried a quarter of the way down and let go standing still, so the
        // projection is the position — which is the case where the two rules
        // agree, and the one a fraction of the card's own height is chosen for.
        XCTAssertTrue(
            TdaySheetDragToDismiss.shouldDismiss(
                translation: 190,
                predictedEndTranslation: 192,
                sheetHeight: sheetHeight
            )
        )
    }

    func testALongDragAlreadyBeingWalkedBackIsNot() {
        // Well past the threshold on position and travelling the other way. This
        // is the case the whole projection exists for: the user is putting the
        // card back and the last frame of the gesture says so.
        XCTAssertFalse(
            TdaySheetDragToDismiss.shouldDismiss(
                translation: 300,
                predictedEndTranslation: 90,
                sheetHeight: sheetHeight
            )
        )
    }

    func testTheThresholdIsAFractionOfTheCardAndNotAFlatDistance() {
        // One projection, two cards. The short create-floater sheet lets it go;
        // the tall create-task sheet does not — which a flat 30 pt could not say,
        // and is why this rule takes a height at all.
        let projection: CGFloat = 150
        XCTAssertTrue(
            TdaySheetDragToDismiss.shouldDismiss(
                translation: projection,
                predictedEndTranslation: projection,
                sheetHeight: 420
            )
        )
        XCTAssertFalse(
            TdaySheetDragToDismiss.shouldDismiss(
                translation: projection,
                predictedEndTranslation: projection,
                sheetHeight: sheetHeight
            )
        )
    }

    func testAnUnmeasuredCardRefusesRatherThanDismissingOnTheSlop() {
        // `contentHeight` is 0 until the first preference lands. A fraction of
        // nothing is nothing, so an unguarded rule would read the 10 pt of slop
        // that starts the gesture as a completed dismissal.
        XCTAssertFalse(
            TdaySheetDragToDismiss.shouldDismiss(
                translation: 400,
                predictedEndTranslation: 600,
                sheetHeight: 0
            )
        )
    }

    func testAnUpwardDragIsNotADismissalHoweverItEnds() {
        // The card does not move for an upward pull — it is already at its own
        // content height — so a gesture that ended above where it started never
        // moved anything, and the flick it was released with is not an answer to
        // a question the user asked.
        XCTAssertFalse(
            TdaySheetDragToDismiss.shouldDismiss(
                translation: -60,
                predictedEndTranslation: 400,
                sheetHeight: sheetHeight
            )
        )
    }
}
