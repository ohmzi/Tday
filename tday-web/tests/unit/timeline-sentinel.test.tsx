// @vitest-environment jsdom

/**
 * The Today/Overdue pager: what it says, when it says it, and who to.
 *
 * The strip at the foot of the feed carried a hardcoded English sentence in a
 * ten-locale app, and sat in no live region at all — so on nine locales it was
 * the wrong language, and on every locale a screen reader was told nothing
 * while ten rows were inserted above it.
 *
 * The second half is the one a jsdom test has to be careful about: a live
 * region is silent unless its contents CHANGE, so the assertions below page the
 * feed for real and compare the region before and after. A region that holds
 * the same sentence across a page-in would pass a "the text is there" test and
 * announce nothing to anybody, which is the shape this file exists to catch.
 *
 * Both halves are asserted against the real container rather than a stand-in:
 * the sentences are built from what `messages/en.json` actually holds, so
 * re-inlining the English breaks this file instead of passing it, and the
 * region is read back through `getByRole("status")`, which is the same lookup
 * the accessibility tree does.
 *
 * Everything the feed draws around the pager is mocked away — this file is
 * about the few lines at the bottom of the container, and the real
 * `TimelineSections`/`OverdueDaySections`/watermark tree would drag the whole
 * dnd and query stack in to assert nothing extra. `useTimelinePaging` is NOT
 * mocked: `hasMore`, `pageReveal`, the ref and the observer are the production
 * ones, because the point is what the production pager does when it trips.
 */

