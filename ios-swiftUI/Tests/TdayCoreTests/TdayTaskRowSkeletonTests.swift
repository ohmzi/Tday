import SwiftUI
import XCTest

#if SWIFT_PACKAGE
@testable import TdayCore
#else
@testable import Tday
#endif

/// `TdayTaskRowSkeleton` — the placeholder a feed draws before it has rows.
///
/// Two things about this type can be wrong in a way nobody sees until it ships,
/// and neither of them is the drawing. The first is the **lengths**: `Animation`
/// is opaque, so a pulse quietly retimed or a crossfade written at a number
/// instead of a rung compiles, looks deliberate, and can only be caught if the
/// lengths have names — the reason `TdayFeedItemMotion.Durations` exists and the
/// reason this type copies it. The second is the **geometry**: the whole point of
/// a placeholder is that the feed does not resize when the content lands, and a
/// placeholder that has drifted from the row it stands in for still draws
/// perfectly well.
///
/// Whether three grey bars read as a list loading rather than as a list of grey
/// bars is an eye question, and is a row in
/// `docs/verification/phase-9-device-pass.md`.
final class TdayTaskRowSkeletonTests: XCTestCase {

    // MARK: - The lengths

    /// The hand-over is `Enter`, and specifically not the 190 ms that the ledger
    /// row and Android's own `TdayFeedItemMotion` constant both say.
    /// `docs/motion.md` settled that difference in favour of the rung and left the
    /// Android literal owing the move; asserting the rung here is what keeps iOS
    /// from being migrated *back* to 190 by someone reading the ledger row and
    /// taking it for the spec.
    func testTheCrossfadeIsTheEnterRungAndNotTheLedgersNumber() {
        XCTAssertEqual(TdayTaskRowSkeleton.Durations.crossfade, TdayMotion.Durations.enter)
        XCTAssertNotEqual(TdayTaskRowSkeleton.Durations.crossfade, 0.19)
    }

    /// The placeholder leaving and the rows arriving are one dissolve, so they run
    /// off one clock. Two rungs here would put a seam in the middle of it — the
    /// bars gone before the rows are solid, or still there behind them — and that
    /// is not something a still frame or a screenshot test would show.
    func testTheCrossfadeRunsOnTheSameClockAsAnArrivingRow() {
        XCTAssertEqual(
            TdayTaskRowSkeleton.Durations.crossfade,
            TdayFeedItemMotion.Durations.arrival
        )
    }

    /// The pulse is `Change` — a surface altering in place. The lower bound is
    /// what the assertion is really about: `autoreverses` doubles whatever is
    /// written here, so `Quick` would be a 300 ms round trip, which is a flicker
    /// and not breathing.
    func testThePulseIsTheChangeRung() {
        XCTAssertEqual(TdayTaskRowSkeleton.Durations.pulse, TdayMotion.Durations.change)
        XCTAssertGreaterThan(TdayTaskRowSkeleton.Durations.pulse, TdayMotion.Durations.quick)
    }

    /// Both animations are the app's own curve rather than SwiftUI's `.easeInOut`,
    /// which is a materially longer tail and is the obvious thing to reach for.
    /// `TdayMotion.standard` says so in its own doc comment; this is the assertion
    /// that would notice it being reached for.
    func testBothAnimationsRunOnTheStandardCurve() {
        XCTAssertEqual(
            TdayTaskRowSkeleton.crossfade,
            TdayMotion.standard(duration: TdayTaskRowSkeleton.Durations.crossfade)
        )
        XCTAssertEqual(
            TdayTaskRowSkeleton.pulse,
            TdayMotion.standard(duration: TdayTaskRowSkeleton.Durations.pulse)
                .repeatForever(autoreverses: true)
        )
    }

    /// A pulse that can stop is a placeholder that goes still while the request is
    /// still in flight, which reads as the app having given up. `repeatForever` is
    /// applied inside the type rather than at the call site precisely so no feed
    /// can be handed the un-repeated animation, and the assertion for that is that
    /// the shipped value is *not* the bare tween.
    func testThePulseIsNotTheBareTween() {
        XCTAssertNotEqual(
            TdayTaskRowSkeleton.pulse,
            TdayMotion.standard(duration: TdayTaskRowSkeleton.Durations.pulse)
        )
    }

    // MARK: - Reduce Motion

