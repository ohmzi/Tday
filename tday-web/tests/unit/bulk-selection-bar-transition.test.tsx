// @vitest-environment jsdom

/**
 * The selection action bar's enter and exit, proved by the DOM.
 *
 * The bar was `if (!selection.selectionMode) return null` and nothing else: a
 * fixed slab the width of the dock appeared and disappeared in a single paint,
 * in the same slot the dock and the FAB it displaces
 * (`bulk-selection-signal.ts`) were vacating and refilling on the same frame.
 *
 * Presence is what these assert, not the class list: an exit declared on a node
 * that has already been returned as `null` is unreachable by construction, and
 * the classes look perfectly correct either way. They also cover the half of it
 * that is not the animation — `exitSelection` empties the selection in the same
 * call that leaves selection mode, so a bar that merely lingers would spend its
 * whole exit reading "0 selected" with all four actions greyed out.
 *
 * The harness is `bulk-selection-mode.test.tsx`'s, kept deliberately close to
 * it: the bar is only ever reachable through the provider that renders it.
 */

import { useState } from "react";
import { act, cleanup, fireEvent, render, screen } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { TodoItemType } from "@/types";

vi.mock("@/lib/api-client", () => ({
  api: { PATCH: vi.fn(), DELETE: vi.fn() },
}));

vi.mock("@/hooks/use-toast", () => ({
  useToast: () => ({ toast: vi.fn() }),
}));

vi.mock("react-i18next", () => ({
  useTranslation: () => ({
    t: (key: string, options?: Record<string, unknown>) =>
      options && "count" in options ? `${key}:${String(options.count)}` : key,
  }),
}));

vi.mock("@/lib/observability/sentry", () => ({
  addDiagnosticBreadcrumb: vi.fn(),
}));

vi.mock("@/components/Sidebar/List/query/get-list-meta", () => ({
  useListMetaData: () => ({
    listMetaData: { "list-a": { name: "Work", myRole: "OWNER" } },
  }),
}));

vi.mock("@/components/ListDot", () => ({
  default: () => <span data-testid="list-dot" />,
}));

import TaskSelectionProvider, {
  useTaskSelection,
} from "@/providers/TaskSelectionProvider";
import { SURFACE_TRANSITION_MS } from "@/lib/surfaceTransitionTiming";

function buildTodo(id: string): TodoItemType {
  return {
    id,
    title: "Task",
    description: null,
    pinned: false,
    createdAt: new Date("2026-08-01T09:00:00.000Z"),
    order: 1,
    priority: "Low",
    due: new Date("2026-08-20T09:00:00.000Z"),
    rrule: null,
    timeZone: "UTC",
    userID: "user-1",
    completed: false,
    exdates: [],
    instanceDate: null,
    listID: "list-a",
    instances: [],
  } as TodoItemType;
}

function Controls() {
  const selection = useTaskSelection();
  return (
    <>
      <button type="button" onClick={selection.enterSelection}>
        enter
      </button>
      <button type="button" onClick={selection.selectAll}>
        pick-all
      </button>
      <button type="button" onClick={selection.exitSelection}>
        leave
      </button>
    </>
  );
}

function Harness({ rows }: { rows: TodoItemType[] }) {
  const [client] = useState(
    () =>
      new QueryClient({
        defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
      }),
  );
  return (
    <QueryClientProvider client={client}>
      <TaskSelectionProvider rows={rows}>
        <Controls />
      </TaskSelectionProvider>
    </QueryClientProvider>
  );
}

const bar = () =>
  document.querySelector<HTMLElement>(".tday-surface-enter, .tday-surface-exit");
const toolbar = () => document.querySelector<HTMLElement>('[role="toolbar"]');

const enterAndSelectAll = () => {
  fireEvent.click(screen.getByText("enter"));
  fireEvent.click(screen.getByText("pick-all"));
};

