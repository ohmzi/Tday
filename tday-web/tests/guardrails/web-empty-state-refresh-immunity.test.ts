import { readFileSync, readdirSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";

/**
 * The web empty states are decided by an ANSWER, never by a request.
 *
 * Android and iOS both shipped the same defect and both were fixed on this branch: their gates
 * spelled "we have an answer" as `!isLoading`, and `isLoading` is raised there by `refresh()`
 * alone, over a feed already hydrated from cache. The pull that asks the app to re-check its
 * answer was the term that withdrew it. Web was found NOT to have it, for one reason and one
 * only: the boolean its gates read is TanStack Query v5's `isLoading` — `isPending &&
 * isFetching`, where `status` leaves `pending` the moment `data !== undefined` and never goes
 * back — so it means "no answer yet" and a revalidation over cached rows cannot move it.
 *
 * That is a property of WHICH FLAG IS WIRED IN, not of the gate's shape, and it is one
 * `isFetching:` away from being lost by a reader who thinks the two names are synonyms. The
 * query hooks already return `isFetching`; nothing renders from it. This file is what keeps that
 * true, the way `ios-empty-state-presence.test.ts` keeps the calendar's `else` branch off
 * `isLoading` — an absence is exactly what a static read can see, and there is no other way to
 * see this one, since the behaviour it protects is a frame that correctly never happens.
 *
 * `tests/unit/empty-state-survives-refresh.test.tsx` is the other half: it drives the real query
 * hooks against a held-open server and asserts both directions of the rule as behaviour. This
 * file asserts the wiring that makes that behaviour reachable at all.
 */

const WEB = resolve(__dirname, "..", "..");
const SRC = resolve(WEB, "src");

/**
 * Reads a source file with its comments removed.
 *
 * The gates explain themselves at length and the explanations quote the very term being banned —
 * `useFloaterEmptyState`'s comment says `isFetching` three times, on purpose, because naming the
 * trap is how the next reader avoids it. Stripping first means every assertion below is about
 * code, and the prose stays free to argue.
 */
function readCode(file: string): string {
  return readFileSync(file, "utf8")
    .replace(/\/\*[\s\S]*?\*\//g, "")
    .split("\n")
    .filter((line) => !line.trimStart().startsWith("//"))
    .join("\n");
}

/** The single line a gate is declared on, so an assertion can be about that line and not the file. */
function lineContaining(source: string, needle: string): string {
  return source.split("\n").find((line) => line.includes(needle)) ?? "";
}

/**
 * Every `.ts`/`.tsx` under `dir`, with `query/` folders left out.
 *
 * The split is the rule itself: the query layer is allowed — encouraged — to return `isFetching`,
 * because that is where a future refresh indicator would get it from. What may not happen is the
 * flag reaching a screen.
 */
function sourcesOutsideQueryLayer(dir: string): string[] {
  const out: string[] = [];
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    if (entry.isDirectory()) {
      if (entry.name === "query") continue;
      out.push(...sourcesOutsideQueryLayer(resolve(dir, entry.name)));
    } else if (/\.tsx?$/.test(entry.name)) {
      out.push(resolve(dir, entry.name));
    }
  }
  return out;
}

const GATES: ReadonlyArray<{ file: string; path: string; loading: string }> = [
  // The screen in the report.
  {
    file: "useFloaterEmptyState.ts",
    path: resolve(SRC, "features/floater/lib/useFloaterEmptyState.ts"),
    loading: "isLoading",
  },
  {
    file: "useListEmptyState.ts",
    path: resolve(SRC, "features/list/lib/useListEmptyState.ts"),
    loading: "listTodosLoading",
  },
  // One gate for Today, All, Priority, Scheduled and Overdue — five screens, not one.
  {
    file: "useTimelineEmptyState.ts",
    path: resolve(SRC, "features/todayTodos/lib/useTimelineEmptyState.ts"),
    loading: "todoLoading",
  },
];

describe("no web empty-state gate can be decided by a request in flight", () => {
  for (const gate of GATES) {
    const code = readCode(gate.path);

    it(`${gate.file} names neither isFetching nor isRefetching`, () => {
      // Both, because they are the same mistake at two scopes: `isFetching` is true for every
      // request this query makes, `isRefetching` for every one after the first. Either would make
      // the pull-to-refresh gesture the term that withdraws the answer, which is the native bug
      // verbatim.
      expect(code).not.toBe("");
      expect(code).not.toContain("isFetching");
      expect(code).not.toContain("isRefetching");
    });

    it(`${gate.file} still withholds the scene until a first answer exists`, () => {
      // The ban above has an obvious wrong way to satisfy it — drop the loading term altogether —
      // and that way is a worse bug than the one being prevented: "No floater tasks", said to
      // someone whose tasks are still on the wire. The two assertions are kept side by side so
      // neither can be read as the whole rule.
      const declaration = lineContaining(code, "const showEmpty");
      expect(declaration).not.toBe("");
      expect(declaration).toContain(`!${gate.loading}`);
    });
  }
});

describe("the flag those gates read is the query's isLoading and not its isFetching", () => {
  // The gates only ever see a boolean and a name. What decides whether web has the native bug is
  // the one line in each query module that chooses which of the two flags gets that name, and the
  // one line in each container that passes it on — so both are pinned here, together, rather than
  // left to agree by convention.
  const WIRING: ReadonlyArray<{ name: string; query: string; consumer: string; passes: string }> = [
    {
      name: "the Anytime feed",
      query: "features/floater/query/get-floater.ts",
      consumer: "features/floater/component/NativeFloaterTaskHomeDashboard.tsx",
      passes: "isLoading: floaterLoading",
    },
    {
      name: "one Anytime list",
      query: "features/floaterList/query/get-floater-list.ts",
      consumer: "features/floaterList/component/FloaterListContainer.tsx",
      passes: "isLoading: floaterListLoading",
    },
    {
      name: "one list",
      query: "features/list/query/get-list-todos.ts",
      consumer: "features/list/component/ListContainer.tsx",
      passes: "listTodosLoading",
    },
    {
      name: "the scoped timeline",
      query: "features/todayTodos/query/get-todo-timeline.ts",
      consumer: "features/todayTodos/component/AllTasksTimelineContainer.tsx",
      passes: "todoLoading",
    },
  ];

  for (const wiring of WIRING) {
    it(`${wiring.name} gives its gate the flag that means "no answer yet"`, () => {
      const query = readCode(resolve(SRC, wiring.query));
      const consumer = readCode(resolve(SRC, wiring.consumer));
      const alias = wiring.passes.includes(": ")
        ? wiring.passes.slice(wiring.passes.indexOf(": ") + 2)
        : wiring.passes;

      expect(query).toContain(`isLoading: ${alias}`);
      expect(consumer).toContain(wiring.passes);
    });
  }

  it("hands the first load to the skeleton rather than to the empty scene", () => {
    // The other end of the same boolean, and the reason dropping the loading term would be a
    // regression rather than a simplification: on the two Anytime screens AWAITING_FIRST is
    // already answered — by the Phase 9 row skeleton, off this exact flag.
    const floaterHome = readCode(
      resolve(SRC, "features/floater/component/NativeFloaterTaskHomeDashboard.tsx"),
    );
    const floaterList = readCode(
      resolve(SRC, "features/floaterList/component/FloaterListContainer.tsx"),
    );
    expect(floaterHome).toContain("useSkeletonCrossfade(floaterLoading)");
    expect(floaterList).toContain("useSkeletonCrossfade(floaterListLoading)");
  });
});

describe("isFetching stays in the query layer of the feeds that own these gates", () => {
  // Scoped to the four feature trees the reported bug lives in rather than to all of `src`: this
  // is the blast radius of the one copy-pasted gate, and a ban wider than the defect would start
  // deciding unrelated screens' spinners. Inside it, the rule is absolute — a flag that is true
  // for every revalidation has no business above the query folder, where the only thing left to
  // do with it is render something.
  const FEATURES = ["features/floater", "features/floaterList", "features/list", "features/todayTodos"];

  for (const feature of FEATURES) {
    it(`${feature} renders nothing from a revalidation flag`, () => {
      const offenders = sourcesOutsideQueryLayer(resolve(SRC, feature))
        .filter((file) => /\bisFetching\b|\bisRefetching\b/.test(readCode(file)))
        .map((file) => file.slice(SRC.length + 1));

      expect(offenders, `${feature} reads a revalidation flag outside its query layer`).toEqual([]);
    });
  }
});
