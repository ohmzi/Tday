// @vitest-environment jsdom

// The calendar task form renders in a bottom drawer below 640 px and a centred modal above
// it. Those are two different component types, so React tears one down and builds the other
// when the viewport crosses the breakpoint — a rotation, a split-screen resize, a dragged
// browser window. While the draft lived inside the shells, that swap silently blanked
// everything the user had typed. These tests type into one shell, cross 640 px, and insist
// the values are still there in the other.

import React from "react";
import { act, cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { TodoItemType } from "@/types";

vi.mock("react-i18next", () => ({
  useTranslation: () => ({ t: (key: string) => key }),
  initReactI18next: { type: "3rdParty", init: () => {} },
}));

// Nothing is submitted here — only typed — so the mutations just need to exist.
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

// The confirmation dialogs only open on close or on saving a series.
vi.mock(
  "@/features/calendar/component/ConfirmationModals/ConfirmCancelEditDrawer",
  () => ({ default: () => null }),
);
vi.mock("@/features/calendar/component/ConfirmationModals/ConfirmCancelEdit", () => ({
  default: () => null,
}));
vi.mock("@/features/calendar/component/ConfirmationModals/ConfirmEditAllDrawer", () => ({
  default: () => null,
}));
vi.mock("@/features/calendar/component/ConfirmationModals/ConfirmEditAll", () => ({
  default: () => null,
}));

// vaul drives the real drawer off layout and visual-viewport APIs jsdom does not implement,
// and the modal portals itself. All this test wants from either is a marker it can use to
// tell which shell is currently mounted.
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

vi.mock("@/components/Sidebar/List/query/get-list-meta", () => ({
  useListMetaData: () => ({
    listMetaData: {},
    listMetaLoading: false,
    isFetching: false,
    isPending: false,
  }),
}));

// The real title field is a contenteditable with chrono date parsing behind it, and the real
// notes field is a TipTap editor. Plain inputs stand in for both so the test can type, while
// still writing through exactly the setters the form threads down.
vi.mock("@/components/todo/component/TodoForm/NLPTitleInput", () => ({
  default: ({
    title,
    setTitle,
  }: {
    title: string;
    setTitle: (value: string) => void;
  }) => (
    <input
      data-testid="title-input"
      value={title}
      onChange={(event) => setTitle(event.target.value)}
    />
  ),
}));

vi.mock("@/components/todo/component/NotesField/NotesField", () => ({
  default: ({
    value,
    onChange,
  }: {
    value: string;
    onChange: (value: string) => void;
  }) => (
    <textarea
      data-testid="notes-input"
      value={value}
      onChange={(event) => onChange(event.target.value)}
    />
  ),
}));

vi.mock("@/components/todo/component/TodoForm/TodoFormSelectors", () => ({
  default: () => null,
}));

import CreateCalendarFormContainer from "@/features/calendar/component/CalendarForm/CreateFormContainer";
import EditCalendarFormContainer from "@/features/calendar/component/CalendarForm/EditFormContainer";

const SLOT_START = new Date("2026-04-03T14:00:00.000Z");
const SLOT_END = new Date("2026-04-03T14:30:00.000Z");

const todo: TodoItemType = {
  id: "todo-1",
  title: "Renew passport",
  description: "",
  pinned: false,
  createdAt: new Date("2026-04-01T09:00:00.000Z"),
  order: 0,
  priority: "Low",
  due: SLOT_END,
  rrule: null,
  timeZone: "UTC",
  userID: "user-1",
  completed: false,
  exdates: [],
  listID: null,
};

function setViewportWidth(width: number) {
  Object.defineProperty(window, "innerWidth", {
    configurable: true,
    writable: true,
    value: width,
  });
}

/** Resize the window past the breakpoint the way a rotation or a window drag would. */
async function resizeTo(width: number) {
  await act(async () => {
    setViewportWidth(width);
    window.dispatchEvent(new Event("resize"));
  });
}

function titleInput() {
  return screen.getByTestId("title-input") as HTMLInputElement;
}

function notesInput() {
  return screen.getByTestId("notes-input") as HTMLTextAreaElement;
}

describe("calendar form shell swap at 640 px", () => {
  afterEach(() => {
    cleanup();
  });

  it("keeps a half-written new task when the viewport grows past 640 px", async () => {
    setViewportWidth(400);

    render(
      <CreateCalendarFormContainer
        start={SLOT_START}
        end={SLOT_END}
        displayForm
        setDisplayForm={vi.fn()}
      />,
    );

    await screen.findByTestId("shell-drawer");
    fireEvent.change(titleInput(), { target: { value: "Dentist, bring referral" } });
    fireEvent.change(notesInput(), { target: { value: "ask about the night guard" } });

    await resizeTo(900);
    await screen.findByTestId("shell-modal");

    expect(screen.queryByTestId("shell-drawer")).toBeNull();
    expect(titleInput().value).toBe("Dentist, bring referral");
    expect(notesInput().value).toBe("ask about the night guard");
  });

  it("keeps a half-written new task when the viewport shrinks below 640 px", async () => {
    setViewportWidth(1024);

    render(
      <CreateCalendarFormContainer
        start={SLOT_START}
        end={SLOT_END}
        displayForm
        setDisplayForm={vi.fn()}
      />,
    );

    await screen.findByTestId("shell-modal");
    fireEvent.change(titleInput(), { target: { value: "Collect the parcel" } });

    await resizeTo(375);
    await screen.findByTestId("shell-drawer");

    expect(screen.queryByTestId("shell-modal")).toBeNull();
    expect(titleInput().value).toBe("Collect the parcel");
  });

  it("keeps unsaved edits to an existing task across the breakpoint", async () => {
    setViewportWidth(400);

    render(
      <EditCalendarFormContainer todo={todo} displayForm setDisplayForm={vi.fn()} />,
    );

    await screen.findByTestId("shell-drawer");
    // The edit form opens seeded from the todo; the user rewrites the title.
    expect(titleInput().value).toBe("Renew passport");
    fireEvent.change(titleInput(), { target: { value: "Renew passport — photos first" } });

    await resizeTo(900);
    await screen.findByTestId("shell-modal");

    expect(titleInput().value).toBe("Renew passport — photos first");
  });
});
