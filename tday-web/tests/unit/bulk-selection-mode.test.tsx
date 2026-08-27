// @vitest-environment jsdom

import { useState } from "react";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { TodoItemType } from "@/types";

const patchMock = vi.fn();
const deleteMock = vi.fn();
const toastMock = vi.fn();

vi.mock("@/lib/api-client", () => ({
  api: {
    PATCH: (...args: unknown[]) => patchMock(...args),
    DELETE: (...args: unknown[]) => deleteMock(...args),
  },
}));

vi.mock("@/hooks/use-toast", () => ({
  useToast: () => ({ toast: (...args: unknown[]) => toastMock(...args) }),
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
    listMetaData: {
      "list-a": { name: "Work", myRole: "OWNER" },
      "list-b": { name: "Home", myRole: "OWNER" },
      "list-c": { name: "Shared read-only", myRole: "VIEWER" },
    },
  }),
}));

vi.mock("@/components/ListDot", () => ({
  default: () => <span data-testid="list-dot" />,
}));

import TaskSelectionProvider, {
  useTaskSelection,
} from "@/providers/TaskSelectionProvider";

type CapturedToast = {
  description: string;
  variant?: string;
  action?: { label: string; onClick: () => void };
  onAutoClose?: () => void;
  onDismiss?: () => void;
};

const stagedToast = () =>
  toastMock.mock.calls
    .map(([options]) => options as CapturedToast)
    .find((options) => Boolean(options.action));

function buildTodo(overrides: Partial<TodoItemType> = {}): TodoItemType {
  return {
    id: "todo-1:null",
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
    ...overrides,
  };
}

/** Stands in for the task rows: the bar is the thing under test. */
function SelectionControls({ rows }: { rows: TodoItemType[] }) {
  const selection = useTaskSelection();
  return (
    <div>
      <button type="button" onClick={selection.enterSelection}>
        enter
      </button>
      <button type="button" onClick={selection.selectAll}>
        pick-all
      </button>
      {rows.map((row) => (
        <button
          key={row.id}
          type="button"
          onClick={() => selection.toggle(row.id)}
        >
          {`pick-${row.id}`}
        </button>
      ))}
      <span data-testid="mode">{String(selection.selectionMode)}</span>
      <span data-testid="count">{selection.selectedCount}</span>
      <span data-testid="available">{String(selection.available)}</span>
    </div>
  );
}

function Harness({
  initialRows,
  scopeListId = null,
}: {
  initialRows: TodoItemType[];
  scopeListId?: string | null;
}) {
  const [rows, setRows] = useState(initialRows);
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return (
    <QueryClientProvider client={queryClient}>
      <button type="button" onClick={() => setRows([])}>
        empty-the-screen
      </button>
      <TaskSelectionProvider rows={rows} scopeListId={scopeListId}>
        <SelectionControls rows={rows} />
      </TaskSelectionProvider>
    </QueryClientProvider>
  );
}

const enterAndSelectAll = () => {
  fireEvent.click(screen.getByText("enter"));
  fireEvent.click(screen.getByText("pick-all"));
};

const barButton = (name: string) => screen.getByRole("button", { name });

