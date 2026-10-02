// @vitest-environment jsdom

import { cleanup, render } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";
import { GuideIcon } from "@/features/guide/GuideIcon";

afterEach(cleanup);

describe("GuideIcon", () => {
  it("draws the activity glyph the crash-reports topic names, not the fallback", () => {
    const { container } = render(<GuideIcon name="activity" />);

    expect(container.querySelector("svg")?.getAttribute("class")).toContain("lucide-activity");
  });

  it("falls back to the help glyph for a name it does not know", () => {
    const { container } = render(<GuideIcon name="no-such-glyph" />);

    expect(container.querySelector("svg")?.getAttribute("class")).toContain("lucide-circle-help");
  });
});
