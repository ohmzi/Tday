// @vitest-environment jsdom

/**
 * The Completed row's list mark is the list's GLYPH, resolved out of the store the row belongs to.
 *
 * `list-mark-scope.test.ts` pins whether a row draws a mark and `ios-completed-row-list-mark.test.ts`
 * pins which glyph iOS draws; what neither of them can see is the web wiring, and the wiring is
 * where this defect lived: the row drew a bare coloured span and the list's NAME, so a completed
 * task in a list whose icon is a heart was marked exactly like one in a list whose icon is a cart.
 *
 * Two things are asserted here that a source read cannot:
 *
 *   1. **The glyph comes from the LIVE list, by id first.** A completed record is a denormalised
 *      snapshot — the backend's table has a name and a colour and never an icon column — so an
 *      `iconKey` has to be looked up, and looking it up is the whole feature. The row is handed a
 *      store whose every entry carries an `iconKey` and is asked for that glyph.
 *   2. **It falls through to the NAME when the id misses, inside the row's own namespace.** A
 *      completed Floater's `listID` is nulled by `ON DELETE SET NULL` the moment its list goes
 *      away, so the id lookup is expected to miss on exactly the row the `AlertTriangle` warns
 *      about — and the name the snapshot kept is what finds the list again. Resolving the todo row
 *      against the floater store would find nothing at all, so the namespace is asserted too.
 *
 * The colour is part of the same claim rather than a separate one: the glyph IS the colour mark
 * (it is tinted, as on Android and iOS), and the row used to paint the API's raw enum NAME into a
 * CSS declaration — where `ROSE`, `DEEP_BLUE`, `LIGHT_RED`, `BRICK` and `SLATE` are not CSS
 * colours at all and the dot rendered with no colour. `ROSE` is asserted below for that reason.
 *
 * jsdom lays nothing out, so this file cannot say the mark is on the first line or that the glyph
 * is 14px. Both are `task-row-first-line-alignment.test.ts`'s and the shared `ListDot`'s business.
 */

import type { ReactNode } from "react";
import { cleanup, render, screen } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { CompletedFloaterItemType, CompletedTodoItemType } from "@/types";

type Meta = { name: string; color: string; iconKey: string };

// `vi.hoisted` so the two stores exist before the mocked modules are imported — the factories
// close over them and are called at module load, not at mock-declaration time.
const { listMetaData, floaterListMetaData } = vi.hoisted(() => ({
  listMetaData: {} as Record<string, Meta>,
  floaterListMetaData: {} as Record<string, Meta>,
}));

vi.mock("@/lib/api-client", () => ({
  api: { PATCH: vi.fn(), DELETE: vi.fn(), POST: vi.fn() },
}));
vi.mock("@/hooks/use-toast", () => ({ useToast: () => ({ toast: vi.fn() }) }));
vi.mock("@/components/Sidebar/List/query/get-list-meta", () => ({
  useListMetaData: () => ({ listMetaData }),
}));
vi.mock("@/features/floaterList/query/get-floater-list-meta", () => ({
  useFloaterListMetaData: () => ({ floaterListMetaData }),
}));

import { CompletedTodoItemContainer } from "@/features/completed/component/ItemContainer";
import { CompletedFloaterItemContainer } from "@/features/completed/component/CompletedFloaterItemContainer";

const TODO: CompletedTodoItemType = {
  id: "completed-1",
  originalTodoID: "todo-1",
  title: "Water the plants",
  createdAt: new Date("2026-08-22T09:00:00.000Z"),
  completedAt: new Date("2026-08-22T10:00:00.000Z"),
  priority: "Low",
  due: null,
  userID: "user-1",
  rrule: null,
  instanceDate: null,
  listName: "Reading",
} as CompletedTodoItemType;

const FLOATER: CompletedFloaterItemType = {
  id: "completed-floater-1",
  originalFloaterID: "floater-1",
  title: "Call the bank",
  completedAt: new Date("2026-08-22T10:00:00.000Z"),
  priority: "Low",
  daysToComplete: null,
  listDeleted: false,
  listName: "Reading",
} as CompletedFloaterItemType;

function renderRow(node: ReactNode) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return render(<QueryClientProvider client={queryClient}>{node}</QueryClientProvider>);
}