describe("bulk selection mode", () => {
  beforeEach(() => {
    patchMock.mockReset();
    deleteMock.mockReset();
    toastMock.mockReset();
    patchMock.mockResolvedValue(undefined);
    deleteMock.mockResolvedValue(undefined);
  });

  afterEach(cleanup);

  it("sends no delete until the dialog is confirmed AND the toast settles", () => {
    const rows = [
      buildTodo({ id: "a:null" }),
      buildTodo({ id: "b:null" }),
      buildTodo({ id: "c:null" }),
    ];
    render(<Harness initialRows={rows} />);
    enterAndSelectAll();
    expect(screen.getByTestId("count").textContent).toBe("3");

    // Layer 1: the bar's Delete only opens the confirmation.
    fireEvent.click(barButton("bulkDelete"));
    expect(deleteMock).not.toHaveBeenCalled();
    expect(screen.getByText("bulkDeleteTitle:3")).toBeTruthy();
    expect(screen.getByText("bulkDeleteBody")).toBeTruthy();

    // Layer 2: confirming only stages it behind the undo toast.
    fireEvent.click(barButton("bulkDeleteConfirm:3"));
    expect(deleteMock).not.toHaveBeenCalled();

    // Only the settled toast lets the requests go.
    stagedToast()?.onAutoClose?.();
    expect(deleteMock).toHaveBeenCalledTimes(3);
  });

  it("sends nothing when the delete confirmation is cancelled", () => {
    render(<Harness initialRows={[buildTodo({ id: "a:null" })]} />);
    enterAndSelectAll();

    fireEvent.click(barButton("bulkDelete"));
    fireEvent.click(barButton("cancel"));

    expect(deleteMock).not.toHaveBeenCalled();
    expect(stagedToast()).toBeUndefined();
    // Cancelling the dialog leaves the selection where it was.
    expect(screen.getByTestId("mode").textContent).toBe("true");
    expect(screen.getByTestId("count").textContent).toBe("1");
  });

  it("never offers any action for repeating rows that are not occurrences", () => {
    const rows = [
      buildTodo({ id: "r1:1", rrule: "FREQ=DAILY" }),
      buildTodo({ id: "r2:2", rrule: "FREQ=WEEKLY" }),
    ];
    render(<Harness initialRows={rows} />);
    enterAndSelectAll();

    // A recurring row has no per-occurrence delete/priority/move route, so
    // acting on it would destroy or re-list the whole series.
    expect(barButton("bulkDelete")).toHaveProperty("disabled", true);
    expect(barButton("bulkPriority")).toHaveProperty("disabled", true);
    expect(barButton("bulkMove")).toHaveProperty("disabled", true);
    // Complete addresses the occurrence a row was shown as — and neither of
    // these rows is one. `buildTodo` leaves instanceDate null, which is what
    // every endpoint actually returns for a recurring template, so completing
    // them would write a phantom history row and complete nothing.
    expect(barButton("bulkComplete")).toHaveProperty("disabled", true);
  });

  it("offers complete for a repeating row that is a materialised occurrence", () => {
    const rows = [
      buildTodo({
        id: "r1:1",
        rrule: "FREQ=DAILY",
        instanceDate: new Date("2026-08-20T09:00:00.000Z"),
      }),
    ];
    render(<Harness initialRows={rows} />);
    enterAndSelectAll();

    expect(barButton("bulkComplete")).toHaveProperty("disabled", false);
    // Still never the other three: those hit the series, occurrence or not.
    expect(barButton("bulkDelete")).toHaveProperty("disabled", true);
    expect(barButton("bulkPriority")).toHaveProperty("disabled", true);
    expect(barButton("bulkMove")).toHaveProperty("disabled", true);
  });

  it("says how many rows an action will really touch when some are skipped", () => {
    const rows = [
      buildTodo({ id: "p:null" }),
      buildTodo({ id: "r:1", rrule: "FREQ=DAILY" }),
    ];
    render(<Harness initialRows={rows} />);
    enterAndSelectAll();

    fireEvent.click(barButton("bulkDelete"));
    expect(screen.getByText("bulkDeleteTitle:1")).toBeTruthy();
    expect(screen.getByText("bulkAppliesTo:1")).toBeTruthy();
  });

  it("asks again before a move that spans more than one source list", () => {
    const rows = [
      buildTodo({ id: "a:null", listID: "list-a" }),
      buildTodo({ id: "b:null", listID: "list-b" }),
    ];
    render(<Harness initialRows={rows} />);
    enterAndSelectAll();

    fireEvent.click(barButton("bulkMove"));
    // Viewer lists are never offered as a destination.
    expect(screen.queryByText("Shared read-only")).toBeNull();
    fireEvent.click(screen.getByText("Home"));

    expect(patchMock).not.toHaveBeenCalled();
    expect(screen.getByText("bulkMoveTitle:2")).toBeTruthy();

    fireEvent.click(barButton("bulkMoveConfirm:2"));
    expect(patchMock).toHaveBeenCalledTimes(2);
  });

  it("moves straight through when every row comes from the same list", () => {
    const rows = [
      buildTodo({ id: "a:null", listID: null }),
      buildTodo({ id: "b:null", listID: null }),
    ];
    render(<Harness initialRows={rows} scopeListId="list-a" />);
    enterAndSelectAll();

    fireEvent.click(barButton("bulkMove"));
    fireEvent.click(screen.getByText("Home"));

    // One more bulk move puts them back, so there is nothing to warn about.
    expect(screen.queryByText("bulkMoveTitle:2")).toBeNull();
    expect(patchMock).toHaveBeenCalledTimes(2);
    expect(screen.getByTestId("mode").textContent).toBe("false");
  });

  it("closes the mode after an action is dispatched", () => {
    render(<Harness initialRows={[buildTodo({ id: "a:null" })]} />);
    enterAndSelectAll();

    fireEvent.click(barButton("bulkComplete"));

    expect(screen.getByTestId("mode").textContent).toBe("false");
    expect(screen.getByTestId("count").textContent).toBe("0");
  });

  it("refuses to select past the cap", () => {
    const rows = Array.from({ length: 130 }, (_, index) =>
      buildTodo({ id: `t-${index}:null` }),
    );
    render(<Harness initialRows={rows} />);
    enterAndSelectAll();

    expect(screen.getByTestId("count").textContent).toBe("100");
    expect(screen.getByText("bulkSelectedCapped:100")).toBeTruthy();

    // A tap on an unselected row at the cap does not take.
    fireEvent.click(screen.getByText("pick-t-120:null"));
    expect(screen.getByTestId("count").textContent).toBe("100");

    // Deselecting frees capacity again.
    fireEvent.click(screen.getByText("pick-t-0:null"));
    fireEvent.click(screen.getByText("pick-t-120:null"));
    expect(screen.getByTestId("count").textContent).toBe("100");
  });

  it("leaves the mode when everything selected has gone from the screen", () => {
    render(<Harness initialRows={[buildTodo({ id: "a:null" })]} />);
    enterAndSelectAll();
    expect(screen.getByTestId("mode").textContent).toBe("true");

    fireEvent.click(screen.getByText("empty-the-screen"));

    expect(screen.getByTestId("mode").textContent).toBe("false");
    expect(screen.getByTestId("available").textContent).toBe("false");
  });
});
