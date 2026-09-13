import type { DropAnimation } from "@dnd-kit/core";
import { DURATION_MS, EASE } from "@/lib/motion";

/**
 * The two ends of picking a task up and putting it down, decided once.
 *
 * Three screens drag a task — the calendar's month grid, the timeline's date
 * sections, the Today screen's time buckets — and each one draws the same card
 * under the finger through dnd-kit's `DragOverlay`. A lift that each of them
 * decided for itself would be three lifts, so the decision lives here and the
 * three call sites spend it.
 *
 * **A lift is elevation and scale, never transparency.** The card that follows
 * the finger is the one thing on the screen the user is holding; fading it is
 * the vocabulary's word for *disabled*, and a held object that reads as disabled
 * is the opposite of the signal. Transparency on these screens already means
 * something else and keeps meaning it: the row left behind in the list dims,
 * because that is the hole the card came out of. The card itself is opaque, sits
 * higher, and is slightly larger than the row it left.
 *
 * How much larger is not a new number. `PRESS_SCALES.card` is how far a card
 * sinks under a finger; a lift is the same card travelling the other way under
 * the same finger, so it rises by the same amount — `calc(2 - var(--tday-press-card))`,
 * written in `globals.css` where [DRAG_LIFT_CLASS] is declared. The alternative
 * was a tenth press literal in a tree `docs/motion.md` already counts nine of.
 *
 * Emphasis at both ends, by the second idiom rule: a card that changes how big
 * it is and then changes where it is has changed geometry twice, and geometry
 * takes the longer rung whichever direction it runs in.
 */

/**
 * The class that lifts the card inside a `DragOverlay`. Declared in
 * `globals.css`, which is where the scale that pairs with `--tday-press-card`
 * and the shadow it casts can both be written as CSS rather than as two
 * inline strings that have to agree.
 */
export const DRAG_LIFT_CLASS = "tday-drag-lift";

/**
 * How a drag overlay lands when the finger lets go.
 *
 * `dropAnimation={null}` is not "no animation", it is "no landing": dnd-kit
 * unmounts the overlay on the frame of the release, so a card the user has been
 * carrying stops existing mid-air. Everything else the drag does is continuous —
 * the card lifts under the finger, a day cell or a bucket rings as it passes —
 * and then the one moment the user is actually waiting on, the moment that says
 * the app took the task, is a cut.
 *
 * Emphasis, by the second idiom rule in `docs/motion.md`: the overlay travels
 * from wherever it was released to the rect the draggable occupies, which is a
 * position change and not an edit replayed in place. The Enter curve, because
 * the card is already moving when the finger releases — a curve that eased in
 * would stop the card dead and start it again, and this one only has to take
 * the speed it already has off.
 *
 * Only the two values this app has an opinion about are named. dnd-kit fills in
 * the rest from `defaultDropAnimationConfiguration`, which is where the
 * keyframes and the side effect that hides the draggable underneath come from;
 * restating either here would be adopting a library default as a decision.
 *
 * @param reduceMotion - Whether the platform asks for reduced motion.
 * @returns The drop animation, or `null` for dnd-kit's unanimated removal.
 */
export function dragOverlayDropAnimation(reduceMotion: boolean): DropAnimation | null {
  // Reduced motion removes the trip, never the destination — and the
  // destination of a drop is the overlay gone and the task on its new day,
  // which is exactly what `null` draws. This is the rare case where the
  // preference and the library's own switch want the same thing.
  if (reduceMotion) return null;

  return {
    duration: DURATION_MS.emphasis,
    easing: EASE.enter,
  };
}
