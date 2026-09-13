/**
 * The calendar's drag overlay used to be handed `dropAnimation={null}`, which is
 * not "no animation" but "no landing": dnd-kit removes the overlay on the frame
 * of the release, so a card carried across the month grid stops existing in
 * mid-air. Everything either side of that moment is continuous — the card lifts
 * under the finger, day cells ring as it passes, the task appears on its new
 * day — and the one frame the user is actually waiting on was a cut.
 *
 * Nothing that renders this screen can see the defect: dnd-kit runs the drop
 * through `Element.animate`, which jsdom does not implement, and the settled DOM
 * is identical either way. What can be checked is the decision — that a drop
 * animation exists at all, that it is spelled with the vocabulary rather than
 * with a number, and that the preference turns it off rather than shortening it.
 *
 * That leaves the config right and the overlay possibly not asking for it, which
 * is a green suite and a cut drop. `calendar-client-motion-wiring.test.tsx`
 * closes that half by reading the prop off the overlay the screen renders.
 */

import { describe, expect, it } from "vitest";
import { DURATION_MS, EASE } from "@/lib/motion";
import { calendarDropAnimation } from "@/features/calendar/lib/dragOverlayDrop";

describe("the calendar's drag overlay lands", () => {
  it("gives the drop a duration and a curve instead of a cut", () => {
    const drop = calendarDropAnimation(false);

    expect(drop).not.toBeNull();
    // Emphasis because the overlay travels: the geometry rule in
    // `docs/motion.md`, not a judgement about how important a drop is. Read off
    // the rung rather than compared to 320, so that the assertion is "this call
    // site names the token" and not "the token is still what it was".
    expect(drop).toMatchObject({ duration: DURATION_MS.emphasis, easing: EASE.enter });
  });

  it("leaves dnd-kit's own keyframes and side effects alone", () => {
    // The library fills these in from `defaultDropAnimationConfiguration`. Naming
    // them here would be adopting a default as a decision — and the two this app
    // does have an opinion about are the two above.
    expect(Object.keys(calendarDropAnimation(false) ?? {}).sort()).toEqual(["duration", "easing"]);
  });

  it("hands reduced motion the finished state, which is the overlay gone", () => {
    // The fifth idiom rule: the trip goes, the destination stays. A drop's
    // destination is the overlay removed and the task on its new day, which is
    // exactly what dnd-kit draws when there is no drop animation — the rare case
    // where the preference and the library's own switch want the same thing.
    expect(calendarDropAnimation(true)).toBeNull();
  });
});
