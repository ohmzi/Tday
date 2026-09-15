import { describe, expect, it } from "vitest";
import {
  OVERDUE_ROWS_FADE_MS,
  TODAY_EARLIER_EXIT_MS,
  earlierIsExpanding,
  earlierSlotChangesHands,
  emptySceneIsLeaving,
  emptySceneLeavesOnCancel,
  shouldShowTodayEmptyIllustration,
} from "@/features/todayTodos/lib/todayEarlierIllustration";
import type { EarlierHandoff } from "@/features/todayTodos/lib/useEarlierExpandHandoff";

/** Every state the swap can be in, for the exhaustive loops below. */
const HANDOFFS: EarlierHandoff[] = ["idle", "scene-leaving", "rows-leaving"];

/**
 * Requirement 1's regression coverage: adding Today's "Earlier" bucket must
 * not change when the empty-state illustration/confetti pair shows for a day
 * with zero pending tasks. `shouldShowTodayEmptyIllustration` is the single
 * function `AllTasksTimelineContainer.tsx` now calls to decide that (see its
 * `showEmptyIllustration` line) — testing it directly exercises the real
 * production decision, not a re-implementation of it.
 *
 * `celebrate` here always represents `taskJustCompleted() || remoteEmptied`
 * — a signal computed entirely upstream of, and independent from, anything
 * about Earlier (see the container's own `celebrate` line). These tests fix
 * that signal and vary only Earlier's own state, to show the illustration
 * (and therefore the `celebrate` prop `AllTasksTimelineContainer` passes
 * straight through to it) responds to Earlier's state changing WHO renders
 * the slot, never to WHETHER a completion just happened.
 */
