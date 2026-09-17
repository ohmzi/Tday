import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";

/**
 * THE COMPLETED ROW'S LIST MARK IS THE LIST'S OWN GLYPH
 *
 * The second half of the question `list-mark-scope.test.ts` covers. That file pins WHERE a row's
 * list mark is drawn, and its unit-test twins pin the predicate that decides it. None of them —
 * and nothing else in this repository — pins WHICH glyph the mark is. On the Completed screen that
 * turned out to matter: every row drew one hardcoded SF Symbol, `tray.fill`, whatever list the
 * task was in.
 *
 * Why the tray is wrong rather than merely generic: `tray.fill` is this app's own glyph for the
 * `inbox` list key — `ScheduledTaskHomeScreen.swift` names it as the `inbox` option and
 * `tdayLucideListAsset` maps an unknown or empty key to `LucideInbox`. So a completed row did not
 * draw "no list mark"; it drew a mark that says **Inbox**, for a task in any list at all.
 *
 * What the three clients did at the time this file was written, and which of them was a defect:
 *   - **Android** had both halves — it branched the row's list collection on `isFloater` and drew
 *     `tdayListIconForList(listMeta?.iconKey, listMeta?.name)`, so a Floater's mark was drawn from
 *     the Floater list;
 *   - **web** drew no glyph either — a colour dot on mobile and a dot plus the list's NAME on
 *     desktop — and was wired to the same lookup in the change that extended this file's claim to
 *     the third client: `ListDot` / `FloaterListDot`, resolved by id and falling through to the
 *     name the snapshot kept, in the same pill the pending rows already draw their glyph in. The
 *     block below is that half;
 *   - **iOS** drew the constant.
 *
 * The three are hand-written twins, so the claims are gathered in one file per client rather than
 * one file per icon: this one now holds iOS's and web's, and `list-mark-scope.test.ts` holds the
 * scope predicate all three spell identically.
 *
 * The reason a lookup is mandatory on iOS, rather than the item carrying its own icon: the
 * completed record is a denormalised SNAPSHOT. The backend's `completedtodo` table stores
 * `projectName`/`projectColor` and no icon column, and `CompletedItem` mirrors that with
 * `listId`/`listName`/`listColor` and no `iconKey`. So the glyph has to come from resolving the
 * live list, and the resolution has to search the namespace the row belongs to: scheduled lists
 * and Floater lists are two disjoint stores, and a completed Floater resolved against the
 * scheduled collection would find nothing and silently lose its mark — not draw a wrong one.
 *
 * There is no local iOS compile and CI's `xctest` cannot see a rendered glyph, so a static read is
 * the only thing that can hold this line. It is enough here because the defect was the absence of
 * a construct — no resolver call, no icon view — and an absence is what a static read sees.
 */

const MONO = resolve(__dirname, "..", "..", "..");

const COMPLETED_SCREEN = resolve(MONO, "ios-swiftUI/Tday/Feature/Completed/CompletedScreen.swift");
const ROW_LIST_MARK = resolve(MONO, "ios-swiftUI/Tday/Feature/Todos/TaskRowListMark.swift");
const DOMAIN_MODELS = resolve(MONO, "ios-swiftUI/Tday/Core/Model/DomainModels.swift");

/**
 * Source with block comments and whole-line `//` prose removed.
 *
 * These files explain themselves at length, and this file's own subject is quoted in those
 * explanations — the doc on `tdayResolvedRowList` names `isFloater` and both collections, and the
 * comment above the row's mark in `CompletedScreen.swift` describes the construct being asserted
 * about. Stripping first means every assertion below is about code.
 */
