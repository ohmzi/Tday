// @vitest-environment jsdom

/**
 * What is on screen for the calendar task form between the tap and the chunk.
 *
 * The split used to sit around the surface: both containers lazily imported a
 * drawer AND a modal behind a single `DrawerPlaceholder`. Two defects fell out
 * of one cause. Above 640 px the fallback answered for the wrong branch, so a
 * desktop tap put a bottom sheet on screen for an arriving centred modal. And
 * because the fallback was a finished sheet at the geometry the real one ends
 * at, the chunk landing could only take it away while vaul slid an identical
 * sheet into the same place — one tap, two arrivals.
 *
 * Both are now fixed by construction rather than by matching: the shells are
 * eager and only the body is lazy, so the surface the user sees *is* the real
 * one. That is exactly the kind of fix a test suite can sleep through —
 * `calendar-form-shell-resize` waits for a shell and then reads the fields, and
 * the old eager body satisfied it just as well. So this file asserts the one
 * thing neither shape can fake: what the document holds while the body's chunk
 * is still in flight, and whether the surface holding it survives the handover.
 *
 * The chunk is held open by a gate rather than mocked as never settling, because
 * the second row is about the frame *after* it lands. `vi.hoisted` is what lets
 * the factory reach it: `vi.mock` is hoisted above the imports, so a plain
 * `const` declared here would still be in its temporal dead zone when the
 * factory runs.
 */

import React from "react";
import { act, cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

const gate = vi.hoisted(() => {
  let open!: () => void;
  const opened = new Promise<void>((resolve) => {
    open = resolve;
  });
  return { opened, open: () => open() };
});

// The lazy chunk itself, held shut until a test opens it. Everything the real
// body would pull in — chrono, TipTap, the selector overlays — is behind this
// one import, so standing in for it is also what keeps this file cheap.
vi.mock(
  "@/features/calendar/component/CalendarForm/Form/CalendarTaskFormBody",
  async () => {
    await gate.opened;
    return { default: () => <div data-testid="real-body" /> };
  },
);

vi.mock("react-i18next", () => ({
  useTranslation: () => ({ t: (key: string) => key }),
  initReactI18next: { type: "3rdParty", init: () => {} },
}));

// Nothing is submitted here, so the mutations only need to exist.
vi.mock("@/features/calendar/query/create-calendar-todo", () => ({
  useCreateCalendarTodo: () => ({
    createCalendarTodo: vi.fn(),
    createTodoStatus: "idle",
  }),
}));

vi.mock("@/features/calendar/query/update-calendar-todo", () => ({
  useEditCalendarTodo: () => ({
    editCalendarTodo: vi.fn(),
    editTodoStatus: "idle",
  }),
}));

// useCalendarTaskFormState reads the selected list's default priority off this —
// a real `useQuery` that throws without a QueryClientProvider this file never sets up.
vi.mock("@/components/Sidebar/List/query/get-list-meta", () => ({
  useListMetaData: () => ({
    listMetaData: {},
    listMetaLoading: false,
    isFetching: false,
    isPending: false,
  }),
}));

vi.mock(
  "@/features/calendar/component/ConfirmationModals/ConfirmCancelEditDrawer",
  () => ({ default: () => null }),
);
vi.mock("@/features/calendar/component/ConfirmationModals/ConfirmCancelEdit", () => ({
  default: () => null,
}));

// Same stand-ins as `calendar-form-shell-resize`: vaul and Radix both need
// layout APIs jsdom does not implement, and all either shell is asked for here
// is a marker saying which one is mounted. Crucially neither portals, so
// anything this file finds outside a marker got there on its own — which is how
// the old placeholder drew itself.
vi.mock("@/components/ui/drawer", () => ({
  Drawer: ({ children }: { children: React.ReactNode }) => (
    <div data-testid="shell-drawer">{children}</div>
  ),
  DrawerContent: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
  DrawerTitle: ({ children }: { children: React.ReactNode }) => <h2>{children}</h2>,
}));

vi.mock("@/components/ui/Modal", () => ({
  Modal: ({ children }: { children: React.ReactNode }) => (
    <div data-testid="shell-modal">{children}</div>
  ),
  ModalOverlay: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
  ModalContent: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
}));

import CreateCalendarFormContainer from "@/features/calendar/component/CalendarForm/CreateFormContainer";

const SLOT_START = new Date("2026-04-03T14:00:00.000Z");
const SLOT_END = new Date("2026-04-03T14:30:00.000Z");

function setViewportWidth(width: number) {
  Object.defineProperty(window, "innerWidth", {
    configurable: true,
    writable: true,
    value: width,
  });
}

function openFormAt(width: number) {
  setViewportWidth(width);
  render(
    <CreateCalendarFormContainer
      start={SLOT_START}
      end={SLOT_END}
      displayForm
      setDisplayForm={vi.fn()}
    />,
  );
}

/** The body's stand-in while its chunk is in flight. */
const placeholders = () => Array.from(document.querySelectorAll("[aria-busy='true']"));

afterEach(cleanup);

describe("the calendar form waits for its body inside the surface it opened", () => {
  it("draws the desktop modal, not a bottom sheet, while the chunk is in flight", async () => {
    // The first row, and the one the old code could not get right at all: with a
    // single fallback standing in for two branches, the branch it was shaped like
    // won. Above 640 px that was the wrong one.
    openFormAt(900);

    const shell = await screen.findByTestId("shell-modal");
    expect(screen.queryByTestId("shell-drawer")).toBeNull();
    expect(screen.queryByTestId("real-body")).toBeNull();

    // One placeholder, and it is *inside* the modal rather than beside it. A
    // surface-shaped fallback would be a sibling of the shell — or, as the
    // portalled `DrawerPlaceholder` was, of the whole tree.
    expect(placeholders()).toHaveLength(1);
    expect(shell.contains(placeholders()[0])).toBe(true);
  });

  it("draws the drawer below the breakpoint, with the same placeholder inside it", () => {
    // The branch the old fallback happened to match. It is asserted for the same
    // reason the modal is: what makes this right is that nobody chooses, so the
    // two breakpoints have to be checked the same way to show that.
    openFormAt(400);

    const shell = screen.getByTestId("shell-drawer");
    expect(screen.queryByTestId("shell-modal")).toBeNull();
    expect(placeholders()).toHaveLength(1);
    expect(shell.contains(placeholders()[0])).toBe(true);
  });

  it("hands the chunk over into the surface already on screen, not a second one", async () => {
    // The second row. A fallback cannot hand over — being replaced is all it does
    // — so while the split sat around the surface, the arriving sheet was always a
    // different sheet from the one standing in for it. Identity is the whole
    // assertion: the same node before and after means nothing was torn down and
    // nothing slid in a second time.
    openFormAt(400);

    const before = screen.getByTestId("shell-drawer");

    await act(async () => {
      gate.open();
    });

    await screen.findByTestId("real-body");
    expect(screen.getByTestId("shell-drawer")).toBe(before);
    expect(placeholders()).toHaveLength(0);
  });
});
