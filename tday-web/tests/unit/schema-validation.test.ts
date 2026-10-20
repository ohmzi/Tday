import { describe, expect, it } from "vitest";

import { floaterSchema, todoSchema } from "@/schema";

/**
 * The messages the create/patch helpers surface to the user come straight off
 * `error.issues[0].message`. These pin them (and the `issues` shape zod 4
 * reports them through) so a validation-library upgrade cannot quietly swap a
 * hand-written sentence for the library's default one.
 */

const valid = {
  title: "Buy milk",
  description: null,
  priority: "Low",
  due: new Date(2026, 9, 1, 9, 30, 45, 123),
  rrule: null,
};

function firstMessage(result: { success: boolean; error?: { issues: { message: string }[] } }) {
  expect(result.success).toBe(false);
  return result.error?.issues[0].message;
}

describe("todoSchema", () => {
  it("accepts a well-formed payload and trims the due time to the minute", () => {
    const parsed = todoSchema.safeParse(valid);

    expect(parsed.success).toBe(true);
    if (parsed.success) {
      expect(parsed.data.due.getSeconds()).toBe(0);
      expect(parsed.data.due.getMilliseconds()).toBe(0);
    }
  });

  it("reports the hand-written message for a blank or missing title", () => {
    expect(firstMessage(todoSchema.safeParse({ ...valid, title: "   " }))).toBe(
      "title cannot be left empty",
    );
    expect(firstMessage(todoSchema.safeParse({ ...valid, title: undefined }))).toBe(
      "title cannot be left empty",
    );
  });

  it("reports the hand-written message for an unknown priority", () => {
    expect(firstMessage(todoSchema.safeParse({ ...valid, priority: "Urgent" }))).toBe(
      "priority must be one of: lowest, low, medium, high",
    );
  });

  it("reports the hand-written message for a due value that is not a date", () => {
    expect(firstMessage(todoSchema.safeParse({ ...valid, due: "tomorrow" }))).toBe(
      "end date is not identified",
    );
  });

  it("accepts a null description", () => {
    expect(todoSchema.safeParse({ ...valid, description: null }).success).toBe(true);
  });
});

describe("floaterSchema", () => {
  it("reports the hand-written message for an unknown priority", () => {
    expect(
      firstMessage(floaterSchema.safeParse({ title: "Idea", priority: "Someday" })),
    ).toBe("priority must be one of: lowest, low, medium, high");
  });
});
