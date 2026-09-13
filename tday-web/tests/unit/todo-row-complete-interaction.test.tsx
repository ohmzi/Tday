// @vitest-environment jsdom

/**
 * Ticking a task plays the same staged sequence the native apps do, on the same clock: green
 * check, then the title strike sweeps in while the notes get a plain line-through, then the row
 * fades and collapses its box out of the list. The timings come from `@/lib/taskCompletionTiming`,
 * whose first two legs mirror the native TASK_COMPLETION_* constants (160 / 360ms).
 *
 * The last leg is the one web does not share: the native lists animate a removal themselves, so on
 * web the row's own collapse IS the removal and the prune has to wait for it. That is what these
 * tests hold — the box closes while the row is still there, and the rows below travel rather than
 * jump into a gap.
 *
 * They also pin that the sequence runs on its OWN timers and is not gated on the undo toast: the
 * toast lives 5s and the animation is done in well under a second.
 */

import type { ReactNode } from "react";
import { act, cleanup, render, screen } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { TodoItemType } from "@/types";
import {
  TASK_COMPLETION_CHECK_TO_STRIKE_MS,
  TASK_COMPLETION_COLLAPSE_MS,
  TASK_COMPLETION_STRIKE_TO_FADE_MS,
  TASK_COMPLETION_TOTAL_MS,
} from "@/lib/taskCompletionTiming";
import { getTodoFocusElementId } from "@/lib/todoToastNavigation";
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
vi.mock("@/components/Sidebar/List/query/get-list-meta", () => ({
  useListMetaData: () => ({ listMetaData: {} }),
}));
vi.mock("@/features/user/query/get-timezone", () => ({
  useUserTimezone: () => ({ timeZone: "UTC" }),
}));
vi.mock("@/features/todayTodos/query/demote-todo", () => ({
  useDemoteTodo: () => ({ demoteMutateFn: vi.fn() }),
}));
vi.mock("@/components/todo/component/TodoForm/TaskFormSheet", () => ({
  default: () => null,
}));

import { TodoItemCard } from "@/components/todo/component/TodoItemContainer";
import TodoMutationProvider from "@/providers/TodoMutationProvider";
import { useCompleteTodo } from "@/features/todayTodos/query/complete-todo";

const TODO: TodoItemType = {
  id: "todo-1",
  title: "Water the plants",
  description: null,
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
  queryClient.setQueryData<TodoItemType[]>(["todo"], [TODO, OTHER]);
  queryClient.setQueryData<TodoItemType[]>(["todoTimeline"], [TODO, OTHER]);

  function Wrapper({ children }: { children: ReactNode }) {
    return (
      <MemoryRouter initialEntries={["/en/app/todo"]}>
        <QueryClientProvider client={queryClient}>
          <TodoMutationProvider
            useCompleteTodo={useCompleteTodo}
            useDeleteTodo={(() => ({ deleteMutateFn: vi.fn(), deletePending: false })) as never}
            useEditTodo={(() => ({ editTodoMutateFn: vi.fn(), editTodoStatus: "idle" })) as never}
            useEditTodoInstance={
              (() => ({ editTodoInstanceMutateFn: vi.fn(), editTodoInstanceStatus: "idle" })) as never
            }
            usePrioritizeTodo={
              (() => ({ prioritizeMutateFn: vi.fn(), prioritizePending: false })) as never
            }
            useReorderTodo={(() => ({ reorderMutateFn: vi.fn(), reorderPending: false })) as never}
          >
            {children}
          </TodoMutationProvider>
        </QueryClientProvider>
      </MemoryRouter>
    );
  }

  render(<TodoItemCard todoItem={TODO} />, { wrapper: Wrapper });
  return queryClient;
}

/**
 * The row's own box — the element that carries the collapse. Read back out of the document by id
 * rather than from a render result, because what is being asserted is what the DOM was handed.
 */
function row(): HTMLElement {
  const element = document.getElementById(getTodoFocusElementId(TODO.id));
  if (!element) throw new Error("the row is not on screen");
  return element;
}

/**
 * The grid item inside that box — the one holding the row's content, found through the checkbox
 * rather than by class so it survives a restyle.
 *
 * It gets its own assertions because a grid item's automatic minimum size is its own content: the
 * track above can be told to run to 0fr all it likes and the box will not close until this element
 * is allowed to be smaller than the row it holds.
 */
