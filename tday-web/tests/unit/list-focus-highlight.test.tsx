// @vitest-environment jsdom

/**
 * A custom-list screen opened from a search result or deep link highlights exactly
 * one row. For a recurring series that is the occurrence that matched, not every
 * occurrence of the series, and the first occurrence when the matched one is not
 * shown.
 */

import type { ReactNode } from "react";
import { cleanup, render } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("react-i18next", () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: "en" } }),
  initReactI18next: { type: "3rdParty", init: () => {} },
}));
vi.mock("@/lib/api-client", () => ({
  api: { GET: vi.fn(), PATCH: vi.fn(), DELETE: vi.fn(), POST: vi.fn() },
}));
vi.mock("@/hooks/use-toast", () => ({ useToast: () => ({ toast: vi.fn() }) }));
vi.mock("@/hooks/use-undoable-delete", () => ({ useUndoableDelete: () => vi.fn() }));
vi.mock("@/hooks/use-share-list", () => ({ useShareListAsText: () => vi.fn() }));
vi.mock("@/hooks/useAppMode", () => ({ useIsLocalMode: () => false }));
vi.mock("@/lib/navigation", () => ({
  useLocale: () => "en",
  usePathname: () => "/en/app/list/list-1",
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
  Link: ({ children }: { children: ReactNode }) => children,
}));
vi.mock("@/features/user/query/get-timezone", () => ({
  useUserTimezone: () => ({ timeZone: "UTC" }),
}));
vi.mock("@/components/Sidebar/List/query/get-list-meta", () => ({
  useListMetaData: () => ({
    listMetaData: { "list-1": { name: "Chores", color: "BLUE", myRole: "OWNER" } },
  }),
}));
vi.mock("@/features/list/query/get-list-todos", () => ({
  useList: () => ({ listTodos: seededRows, listTodosLoading: false }),
}));
vi.mock("@/components/app/NativePageHeader", () => ({
  default: () => null,
  useNativePageBarSlots: () => ({}),
}));
vi.mock("@/components/ui/MobileSearchHeader", () => ({ default: () => null }));
vi.mock("@/components/app/ScreenWatermark", () => ({ default: () => null }));
vi.mock("@/components/Sidebar/List/ListFormSheet", () => ({ default: () => null }));
vi.mock("@/features/list/component/ManageMembersSheet", () => ({ default: () => null }));
vi.mock("@/features/summary/SummaryButton", () => ({ default: () => null }));
vi.mock("@/components/todo/bulk/BulkSelectButton", () => ({ default: () => null }));
vi.mock("@/features/todayTodos/component/TimelineEmptyState", () => ({ default: () => null }));

import ListContainer from "@/features/list/component/ListContainer";
import type { TodoItemType } from "@/types";

let seededRows: TodoItemType[] = [];

function occurrence(
  id: string,
  title: string,
  instanceMs: number | "undefined",
  due: Date,
  rrule: string | null,
): TodoItemType {
  return {
    id: `${id}:${instanceMs}`,
    title,
    description: null,
    pinned: false,
    createdAt: new Date("2026-08-01T00:00:00Z"),
    order: 0,
    priority: "Low",
    due,
    rrule,
    timeZone: "UTC",
    userID: "user-1",
    completed: false,
    exdates: [],
    listID: "list-1",
  };
}

const MON = new Date("2026-08-17T09:00:00Z");
const TUE = new Date("2026-08-18T09:00:00Z");

function renderList(url: string, rows: TodoItemType[]) {
  seededRows = rows;
  const queryClient = new QueryClient({
    defaultOptions: { mutations: { retry: false }, queries: { retry: false } },
  });
  return render(
    <MemoryRouter initialEntries={[url]}>
      <QueryClientProvider client={queryClient}>
        <ListContainer id="list-1" />
      </QueryClientProvider>
    </MemoryRouter>,
  );
}

/** The class TodoItemContainer puts on a row only while it is the highlighted one. */
function highlightedRows(container: HTMLElement) {
  return container.querySelectorAll('[class~="inset-ring-accent/25"]');
}

beforeAll(() => {
  // jsdom has no layout; the highlight scrolls through this.
  Element.prototype.scrollIntoView = vi.fn();
});

beforeEach(() => {
  seededRows = [];
});

afterEach(() => {
  cleanup();
});

describe("custom list screen focus", () => {
  it("highlights exactly one row for a focused plain task", () => {
    const rows = [
      occurrence("todo-a", "Sweep floors", "undefined", MON, null),
      occurrence("todo-b", "Water plants", "undefined", TUE, null),
    ];

    const { container } = renderList("/en/app/list/list-1?focusTask=todo-b", rows);

    const highlighted = highlightedRows(container);
    expect(highlighted).toHaveLength(1);
    expect(highlighted[0]?.textContent).toContain("Water plants");
  });

  it("highlights only the matched occurrence of a recurring series", () => {
    const monMs = MON.getTime();
    const tueMs = TUE.getTime();
    const rows = [
      occurrence("todo-r", "Take out bins (Mon)", monMs, MON, "FREQ=DAILY"),
      occurrence("todo-r", "Take out bins (Tue)", tueMs, TUE, "FREQ=DAILY"),
    ];

    const { container } = renderList(
      `/en/app/list/list-1?focusTask=todo-r&focusInstance=${tueMs}`,
      rows,
    );

    const highlighted = highlightedRows(container);
    expect(highlighted).toHaveLength(1);
    expect(highlighted[0]?.textContent).toContain("(Tue)");
    expect(highlighted[0]?.textContent).not.toContain("(Mon)");
  });

  it("falls back to the first occurrence when the matched one is not shown", () => {
    const monMs = MON.getTime();
    const tueMs = TUE.getTime();
    const rows = [
      occurrence("todo-r", "Take out bins (Mon)", monMs, MON, "FREQ=DAILY"),
      occurrence("todo-r", "Take out bins (Tue)", tueMs, TUE, "FREQ=DAILY"),
    ];

    const { container } = renderList(
      "/en/app/list/list-1?focusTask=todo-r&focusInstance=1",
      rows,
    );

    const highlighted = highlightedRows(container);
    expect(highlighted).toHaveLength(1);
    expect(highlighted[0]?.textContent).toContain("(Mon)");
  });

  it("highlights nothing without a focus param", () => {
    const rows = [occurrence("todo-a", "Sweep floors", "undefined", MON, null)];

    const { container } = renderList("/en/app/list/list-1", rows);

    expect(highlightedRows(container)).toHaveLength(0);
  });
});
