// @vitest-environment jsdom

/**
 * The empty scene renders AFTER the block that carries the "Earlier" header, on
 * every screen where the two can be on the page at once.
 *
 * This is a document-order claim and jsdom is the right place for it: no layout
 * is needed to ask which node comes first, and document order is exactly what
 * decides whether expanding Earlier grows downward from a header that stays put
 * or shrinks a 42vh box sitting above it and throws the header to the top of the
 * screen. That was web's behaviour, it was the last client with it, and the
 * comment asserting it claimed parity as the reason — so a rewritten comment
 * alone would leave nothing to stop the next refactor putting the block back.
 *
 * Both containers are covered because both own a copy of the ordering:
 * `AllTasksTimelineContainer` (Today/All/Priority/Scheduled/Overdue) and
 * `ListContainer` (every custom list). Earlier is the FIRST section
 * `buildTimelineSections` pushes for these scopes (`placesEarlierBeforeToday`),
 * so "after the sections block" is "after the Earlier header" for real.
 */

import type { ReactNode } from "react";
import { cleanup, render } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";

import type { TodoItemType } from "@/types";

/** The two nodes whose order is the whole subject. */
vi.mock("@/components/todo/dnd/TimelineSections", () => ({
  default: () => <div data-testid="earlier-block" />,
}));
vi.mock("@/features/todayTodos/component/TimelineEmptyState", () => ({
  default: () => <div data-testid="empty-scene" />,
}));

// Everything else the two screens draw is mocked away: the real header, sheet
// and dnd trees would pull the query and toast stacks in to assert nothing
// extra, and this file is about which node comes first.
vi.mock("@/components/app/ScreenWatermark", () => ({ default: () => null }));
vi.mock("@/components/ui/MobileSearchHeader", () => ({ default: () => null }));
vi.mock("@/components/app/NativePageHeader", () => ({
  default: () => null,
  useNativePageBarSlots: () => ({}),
}));
vi.mock("@/features/summary/SummaryButton", () => ({ default: () => null }));
vi.mock("@/features/summary/WeekInReviewCard", () => ({ default: () => null }));
vi.mock("@/components/todo/bulk/BulkSelectButton", () => ({ default: () => null }));
vi.mock("@/components/todo/component/TodoListLoading", () => ({ default: () => null }));
vi.mock("@/components/app/EmptyState", () => ({ default: () => null }));
vi.mock("@/components/Sidebar/List/ListFormSheet", () => ({ default: () => null }));
vi.mock("@/features/list/component/ManageMembersSheet", () => ({ default: () => null }));
vi.mock("@/features/todayTodos/component/OverdueDaySections", () => ({ default: () => null }));
vi.mock("@/features/todayTodos/component/TodayTimeBuckets", () => ({ default: () => null }));
vi.mock("@/providers/TodoMutationProvider", () => ({
  default: ({ children }: { children: ReactNode }) => <>{children}</>,
}));
vi.mock("@/providers/TaskSelectionProvider", () => ({
  default: ({ children }: { children: ReactNode }) => <>{children}</>,
}));

