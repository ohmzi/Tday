import { readFileSync } from "fs";
import path from "path";
import { beforeEach, describe, expect, it, vi } from "vitest";

import {
  compactSummaryTitle,
  floaterPileBand,
  planFloaterSummary,
  renderFloaterSummary,
  type FloaterSummaryTask,
} from "@/lib/floaterSummary";

/**
 * Quality tests for the undated ("Anytime") summary in web local mode.
 *
 * Deliberately the same shape as the shared Kotlin `FloaterSummaryTest`: properties computed from
 * the input, not literal copy, checked in all ten locales. Local mode is the half the Kotlin
 * rewrite could not reach — a browser workspace never calls the backend — and it kept reciting
 * "both are due anytime", which Italian renders as "entrambe sono in scadenza", *both are
 * expiring*, for tasks that by definition expire never.
 */

const LOCALES = ["en", "es", "fr", "de", "it", "pt", "ru", "zh", "ja", "ms"];
const MESSAGES_ROOT = path.resolve(__dirname, "..", "..", "messages");
const DAY = 86_400_000;
const NOW = 1_780_390_800_000;

type Bundle = Record<string, unknown>;

const bundles: Record<string, Bundle> = Object.fromEntries(
  LOCALES.map((locale) => [
    locale,
    (JSON.parse(readFileSync(path.join(MESSAGES_ROOT, `${locale}.json`), "utf-8")) as {
      summary: Bundle;
    }).summary,
  ]),
);

/** The naive `{{param}}` replace i18next does for these keys, with no plural machinery. */
function translator(locale: string) {
  return (key: string, params?: Record<string, unknown>) => {
    const bare = key.replace(/^summary:/, "");
    let out = String(bundles[locale][bare] ?? bundles.en[bare] ?? bare);
    for (const [name, value] of Object.entries(params ?? {})) {
      out = out.replaceAll(`{{${name}}}`, String(value));
    }
    return out;
  };
}

function task(
  title: string,
  overrides: Partial<FloaterSummaryTask> = {},
): FloaterSummaryTask {
  return {
    title,
    priority: "Low",
    pinned: false,
    updatedAtEpochMs: null,
    ...overrides,
  };
}

const plain = (count: number) =>
  Array.from({ length: count }, (_, i) => task(`Floater number ${i + 1}`));

function summarize(tasks: FloaterSummaryTask[], locale = "en"): string {
  return renderFloaterSummary(planFloaterSummary(tasks, NOW), translator(locale));
}

/**
 * Keys whose wording is ABOUT DEADLINES. An undated task has none, so none of their literal
 * fragments may appear in an undated summary, in any locale. This is the check that catches the
 * Italian "in scadenza"; it reads the bundle rather than a hand-written word list so a future edit
 * to a due phrase is covered automatically.
 */
const DUE_VOCABULARY_KEYS = [
  "dueAnytime",
  "dueOnDate",
  "dueTodayWindow",
  "dueTomorrowWindow",
  "dueYesterdayWindow",
  "dueTonight",
  "targetToday",
  "targetTomorrow",
  "targetYesterday",
  "dayGroupedPresent",
  "dayGroupedPast",
  "overdueCatchUp",
];

const isCjk = (c: string) => {
  const code = c.codePointAt(0) ?? 0;
  return code >= 0x3040 && code <= 0x9fff;
};

function literalFragments(template: string): string[] {
  return template
    .split(/\{\{[a-zA-Z]+\}\}/)
    .map((part) => part.replace(/^[\s,.;:—·、，。：-]+|[\s,.;:—·、，。：-]+$/g, ""))
    .filter((f) => f.length >= 3 || (f.length >= 2 && [...f].some(isCjk)));
}

/** Whole-word containment, so German "war" inside "wartet" is not a false positive. */
function containsWord(haystack: string, fragment: string): boolean {
  if ([...fragment].some(isCjk)) return haystack.includes(fragment);
  const letter = /\p{L}/u;
  let from = haystack.indexOf(fragment);
  while (from >= 0) {
    const before = haystack[from - 1];
    const after = haystack[from + fragment.length];
    if (!(before && letter.test(before)) && !(after && letter.test(after))) return true;
    from = haystack.indexOf(fragment, from + 1);
  }
  return false;
}

