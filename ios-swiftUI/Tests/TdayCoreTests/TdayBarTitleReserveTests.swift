import CoreGraphics
import XCTest

#if SWIFT_PACKAGE
@testable import TdayCore
#else
@testable import Tday
#endif

/// The docked title's reserve, pinned as arithmetic.
///
/// There is no iOS toolchain on the machine this was written on, and no device
/// either, so a rendered assertion about a bar could only ever restate whatever
/// the test itself believed about font metrics. The rule is therefore a pure
/// function of four numbers and this file is the whole proof — the same shape, for
/// the same reason, as Android's `TdayBarTitleReserveTest` and web's
/// `native-page-bar-title-reserve.test.ts` one client over.
///
/// THE WIDTHS ARE NAMED FOR WHERE THEY COME FROM, because two of them are geometry
/// and one is a typeface. `BAR_WIDTH`, `BACK_BUTTON` and `ACTION_CLUSTER` are read
/// off `TodoTimelineMetrics` and this file's own arithmetic on a 393pt phone:
/// `topBarButtonFrame` is 56, `topBarButtonSpacing` is 8 so the calendar's two
/// trailing circles are 120, and the bar insets `horizontalPadding` 18 either side.
/// `CALENDAR_TITLE_WIDTH` comes from the font file this app ships rather than from
/// a device: `Resources/Fonts/Nunito.ttf` instantiated at wght 800 — the instance
/// `TdayFont.postScriptName` resolves `.heavy` to, `Nunito-ExtraBold` — with the
/// `hmtx` advances for the eight glyphs of "Calendar" summed and taken at 32pt,
/// which is `TodoTimelineMetrics.heroTitleSize`. 4297/1000 × 32 = 137.5. Advances
/// only, so it carries no GPOS kerning; the web bar's twin of this number was
/// measured with kerning included and came in 0.5% BELOW its own advance sum
/// (146.6 against 147.4 at wght 900), so this one is the same half percent
/// generous and nothing below turns on the difference. The property tests at the
/// foot depend on no width at all, which is why they are the load-bearing half.
final class TdayBarTitleReserveTests: XCTestCase {

    /// `TdayBarTitleReserveMetrics` has no cases, so it cannot be held as a value.
    private typealias M = TdayBarTitleReserveMetrics

    /// A 393pt phone: this bar's inner width is the screen less 18pt either side.
    private let barWidth: CGFloat = 357
    /// `TodoTimelineMetrics.topBarButtonFrame` — the one back chevron on the left.
    private let backButton: CGFloat = 56
    /// Two of those circles and the gap between them — search, then Today, which
    /// has already folded to a circle by the time the title is on screen.
    private let actionCluster: CGFloat = 120
    /// See the file note: the shipped Nunito's advances at wght 800, 32pt.
    private let calendarTitleWidth: CGFloat = 137.5

    private func room(_ reserve: TdayBarTitleReserve, in width: CGFloat) -> CGFloat {
        width - reserve.leading - reserve.trailing
    }

    /// The rule as it stood on this client, kept here so the change can be
    /// measured against it rather than described. Its only difference is the gate:
    /// this bar asked whether a STUMP fitted, not whether the title did.
    private func previousReserve(
        barWidth: CGFloat,
        leading: CGFloat,
        trailing: CGFloat
    ) -> (leading: CGFloat, trailing: CGFloat, room: CGFloat) {
        let symmetric = max(leading, trailing) + M.sideGap
        let centred = barWidth - symmetric * 2 >= M.minWidth
        let leadingReserve = centred ? symmetric : leading + M.sideGap
        let trailingReserve = centred ? symmetric : trailing + M.sideGap
        return (leadingReserve, trailingReserve, barWidth - leadingReserve - trailingReserve)
    }

    // MARK: - The bug it was ported to fix

    func testTheOldGateLeftCalendarLessThanTheWordNeeds() {
        // Not an assertion about the new code: it is the evidence that the case is
        // real, so that the expectation below is a fix and not a preference.
        // Mirroring the 120pt cluster spends 132 a side, 264 of the 357.
        let before = previousReserve(
            barWidth: barWidth,
            leading: backButton,
            trailing: actionCluster
        )
        XCTAssertEqual(before.leading, 132)
        XCTAssertEqual(before.trailing, 132)
        XCTAssertEqual(before.room, 93)

        // And the scale factor this bar leaned on could not cover the difference.
        // 0.72 of the word is 99.0, still past 93 — which is why the report is a
        // title that goes to "Cale…" rather than one that is merely small.
        XCTAssertLessThan(before.room, calendarTitleWidth * 0.72)
    }

