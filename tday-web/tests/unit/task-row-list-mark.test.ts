import { describe, expect, it } from "vitest";
import { shouldShowListMark } from "@/lib/listMark";

/**
 * Where the per-row list mark is drawn, asserted without a screen.
 *
 * There is no browser here, so "the mark is gone from the list detail" is not a claim
 * a test in this repo can make by looking. What it can do is what the rest of the tree
 * does with unprovable-locally claims: pull the decision out into a pure function and
 * pin the function. The rule below IS the rendering condition — `TodoItemCard` and
 * `FloaterItemContainer` both call it and hold no second opinion — so a table over its
 * inputs is a table over what those rows draw.
 *
 * Transcribed case for case from Android's `TaskRowListMarkTest` and iOS's
 * `TaskRowListMarkTests`, because the three clients are supposed to be answering one
 * question. Nothing generates these three files, so the tests asking the same things
 * is the only thing that would catch one of them drifting.
 *
 * There was no test for this condition on any client before: on the native two it was
 * a `when` over the screen's mode, which is how it could go on answering "yes" for two
 * different screens that share a mode; here it was a bare truthiness check on the row's
 * list id, which cannot answer anything about the screen at all.
 */
describe("shouldShowListMark", () => {
  const groceries = "list-groceries";
  const work = "list-work";

  it("marks every row that has a list on a mixed feed", () => {
    // Today, Overdue, All, Priority, the calendar, Completed and the Anytime home feed
    // all arrive here as "no scope": the row could have come from anywhere, so the mark
    // is the only thing on it that says where.
    expect(shouldShowListMark(groceries, null)).toBe(true);
    expect(shouldShowListMark(work, null)).toBe(true);
    expect(shouldShowListMark(groceries, undefined)).toBe(true);
    // A blank id is the same statement as a missing one. Ids reach this from query
    // params and cached rows, and "" versus null has never meant anything to anybody.
    expect(shouldShowListMark(groceries, "")).toBe(true);
    expect(shouldShowListMark(groceries, "   ")).toBe(true);
  });

  it("does not repeat a list detail's own name on every row", () => {
    // The reported defect. On this client the scheduled list detail is where it bites:
    // `/api/list/:id` returns `ListTodoDto`, which stamps each row with the endpoint's
    // own list id, so every row printed the heading back beside its title.
    expect(shouldShowListMark(groceries, groceries)).toBe(false);
  });

  it("keeps the mark on a row from another list, even on a scoped screen", () => {
    // The case that must NOT be swept up with the redundant ones: for the frame between
    // a task being moved out of this list and the query catching up, the row is the one
    // thing on screen that disagrees with the title — and the mark is how a reader can
    // tell that is what they are looking at.
    expect(shouldShowListMark(work, groceries)).toBe(true);
  });

  it("draws nothing for a task that is in no list", () => {
    // Unfiled Anytime tasks share the Anytime home feed with listed ones. There is no
    // list, so there is no glyph and no name — drawing the default inbox here would
    // invent a list the task is not in.
    expect(shouldShowListMark(null, null)).toBe(false);
    expect(shouldShowListMark(undefined, null)).toBe(false);
    expect(shouldShowListMark("", groceries)).toBe(false);
    expect(shouldShowListMark("   ", null)).toBe(false);
  });
});
