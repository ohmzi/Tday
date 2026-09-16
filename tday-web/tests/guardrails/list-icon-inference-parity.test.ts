import { readdirSync, readFileSync } from "node:fs";
import { join, relative, resolve } from "node:path";
import { describe, expect, it } from "vitest";

/**
 * ONE KEYWORD TABLE, AND EVERY KEY IN IT MUST BE A GLYPH THE CLIENT CAN ACTUALLY DRAW
 *
 * A list whose owner never picked an icon takes one from its name. The words that decide
 * that live in `shared/.../listicon/ListIconInference.kt`; iOS reads a generated copy,
 * because the app links no Kotlin — `TdayApp.xcodeproj/project.pbxproj` has never named the
 * `TdayShared` framework and `Package.swift` never depended on it, so the framework
 * `shared/build.gradle.kts` declares for the iOS targets has no consumer. A committed
 * artifact written by `:shared:exportListIconTable` is the same answer `motion-tokens.ts`
 * and `TdayMotionGenerated.swift` already are to the same problem.
 *
 * ## Why this file exists when two other gates already look like they cover it
 *
 * `:shared:verifyListIconTable` proves the generated copy is not stale, and Android's
 * `ListIconInferenceKeyParityTest` proves the three clients' ICON REGISTRIES hold the same
 * 68 keys. Neither one catches the two ways this can break on iOS:
 *
 *   1. **A second word list.** The whole argument for generating the table is that three
 *      hand-kept copies drift. Nothing stops someone adding a Swift dictionary of keywords
 *      next to the matcher — the drift gate would stay green, because the generated file
 *      would still match its source. So the absence of a second list has to be asserted.
 *   2. **`todoListSettingsIconKeys`.** That array — the settings sheet's picker, and what
 *      `normalizedTodoListIconKey` validates against — is a THIRD iOS list, separate from
 *      `tdayLucideListAssetTable`, and no test has ever compared them. It matters now
 *      because the settings sheet seeds its picker from the inference and normalises the
 *      result: an inferred key missing from that array would be snapped back to `inbox`,
 *      so opening the sheet on a list wearing a name-derived cart would show an inbox and
 *      offer to save it. Every failure in this family is SILENT — `tdayLucideListAsset`
 *      answers `LucideInbox` for an unknown key with no log line — which is why they are
 *      worth a gate rather than a comment.
 *
 * The rule itself (lowercase, split on non-alphanumerics, refuse on disagreement) stays a
 * hand-written twin in `TdayListIconInference.swift`, the way `floaterRestingTier` is the
 * hand-written twin of `FloaterResting.tierFor`. Its cases are pinned in
 * `ListIconInferenceTests.swift`, copied from the shared `ListIconInferenceTest.kt`; xctest
 * in CI is that half's only gate, and this file does not pretend to stand in for it.
 *
 * ## Web
 *
 * Web reads the table for a plainer reason than iOS's — it runs no Kotlin at all, and never
 * could — so the second describe below asks the same three questions of
 * `src/generated/list-icon-table.ts`. Its rule is `src/lib/listIconInference.ts` and its
 * cases are pinned by `tests/unit/list-icon-inference.test.ts`, which runs for real in this
 * same suite; what belongs HERE is only what that test cannot see, namely whether the two
 * artifacts still agree with the Kotlin and whether a second word list has appeared. The
 * silent-failure argument is identical: `getListIcon` answers `Inbox` for an unknown key
 * with no warning, so an emittable key missing from `listIconOptions` would ship as a list
 * quietly wearing the glyph the inference was written to replace.
 */
/** Paths under the repository root — these claims reach three clients. */
const readRepo = (path: string) =>
  readFileSync(resolve(__dirname, "../../..", path), "utf8");

const KOTLIN_TABLE = "shared/src/commonMain/kotlin/com/ohmz/tday/shared/listicon/ListIconInference.kt";

/** `"work" to listOf("work", "job", …),` — one line per icon key in the shared table. */
function sharedPairs(): Array<[string, string]> {
  const source = readRepo(KOTLIN_TABLE);
  const pairs: Array<[string, string]> = [];
  for (const row of source.matchAll(/^\s*"([a-z0-9]+)" to listOf\(([^)]*)\),$/gm)) {
    for (const word of row[2].matchAll(/"([a-z0-9]+)"/g)) {
      pairs.push([word[1], row[1]]);
    }
  }
  return pairs;
}

