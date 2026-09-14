// @vitest-environment jsdom

/**
 * The custom-list screen was the fourth screen with the 42vh problem and the one
 * that never got the travel. Ticking the last CURRENT task off a list mounts the
 * empty scene — `min-h-[42vh]` — in the frame that prunes the row, and the Earlier
 * block underneath it goes on rendering for as long as the list holds overdue
 * tasks, so most of a screen arrives above a block that then lands in its new place
 * with nothing to read it by. `AllTasksTimelineContainer`,
 * `NativeScheduledTaskHomeDashboard` and `NativeFloaterTaskHomeDashboard` were each
 * given `useRowPlacement` for exactly that; this is the fourth.
 *
 * jsdom computes no layout, so nothing here can watch anything travel — the device
 * row owns that half. What it can pin is the wiring a refactor of the container
 * drops silently: that the ref reaches the scroll wrapper the whole feed sits in
 * (a ref on any inner box tracks the wrong children, and a ref on nothing at all
 * still renders a perfectly correct screen), and that the celebration is held back
 * by the travel it would otherwise fire across. The real hook runs here rather than
 * a stub of it, so a hook that stopped handing its container ref back fails this
 * file too.
 */

import type { ReactNode, RefObject } from "react";
import { cleanup, render } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";

import { DELAY_MS } from "@/lib/motion";
import type { TodoItemType } from "@/types";

/** The container ref the production hook handed the screen, as the screen got it. */
const placement = vi.hoisted(() => ({
  ref: null as RefObject<HTMLElement | null> | null,
}));
vi.mock("@/hooks/useRowPlacement", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/hooks/useRowPlacement")>();
  return {
    ...actual,
    useRowPlacement: <T extends HTMLElement>() => {
      const ref = actual.useRowPlacement<T>();
      placement.ref = ref;
      return ref;
    },
  };
});

/** What the screen asked the empty scene for, rather than what the scene did with it. */
const scene = vi.hoisted(() => ({ props: null as { celebrationStartDelayMs?: number } | null }));
vi.mock("@/features/todayTodos/component/TimelineEmptyState", () => ({
  default: (props: { celebrationStartDelayMs?: number }) => {
    scene.props = props;
    return <div data-testid="empty-scene" />;
  },
}));

// The block that travels. Everything else the screen draws is mocked to nothing:
// the real header/watermark/sheet tree would drag the dnd and query stacks in to
// assert nothing extra, and this file is about which box holds them.
vi.mock("@/components/todo/dnd/TimelineSections", () => ({
  default: () => <div data-testid="earlier-block" />,
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

// The mutation hooks are handed to the (mocked) provider as values and never
// called here; standing them in keeps the api-client and toast stack out.
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

/**
 * The defect's own state: no current tasks left, one overdue task still in the
 * list. That is what puts the scene and the Earlier block on the page together —
 * `showEmpty` counts only the non-Earlier rows.
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

function renderList() {
  placement.ref = null;
  scene.props = null;
  todos.value = onlyOverdue();
  return render(
    <MemoryRouter initialEntries={["/en/app/list/list-a"]}>
      <ListContainer id="list-a" />
    </MemoryRouter>,
  );
}

afterEach(cleanup);

describe("the custom list's empty-state travel", () => {
  it("places the whole feed, not a box inside it", () => {
    const { getByTestId } = renderList();
    const container = placement.ref?.current;

    // The wrapper, identified the way the screen identifies it rather than by
    // walking a fixed number of parents: a box that both of these sit inside is
    // the one whose children take new slots.
    expect(container).not.toBeNull();
    expect(container?.classList.contains("mb-20")).toBe(true);
    expect(container?.contains(getByTestId("empty-scene"))).toBe(true);
    expect(container?.contains(getByTestId("earlier-block"))).toBe(true);
  });

  it("holds the celebration back by the travel it would otherwise fire across", () => {
    renderList();
    // The number is read from the token, not restated: the point is that the
    // screen spends the placement lead, whatever `Emphasis` is worth that day.
    expect(scene.props?.celebrationStartDelayMs).toBe(DELAY_MS.placementLead);
  });
});