describe("anytime summary copy", () => {
  it("never uses deadline vocabulary, in any locale", () => {
    const sets = [
      plain(1),
      plain(3),
      plain(12),
      [task("Renew passport", { pinned: true }), task("Fix the bike light")],
      Array.from({ length: 5 }, (_, i) =>
        task(`Dormant ${i}`, { updatedAtEpochMs: NOW - 200 * DAY }),
      ),
    ];
    for (const locale of LOCALES) {
      const forbidden = [
        ...new Set(DUE_VOCABULARY_KEYS.flatMap((key) => literalFragments(translator(locale)(key)))),
      ];
      for (const tasks of sets) {
        const out = summarize(tasks, locale);
        for (const fragment of forbidden) {
          expect(
            containsWord(out, fragment),
            `[${locale}] leaked deadline wording "${fragment}": ${out}`,
          ).toBe(false);
        }
      }
    }
  });

  it("echoes at most one title back at the list", () => {
    // Zero-padded so no title is a prefix of another.
    const titles = Array.from({ length: 12 }, (_, i) =>
      `Distinct title ${String(i + 1).padStart(2, "0")}`,
    );
    const sets = [
      titles.map((t) => task(t)),
      titles.map((t, i) => task(t, { pinned: i === 3 })),
      titles.map((t, i) => task(t, { pinned: i >= 9 })),
      titles.map((t, i) => task(t, { priority: i === 5 ? "High" : "Low" })),
    ];
    for (const locale of LOCALES) {
      for (const tasks of sets) {
        const out = summarize(tasks, locale);
        const echoed = titles.filter((t) => out.includes(t)).length;
        expect(echoed, `[${locale}] echoed ${echoed} titles: ${out}`).toBeLessThanOrEqual(1);
      }
    }
  });

  it("does not grow with the list", () => {
    for (const locale of LOCALES) {
      expect(summarize(plain(12), locale).length).toBe(summarize(plain(40), locale).length);
      const big = plain(40);
      const listLength = big.reduce((sum, t) => sum + t.title.length, 0);
      expect(summarize(big, locale).length * 4).toBeLessThan(listLength);
    }
  });

  it("states no number the input cannot justify", () => {
    const tasks = Array.from({ length: 7 }, (_, i) =>
      task(`Waiting ${i + 1}`, {
        pinned: i < 2,
        updatedAtEpochMs: i === 6 ? NOW - 120 * DAY : NOW - DAY,
      }),
    );
    const derivable = new Set(
      [
        tasks.length,
        tasks.filter((t) => t.pinned).length,
        tasks.filter((t) => (t.updatedAtEpochMs ?? NOW) <= NOW - 90 * DAY).length,
        tasks.filter((t) => t.priority === "High").length,
      ].map(String),
    );
    for (const locale of LOCALES) {
      const out = summarize(tasks, locale);
      for (const match of out.matchAll(/\d+/g)) {
        expect(derivable.has(match[0]), `[${locale}] stated ${match[0]}: ${out}`).toBe(true);
      }
    }
  });

  it("renders real sentences rather than keys or placeholders", () => {
    const tasks = [task("Renew passport", { pinned: true }), task("Call the dentist")];
    for (const locale of LOCALES) {
      const out = summarize(tasks, locale);
      expect(out, `[${locale}]`).not.toContain("{{");
      expect(out, `[${locale}]`).not.toContain("floater");
      expect(out, `[${locale}]`).toContain("Renew passport");
      expect(out.length, `[${locale}] implausible length: ${out}`).toBeGreaterThan(9);
      expect(out.length, `[${locale}] implausible length: ${out}`).toBeLessThan(221);
    }
  });

  it("lets pinning and dormancy move the output", () => {
    const base = plain(4);
    for (const locale of LOCALES) {
      expect(summarize(base.map((t, i) => ({ ...t, pinned: i === 1 })), locale)).not.toBe(
        summarize(base, locale),
      );
      expect(
        summarize(base.map((t) => ({ ...t, updatedAtEpochMs: NOW - 200 * DAY })), locale),
      ).not.toBe(summarize(base.map((t) => ({ ...t, updatedAtEpochMs: NOW - 2 * DAY })), locale));
    }
  });

  it("reads differently for structurally different sets", () => {
    const shapes = [
      plain(1),
      plain(3),
      plain(8),
      plain(20),
      plain(3).map((t, i) => ({ ...t, pinned: i === 0 })),
      plain(3).map((t) => ({ ...t, pinned: true })),
      plain(3).map((t, i) => ({ ...t, priority: i === 0 ? "High" : "Low" })),
      plain(3).map((t) => ({ ...t, updatedAtEpochMs: NOW - 200 * DAY })),
    ];
    for (const locale of LOCALES) {
      const rendered = shapes.map((shape) => summarize(shape, locale));
      expect(new Set(rendered).size, `[${locale}] shapes collapsed: ${rendered}`).toBe(
        rendered.length,
      );
    }
  });

  it("joins without a stray gap after a CJK full stop", () => {
    const tasks = [task("Renew passport", { pinned: true }), task("Call the dentist")];
    for (const locale of ["zh", "ja"]) {
      expect(summarize(tasks, locale)).not.toContain("。 ");
    }
    expect(summarize(tasks, "en")).toContain(". ");
  });

  it("never names the only row on screen", () => {
    const plan = planFloaterSummary([task("Renew passport", { pinned: true })], NOW);
    expect(plan.note).toBe("none");
    expect(plan.noteTitle).toBeNull();
    // A note that COUNTS rather than points still survives: it says something new.
    expect(
      planFloaterSummary([task("Dormant", { updatedAtEpochMs: NOW - 200 * DAY })], NOW).note,
    ).toBe("restingAll");
  });

  it("mirrors the shared planner's band boundaries and note precedence", () => {
    expect(floaterPileBand(1)).toBe("one");
    expect(floaterPileBand(2)).toBe("few");
    expect(floaterPileBand(4)).toBe("few");
    expect(floaterPileBand(5)).toBe("some");
    expect(floaterPileBand(11)).toBe("some");
    expect(floaterPileBand(12)).toBe("many");

    const note = (tasks: FloaterSummaryTask[]) => planFloaterSummary(tasks, NOW).note;
    expect(note(plain(3))).toBe("none");
    expect(note(plain(3).map((t, i) => ({ ...t, pinned: i === 0 })))).toBe("pinnedOne");
    expect(note(plain(3).map((t, i) => ({ ...t, pinned: i < 2 })))).toBe("pinnedMany");
    expect(
      note([
        task("a", { updatedAtEpochMs: NOW - 200 * DAY }),
        task("b", { updatedAtEpochMs: NOW - DAY }),
      ]),
    ).toBe("restingOne");
    // A wholly dormant pile outranks a pin — the dormancy is the story, not the mark.
    expect(
      note(plain(3).map((t) => ({ ...t, pinned: true, updatedAtEpochMs: NOW - 200 * DAY }))),
    ).toBe("restingAll");
    expect(note(plain(3).map((t, i) => ({ ...t, priority: i === 0 ? "High" : "Low" })))).toBe(
      "priorityOne",
    );
    // Medium is worth naming too, but ranks below High: a set with only Medium tasks gets the
    // medium note, and a set with both gets the High note, not this one.
    expect(
      note(plain(3).map((t, i) => ({ ...t, priority: i === 0 ? "Medium" : "Low" }))),
    ).toBe("mediumOne");
    expect(note(plain(3).map((t) => ({ ...t, priority: "Medium" })))).toBe("mediumMany");
    expect(
      note(plain(3).map((t, i) => ({ ...t, priority: i === 0 ? "High" : "Medium" }))),
    ).toBe("priorityOne");
  });

  it("names the task at the top of the list", () => {
    // Mirrors TaskSortEngine.compareFloaters: pinned, then priority, then most recent.
    const tasks = [
      task("Low and old", { updatedAtEpochMs: NOW - 10 * DAY }),
      task("High priority", { priority: "High" }),
      task("Pinned", { pinned: true }),
    ];
    expect(planFloaterSummary(tasks, NOW).noteTitle).toBe("Pinned");
    expect(planFloaterSummary(tasks.filter((t) => !t.pinned), NOW).noteTitle).toBe("High priority");
  });

  it("compacts an over-long title the way the engine does", () => {
    const t = translator("en");
    expect(compactSummaryTitle("  spaced   out  ", t)).toBe("spaced out");
    expect(compactSummaryTitle("", t)).toBe(String(bundles.en.untitledTask));
    expect(compactSummaryTitle("x".repeat(60), t)).toBe(`${"x".repeat(43)}...`);
  });
});

