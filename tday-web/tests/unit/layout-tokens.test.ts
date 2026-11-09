import { describe, expect, it } from "vitest";

import { LAYOUT_TOKENS } from "@/generated/layout-tokens";
import { EMPTY_SLOT_MIN_HEIGHT, contentGapPx, contentGapStyle } from "@/lib/layoutTokens";

/**
 * The cases mirror `LayoutTokensTest` in `:shared` — the Kotlin rule and this one are
 * the same arithmetic, and these are the questions both are asked.
 */
describe("layout tokens", () => {
  it("never goes under the fade band", () => {
    expect(contentGapPx(640, 400)).toBe(LAYOUT_TOKENS.contentGapMin);
    expect(contentGapPx(667, 254)).toBe(LAYOUT_TOKENS.contentGapMin);
  });

  it("is capped on a tall window", () => {
    expect(contentGapPx(1400, 280)).toBe(LAYOUT_TOKENS.contentGapMax);
  });

  it("spends exactly what reaches the line", () => {
    const gap = contentGapPx(800, 250);
    expect(250 + gap).toBeCloseTo(LAYOUT_TOKENS.contentStartRatio * 800, 9);
  });

  it("writes a plain-px fallback before the svh declaration", () => {
    const css = contentGapStyle(246.5);
    expect(css.startsWith(`height:${LAYOUT_TOKENS.contentGapMin}px;height:clamp(`)).toBe(true);
    expect(css).toContain("calc(38svh - 246.5px)");
    expect(css).toContain(`${LAYOUT_TOKENS.contentGapMax}px)`);
  });

  it("gives the empty scene the same ratio, in the same unit", () => {
    expect(EMPTY_SLOT_MIN_HEIGHT).toBe("36svh");
  });
});