describe("shouldShowTodayEmptyIllustration", () => {
  it("never shows once there are pending tasks (showEmpty false), regardless of Earlier", () => {
    for (const hasEarlierItems of [false, true]) {
      for (const earlierExpanded of [false, true]) {
        for (const earlierHandoff of HANDOFFS) {
          for (const celebrate of [false, true]) {
            expect(
              shouldShowTodayEmptyIllustration({
                showEmpty: false,
                hasEarlierItems,
                earlierExpanded,
                earlierHandoff,
                celebrate,
              }),
            ).toBe(false);
          }
        }
      }
    }
  });

  it("baseline (no Earlier bucket at all): unchanged from before this feature existed", () => {
    // This is exactly the pre-existing PR #152 finding: an empty Today with no
    // overdue tasks shows the plain scene, and a just-completed last task
    // shows it celebrating — both untouched by this feature.
    expect(
      shouldShowTodayEmptyIllustration({
        showEmpty: true,
        hasEarlierItems: false,
        earlierExpanded: false,
        earlierHandoff: "idle",
        celebrate: false,
      }),
    ).toBe(true);
    expect(
      shouldShowTodayEmptyIllustration({
        showEmpty: true,
        hasEarlierItems: false,
        earlierExpanded: false,
        earlierHandoff: "idle",
        celebrate: true,
      }),
    ).toBe(true);
  });

  describe("requirement 1: completing the last pending task still celebrates in every Earlier state", () => {
    it("zero pending, Earlier has tasks, collapsed — celebrates", () => {
      expect(
        shouldShowTodayEmptyIllustration({
          showEmpty: true,
          hasEarlierItems: true,
          earlierExpanded: false,
          earlierHandoff: "idle",
          celebrate: true,
        }),
      ).toBe(true);
    });

    it("zero pending, Earlier has tasks, expanded — still celebrates (the illustration reclaims the slot for the celebration window)", () => {
      expect(
        shouldShowTodayEmptyIllustration({
          showEmpty: true,
          hasEarlierItems: true,
          earlierExpanded: true,
          earlierHandoff: "idle",
          celebrate: true,
        }),
      ).toBe(true);
    });

    it("zero pending, Earlier has tasks, mid hand-off (exiting) — still shown (in flight)", () => {
      expect(
        shouldShowTodayEmptyIllustration({
          showEmpty: true,
          hasEarlierItems: true,
          earlierExpanded: false,
          earlierHandoff: "scene-leaving",
          celebrate: true,
        }),
      ).toBe(true);
    });

    it("celebration flag alone flips the outcome, independent of Earlier's own shape", () => {
      // Same "zero pending, Earlier has tasks" setup, only `celebrate` toggles —
      // demonstrating this function's celebration outcome tracks `celebrate`,
      // not `hasEarlierItems`/`earlierExpanded`.
      const base = {
        showEmpty: true,
        hasEarlierItems: true,
        earlierExpanded: true,
        earlierHandoff: "idle",
      };
      expect(shouldShowTodayEmptyIllustration({ ...base, celebrate: true })).toBe(true);
      expect(shouldShowTodayEmptyIllustration({ ...base, celebrate: false })).toBe(false);
    });

    it("holds the scene across the render where the window shuts and the beat is not armed yet", () => {
      // The two lines above are one render apart in production: `celebrate`
      // going false IS the window closing, and the hand-off that plays the
      // departure cannot exist until the commit after it. Answering false in
      // between takes the scene's own node out of the tree, and an exit hung on
      // a node that was just created is not an exit — see
      // `useCelebrationSceneExit`, and the DOM-level test in
      // `celebrate-window-expiry.test.tsx` that holds it to that.
      const base = {
        showEmpty: true,
        hasEarlierItems: true,
        earlierExpanded: true,
        earlierHandoff: "idle" as EarlierHandoff,
        celebrate: false,
      };
      expect(shouldShowTodayEmptyIllustration({ ...base, sceneHeldForExit: true })).toBe(true);
      // And it is a hold, not a second window: it says nothing on its own once
      // the screen has stopped being the shape where the scene was on the slot.
      expect(
        shouldShowTodayEmptyIllustration({ ...base, showEmpty: false, sceneHeldForExit: true }),
      ).toBe(false);
    });
  });

  describe("requirement 2: the illustration shows whenever Earlier is collapsed, celebration or not", () => {
    it("zero pending, Earlier has tasks, collapsed, no recent completion — plain scene still shows", () => {
      expect(
        shouldShowTodayEmptyIllustration({
          showEmpty: true,
          hasEarlierItems: true,
          earlierExpanded: false,
          earlierHandoff: "idle",
          celebrate: false,
        }),
      ).toBe(true);
    });
  });

  describe("requirement 3: Earlier's own rows own the slot once genuinely expanded, outside the celebration window", () => {
    it("zero pending, Earlier has tasks, expanded, not celebrating — illustration hides", () => {
      expect(
        shouldShowTodayEmptyIllustration({
          showEmpty: true,
          hasEarlierItems: true,
          earlierExpanded: true,
          earlierHandoff: "idle",
          celebrate: false,
        }),
      ).toBe(false);
    });

    it("keeps drawing the scene through its own exit — `earlierExpanded` is still false there", () => {
      // The expand beat, in the shape the real hook produces it: `expanded`
      // only flips once the timer fires, so the scene is still the occupant
      // and still draws itself while it leaves.
      expect(
        shouldShowTodayEmptyIllustration({
          showEmpty: true,
          hasEarlierItems: true,
          earlierExpanded: false,
          earlierHandoff: "scene-leaving",
          celebrate: false,
        }),
      ).toBe(true);
    });

    it("keeps drawing the scene through the celebration window's own exit", () => {
      // The other route into the same beat, and the one the flag below cannot
      // cover: the window runs out with Earlier already expanded, so the scene
      // is leaving a slot the rows are about to own while `earlierExpanded` has
      // been true throughout. Without this the scene would be cut on the frame
      // the window shut — which is the defect the beat replaced.
      expect(
        shouldShowTodayEmptyIllustration({
          showEmpty: true,
          hasEarlierItems: true,
          earlierExpanded: true,
          earlierHandoff: "scene-leaving",
          celebrate: false,
        }),
      ).toBe(true);
    });

    it("holds the scene OFF through a collapse, where the same flag is already false", () => {
      // The one state the flag cannot be read for, and the whole of this
      // hand-off's other half: a collapse drops `expanded` on the tap because
      // that is what arms the rows' fade, so reading it alone would put the
      // scene back on the slot in the same frame — 42vh claimed on top of rows
      // still holding their own height, which is the pile-up the beat exists
      // to unpick.
      expect(
        shouldShowTodayEmptyIllustration({
          showEmpty: true,
          hasEarlierItems: true,
          earlierExpanded: false,
          earlierHandoff: "rows-leaving",
          celebrate: false,
        }),
      ).toBe(false);
    });
  });
});