    func testCalendarGetsTheWholeWordOnceTheTitleIsMeasured() {
        let reserve = tdayBarTitleReserve(
            barWidth: barWidth,
            leading: backButton,
            trailing: actionCluster,
            titleWidth: calendarTitleWidth
        )
        // Per-side: 56 + 12 leading, 120 + 12 trailing, leaving 157.
        XCTAssertEqual(reserve.leading, 68)
        XCTAssertEqual(reserve.trailing, 132)
        XCTAssertEqual(room(reserve, in: barWidth), 157)
        XCTAssertGreaterThanOrEqual(room(reserve, in: barWidth), calendarTitleWidth)
        XCTAssertTrue(reserve.hasRoom)
    }

    func testFallingThroughCostsTheCrossfadeExactlyTheDifferenceBetweenTheSides() {
        // The one thing the per-side branch is not free. The docked copy stops
        // being centred on the bar and sits (trailing - leading) / 2 to the left,
        // which is what the mirrored branch exists to prevent — so it is spent
        // only where the alternative is a word that does not fit at all.
        let mirrored = max(backButton, actionCluster)
        let reserve = tdayBarTitleReserve(
            barWidth: barWidth,
            leading: backButton,
            trailing: actionCluster,
            titleWidth: calendarTitleWidth
        )
        XCTAssertEqual(reserve.leading, backButton + M.sideGap)
        XCTAssertNotEqual(reserve.leading, mirrored + M.sideGap)
        XCTAssertEqual((reserve.trailing - reserve.leading) / 2, 32)
    }

    // MARK: - The mirrored branch, which is still first

    func testKeepsABarWhoseTitleFitsCentredOnTheBar() {
        // Nothing on the right at all, as on a screen whose title is its only
        // control: mirroring the 56pt back button still clears a 140pt name, so
        // the title stays centred rather than drifting off the bar.
        let reserve = tdayBarTitleReserve(
            barWidth: 380,
            leading: backButton,
            trailing: 0,
            titleWidth: 140
        )
        XCTAssertEqual(reserve.leading, 68)
        XCTAssertEqual(reserve.trailing, 68)
        XCTAssertEqual(room(reserve, in: 380), 244)
    }

    func testIsWhatAnUnmeasuredTitleStillGetsUnchanged() {
        // Zero fits everything, so the frame before the probe has a box behaves
        // exactly as this bar always did. That is what makes a late measurement
        // safe: the first frame is the old behaviour and every frame after it is
        // the fixed one.
        let bars: [(CGFloat, CGFloat, CGFloat)] = [
            (357, backButton, actionCluster),
            (380, backButton, 0),
            (342, backButton, 236),
        ]
        for (width, leading, trailing) in bars {
            let now = tdayBarTitleReserve(barWidth: width, leading: leading, trailing: trailing)
            let before = previousReserve(barWidth: width, leading: leading, trailing: trailing)
            XCTAssertEqual(now.leading, before.leading)
            XCTAssertEqual(now.trailing, before.trailing)
        }
    }

    func testIsNotHeldByATitleNarrowerThanTheFloorOnABarWithNoRoom() {
        // The `minWidth` term of the `max` is the old gate, kept. Without it a
        // 20pt title would call a 30pt gap "centred", and `hasRoom` would then be
        // answering against a reserve chosen for the wrong reason.
        let reserve = tdayBarTitleReserve(
            barWidth: 200,
            leading: backButton,
            trailing: 77,
            titleWidth: 20
        )
        XCTAssertEqual(reserve.leading, 68)
        XCTAssertEqual(reserve.trailing, 89)
        XCTAssertFalse(reserve.hasRoom)
    }

    // MARK: - Bars the reserve cannot rescue

