// @vitest-environment jsdom

/**
 * The scope prompt for a recurring task. When the repeat changed, "this occurrence" is
 * disabled with a line saying repeats apply to the whole series. "All" carries the
 * occurrence's own date as the checksum, so an unchanged date does not move the series.
 */

import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { TodoItemType } from "@/types";

const editCalendarTodo = vi.fn();
const editCalendarTodoInstance = vi.fn();

vi.mock("react-i18next", () => ({
  useTranslation: () => ({
    t: (key: string) => key,
  }),
  initReactI18next: { type: "3rdParty", init: () => {} },
}));

vi.mock("@/features/calendar/query/update-calendar-todo", () => ({
  useEditCalendarTodo: () => ({ editCalendarTodo, editTodoStatus: "idle" }),
}));

vi.mock("@/features/calendar/query/update-calendar-todo-instance", () => ({
  useEditCalendarTodoInstance: () => ({ editCalendarTodoInstance }),
}));

import ConfirmEditAllDialog from "@/features/calendar/component/ConfirmationModals/ConfirmEditAll";

const OCCURRENCE_DUE = new Date("2026-04-05T14:00:00.000Z");

function buildTodo(): TodoItemType {
  return {
    id: "todo-1:123",
    title: "Standup",
    description: null,
    pinned: false,
    createdAt: OCCURRENCE_DUE,
    order: 1,
    priority: "Low",
    due: OCCURRENCE_DUE,
    rrule: "RRULE:FREQ=DAILY",
    timeZone: "UTC",
    userID: "user-1",
    completed: false,
    exdates: [],
    instanceDate: OCCURRENCE_DUE,
    listID: null,
    instances: [],
  } as TodoItemType;
}

function renderDialog(repeatChanged: boolean) {
  render(
    <ConfirmEditAllDialog
      todo={buildTodo()}
      rruleChecksum="RRULE:FREQ=DAILY"
      dateRangeChecksum={OCCURRENCE_DUE.toISOString()}
      setDisplayForm={vi.fn()}
      editAllDialogOpen
      setEditAllDialogOpen={vi.fn()}
      repeatChanged={repeatChanged}
    />,
  );
}

describe("ConfirmEditAll scope prompt", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(cleanup);

  it("disables this-occurrence and explains series scope when the repeat changed", () => {
    renderDialog(true);

    expect(screen.getByRole("button", { name: "editAll.editInstance" }).hasAttribute("disabled")).toBe(true);
    expect(screen.getByText("editAll.repeatSeriesNote")).toBeTruthy();
  });

  it("keeps this-occurrence available and shows no note for a non-repeat edit", () => {
    renderDialog(false);

    expect(screen.getByRole("button", { name: "editAll.editInstance" }).hasAttribute("disabled")).toBe(false);
    expect(screen.queryByText("editAll.repeatSeriesNote")).toBeNull();
  });

  it("all occurrences sends the occurrence date as the checksum so the series date is not moved", () => {
    renderDialog(true);

    fireEvent.click(screen.getByRole("button", { name: "editAll.editAll" }));

    expect(editCalendarTodo).toHaveBeenCalledTimes(1);
    const payload = editCalendarTodo.mock.calls[0][0];
    expect(payload.dateRangeChecksum).toBe(OCCURRENCE_DUE.toISOString());
    expect(payload.rruleChecksum).toBe("RRULE:FREQ=DAILY");
    expect(editCalendarTodoInstance).not.toHaveBeenCalled();
  });
});
