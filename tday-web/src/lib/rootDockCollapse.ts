/**
 * When the root feed's dock is the whole control and when it is one tab.
 *
 * Dimensions and a decision, not motion: two scroll offsets and the rule that
 * reads them. Nothing here is a duration, a curve or a spring, so none of it
 * belongs in `MotionTokens.kt` — a fold point three clients share is a place,
 * not a length of time. What the collapse LOOKS like is `RootDock.tsx`'s
 * business and is on the ladder; where it happens is this file's.
 */

/**
 * The fold point. Past this much scroll the dock gives up everything but the
 * tab you are on.
 *
 * The same number on all three clients, spelled in each one's own unit:
 * `RootFeedDockCollapse.CollapseThreshold = 44.dp` at
 * `android-compose/app/src/main/java/com/ohmz/tday/compose/ui/component/RootFeedDock.kt:122`
 * — one declaration, read by both Android feeds, where this comment used to cite
 * a copy of the literal in each of them — and `rootDockCollapseThreshold: CGFloat = 44` at
 * `ios-swiftUI/Tday/Feature/Todos/TodoListScreen.swift:126` and
 * `.../ScheduledTaskHome/ScheduledTaskHomeScreen.swift:12`. Moving it here
 * moves one client's dock away from the other two, which is worse than
 * whatever the move was for: this is the one control on every root feed, and a
 * user who has the app twice has one expectation of it.
 */
export const ROOT_DOCK_COLLAPSE_PX = 44;

/**
 * The release edge, and the reason there are two numbers rather than one.
 *
 * A single threshold flips on the boundary pixel. A finger resting just past
 * the fold — which is where a finger ends up, since 44px is barely into the
 * feed and is the first thing a thumb drags through — carries the offset back
 * and forth across it once a frame, and the dock strobes between its two
 * shapes for as long as the finger sits there. The two shapes differ by every
 * tab but one, so the strobe is the width of the capsule, not a detail of it.
 *
 * 20px of dead band is wide enough to swallow that jitter and narrow enough
 * that a deliberate scroll back to the top expands the dock well before the
 * feed's own top arrives — the control is waiting when the user gets there
 * rather than opening in front of them.
 */
export const ROOT_DOCK_EXPAND_PX = 24;

/**
 * The next collapsed state, given the current one and where the feed is.
 *
 * A fold over the previous answer rather than a threshold test, because that is
 * what hysteresis is: at offset 30 the question "is the dock collapsed" has no
 * answer that does not include what it was at offset 29.
 *
 * A scroller rubber-banding past its own top reads negative, and one that has
 * not laid out yet reads `NaN`. Both mean the feed is at its top, and both get
 * that answer from the comparisons themselves: every edge above is positive, so
 * a negative offset and `NaN` alike lose whichever comparison they are put to.
 * A `Math.max(offsetPx, 0)` in front of them cannot change an answer this
 * function returns — it was here, and it never could have. iOS does need its
 * own at `ios-swiftUI/Tday/Core/UI/RootFeedHeroHeader.swift:754`, but that one
 * publishes the offset itself rather than a side of a threshold, and a bounce
 * published as a negative number moves a header.
 */
export function nextRootDockCollapsed(previous: boolean, offsetPx: number): boolean {
  return previous ? offsetPx > ROOT_DOCK_EXPAND_PX : offsetPx > ROOT_DOCK_COLLAPSE_PX;
}