function readCode(file: string): string {
  return readFileSync(file, "utf8")
    .replace(/\/\*[\s\S]*?\*\//g, "")
    .split("\n")
    .filter((line) => !line.trimStart().startsWith("//"))
    .join("\n");
}

/**
 * The brace-delimited block that opens after `anchor`, anchor included.
 *
 * Brace counting is naive — it does not know about braces inside strings — which is safe for the
 * one block this file points at, which contains none. A miscount truncates the block and the
 * assertion using it fails, so the failure mode is a red test rather than a quiet pass.
 */
function blockAfter(source: string, anchor: string): string {
  const anchorAt = source.indexOf(anchor);
  if (anchorAt < 0) return "";
  const open = source.indexOf("{", anchorAt);
  if (open < 0) return "";

  let depth = 0;
  for (let i = open; i < source.length; i++) {
    if (source[i] === "{") depth += 1;
    else if (source[i] === "}") {
      depth -= 1;
      if (depth === 0) return source.slice(anchorAt, i + 1);
    }
  }
  return source.slice(anchorAt);
}

/** The completed row's own body — the surface that draws the mark. */
const COMPLETED_ROW = blockAfter(readCode(COMPLETED_SCREEN), "private struct CompletedTimelineRow: View {");

describe("the completed row draws the list's own glyph", () => {
  it("scopes its assertions to the row it means", () => {
    // The canary. Every assertion below is about a block, and `blockAfter` returns the empty
    // string when its anchor moves — which would make a `not.toContain` pass for the wrong
    // reason. A renamed struct must fail here instead.
    expect(COMPLETED_ROW.length).toBeGreaterThan(500);
    expect(COMPLETED_ROW).toContain("showListIndicator");
  });

  it("resolves the row's list instead of drawing a constant", () => {
    // The defect: `Image(systemName: "tray.fill")`, one glyph for every list, gated only on the
    // snapshotted `item.listName` being non-empty and tinted only by `item.listColor`. It is this
    // app's `inbox` glyph, so the row said "Inbox" for a task in any list.
    expect(COMPLETED_ROW).not.toContain('Image(systemName: "tray.fill")');

    // And the replacement, both halves: a resolver that turns the row's snapshot into the live
    // list, and the shared icon view that draws that list's glyph — the same chain every other
    // list mark on this client uses, so a list's icon cannot differ between screens.
    expect(COMPLETED_ROW).toContain("tdayResolvedRowList");
    expect(COMPLETED_ROW).toContain("TdayListIcon(");
  });

  it("feeds the resolver both namespaces, so the row's own kind picks the collection", () => {
    // The row gets BOTH collections and lets the resolver branch on the item, rather than
    // choosing one at the call site. A call site that picked would be the one untested place
    // the namespace could be lost; both namespaces in view means the branch is inside the
    // function the unit tests above pin.
    expect(COMPLETED_ROW).toContain("floaterLists");
    expect(COMPLETED_ROW).toContain("scheduledLists");
  });
});

describe("the resolver searches the row's own namespace", () => {
  const RESOLVER = readCode(ROW_LIST_MARK);

  it("exists, and branches on the item's kind", () => {
    expect(RESOLVER).toContain("func tdayResolvedRowList(");
    // The branch itself. A resolver that searched one collection for both kinds would compile,
    // pass a scheduled row's test, and drop every completed Floater's mark on device — the
    // failure direction with no symptom to grep for.
    expect(RESOLVER).toContain("isFloater ? floaterLists : scheduledLists");
  });

  it("tries the id before the name", () => {
    // `listId` survives a rename, so it is the better key; it is not sufficient alone, because
    // the backend nulls a completed Floater's listID when its list is deleted.
    const byId = RESOLVER.indexOf("$0.id == listId");
    const byName = RESOLVER.indexOf("== wanted");
    expect(byId).toBeGreaterThan(-1);
    expect(byName).toBeGreaterThan(-1);
    expect(byId).toBeLessThan(byName);
  });
});

describe("the web completed rows draw the row's own glyph too", () => {
  // `FloaterListDot` for the floater row and not `ListDot`: the two namespaces are disjoint, and
  // a completed Floater resolved against the scheduled store would find nothing at all.
  const WEB_ROWS = [
    { path: "features/completed/component/ItemContainer.tsx", component: "ListDot" },
    {
      path: "features/completed/component/CompletedFloaterItemContainer.tsx",
      component: "FloaterListDot",
    },
  ];

  it.each(WEB_ROWS)("$path draws $component rather than a bare colour dot", ({ path, component }) => {
    const row = readCode(resolve(MONO, "tday-web/src", path));

    // The defect, in its web spelling: two `rounded-full` spans painted with the API's raw
    // colour NAME, one for mobile and one inside the desktop pill. Five of the fifteen values
    // that name can take are not CSS colours, and for those the dot rendered with no colour.
    expect(row).not.toContain("inline-block h-3 w-3 shrink-0 rounded-full");
    expect(row).not.toContain("inline-block h-2.5 w-2.5 shrink-0 rounded-full");
    expect(row).toContain(`<${component}`);

    // And the two fallbacks, which are not decoration: a completed record is a snapshot, and the
    // backend nulls its `listID` when the list is deleted, so the id lookup is expected to miss
    // and the name is what is left to find the list by. Without them the row would draw this
    // app's `inbox` glyph — the very thing the iOS half of this file is about.
    expect(row).toContain("name={listName}");
    expect(row).toContain("color={listColor}");
  });

  it("resolves the id first and falls through to the name", () => {
    // Same rule the iOS resolver above is pinned to, in the twin that carries it on this client:
    // an id survives a rename, and the name is what is left once the id is gone.
    const resolver = readCode(resolve(MONO, "tday-web/src/lib/listMark.ts"));
    const byId = resolver.indexOf("namespace[byId]");
    const byName = resolver.indexOf(`list.name ?? ""`);
    expect(byId).toBeGreaterThan(-1);
    expect(byName).toBeGreaterThan(-1);
    expect(byId).toBeLessThan(byName);
  });
});

describe("why the lookup is mandatory rather than the item carrying its own icon", () => {
  it("the completed record has no icon key to draw", () => {
    // The structural fact the whole file rests on. If `CompletedItem` ever gains an `iconKey`,
    // this fails and the resolver gets a second opinion to reconcile — which is the moment to
    // have the argument, rather than after a mark disagrees with its list.
    const item = blockAfter(readCode(DOMAIN_MODELS), "struct CompletedItem: Identifiable");
    expect(item.length).toBeGreaterThan(200);
    expect(item).toContain("let listName: String?");
    expect(item).toContain("let isFloater: Bool");
    expect(item).not.toContain("iconKey");
  });
});
