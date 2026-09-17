import CoreGraphics
import SwiftUI

/// How much of a pinned bar the docked title may not have, so that it stays clear
/// of the controls either side of it — and whether what is left is worth drawing a
/// title into at all.
///
/// Its own file, and pure, for the reason its two siblings are: this is a layout
/// claim, there is no device here to settle one on, and the arithmetic IS the
/// behaviour. `tdayBarTitleReserve` in `TdayHeroTitleHeader.kt` and
/// `nativePageBarTitleReserve` in `tday-web/src/components/app/` are the same rule
/// on the other two clients — same two branches, same two terms in the gate, same
/// degenerate case — and each of the three is pinned by its own suite
/// (`TdayBarTitleReserveTest.kt`, `native-page-bar-title-reserve.test.ts`, and
/// `TdayBarTitleReserveTests` here).
///
/// This client was the last to get it, and the reason is worth recording: its bar
/// mirrored the wider side unconditionally and leaned on `.minimumScaleFactor` to
/// survive the reserve that overcharged it. That works right up until it does not.
/// On a 393pt phone the calendar's bar is 357pt wide inside its own margin, the
/// trailing cluster is two 56pt circles plus their gap, and mirroring spends 132pt
/// on BOTH sides — 264 of the 357 — leaving "Calendar" 93pt for a word that wants
/// 137.5 at 32pt ExtraBold. 0.72 of that is 99, still past 93, so the copy did not
/// merely shrink: it ellipsized, at the one moment it was supposed to be handing
/// off from the same word drawn in full in the block underneath.
enum TdayBarTitleReserveMetrics {
    /// Clear air between the docked title and whatever sits beside it.
    ///
    /// 12 here against the other two clients' 8, and deliberately not reconciled
    /// in this change. This bar has reserved a 12pt gap since it existed; the rule
    /// being ported is which SIDES to reserve on, not how much air to leave, and
    /// moving the gap would move the title on every screen that already fits —
    /// the one thing this rule must never do.
    static let sideGap: CGFloat = 12

    /// Least width worth docking a title into — one initial and the ellipsis,
    /// which at 32pt is nearly all ellipsis. Below this the bar keeps no title at
    /// all and the block's own copy stands as the screen's only heading.
    ///
    /// The web bar's twin carries the same number and the same reasoning, and
    /// Android's `TdayHeroTitleMetrics.DockedTitleMinWidth` the same again.
    ///
    /// It gates whether a title is shown AT ALL. It deliberately does not gate
    /// whether one FITS — see `tdayBarTitleReserve(barWidth:leading:trailing:titleWidth:)`,
    /// where confusing the two is the bug this whole file exists to undo. On this
    /// client the two were confused in the other direction: nothing measured the
    /// title, so nothing could tell a reserve the name fitted inside from one it
    /// did not.
    static let minWidth: CGFloat = 56
}

/// What a bar writes onto its docked title, and whether it draws one.
struct TdayBarTitleReserve: Equatable {
    /// Left inset, for the docked title's full-width box.
    let leading: CGFloat
    /// Right inset, for the same box.
    let trailing: CGFloat
    /// False when the bar's own controls have eaten the row: draw no title.
    let hasRoom: Bool
}

