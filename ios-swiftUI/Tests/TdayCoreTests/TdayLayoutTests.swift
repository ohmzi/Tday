import CoreGraphics
import XCTest

#if SWIFT_PACKAGE
@testable import TdayCore
#else
@testable import Tday
#endif

/// The layout rule, pinned as arithmetic.
///
/// The cases are the ones `LayoutTokensTest` asks in `:shared` and
/// `layout-tokens.test.ts` asks on the web: the three clients run one rule, and
/// these are the questions all of them are held to. There is no iOS toolchain on the
/// machine that wrote this, so it is the macOS CI run that proves them.
final class TdayLayoutTests: XCTestCase {

    func testTheGapNeverGoesUnderTheFadeBand() {
        XCTAssertEqual(
            TdayLayout.contentGap(screenHeight: 640, titleBottom: 400),
            TdayLayoutGenerated.contentGapMin
        )
        XCTAssertEqual(
            TdayLayout.contentGap(screenHeight: 667, titleBottom: 254),
            TdayLayoutGenerated.contentGapMin
        )
    }

    func testTheGapIsCappedOnATallScreen() {
        XCTAssertEqual(
            TdayLayout.contentGap(screenHeight: 1400, titleBottom: 280),
            TdayLayoutGenerated.contentGapMax
        )
    }

    func testTheGapSpendsExactlyWhatReachesTheLine() {
        let gap = TdayLayout.contentGap(screenHeight: 800, titleBottom: 250)
        XCTAssertEqual(250 + gap, TdayLayoutGenerated.contentStartRatio * 800, accuracy: 1e-9)
    }

    /// The phone this client was drawn on: 874pt tall, a 62pt safe area, the 56pt
    /// bar and the 178pt hero block. The line lands within a fraction of a point of
    /// the floor, so the gap is what it was before the ratio existed and the content
    /// starts at 332 — the line the other clients are brought to.
    func testTheReferencePhoneKeepsItsPresentPosition() {
        let titleBottom: CGFloat = 62
            + TodoTimelineMetrics.topBarRowHeight
            + TodoTimelineMetrics.titleCollapseDistance
        let gap = TdayLayout.contentGap(screenHeight: 874, titleBottom: titleBottom)
        XCTAssertEqual(gap, TodoTimelineMetrics.settledContentGap, accuracy: 0.5)
        XCTAssertEqual(titleBottom + gap, 332, accuracy: 1)
    }

    func testTheEmptySlotScalesWithTheScreen() {
        XCTAssertEqual(
            TdayLayout.emptySlotHeight(screenHeight: 800),
            TdayLayoutGenerated.emptySlotRatio * 800,
            accuracy: 1e-9
        )
    }
}
