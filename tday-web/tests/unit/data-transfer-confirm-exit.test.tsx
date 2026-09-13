// @vitest-environment jsdom

/**
 * The eighth Modal call site, and the one the exit fix missed.
 *
 * `Modal` now holds its portal in the document for `MODAL_EXIT_MS` after it is closed, so the
 * scrim and the card have frames to animate out in. That hands every call site a new obligation
 * it did not have while the subtree was torn down on the same frame: whatever the card renders
 * has to survive the close, because the user is still looking at it.
 *
 * The import confirmation did not. Its open flag is `pending !== null` and its body is
 * `pending`'s dry-run preview, so pressing either button cleared both at once — the card spent
 * its whole 200 ms exit claiming the import it was asking about would add nothing, with the
 * "N ids were remapped" sentence gone from under it.
 *
 * `modal-exit-presence.test.tsx` proves the portal lingers. This proves what is inside it while
 * it does, which is a different assertion and was still false after that one passed.
 */

import React from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

// `count` is the whole point of the assertions below, so it is spelled into the key rather than
// interpolated into a sentence no test should have to match.
vi.mock("react-i18next", () => ({
  useTranslation: () => ({
    t: (key: string, options?: Record<string, unknown>) =>
      options && "count" in options ? `${key}:${String(options.count)}` : key,
  }),
}));

vi.mock("@/lib/navigation", () => ({
  Link: ({ children, ...rest }: { children?: React.ReactNode }) => <a {...rest}>{children}</a>,
}));

const toastMock = vi.fn();
vi.mock("@/hooks/use-toast", () => ({
  useToast: () => ({ toast: (...args: unknown[]) => toastMock(...args) }),
}));

vi.mock("@/hooks/useAppMode", () => ({ useIsLocalMode: () => false }));

// The card's own summary line reads these four; none of them is what this file is about.
vi.mock("@/features/todayTodos/query/get-todo", () => ({ useTodo: () => ({ todos: [] }) }));
vi.mock("@/features/floater/query/get-floater", () => ({ useFloater: () => ({ floaters: [] }) }));
vi.mock("@/features/completed/query/get-completedTodo", () => ({
  useCompletedTodo: () => ({ completedTodos: [] }),
}));
vi.mock("@/components/Sidebar/List/query/get-list-meta", () => ({
  useListMetaData: () => ({ listMetaData: {} }),
}));

const postMock = vi.fn();
vi.mock("@/lib/api-client", () => ({
  api: { POST: (...args: unknown[]) => postMock(...args) },
}));

vi.mock("@/lib/fileTransfer", () => ({
  downloadJson: vi.fn(),
  fileTimestamp: () => "stamp",
  readJsonFile: () => Promise.resolve({ export: "bundle" }),
}));

import { MODAL_EXIT_MS } from "@/components/ui/Modal";
import DataTransferCard from "@/components/settings/DataTransferCard";

/** A dry run that adds 5 items and remaps 2 ids — two numbers the exit must keep showing. */
const DRY_RUN = {
  dryRun: true,
  imported: {
    lists: 1,
    floaterLists: 0,
    todos: 3,
    floaters: 1,
    todoInstances: 0,
    completedTodos: 0,
    completedFloaters: 0,
    remappedIds: 2,
    preferencesApplied: false,
  },
};

function renderCard() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return render(
    <QueryClientProvider client={client}>
      <DataTransferCard />
    </QueryClientProvider>,
  );
}

/** Everything on screen, portal included — the confirmation card renders into `document.body`. */
const visibleText = () => document.body.textContent ?? "";

const confirmCard = () =>
  document.querySelector<HTMLElement>(".fixed.inset-0[data-state]");

/** Picks a bundle and waits for the dry run, which is what opens the confirmation. */
async function openConfirmation() {
  const input = document.querySelector<HTMLInputElement>('input[type="file"]');
  if (!input) throw new Error("the import row renders no file input");
  await act(async () => {
    fireEvent.change(input, {
      target: { files: [new File(["{}"], "tday-export.json", { type: "application/json" })] },
    });
  });
}

describe("the import confirmation while it is leaving", () => {
  beforeEach(() => {
    // Deliberately NOT `shouldAdvanceTime`: opening the dialog awaits two real promises (the
    // file read and the dry run), and under a loaded parallel suite that wall time is worth more
    // than the 200 ms exit being measured — the card would already be gone before the first
    // advance. Nothing here needs the clock to move on its own.
    vi.useFakeTimers();
    postMock.mockReset();
    postMock.mockResolvedValue(DRY_RUN);
    toastMock.mockReset();
  });

  afterEach(() => {
    cleanup();
    vi.useRealTimers();
  });

  it("asks about the bundle it previewed", async () => {
    renderCard();
    await openConfirmation();

    expect(visibleText()).toContain("data.confirmBody:5");
    expect(visibleText()).toContain("data.confirmRemapped:2");
  });

  it("keeps the counts it asked about on screen for the whole exit", async () => {
    renderCard();
    await openConfirmation();

    act(() => {
      screen.getByText("data.confirmCancel").click();
    });

    // Still here — `Modal` holds the portal — and still saying what it said. Reading the
    // cleared `pending` straight put "adds 0 items" in front of the user for 200 ms instead.
    expect(confirmCard()?.getAttribute("data-state")).toBe("closed");
    expect(visibleText()).toContain("data.confirmBody:5");
    expect(visibleText()).toContain("data.confirmRemapped:2");

    await act(async () => {
      await vi.advanceTimersByTimeAsync(MODAL_EXIT_MS - 20);
    });
    expect(visibleText()).toContain("data.confirmBody:5");

    await act(async () => {
      await vi.advanceTimersByTimeAsync(20);
    });
    expect(confirmCard()).toBeNull();
  });

  it("re-previews rather than replaying the last answer when a second bundle is picked", async () => {
    renderCard();
    await openConfirmation();

    act(() => {
      screen.getByText("data.confirmCancel").click();
    });
    await act(async () => {
      await vi.advanceTimersByTimeAsync(MODAL_EXIT_MS);
    });

    postMock.mockResolvedValue({
      ...DRY_RUN,
      imported: { ...DRY_RUN.imported, todos: 8, remappedIds: 0 },
    });
    await openConfirmation();

    // The retained bundle is a courtesy to the frames on their way out, never a cache: a fresh
    // dry run replaces it outright, including dropping the remapped line the last one carried.
    expect(visibleText()).toContain("data.confirmBody:10");
    expect(visibleText()).not.toContain("data.confirmRemapped");
  });
});
