import { readdirSync, readFileSync } from "node:fs";
import { join, relative, resolve } from "node:path";
import { describe, expect, it } from "vitest";

/**
 * THE PER-ROW LIST MARK IS DRAWN WHERE THE SCREEN HAS NOT ALREADY SAID WHICH LIST
 *
 * `shouldShowListMark` is pure and pinned per client — `tests/unit/task-row-list-mark.test.ts`
 * here, `TaskRowListMarkTest` on Android, `TaskRowListMarkTests` on iOS. What none of those
 * three can see is the wiring around them, and the wiring is where this defect lived for a
 * release: the rule was right in the abstract on every client and the row simply never asked
 * it the right question.
 *
 * So this file asserts the two structural facts the unit tests cannot:
 *
 *   1. **Every row surface goes through the predicate.** A row that draws a list glyph from a
 *      bare `listID &&` is a second opinion about the same question, and it is exactly what
 *      the two row components held before this change. A new row surface written by copying
 *      an old one is how that comes back.
 *   2. **Only the scoped screens declare a scope.** `ListScopeProvider` mounted on a mixed
 *      feed would suppress the mark where a row really could be from any list — the one
 *      failure direction that loses information rather than repeating it, and a silent one:
 *      the mark would simply stop appearing, with nothing to log and no test to notice.
 *
 * Plus the cross-client check nobody else makes: all three clients spell the same predicate.
 * The three implementations are hand-written twins with no generator between them, so the
 * only thing standing between them and three different answers is that they are asked to
 * exist under one name and are each tested against the same table of cases.
 */
const REPO = resolve(__dirname, "../../..");
const WEB_SRC = resolve(__dirname, "../../src");

const readRepo = (path: string) => readFileSync(join(REPO, path), "utf8");

function webSourceFiles(): string[] {
  const walk = (dir: string): string[] =>
    readdirSync(dir, { withFileTypes: true })
      .sort((a, b) => a.name.localeCompare(b.name))
      .flatMap((entry) => {
        const full = join(dir, entry.name);
        if (entry.isDirectory()) return walk(full);
        return /\.tsx?$/.test(entry.name) ? [full] : [];
      });
  return walk(WEB_SRC);
}

/** Every web file containing `needle`, as paths relative to `src`, sorted. */
function webFilesContaining(needle: string): string[] {
  return webSourceFiles()
    .filter((file) => readFileSync(file, "utf8").includes(needle))
    .map((file) => relative(WEB_SRC, file));
}

describe("the per-row list mark is gated on the screen's scope", () => {
  it("is decided by one predicate, in one place, on web", () => {
    expect(webFilesContaining("export function shouldShowListMark")).toEqual([
      "lib/listMark.ts",
    ]);
  });

  it("is asked by every row that draws a list glyph", () => {
    // `ListDot` and `FloaterListDot` are also used by list PICKERS and sidebars, where the
    // glyph identifies a list the user is choosing rather than annotating a task — so the
    // claim is narrowed to the row components, named rather than pattern-matched. A new row
    // surface belongs on this list, and the act of adding it is the moment to ask whether it
    // is a scoped screen.
    const rows = [
      "components/todo/component/TodoItemContainer.tsx",
      "features/floater/component/FloaterItemContainer.tsx",
    ];
    for (const row of rows) {
      const source = readFileSync(join(WEB_SRC, row), "utf8");
      expect(source, `${row} must ask shouldShowListMark`).toContain("shouldShowListMark");
      expect(source, `${row} must read the screen's scope`).toContain("useScopedListId");
    }

    // The mixed-feed rows are deliberately absent from that list, and their marks are
    // deliberately ungated: the calendar and Completed span every list by construction, so a
    // row there has no screen title to repeat.
    for (const feed of [
      "features/calendar/component/CalendarClient.tsx",
      "features/completed/component/ItemContainer.tsx",
    ]) {
      expect(
        readFileSync(join(WEB_SRC, feed), "utf8"),
        `${feed} is a mixed feed and must not gate its mark`,
      ).not.toContain("shouldShowListMark");
    }
  });

  it("is scoped by the two list-detail screens and nothing else", () => {
    expect(webFilesContaining("<ListScopeProvider")).toEqual([
      "features/floaterList/component/FloaterListContainer.tsx",
      "features/list/component/ListContainer.tsx",
    ]);
  });

  it("is spelled the same on all three clients", () => {
    // Same name, same two inputs, three hand-written twins. Renaming one is the cheapest way
    // for the three to drift apart without any test on any client going red.
    expect(readRepo("tday-web/src/lib/listMark.ts")).toContain(
      "export function shouldShowListMark(",
    );
    expect(
      readRepo(
        "android-compose/app/src/main/java/com/ohmz/tday/compose/feature/todos/TaskRowListMark.kt",
      ),
    ).toContain("fun shouldShowListMark(rowListId: String?, scopedListId: String?)");
    expect(readRepo("ios-swiftUI/Tday/Feature/Todos/TaskRowListMark.swift")).toContain(
      "func shouldShowListMark(rowListId: String?, scopedListId: String?)",
    );
  });
});
