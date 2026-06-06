import { readFileSync } from "fs";
import path from "path";
import { describe, expect, it } from "vitest";

// Translations are bundled offline from messages/<locale>.json (imported into
// src/i18n.ts). Every locale must have exactly the same key set as English.
const MESSAGES_ROOT = path.resolve(__dirname, "..", "..", "messages");
const LOCALES = ["en", "es", "fr", "de", "it", "pt", "ru", "zh", "ja", "ms"];

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

function readTranslation(locale: string): Record<string, unknown> {
  return JSON.parse(
    readFileSync(path.join(MESSAGES_ROOT, `${locale}.json`), "utf-8"),
  ) as Record<string, unknown>;
}

describe("i18n locale parity", () => {
  const englishKeys = [...new Set(flattenKeys(readTranslation("en")))].sort();

  it("keeps every locale aligned with english keys", () => {
    for (const locale of LOCALES.filter((l) => l !== "en")) {
      const localeKeys = [...new Set(flattenKeys(readTranslation(locale)))].sort();
      expect(
        localeKeys,
        `${locale} should not add or remove translation keys`,
      ).toEqual(englishKeys);
    }
  });
});
