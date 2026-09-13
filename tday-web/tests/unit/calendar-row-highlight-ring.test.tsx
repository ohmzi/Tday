// @vitest-environment jsdom

/**
 * The highlight a deep link leaves on a calendar row, and the reason it needs a test rather than
 * a look.
 *
 * Arriving at `/app/calendar?task=…` scrolls the row into view and marks it. That mark is drawn
 * two ways from one className — a ring under `sm`, a background tint above it — and the two are
 * never on screen together, so a reviewer reading the row on a desktop sees the tint fade in and
 * has no way to notice that the ring beside it in the same string cut in one frame. Tailwind
 * draws `ring-2` as a `box-shadow`, `box-shadow` was missing from the element's inline
 * transition list, and a transition list is a whitelist: everything not named in it is exempt.
 *
 * So the assertions are structural, and they are about the whitelist rather than about a
 * duration. jsdom applies no stylesheet and runs no animation; what it can hold is that the
 * element the ring is drawn on is the element the transition is declared on, and that the ring
 * and the tint are named on the same clock. Both halves matter — a whitelist that named
 * `box-shadow` on some other node would read identically in a diff and animate nothing.
 *
 * `CalendarTaskRow` is exported for `calendar-row-complete-interaction.test.tsx`; this file
 * reaches the same row for the same reason, which the export's own comment gives.
 */

import type { ReactNode } from "react";
import { render, screen, cleanup } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { TodoItemType } from "@/types";

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
vi.mock("@/features/user/query/get-timezone", () => ({
  useUserTimezone: () => ({ timeZone: "UTC" }),
}));

import { CalendarTaskRow } from "@/features/calendar/component/CalendarClient";

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

function renderRow(highlighted: boolean) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  queryClient.setQueryData<TodoItemType[]>(["calendarTodo"], [TODO]);

  function Wrapper({ children }: { children: ReactNode }) {
    return (
      <MemoryRouter initialEntries={["/en/app/calendar"]}>
        <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
      </MemoryRouter>
    );
  }

  render(<CalendarTaskRow todo={TODO} highlighted={highlighted} />, { wrapper: Wrapper });
}

/** The row's foreground — the element that both carries the highlight and declares the clock. */
function foreground(): HTMLElement {
  const element = screen.getByRole("checkbox").closest<HTMLElement>("div.z-10");
  if (!element) throw new Error("the calendar row's foreground is not on screen");
  return element;
}

/** `<property> <duration> <easing>` for one entry of a transition shorthand. */
function leg(transition: string, property: string): string {
  const match = transition.split(",").find((part) => part.trim().startsWith(`${property} `));
  if (!match) throw new Error(`\`${property}\` is not on the whitelist: ${transition}`);
  return match.trim();
}

describe("the highlight a deep link leaves on a calendar row", () => {
  beforeEach(() => {
    // `scrollIntoView` is what the row does with the highlight, and jsdom does not implement it.
    Element.prototype.scrollIntoView = vi.fn();
  });

  afterEach(cleanup);

  it("draws the ring and the tint on the element that declares the transition", () => {
    renderRow(true);

    // One className, two renderings of one mark: `ring-2` below `sm`, `bg-accent/5` above it.
    // The ring is a box-shadow, which is the whole reason the omission was invisible.
    expect(foreground().className).toContain("ring-2");
    expect(foreground().className).toContain("sm:bg-accent/5");
    expect(foreground().style.transition).toContain("box-shadow");
  });

  it("puts the ring on the same clock as the tint it stands in for", () => {
    renderRow(true);

    const transition = foreground().style.transition;
    const ring = leg(transition, "box-shadow");
    const tint = leg(transition, "background-color");

    // Same duration and same curve, named through the tokens rather than written out: the two
    // are one signal at two breakpoints, and a signal that lands at two speeds is two signals.
    expect(ring.replace("box-shadow", "")).toBe(tint.replace("background-color", ""));
    expect(ring).toContain("var(--tday-duration-quick)");
    expect(ring).toContain("var(--tday-ease-standard)");
  });

  it("keeps the whitelist on the row that is not highlighted", () => {
    // The transition is declared unconditionally, which is what lets the mark fade OUT as well —
    // an assertion worth making because the obvious cheap fix is to add `box-shadow` next to the
    // ring's own className and leave the arrival animated and the departure a cut.
    renderRow(false);

    expect(foreground().className).not.toContain("ring-2");
    expect(foreground().style.transition).toContain("box-shadow");
  });
});