// ---- the wiring: local mode must actually reach the above ----

const workspace = {
  floaters: [] as Array<Record<string, unknown>>,
  todos: [],
  completedTodos: [],
};

vi.mock("@/i18n", () => ({
  default: {
    t: (key: string, params?: Record<string, unknown>) => {
      const bare = key.replace(/^summary:/, "");
      const raw = JSON.parse(
        readFileSync(path.resolve(__dirname, "..", "..", "messages", "en.json"), "utf-8"),
      ) as { summary: Record<string, unknown> };
      if (params && "returnObjects" in params) return raw.summary[bare];
      let out = String(raw.summary[bare] ?? bare);
      for (const [name, value] of Object.entries(params ?? {})) {
        out = out.replaceAll(`{{${name}}}`, String(value));
      }
      return out;
    },
  },
}));

vi.mock("@/lib/local/localDb", () => ({
  loadWorkspace: () => workspace,
  LOCAL_USER_ID: "local-user",
}));

describe("local mode Anytime summary", () => {
  beforeEach(() => {
    workspace.floaters = [];
  });

  const floaterRow = (
    id: string,
    overrides: Record<string, unknown> = {},
  ): Record<string, unknown> => ({
    id,
    title: id,
    description: null,
    pinned: false,
    priority: "Low",
    completed: false,
    order: 0,
    listID: null,
    createdAt: "2026-01-01T00:00:00.000Z",
    updatedAt: "2026-01-01T00:00:00.000Z",
    ...overrides,
  });

  async function summarizeLocalFloaters() {
    const { summarizeLocal } = await import("@/lib/local/localSummary");
    return summarizeLocal({ mode: "floater", timeZone: "UTC" }) as {
      summary: string;
      taskCount: number;
    };
  }

  it("summarizes by shape instead of reciting the rows", async () => {
    workspace.floaters = [
      floaterRow("Renew passport"),
      floaterRow("Fix the bike light"),
      floaterRow("Sort the loft boxes"),
    ];
    const result = await summarizeLocalFloaters();
    // The pre-fix output was "Start with Renew passport, which is anytime. Next up, Fix the bike
    // light and Sort the loft boxes, both are due anytime." — every row reprinted, with a
    // deadline asserted for tasks that have none.
    expect(result.summary).not.toContain("Renew passport");
    expect(result.summary).not.toContain("Sort the loft boxes");
    expect(result.summary.toLowerCase()).not.toContain("due");
    expect(result.summary.toLowerCase()).not.toContain("anytime");
    expect(result.taskCount).toBe(3);
  });

  it("says nothing is waiting rather than nothing needs attention", async () => {
    const result = await summarizeLocalFloaters();
    const bundle = bundles.en;
    expect(result.summary).toBe(String(bundle.floaterClear));
    expect(result.summary).not.toBe(String(bundle.clearForNow));
    expect(result.taskCount).toBe(0);
  });

  it("reads the row's own last-write clock, so the list and the summary agree", async () => {
    const old = new Date(Date.now() - 200 * DAY).toISOString();
    const fresh = new Date(Date.now() - DAY).toISOString();
    workspace.floaters = [1, 2, 3].map((i) => floaterRow(`Waiting ${i}`, { updatedAt: old }));
    const dormant = (await summarizeLocalFloaters()).summary;
    workspace.floaters = [1, 2, 3].map((i) => floaterRow(`Waiting ${i}`, { updatedAt: fresh }));
    expect(dormant).not.toBe((await summarizeLocalFloaters()).summary);
  });
});
