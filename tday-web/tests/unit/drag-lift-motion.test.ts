/**
 * Picking a task up, and putting it down.
 *
 * Three screens drag a task and all three used to do it the same two wrong ways.
 * The card appeared at full size with its shadow already cast, so a pick-up had
 * no moment of picking up; and `dropAnimation={null}` is not "no animation" but
 * "no landing" — dnd-kit removes the overlay on the frame of the release, so a
 * card carried across a month grid stops existing in mid-air. Everything either
 * side of those two moments is continuous, and they were the two cuts.
 *
 * Nothing that renders these screens can see either defect. dnd-kit runs the
 * drop through `Element.animate`, which jsdom does not implement, and the lift
 * is a stylesheet jsdom does not load; the settled DOM is identical either way.
 * What can be checked is the decision — that both ends exist, that they are
 * spelled with the vocabulary rather than with numbers, and that the preference
 * removes the trip and keeps the destination at each end.
 *
 * The lift half is therefore a static read of `globals.css` and of the three
 * overlays, the same way `motion-reachability-web.test.ts` reads them: the
 * defect is in what the source declares, not in what a render produces. Where a
 * class is exported rather than written inline it is read as the value and not
 * as the text — a source regex only ever sees one of the literals a class string
 * happens to be spelled across.
 * `calendar-client-motion-wiring.test.tsx` closes the remaining gap — a correct
 * config that no overlay asks for — by reading the prop off the real screen.
 */

import { readFileSync } from "fs";
import path from "path";
import { describe, expect, it } from "vitest";
import { DURATION_MS, EASE } from "@/lib/motion";
import {
  DRAG_LIFT_CLASS,
  DRAG_VACATED_TRANSITION,
  dragOverlayDropAnimation,
} from "@/lib/dragLiftMotion";
import { overlayCardClass } from "@/components/todo/dnd/timelineDndClasses";

const SRC = path.resolve(__dirname, "..", "..", "src");
const read = (...parts: string[]) => readFileSync(path.join(SRC, ...parts), "utf-8");

const GLOBALS = read("globals.css");

/** The `.tday-drag-lift { … }` rule body, without the `@media` copy below it. */
const liftRule = GLOBALS.match(/\n\.tday-drag-lift \{([^}]*)\}/)?.[1] ?? "";
/** The `@keyframes tday-drag-lift { … }` body. */
const liftKeyframes = GLOBALS.match(/@keyframes tday-drag-lift \{([\s\S]*?)\n\}/)?.[1] ?? "";