describe("the iOS list-icon inference reads one shared table", () => {
  const IOS_GENERATED = "ios-swiftUI/Tday/UI/Theme/TdayListIconInferenceGenerated.swift";
  const IOS_MATCHER = "ios-swiftUI/Tday/UI/Theme/TdayListIconInference.swift";
  const IOS_ASSETS = "ios-swiftUI/Tday/UI/Theme/TdayTheme.swift";
  const IOS_PICKER = "ios-swiftUI/Tday/Feature/Todos/TodoListScreen.swift";

  /** `"groceries": "cart",` inside the generated `iconKeyByKeyword` literal. */
  function generatedPairs(): Array<[string, string]> {
    const body = section(readRepo(IOS_GENERATED), "iconKeyByKeyword: [String: String] = [");
    return [...body.matchAll(/"([a-z0-9]+)": "([a-z0-9]+)",/g)].map(
      (m) => [m[1], m[2]] as [string, string],
    );
  }

  function generatedEmittableKeys(): string[] {
    const body = section(readRepo(IOS_GENERATED), "emittableIconKeys: Set<String> = [");
    return [...body.matchAll(/"([a-z0-9]+)",/g)].map((m) => m[1]);
  }

  /** The text between a literal's opening line and the line that closes it. */
  function section(source: string, opener: string): string {
    const start = source.indexOf(opener);
    expect(start, `${opener} — the generated file's shape changed`).toBeGreaterThan(-1);
    const rest = source.slice(start + opener.length);
    const end = rest.indexOf("\n    ]");
    expect(end, `${opener} — no closing bracket`).toBeGreaterThan(-1);
    return rest.slice(0, end);
  }

  it("carries every word of the shared table, and no others", () => {
    // Set equality rather than a byte comparison: `verifyListIconTable` already owns the
    // bytes, and repeating that here would fail on a whitespace change with nothing to
    // learn from it. What is worth asserting twice — once in Gradle, once where the web
    // suite runs — is that the two tables say the same thing.
    const shared = sharedPairs();
    expect(shared.length).toBeGreaterThan(200);
    expect(new Map(generatedPairs())).toEqual(new Map(shared));
  });

  it("declares the emittable keys the shared table can actually produce", () => {
    const emittable = new Set(sharedPairs().map(([, key]) => key));
    expect(new Set(generatedEmittableKeys())).toEqual(emittable);
    // The caller's fallback is never an inference. If the matcher could answer "inbox",
    // "I inferred the default" and "I have nothing" would reach `tdayLucideListAsset` as
    // the same value, and the rule that a chosen icon always wins rests on telling them
    // apart.
    expect(emittable.has("inbox")).toBe(false);
  });

  it("only names glyphs iOS can draw, in both of its icon lists", () => {
    const assetTable = readRepo(IOS_ASSETS).split('tdayLucideListAssetTable: [String: String] = [')[1];
    const assets = new Map(
      [...assetTable.split("\n]")[0].matchAll(/"([a-z0-9]+)": "(Lucide[A-Za-z0-9]+)"/g)].map(
        (m) => [m[1], m[2]] as [string, string],
      ),
    );
    const pickerBlock = readRepo(IOS_PICKER).split("private let todoListSettingsIconKeys = [")[1];
    const picker = new Set(
      [...pickerBlock.split("\n]")[0].matchAll(/"([a-z0-9]+)"/g)].map((m) => m[1]),
    );

    expect(assets.size, "the iOS asset table scan found nothing — its shape changed").toBeGreaterThan(50);
    expect(picker.size, "the iOS picker scan found nothing — its shape changed").toBeGreaterThan(50);

    const emittable = generatedEmittableKeys();
    expect(emittable.filter((key) => !assets.has(key))).toEqual([]);
    expect(emittable.filter((key) => !picker.has(key))).toEqual([]);
    // Membership is not enough: a key that resolved to the inbox asset would pass the two
    // checks above and still paint the picture the inference was meant to replace.
    expect(emittable.filter((key) => assets.get(key) === "LucideInbox")).toEqual([]);
  });

  it("keeps the generated file the only place iOS spells a keyword", () => {
    const matcher = readRepo(IOS_MATCHER);
    expect(matcher).toContain("TdayListIconInferenceTable.iconKeyByKeyword");
    // A word list in the matcher, or anywhere else under ios-swiftUI, is the fork this
    // whole codegen exists to prevent. `[String: String]` is the shape one would take.
    expect(matcher).not.toContain("[String: String]");
    expect(readRepo(IOS_GENERATED)).toContain(
      "// GENERATED by :shared:exportListIconTable — do not edit by hand.",
    );
  });
});

