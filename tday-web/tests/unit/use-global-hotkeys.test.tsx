// @vitest-environment jsdom
import { describe, expect, it, vi, beforeEach, afterEach } from "vitest";
import { cleanup, renderHook } from "@testing-library/react";
import { useGlobalHotkeys } from "@/hooks/useGlobalHotkeys";

function makeHandlers() {
  return {
    onNewTask: vi.fn(),
    onTogglePalette: vi.fn(),
    onShowShortcuts: vi.fn(),
  };
}

function press(
  key: string,
  init: KeyboardEventInit = {},
  target: EventTarget = document.body,
) {
  const event = new KeyboardEvent("keydown", {
    key,
    bubbles: true,
    cancelable: true,
    ...init,
  });
  target.dispatchEvent(event);
  return event;
}

describe("useGlobalHotkeys", () => {
  let input: HTMLInputElement;

  beforeEach(() => {
    input = document.createElement("input");
    document.body.appendChild(input);
  });

  afterEach(() => {
    input.remove();
    // Unmount the hook between tests: a still-mounted instance would consume
    // the next test's event with preventDefault and starve its assertions.
    cleanup();
  });

  it("opens the quick-add on plain N and claims the event", () => {
    const handlers = makeHandlers();
    renderHook(() => useGlobalHotkeys(handlers));

    const event = press("n");

    expect(handlers.onNewTask).toHaveBeenCalledTimes(1);
    expect(event.defaultPrevented).toBe(true);
  });

  it("ignores N while typing in an input", () => {
    const handlers = makeHandlers();
    renderHook(() => useGlobalHotkeys(handlers));

    press("n", {}, input);

    expect(handlers.onNewTask).not.toHaveBeenCalled();
  });

  it("ignores N with a command modifier held", () => {
    const handlers = makeHandlers();
    renderHook(() => useGlobalHotkeys(handlers));

    press("n", { metaKey: true });
    press("n", { ctrlKey: true });

    expect(handlers.onNewTask).not.toHaveBeenCalled();
  });

  it("toggles the palette on Cmd+K and Ctrl+K, even from an input", () => {
    const handlers = makeHandlers();
    renderHook(() => useGlobalHotkeys(handlers));

    press("k", { metaKey: true });
    press("k", { ctrlKey: true }, input);

    expect(handlers.onTogglePalette).toHaveBeenCalledTimes(2);
  });

  it("leaves Cmd+K alone when a page handler already claimed it", () => {
    const handlers = makeHandlers();
    renderHook(() => useGlobalHotkeys(handlers));

    // Mirrors the floater search: a document-level listener preventDefaults
    // before the window-level global layer sees the event.
    const claim = (event: KeyboardEvent) => event.preventDefault();
    document.addEventListener("keydown", claim);
    press("k", { metaKey: true });
    document.removeEventListener("keydown", claim);

    expect(handlers.onTogglePalette).not.toHaveBeenCalled();
  });

  it("shows the shortcuts overview on ?", () => {
    const handlers = makeHandlers();
    renderHook(() => useGlobalHotkeys(handlers));

    press("?", { shiftKey: true });

    expect(handlers.onShowShortcuts).toHaveBeenCalledTimes(1);
  });

  it("ignores ? while typing in an input", () => {
    const handlers = makeHandlers();
    renderHook(() => useGlobalHotkeys(handlers));

    press("?", { shiftKey: true }, input);

    expect(handlers.onShowShortcuts).not.toHaveBeenCalled();
  });

  it("stops listening after unmount", () => {
    const handlers = makeHandlers();
    const { unmount } = renderHook(() => useGlobalHotkeys(handlers));
    unmount();

    press("n");

    expect(handlers.onNewTask).not.toHaveBeenCalled();
  });
});