/**
 * Which taps are sequenced at all, which is a different question from who ends
 * up owning the slot: a tap that swaps nothing gets no beat, and the scene it
 * would have played a departure on is left exactly where it is.
 */
describe("earlierSlotChangesHands", () => {
  it("is false on a screen that still has current tasks — there is no scene in the swap", () => {
    for (const hasEarlierItems of [false, true]) {
      for (const celebrate of [false, true]) {
        expect(
          earlierSlotChangesHands({ showEmpty: false, hasEarlierItems, celebrate }),
        ).toBe(false);
      }
    }
  });

  it("is false with no Earlier bucket to trade with", () => {
    expect(
      earlierSlotChangesHands({ showEmpty: true, hasEarlierItems: false, celebrate: false }),
    ).toBe(false);
  });

  it("sequences the ordinary tap: empty scope, Earlier holding rows", () => {
    expect(
      earlierSlotChangesHands({ showEmpty: true, hasEarlierItems: true, celebrate: false }),
    ).toBe(true);
  });

  it("sequences nothing inside the celebration window", () => {
    // The fade-then-snap defect, at its source. The scene stays on the slot for
    // the rest of that window, so a beat here would have played it a departure
    // it was not making — sinking the scene and the confetti still crossing it
    // to nothing, then snapping both back when the class came off.
    expect(
      earlierSlotChangesHands({ showEmpty: true, hasEarlierItems: true, celebrate: true }),
    ).toBe(false);
  });

  it("answers the same question the branch it mirrors answers", () => {
    // The anti-drift assertion, kept from the predicate this replaced.
    // `shouldShowTodayEmptyIllustration`'s expanded branch decides whether the
    // scene survives a tap; this decides whether the tap is sequenced at all.
    // They are the same decision read one beat apart, so a condition added to
    // that branch and not to this one should fail here rather than in front of
    // a user.
    for (const celebrate of [false, true]) {
      const sceneSurvivesTheTap = shouldShowTodayEmptyIllustration({
        showEmpty: true,
        hasEarlierItems: true,
        earlierExpanded: true,
        earlierHandoff: "idle",
        celebrate,
      });
      expect(
        earlierSlotChangesHands({ showEmpty: true, hasEarlierItems: true, celebrate }),
      ).toBe(!sceneSurvivesTheTap);
    }
  });
});

/**
 * The departure itself — ink and track, one question. `timeline-empty-state-slot.test.ts`
 * asserts that both class names come off this one answer; this asserts the answer.
 */
describe("emptySceneIsLeaving", () => {
  it("is true only while the scene is the half that is leaving", () => {
    expect(emptySceneIsLeaving({ earlierHandoff: "scene-leaving" })).toBe(true);
    for (const earlierHandoff of ["idle", "rows-leaving"] as EarlierHandoff[]) {
      expect(emptySceneIsLeaving({ earlierHandoff })).toBe(false);
    }
  });
});

/**
 * The OTHER departure, and the point of it being a separate question: the scene
 * leaving because the scope refilled, not because Earlier is taking the slot.
 *
 * The two can never both be true and they answer to different rungs, so the one
 * thing worth pinning is that this stays deaf to everything the hand-off is
 * about. It sees a scene the container has stopped asking for, still in the tree
 * (`useFadeUnmount`), over a scope that has tasks again — and nothing else.
 */
