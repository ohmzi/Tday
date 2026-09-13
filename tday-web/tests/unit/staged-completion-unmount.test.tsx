// @vitest-environment jsdom

/**
 * Ticking a task is a promise the app makes before it keeps it: the row shows the green check
 * straight away and the real work — pruning the caches, raising the undoable toast, and eventually
 * PATCHing `/api/todo/complete` — happens 780ms later, at the end of the staged check-off sequence.
 *
 * The row is not entitled to survive those 780ms. A filter flips, a section collapses, the user
 * navigates, a parent re-keys its children — any of those unmounts the row while the sequence is
 * still running. When the staging lived in the row's own `useState` + `useRef<number[]>` the
 * unmount cleanup cleared the timers, and the completion the user had already seen acknowledged
 * was silently dropped: nothing was pruned, no toast appeared, no request was ever sent. The user
 * ticked a task and the task came back.
 *
 * So these tests are about durability, not about choreography (that is
 * `todo-row-complete-interaction.test.tsx`). They unmount the row mid-window on purpose and assert
 * the completion still lands, and that a row which comes back mid-window comes back mid-sequence
 * rather than untouched.
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

const patchMock = vi.fn();
/**
 * The staged completion hands the undo toast the pair of callbacks that decide whether the request
 * is ever sent. Capturing them here is how a test can prove the completion reached the *server*
 * path and not merely the local caches.
 */
let capturedHandlers: { commit: () => void; undo: () => void } | null = null;
/** How many completions reached the toast — one tick must never stage two. */
let stagedToasts = 0;

vi.mock("@/lib/api-client", () => ({
  api: { PATCH: (...a: unknown[]) => patchMock(...a), DELETE: vi.fn(), POST: vi.fn() },
}));
vi.mock("@/hooks/use-toast", () => ({ useToast: () => ({ toast: vi.fn() }) }));
vi.mock("@/hooks/use-todo-action-toast", () => ({
  useTodoActionToast: () => ({
    showTodoCompletedToast: (handlers: { commit: () => void; undo: () => void }) => {
      capturedHandlers = handlers;
      stagedToasts += 1;
    },
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

/**
 * Mounts one row and hands back the pieces a test needs to take it away and put it back: the
 * client whose caches the completion prunes, and the wrapper to re-render inside so a second mount
 * shares that client rather than starting a fresh world.
 */
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

  const { unmount } = render(<TodoItemCard todoItem={TODO} />, { wrapper: Wrapper });
  return { queryClient, Wrapper, unmount };
}

const idsIn = (queryClient: QueryClient, key: string) =>
  queryClient.getQueryData<TodoItemType[]>([key])?.map((todo) => todo.id);

describe("a staged completion whose row unmounts mid-window", () => {
  beforeEach(() => {
    patchMock.mockReset();
    patchMock.mockResolvedValue(null);
    capturedHandlers = null;
    stagedToasts = 0;
    vi.useFakeTimers({ shouldAdvanceTime: true });
  });

  afterEach(() => {
    vi.useRealTimers();
    // This config has no globals-based auto cleanup, so a second render would otherwise find
    // two checkboxes in the same container.
    cleanup();
  });

  it("still commits after the row is taken away part-way through", async () => {
    const { queryClient, unmount } = renderRow();

    await act(async () => {
      screen.getByRole("checkbox").click();
    });

    // Part-way: struck, and still holding its place in both caches.
    await act(async () => {
      await vi.advanceTimersByTimeAsync(TASK_COMPLETION_CHECK_TO_STRIKE_MS + 10);
    });
    expect(idsIn(queryClient, "todoTimeline")).toEqual(["todo-1", "todo-2"]);

    // The row goes away with ~600ms still to run — the filter changed, the list re-keyed, the
    // user navigated. Whatever the reason, the tick already happened.
    unmount();

    await act(async () => {
      await vi.advanceTimersByTimeAsync(TASK_COMPLETION_TOTAL_MS);
    });

    // The completion landed anyway: caches pruned...
    expect(idsIn(queryClient, "todoTimeline")).toEqual(["todo-2"]);
    expect(idsIn(queryClient, "todo")).toEqual(["todo-2"]);
    // ...and the undo toast got its commit handler, so the request still fires when the toast
    // closes without undo. Losing this half would leave the task ticked on this device only.
    expect(capturedHandlers).not.toBeNull();
    await act(async () => {
      capturedHandlers?.commit();
      await vi.advanceTimersByTimeAsync(0);
    });
    expect(patchMock).toHaveBeenCalledWith(
      expect.objectContaining({ url: "/api/todo/complete" }),
    );
  });

  it("brings a row that comes back mid-window back mid-sequence, not untouched", async () => {
    const { queryClient, Wrapper, unmount } = renderRow();

    await act(async () => {
      screen.getByRole("checkbox").click();
    });
    await act(async () => {
      await vi.advanceTimersByTimeAsync(TASK_COMPLETION_CHECK_TO_STRIKE_MS + 10);
    });

    // Unmounted and immediately remounted — a re-keyed list, a tab returning, a section reopening.
    unmount();
    cleanup();
    render(<TodoItemCard todoItem={TODO} />, { wrapper: Wrapper });

    // The remounted row is still ticked and still struck: the sequence belongs to the task, not
    // to the DOM node that happened to be showing it.
    expect((screen.getByRole("checkbox") as HTMLInputElement).checked).toBe(true);
    expect(screen.getByText("Water the plants").className).toContain("task-strike");

    // And it finishes on the original clock rather than restarting from the remount.
    await act(async () => {
      await vi.advanceTimersByTimeAsync(
        TASK_COMPLETION_STRIKE_TO_FADE_MS + TASK_COMPLETION_TOTAL_MS,
      );
    });
    expect(idsIn(queryClient, "todoTimeline")).toEqual(["todo-2"]);
  });

  it("does not restart the window when the row remounts and is tapped again", async () => {
    const { queryClient, Wrapper, unmount } = renderRow();

    await act(async () => {
      screen.getByRole("checkbox").click();
    });
    await act(async () => {
      await vi.advanceTimersByTimeAsync(400);
    });

    unmount();
    cleanup();
    render(<TodoItemCard todoItem={TODO} />, { wrapper: Wrapper });

    // The guard against a second tap has to survive the unmount too. If it doesn't, the remounted
    // row is a fresh component with no memory of the completion already in flight, and this tap
    // starts the 780ms over again — so the commit arrives late, and twice.
    await act(async () => {
      screen.getByRole("checkbox").click();
    });
    await act(async () => {
      await vi.advanceTimersByTimeAsync(TASK_COMPLETION_TOTAL_MS - 400 + 20);
    });

    // Committed on the FIRST tap's clock, exactly once.
    expect(idsIn(queryClient, "todoTimeline")).toEqual(["todo-2"]);
    expect(stagedToasts).toBe(1);

    // Nothing else is still pending: a second window would land here.
    await act(async () => {
      await vi.advanceTimersByTimeAsync(TASK_COMPLETION_TOTAL_MS);
    });
    expect(stagedToasts).toBe(1);
  });
});