describe("a dragged card lifts", () => {
  it("declares the lifted state on the class and the resting state in the keyframe", () => {
    // This is the whole reason the rule is written round this way rather than as
    // a `to`. The fifth idiom rule says a surface that cannot animate must still
    // draw its FINISHED state, and the finished state of a lift is a card that
    // is up. Put the scale in a `to` and a reduced-motion reader gets a card
    // sitting flat in the list while the finger carries nothing.
    expect(liftRule).toMatch(/scale:\s*calc\(2 - var\(--tday-press-card\)\)/);
    expect(liftKeyframes).toMatch(/from\s*\{/);
    expect(liftKeyframes).toMatch(/scale:\s*1;/);
    expect(liftKeyframes).not.toMatch(/\bto\s*\{/);
  });

  it("rises by the press scale read backwards rather than by a number of its own", () => {
    // `--tday-press-card` is how far a card sinks under a finger; a lift is the
    // same card going the other way under the same finger. Asserted as the
    // token's name and not as 1.03, so this says "the call site derives it" and
    // not "the token is still what it was" — the trap
    // `EarlierIllustrationMotionTest.kt` names and `motion-parity.test.ts`
    // inherits. `docs/motion.md` already counts nine press literals across the
    // three clients; a tenth is not what this unit is for.
    expect(liftRule).toContain("--tday-press-card");
    expect(liftRule).not.toMatch(/scale:\s*1\.\d/);
  });

  it("takes Emphasis on the Enter curve, both named as tokens", () => {
    // Emphasis by the geometry rule: the card changes how big it is. Enter
    // because it is arriving under a finger and should settle rather than stop.
    expect(liftRule).toContain("var(--tday-duration-emphasis)");
    expect(liftRule).toContain("var(--tday-ease-enter)");
  });

  it("keeps the card up when the platform asks for no motion", () => {
    const reduced = GLOBALS.slice(GLOBALS.indexOf(".tday-drag-lift"))
      .match(/@media \(prefers-reduced-motion: reduce\) \{([\s\S]*?)\n\}/)?.[1] ?? "";
    expect(reduced).toMatch(/\.tday-drag-lift \{ animation: none; \}/);
    // The trip goes and nothing else: a rule that also unset the scale here
    // would be removing the destination, which is the rule read backwards.
    expect(reduced).not.toContain("scale:");
  });

  it("is asked for by all three overlays, and none of them fades the card", () => {
    // A card the finger is holding at 70% reads as disabled. Transparency on
    // these screens is the vacated row's word and stays there; see
    // `timelineDndClasses.ts`, which argues the swap where it made it.
    //
    // Asserted against the RESOLVED class and not against the file's text. The
    // shared class is spelled across two string literals, so a regex anchored on
    // `pointer-events-none` and bounded by the closing quote could only ever see
    // the first of them — `opacity-70` put back in the second half passed, which
    // is a check that cannot fail for two of the three overlays. Reading the
    // exported value is immune to how the string is spelled and covers both dnd
    // contexts at once.
    expect(overlayCardClass).toContain(DRAG_LIFT_CLASS);
    expect(overlayCardClass).not.toMatch(/\bopacity-\d/);

    // The calendar builds its own card inline, so it is read where it is drawn —
    // the whole `<DragOverlay>` block rather than one quoted literal inside it.
    const overlayJsx = read("features", "calendar", "component", "CalendarClient.tsx")
      .match(/<DragOverlay[\s\S]*?<\/DragOverlay>/)?.[0] ?? "";
    expect(overlayJsx).toContain("activeTodo");
    expect(overlayJsx).toContain("DRAG_LIFT_CLASS");
    expect(overlayJsx).not.toMatch(/\bopacity-\d/);

    // The two `@dnd-kit` contexts share one card class, so naming it once is
    // naming it for both; the calendar draws its own and is checked above.
    for (const context of ["TimelineDndContext.tsx", "TodayBucketDnd.tsx"]) {
      expect(read("components", "todo", "dnd", context)).toContain("overlayCardClass");
    }
    expect(DRAG_LIFT_CLASS).toBe("tday-drag-lift");
  });

  it("empties the row it came out of on the same rung, rather than cutting", () => {
    // The other half of one gesture. Android moved the vacated row onto the
    // rise's own spec in the same unit; web left it a hard toggle, so a long
    // press was still a card rising beside a row that blinked. Same rung, same
    // curve, both named rather than spelled out.
    expect(DRAG_VACATED_TRANSITION).toContain("var(--tday-duration-emphasis)");
    expect(DRAG_VACATED_TRANSITION).toContain("var(--tday-ease-enter)");
    expect(DRAG_VACATED_TRANSITION).toMatch(/^opacity /);

    // And it has to ride the inline style. dnd-kit writes its own `transform`
    // shorthand into the timeline row's `style` for the whole drag, and an
    // inline shorthand outranks any utility the row could carry — so a row that
    // reached for `transition-opacity` here would be declaring a transition that
    // never runs. Both rows spend the constant; neither spells a utility.
    for (const row of [
      read("components", "todo", "component", "TodoItemContainer.tsx"),
      read("features", "calendar", "component", "CalendarClient.tsx"),
    ]) {
      expect(row).toContain("DRAG_VACATED_TRANSITION");
      expect(row).not.toMatch(/transition-opacity duration-/);
    }
  });
});

describe("a dragged card lands", () => {
  it("gives the drop a duration and a curve instead of a cut", () => {
    const drop = dragOverlayDropAnimation(false);

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
    expect(Object.keys(dragOverlayDropAnimation(false) ?? {}).sort()).toEqual([
      "duration",
      "easing",
    ]);
  });

  it("hands reduced motion the finished state, which is the overlay gone", () => {
    // The fifth idiom rule: the trip goes, the destination stays. A drop's
    // destination is the overlay removed and the task on its new day, which is
    // exactly what dnd-kit draws when there is no drop animation — the rare case
    // where the preference and the library's own switch want the same thing.
    expect(dragOverlayDropAnimation(true)).toBeNull();
  });

  it("is one decision spent three times, not three decisions", () => {
    // The reason this module left `features/calendar/lib`. Two of these screens
    // had `dropAnimation={null}` while the third had a landing, which is the
    // shape a per-screen decision leaves behind.
    for (const source of [
      read("components", "todo", "dnd", "TimelineDndContext.tsx"),
      read("components", "todo", "dnd", "TodayBucketDnd.tsx"),
      read("features", "calendar", "component", "CalendarClient.tsx"),
    ]) {
      expect(source).toContain("dropAnimation={dragOverlayDropAnimation(reduceMotion)}");
      expect(source).not.toContain("dropAnimation={null}");
    }
  });
});
