import type { ReactNode } from "react";
import { cn } from "@/lib/utils";

/**
 * The SLOT an empty scene sits in, as opposed to the ink on it.
 *
 * `EmptyState` is 42vh of the page. Both of the ways it can leave want that
 * height given back over the same beat the ink fades on, rather than dropped in
 * the frame the node goes away — which is the single largest jump either
 * departure can make, and the reason `.tday-empty-slot` exists at all (see its
 * own comment in globals.css for why a one-row grid is the only interpolable
 * spelling of "as tall as whatever is inside it").
 *
 * Lifted out of `TimelineEmptyState` when the two Anytime feeds needed the same
 * thing. They draw `EmptyState` directly — no Earlier bucket, so no hand-off and
 * nothing for that component's other half to do — but they cut the confetti
 * exactly the way the scoped screens used to, because the scene's mount guard
 * there is the emptiness test itself. Three copies of a two-class wrapper is how
 * the fourth gets one of the classes and not the other; this is one spelling.
 *
 * The inner box is not decoration and must not be flattened away: a grid item
 * that is not a scroll container keeps its own content height as the track's
 * floor, so the track has nothing to shrink to without a child of its own to
 * take the clip. Both closing classes scope `overflow: hidden` to it rather than
 * leaving it on, which would clip the resting scene.
 *
 * @param handingOffToEarlier the scene is giving this slot to Earlier's own rows
 *   while the scope is still EMPTY (`emptySceneIsLeaving`). Two class names and
 *   one question — the stylesheet wants them apart because the ink is an
 *   animation and the track is a transition on a grid, but they go on together
 *   or not at all.
 * @param leavingOnCancel the scope REFILLED under the scene
 *   (`emptySceneLeavesOnCancel`): an undo put the completed row back, or a task
 *   arrived. The ink and the track leave together on `Quick`, the same rung the
 *   paper inside is fading on, so scene and confetti go as one thing. Never true
 *   at the same time as the hand-off above — see `emptySceneLeavesOnCancel` for
 *   why those are two departures and not one.
 */
export default function EmptyStateSlot({
  handingOffToEarlier = false,
  leavingOnCancel = false,
  children,
}: {
  handingOffToEarlier?: boolean;
  leavingOnCancel?: boolean;
  children: ReactNode;
}) {
  return (
    <div
      className={cn(
        "tday-empty-slot",
        handingOffToEarlier && "tday-empty-exit",
        handingOffToEarlier && "tday-empty-slot-closing",
        leavingOnCancel && "tday-empty-cancel-exit",
      )}
    >
      <div>{children}</div>
    </div>
  );
}