import { act, cleanup, render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { readFileSync } from "fs";
import path from "path";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import type { TodoItemType } from "@/types";

vi.mock("@/components/app/ScreenWatermark", () => ({ default: () => null }));
vi.mock("@/components/ui/MobileSearchHeader", () => ({ default: () => null }));
vi.mock("@/components/app/NativePageHeader", () => ({
  default: () => null,
  useNativePageBarSlots: () => ({}),
}));
vi.mock("@/features/summary/SummaryButton", () => ({ default: () => null }));
vi.mock("@/features/summary/WeekInReviewCard", () => ({ default: () => null }));
vi.mock("@/components/todo/bulk/BulkSelectButton", () => ({ default: () => null }));
vi.mock("@/components/todo/component/TodoListLoading", () => ({ default: () => null }));
vi.mock("@/components/todo/dnd/TimelineSections", () => ({ default: () => null }));
vi.mock("@/components/app/EmptyState", () => ({ default: () => null }));
vi.mock("@/features/todayTodos/component/TodayTimeBuckets", () => ({ default: () => null }));
vi.mock("@/features/todayTodos/component/TodayEarlierSection", () => ({ default: () => null }));
vi.mock("@/features/todayTodos/component/TimelineEmptyState", () => ({ default: () => null }));

// The one mocked child that reports something back: how many rows the pager has
// actually revealed, so "there is a next page" is a fact about the feed and not
// an assumption about the fixture.
vi.mock("@/features/todayTodos/component/OverdueDaySections", () => ({
  default: ({
    regularSections,
    earlierSections,
  }: {
    regularSections: { todos: unknown[] }[];
    earlierSections: { todos: unknown[] }[];
  }) => (
    <div data-testid="revealed">
      {[...regularSections, ...earlierSections].reduce(
        (total, section) => total + section.todos.length,
        0,
      )}
    </div>
  ),
}));

vi.mock("@/providers/TodoMutationProvider", () => ({
  default: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}));
vi.mock("@/providers/TaskSelectionProvider", () => ({
  default: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}));

// The mutation hooks are handed to the (mocked) provider as values and never
// called here; standing them in keeps the api-client and toast stack out.
vi.mock("@/features/todayTodos/query/complete-todo", () => ({ useCompleteTodo: vi.fn() }));
vi.mock("@/features/todayTodos/query/delete-todo", () => ({ useDeleteTodo: vi.fn() }));
vi.mock("@/features/todayTodos/query/prioritize-todo", () => ({ usePrioritizeTodo: vi.fn() }));
vi.mock("@/features/todayTodos/query/update-todo", () => ({ useEditTodo: vi.fn() }));
vi.mock("@/features/todayTodos/query/update-todo-instance", () => ({
  useEditTodoInstance: vi.fn(),
}));
vi.mock("@/features/todayTodos/query/reorder-todo", () => ({ useReorderTodo: vi.fn() }));
vi.mock("@/features/user/query/get-timezone", () => ({ useUserTimezone: () => undefined }));
// `useTimelineEmptyState` asks the completion history whether the day is done;
// that is a live query, and this file has no server.
vi.mock("@/features/completed/query/get-completedTodo", () => ({
  useCompletedTodo: () => ({ completedTodos: [], todoLoading: false }),
}));

const todos = vi.hoisted(() => ({ value: [] as unknown[] }));
vi.mock("@/features/todayTodos/query/get-todo-timeline", () => ({
  useTodoTimeline: () => ({ todos: todos.value, todoLoading: false }),
}));

import "@/i18n";
import AllTasksTimelineContainer from "@/features/todayTodos/component/AllTasksTimelineContainer";

/** `useTimelinePaging`'s own page size — the pager reveals this many at a time. */
const PAGE_SIZE = 10;
/** Two whole pages and a short one, so a page-in can be watched twice. */
const THREE_PAGES = PAGE_SIZE * 2 + 5;

const EN_APP = (
  JSON.parse(
    readFileSync(path.resolve(__dirname, "..", "..", "messages", "en.json"), "utf-8"),
  ) as { app: Record<string, string> }
).app;

/** The count sentence as English actually spells it, with the pager's numbers in. */
function enShownOfTotal(shown: number, total: number): string {
  return EN_APP.tasksShownOfTotal.replace("{{shown}}", String(shown)).replace(
    "{{total}}",
    String(total),
  );
}

const CONTAINER_SOURCE = readFileSync(
  path.resolve(
    __dirname,
    "..",
    "..",
    "src",
    "features",
    "todayTodos",
    "component",
    "AllTasksTimelineContainer.tsx",
  ),
  "utf-8",
);

/** Overdue by construction: every fixture is due days ago, so `scope="overdue"` keeps it. */
function overdueTodos(count: number): TodoItemType[] {
  const dayMs = 24 * 60 * 60 * 1000;
  return Array.from({ length: count }, (_, index) => ({
    id: `todo-${index}:undefined`,
    title: `Overdue task ${index}`,
    description: null,
    completed: false,
    priority: "Low",
    due: new Date(Date.now() - (index + 1) * dayMs),
    createdAt: new Date(Date.now() - 30 * dayMs),
    updatedAt: null,
    rrule: null,
    instanceDate: null,
    listID: null,
  })) as unknown as TodoItemType[];
}

const observed: Element[] = [];
/** Trips the sentinel the way a scroll would — set by the stub below. */
let page: (() => void) | null = null;

beforeEach(() => {
  observed.length = 0;
  page = null;
  // jsdom ships no IntersectionObserver, and the pager builds one the moment it
  // has a next page. Recording what it observes is how one test checks the ref
  // still reaches the strip; keeping the callback is how the rest reveal a page
  // without a viewport.
  vi.stubGlobal(
    "IntersectionObserver",
    class {
      constructor(private readonly callback: IntersectionObserverCallback) {}
      observe(element: Element) {
        observed.push(element);
        page = () =>
          this.callback(
            [{ isIntersecting: true } as IntersectionObserverEntry],
            this as unknown as IntersectionObserver,
          );
      }
      unobserve() {}
      disconnect() {}
      takeRecords() {
        return [];
      }
    },
  );
});

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

function renderOverdue(count: number) {
  todos.value = overdueTodos(count);
  return render(
    <MemoryRouter initialEntries={["/en/app/overdue"]}>
      <AllTasksTimelineContainer scope="overdue" />
    </MemoryRouter>,
  );
}

/** Reveals the next page, and hands back what the region said afterwards. */
function revealNextPage(): string {
  expect(page, "the pager built no observer, so there is no next page").not.toBeNull();
  act(() => page?.());
  return screen.getByRole("status").textContent ?? "";
}

describe("the Today/Overdue pager", () => {
  it("mounts its live region empty, so the first page is not announced", () => {
    renderOverdue(PAGE_SIZE + 5);

    const region = screen.getByRole("status");
    expect(region.getAttribute("aria-live")).toBe("polite");
    // Nothing has been revealed yet — the rows the reader has are simply the
    // ones the screen opened with, and a region that arrives already talking is
    // announced at the reader's discretion anyway.
    expect(region.textContent).toBe("");
    expect(screen.getByTestId("revealed").textContent).toBe(String(PAGE_SIZE));
  });

  it("says how much of the feed is on the page each time a page lands", () => {
    renderOverdue(THREE_PAGES);

    const before = screen.getByRole("status").textContent;
    const afterFirst = revealNextPage();

    expect(screen.getByTestId("revealed").textContent).toBe(String(PAGE_SIZE * 2));
    // The assertion this file exists for: the region's contents CHANGED on the
    // frame the rows landed. A sentence that reads the same before and after is
    // a sentence no reader speaks.
    expect(afterFirst).not.toBe(before);
    expect(afterFirst).toBe(enShownOfTotal(PAGE_SIZE * 2, THREE_PAGES));

    // And again on the last page, which lands on the true total rather than
    // falling silent — that is how the reader learns the feed is complete.
    const afterLast = revealNextPage();
    expect(afterLast).not.toBe(afterFirst);
    expect(afterLast).toBe(enShownOfTotal(THREE_PAGES, THREE_PAGES));
  });

  it("speaks the locale file, not an inlined English string", () => {
    renderOverdue(THREE_PAGES);

    expect(EN_APP.tasksShownOfTotal).toBeTruthy();
    expect(EN_APP.scrollForMore).toBeTruthy();
    expect(revealNextPage()).toBe(enShownOfTotal(PAGE_SIZE * 2, THREE_PAGES));
    expect(screen.getByText(EN_APP.scrollForMore)).toBeTruthy();
    // The exact string that used to be inlined at the call site, and the
    // sentence that replaced it: the pager narrates no fetch, because there is
    // none. Asserting on the SOURCE as well as the DOM is what makes this fail
    // for the right reason if someone re-hardcodes either.
    expect(document.body.textContent).not.toContain("Loading more tasks...");
    expect(CONTAINER_SOURCE).not.toContain("Loading more tasks...");
    expect(CONTAINER_SOURCE).not.toContain("loadingMoreTasks");
    expect(CONTAINER_SOURCE).toContain('appDict("tasksShownOfTotal"');
    expect(CONTAINER_SOURCE).toContain('appDict("scrollForMore")');
  });

  it("keeps the region mounted, and drops the strip, once the feed is complete", () => {
    // Fewer tasks than a page: there is no next page, so there is no strip —
    // but the region itself stays, because a status node that only appears when
    // it has something to say is the case screen readers are free to skip.
    renderOverdue(3);

    expect(screen.getByRole("status").textContent).toBe("");
    expect(screen.queryByText(EN_APP.scrollForMore)).toBeNull();
    expect(screen.getByTestId("revealed").textContent).toBe("3");
  });

  it("still hands the observer the node it pages from", () => {
    renderOverdue(PAGE_SIZE + 5);

    expect(observed).toHaveLength(1);
    // The strip is the sentinel and sits outside the live region now: the
    // region carries the spoken half, the strip the seen one, and this is the
    // assertion that says splitting them did not cost the pager its trigger.
    expect(screen.getByRole("status").contains(observed[0])).toBe(false);
    expect(observed[0].textContent).toBe(EN_APP.scrollForMore);
  });
});

describe("the pager's two sentences across the ten locales", () => {
  const LOCALES = ["en", "es", "fr", "de", "it", "pt", "ru", "zh", "ja", "ms"];
  const KEYS = ["scrollForMore", "tasksShownOfTotal"] as const;

  function appNamespace(locale: string): Record<string, string> {
    return (
      JSON.parse(
        readFileSync(
          path.resolve(__dirname, "..", "..", "messages", `${locale}.json`),
          "utf-8",
        ),
      ) as { app: Record<string, string> }
    ).app;
  }

  it("are present and non-empty in every locale", () => {
    for (const locale of LOCALES) {
      for (const key of KEYS) {
        expect(appNamespace(locale)[key]?.trim(), `${locale}.${key}`).toBeTruthy();
      }
    }
  });

  it("keep both of the count sentence's numbers in every locale", () => {
    // Word order moves the numbers around — ja and zh put the total first — but
    // a translation that drops one of them leaves the reader with half a fact.
    for (const locale of LOCALES) {
      const sentence = appNamespace(locale).tasksShownOfTotal;
      expect(sentence, `${locale} shown`).toContain("{{shown}}");
      expect(sentence, `${locale} total`).toContain("{{total}}");
    }
  });

  it("are actually translated where a shared word cannot explain it", () => {
    // Latin-script locales can legitimately share a word with English; these
    // three cannot, so an English value in them is an untranslated placeholder
    // rather than a coincidence.
    for (const locale of ["ru", "zh", "ja"]) {
      for (const key of KEYS) {
        expect(appNamespace(locale)[key], `${locale}.${key}`).not.toBe(EN_APP[key]);
      }
    }
  });

  it("no longer ship the sentence that named a load", () => {
    // Today/Overdue paging slices an array that is already in memory: there is
    // no request, so "Loading more tasks…" described a wait that never
    // happened. Retiring it from one locale file and not the ten is what the
    // i18n parity guardrail is for; this is the same check at the call site.
    for (const locale of LOCALES) {
      expect(appNamespace(locale).loadingMoreTasks, locale).toBeUndefined();
    }
  });
});