    /// The gate answers `nil`, which in SwiftUI means "apply, do not animate" —
    /// so the state still changes and the finished frame is still drawn. What this
    /// holds is that the placeholder goes through the gate at all: the failure it
    /// is written against is a `repeatForever` that was never asked, which is a
    /// surface pulsing forever on a device whose owner asked the system to stop
    /// animating things.
    func testReduceMotionResolvesBothAnimationsToNil() {
        XCTAssertNil(TdayMotionResolution.reduced(TdayTaskRowSkeleton.crossfade))
        XCTAssertNil(TdayMotionResolution.reduced(TdayTaskRowSkeleton.pulse))
    }

    /// The other half, and not a formality: a gate that answered `nil` both ways
    /// would pass the test above and take the placeholder's motion with it.
    func testFullMotionKeepsBothAnimations() {
        XCTAssertEqual(
            TdayMotionResolution.full(TdayTaskRowSkeleton.crossfade),
            TdayTaskRowSkeleton.crossfade
        )
        XCTAssertEqual(
            TdayMotionResolution.full(TdayTaskRowSkeleton.pulse),
            TdayTaskRowSkeleton.pulse
        )
    }

    // MARK: - The geometry

    /// The numbers `ScheduledTaskHomeTodayTaskRow.rowContent` was built at, which
    /// the Today placeholder reads from the same place the row does. The timeline
    /// feeds are a different row and have their own test below.
    ///
    /// That shared read is what makes the match an identity rather than a
    /// coincidence, and it is also what this test has to defend: with one
    /// constant behind both views, editing the row silently edits the placeholder
    /// too, and a spacing change nobody meant to make to a loading state would go
    /// through review as a row tweak. Pinning the values here does not stop that
    /// edit — it makes it a failing test that says the placeholder was built to
    /// these numbers and should be looked at again.
    func testTheRowMetricsAreTheOnesTheRealRowWasBuiltAt() {
        XCTAssertEqual(TdayTaskRowMetrics.contentSpacing, 12)
        XCTAssertEqual(TdayTaskRowMetrics.checkSlot, 38)
        XCTAssertEqual(TdayTaskRowMetrics.checkGlyph, 24)
        XCTAssertEqual(TdayTaskRowMetrics.textSpacing, 3)
        XCTAssertEqual(TdayTaskRowMetrics.titleFontSize, 18)
        XCTAssertEqual(TdayTaskRowMetrics.subtitleFontSize, 13)
        XCTAssertEqual(TdayTaskRowMetrics.notesFontSize, 12)
        XCTAssertEqual(TdayTaskRowMetrics.metaIcon, 14)
        XCTAssertEqual(TdayTaskRowMetrics.metaSpacing, 8)
        XCTAssertEqual(TdayTaskRowMetrics.metaTrailingPadding, 8)
        XCTAssertEqual(TdayTaskRowMetrics.verticalPadding, 10)
        XCTAssertEqual(TdayTaskRowMetrics.horizontalPadding, 4)
    }

    /// The Today *set* is the Today row, field for field.
    ///
    /// `TdayTaskRowSkeletonMetrics` is a struct with a memberwise initialiser, and
    /// a memberwise initialiser of ten `CGFloat`s is a place where two adjacent
    /// arguments can be swapped and everything still compiles. This is the
    /// assertion that the set was wired to the constants above rather than
    /// plausibly near them.
    func testTheTodaySetIsTheTodayRowFieldForField() {
        let today = TdayTaskRowSkeletonMetrics.today
        XCTAssertEqual(today.contentSpacing, TdayTaskRowMetrics.contentSpacing)
        XCTAssertEqual(today.checkSlot, TdayTaskRowMetrics.checkSlot)
        XCTAssertEqual(today.checkGlyph, TdayTaskRowMetrics.checkGlyph)
        XCTAssertEqual(today.textSpacing, TdayTaskRowMetrics.textSpacing)
        XCTAssertEqual(today.titleFontSize, TdayTaskRowMetrics.titleFontSize)
        XCTAssertEqual(today.subtitleFontSize, TdayTaskRowMetrics.subtitleFontSize)
        XCTAssertEqual(today.metaIcon, TdayTaskRowMetrics.metaIcon)
        XCTAssertEqual(today.metaTrailingPadding, TdayTaskRowMetrics.metaTrailingPadding)
        XCTAssertEqual(today.verticalPadding, TdayTaskRowMetrics.verticalPadding)
        XCTAssertEqual(today.horizontalPadding, TdayTaskRowMetrics.horizontalPadding)
        XCTAssertNil(today.checkBaselineNudge)
    }

