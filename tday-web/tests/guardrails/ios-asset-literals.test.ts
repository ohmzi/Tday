import { existsSync, readdirSync, readFileSync } from "node:fs";
import { join, relative, resolve } from "node:path";
import { describe, expect, it } from "vitest";

/**
 * iOS asset-name literals — a name typed into Swift must be a name the catalog actually holds.
 *
 * SwiftUI resolves `Image("Name")` at run time against the asset catalogs the *target* bundles. A
 * name with no imageset behind it is not a build error and not a log line: the image is simply
 * blank. `Image("LucideTrash")` shipped that way on the Morning Sweep "Let it go" row for as long
 * as nobody looked at the screen on a device, because the glyph that exists is `ActionDelete`
 * (Lucide `trash`) and `LucideTrash2` (a different glyph, `trash-2`) — close enough to read as
 * correct in review. This machine has no Xcode, so the compiler that would not have caught it
 * anyway is not the gap; the gap is that nothing else in the suite looks at the literal.
 *
 * `settings-icons.test.ts` already does this for one file (`SettingsScreen.swift`) and for the
 * `Lucide` prefix only. This is the whole-source version: every string literal in an app target
 * that is shaped like one of our asset names — the `Lucide…`, `Nav…`, `Tile…` and `Action…`
 * families that `docs/ICONS.md` names — must be a directory `<name>.imageset` holding a
 * `Contents.json`.
 *
 * Which catalog a literal is checked against follows the project file rather than a guess. Three
 * targets bundle one — the app and the widget, which both ship `Tday/Assets.xcassets` (the same
 * file reference in `project.pbxproj`), and the watch app (`TdayWatch/Assets.xcassets`). The watch
 * complication and the share extension bundle none, so an asset literal in their sources resolves
 * to nothing at run time and is a failure by itself; giving one of them a catalog is a deliberate
 * pbxproj change that should update `CATALOG_FOR_TARGET` below in the same commit.
 *
 * Deliberately out of scope, because a literal scan cannot see them and two other guardrails do:
 * `HelpGuideScreen.iconAsset` builds `"Lucide" + PascalCase(glyph)` at run time
 * (`guide-icons.test.ts` covers every guide glyph), and `tdayLucideListAssetTable` is a plain
 * dictionary of full names that this scan reads like any other literal — its keys are not asset
 * names and do not match. `Image(systemName:)` strings are SF Symbols, not catalog entries, and
 * none of the four prefixes begins an SF Symbol name.
 */

const MONO = resolve(__dirname, "..", "..", "..");
const IOS_ROOT = resolve(MONO, "ios-swiftUI");

const APP_CATALOG = join(IOS_ROOT, "Tday", "Assets.xcassets");
const WATCH_CATALOG = join(IOS_ROOT, "TdayWatch", "Assets.xcassets");

/** Top-level source folders of the app targets, and the one catalog each bundles (null = none). */
const CATALOG_FOR_TARGET: Record<string, string | null> = {
  Tday: APP_CATALOG,
  TdayWatch: WATCH_CATALOG,
  TdayWatchWidget: null,
  // Bundled since the widget started drawing list glyphs and feed watermarks from the Lucide set.
  TdayWidget: APP_CATALOG,
  TdayShareExtension: null,
};

/** The shape of an asset name this repo mints: a known family prefix plus a PascalCase glyph. */
const ASSET_NAME = /^(?:Lucide|Nav|Tile|Action)[A-Za-z0-9]+$/;

function walkSwift(dir: string): string[] {
  const found: string[] = [];
  if (!existsSync(dir)) return found;
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    // Same rule as `ios-target-membership.test.ts`: nothing built from this repository lives in a
    // dotted directory, and `.spm-cache/` on a CI checkout holds thousands of third-party files.
    if (entry.name.startsWith(".")) continue;
    const full = join(dir, entry.name);
    if (entry.isDirectory()) found.push(...walkSwift(full));
    else if (entry.name.endsWith(".swift")) found.push(full);
  }
  return found;
}

interface Literal {
  value: string;
  /** Character offset of the literal's opening delimiter in the source. */
  at: number;
}

/**
 * Every string literal in a Swift source that is real code — comments are skipped, and so are
 * literals carrying an interpolation (`"Lucide\(name)"` names nothing a scan can resolve).
 *
 * A scanner rather than "strip `//` lines, then regex": a comment marker inside a string
 * (`"https://…"`) must not eat the rest of the line, and a string inside a `\( … )` interpolation
 * is its own literal. Handles line and nested block comments, `"…"`, `"""…"""` and `#"…"#`.
 */
