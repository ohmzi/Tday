import SwiftUI

/// The geometry of the Today row, in one place, so a placeholder can be built to
/// it rather than at it.
///
/// These numbers were already in the app — they are the ones
/// `ScheduledTaskHomeTodayTaskRow.rowContent` was written with, moved here
/// unchanged and read back from there. Copying them into the skeleton instead
/// would have produced two independent spellings of the same row, and the failure
/// that follows from that is not a wrong number but a *drifting* one: the
/// placeholder keeps matching the row exactly until somebody adjusts the row, and
/// from then on the feed resizes at the moment the content lands, which is the one
/// thing a placeholder exists to prevent. Shared, a change to the row is a change
/// to the placeholder in the same edit.
///
/// Only the metrics the placeholder can stand in for live here. The row's colours,
/// its glyph names and its swipe geometry stay where they are used — a shared
/// constant is worth the indirection when two views must agree, and is noise when
/// only one view reads it.
enum TdayTaskRowMetrics {

    /// Between the check button and the text column.
    static let contentSpacing: CGFloat = 12

    /// The check button's tap target. Larger than the glyph inside it, which is
    /// why both are named: the placeholder has to reserve the slot but draw the
    /// mark.
    static let checkSlot: CGFloat = 38

    /// The check glyph itself.
    static let checkGlyph: CGFloat = 24

    /// Between the title, the due line and the notes line.
    static let textSpacing: CGFloat = 3

    static let titleFontSize: CGFloat = 18
    static let subtitleFontSize: CGFloat = 13
    static let notesFontSize: CGFloat = 12

    /// The trailing list/priority marks: their size, the gap between them, and
    /// the inset that keeps them off the row's edge.
    static let metaIcon: CGFloat = 14
    static let metaSpacing: CGFloat = 8
    static let metaTrailingPadding: CGFloat = 8

    static let verticalPadding: CGFloat = 10
    static let horizontalPadding: CGFloat = 4

    /// How far below its own centre the check button reports its first text
    /// baseline, so it stays on line one of a text column that runs to several.
    ///
    /// The same 5 pt the timeline row has used since it learned to wrap
    /// (`TodoTimelineMetrics.minimalRowBaselineNudge`), named here as well rather
    /// than reached for across the feature boundary: this is `Core/UI`, and a core
    /// metric that imports a screen's constant is a dependency pointing the wrong
    /// way. The two are pinned equal by `TdayTaskRowSkeletonTests` so they cannot
    /// drift into two different nudges for one gesture.
    static let checkBaselineNudge: CGFloat = 5
}

/// The part of a row's geometry a placeholder can stand in for, as a value rather
/// than a namespace — because there is more than one row.
///
/// [TdayTaskRowMetrics] above is Today's row, and the first cut of this file handed
/// the Today set to all three feeds. The two timeline feeds are a different row:
/// `TodoListScreen.minimalTimelineRow` and `CompletedScreen.CompletedTimelineRow`
/// are drawn at `TodoTimelineMetrics`, whose vertical padding is 8 to Today's 10,
/// whose text column is spaced 4 to Today's 3 and which has no horizontal padding
/// of its own at all. Three placeholder rows built at the wrong one of those run
/// about a line taller than the rows they stand in for and sit 4 pt to their right,
/// which is the resize-at-the-moment-content-arrives this type exists to prevent,
/// wearing a shared constant as a disguise.
///
/// So the sets are plural, and each one is built *from* the constants its own row
/// is drawn with — `.today` from [TdayTaskRowMetrics], `.minimalTimeline` and
/// `.completedTimeline` from `TodoTimelineMetrics` beside the rows themselves. The
/// no-drift property is the shared read, not the singleness: adjust a row and its
/// placeholder moves with it, whichever row it was.
struct TdayTaskRowSkeletonMetrics {
    var contentSpacing: CGFloat
    var checkSlot: CGFloat
    var checkGlyph: CGFloat
    var textSpacing: CGFloat
    var titleFontSize: CGFloat
    var subtitleFontSize: CGFloat
    var metaIcon: CGFloat
    var metaTrailingPadding: CGFloat
    var verticalPadding: CGFloat
    var horizontalPadding: CGFloat

    /// How the row stacks its check button against its text column.
    ///
    /// Every feed now aligns on the title's first baseline so the toggle stays on
    /// line one of a text column that runs to several — it is the task's own mark
    /// and belongs beside its first word, not in the gap between lines one and two.
    /// Today and Completed used to centre theirs and this field is what carried the
    /// difference; it stays a field because the alignment and the nudge below decide
    /// a row's HEIGHT, so a placeholder that guessed either would be a different
    /// height from the row it stands in for, and because nothing guarantees the next
    /// row to arrive is one of these.
    var rowAlignment: VerticalAlignment

