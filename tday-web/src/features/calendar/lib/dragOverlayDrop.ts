import type { DropAnimation } from "@dnd-kit/core";
import { DURATION_MS, EASE } from "@/lib/motion";

/**
 * How the calendar's drag overlay lands when the finger lets go.
 *
 * `dropAnimation={null}` is not "no animation", it is "no landing": dnd-kit
 * unmounts the overlay on the frame of the release, so a card the user has been
 * carrying across the month grid stops existing mid-air. Everything else the
 * drag does is continuous — the card lifts under the finger, a day cell rings
 * as it passes — and then the one moment the user is actually waiting on, the
 * moment that says the app took the task, is a cut.
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
export function calendarDropAnimation(reduceMotion: boolean): DropAnimation | null {
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