describe("the web list-icon inference reads the same shared table", () => {
  const WEB_GENERATED = "tday-web/src/generated/list-icon-table.ts";
  const WEB_MATCHER = "tday-web/src/lib/listIconInference.ts";
  const WEB_REGISTRY = "tday-web/src/lib/listIcons.ts";

  /** `"groceries": "cart",` inside the generated `LIST_ICON_KEY_BY_KEYWORD` literal. */
  function generatedPairs(): Array<[string, string]> {
    const body = webSection("LIST_ICON_KEY_BY_KEYWORD: Readonly<Record<string, string>> = {");
    return [...body.matchAll(/"([a-z0-9]+)": "([a-z0-9]+)",/g)].map(
      (m) => [m[1], m[2]] as [string, string],
    );
  }

  function generatedEmittableKeys(): string[] {
    const body = webSection("LIST_ICON_EMITTABLE_KEYS: readonly string[] = [");
    return [...body.matchAll(/"([a-z0-9]+)",/g)].map((m) => m[1]);
  }

  /** The text between a literal's opening line and the line that closes it. */
  function webSection(opener: string): string {
    const source = readRepo(WEB_GENERATED);
    const start = source.indexOf(opener);
    expect(start, `${opener} — the generated file's shape changed`).toBeGreaterThan(-1);
    const rest = source.slice(start + opener.length);
    const end = rest.search(/\n(\}|\]);/);
    expect(end, `${opener} — no closing bracket`).toBeGreaterThan(-1);
    return rest.slice(0, end);
  }

  /**
   * The `key:` of every entry in `listIconOptions`, with the component it draws.
   *
   * Misses exactly one row — `inbox`, whose key is spelled `DEFAULT_LIST_ICON_KEY`
   * rather than a literal — and that is harmless here: `inbox` is the one key the
   * matcher is forbidden to emit, so it is never on either side of these checks.
   */
  function registryEntries(): Map<string, string> {
    const block = readRepo(WEB_REGISTRY).split("export const listIconOptions: ListIconOption[] = [")[1];
    return new Map(
      [...block.split("\n];")[0].matchAll(/key: "([a-z0-9]+)",[^}]*icon: ([A-Za-z0-9]+)/g)].map(
        (m) => [m[1], m[2]] as [string, string],
      ),
    );
  }

  it("carries every word of the shared table, and no others", () => {
    // Set equality rather than a byte comparison: `verifyListIconTable` already owns the
    // bytes, and repeating that here would fail on a whitespace change with nothing to
    // learn from it. What is worth asserting where the web suite runs is that the two
    // tables say the same thing.
    const shared = sharedPairs();
    expect(shared.length).toBeGreaterThan(200);
    expect(new Map(generatedPairs())).toEqual(new Map(shared));
  });

  it("declares the emittable keys the shared table can actually produce", () => {
    const emittable = new Set(sharedPairs().map(([, key]) => key));
    expect(new Set(generatedEmittableKeys())).toEqual(emittable);
    // The caller's fallback is never an inference. If the matcher could answer "inbox",
    // "I inferred the default" and "I have nothing" would reach `normalizeListIconKey`
    // as the same value, and `resolveListIconKey`'s never-override rule rests on telling
    // them apart.
    expect(emittable.has("inbox")).toBe(false);
  });

  it("only names glyphs the web registry can draw", () => {
    const registry = registryEntries();
    expect(registry.size, "the listIconOptions scan found nothing — its shape changed")
      .toBeGreaterThan(50);

    const emittable = generatedEmittableKeys();
    expect(emittable.filter((key) => !registry.has(key))).toEqual([]);
    // Membership is not enough: a key mapped to the Inbox component would pass the check
    // above and still paint the picture the inference was written to replace. It is also
    // not hypothetical — `listIconOptions` already maps four sport keys to the same bare
    // `Circle`, which is why none of them is in the table.
    expect(emittable.filter((key) => registry.get(key) === "Inbox")).toEqual([]);
  });

  it("keeps the generated file the only place web spells a keyword", () => {
    const matcher = readRepo(WEB_MATCHER);
    expect(matcher).toContain("LIST_ICON_KEY_BY_KEYWORD");
    expect(readRepo(WEB_GENERATED)).toContain(
      "// GENERATED by :shared:exportListIconTable — do not edit by hand.",
    );

    // A second word list anywhere under `src` is the fork the whole codegen exists to
    // prevent, and it would leave every other gate green: the generated file would still
    // match its source. "groceries" stands in for the table — it is a word no other part
    // of this app has any reason to spell — so any file but the generated one containing
    // it is either a copied table or a fixture that should be reading the real one.
    const spellings = webSourceFiles().filter((file) =>
      readFileSync(file, "utf8").includes('"groceries"'),
    );
    expect(spellings.map((file) => relative(WEB_SRC, file))).toEqual([
      "generated/list-icon-table.ts",
    ]);
  });
});

const WEB_SRC = resolve(__dirname, "../../src");

/** Every TypeScript source under `tday-web/src`, in a stable order. */
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
