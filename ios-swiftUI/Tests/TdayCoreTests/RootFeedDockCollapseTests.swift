import SwiftUI
import XCTest

#if SWIFT_PACKAGE
@testable import TdayCore
#else
@testable import Tday
#endif

/// The root dock's fold point, driven the way a finger drives it: one answer at a
/// time, each one fed the one before it.
///
/// The defect this pins is not a wrong distance. Both root feeds folded the dock on
/// a single `offset > 44` comparison, and a single comparison has no memory, so the
/// point either side of 44 is the point either side of a 60pt pill and a 212pt
/// capsule. A scroll view settling a point back and forth out of its own
/// deceleration — or a finger parked there — made the dock strobe. Endpoint
/// assertions cannot see that: 20 points of scroll leaves the dock open and 60 folds
/// it, before this change and after it. The third case below is the whole test. At
/// 30, having already folded, the dock must STAY folded; the symmetric comparison
/// answers that it opens.
///
/// Expectations are pinned as literals rather than re-derived from
/// `RootFeedDockCollapse.collapseThreshold` and `.expandThreshold` — a test that
/// computes its inputs from the constants it is testing agrees with them whatever
/// they become, and these two are shared with Android and web, which is exactly the
/// drift worth failing over.
final class RootFeedDockCollapseTests: XCTestCase {

    private func fold(previous: Bool, offset: CGFloat) -> Bool {
        RootFeedDockCollapse.next(previous: previous, offset: offset)
    }

    func testTheTwoThresholdsAreTheDistancesTheOtherClientsFoldAt() {
        XCTAssertEqual(RootFeedDockCollapse.collapseThreshold, 44)
        XCTAssertEqual(RootFeedDockCollapse.expandThreshold, 24)
    }

    func testAFoldSurvivesAScrollBackThatAnOpenDockWouldNotHaveFoldedOn() {
        let openApproaching = fold(previous: false, offset: 40)
        let folded = fold(previous: openApproaching, offset: 46)
        let stillFolded = fold(previous: folded, offset: 30)
        let openAgain = fold(previous: stillFolded, offset: 20)

        XCTAssertFalse(openApproaching)
        XCTAssertTrue(folded)
        XCTAssertTrue(stillFolded)
        XCTAssertFalse(openAgain)
    }

    /// A feed rubber-banded above its own top reports a negative offset, and the
    /// claim here is about the answer rather than about the clamp that produces it:
    /// with both edges positive, a negative offset compares false either way. What
    /// it would catch is the offset being read as a position, and the dock folding
    /// on anything that is not exactly zero.
    func testAnOffsetAboveTheTopOfTheFeedIsNoTravelAtAll() {
        XCTAssertFalse(fold(previous: false, offset: -120))
        XCTAssertFalse(fold(previous: true, offset: -120))
    }
}
