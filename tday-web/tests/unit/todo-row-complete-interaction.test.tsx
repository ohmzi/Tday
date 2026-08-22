// @vitest-environment jsdom

/**
 * Ticking a task plays the same staged sequence the native apps do — green check, then strike
 * through the title and notes, then fade — and only then leaves the list.
 *
 * The point of these tests is that the sequence runs on its OWN timers and is not gated on the
 * undo toast in any way. The toast lasts 5s; the animation finishes in under 1s, and the rows
 * below close up at that point rather than waiting for the toast to expire.
 */

import type { ReactNode } from "react";
import { act, cleanup, render, screen } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { TodoItemType } from "@/types";

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

describe("ticking a task row's checkbox", () => {
  beforeEach(() => {
    patchMock.mockReset();
    patchMock.mockResolvedValue(null);
    vi.useFakeTimers({ shouldAdvanceTime: true });
  });

  afterEach(() => {
    vi.useRealTimers();
    // This config has no globals-based auto cleanup, so a second render would otherwise find
    // two checkboxes in the same container.
    cleanup();
  });

  it("plays check -> strike -> fade, then removes the row on its own timing", async () => {
    const queryClient = renderRow();
    const checkbox = screen.getByRole("checkbox");
    const title = () => screen.getByText("Water the plants");

    await act(async () => {
      checkbox.click();
    });

    // 1. Checked straight away, but nothing struck through and still in the list.
    expect((checkbox as HTMLInputElement).checked).toBe(true);
    expect(title().className).not.toContain("line-through");
    expect(queryClient.getQueryData<TodoItemType[]>(["todoTimeline"])).toHaveLength(2);

    // 2. Struck through.
    await act(async () => {
      await vi.advanceTimersByTimeAsync(300);
    });
    expect(title().className).toContain("line-through");
    expect(queryClient.getQueryData<TodoItemType[]>(["todoTimeline"])).toHaveLength(2);

    // 3. Fading, and still occupying the list while it does.
    await act(async () => {
      await vi.advanceTimersByTimeAsync(350);
    });
    expect(queryClient.getQueryData<TodoItemType[]>(["todoTimeline"])).toHaveLength(2);

    // 4. Gone — the rows below close up, well inside the 5s undo toast.
    await act(async () => {
      await vi.advanceTimersByTimeAsync(400);
    });
    expect(queryClient.getQueryData<TodoItemType[]>(["todoTimeline"])?.map((t) => t.id)).toEqual([
      "todo-2",
    ]);
    expect(queryClient.getQueryData<TodoItemType[]>(["todo"])?.map((t) => t.id)).toEqual(["todo-2"]);
  });

  /**
   * The toast's own 5s lifetime must not hold the row in the list: the sequence above completes
   * at ~960ms regardless of what the toast is doing.
   */
  it("removes the row well before the undo toast would expire", async () => {
    const queryClient = renderRow();

    await act(async () => {
      screen.getByRole("checkbox").click();
    });
    await act(async () => {
      await vi.advanceTimersByTimeAsync(1000);
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
      await vi.advanceTimersByTimeAsync(1200);
    });

    expect(queryClient.getQueryData<TodoItemType[]>(["todoTimeline"])?.map((t) => t.id)).toEqual([
      "todo-2",
    ]);
  });
});
