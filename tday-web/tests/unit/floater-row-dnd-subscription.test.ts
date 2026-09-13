/**
 * The floater row's dnd-kit subscription, which had nothing to subscribe to.
 *
 * Every Anytime row called `useSortable({ id })` while there is no `DndContext`
 * anywhere above it — floater order is fixed (priority → most-recently-modified
 * → id, shared with both native clients and the widgets) and drag-to-reorder is
 * retired there, as `FloaterGroup`'s own doc comment says. dnd-kit does not
 * throw for that: the hook resolves against the library's default context, so
 * every row in the list quietly registered a droppable nobody could drag onto
 * and re-ran its measuring work on every render, for a `transform` that was
 * permanently null and an `isDragging` that was permanently false.
 *
 * That is a source-level fact, not a rendered one — the rendered output was
 * identical with the subscription and without it, which is exactly why it
 * survived review — so this is a static read of the source, the same approach
 * `tests/guardrails/motion-reachability-web.test.ts` takes for the same reason.
 *
 * The premise is asserted first. If a drag context ever does arrive over the
 * Anytime list, the second test below is the one to delete, and wiring the rows
 * back up is the fix — not deleting the premise.
 */

import { readdirSync, readFileSync, existsSync } from "fs";
import path from "path";
import { describe, expect, it } from "vitest";

const SRC = path.resolve(__dirname, "..", "..", "src");

/** The whole render tree an Anytime row is mounted in, screens included. */
const FLOATER_TREES = [
  path.join(SRC, "features", "floater"),
  path.join(SRC, "features", "floaterList"),
];

/**
 * Every file that puts a dnd-kit provider on the screen. Kept as an explicit
 * list rather than only a search, so that a fifth one arriving fails here and
 * gets a human to check whether it now sits above the Anytime list.
 */
const KNOWN_DND_PROVIDERS = [
  "components/todo/component/TodoForm/TaskStepsSection.tsx",
  "components/todo/dnd/TimelineDndContext.tsx",
  "components/todo/dnd/TodayBucketDnd.tsx",
  "features/calendar/component/CalendarClient.tsx",
];

function walk(dir: string): string[] {
  const out: string[] = [];
  if (!existsSync(dir)) return out;
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) out.push(...walk(full));
    else if (/\.tsx?$/.test(entry.name)) out.push(full);
  }
  return out;
}

const rel = (file: string) => path.relative(SRC, file).split(path.sep).join("/");

describe("the Anytime row and drag-and-drop", () => {
  it("mounts inside no dnd-kit context, on any screen", () => {
    const providers = walk(SRC)
      .filter((file) => /<DndContext[\s>]/.test(readFileSync(file, "utf-8")))
      .map(rel)
      .sort();

    expect(providers).toEqual(KNOWN_DND_PROVIDERS);
    // None of the four is a floater screen, and none of them renders one: the
    // Anytime list is reached from `FloaterListContainer` and
    // `NativeFloaterTaskHomeDashboard`, neither of which is under any of these.
    expect(providers.filter((file) => file.includes("floater"))).toEqual([]);
  });

  it("therefore subscribes to nothing from dnd-kit", () => {
    const offenders = FLOATER_TREES.flatMap(walk)
      .filter((file) => /["']@dnd-kit\//.test(readFileSync(file, "utf-8")))
      .map(rel);

    expect(offenders).toEqual([]);
  });

  it("still takes the scheduled row's dnd-kit import as legitimate", () => {
    // The counter-example, so this file is never read as "dnd-kit is banned".
    // `TodoItemContainer` keeps its `useSortable` for the opposite reason: the
    // scheduled row really does render inside `TimelineDndContext` /
    // `TodayBucketDndContext`.
    const scheduledRow = readFileSync(
      path.join(SRC, "components", "todo", "component", "TodoItemContainer.tsx"),
      "utf-8",
    );
    expect(scheduledRow).toContain("@dnd-kit/sortable");
  });
});
