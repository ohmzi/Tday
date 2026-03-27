import { readdirSync, readFileSync, statSync } from "fs";
import path from "path";
import { describe, expect, it } from "vitest";

const ROOT = path.resolve(__dirname, "..", "..");
const LOCALES_ROOT = path.join(ROOT, "public", "locales");
const ENGLISH_TRANSLATION = path.join(LOCALES_ROOT, "en", "translation.json");

function flattenKeys(
  value: unknown,
  prefix = "",
  keys: string[] = [],
): string[] {
  if (!value || typeof value !== "object" || Array.isArray(value)) {
    if (prefix) keys.push(prefix);
    return keys;
  }

  for (const [childKey, childValue] of Object.entries(
    value as Record<string, unknown>,
  )) {
    const nextPrefix = prefix ? `${prefix}.${childKey}` : childKey;
    flattenKeys(childValue, nextPrefix, keys);
  }

  return keys;
}

function readTranslation(filePath: string): Record<string, unknown> {
  return JSON.parse(readFileSync(filePath, "utf-8")) as Record<string, unknown>;
}

describe("i18n locale parity", () => {
  const englishKeys = new Set(flattenKeys(readTranslation(ENGLISH_TRANSLATION)));

  const localeDirectories = readdirSync(LOCALES_ROOT)
    .map((name) => path.join(LOCALES_ROOT, name))
    .filter((directory) => statSync(directory).isDirectory());

  it("keeps every locale aligned with english keys", () => {
    for (const localeDirectory of localeDirectories) {
      const locale = path.basename(localeDirectory);
      const translationPath = path.join(localeDirectory, "translation.json");
      const localeKeys = new Set(flattenKeys(readTranslation(translationPath)));

      expect(
        [...localeKeys].sort(),
        `${locale} should not add or remove translation keys`,
      ).toEqual([...englishKeys].sort());
    }
  });
});
