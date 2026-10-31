import { execFileSync } from "node:child_process";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";
import { ILLUSTRATION_SCENES } from "@/components/illustrations/scenes.generated";

// The illustrated states are generated for web, Android and iOS from
// design/illustrations by scripts/illustrations.mjs. This is the drift gate:
// an edited scene or palette without a regeneration fails here.
describe("illustrations", () => {
  it("generated artwork matches design/illustrations", () => {
    const script = resolve(__dirname, "../../../scripts/illustrations.mjs");
    expect(() => execFileSync(process.execPath, [script, "--check"], { stdio: "pipe" })).not.toThrow();
  });

  it("every scene the screens use exists", () => {
    for (const id of ["cal", "done", "overdue", "box", "anytime", "history", "search", "lost", "oops", "update", "srv"]) {
      expect(ILLUSTRATION_SCENES).toHaveProperty(id);
    }
  });
});
