// @vitest-environment jsdom

/**
 * The calendar's task row plays the same check-off as the Today row, and that is the whole point
 * of this file: it is the same task, ticked off through the same control, and it used to finish a
 * third slower here than it did there — 280 / 620 / 960 against 160 / 360 / 260. Nothing in the
 * repository would have said so. The literal counter cannot see a row retimed through named
 * constants, and no test rendered this row at all.
 *
 * So the assertions below are deliberately the same assertions
 * `todo-row-complete-interaction.test.tsx` makes about the Today row, taken against a different
 * component: the same phase classes at the same offsets, the same grid track closing under the
 * same fade, the same prune waiting for the box rather than for the ink. If the two rows ever
 * disagree again, the two files disagree first.
 *
 * `CalendarTaskRow` is exported for this. It is one row out of a 1,300-line screen and rendering
 * the screen to reach it would put a month grid, a drag context and four queries between the test
 * and the four beats it is about.
 */

import type { ReactNode } from "react";
import { act, cleanup, render, screen } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { TodoItemType } from "@/types";
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
vi.mock("@/hooks/use-todo-action-toast", () => ({
  useTodoActionToast: () => ({
    showTodoCompletedToast: vi.fn(),
    showTodoDeletedToast: vi.fn(),
  }),
}));
vi.mock("@/features/user/query/get-timezone", () => ({
  useUserTimezone: () => ({ timeZone: "UTC" }),
}));

import { CalendarTaskRow } from "@/features/calendar/component/CalendarClient";

const TODO: TodoItemType = {
  id: "todo-1",
  title: "Water the plants",
  description: "By the window",
  completed: false,
  priority: "Low",
  due: new Date("2026-08-22T10:00:00.000Z"),
  rrule: null,
  exdates: [],
  instanceDate: null,
  listID: null,
} as unknown as TodoItemType;

const OTHER: TodoItemType = { ...TODO, id: "todo-2", title: "Call the bank" };

function renderRow() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  queryClient.setQueryData<TodoItemType[]>(["calendarTodo"], [TODO, OTHER]);

  function Wrapper({ children }: { children: ReactNode }) {
    return (
      <MemoryRouter initialEntries={["/en/app/calendar"]}>
        <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
      </MemoryRouter>
    );
  }

  render(<CalendarTaskRow todo={TODO} />, { wrapper: Wrapper });
  return queryClient;
}

/** The row's outer box — the element carrying the grid track that closes. */
function box(): HTMLElement {
  const checkbox = screen.getByRole("checkbox");
  const element = checkbox.closest("div.grid");
  if (!element) throw new Error("the row's collapsing box is not on screen");
  return element as HTMLElement;
}

function remaining(queryClient: QueryClient): string[] {
  return (queryClient.getQueryData<TodoItemType[]>(["calendarTodo"]) ?? []).map((t) => t.id);
}

const REAL_MATCH_MEDIA = window.matchMedia;

describe("ticking a calendar task row's checkbox", () => {
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

  it("plays check -> strike -> fade on the shared clock, then removes the row", async () => {
    const queryClient = renderRow();
    const checkbox = screen.getByRole("checkbox");
    const title = () => screen.getByText("Water the plants");
    const notes = () => screen.getByText("By the window");

    await act(async () => {
      checkbox.click();
    });

    // 1. Checked at once; not struck yet, still on the day.
    expect((checkbox as HTMLInputElement).checked).toBe(true);
    expect(title().className).not.toContain("task-strike");
    expect(remaining(queryClient)).toHaveLength(2);
    expect(box().className).toContain("grid-rows-[1fr]");
    expect(box().className).toMatch(/(^|\s)grid(\s|$)/);
    expect(box().style.gridTemplateRows).toBe("");

    // 2. The rule fades in over title AND notes, on one class, at 160ms. The notes carried
    //    Tailwind's bare `line-through` for a long time, which snapped a rule on underneath one
    //    that was fading in — one task reading as two edits.
    await act(async () => {
      await vi.advanceTimersByTimeAsync(TASK_COMPLETION_CHECK_TO_STRIKE_MS + 10);
    });
    expect(title().className).toContain("task-strike");
    expect(notes().className).toContain("task-strike");
    expect(notes().className).not.toContain("line-through");
    expect(box().style.gridTemplateRows).toBe("");
    expect(remaining(queryClient)).toHaveLength(2);

    // 3. At 520ms the ink leaves and the box shuts under it, so the rows below the day travel
    //    into the space instead of jumping through a full-height gap — which is what this row
    //    did before it had a track to close.
    await act(async () => {
      await vi.advanceTimersByTimeAsync(TASK_COMPLETION_STRIKE_TO_FADE_MS);
    });
    expect(box().style.opacity).toBe("0");
    expect(box().style.gridTemplateRows).toBe("0fr");
    expect(box().style.transition).toContain("grid-template-rows");
    expect(remaining(queryClient)).toHaveLength(2);

    // 4. Gone once the box is shut, and not before.
    await act(async () => {
      await vi.advanceTimersByTimeAsync(TASK_COMPLETION_TOTAL_MS);
    });
    expect(remaining(queryClient)).toEqual(["todo-2"]);
  });

  /** The cut the other four rows make, for the reason `taskCompletionTiming` argues. */
  it("prunes as soon as the frame is finished under reduced motion", async () => {
    installReducedMotion(true);
    const queryClient = renderRow();

    await act(async () => {
      screen.getByRole("checkbox").click();
    });
    await act(async () => {
      await vi.advanceTimersByTimeAsync(
        TASK_COMPLETION_CHECK_TO_STRIKE_MS + TASK_COMPLETION_STRIKE_TO_FADE_MS + 10,
      );
    });

    expect(remaining(queryClient)).toEqual(["todo-2"]);
    expect(box().style.transition).toBe("");
    expect(box().style.gridTemplateRows).toBe("0fr");
  });
});
