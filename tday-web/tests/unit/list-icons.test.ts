import { describe, expect, it } from "vitest";
import {
  DEFAULT_LIST_ICON_KEY,
  getListIcon,
  normalizeListIconKey,
} from "@/lib/listIcons";

describe("list icon helpers", () => {
  it("defaults empty and unsupported keys to inbox", () => {
    expect(normalizeListIconKey(null)).toBe(DEFAULT_LIST_ICON_KEY);
    expect(normalizeListIconKey("")).toBe(DEFAULT_LIST_ICON_KEY);
    expect(normalizeListIconKey("not-a-real-icon")).toBe(DEFAULT_LIST_ICON_KEY);
    expect(getListIcon("not-a-real-icon")).toBe(getListIcon(DEFAULT_LIST_ICON_KEY));
  });

  it("normalizes keys and native legacy aliases", () => {
    expect(normalizeListIconKey(" Work ")).toBe("work");
    expect(normalizeListIconKey("briefcase")).toBe("work");
    expect(normalizeListIconKey("cocktail")).toBe("drink");
    expect(normalizeListIconKey("travel")).toBe("flight");
  });
});
