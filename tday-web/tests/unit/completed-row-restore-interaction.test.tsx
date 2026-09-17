// @vitest-environment jsdom

/**
 * Un-ticking a task on a Completed screen is the check-off played backwards, and it is played on
 * the check-off's own clock: the tick clears, the rule lifts, then the row fades while its box
 * shuts under it. `todo-row-complete-interaction.test.tsx` holds the forward direction on the
 * Today row; this holds the reverse on the two rows that play it, which are the two that were
 * running 280 / 620 / 960 — the same four beats a third slower — until the whole app was put on
 * `@/lib/taskCompletionTiming`.
 *
 * The two rows are asserted through one table rather than one file each. They are twins by
 * construction (`CompletedFloaterItemContainer.tsx` says so in its own header) and the defect
 * worth catching is one of them being changed without the other, which a shared table makes
 * impossible to do quietly.
 *
 * The class the rule comes off with gets its own attention. `.task-unstrike` is not `.task-strike`
 * reversed and cannot be: a completed row is BORN struck, so the strike is a plain
 * `line-through` until the beat the user asked for, and only then does a class whose animation
 * ends transparent take over. Asserting the swap is asserting that the row does not fade a rule
 * in on every item in the list the moment the list mounts.
 */

import type { ComponentType, ReactNode } from "react";
import { act, cleanup, render, screen } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { CompletedFloaterItemType, CompletedTodoItemType } from "@/types";
import {
  TASK_COMPLETION_CHECK_TO_STRIKE_MS,
  TASK_COMPLETION_STRIKE_TO_FADE_MS,
  TASK_COMPLETION_TOTAL_MS,
} from "@/lib/taskCompletionTiming";
import { installReducedMotion } from "../setup/reduced-motion";

const patchMock = vi.fn();

vi.mock("@/lib/api-client", () => ({
  api: { PATCH: (...a: unknown[]) => patchMock(...a), DELETE: vi.fn(), POST: vi.fn() },
}));
vi.mock("@/hooks/use-toast", () => ({ useToast: () => ({ toast: vi.fn() }) }));
// Both rows draw their list mark through a `ListDot`, which reads the list metadata store. The
// mock above has no `GET`, so an unmocked hook would fail its query inside every render here —
// React Query would swallow it and the assertions below would still pass, which is exactly the
// kind of accidental pass `staged-completion-unmount` and a dozen others refuse the same way.
vi.mock("@/components/Sidebar/List/query/get-list-meta", () => ({
  useListMetaData: () => ({ listMetaData: {} }),
}));
vi.mock("@/features/floaterList/query/get-floater-list-meta", () => ({
  useFloaterListMetaData: () => ({ floaterListMetaData: {} }),
}));

import { CompletedTodoItemContainer } from "@/features/completed/component/ItemContainer";
import { CompletedFloaterItemContainer } from "@/features/completed/component/CompletedFloaterItemContainer";

const TODO: CompletedTodoItemType = {
  id: "completed-1",
  originalTodoID: "todo-1",
  title: "Water the plants",
  createdAt: new Date("2026-08-22T09:00:00.000Z"),
  completedAt: new Date("2026-08-22T10:00:00.000Z"),
  priority: "Low",
  due: null,
  userID: "user-1",
  rrule: null,
  instanceDate: null,
} as CompletedTodoItemType;

const FLOATER: CompletedFloaterItemType = {
  id: "completed-floater-1",
  originalFloaterID: "floater-1",
  title: "Call the bank",
  completedAt: new Date("2026-08-22T10:00:00.000Z"),
  priority: "Low",
  daysToComplete: null,
} as CompletedFloaterItemType;

/**
 * The two rows, each paired with the cache key its own un-complete mutation prunes — which is
 * what "gone" means for a Completed row, there being no parent list component in the test to
 * watch it leave one.
 */
const ROWS: {
  label: string;
  cacheKey: string;
  title: string;
  Row: ComponentType;
}[] = [
  {
    label: "the Completed todo row",
    cacheKey: "completedTodo",
    title: TODO.title,
    Row: () => <CompletedTodoItemContainer completedTodoItem={TODO} />,
  },
  {
    label: "the Completed floater row",
    cacheKey: "completedFloater",
    title: FLOATER.title,
    Row: () => <CompletedFloaterItemContainer completedFloaterItem={FLOATER} />,
  },
];

function renderRow(row: (typeof ROWS)[number]) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  queryClient.setQueryData([row.cacheKey], [TODO, FLOATER]);

  function Wrapper({ children }: { children: ReactNode }) {
    return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
  }

  render(<row.Row />, { wrapper: Wrapper });
  return queryClient;
}

/** The row's outer box — the element carrying the grid track that closes. */
function box(): HTMLElement {
  const checkbox = screen.getByRole("checkbox");
  const element = checkbox.closest("div.grid");
  if (!element) throw new Error("the row's collapsing box is not on screen");
  return element as HTMLElement;
}

/** The grid item inside it, which has to be allowed to shrink for the track to close. */
function foreground(): HTMLElement {
  const checkbox = screen.getByRole("checkbox");
  const item = Array.from(box().children).find((child) => child.contains(checkbox));
  if (!item) throw new Error("the row's foreground is not on screen");
  return item as HTMLElement;
}

