import CoreGraphics
import UIKit

/// The iOS reading of the shared layout tokens: where a titled screen's content
/// starts under its hero title, and how tall the slot an empty scene sits in is.
///
/// `TdayLayoutGenerated` is written by `:shared:exportLayoutTokens` from
/// `LayoutTokens.kt`; Android reads the Kotlin object directly and the web has its
/// own generated copy. All three spend the same ratios of the SCREEN height, so the
/// first row — and the scene that stands in for it — lands at the same height on
/// every client. A ratio is what lets that hold while the fixed numbers beneath it
/// (this client's 56pt title box against Android's 40, the safe-area inset a
/// Dynamic Island phone carries) legitimately differ.
///
/// The rule is `LayoutTokens.contentGap`, ported: measure where the title ends and
/// spend exactly what reaches the shared line, held between a floor (the fade band
/// plus 6, so the first row rests BELOW the band that dissolves rows under the bar)
/// and a cap (so a tall phone does not strand its title). On the 874pt phone this
/// client was drawn on the floor is what binds, which is why iOS does not move
/// there and the other two clients come to it.
///
/// Pinned by `TdayLayoutTests` against the same cases as the Kotlin and web twins.
enum TdayLayout {

    /// The gap to leave under a hero title so the content starts on the shared line.
    ///
    /// - Parameters:
    ///   - screenHeight: the full screen height, status bar included.
    ///   - titleBottom: the distance from the top of the screen to the bottom of the
    ///     title block: the safe-area inset plus the pinned bar plus the hero block.
    static func contentGap(screenHeight: CGFloat, titleBottom: CGFloat) -> CGFloat {
        let wanted = TdayLayoutGenerated.contentStartRatio * screenHeight - titleBottom
        return min(
            TdayLayoutGenerated.contentGapMax,
            max(TdayLayoutGenerated.contentGapMin, wanted)
        )
    }

    /// The empty-scene slot's minimum height for a screen of `screenHeight`.
    static func emptySlotHeight(screenHeight: CGFloat) -> CGFloat {
        TdayLayoutGenerated.emptySlotRatio * screenHeight
    }

    // MARK: - Reading this device

    private static var screenHeight: CGFloat {
        UIScreen.main.bounds.height
    }

    /// The top safe-area inset of the key window — the status bar, or the Dynamic
    /// Island's room. Zero before a window exists, which only a preview or a test
    /// ever sees, and the floor holds there.
    private static var topSafeAreaInset: CGFloat {
        UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap(\.windows)
            .first(where: \.isKeyWindow)?
            .safeAreaInsets.top ?? 0
    }

    /// The gap under a hero title block on this device.
    static func heroContentGap() -> CGFloat {
        contentGap(
            screenHeight: screenHeight,
            titleBottom: topSafeAreaInset
                + TodoTimelineMetrics.topBarRowHeight
                + TodoTimelineMetrics.titleCollapseDistance
        )
    }

    /// The empty-scene slot's minimum height on this device.
    static func emptySlotHeight() -> CGFloat {
        emptySlotHeight(screenHeight: screenHeight)
    }
}
