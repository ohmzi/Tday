// @vitest-environment jsdom

/**
 * The ring a deep link leaves on a task row has to be drawn inside the box that clips it.
 *
 * Every task row is a `grid-rows-[1fr] overflow-hidden` wrapper with exactly one grid item in it,
 * and that item's border box is the wrapper's clip box to the pixel. So an outset ring — which is
 * what Tailwind's `ring-2` is, a `box-shadow` painted beyond the border box — survived only where
 * the rounded corners pulled the box away from its own clip: four short arcs, and nothing along
 * any edge. The mark `calendar-row-highlight-ring.test.tsx` proved was on a clock was a mark
 * almost nobody could see. The wrapper's clip is not negotiable, either: it is what lets the 1fr
 * track close on a completing row, and it is what keeps a row swiped 210px under a finger from
 * painting outside its own box.
 *
 * Two things are asserted, and both of them are about shape rather than about pixels, because
 * jsdom applies no stylesheet, computes no layout and would report an outset ring and an inset one
 * as the same empty string.
 *
 * The first is that the ring is inset and that its geometry does not move — `inset-ring-2` on both
 * sides of `highlighted`, with only the colour swapped. That second half looks like tidiness and
 * is not, though the reason is not that the conditional ring would cut: an unmarked row declares
 * no `box-shadow`, and CSS pads a `none` against the other list adopting its `inset` flags, so
 * that shape does transition — by growing the ring 0px → 2px. Growing is geometry, on a `Quick`
 * leg meant for paint; and it holds only until the first `shadow-*` or ring utility leaves a
 * composite `box-shadow` on the resting row, at which point the non-inset initial of
 * `--tw-inset-ring-shadow` makes the flags disagree and the property stops transitioning
 * altogether — PR 25b's defect back with every gate green. A fixed 2px ring that moves only alpha
 * is the shape neither objection reaches. The fade is only inspectable here as that fixity.
 *
 * The second is that all three rows carry one string. Three copies of one defect is why this row
 * exists, so the assertion is a contiguous substring rather than a set of tokens: it fails if the
 * three drift apart by so much as an inserted class, which is how they got here.
 *
 * `ring-2` is a substring of `inset-ring-2`, so every "no outset ring" assertion is written with
 * token boundaries. A `toContain` here would pass on the defect and on the fix alike.
 */

import type { ReactNode } from "react";
import { cleanup, render, screen } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { FloaterItemType, TodoItemType } from "@/types";

