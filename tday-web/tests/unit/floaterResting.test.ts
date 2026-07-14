import { describe, expect, it } from "vitest";

import { floaterRestingTier } from "@/lib/floaterResting";

const DAY = 86_400_000;
const NOW = 1_800_000_000_000;

describe("floaterRestingTier", () => {
  it("is active when fresh", () => {
    expect(floaterRestingTier(NOW - 5 * DAY, NOW)).toBe("active");
    expect(floaterRestingTier(NOW, NOW)).toBe("active");
  });

  it("fades at 30 days", () => {
    expect(floaterRestingTier(NOW - 29 * DAY, NOW)).toBe("active");
    expect(floaterRestingTier(NOW - 30 * DAY, NOW)).toBe("fading");
    expect(floaterRestingTier(NOW - 89 * DAY, NOW)).toBe("fading");
  });

  it("rests at 90 days", () => {
    expect(floaterRestingTier(NOW - 90 * DAY, NOW)).toBe("resting");
    expect(floaterRestingTier(NOW - 400 * DAY, NOW)).toBe("resting");
  });

  it("is active with no timestamp", () => {
    expect(floaterRestingTier(null, NOW)).toBe("active");
    expect(floaterRestingTier(0, NOW)).toBe("active");
  });
});
