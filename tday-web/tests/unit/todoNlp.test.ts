import { describe, expect, it } from "vitest";

import { parseRecurrencePriority, parseTodoTitle } from "@/lib/todoNlp";

describe("parseRecurrencePriority", () => {
  it("captures the 5 recurrence presets and strips the phrase", () => {
    expect(parseRecurrencePriority("water plants every day")).toEqual({
      cleanTitle: "water plants",
      rrule: "RRULE:FREQ=DAILY;INTERVAL=1",
      priority: null,
    });
    expect(parseRecurrencePriority("standup weekly").rrule).toBe(
      "RRULE:FREQ=WEEKLY;INTERVAL=1",
    );
    expect(parseRecurrencePriority("gym every weekday").rrule).toBe(
      "RRULE:FREQ=WEEKLY;INTERVAL=1;BYDAY=MO,TU,WE,TH,FR",
    );
    expect(parseRecurrencePriority("rent monthly").rrule).toBe(
      "RRULE:FREQ=MONTHLY;INTERVAL=1",
    );
    expect(parseRecurrencePriority("taxes annually").rrule).toBe(
      "RRULE:FREQ=YEARLY;INTERVAL=1",
    );
  });

  it("prefers weekday over the substring week", () => {
    expect(parseRecurrencePriority("report weekdays").rrule).toBe(
      "RRULE:FREQ=WEEKLY;INTERVAL=1;BYDAY=MO,TU,WE,TH,FR",
    );
  });

  it("captures priority markers and words", () => {
    expect(parseRecurrencePriority("call mom !!")).toEqual({
      cleanTitle: "call mom",
      rrule: null,
      priority: "High",
    });
    expect(parseRecurrencePriority("email boss !").priority).toBe("Medium");
    expect(parseRecurrencePriority("fix bug high priority").priority).toBe("High");
    expect(parseRecurrencePriority("buy milk low").priority).toBe("Low");
  });

  it("does not treat a non-trailing bare word as priority", () => {
    expect(parseRecurrencePriority("buy low-fat milk").priority).toBeNull();
    expect(parseRecurrencePriority("review highlights").priority).toBeNull();
  });

  it("captures both recurrence and priority together", () => {
    expect(parseRecurrencePriority("gym every day !!")).toEqual({
      cleanTitle: "gym",
      rrule: "RRULE:FREQ=DAILY;INTERVAL=1",
      priority: "High",
    });
  });

  it("leaves plain titles untouched", () => {
    expect(parseRecurrencePriority("buy groceries")).toEqual({
      cleanTitle: "buy groceries",
      rrule: null,
      priority: null,
    });
  });
});

describe("parseTodoTitle recurrence/priority", () => {
  it("merges recurrence + priority alongside a parsed date", () => {
    const out = parseTodoTitle({
      text: "standup tomorrow every weekday !!",
      referenceEpochMs: Date.UTC(2026, 0, 1, 9, 0, 0),
    });
    expect(out.rrule).toBe("RRULE:FREQ=WEEKLY;INTERVAL=1;BYDAY=MO,TU,WE,TH,FR");
    expect(out.priority).toBe("High");
    expect(out.cleanTitle).toBe("standup");
    expect(out.dueEpochMs).not.toBeNull();
  });

  it("captures recurrence/priority even with no date", () => {
    const out = parseTodoTitle({ text: "water plants every day" });
    expect(out.dueEpochMs).toBeNull();
    expect(out.rrule).toBe("RRULE:FREQ=DAILY;INTERVAL=1");
    expect(out.cleanTitle).toBe("water plants");
  });
});
