// @vitest-environment jsdom

/**
 * Ticking a task must close the gap in the list straight away.
 *
 * The row used to run a staged strike-through animation and only call `completeMutateFn` from the
 * last timer (~960ms), so the list sat with a hole in it — the row was invisible from 620ms but
 * still occupied its full height — until the animation finished. Completion now prunes in the
 * same tick, and the undo toast is what puts the row back.
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

  it("prunes the task in the same tick, with no timers advanced", async () => {
    const queryClient = renderRow();

    const checkbox = screen.getByRole("checkbox");
    await act(async () => {
      checkbox.click();
    });

    // Deliberately no timer advance: the rows below have to move up immediately.
    expect(queryClient.getQueryData<TodoItemType[]>(["todoTimeline"])?.map((t) => t.id)).toEqual([
      "todo-2",
    ]);
    expect(queryClient.getQueryData<TodoItemType[]>(["todo"])?.map((t) => t.id)).toEqual(["todo-2"]);
  });

  it("does not re-fire if the checkbox is tapped twice", async () => {
    const queryClient = renderRow();
    const checkbox = screen.getByRole("checkbox");

    await act(async () => {
      checkbox.click();
      checkbox.click();
    });

    expect(queryClient.getQueryData<TodoItemType[]>(["todoTimeline"])?.map((t) => t.id)).toEqual([
      "todo-2",
    ]);
  });
});