    /// The timeline sets are the timeline row, which is a *different* row.
    ///
    /// The first cut of the placeholder had one set and three call sites, so a task
    /// list and Completed drew Today's geometry: 10 pt of vertical padding against
    /// their 8, a text column spaced 3 against their 4, and 4 pt of horizontal
    /// padding they do not have — four or five points per row, a good half-line
    /// across three of them, appearing at the exact moment the content lands. That
    /// is not a cosmetic difference; it is the entire defect a placeholder is built
    /// to remove, and it is invisible to every other test here because the numbers
    /// were all perfectly self-consistent.
    func testTheTimelineSetsAreTheTimelineRowAndNotTodays() {
        for metrics in [TdayTaskRowSkeletonMetrics.minimalTimeline, .completedTimeline] {
            XCTAssertEqual(metrics.contentSpacing, TodoTimelineMetrics.minimalRowContentSpacing)
            XCTAssertEqual(metrics.checkSlot, TodoTimelineMetrics.minimalRowToggleFrame)
            XCTAssertEqual(metrics.checkGlyph, TodoTimelineMetrics.minimalRowToggleSize)
            XCTAssertEqual(metrics.textSpacing, TodoTimelineMetrics.minimalRowTextSpacing)
            XCTAssertEqual(metrics.titleFontSize, TodoTimelineMetrics.minimalRowTitleSize)
            XCTAssertEqual(metrics.subtitleFontSize, TodoTimelineMetrics.minimalRowSubtitleSize)
            XCTAssertEqual(metrics.metaIcon, TodoTimelineMetrics.minimalRowIndicatorSize)
            XCTAssertEqual(
                metrics.metaTrailingPadding,
                TodoTimelineMetrics.minimalRowTrailingIndicatorPadding
            )
            XCTAssertEqual(metrics.verticalPadding, TodoTimelineMetrics.minimalRowVerticalPadding)
            // The List's own `listRowInsets` hold these rows off the edges, so the
            // row adds nothing and neither may the placeholder.
            XCTAssertEqual(metrics.horizontalPadding, 0)
        }
    }

    /// The three sets differ in the two fields that decide a row's height, so a
    /// call site that reached for the wrong one is a visible bug rather than a
    /// rounding error — which is the argument for there being three of them at all.
    func testTheSetsActuallyDisagreeAboutHeight() {
        XCTAssertNotEqual(
            TdayTaskRowSkeletonMetrics.today.verticalPadding,
            TdayTaskRowSkeletonMetrics.minimalTimeline.verticalPadding
        )
        XCTAssertNotEqual(
            TdayTaskRowSkeletonMetrics.today.textSpacing,
            TdayTaskRowSkeletonMetrics.minimalTimeline.textSpacing
        )
    }

    /// A task list hangs its toggle off the title's first baseline and nudges the
    /// guide so it stays on line one of a title that wraps; Today and Completed
    /// centre theirs. The nudge is part of the row's height — it is what stands the
    /// button proud of the text column — so a placeholder that took the alignment
    /// without the guide, or the guide without the alignment, would be a different
    /// height from the row it stands in for on the one feed that uses it.
    func testOnlyTheBaselineAlignedSetCarriesTheBaselineNudge() {
        XCTAssertEqual(
            TdayTaskRowSkeletonMetrics.minimalTimeline.checkBaselineNudge,
            TodoTimelineMetrics.minimalRowBaselineNudge
        )
        XCTAssertNil(TdayTaskRowSkeletonMetrics.completedTimeline.checkBaselineNudge)
        XCTAssertNil(TdayTaskRowSkeletonMetrics.today.checkBaselineNudge)
    }

    /// The glyph is drawn inside the slot, not as the slot. The check button's tap
    /// target is deliberately larger than the mark in it, and a placeholder that
    /// filled the whole target would put a 38 pt disc where the user is about to
    /// see a 24 pt circle — the row would not resize, but the eye would still be
    /// handed the wrong drawing.
    func testTheCheckGlyphIsSmallerThanTheTapTargetAroundIt() {
        XCTAssertLessThan(TdayTaskRowMetrics.checkGlyph, TdayTaskRowMetrics.checkSlot)
    }
}