describe("emptySceneLeavesOnCancel", () => {
  it("is true only for a scene still mounted over a scope that refilled", () => {
    expect(
      emptySceneLeavesOnCancel({
        sceneStillMounted: true,
        showEmptyIllustration: false,
        showEmpty: false,
      }),
    ).toBe(true);
  });

  it("is false while the scope is still empty — that departure is the hand-off's", () => {
    // The scene going off this slot with `showEmpty` still true is Earlier
    // taking it, which has its own longer beat (`TODAY_EARLIER_EXIT_MS`) and its
    // own two class names. Lingering here as well would stack a second
    // departure on a departure.
    expect(
      emptySceneLeavesOnCancel({
        sceneStillMounted: true,
        showEmptyIllustration: false,
        showEmpty: true,
      }),
    ).toBe(false);
  });

  it("is false for a scene the container is still asking for", () => {
    // The overdue path: the row came back into Earlier, the scope still reads as
    // finished, and the scene is not going anywhere. Only the burst's own
    // envelope runs inside it.
    for (const showEmpty of [false, true]) {
      expect(
        emptySceneLeavesOnCancel({
          sceneStillMounted: true,
          showEmptyIllustration: true,
          showEmpty,
        }),
      ).toBe(false);
    }
  });

  it("is false once the node is gone, which is also the reduced-motion answer", () => {
    // `useFadeUnmount` hands back false immediately under that preference —
    // nothing was painted, so no wait may survive in front of the restored row.
    for (const showEmptyIllustration of [false, true]) {
      for (const showEmpty of [false, true]) {
        expect(
          emptySceneLeavesOnCancel({
            sceneStillMounted: false,
            showEmptyIllustration,
            showEmpty,
          }),
        ).toBe(false);
      }
    }
  });
});

/**
 * The same beat the header reads, from the other end. Asserted against the
 * states rather than against `emptySceneIsLeaving` — comparing a derivation
 * back to the thing it derives from would pass whatever both changed to — and
 * the pairing below is what pins that the two stay one answer.
 */
describe("earlierIsExpanding", () => {
  it("is true only while the bucket is on its way open", () => {
    expect(earlierIsExpanding({ earlierHandoff: "scene-leaving" })).toBe(true);
    for (const earlierHandoff of ["idle", "rows-leaving"] as EarlierHandoff[]) {
      expect(earlierIsExpanding({ earlierHandoff })).toBe(false);
    }
  });

  it("answers on every state exactly as the scene's own departure does", () => {
    // One moment with two names: the scene only ever leaves this slot to hand
    // it to Earlier's rows. If a later beat ever separates them, this is where
    // it has to be argued rather than discovered by a chevron pointing the
    // wrong way.
    for (const earlierHandoff of HANDOFFS) {
      expect(earlierIsExpanding({ earlierHandoff })).toBe(
        emptySceneIsLeaving({ earlierHandoff }),
      );
    }
  });
});

describe("TODAY_EARLIER_EXIT_MS", () => {
  it("is the Enter rung, written out rather than read back from its own token", () => {
    // The literal and not `DURATION_MS.enter`: comparing the constant back
    // against the thing that defines it would pass whatever either one changed
    // to — the trap `EarlierIllustrationMotionTest.kt` argues its way out of on
    // Android, and `tests/guardrails/motion-parity.test.ts` re-argues for the
    // whole vocabulary. 200 sits here so that moving this hand-off onto another
    // rung has to be a deliberate edit to a line that says which rung it is on.
    expect(TODAY_EARLIER_EXIT_MS).toBe(200);
  });

  it("is shorter than the arrival it hands the slot to", () => {
    // The rule that picked the rung, kept as an assertion rather than as a
    // comment: Earlier's own rows fade in over OVERDUE_ROWS_FADE_MS, and an
    // exit that outlasts the arrival it is making room for reads as the app
    // hesitating (docs/motion.md, first idiom rule). Retuning either one past
    // the other should fail here rather than in front of a user.
    expect(TODAY_EARLIER_EXIT_MS).toBeLessThan(OVERDUE_ROWS_FADE_MS);
  });
});
