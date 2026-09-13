// @vitest-environment jsdom

/**
 * Both calendar delete dialogs are `lazy()`, and both Suspense boundaries used
 * to answer with `null`. So a Delete tap made before the chunk landed put
 * nothing on screen at all — which is the same answer the app gives to a tap it
 * never received, and the reason the next thing a user does is press it again.
 *
 * The window is narrow and real. The boundary renders unconditionally, so the
 * import starts when the row mounts rather than when the button is pressed; what
 * is left is a cold cache or a bad connection, which is exactly the case where
 * an unanswered tap costs most.
 *
 * The gate is the other half and is easier to get wrong than the fallback: a
 * fallback that drew itself whenever the boundary was suspended would put a
 * modal over the calendar on first paint, once per row. So both halves are
 * asserted here — nothing before the tap, a dialog-shaped placeholder after it.
 *
 * The chunks are mocked as imports that never settle, which is the only way to
 * hold a real Suspense boundary in its fallback: vitest resolves a genuine
 * dynamic import inside a microtask, so the fallback would otherwise be gone
 * before an assertion could see it.
 */

import type { ReactNode } from "react";
import { act, cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
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
// Never settles: the row's Suspense boundaries stay in their fallbacks for the
// whole test, which is the state this file is about.
vi.mock(
  "@/features/calendar/component/ConfirmationModals/ConfirmDelete",
  () => new Promise(() => {}),
);
vi.mock(
  "@/features/calendar/component/ConfirmationModals/ConfirmDeleteAll",
  () => new Promise(() => {}),
);

import { CalendarTaskRow } from "@/features/calendar/component/CalendarClient";

const TODO: TodoItemType = {
  id: "todo-1",
  title: "Water the plants",
  description: "",
  completed: false,
  priority: "Low",
  due: new Date("2026-08-22T10:00:00.000Z"),
  rrule: null,
  exdates: [],
  instanceDate: null,
  listID: null,
} as unknown as TodoItemType;

/** The same task on a repeat, which asks the other dialog of the two. */
const RECURRING: TodoItemType = {
  ...TODO,
  id: "todo-2",
  rrule: "FREQ=WEEKLY",
} as unknown as TodoItemType;

afterEach(cleanup);

function renderRow(todo: TodoItemType) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  queryClient.setQueryData<TodoItemType[]>(["calendarTodo"], [todo]);

  function Wrapper({ children }: { children: ReactNode }) {
    return (
      <MemoryRouter initialEntries={["/en/app/calendar"]}>
        <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
      </MemoryRouter>
    );
  }

  render(<CalendarTaskRow todo={todo} />, { wrapper: Wrapper });
}

/** The placeholder's card, which is the thing the tap is answered with. */
const placeholder = () => document.querySelector<HTMLElement>("[aria-busy='true']");
/** The scrim it is drawn on, and the way back out of it. */
const scrim = () => placeholder()!.closest<HTMLElement>(".fixed.inset-0")!;

/** The row draws its actions twice: swipe-revealed on mobile, hover on desktop. */
function tapDelete() {
  const buttons = screen.getAllByLabelText("Delete");
  act(() => {
    fireEvent.click(buttons[buttons.length - 1]);
  });
}

describe("the calendar's delete dialog answers the tap that opened it", () => {
  it("draws nothing while the dialog is closed, however long the chunk takes", () => {
    renderRow(TODO);

    expect(placeholder()).toBeNull();
  });

  it("puts a dialog-shaped placeholder up on the first tap", () => {
    renderRow(TODO);

    tapDelete();

    const card = placeholder();
    expect(card).not.toBeNull();
    // A line, a sentence and two buttons — the shape of the confirm dialog it
    // is standing in for, not of the edit form `ModalPlaceholder` answers for.
    expect(card!.querySelectorAll(".animate-pulse")).toHaveLength(4);
  });

  it("arrives on the same scrim and the same card the real dialog will", () => {
    // Built from the Modal primitives rather than a portal of its own, so the
    // chunk lands into the card that is already on screen instead of replacing
    // a differently-shaped one.
    renderRow(TODO);

    tapDelete();

    expect(scrim().className).toContain("bg-black/65");
    expect(placeholder()!.closest(".max-w-lg")).not.toBeNull();
  });

  it("can be taken back while the chunk is still in flight", async () => {
    // A tap that can be answered can also be regretted. A scrim that swallowed
    // both for the length of a download would be a worse answer than none.
    renderRow(TODO);
    tapDelete();

    act(() => {
      fireEvent.click(scrim());
    });

    await waitFor(() => expect(placeholder()).toBeNull());
  });

  it("answers the recurring task's delete too, which is a different dialog", () => {
    // `requestDelete` sends a repeating task to `ConfirmDeleteAll`. Same tap,
    // same silence, same fallback.
    renderRow(RECURRING);

    tapDelete();

    expect(placeholder()).not.toBeNull();
  });
});
