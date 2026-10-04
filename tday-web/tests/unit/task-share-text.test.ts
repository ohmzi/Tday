import { describe, expect, it } from "vitest";
import type { TFunction } from "i18next";
import { buildTaskShareText } from "@/lib/listShareText";

/**
 * The plain text a copied task becomes. It used to carry a "Priority: High" line
 * too; copying a task should copy the task, and the urgency tier is a list-view
 * marking rather than part of what the task says, so the line is gone on all
 * three platforms.
 */
const t = ((key: string) => key) as unknown as TFunction;

describe("buildTaskShareText", () => {
  it("is the title alone for a bare task", () => {
    expect(
      buildTaskShareText({ todo: { title: "Buy milk" }, lang: "en", t }),
    ).toBe("Buy milk");
  });

  it("appends flattened notes under the title", () => {
    const text = buildTaskShareText({
      todo: { title: "Buy milk", description: "Semi-skimmed\n2 litres" },
      lang: "en",
      t,
    });
    expect(text.split("\n")).toEqual(["Buy milk", "Semi-skimmed", "2 litres"]);
  });

  it("never carries a priority line, whatever the task's tier", () => {
    // The field is not even part of the input shape any more; a caller that had
    // one to offer simply cannot hand it over.
    const text = buildTaskShareText({
      todo: { title: "Pay rent" },
      lang: "en",
      t,
    });
    expect(text.toLowerCase()).not.toContain("priority");
  });
});
