// @vitest-environment jsdom
import { describe, expect, it, afterEach, vi } from "vitest";
import { scrollBy, scrollIntoView, scrollTo } from "@/lib/scroll";
import { installReducedMotion } from "../setup/reduced-motion";

const REAL_MATCH_MEDIA = window.matchMedia;

afterEach(() => {
  window.matchMedia = REAL_MATCH_MEDIA;
});

/** A stand-in for whatever element a call site happens to be holding. */
function target() {
  return {
    scrollIntoView: vi.fn(),
    scrollTo: vi.fn(),
    scrollBy: vi.fn(),
  } as unknown as Element & {
    scrollIntoView: ReturnType<typeof vi.fn>;
    scrollTo: ReturnType<typeof vi.fn>;
    scrollBy: ReturnType<typeof vi.fn>;
  };
}

describe("scrollIntoView", () => {
  it("smooth-scrolls when the user has not asked for less motion", () => {
    installReducedMotion(false);
    const element = target();
    scrollIntoView(element, { block: "center" });
    expect(element.scrollIntoView).toHaveBeenCalledWith({
      block: "center",
      behavior: "smooth",
    });
  });

  it("jumps instead of travelling under reduced motion — the destination without the trip", () => {
    installReducedMotion(true);
    const element = target();
    scrollIntoView(element, { block: "center" });
    expect(element.scrollIntoView).toHaveBeenCalledWith({
      block: "center",
      behavior: "auto",
    });
  });

  it("reads the preference per call, so a mid-session flip is honoured", () => {
    const preference = installReducedMotion(false);
    const element = target();

    scrollIntoView(element);
    preference.set(true);
    scrollIntoView(element);

    expect(element.scrollIntoView.mock.calls[0][0].behavior).toBe("smooth");
    expect(element.scrollIntoView.mock.calls[1][0].behavior).toBe("auto");
  });

  it("downgrades only — an explicit `auto` is never promoted to smooth", () => {
    installReducedMotion(false);
    const element = target();
    scrollIntoView(element, { behavior: "auto", block: "start" });
    expect(element.scrollIntoView).toHaveBeenCalledWith({
      behavior: "auto",
      block: "start",
    });
  });

  it("does nothing with a missing target, so call sites need no `?.` of their own", () => {
    installReducedMotion(false);
    expect(() => scrollIntoView(null)).not.toThrow();
    expect(() => scrollIntoView(undefined, { block: "center" })).not.toThrow();
  });
});

describe("scrollTo and scrollBy", () => {
  it("carry the same downgrade as scrollIntoView", () => {
    installReducedMotion(true);
    const element = target();

    scrollTo(element, { top: 0 });
    scrollBy(element, { top: 40 });

    expect(element.scrollTo).toHaveBeenCalledWith({ top: 0, behavior: "auto" });
    expect(element.scrollBy).toHaveBeenCalledWith({ top: 40, behavior: "auto" });
  });

  it("smooth-scroll a scroller when motion is fine", () => {
    installReducedMotion(false);
    const element = target();

    scrollTo(element, { top: 0 });
    scrollBy(element, { top: 40 });

    expect(element.scrollTo).toHaveBeenCalledWith({ top: 0, behavior: "smooth" });
    expect(element.scrollBy).toHaveBeenCalledWith({ top: 40, behavior: "smooth" });
  });
});