    /// Where the check button reports its own baseline, for the rows that align on
    /// one. A view with no text in it has no text baseline, so SwiftUI would align
    /// the button by its bottom edge and stand it a good half-line low — which is
    /// why the real row nudges it, and why a placeholder that skipped the nudge
    /// would be a different height from the row it stands in for. `nil` where the
    /// alignment is `.center` and the guide is never consulted — which no shipping
    /// set is today, and the type keeps the case anyway rather than making a row
    /// that wants centring unrepresentable.
    var checkBaselineNudge: CGFloat?

    /// Today's row, from the constants Today's row is drawn with.
    static let today = TdayTaskRowSkeletonMetrics(
        contentSpacing: TdayTaskRowMetrics.contentSpacing,
        checkSlot: TdayTaskRowMetrics.checkSlot,
        checkGlyph: TdayTaskRowMetrics.checkGlyph,
        textSpacing: TdayTaskRowMetrics.textSpacing,
        titleFontSize: TdayTaskRowMetrics.titleFontSize,
        subtitleFontSize: TdayTaskRowMetrics.subtitleFontSize,
        metaIcon: TdayTaskRowMetrics.metaIcon,
        metaTrailingPadding: TdayTaskRowMetrics.metaTrailingPadding,
        verticalPadding: TdayTaskRowMetrics.verticalPadding,
        horizontalPadding: TdayTaskRowMetrics.horizontalPadding,
        rowAlignment: .firstTextBaseline,
        checkBaselineNudge: TdayTaskRowMetrics.checkBaselineNudge
    )
}

/// One task row, before there is a task to put in it.
///
/// iOS had no loading state for a feed at all: `isLoading` drove the refresh pill
/// and suppressed the empty scene, and nothing on any of the three feeds drew a
/// thing for it — a cold open was an empty frame until the first response landed
/// and then rows appearing out of it, which reads as a screen that failed rather
/// than one that is working. The other two clients grow their own placeholder in
/// the same set of PRs; this is the reading with the least to retire, because there
/// was nothing here to replace.
///
/// Built at [TdayTaskRowSkeletonMetrics] — the caller's own row — rather than at a
/// pleasing set of grey blocks, because a placeholder whose height is a guess buys
/// the jump it was added to remove. The two bars are sized by an invisible line of
/// the *real* font for the same reason: a line-height constant is a guess at a
/// metric Dynamic Type moves underneath it, and the font already knows the answer
/// at whatever size the user has chosen.
struct TdayTaskRowSkeleton: View {

    /// How long the placeholder's own motion runs. Named rather than inlined for
    /// the reason `TdayFeedItemMotion.Durations` gives: `Animation` is opaque,
    /// nothing can ask one how long it runs, and the relationships that make this
    /// pair correct are only assertable if the lengths have names.
    enum Durations {

        /// Change — the rung for a surface altering in place, which is the whole
        /// of what the pulse does. Not `Quick`: at 150 ms a round trip is 300 ms
        /// and reads as a flicker rather than as breathing.
        static let pulse: TimeInterval = TdayMotion.Durations.change

        /// Enter — one thing arriving, which is what the real rows are.
        /// Deliberately the same rung `TdayFeedItemMotion.Durations.arrival` sits
        /// on, so the placeholder leaving and the rows arriving run off one clock;
        /// two lengths here would be a dissolve with a seam in it.
        static let crossfade: TimeInterval = TdayMotion.Durations.enter
    }

    /// The alphas, which are the whole of the drawing.
    ///
    /// [rest] is the resting state and the one a Reduce Motion user is held at —
    /// the fifth idiom rule of `docs/motion.md` read carefully: a placeholder
    /// parked at the *faded* end would be a surface permanently half-drawn, which
    /// is the accommodation turning into the defect.
    private enum Alphas {
        static let fill: Double = 0.16
        static let rest: Double = 1
        static let dim: Double = 0.45
    }

    /// How far each bar runs before it stops. Caps rather than widths: the bars
    /// are flexible and take the narrower of this and the column they are in, so a
    /// small screen shortens them instead of overflowing.
    private enum BarWidths {
        static let title: CGFloat = 188
        static let subtitle: CGFloat = 104
    }

    /// The pulse, and the one shape every caller gets. `repeatForever` is applied
    /// here rather than at the call site so a placeholder cannot be given a pulse
    /// that stops.
    static let pulse: Animation = TdayMotion
        .standard(duration: Durations.pulse)
        .repeatForever(autoreverses: true)

    /// The hand-over between the placeholder and the rows that replace it.
    ///
    /// The Enter *rung*, on the Standard *curve* — which is not the pairing the
    /// name suggests, and is the one `AppRootView` argues for at its own route
    /// hand-over: a crossfade runs both halves off one clock, so neither the
    /// decelerate that describes a thing arriving nor the accelerate that
    /// describes one leaving is a description of it.
    static let crossfade: Animation = TdayMotion.standard(duration: Durations.crossfade)

