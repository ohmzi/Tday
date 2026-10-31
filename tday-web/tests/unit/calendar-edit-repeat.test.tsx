// @vitest-environment jsdom

/**
 * Editing a repeating task in the calendar: any edit to a recurring task opens the scope
 * prompt. A changed repeat is series-wide, so the prompt is told to disable "this occurrence".
 */

import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { RRule } from "rrule";
import type { TodoItemType } from "@/types";

const editCalendarTodo = vi.fn();

vi.mock("react-i18next", () => ({
  useTranslation: () => ({
    t: (key: string) => key,
  }),
  initReactI18next: { type: "3rdParty", init: () => {} },
}));

vi.mock("@/features/calendar/query/update-calendar-todo", () => ({
  useEditCalendarTodo: () => ({ editCalendarTodo, editTodoStatus: "idle" }),
}));

vi.mock("@/features/calendar/component/ConfirmationModals/ConfirmCancelEdit", () => ({
  default: () => null,
}));

// Reports the scope prompt's open state and the flag it was given, without its portal.
vi.mock("@/features/calendar/component/ConfirmationModals/ConfirmEditAll", () => ({
  default: ({ editAllDialogOpen, repeatChanged }: { editAllDialogOpen: boolean; repeatChanged: boolean }) =>
    editAllDialogOpen ? (
      <div data-testid="edit-all-dialog" data-repeat-changed={String(repeatChanged)} />
    ) : null,
}));

vi.mock("@/features/calendar/component/CalendarForm/Form/LazyCalendarTaskFormBody", () => ({
  default: () => null,
}));

import EditModal from "@/features/calendar/component/CalendarForm/Form/ModalForm/EditModal";
import type { CalendarTaskFormState } from "@/features/calendar/hooks/useCalendarTaskFormState";

const DUE = new Date("2026-04-03T14:00:00.000Z");

function buildTodo(overrides: Partial<TodoItemType> = {}): TodoItemType {
  return {
    id: "todo-1",
    title: "Standup",
    description: null,
    pinned: false,
    createdAt: DUE,
    order: 1,
    priority: "Low",
    due: DUE,
    rrule: "RRULE:FREQ=DAILY",
    timeZone: "UTC",
    userID: "user-1",
    completed: false,
    exdates: [],
    instanceDate: null,
    listID: null,
    instances: [],
    ...overrides,
  } as TodoItemType;
}

function buildForm(overrides: Partial<CalendarTaskFormState> = {}): CalendarTaskFormState {
  return {
    title: "Standup",
    setTitle: vi.fn(),
    description: "",
    setDescription: vi.fn(),
    priority: "Low",
    setPriority: vi.fn(),
    priorityTouchedByUser: true,
    setPriorityTouchedByUser: vi.fn(),
    dateRange: { from: DUE, to: DUE },
    setDateRange: vi.fn(),
    rruleOptions: { freq: RRule.DAILY },
    setRruleOptions: vi.fn(),
    repeatTouchedByUser: false,
    setRepeatTouchedByUser: vi.fn(),
    listID: null,
    setListID: vi.fn(),
    derivedRepeatType: "Daily",
    ...overrides,
  };
}

function renderEdit(todo: TodoItemType, form: CalendarTaskFormState) {
  render(<EditModal todo={todo} displayForm setDisplayForm={vi.fn()} form={form} />);
  fireEvent.click(screen.getByRole("button", { name: "save" }));
}

describe("calendar edit: recurring task scope prompt", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(cleanup);

  it("changing the repeat opens the scope prompt flagged as a repeat change, without saving yet", () => {
    renderEdit(
      buildTodo(),
      buildForm({ rruleOptions: { freq: RRule.WEEKLY }, derivedRepeatType: "Weekly" }),
    );

    expect(editCalendarTodo).not.toHaveBeenCalled();
    expect(screen.getByTestId("edit-all-dialog").getAttribute("data-repeat-changed")).toBe("true");
  });

  it("clearing the repeat also goes through the scope prompt", () => {
    renderEdit(buildTodo(), buildForm({ rruleOptions: null, derivedRepeatType: null }));

    expect(editCalendarTodo).not.toHaveBeenCalled();
    expect(screen.getByTestId("edit-all-dialog").getAttribute("data-repeat-changed")).toBe("true");
  });

  it("an unchanged repeat opens the scope prompt without the repeat flag", () => {
    renderEdit(buildTodo(), buildForm({ title: "Standup, renamed" }));

    expect(editCalendarTodo).not.toHaveBeenCalled();
    expect(screen.getByTestId("edit-all-dialog").getAttribute("data-repeat-changed")).toBe("false");
  });

  it("a one-off task gaining a repeat saves directly against a null checksum", () => {
    renderEdit(
      buildTodo({ rrule: null }),
      buildForm({ rruleOptions: { freq: RRule.WEEKLY }, derivedRepeatType: "Weekly" }),
    );

    expect(screen.queryByTestId("edit-all-dialog")).toBeNull();
    expect(editCalendarTodo).toHaveBeenCalledTimes(1);
    const payload = editCalendarTodo.mock.calls[0][0];
    expect(payload.rrule).toBe("RRULE:FREQ=WEEKLY");
    expect(payload.rruleChecksum).toBeNull();
  });
});