    func testReservesWhatIsActuallyThereWhenTheBarHasNoBoxYet() {
        let reserve = tdayBarTitleReserve(
            barWidth: 0,
            leading: backButton,
            trailing: actionCluster,
            titleWidth: calendarTitleWidth
        )
        XCTAssertEqual(reserve.leading, backButton)
        XCTAssertEqual(reserve.trailing, actionCluster)
        XCTAssertTrue(reserve.hasRoom)
    }

    func testDrawsNoTitleWhenTheControlsHaveEatenTheRow() {
        // Mirrored is already negative, per-side leaves under the floor, and a
        // reserve wider than the bar would paint the title across the buttons
        // rather than hide it. So: no title, and the block's own copy stands as
        // the screen's only heading.
        let reserve = tdayBarTitleReserve(
            barWidth: 320,
            leading: backButton,
            trailing: 290,
            titleWidth: 160
        )
        XCTAssertLessThan(room(reserve, in: 320), M.minWidth)
        XCTAssertFalse(reserve.hasRoom)
    }

    // MARK: - The property that makes the change safe everywhere

    /// Every combination a real bar could present, plus a good deal that no bar
    /// does. The claims below are what let this land on a bar with no device to
    /// check it on.
    private let barWidths: [CGFloat] = [200, 280, 320, 342, 357, 393, 412, 600, 1024]
    private let clusters: [CGFloat] = [0, 44, 56, 66, 112, 120, 150, 236, 290]
    private let titles: [CGFloat] = [0, 23, 51, 100, 137.5, 240, 500]

    func testNeverHandsATitleLessRoomThanThePreviousRuleDid() {
        for width in barWidths {
            for leading in clusters {
                for trailing in clusters {
                    for title in titles {
                        let now = room(
                            tdayBarTitleReserve(
                                barWidth: width,
                                leading: leading,
                                trailing: trailing,
                                titleWidth: title
                            ),
                            in: width
                        )
                        let before = previousReserve(
                            barWidth: width,
                            leading: leading,
                            trailing: trailing
                        ).room
                        XCTAssertGreaterThanOrEqual(
                            now,
                            before,
                            "bar=\(width) leading=\(leading) trailing=\(trailing) title=\(title)"
                        )
                    }
                }
            }
        }
    }

    func testGainsExactlyTheDifferenceBetweenTheSidesWhenItFallsThrough() {
        // Per-side leaves W - L - T - 2g; mirrored leaves W - 2·max(L,T) - 2g. The
        // difference is |T - L| and it is never negative, which is the whole
        // argument: this step can only ever give the title width.
        for width in barWidths {
            for leading in clusters {
                for trailing in clusters {
                    let perSide = width - leading - trailing - M.sideGap * 2
                    let mirrored = width - max(leading, trailing) * 2 - M.sideGap * 2
                    XCTAssertEqual(perSide - mirrored, abs(trailing - leading))
                    XCTAssertGreaterThanOrEqual(perSide, mirrored)
                }
            }
        }
    }

    func testNeverLetsATitleThatWasDrawnBeforeStopBeingDrawn() {
        for width in barWidths {
            for leading in clusters {
                for trailing in clusters {
                    let before = previousReserve(
                        barWidth: width,
                        leading: leading,
                        trailing: trailing
                    ).room >= M.minWidth
                    for title in titles {
                        let now = tdayBarTitleReserve(
                            barWidth: width,
                            leading: leading,
                            trailing: trailing,
                            titleWidth: title
                        ).hasRoom
                        if before && width > 0 {
                            XCTAssertTrue(
                                now,
                                "bar=\(width) leading=\(leading) trailing=\(trailing) title=\(title)"
                            )
                        }
                    }
                }
            }
        }
    }

    func testNeverReservesSoMuchThatTheTitleWouldPaintOverAControl() {
        for width in barWidths {
            for leading in clusters {
                for trailing in clusters {
                    for title in titles {
                        let reserve = tdayBarTitleReserve(
                            barWidth: width,
                            leading: leading,
                            trailing: trailing,
                            titleWidth: title
                        )
                        guard reserve.hasRoom else { continue }
                        XCTAssertGreaterThanOrEqual(reserve.leading, leading)
                        XCTAssertGreaterThanOrEqual(reserve.trailing, trailing)
                    }
                }
            }
        }
    }
}
