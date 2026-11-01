import { describe, expect, it } from "vitest";
import { buildTaskShareText } from "@/lib/listShareText";

/**
 * The plain text a copied task becomes: title and notes only. It used to carry
 * "Priority: High" and "Due: …" lines too. Both are scheduling details rather
 * than what the task says, so they're gone on all three platforms.
 */
describe("buildTaskShareText", () => {
  it("is the title alone for a bare task", () => {
    expect(
      buildTaskShareText({ todo: { title: "Buy milk" } }),
    ).toBe("Buy milk");
  });

  it("appends flattened notes under the title", () => {
    const text = buildTaskShareText({
      todo: { title: "Buy milk", description: "Semi-skimmed\n2 litres" },
    });
    expect(text.split("\n")).toEqual(["Buy milk", "Semi-skimmed", "2 litres"]);
  });

  it("never carries a priority line, whatever the task's tier", () => {
    // The field is not even part of the input shape any more; a caller that had
    // one to offer simply cannot hand it over.
    const text = buildTaskShareText({
      todo: { title: "Pay rent" },
    });
    expect(text.toLowerCase()).not.toContain("priority");
  });

  it("never carries the due date or time", () => {
    // Also not part of the input shape: a scheduled task copies as title and notes.
    const text = buildTaskShareText({
      todo: { title: "Update passport details", description: "Bring the old one" },
    });
    expect(text).toBe("Update passport details\nBring the old one");
  });
});