/**
 * The row's trailing list mark, addressed through the list NAME it draws rather than by class:
 * the name span's nearest `div` ancestor is the mark's box (the name and the desktop glyph sit
 * in a `span` pill inside it), and that box is the only place in the row a list glyph is drawn.
 * Scoping matters — the row's restore circle is a Lucide `<svg>` too.
 */
function mark(): HTMLElement {
  const box = screen.getByText("Reading").closest("div");
  if (!box) throw new Error("the row's list mark is not on screen");
  return box as HTMLElement;
}

/**
 * Every glyph the mark drew, as `lucide-<key>` class tokens. Lucide names each `<svg>` after its
 * own icon (`lucide-heart`), which is the only handle jsdom gives on WHICH icon was chosen —
 * there is no stylesheet, so the geometry is an empty string either way.
 */
function glyphClasses(): string[] {
  return Array.from(mark().querySelectorAll("svg")).map(
    (svg) =>
      Array.from(svg.classList).find((token) => token.startsWith("lucide-")) ?? "",
  );
}

/** The colour class every glyph in the mark carries. */
function tints(): string[] {
  return Array.from(mark().querySelectorAll("svg")).map(
    (svg) =>
      (svg.getAttribute("class") ?? "")
        .split(/\s+/)
        .find((token) => token.startsWith("text-accent-")) ?? "",
  );
}

describe("the completed row draws the list's own glyph, from the list's own store", () => {
  beforeEach(() => {
    for (const key of Object.keys(listMetaData)) delete listMetaData[key];
    for (const key of Object.keys(floaterListMetaData)) delete floaterListMetaData[key];
  });

  afterEach(() => cleanup());

  it("draws the live list's iconKey, tinted by the live list's colour", () => {
    listMetaData["l1"] = { name: "Reading", color: "ROSE", iconKey: "heart" };

    renderRow(
      <CompletedTodoItemContainer
        // A snapshot colour that disagrees with the store: the live list wins, exactly as
        // `todoListAccentColor(for: resolvedList?.color ?? item.listColor)` does on iOS.
        completedTodoItem={{ ...TODO, listID: "l1", listColor: "RED" }}
      />,
    );

    // Both marks: the mobile glyph and the one inside the desktop pill.
    expect(glyphClasses()).toEqual(["lucide-heart", "lucide-heart"]);
    expect(tints()).toEqual(["text-accent-rose", "text-accent-rose"]);
  });

  it("finds the list by NAME when the snapshotted id no longer matches", () => {
    // The deleted-list shape: the row still holds the name, the store has been recreated under
    // a new id (or never had this one).
    listMetaData["recreated"] = { name: "  reading ", color: "BLUE", iconKey: "book" };

    renderRow(
      <CompletedTodoItemContainer
        completedTodoItem={{ ...TODO, listID: "l1", listColor: "RED" }}
      />,
    );

    expect(glyphClasses()).toEqual(["lucide-book", "lucide-book"]);
    expect(tints()).toEqual(["text-accent-blue", "text-accent-blue"]);
  });

  it("keeps the snapshotted colour when the list is gone for good", () => {
    // No live list to tint from. The glyph is inferred from the name the snapshot kept, and the
    // colour is the one it kept — `SLATE`, which the row's old raw CSS declaration dropped.
    renderRow(
      <CompletedTodoItemContainer
        completedTodoItem={{ ...TODO, listID: "gone", listColor: "SLATE" }}
      />,
    );

    const glyphs = glyphClasses();
    expect(glyphs).toHaveLength(2);
    expect(glyphs[0]).not.toBe("");
    expect(tints()).toEqual(["text-accent-slate", "text-accent-slate"]);
  });

  it("resolves a completed Floater against the floater store, by name", () => {
    // The row's own namespace, and the id the backend nulls. A Floater resolved against the
    // SCHEDULED store would find nothing and silently lose its mark.
    listMetaData["l1"] = { name: "Reading", color: "ROSE", iconKey: "heart" };
    floaterListMetaData["f2"] = { name: "Reading", color: "TEAL", iconKey: "book" };

    renderRow(
      <CompletedFloaterItemContainer
        completedFloaterItem={{ ...FLOATER, listID: null, listColor: "RED" }}
      />,
    );

    expect(glyphClasses()).toEqual(["lucide-book", "lucide-book"]);
    expect(tints()).toEqual(["text-accent-teal", "text-accent-teal"]);
  });
});
