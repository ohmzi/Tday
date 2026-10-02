// TEST-CRASH: proves every temporary crash id builds its documented error type and a message the
// web scrubber leaves untouched, so a missing Sentry issue means a delivery problem, not a typo.
import { describe, expect, it } from "vitest";
import {
  TEST_CRASH_IDS,
  TEST_CRASH_PLANS,
  buildTestCrashError,
  testCrashMessage,
} from "@/lib/testCrash";
import { redactDiagnosticText } from "@/lib/observability/webScrub";

describe("TEST-CRASH ids", () => {
  it("defines the expected set", () => {
    expect(TEST_CRASH_IDS).toHaveLength(20);
  });

  it.each(TEST_CRASH_IDS)("%s builds its documented error with a scrub-proof message", (id) => {
    const plan = TEST_CRASH_PLANS[id];
    const error = buildTestCrashError(id);
    const message = testCrashMessage(id);

    expect(error.message).toBe(message);
    expect(error.name).toBe(plan.kind);
    expect(message.startsWith(`TEST-CRASH ${id}: `)).toBe(true);
    expect(message.length).toBeLessThanOrEqual(80);
    // The same limit on every client, so one search finds the report from all three.
    expect(id.length).toBeLessThanOrEqual(18);
    expect(/^[\x20-\x7e]+$/.test(message)).toBe(true);
    expect(redactDiagnosticText(message)).toBe(message);
  });
});
