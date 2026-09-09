// @vitest-environment jsdom

/**
 * The Today screen's empty state — `AllTasksTimelineContainer` (scope="today") — must:
 *
 *   1. Stay hidden while any task due today is still pending, no matter what else is
 *      sitting in the shared todo cache (an overdue task included).
 *   2. Show the plain, non-celebrating scene when today is empty for a reason other than
 *      "the user just finished it" (nothing was ever due today).
 *   3. Show the CELEBRATING scene (confetti) the moment the user's own tap completes
 *      today's last pending task.
 *
 * Today's own dataset (`scopeFilteredItems` for scope="today") is already filtered to
 * `dayDiff === 0` before `hasScopedTasks`/`showEmpty` ever see it — an overdue task never
 * enters the set this screen calls empty. This file locks that invariant in, and pins the
 * confetti wiring (`taskJustCompleted()` from `@/lib/task-completion-signal`, feeding
 * `EmptyState`'s `celebrate` prop) so it can't regress silently.
 *
 * Rather than driving a checkbox through `DraggableTodayTask` (mocked away below — dnd-kit's
 * sensors want browser APIs jsdom doesn't provide, and that click is already covered by
 * `todo-row-complete-interaction.test.tsx` and `complete-todo-cache-prune.test.tsx`), the
 * "completed" test reproduces exactly what `completeMutateFn` does synchronously: call
 * `markTaskCompleted()`, then prune the row from the `["todoTimeline"]` cache this container
 * reads. That isolates what this file actually owns — the container's OWN reaction to that
 * signal — from the mutation hook's plumbing, which has its own tests already.
 */

import type { ReactNode } from "react";
import { act, cleanup, render, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { CompletedTodoItemType, TodoItemType } from "@/types";
import { markTaskCompleted } from "@/lib/task-completion-signal";

vi.mock("react-i18next", async (importOriginal) => {
  // `@/lib/navigation` (used by `AllTasksTimelineContainer` for `useLocale`) pulls in the
  // real `@/i18n`, which wires `initReactI18next` at import time — keep every other export
  // real and only replace the hook this file actually cares about.
  const actual = await importOriginal<typeof import("react-i18next")>();
  return {
    ...actual,
    useTranslation: () => ({ t: (key: string) => key }),
  };
});

// Everything below is chrome around the timeline (header, search bar, watermark, summary
// card, bulk-select) that this file has no opinion on — stubbed out so the render stays
// focused on `showEmpty` / `hasScopedTasks` / `EmptyState`.
vi.mock("@/components/app/NativePageHeader", () => ({
  default: () => null,
  useNativePageBarSlots: () => ({}),
}));
vi.mock("@/components/ui/MobileSearchHeader", () => ({ default: () => null }));
vi.mock("@/components/app/ScreenWatermark", () => ({ default: () => null }));
vi.mock("@/features/summary/SummaryButton", () => ({ default: () => null }));
vi.mock("@/features/summary/WeekInReviewCard", () => ({ default: () => null }));
vi.mock("@/components/todo/component/TodoListLoading", () => ({ default: () => null }));
vi.mock("@/components/todo/bulk/BulkSelectButton", () => ({ default: () => null }));

// The Morning/Afternoon/Tonight drag-and-drop layer: real dnd-kit sensors assume browser
// APIs jsdom doesn't have, and dragging is not what this file tests.
vi.mock("@/components/todo/dnd/TodayBucketDnd", () => ({
  TODAY_BUCKETS: [
    { label: "Morning", targetHour: 9 },
    { label: "Afternoon", targetHour: 15 },
    { label: "Tonight", targetHour: 20 },
  ],
  TodayBucketDndContext: ({ children }: { children: ReactNode }) => children,
  TodayBucketDroppable: ({ children }: { children: ReactNode }) => children,
  DraggableTodayTask: ({ todo }: { todo: TodoItemType }) => <div>{todo.title}</div>,
}));

// `TodoMutationProvider` only stores these hook functions in context; nothing in this render
// tree (the drag layer above is stubbed) ever calls them, so trivial stand-ins are enough.
vi.mock("@/features/todayTodos/query/complete-todo", () => ({
  useCompleteTodo: () => ({ completeMutateFn: vi.fn(), completePending: false }),
}));
vi.mock("@/features/todayTodos/query/delete-todo", () => ({
  useDeleteTodo: () => ({ deleteMutateFn: vi.fn(), deletePending: false }),
}));
vi.mock("@/features/todayTodos/query/prioritize-todo", () => ({
  usePrioritizeTodo: () => ({ prioritizeMutateFn: vi.fn(), prioritizePending: false }),
}));
vi.mock("@/features/todayTodos/query/update-todo", () => ({
  useEditTodo: () => ({ editTodoMutateFn: vi.fn(), editTodoStatus: "idle" }),
}));
vi.mock("@/features/todayTodos/query/update-todo-instance", () => ({
  useEditTodoInstance: () => ({
    editTodoInstanceMutateFn: vi.fn(),
    editTodoInstanceStatus: "idle",
  }),
}));
vi.mock("@/features/todayTodos/query/reorder-todo", () => ({
  useReorderTodo: () => ({ reorderMutateFn: vi.fn(), reorderPending: false }),
}));

import AllTasksTimelineContainer from "@/features/todayTodos/component/AllTasksTimelineContainer";

function buildTodo(overrides: Partial<TodoItemType>): TodoItemType {
  return {
    id: "todo-id",
    title: "Untitled",
    description: null,
    pinned: false,
    createdAt: new Date("2026-08-01T09:00:00.000Z"),
    updatedAt: null,
    order: 1,
    priority: "Low",
    due: new Date(),
    rrule: null,
    exdates: [],
    timeZone: "UTC",
    userID: "user-1",
    completed: false,
    instanceDate: null,
    listID: null,
    ...overrides,
  } as TodoItemType;
}

// Built from "now"'s own UTC calendar date, not a fixed literal — so `dayDiff` lands on 0/-3
// no matter what day the suite runs.
const now = new Date();
const todayDue = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate(), 9, 0, 0));
const overdueDue = new Date(
  Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate() - 3, 9, 0, 0),
);

