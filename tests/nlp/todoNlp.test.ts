import { parseTodoTitle } from "@/lib/todoNlp";

describe("parseTodoTitle", () => {
  test("extracts date phrase and keeps clean title", () => {
    const reference = Date.UTC(2026, 1, 23, 12, 0, 0);
    const result = parseTodoTitle({
      text: "clean car tomorrow at 9pm",
      locale: "en-US",
      referenceEpochMs: reference,
      timezoneOffsetMinutes: 0,
      defaultDurationMinutes: 60,
    });

    expect(result.cleanTitle).toBe("clean car");
    expect(result.matchedText?.toLowerCase()).toContain("tomorrow");
    expect(result.startEpochMs).not.toBeNull();
    expect(result.dueEpochMs).not.toBeNull();
    expect((result.dueEpochMs ?? 0) - (result.startEpochMs ?? 0)).toBe(60 * 60 * 1000);
  });

  test("returns unchanged title when no date phrase exists", () => {
    const result = parseTodoTitle({
      text: "buy groceries and milk",
      locale: "en-US",
    });

    expect(result.cleanTitle).toBe("buy groceries and milk");
    expect(result.startEpochMs).toBeNull();
    expect(result.dueEpochMs).toBeNull();
    expect(result.matchedText).toBeNull();
  });
});