vi.mock("@/lib/api-client", () => ({
  api: { PATCH: vi.fn(), DELETE: vi.fn(), POST: vi.fn() },
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
vi.mock("@/features/floaterList/query/get-floater-list-meta", () => ({
  useFloaterListMetaData: () => ({ floaterListMetaData: {} }),
}));
vi.mock("@/features/floater/query/delete-floater", () => ({
  useDeleteFloater: () => ({ deleteMutateFn: vi.fn(), deletePending: false }),
}));
// Surfaces reachable only from controls this file never touches. They drag in a popover and two
// form sheets, none of which has an opinion about how a row is marked.
vi.mock("@/components/todo/component/TodoForm/TaskFormSheet", () => ({ default: () => null }));
vi.mock("@/features/floater/component/PromoteFloaterMenu", () => ({
  PromoteFloaterMenu: () => null,
}));
vi.mock("@/features/floater/component/FloaterFormSheet", () => ({ default: () => null }));

import { CalendarTaskRow } from "@/features/calendar/component/CalendarClient";
import { TodoItemCard } from "@/components/todo/component/TodoItemContainer";
import FloaterItemContainer from "@/features/floater/component/FloaterItemContainer";
import TodoMutationProvider from "@/providers/TodoMutationProvider";
import { useCompleteTodo } from "@/features/todayTodos/query/complete-todo";

/** The ring's geometry, declared whatever the row's state, so only its colour can travel. */
const RING_GEOMETRY = "inset-ring-2";
/** The marked row's whole shape, in the order the three sources write it. */
const MARKED = "rounded-lg inset-ring-accent/25 sm:bg-accent/5 sm:inset-ring-transparent";
/** The unmarked row's — same ring, no colour. */
const UNMARKED = "inset-ring-transparent";

/** `class` as a token, so `ring-2` cannot match inside `inset-ring-2`. */
function hasToken(className: string, token: string): boolean {
  return className.split(/\s+/).includes(token);
}

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

const FLOATER = {
  id: "fl-1",
  title: "Pick a paint colour",
  description: null,
  completed: false,
  priority: "Low",
  listID: null,
} as unknown as FloaterItemType;

function client(key: string, rows: unknown[]): QueryClient {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  queryClient.setQueryData([key], rows);
  return queryClient;
}

function wrapperFor(queryClient: QueryClient, route: string) {
  return function Wrapper({ children }: { children: ReactNode }) {
    return (
      <MemoryRouter initialEntries={[route]}>
        <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
      </MemoryRouter>
    );
  };
}

/**
 * The grid item inside the row's box, found through the checkbox rather than by class. The class
 * string is what is under assertion, so a selector built out of it could not fail.
 */
function foreground(): HTMLElement {
  const checkbox = screen.getByRole("checkbox");
  const element = checkbox.closest<HTMLElement>("div.z-10");
  if (!element) throw new Error("the row's foreground is not on screen");
  return element;
}

function renderCalendarRow(highlighted: boolean): void {
  render(<CalendarTaskRow todo={TODO} highlighted={highlighted} />, {
    wrapper: wrapperFor(client("calendarTodo", [TODO]), "/en/app/calendar"),
  });
}

function renderTodoRow(highlighted: boolean): void {
  const queryClient = client("todo", [TODO]);
  queryClient.setQueryData(["todoTimeline"], [TODO]);
  const Outer = wrapperFor(queryClient, "/en/app/todo");

  function Wrapper({ children }: { children: ReactNode }) {
    return (
      <Outer>
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
      </Outer>
    );
  }

  render(<TodoItemCard todoItem={TODO} highlighted={highlighted} />, { wrapper: Wrapper });
}

function renderFloaterRow(highlighted: boolean): void {
  render(<FloaterItemContainer floater={FLOATER} highlighted={highlighted} />, {
    wrapper: wrapperFor(client("floater", [FLOATER]), "/en/app/floater"),
  });
}

const ROWS: ReadonlyArray<[string, (highlighted: boolean) => void]> = [
  ["the calendar day list", renderCalendarRow],
  ["the Today / scheduled list", renderTodoRow],
  ["the Anytime list", renderFloaterRow],
];

describe("the ring a deep link leaves on a task row", () => {
  beforeEach(() => {
    // The row scrolls itself into view when it is marked, and jsdom does not implement it.
    Element.prototype.scrollIntoView = vi.fn();
  });

  afterEach(cleanup);

  it.each(ROWS)("is drawn inside the box that clips it on %s", (_name, renderRow) => {
    renderRow(true);
    const className = foreground().className;

    expect(hasToken(className, RING_GEOMETRY)).toBe(true);
    expect(className).toContain("inset-ring-accent/25");

    // The defect, in the only spelling jsdom can see: an outset ring, and the `sm:` reset that
    // only an outset ring needs.
    expect(hasToken(className, "ring-2")).toBe(false);
    expect(hasToken(className, "sm:ring-0")).toBe(false);
  });

  it.each(ROWS)("keeps the desktop mark a flat tint on %s", (_name, renderRow) => {
    renderRow(true);
    const className = foreground().className;

    // Above `sm` the mark has always been the tint and no ring at all, and that is the half a
    // reviewer on a desktop is actually looking at. Whatever replaced `sm:ring-0` has to carry
    // its own reset or the fix for the phone is a regression on every other screen.
    expect(className).toContain("sm:bg-accent/5");
    expect(hasToken(className, "sm:inset-ring-transparent")).toBe(true);
  });

  it.each(ROWS)("declares the same ring when it is not marked on %s", (_name, renderRow) => {
    // The assertion that keeps PR 25b's fade a fade of paint. Both states name an inset shadow of
    // the same width, so nothing about the ring's size moves on a rung that is for colour; and
    // because the unmarked state declares the composite `box-shadow` itself, no utility landing
    // here later can turn the mark's arrival into the discrete swap that a `0 0 #0000` non-inset
    // initial would otherwise produce.
    renderRow(false);
    const className = foreground().className;

    expect(hasToken(className, RING_GEOMETRY)).toBe(true);
    expect(hasToken(className, UNMARKED)).toBe(true);
    expect(className).not.toContain("inset-ring-accent");
  });

  it.each(ROWS)("draws the ring on the element that declares the clock on %s", (_name, renderRow) => {
    // The two fixes have to meet on one node. `swipeTransition` is a whitelist written into this
    // element's inline style, so a ring hoisted onto the wrapper to escape the clip would be a
    // ring with no transition — which is why the wrapper was rejected as the place to draw it.
    renderRow(true);
    const element = foreground();

    expect(hasToken(element.className, RING_GEOMETRY)).toBe(true);
    expect(element.style.transition).toContain("box-shadow");
  });

  it("writes one string in all three rows", () => {
    const marked = ROWS.map(([, renderRow]) => {
      renderRow(true);
      const className = foreground().className;
      cleanup();
      return className;
    });

    // Contiguous, not merely present: the shape is the thing being shared, and a class inserted
    // into the middle of one of them is the drift this row was opened to end.
    for (const className of marked) {
      expect(className).toContain(MARKED);
    }
  });
});
