import { describe, expect, it } from "vitest";
import {
  TODAY_EARLIER_EXIT_MS,
  shouldShowTodayEmptyIllustration,
} from "@/features/todayTodos/lib/todayEarlierIllustration";

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
        for (const earlierHandoffPending of [false, true]) {
          for (const celebrate of [false, true]) {
            expect(
              shouldShowTodayEmptyIllustration({
                showEmpty: false,
                hasEarlierItems,
                earlierExpanded,
                earlierHandoffPending,
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
        earlierHandoffPending: false,
        celebrate: false,
      }),
    ).toBe(true);
    expect(
      shouldShowTodayEmptyIllustration({
        showEmpty: true,
        hasEarlierItems: false,
        earlierExpanded: false,
        earlierHandoffPending: false,
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
          earlierHandoffPending: false,
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
          earlierHandoffPending: false,
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
          earlierHandoffPending: true,
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
        earlierHandoffPending: false,
      };
      expect(shouldShowTodayEmptyIllustration({ ...base, celebrate: true })).toBe(true);
      expect(shouldShowTodayEmptyIllustration({ ...base, celebrate: false })).toBe(false);
    });
  });

  describe("requirement 2: the illustration shows whenever Earlier is collapsed, celebration or not", () => {
    it("zero pending, Earlier has tasks, collapsed, no recent completion — plain scene still shows", () => {
      expect(
        shouldShowTodayEmptyIllustration({
          showEmpty: true,
          hasEarlierItems: true,
          earlierExpanded: false,
          earlierHandoffPending: false,
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
          earlierHandoffPending: false,
          celebrate: false,
        }),
      ).toBe(false);
    });

    it("mid hand-off always wins over the expanded flag — still on screen until the timer fires", () => {
      // `earlierExpanded` is still false at this instant in the real
      // component (see useEarlierExpandHandoff: `expanded` only flips once
      // the timer fires), but this asserts the precedence explicitly even if
      // a future caller passed a stale `true`.
      expect(
        shouldShowTodayEmptyIllustration({
          showEmpty: true,
          hasEarlierItems: true,
          earlierExpanded: true,
          earlierHandoffPending: true,
          celebrate: false,
        }),
      ).toBe(true);
    });
  });
});

describe("TODAY_EARLIER_EXIT_MS", () => {
  it("matches the illustration's own CSS arrival duration (.tday-empty-enter, globals.css)", () => {
    // Not a coincidence: the exit is designed to mirror the entrance. This
    // pins the value so a future edit to one side is caught rather than
    // silently drifting from the other.
    expect(TODAY_EARLIER_EXIT_MS).toBe(520);
  });
});
