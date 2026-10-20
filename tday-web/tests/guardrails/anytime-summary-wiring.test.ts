import { readFileSync, readdirSync, statSync } from "fs";
import path from "path";
import { describe, expect, it } from "vitest";

/**
 * Keeps web local mode wired to the shared Anytime vocabulary.
 *
 * The failure this exists to stop already happened once: the twelve `summary.floater*` keys were
 * added to all ten locale files and to the Kotlin engine, the i18n parity guardrail went green
 * because every locale had them, and web local mode went on shipping the pre-fix summary for
 * months' worth of releases — reciting every row, and telling Italian users their undated tasks
 * were "in scadenza". Key parity across locales says nothing about whether any code reads them.
 *
 * Source of truth is the exporter's own key list, so a future key added to the engine is covered
 * the moment it is declared, not when someone remembers to update this file.
 */

const REPO_ROOT = path.resolve(__dirname, "..", "..", "..");
const EXPORTER = path.join(
  REPO_ROOT,
  "shared/src/jvmMain/kotlin/com/ohmz/tday/shared/guide/export/GuideContentExporter.kt",
);
const WEB_SRC = path.resolve(__dirname, "..", "..", "src");
const EN_MESSAGES = path.resolve(__dirname, "..", "..", "messages", "en.json");

/** The `floater*` entries of the exporter's SUMMARY_VALUE_KEYS list. */
function sharedFloaterKeys(): string[] {
  const source = readFileSync(EXPORTER, "utf-8");
  const start = source.indexOf("private val SUMMARY_VALUE_KEYS");
  // The list ends at a `)` alone on its own line. Scanning for the first `)` instead stops inside
  // the list's own comments — `("Anytime")` sits in one — and silently reads half the vocabulary.
  const end = source.indexOf("\n    )", start);
  const list = source.slice(start, end);
  return [...list.matchAll(/"(floater[A-Za-z]*)"/g)].map((m) => m[1]);
}

function sourceFiles(dir: string, out: string[] = []): string[] {
  for (const entry of readdirSync(dir)) {
    const full = path.join(dir, entry);
    if (statSync(full).isDirectory()) sourceFiles(full, out);
    else if (/\.(ts|tsx)$/.test(entry)) out.push(full);
  }
  return out;
}

describe("anytime summary wiring", () => {
  const keys = sharedFloaterKeys();

  it("finds the shared undated vocabulary at all", () => {
    // A rename of SUMMARY_VALUE_KEYS must fail loudly rather than silently assert nothing.
    expect(keys.length).toBeGreaterThanOrEqual(12);
    expect(keys).toContain("floaterClear");
  });

  it("declares every shared undated key in the english bundle", () => {
    const summary = (JSON.parse(readFileSync(EN_MESSAGES, "utf-8")) as {
      summary: Record<string, unknown>;
    }).summary;
    for (const key of keys) {
      expect(typeof summary[key], `summary.${key} is missing from messages/en.json`).toBe("string");
    }
  });

  it("references every shared undated key from web source", () => {
    const corpus = sourceFiles(WEB_SRC)
      .map((file) => readFileSync(file, "utf-8"))
      .join("\n");
    for (const key of keys) {
      expect(
        corpus.includes(`"${key}"`),
        `summary.${key} exists in all ten locales but no web source file reads it — ` +
          `local mode is rendering something other than the shared Anytime copy`,
      ).toBe(true);
    }
  });
});