const TODAY_TODO = buildTodo({ id: "todo-today", title: "Reply to Alex", due: todayDue });
const OVERDUE_TODO = buildTodo({ id: "todo-overdue", title: "Renew passport", due: overdueDue });

function renderToday(todos: TodoItemType[]) {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: {
        retry: false,
        refetchOnMount: false,
        refetchOnWindowFocus: false,
        refetchOnReconnect: false,
        staleTime: Infinity,
      },
      mutations: { retry: false },
    },
  });
  queryClient.setQueryData<TodoItemType[]>(["todoTimeline"], todos);
  queryClient.setQueryData<CompletedTodoItemType[]>(["completedTodo"], []);
  queryClient.setQueryData(["userTimezone"], { timeZone: "UTC" });

  const utils = render(
    <MemoryRouter initialEntries={["/en/app/today"]}>
      <QueryClientProvider client={queryClient}>
        <AllTasksTimelineContainer scope="today" />
      </QueryClientProvider>
    </MemoryRouter>,
  );

  return { queryClient, ...utils };
}

// Shared no-op: `Confetti`'s `useReducedMotion` only ever reads `matches` here, so the
// listener registration methods below exist purely to satisfy `MediaQueryList`'s shape —
// one stand-in body covers all four.
function noopListener() {
  /* not exercised: this suite never triggers a matchMedia change event */
}

describe("Today screen empty state + celebration (scope=\"today\")", () => {
  beforeEach(() => {
    // Reduced-motion: `Confetti` still mounts its <canvas> (proving `celebrate` was true)
    // but skips the draw loop that needs a real 2D context + ResizeObserver — neither of
    // which jsdom implements.
    window.matchMedia = ((query: string) => ({
      matches: true,
      media: query,
      addEventListener: noopListener,
      removeEventListener: noopListener,
      addListener: noopListener,
      removeListener: noopListener,
      onchange: null,
      dispatchEvent: () => false,
    })) as unknown as typeof window.matchMedia;
  });

  afterEach(() => {
    cleanup();
  });

  it("stays hidden while a today task is still pending, even with an overdue task in the shared cache", () => {
    const { container } = renderToday([TODAY_TODO, OVERDUE_TODO]);

    expect(screen.queryByText("todayEmpty")).toBeNull();
    expect(container.querySelector("canvas")).toBeNull();
  });

  it("shows the plain empty state — no confetti — when today never had anything, regardless of the overdue task", () => {
    const { container } = renderToday([OVERDUE_TODO]);

    expect(screen.getByText("todayEmpty")).not.toBeNull();
    expect(container.querySelector("canvas")).toBeNull();
  });

  it("celebrates the moment today's last pending task is completed locally — the overdue task stays in the cache untouched", async () => {
    const { queryClient, container } = renderToday([TODAY_TODO, OVERDUE_TODO]);

    act(() => {
      // What `completeMutateFn` (@/features/todayTodos/query/complete-todo) does
      // synchronously on a tap: mark the completion signal, then prune the row from the
      // cache this container reads. The overdue row is left exactly where it was — it
      // belongs to the separate `scope="overdue"` screen (Android's TodoListMode.OVERDUE,
      // iOS's `.overdueTodos`) and was never part of Today's own dataset in the first
      // place, so there's no Earlier bucket here for it to hide inside.
      markTaskCompleted();
      queryClient.setQueryData<TodoItemType[]>(["todoTimeline"], [OVERDUE_TODO]);
    });

    await waitFor(() => expect(screen.getByText("todayEmpty")).not.toBeNull());
    expect(container.querySelector("canvas")).not.toBeNull();
  });
});