/// Mirror the WIDER side on both sides while the title actually fits in what that
/// leaves; otherwise reserve only what each side really holds.
///
/// The mirrored branch is first and stays first: it keeps the docked title centred
/// on the BAR rather than on the leftovers, and that is load-bearing for the
/// handoff every one of these bars plays — the block's own copy of the title is
/// centred on the screen, so a docked copy centred on the leftovers instead would
/// slide sideways as the two cross-fade.
///
/// The bug this function was ported to fix lives in one term. A gate reading
/// `barWidth - symmetric * 2 >= minWidth` asks whether a STUMP would fit, not
/// whether the TITLE would — two different questions, and the second is the one
/// the branch is choosing on behalf of. Android ran the identical rule and it
/// produced a reported bug; the web bar had it worse, because its Calendar "Today"
/// control is a text pill rather than a collapsing circle; this client had no gate
/// at all, which is the same bug with the evidence hidden behind a scale factor.
///
/// Both terms of the `max` are load-bearing and neither subsumes the other. The
/// title term is the fix. The ``TdayBarTitleReserveMetrics/minWidth`` term is the
/// OLD gate, kept: without it a title narrower than the floor — or one not yet
/// measured — would hold the mirrored branch on a bar with no room for any title
/// at all, and `hasRoom` below would then answer against a reserve chosen for the
/// wrong reason. Keeping it means this step can only ever widen what the title
/// gets, never narrow it, which is what makes the change safe for the screens that
/// were already fitting.
///
/// Why the change cannot cost any bar width, which is what makes it safe. Per-side
/// leaves `W - L - T - 2g` and mirrored leaves `W - 2·max(L,T) - 2g`, so per-side
/// beats mirrored by exactly `|T - L|` — never negative. This function only ever
/// moves a bar from mirrored to per-side, never the other way, so the room it
/// hands back is greater than or equal to the room the old rule handed back, for
/// every bar, at every width, whatever the device measures. Web's suite pins that
/// as a property rather than as a handful of examples, and
/// `TdayBarTitleReserveTests` pins it here.
///
/// Deliberately NOT ported from Android in this pass: `tdayBarTitleScale`, the
/// bounded shrink that runs after this. This client already has it, as
/// `.minimumScaleFactor(0.72)` on the docked title — Android's constant was taken
/// FROM this bar — and it stays exactly where it is, as the backstop below the
/// floor. Web chose not to port it and ellipsizes instead.
///
/// @param titleWidth what the title wants at the full docked size. Zero when it
///   has not been measured yet — and zero fits everything, so the first frame
///   takes the mirrored branch exactly as this bar always did, then settles onto
///   the real answer when the measurement lands. That is what makes the
///   measurement safe to be a frame late.
func tdayBarTitleReserve(
    barWidth: CGFloat,
    leading: CGFloat,
    trailing: CGFloat,
    titleWidth: CGFloat = 0
) -> TdayBarTitleReserve {
    // Read out rather than held as `let m = TdayBarTitleReserveMetrics`: this is
    // an enum with no cases, so it has no value to hold and the binding would not
    // compile. The other two clients can write it because theirs is an object and
    // a `const`, which is the one place this rule's three copies differ in shape.
    let sideGap = TdayBarTitleReserveMetrics.sideGap
    let minWidth = TdayBarTitleReserveMetrics.minWidth

    // Before the bar has a box — the very first frame, or a bar that is not
    // displayed — reserve what is actually there: never centred, but never
    // overlapping either, and replaced on the very next frame. Answering
    // `hasRoom` here is moot in practice, since a bar with no rect has no
    // collapse progress either and the title is at zero opacity regardless; it
    // matches Android's and web's degenerate case so the three functions stay
    // readable against each other.
    if barWidth <= 0 {
        return TdayBarTitleReserve(leading: leading, trailing: trailing, hasRoom: true)
    }

    let symmetric = max(leading, trailing) + sideGap
    let centred = barWidth - symmetric * 2 >= max(titleWidth, minWidth)
    let leadingReserve = centred ? symmetric : leading + sideGap
    let trailingReserve = centred ? symmetric : trailing + sideGap
    return TdayBarTitleReserve(
        leading: leadingReserve,
        trailing: trailingReserve,
        // The step that refuses rather than lets a title paint across the
        // controls: at this width there is nothing left to shrink into, and a
        // title at a third of the size it is handing off from is a different
        // piece of text arriving, not the same one.
        hasRoom: barWidth - leadingReserve - trailingReserve >= minWidth
    )
}

/// What a bar's docked title wants at the size it is drawn, reported by a hidden
/// copy of it.
struct TdayDockedTitleWidthKey: PreferenceKey {
    static var defaultValue: CGFloat = 0

    static func reduce(value: inout CGFloat, nextValue: () -> CGFloat) {
        value = max(value, nextValue())
    }
}

/// The width of the box the title is laid out in — the bar's own inner width, and
/// the space every reserve above is a fraction of.
struct TdayBarWidthKey: PreferenceKey {
    static var defaultValue: CGFloat = 0

    static func reduce(value: inout CGFloat, nextValue: () -> CGFloat) {
        value = max(value, nextValue())
    }
}

extension View {
    /// Reports the two numbers the reserve is a function of — the bar's inner
    /// width, and what the title it will dock wants at the size it is drawn —
    /// without moving either.
    ///
    /// Attached as a `background`, so it can add no width, no height and no layout
    /// of its own. The copy is `fixedSize`, so it answers with the title's natural
    /// width rather than with the width the bar happens to leave it — the visible
    /// title is the thing being sized, so its own width is the answer rather than
    /// the question — and it is `hidden`, so it is drawn nowhere and read by
    /// nobody. `RootFeedHeroHeader` measures its own docked title with the same
    /// two modifiers on a `Text` in the same position; the only difference is that
    /// this one copies the string instead of measuring the visible one.
    ///
    /// A measured width rather than a `ViewThatFits` pair, which would carry no
    /// number at all: the rule the three clients share is arithmetic, and
    /// arithmetic that nothing can read is arithmetic nothing can pin.
    func tdayMeasuresDockedTitle(barTitle: String, font: Font) -> some View {
        background {
            GeometryReader { bar in
                Text(barTitle)
                    .font(font)
                    .lineLimit(1)
                    .fixedSize()
                    .hidden()
                    .background {
                        GeometryReader { title in
                            Color.clear
                                .preference(
                                    key: TdayDockedTitleWidthKey.self,
                                    value: title.size.width
                                )
                        }
                    }
                    .preference(key: TdayBarWidthKey.self, value: bar.size.width)
            }
        }
    }
}
