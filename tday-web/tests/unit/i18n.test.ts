// @vitest-environment jsdom

import { describe, expect, it } from "vitest";

import i18n, { SUPPORTED_LOCALES } from "@/i18n";

describe("i18n bundled resources", () => {
  it("bundles every supported locale with split namespaces (offline, no HTTP)", () => {
    for (const locale of SUPPORTED_LOCALES) {
      expect(
        i18n.hasResourceBundle(locale, "translation"),
        `${locale} translation namespace should be bundled`,
      ).toBe(true);
      expect(
        i18n.hasResourceBundle(locale, "app"),
        `${locale} app namespace should be bundled`,
      ).toBe(true);
    }
  });

  it("resolves namespaced keys from the bundled resources", () => {
    expect(i18n.getResource("en", "app", "today")).toBe("Today");
    expect(i18n.getResource("en", "today", "addATask")).toBe("Add a task");
    expect(i18n.getResource("en", "completed", "title")).toBe(
      "Completion history",
    );
    // Non-English locales resolve to translated (non-empty) values.
    expect(i18n.getResource("es", "app", "today")).toBeTruthy();
    expect(i18n.getResource("ja", "completed", "title")).toBeTruthy();
  });

  it("keeps the default translation namespace as the full locale payload", () => {
    const en = i18n.getResourceBundle("en", "translation") as Record<
      string,
      unknown
    >;
    expect(en.app).toMatchObject({ today: "Today" });
    expect(en.today).toMatchObject({ addATask: "Add a task" });
    expect(en.completed).toMatchObject({ title: "Completion history" });
  });
});
