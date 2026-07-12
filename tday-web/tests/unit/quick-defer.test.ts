import { describe, expect, it } from "vitest";
import { quickDeferOptions } from "@/lib/quickDefer";

describe("quickDeferOptions", () => {
  it("computes the four instants from a morning reference", () => {
    const now = new Date(2026, 6, 8, 10, 15); // Wednesday 2026-07-08 10:15 local
    const options = quickDeferOptions(now);
    const byKey = Object.fromEntries(options.map((o) => [o.key, o.due]));

    expect(byKey.laterToday).toEqual(new Date(2026, 6, 8, 13, 15));
    expect(byKey.tonight).toEqual(new Date(2026, 6, 8, 19, 0));
    expect(byKey.tomorrow).toEqual(new Date(2026, 6, 9, 9, 0));
    // Next Monday after a Wednesday is 2026-07-13.
    expect(byKey.nextWeek).toEqual(new Date(2026, 6, 13, 9, 0));
  });

  it("hides Tonight once the evening cutoff has passed", () => {
    const evening = new Date(2026, 6, 8, 19, 45);
    const keys = quickDeferOptions(evening).map((o) => o.key);
    expect(keys).not.toContain("tonight");
    expect(keys).toEqual(["laterToday", "tomorrow", "nextWeek"]);
  });
});