function swiftStringLiterals(source: string): Literal[] {
  const found: Literal[] = [];
  const n = source.length;

  function skipBlockComment(from: number): number {
    let depth = 0;
    let i = from;
    while (i < n) {
      if (source.startsWith("/*", i)) {
        depth += 1;
        i += 2;
      } else if (source.startsWith("*/", i)) {
        depth -= 1;
        i += 2;
        if (depth === 0) return i;
      } else {
        i += 1;
      }
    }
    return n;
  }

  /** Scans code; inside an interpolation, returns the index just past its closing `)`. */
  function scanCode(from: number, inInterpolation: boolean): number {
    let i = from;
    let parens = 0;
    while (i < n) {
      if (source.startsWith("//", i)) {
        const eol = source.indexOf("\n", i);
        if (eol < 0) return n;
        i = eol;
        continue;
      }
      if (source.startsWith("/*", i)) {
        i = skipBlockComment(i);
        continue;
      }
      if (source[i] === '"' || /^#+"/.test(source.slice(i, i + 16))) {
        i = scanString(i);
        continue;
      }
      if (inInterpolation) {
        if (source[i] === "(") parens += 1;
        else if (source[i] === ")") {
          if (parens === 0) return i + 1;
          parens -= 1;
        }
      }
      i += 1;
    }
    return n;
  }

  function scanString(start: number): number {
    let i = start;
    let pounds = 0;
    while (source[i] === "#") {
      pounds += 1;
      i += 1;
    }
    const hashes = "#".repeat(pounds);
    const quote = source.startsWith('"""', i) ? '"""' : '"';
    i += quote.length;
    const closer = quote + hashes;
    const escape = `\\${hashes}`;
    let value = "";
    let interpolated = false;
    while (i < n) {
      if (source.startsWith(closer, i)) {
        if (!interpolated) found.push({ value, at: start });
        return i + closer.length;
      }
      if (source.startsWith(escape, i)) {
        if (source[i + escape.length] === "(") {
          interpolated = true;
          i = scanCode(i + escape.length + 1, true);
        } else {
          value += source.slice(i, i + escape.length + 1);
          i += escape.length + 1;
        }
        continue;
      }
      value += source[i];
      i += 1;
    }
    return n;
  }

  scanCode(0, false);
  return found;
}

function lineOf(source: string, at: number): number {
  let line = 1;
  for (let i = 0; i < at; i++) if (source[i] === "\n") line += 1;
  return line;
}

interface Reference {
  name: string;
  /** Repo-relative `file:line`, for the failure message. */
  where: string;
  /** The catalog this file's target bundles, or null when it bundles none. */
  catalog: string | null;
}

function collectReferences(): Reference[] {
  const refs: Reference[] = [];
  for (const [target, catalog] of Object.entries(CATALOG_FOR_TARGET)) {
    for (const file of walkSwift(join(IOS_ROOT, target))) {
      const source = readFileSync(file, "utf8");
      for (const literal of swiftStringLiterals(source)) {
        if (!ASSET_NAME.test(literal.value)) continue;
        refs.push({
          name: literal.value,
          where: `${relative(MONO, file)}:${lineOf(source, literal.at)}`,
          catalog,
        });
      }
    }
  }
  return refs;
}

const hasImageset = (catalog: string, name: string) =>
  existsSync(join(catalog, `${name}.imageset`, "Contents.json"));

describe("the literal scanner reads code, not prose", () => {
  const names = (source: string) => swiftStringLiterals(source).map((l) => l.value);

  it("skips line and (nested) block comments", () => {
    const source = [
      '// Image("LucideGhost")',
      '/* "ActionGhost" /* "TileGhost" */ "NavGhost" */',
      'Image("LucideReal")',
    ].join("\n");
    expect(names(source)).toEqual(["LucideReal"]);
  });

  it("does not treat // inside a string as a comment", () => {
    expect(names('let u = "https://example.test"; Image("LucideAfter")')).toEqual([
      "https://example.test",
      "LucideAfter",
    ]);
  });

  it("drops interpolated literals but still reads strings inside the interpolation", () => {
    expect(names('Image("Lucide\\(glyph)"); Text("\\(pick("TileInner"))")')).toEqual(["TileInner"]);
  });

  it("reads multi-line and raw literals", () => {
    expect(names('let a = """\nLucideMulti\n"""\nlet b = #"ActionRaw"#')).toEqual([
      "\nLucideMulti\n",
      "ActionRaw",
    ]);
  });
});

describe("iOS asset-name literals resolve to an imageset", () => {
  const references = collectReferences();

  it("finds the app's asset references (the scan is not silently empty)", () => {
    expect(references.length).toBeGreaterThan(100);
    expect(references.some((r) => r.name === "ActionDelete")).toBe(true);
    expect(references.some((r) => r.name.startsWith("Nav"))).toBe(true);
    expect(references.some((r) => r.name.startsWith("Tile"))).toBe(true);
  });

  it("every Lucide/Nav/Tile/Action literal names an imageset its target bundles", () => {
    const problems = references.flatMap((ref) => {
      if (ref.catalog === null) {
        return [`${ref.where}  "${ref.name}" — this target bundles no asset catalog`];
      }
      if (!hasImageset(ref.catalog, ref.name)) {
        return [
          `${ref.where}  "${ref.name}" — no ${ref.name}.imageset in ${relative(MONO, ref.catalog)}`,
        ];
      }
      return [];
    });
    expect(
      problems,
      "asset names with nothing behind them render blank — point each at an existing imageset " +
        `(docs/ICONS.md lists the shared glyphs) or add one:\n${problems.join("\n")}`,
    ).toEqual([]);
  });
});
