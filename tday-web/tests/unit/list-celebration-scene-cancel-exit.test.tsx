// @vitest-environment jsdom

/**
 * The host half of the cancel, on the screen the bug was reported from.
 *
 * `Confetti` fades its pieces out over `Quick` instead of vanishing between two
 * frames — but a fade is only as long as the element it is painted into. On the
 * plain path (no overdue rows, so the restored task makes the list non-empty
 * again) the scene the burst flies inside is leaving in the SAME frame the
 * celebration ends, and a container that unmounts it there cuts the fade it just
 * bought. So the container holds the scene in the tree for the length of the
 * envelope and draws it leaving while it is there.
 *
 * Held only for the refill. The scene has a second, older way off this slot —
 * the Earlier hand-off, which takes it away while the list is still EMPTY and
 * has its own longer beat (`TODAY_EARLIER_EXIT_MS`) — and a linger bolted onto
 * that one would be a second departure stacked on a departure.
 *
 * jsdom computes no layout and applies no stylesheet, so what is pinned here is
 * which element is in the tree, for how long, and what it was told it was doing.
 * `.tday-empty-cancel-exit`'s own rung is in globals.css and on the device row.
 */

import type { ReactNode } from "react";
import { act, cleanup, render } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { DURATION_MS } from "@/lib/motion";
import {
  CELEBRATION_WINDOW_MS,
  markCelebrationCancelled,
  markTaskCompleted,
} from "@/lib/task-completion-signal";
import type { TodoItemType } from "@/types";

/** What the container told the scene it was doing, rather than what CSS did with it. */
const scene = vi.hoisted(() => ({ props: null as { leavingOnCancel?: boolean } | null }));
vi.mock("@/features/todayTodos/component/TimelineEmptyState", () => ({
  default: (props: { leavingOnCancel?: boolean }) => {
    scene.props = props;
    return <div data-testid="empty-scene" />;
  },
}));

// Everything else the screen draws is mocked to nothing: the real
// header/watermark/sheet tree would drag the dnd and query stacks in to assert
// nothing extra, and this file is about how long one element survives.
vi.mock("@/components/todo/dnd/TimelineSections", () => ({
  default: () => <div data-testid="rows" />,
}));
vi.mock("@/components/app/ScreenWatermark", () => ({ default: () => null }));
vi.mock("@/components/ui/MobileSearchHeader", () => ({ default: () => null }));
vi.mock("@/components/app/NativePageHeader", () => ({
  default: () => null,
  useNativePageBarSlots: () => ({}),
}));
vi.mock("@/features/summary/SummaryButton", () => ({ default: () => null }));
vi.mock("@/components/todo/bulk/BulkSelectButton", () => ({ default: () => null }));
vi.mock("@/components/todo/component/TodoListLoading", () => ({ default: () => null }));
vi.mock("@/components/app/EmptyState", () => ({ default: () => null }));
vi.mock("@/components/Sidebar/List/ListFormSheet", () => ({ default: () => null }));
vi.mock("@/features/list/component/ManageMembersSheet", () => ({ default: () => null }));
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

import "@/i18n";
import ListContainer from "@/features/list/component/ListContainer";

const DAY_MS = 24 * 60 * 60 * 1000;

function todo(id: string, dueInDays: number): TodoItemType {
  return {
    id: `${id}:undefined`,
    title: id,
    description: null,
    completed: false,
    priority: "Low",
    due: new Date(Date.now() + dueInDays * DAY_MS),
    createdAt: new Date(Date.now() - 30 * DAY_MS),
    rrule: null,
    instanceDate: null,
    listID: null,
  } as unknown as TodoItemType;
}

beforeEach(() => {
  vi.useFakeTimers({ shouldAdvanceTime: true });
  // The celebration stamps are module state and outlive a test file.
  markTaskCompleted();
  markCelebrationCancelled();
  vi.advanceTimersByTime(CELEBRATION_WINDOW_MS * 2);
  scene.props = null;
});

afterEach(() => {
  cleanup();
  vi.useRealTimers();
});

function renderList(initial: TodoItemType[]) {
  todos.value = initial;
  const view = render(
    <MemoryRouter initialEntries={["/en/app/list/list-a"]}>
      <ListContainer id="list-a" />
    </MemoryRouter>,
  );
  return {
    ...view,
    /** The list's contents changing under the screen, as a refetch delivers it. */
    settle: (next: TodoItemType[]) => {
      todos.value = next;
      act(() => {
        view.rerender(
          <MemoryRouter initialEntries={["/en/app/list/list-a"]}>
            <ListContainer id="list-a" />
          </MemoryRouter>,
        );
      });
    },
  };
}

describe("the empty scene when the undone row makes the list non-empty", () => {
  it("is held in the tree for the burst's own fade, then goes", () => {
    const { queryByTestId, settle } = renderList([todo("today-task", 0)]);
    expect(queryByTestId("empty-scene")).toBeNull();

    markTaskCompleted();
    settle([]);
    const held = queryByTestId("empty-scene");
    expect(held).not.toBeNull();
    expect(scene.props?.leavingOnCancel).toBe(false);

    // The undo. The row is back, the list is not empty, and the scene is on its
    // way out — but it is still the same element, because the paper inside it is
    // still fading and a replacement would have nothing to fade.
    settle([todo("today-task", 0)]);
    expect(queryByTestId("empty-scene")).toBe(held);
    expect(scene.props?.leavingOnCancel).toBe(true);

    act(() => {
      vi.advanceTimersByTime(DURATION_MS.quick + 1);
    });
    expect(queryByTestId("empty-scene")).toBeNull();
  });

  it("holds nothing on the overdue path, where the scene is not leaving at all", () => {
    // The reported screenshot's shape: the restored row is overdue, so it lands
    // in Earlier and the list still reads as finished. The scene stays put on
    // its own terms and only the envelope inside it runs — a linger here would
    // be a departure invented for a scene that is not departing.
    const { queryByTestId, settle } = renderList([todo("overdue-task", -3)]);
    markTaskCompleted();
    settle([]);
    expect(queryByTestId("empty-scene")).not.toBeNull();

    settle([todo("overdue-task", -3)]);
    expect(queryByTestId("empty-scene")).not.toBeNull();
    expect(scene.props?.leavingOnCancel).toBe(false);

    act(() => {
      vi.advanceTimersByTime(DURATION_MS.quick + 1);
    });
    expect(queryByTestId("empty-scene")).not.toBeNull();
  });
});