function foreground(): HTMLElement {
  const checkbox = screen.getByRole("checkbox");
  const item = Array.from(row().children).find((child) => child.contains(checkbox));
  if (!item) throw new Error("the row's foreground is not on screen");
  return item as HTMLElement;
}

const REAL_MATCH_MEDIA = window.matchMedia;

describe("ticking a task row's checkbox", () => {
  beforeEach(() => {
    patchMock.mockReset();
    patchMock.mockResolvedValue(null);
    vi.useFakeTimers({ shouldAdvanceTime: true });
  });

  afterEach(() => {
    window.matchMedia = REAL_MATCH_MEDIA;
    vi.useRealTimers();
    // This config has no globals-based auto cleanup, so a second render would otherwise find
    // two checkboxes in the same container.
    cleanup();
  });

  it("plays check -> strike -> fade on the native clock, then removes the row", async () => {
    const queryClient = renderRow();
    const checkbox = screen.getByRole("checkbox");
    const title = () => screen.getByText("Water the plants");

    await act(async () => {
      checkbox.click();
    });

    // 1. Checked at once; not struck yet, still in the list.
    expect((checkbox as HTMLInputElement).checked).toBe(true);
    expect(title().className).not.toContain("task-strike");
    expect(queryClient.getQueryData<TodoItemType[]>(["todoTimeline"])).toHaveLength(2);

    // The track is the whole height animation: `gridTemplateRows` is an inert property on
    // anything that is not a grid, so a later tidy-up that drops these two classes from a row
    // which visibly stacks nothing would leave the collapse silently doing nothing at all.
    expect(row().className).toContain("grid-rows-[1fr]");
    expect(row().className).toMatch(/(^|\s)grid(\s|$)/);
    expect(row().style.gridTemplateRows).toBe("");
    // Nothing is clipped or unclamped while the row is staying: that would cost it the focus ring
    // and the hover actions, which sit proud of its box.
    expect(foreground().style.overflow).toBe("");
    expect(foreground().style.minHeight).toBe("");

    // 2. Strike sweeps in. The title uses the swept rule, not a plain line-through — notes keep
    //    the plain one, exactly as the native rows split it. The box is still at full height:
    //    this beat is the user reading their own edit, and closing up under them would take the
    //    strike off the screen before it has been seen.
    await act(async () => {
      await vi.advanceTimersByTimeAsync(TASK_COMPLETION_CHECK_TO_STRIKE_MS + 10);
    });
    expect(title().className).toContain("task-strike");
    expect(title().className).not.toContain("line-through");
    expect(row().style.gridTemplateRows).toBe("");
    expect(queryClient.getQueryData<TodoItemType[]>(["todoTimeline"])).toHaveLength(2);

    // 3. Fading AND closing: the ink goes on the Change rung, the box on Emphasis, both declared
    //    in one transition so they read as a single departure. The row still holds its key in the
    //    caches — what is shrinking is the space it takes, so the rows below travel into it.
    await act(async () => {
      await vi.advanceTimersByTimeAsync(TASK_COMPLETION_STRIKE_TO_FADE_MS);
    });
    expect(row().style.opacity).toBe("0");
    expect(row().style.gridTemplateRows).toBe("0fr");
    expect(row().style.transition).toContain("grid-template-rows");
    // And the content is let go of, so the track has room to close under it.
    expect(foreground().style.overflow).toBe("hidden");
    expect(foreground().style.minHeight).toBe("0px");
    expect(queryClient.getQueryData<TodoItemType[]>(["todoTimeline"])).toHaveLength(2);

    // 4. Gone once the box is shut, and the rows below close up.
    await act(async () => {
      await vi.advanceTimersByTimeAsync(TASK_COMPLETION_TOTAL_MS);
    });
    expect(queryClient.getQueryData<TodoItemType[]>(["todoTimeline"])?.map((t) => t.id)).toEqual([
      "todo-2",
    ]);
    expect(queryClient.getQueryData<TodoItemType[]>(["todo"])?.map((t) => t.id)).toEqual(["todo-2"]);
  });

  /**
   * The toast's own 5s lifetime must not hold the row in the list: the sequence completes on its
   * own clock regardless of what the toast is doing.
   */
  it("removes the row well before the undo toast would expire", async () => {
    const queryClient = renderRow();

    await act(async () => {
      screen.getByRole("checkbox").click();
    });
    await act(async () => {
      await vi.advanceTimersByTimeAsync(TASK_COMPLETION_TOTAL_MS + 20);
    });

    expect(TASK_COMPLETION_TOTAL_MS).toBeLessThan(5000);
    expect(queryClient.getQueryData<TodoItemType[]>(["todoTimeline"])).toHaveLength(1);
  });

  /**
   * The prune is what actually takes the row out of the list, and web has nothing else that
   * animates a removal — so a prune that lands while the box is still closing is the jump the
   * collapse exists to remove, arriving a frame from the end instead of at the start.
   *
   * Checked against the rung rather than by advancing the clock to a point inside the last leg:
   * the gap between a sequence that waits for the fade and one that waits for the collapse is
   * 60ms, `shouldAdvanceTime` lets real time move the fake clock as well, and a probe that narrow
   * would pass or fail on how busy the machine is. This is not the sum compared back to itself —
   * building the total out of the fade, which is what it used to be, fails here.
   */
  it("gives the collapse a leg of its own before the row is pruned", () => {
    const lastLeg =
      TASK_COMPLETION_TOTAL_MS -
      TASK_COMPLETION_CHECK_TO_STRIKE_MS -
      TASK_COMPLETION_STRIKE_TO_FADE_MS;

    expect(lastLeg).toBeGreaterThanOrEqual(TASK_COMPLETION_COLLAPSE_MS);
  });

  /**
   * Reduced motion removes the trip, never the destination — and it has to remove the WAIT for the
   * trip with it (docs/motion.md, fifth idiom rule, which names this as the rule broken from the
   * other side). The last leg exists to let the box finish closing; with the collapse switched off
   * there is no box left to close, so holding the prune back would be a third of a second of
   * nothing between the row going and the undo toast arriving — spent by the one reader who asked
   * for less motion, not more.
   *
   * The contrast is beat 3 of the first test above, which finds the row still in the caches at
   * exactly this instant.
   */
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

    expect(queryClient.getQueryData<TodoItemType[]>(["todoTimeline"])?.map((t) => t.id)).toEqual([
      "todo-2",
    ]);
  });

  /**
   * The preference can also arrive mid-sequence — a battery saver trips, or the reader reaches for
   * the setting because of what they just saw. The timers are already armed by then and cannot be
   * told, but the row can: it subscribes rather than reading once, so what is left to get right is
   * the frame, and the frame it must draw is the finished one.
   *
   * This is the branch that survives the cut above, and the only place the row's own reduced-motion
   * style is reachable.
   */
  it("drops the transition when the preference flips mid-sequence", async () => {
    const reduced = installReducedMotion(false);
    const queryClient = renderRow();

    await act(async () => {
      screen.getByRole("checkbox").click();
    });
    await act(async () => {
      await vi.advanceTimersByTimeAsync(
        TASK_COMPLETION_CHECK_TO_STRIKE_MS + TASK_COMPLETION_STRIKE_TO_FADE_MS + 10,
      );
    });
    expect(row().style.transition).toContain("grid-template-rows");

    await act(async () => {
      reduced.set(true);
    });

    // Destination without the trip: an empty box and no ink, arrived at rather than travelled to.
    expect(row().style.transition).toBe("");
    expect(row().style.gridTemplateRows).toBe("0fr");
    expect(row().style.opacity).toBe("0");

    // And the completion still lands on the clock it was armed with: the preference silences the
    // motion, not the work.
    await act(async () => {
      await vi.advanceTimersByTimeAsync(TASK_COMPLETION_TOTAL_MS);
    });
    expect(queryClient.getQueryData<TodoItemType[]>(["todoTimeline"])).toHaveLength(1);
  });

  it("does not re-fire if the checkbox is tapped twice", async () => {
    const queryClient = renderRow();
    const checkbox = screen.getByRole("checkbox");

    await act(async () => {
      checkbox.click();
      checkbox.click();
    });
    await act(async () => {
      await vi.advanceTimersByTimeAsync(TASK_COMPLETION_TOTAL_MS + 20);
    });

    expect(queryClient.getQueryData<TodoItemType[]>(["todoTimeline"])?.map((t) => t.id)).toEqual([
      "todo-2",
    ]);
  });
});
