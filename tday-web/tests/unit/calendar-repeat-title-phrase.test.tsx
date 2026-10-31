// @vitest-environment jsdom

/**
 * A repeat the user picked from the Repeat sheet must not be overwritten by a recurrence
 * phrase still sitting in the title. TodoForm already passes `rruleOptions` to the title
 * field so the guard in NLPTitleInput holds; the calendar form body must do the same.
 */

import { act, cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { useRef, useState } from "react";
import { RRule, type Options } from "rrule";

vi.mock("react-i18next", () => ({
  useTranslation: () => ({
    t: (key: string) => key,
  }),
  initReactI18next: { type: "3rdParty", init: () => {} },
}));

vi.mock("@/components/Sidebar/List/query/get-list-meta", () => ({
  useListMetaData: () => ({ listMetaData: {}, listMetaLoading: false }),
}));

vi.mock("@/components/todo/component/NotesField/NotesField", () => ({
  default: () => <div data-testid="notes-field" />,
}));

vi.mock("@/components/todo/component/TodoForm/RepeatSuggestionChip", () => ({
  default: () => null,
}));

import CalendarTaskFormBody from "@/features/calendar/component/CalendarForm/Form/CalendarTaskFormBody";
import deriveRepeatType from "@/lib/deriveRepeatType";

let latestRrule: string | null = null;

function Harness() {
  const titleRef = useRef<HTMLDivElement | null>(null);
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [priority, setPriority] = useState<"Lowest" | "Low" | "Medium" | "High">("Low");
  const [priorityTouchedByUser, setPriorityTouchedByUser] = useState(false);
  const [dateRange, setDateRange] = useState({
    from: new Date("2026-04-03T14:00:00.000Z"),
    to: new Date("2026-04-03T14:00:00.000Z"),
  });
  const [listID, setListID] = useState<string | null>(null);
  const [rruleOptions, setRruleOptions] = useState<Partial<Options> | null>(null);
  latestRrule = rruleOptions ? new RRule(rruleOptions).toString() : null;

  return (
    <CalendarTaskFormBody
      titleRef={titleRef}
      title={title}
      setTitle={setTitle}
      description={description}
      setDescription={setDescription}
      priority={priority}
      setPriority={setPriority}
      priorityTouchedByUser={priorityTouchedByUser}
      setPriorityTouchedByUser={setPriorityTouchedByUser}
      dateRange={dateRange}
      setDateRange={setDateRange}
      listID={listID}
      setListID={setListID}
      rruleOptions={rruleOptions}
      setRruleOptions={setRruleOptions}
      derivedRepeatType={deriveRepeatType({ rruleOptions })}
      onSubmit={vi.fn()}
    />
  );
}

describe("calendar form repeat vs title recurrence phrase", () => {
  afterEach(() => {
    cleanup();
    latestRrule = null;
  });

  it("keeps a repeat picked from the sheet when the title still contains a recurrence phrase", () => {
    render(<Harness />);

    // Pick Weekly from the Repeat sheet.
    fireEvent.click(screen.getByRole("button", { name: /^repeat,/ }));
    fireEvent.click(screen.getByText("everyWeek"));
    expect(latestRrule).toBe("RRULE:FREQ=WEEKLY");

    // Type a title whose phrase names a different repeat; the next input event runs the NLP.
    const titleNode = screen.getByRole("textbox");
    titleNode.textContent = "Water plants every day";
    act(() => {
      fireEvent.input(titleNode);
    });

    expect(latestRrule).toBe("RRULE:FREQ=WEEKLY");
  });

  it("still captures a recurrence phrase typed into an unrepeated title", () => {
    render(<Harness />);

    const titleNode = screen.getByRole("textbox");
    titleNode.textContent = "Water plants every day";
    act(() => {
      fireEvent.input(titleNode);
    });

    expect(latestRrule).toBe("RRULE:FREQ=DAILY;INTERVAL=1");
  });
});
