import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";

/**
 * ONE KEYWORD TABLE, AND EVERY KEY IN IT MUST BE A GLYPH iOS CAN ACTUALLY DRAW
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
 */
describe("the iOS list-icon inference reads one shared table", () => {
  /** Paths under the repository root — this claim reaches two clients. */
  const readRepo = (path: string) =>
    readFileSync(resolve(__dirname, "../../..", path), "utf8");

  const KOTLIN_TABLE = "shared/src/commonMain/kotlin/com/ohmz/tday/shared/listicon/ListIconInference.kt";
  const IOS_GENERATED = "ios-swiftUI/Tday/UI/Theme/TdayListIconInferenceGenerated.swift";
  const IOS_MATCHER = "ios-swiftUI/Tday/UI/Theme/TdayListIconInference.swift";
  const IOS_ASSETS = "ios-swiftUI/Tday/UI/Theme/TdayTheme.swift";
  const IOS_PICKER = "ios-swiftUI/Tday/Feature/Todos/TodoListScreen.swift";

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