    /// The row this stands in for. Defaulted to Today's because Today is the feed
    /// that reads as broken when it opens blank, and because a caller that has not
    /// thought about which row it is drawing is better off with a real set than
    /// with a required argument it will fill in from the nearest example.
    var metrics: TdayTaskRowSkeletonMetrics = .today

    @Environment(\.tdayColors) private var colors
    @Environment(\.tdayAnimation) private var tdayAnimation

    /// Off until `onAppear`. On its own this says only that the view has been
    /// placed; whether that turns into motion is [isPulsing]'s question.
    @State private var pulsing = false

    /// The gate and the flag together, the way `TdayEmptyState` reads its own
    /// float, and deliberately not a `guard` in `onAppear`.
    ///
    /// Reduce Motion can be switched on while this is on screen, and `onAppear`
    /// has long since run by then. Asked here, the flip re-evaluates the alpha
    /// *and* resolves the animation below to `nil` in the same pass, so the
    /// placeholder snaps to its resting value — rather than being abandoned
    /// wherever in the cycle the setting happened to change, which for a fade is
    /// a surface left permanently half-drawn.
    private var isPulsing: Bool {
        tdayAnimation.isEnabled && pulsing
    }

    private var fill: Color {
        colors.onSurfaceVariant.opacity(Alphas.fill)
    }

    var body: some View {
        HStack(alignment: metrics.rowAlignment, spacing: metrics.contentSpacing) {
            Circle()
                .fill(fill)
                .frame(width: metrics.checkGlyph, height: metrics.checkGlyph)
                .frame(width: metrics.checkSlot, height: metrics.checkSlot)
                .modifier(CheckBaseline(nudge: metrics.checkBaselineNudge))

            VStack(alignment: .leading, spacing: metrics.textSpacing) {
                bar(size: metrics.titleFontSize, weight: .bold, maxWidth: BarWidths.title)
                bar(size: metrics.subtitleFontSize, weight: .semibold, maxWidth: BarWidths.subtitle)
            }

            Spacer(minLength: 0)

            // The trailing marks are optional on a real row and unknowable on an
            // empty one, so the placeholder reserves their slot and draws nothing
            // in it. A grey square here would promise a list icon that may never
            // arrive. It carries the same baseline nudge the real row gives its own
            // trailing block, for the same reason the button does.
            Color.clear
                .frame(width: metrics.metaIcon, height: metrics.metaIcon)
                .padding(.trailing, metrics.metaTrailingPadding)
                .modifier(CheckBaseline(nudge: metrics.checkBaselineNudge))
        }
        .padding(.vertical, metrics.verticalPadding)
        .padding(.horizontal, metrics.horizontalPadding)
        .opacity(isPulsing ? Alphas.dim : Alphas.rest)
        .animation(tdayAnimation(Self.pulse), value: isPulsing)
        .onAppear { pulsing = true }
        // Nothing here is content. VoiceOver reaching a placeholder would announce
        // a row that does not exist yet and give the user something to try to act
        // on; the feed's own rows arrive with their labels.
        .accessibilityHidden(true)
    }

    /// The baseline guide the baseline-aligned rows give their check button, and a
    /// no-op for the centred ones.
    ///
    /// A `ViewModifier` rather than an `if` in the body: the two legs of a branch in
    /// a `ViewBuilder` are two identities, so a placeholder handed a different metric
    /// set mid-flight would be torn down and rebuilt — and a rebuilt placeholder
    /// restarts its pulse from the top. Applied unconditionally because a guide for
    /// an alignment the stack never asks about costs nothing.
    private struct CheckBaseline: ViewModifier {
        let nudge: CGFloat?

        func body(content: Content) -> some View {
            content.alignmentGuide(.firstTextBaseline) { dimension in
                guard let nudge else {
                    return dimension[.firstTextBaseline]
                }
                return dimension[VerticalAlignment.center] + nudge
            }
        }
    }

    private func bar(size: CGFloat, weight: Font.Weight, maxWidth: CGFloat) -> some View {
        Text(verbatim: " ")
            .font(.tdayRounded(size: size, weight: weight))
            .hidden()
            .frame(maxWidth: maxWidth, alignment: .leading)
            .overlay {
                Capsule(style: .continuous)
                    .fill(fill)
                    // The capsule sits inside the line box rather than filling it:
                    // a bar at the full ascender-to-descender height reads as a
                    // block, and text does not.
                    .padding(.vertical, 2)
            }
    }
}

/// What a feed shows while its first page is still in flight.
///
/// Three by default: enough that the screen reads as a list rather than as one
/// stuck row, few enough that it never fills a viewport and so never promises
/// more than the response may turn out to contain. Callers that know their slot
/// is shorter should say so rather than let it overflow.
struct TdayTaskRowSkeletonGroup: View {
    var count: Int = 3
    var metrics: TdayTaskRowSkeletonMetrics = .today

    var body: some View {
        VStack(spacing: 0) {
            ForEach(0..<count, id: \.self) { _ in
                TdayTaskRowSkeleton(metrics: metrics)
            }
        }
    }
}