describe("the bulk selection bar", () => {
  beforeEach(() => {
    // No `shouldAdvanceTime` here, unlike most of its neighbours: this file
    // asserts that the bar is still present one tick BEFORE its exit is due,
    // and a clock that also moves with wall time turns that into a race the
    // suite loses whenever a machine is busy. Nothing here needs real time —
    // every interaction is a `fireEvent`.
    vi.useFakeTimers();
  });

  afterEach(() => {
    cleanup();
    vi.useRealTimers();
  });

  it("arrives with an enter rather than simply being there", () => {
    render(<Harness rows={[buildTodo("a:null"), buildTodo("b:null")]} />);
    expect(bar()).toBeNull();

    enterAndSelectAll();

    const arriving = bar();
    expect(arriving).not.toBeNull();
    expect(arriving?.className).toContain("tday-surface-enter");
    expect(arriving?.className).not.toContain("tday-surface-exit");
    // No offset override: the bar sits on the bottom edge, so the pair's own
    // default rise is already the direction it should come from.
    expect(arriving?.className).not.toContain("tday-surface-from-top");
    // One number, read twice: the CSS is handed the same value `useFadeUnmount`
    // is holding the subtree for.
    expect(arriving?.style.animationDuration).toBe(`${SURFACE_TRANSITION_MS}ms`);
  });

  it("is still in the document on the frame selection mode is left", () => {
    render(<Harness rows={[buildTodo("a:null"), buildTodo("b:null")]} />);
    enterAndSelectAll();

    fireEvent.click(screen.getByText("leave"));

    // The assertion this file exists for. Under `if (!selectionMode) return
    // null` the bar was already gone here.
    const leaving = bar();
    expect(leaving).not.toBeNull();
    expect(leaving?.className).toContain("tday-surface-exit");
    // On the way out it is a picture of a toolbar. A tap landing on Delete
    // during those frames would act on a selection already dismissed.
    expect(toolbar()?.className).not.toContain("pointer-events-auto");
  });

  it("keeps showing the selection it had while it leaves", () => {
    render(<Harness rows={[buildTodo("a:null"), buildTodo("b:null")]} />);
    enterAndSelectAll();
    expect(screen.getByText("bulkSelected:2")).toBeTruthy();

    fireEvent.click(screen.getByText("leave"));

    // `exitSelection` emptied the selection on this same tick. Without the
    // snapshot the bar would fade out announcing it has nothing to do.
    expect(screen.getByText("bulkSelected:2")).toBeTruthy();
    expect(screen.queryByText("bulkSelected:0")).toBeNull();
    expect(
      screen.getByRole("button", { name: "bulkDelete" }).hasAttribute("disabled"),
    ).toBe(false);
  });

  it("leaves once the exit it declares is over, and not before", async () => {
    render(<Harness rows={[buildTodo("a:null")]} />);
    enterAndSelectAll();
    fireEvent.click(screen.getByText("leave"));

    await act(async () => {
      await vi.advanceTimersByTimeAsync(SURFACE_TRANSITION_MS - 1);
    });
    expect(bar()).not.toBeNull();

    await act(async () => {
      await vi.advanceTimersByTimeAsync(1);
    });
    expect(bar()).toBeNull();
    expect(toolbar()).toBeNull();
  });

  it("re-entering mid-exit goes straight back to arriving", async () => {
    render(<Harness rows={[buildTodo("a:null")]} />);
    enterAndSelectAll();
    fireEvent.click(screen.getByText("leave"));

    await act(async () => {
      await vi.advanceTimersByTimeAsync(SURFACE_TRANSITION_MS / 2);
    });
    fireEvent.click(screen.getByText("enter"));

    // The enter/exit choice reads `selectionMode`, never the presence value, so
    // a bar caught mid-exit is arriving again rather than stuck fading out.
    expect(bar()?.className).toContain("tday-surface-enter");

    await act(async () => {
      await vi.advanceTimersByTimeAsync(SURFACE_TRANSITION_MS);
    });
    expect(bar()).not.toBeNull();
  });

  it("skips the wait under prefers-reduced-motion", () => {
    const original = window.matchMedia;
    window.matchMedia = ((query: string) => ({
      matches: query.includes("prefers-reduced-motion"),
      media: query,
      addEventListener: () => {
        /* the hook reads `matches` once and never subscribes */
      },
      removeEventListener: () => {
        /* see above */
      },
    })) as unknown as typeof window.matchMedia;

    try {
      render(<Harness rows={[buildTodo("a:null")]} />);
      enterAndSelectAll();
      fireEvent.click(screen.getByText("leave"));
      // Nothing is animating, so lingering would be a stall rather than a
      // courtesy.
      expect(bar()).toBeNull();
    } finally {
      window.matchMedia = original;
    }
  });
});
