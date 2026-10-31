// @vitest-environment jsdom

/**
 * The header search button opens the search field on every screen that shows it.
 *
 * Two headers carry the button:
 * - `MobileSearchHeader` on the non-root pages (Today, Scheduled, Overdue,
 *   list, calendar, completed, settings, guide, ...).
 * - `RootFeedHeroHeader` on the two root feeds (scheduled home, floater home).
 *
 * `MobileSearchHeader` closes itself on a click that lands outside the bar. The
 * bar's own button turns into the input on the same click, so the target the
 * document listener sees has already left the DOM. `Node.contains` on a detached
 * node is false, so the bar read its own press as an outside one and closed at
 * once. A browser flushes React's commit between the React root listener and the
 * document listener (microtask checkpoint); `fireEvent` inside `act` does not,
 * which is why the plain click test alone passes. `clickLikeBrowser` reproduces
 * that ordering by flushing a commit from a bubble listener between the two.
 */

import { useState, type ReactElement } from "react";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { flushSync } from "react-dom";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import MobileSearchHeader from "@/components/ui/MobileSearchHeader";
import RootFeedHeroHeader from "@/components/app/RootFeedHeroHeader";
import { nativeAppScrollAttribute } from "@/components/app/nativeAppLayout";

vi.mock("@/lib/haptics", () => ({
  hapticButtonTap: vi.fn(),
  hapticDismiss: vi.fn(),
}));

vi.mock("@/components/app/NativeAppBrandButton", () => ({
  default: () => <span data-testid="brand" />,
}));

/**
 * Dispatches a click the way a user's click reaches the page: React's root
 * listener runs, the browser then checkpoints microtasks (committing React's
 * update and its effects), and only then does the event reach `document`.
 */
function clickLikeBrowser(target: Element) {
  const globals = globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean };
  const previous = globals.IS_REACT_ACT_ENVIRONMENT;
  globals.IS_REACT_ACT_ENVIRONMENT = false;
  const commitBetweenListeners = () => flushSync(() => {});
  // The React root container is appended to body, so body sits between it and
  // document on the bubble path.
  document.body.addEventListener("click", commitBetweenListeners);
  try {
    target.dispatchEvent(new MouseEvent("click", { bubbles: true, cancelable: true }));
    // Whatever the document listener did is committed after the event, as well.
    flushSync(() => {});
  } finally {
    document.body.removeEventListener("click", commitBetweenListeners);
    globals.IS_REACT_ACT_ENVIRONMENT = previous;
  }
}

const renderWithRouter = (ui: ReactElement) =>
  render(<MemoryRouter>{ui}</MemoryRouter>);

beforeEach(() => {
  // jsdom has no layout; the root feed's rAF bails on a zero-width header.
  Object.defineProperty(HTMLElement.prototype, "clientWidth", {
    configurable: true,
    get: () => 390,
  });
});

afterEach(() => {
  cleanup();
  document.querySelectorAll(`[${nativeAppScrollAttribute}]`).forEach((node) => node.remove());
});

describe("MobileSearchHeader search button", () => {
  it("opens the search field when the button is clicked", () => {
    renderWithRouter(<MobileSearchHeader placeholder="Search tasks..." />);

    expect(screen.queryByPlaceholderText("Search tasks...")).toBeNull();
    fireEvent.click(screen.getByRole("button", { name: "Search" }));

    expect(screen.getByPlaceholderText("Search tasks...")).toBeTruthy();
  });

  it("stays open when the click is committed before the document sees it, as in a browser", () => {
    renderWithRouter(<MobileSearchHeader placeholder="Search tasks..." />);

    clickLikeBrowser(screen.getByRole("button", { name: "Search" }));

    expect(screen.getByPlaceholderText("Search tasks...")).toBeTruthy();
  });

  it("still closes when the user clicks outside the bar", () => {
    renderWithRouter(
      <>
        <MobileSearchHeader placeholder="Search tasks..." />
        <p>page body</p>
      </>,
    );

    clickLikeBrowser(screen.getByRole("button", { name: "Search" }));
    expect(screen.getByPlaceholderText("Search tasks...")).toBeTruthy();

    clickLikeBrowser(screen.getByText("page body"));
    expect(screen.queryByPlaceholderText("Search tasks...")).toBeNull();
  });
});

describe("RootFeedHeroHeader search button", () => {
  function Harness() {
    const [open, setOpen] = useState(false);
    const [query, setQuery] = useState("");
    return (
      <RootFeedHeroHeader
        title="T'Day"
        mark="timeOfDay"
        searchOpen={open}
        searchQuery={query}
        searchPlaceholder="Search Scheduled Task"
        searchPlaceholderShort="Search"
        searchAriaLabel="Search tasks"
        createListAriaLabel="New list"
        settingsAriaLabel="Settings"
        onSearchQueryChange={setQuery}
        onSearchOpenChange={(next) => {
          setOpen(next);
          setQuery("");
        }}
        onCreateList={() => {}}
        onOpenSettings={() => {}}
      />
    );
  }

  it("opens the search field when the button is clicked", () => {
    const scroller = document.createElement("div");
    scroller.setAttribute(nativeAppScrollAttribute, "");
    document.body.appendChild(scroller);
    render(<Harness />, { container: scroller });

    const input = screen.getByPlaceholderText("Search Scheduled Task") as HTMLInputElement;
    expect(input.disabled).toBe(true);

    fireEvent.click(screen.getByRole("button", { name: "Search tasks" }));

    expect(input.disabled).toBe(false);
    expect(document.activeElement).toBe(input);
  });
});