function remaining(queryClient: QueryClient, cacheKey: string): string[] {
  return (queryClient.getQueryData<{ id: string }[]>([cacheKey]) ?? []).map((item) => item.id);
}

const REAL_MATCH_MEDIA = window.matchMedia;

describe.each(ROWS)("un-ticking $label", (row) => {
  beforeEach(() => {
    patchMock.mockReset();
    patchMock.mockResolvedValue(null);
    vi.useFakeTimers({ shouldAdvanceTime: true });
  });

  afterEach(() => {
    window.matchMedia = REAL_MATCH_MEDIA;
    vi.useRealTimers();
    cleanup();
  });

  it("plays uncheck -> unstrike -> fade on the shared clock, then restores the task", async () => {
    const queryClient = renderRow(row);
    const checkbox = screen.getByRole("checkbox");
    const title = () => screen.getByText(row.title);

    // Born struck, and struck with the bare rule: nothing is animating, because nothing has
    // been asked for yet. This is the frame every other row in a full Completed list is also
    // sitting in, which is why the lift cannot be the strike played backwards.
    expect(title().className).toContain("line-through");
    expect(title().className).not.toContain("task-unstrike");

    await act(async () => {
      checkbox.click();
    });

    // 1. The circle empties at once. The rule is still there — this beat is the tick, and
    //    lifting the rule inside it would collapse two beats into one.
    expect((checkbox as HTMLInputElement).checked).toBe(false);
    expect(title().className).toContain("line-through");
    expect(title().className).not.toContain("task-unstrike");
    expect(remaining(queryClient, row.cacheKey)).toHaveLength(2);

    // The track is the whole height animation, and `gridTemplateRows` is inert on anything that
    // is not a grid — so the display is asserted alongside it.
    expect(box().className).toContain("grid-rows-[1fr]");
    expect(box().className).toMatch(/(^|\s)grid(\s|$)/);
    expect(box().style.gridTemplateRows).toBe("");

    // 2. The rule lifts, at 160ms. `.task-unstrike` and not the absence of a class: the
    //    decoration stays declared underneath a colour running to transparent, so the text does
    //    not reflow as the rule goes.
    await act(async () => {
      await vi.advanceTimersByTimeAsync(TASK_COMPLETION_CHECK_TO_STRIKE_MS + 10);
    });
    expect(title().className).toContain("task-unstrike");
    expect(title().className).not.toContain("task-strike ");
    expect(box().style.gridTemplateRows).toBe("");
    expect(remaining(queryClient, row.cacheKey)).toHaveLength(2);

    // 3. At 520ms the ink leaves and the box shuts under it, in one declared transition, so the
    //    rows below travel into the space rather than jumping into a gap.
    await act(async () => {
      await vi.advanceTimersByTimeAsync(TASK_COMPLETION_STRIKE_TO_FADE_MS);
    });
    expect(box().style.opacity).toBe("0");
    expect(box().style.gridTemplateRows).toBe("0fr");
    expect(box().style.transition).toContain("grid-template-rows");
    expect(foreground().style.overflow).toBe("hidden");
    expect(foreground().style.minHeight).toBe("0px");
    expect(remaining(queryClient, row.cacheKey)).toHaveLength(2);

    // 4. Gone once the box is shut — and only then, which is the leg web has and the native
    //    clients do not.
    await act(async () => {
      await vi.advanceTimersByTimeAsync(TASK_COMPLETION_TOTAL_MS);
    });
    expect(remaining(queryClient, row.cacheKey)).not.toContain(
      row.cacheKey === "completedTodo" ? TODO.id : FLOATER.id,
    );
  });

  /**
   * The mirror of the forward row's cut: with no collapse to outlast there is nothing for the
   * last leg to wait for, and a wait in front of a destination already drawn is `docs/motion.md`'s
   * fifth idiom rule broken from the other side.
   */
  it("restores as soon as the frame is finished under reduced motion", async () => {
    installReducedMotion(true);
    const queryClient = renderRow(row);

    await act(async () => {
      screen.getByRole("checkbox").click();
    });
    await act(async () => {
      await vi.advanceTimersByTimeAsync(
        TASK_COMPLETION_CHECK_TO_STRIKE_MS + TASK_COMPLETION_STRIKE_TO_FADE_MS + 10,
      );
    });

    expect(remaining(queryClient, row.cacheKey)).not.toContain(
      row.cacheKey === "completedTodo" ? TODO.id : FLOATER.id,
    );
    // Destination without the trip: an empty box and no ink, arrived at rather than travelled to.
    expect(box().style.transition).toBe("");
    expect(box().style.gridTemplateRows).toBe("0fr");
  });

  it("does not re-fire if the checkbox is tapped twice", async () => {
    const queryClient = renderRow(row);
    const checkbox = screen.getByRole("checkbox");

    await act(async () => {
      checkbox.click();
      checkbox.click();
    });
    await act(async () => {
      await vi.advanceTimersByTimeAsync(TASK_COMPLETION_TOTAL_MS + 20);
    });

    expect(patchMock).toHaveBeenCalledTimes(1);
    expect(remaining(queryClient, row.cacheKey)).toHaveLength(1);
  });
});
