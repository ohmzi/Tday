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