vi.mock("@/features/list/query/complete-list-todo", () => ({ useCompleteListTodo: vi.fn() }));
vi.mock("@/features/list/query/delete-list-todo", () => ({ useDeleteListTodo: vi.fn() }));
vi.mock("@/features/list/query/prioritize-list-todo", () => ({ usePrioritizeListTodo: vi.fn() }));
vi.mock("@/features/list/query/update-list-todo", () => ({ useEditListTodo: vi.fn() }));
vi.mock("@/features/list/query/update-list-todo-instance", () => ({
  useEditListTodoInstance: vi.fn(),
}));
vi.mock("@/features/list/query/reorder-list-todo", () => ({ useReorderListTodo: vi.fn() }));
vi.mock("@/features/todayTodos/query/complete-todo", () => ({ useCompleteTodo: vi.fn() }));
vi.mock("@/features/todayTodos/query/delete-todo", () => ({ useDeleteTodo: vi.fn() }));
vi.mock("@/features/todayTodos/query/prioritize-todo", () => ({ usePrioritizeTodo: vi.fn() }));
vi.mock("@/features/todayTodos/query/update-todo", () => ({ useEditTodo: vi.fn() }));
vi.mock("@/features/todayTodos/query/update-todo-instance", () => ({
  useEditTodoInstance: vi.fn(),
}));
vi.mock("@/features/todayTodos/query/reorder-todo", () => ({ useReorderTodo: vi.fn() }));
vi.mock("@/components/Sidebar/List/query/get-list-meta", () => ({
  useListMetaData: () => ({ listMetaData: {} }),
}));
vi.mock("@/features/user/query/get-timezone", () => ({
  useUserTimezone: () => ({ timeZone: "UTC" }),
}));
vi.mock("@/hooks/use-share-list", () => ({ useShareListAsText: () => vi.fn() }));
vi.mock("@/hooks/useAppMode", () => ({ useIsLocalMode: () => false }));

const todos = vi.hoisted(() => ({ value: [] as unknown[] }));
vi.mock("@/features/list/query/get-list-todos", () => ({
  useList: () => ({ listTodos: todos.value, listTodosLoading: false }),
}));
vi.mock("@/features/todayTodos/query/get-todo-timeline", () => ({
  useTodoTimeline: () => ({ todos: todos.value, todoLoading: false }),
}));

import "@/i18n";
import AllTasksTimelineContainer from "@/features/todayTodos/component/AllTasksTimelineContainer";
import ListContainer from "@/features/list/component/ListContainer";

/**
 * The only state that puts both on screen together: no current tasks left, one
 * overdue task still there. `showEmpty` counts only the non-Earlier rows, so the
 * scene shows while the Earlier bucket goes on rendering its header.
 */
function onlyOverdue(): TodoItemType[] {
  const dayMs = 24 * 60 * 60 * 1000;
  return [
    {
      id: "todo-overdue:undefined",
      title: "Overdue task",
      description: null,
      completed: false,
      priority: "Low",
      due: new Date(Date.now() - 3 * dayMs),
      createdAt: new Date(Date.now() - 30 * dayMs),
      rrule: null,
      instanceDate: null,
      listID: null,
    },
  ] as unknown as TodoItemType[];
}

/**
 * The scoped screens ask react-query for the completed set on their own (the
 * "day done" branch of the empty state). Real provider, empty cache: the answer
 * is not what this file is about, and stubbing the hook would stub out a render
 * path the ordering has to survive.
 */
function withQuery(children: ReactNode) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0 } },
  });
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}

/** True when `first` precedes `second` in document order. */
function precedes(first: Element, second: Element): boolean {
  return (first.compareDocumentPosition(second) & Node.DOCUMENT_POSITION_FOLLOWING) !== 0;
}

afterEach(cleanup);

describe("the empty scene follows the Earlier block", () => {
  it("on the scoped timeline screens", () => {
    todos.value = onlyOverdue();
    const { getByTestId } = render(
      withQuery(
        <MemoryRouter initialEntries={["/en/app/all"]}>
          <AllTasksTimelineContainer scope="all" />
        </MemoryRouter>,
      ),
    );
    // Both on screen at once is the precondition, not an incidental: an
    // assertion about order that passes because one of them is absent proves
    // nothing, and this file would go on passing if the scene stopped rendering.
    const earlier = getByTestId("earlier-block");
    const scene = getByTestId("empty-scene");
    expect(precedes(earlier, scene)).toBe(true);
  });

  it("on a custom list", () => {
    todos.value = onlyOverdue();
    const { getByTestId } = render(
      <MemoryRouter initialEntries={["/en/app/list/list-a"]}>
        <ListContainer id="list-a" />
      </MemoryRouter>,
    );
    const earlier = getByTestId("earlier-block");
    const scene = getByTestId("empty-scene");
    expect(precedes(earlier, scene)).toBe(true);
  });
});
