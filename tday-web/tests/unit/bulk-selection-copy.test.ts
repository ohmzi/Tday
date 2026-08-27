// @vitest-environment jsdom

import { describe, expect, it } from "vitest";

import i18n, { SUPPORTED_LOCALES } from "@/i18n";

/**
 * The bar, the two pickers and the two confirmations read all of their copy out
 * of the `app` namespace, and half of it carries a count. i18next resolves a
 * counted key by looking for the plural suffixes the *target language* names, so
 * a family that reads fine in English can silently fall through to the raw key
 * in Russian. Nothing else in the suite would catch that.
 */
const PLAIN_KEYS = [
  "bulkSelect",
  "bulkSelectAll",
  "bulkDeselectAll",
  "bulkComplete",
  "bulkPriority",
  "bulkMove",
  "bulkDelete",
  "bulkDeleteBody",
  "bulkMoveBody",
] as const;

const COUNTED_KEYS = [
  "bulkSelected",
  "bulkSelectedCapped",
  "bulkAppliesTo",
  "tasksCompleted",
  "tasksDeleted",
  "bulkDeleteTitle",
  "bulkDeleteConfirm",
  "bulkMoveTitle",
  "bulkMoveConfirm",
  "bulkDeleteFailed",
  "bulkUpdateFailed",
] as const;

// One of each i18next plural category that any of the ten locales can ask for.
const COUNTS = [1, 2, 5, 21, 100];

describe("bulk selection copy", () => {
  it("resolves every plain key in all ten locales", () => {
    for (const locale of SUPPORTED_LOCALES) {
      const t = i18n.getFixedT(locale, "app");
      for (const key of PLAIN_KEYS) {
        const value = t(key);
        expect(value, `${locale}.app.${key}`).toBeTruthy();
        // A missing key resolves to the key itself, which is the failure this
        // whole test exists to catch.
        expect(value, `${locale}.app.${key} fell through to the key`).not.toBe(key);
      }
    }
  });

  it("resolves every counted key for every plural category", () => {
    for (const locale of SUPPORTED_LOCALES) {
      const t = i18n.getFixedT(locale, "app");
      for (const key of COUNTED_KEYS) {
        for (const count of COUNTS) {
          const value = t(key, { count, total: count + 1 });
          expect(value, `${locale}.app.${key} @ ${count}`).toBeTruthy();
          expect(
            value,
            `${locale}.app.${key} @ ${count} fell through to the key`,
          ).not.toBe(key);
          expect(
            value,
            `${locale}.app.${key} @ ${count} left {{count}} uninterpolated`,
          ).not.toContain("{{");
        }
      }
    }
  });

  it("says 'this task' rather than '1 task' where English has a singular", () => {
    const t = i18n.getFixedT("en", "app");
    expect(t("bulkDeleteTitle", { count: 1 })).toBe("Delete this task?");
    expect(t("bulkDeleteTitle", { count: 3 })).toBe("Delete 3 tasks?");
    expect(t("bulkDeleteConfirm", { count: 1 })).toBe("Delete task");
    expect(t("bulkDeleteConfirm", { count: 3 })).toBe("Delete 3 tasks");
    expect(t("tasksCompleted", { count: 1 })).toBe("Task completed");
    expect(t("tasksDeleted", { count: 1 })).toBe("Task deleted");
    // The failure toasts used to be flat keys, so one failed row read
    // "1 tasks couldn't be deleted" while Android's <plurals> said it properly.
    expect(t("bulkDeleteFailed", { count: 1 })).toBe("A task couldn't be deleted");
    expect(t("bulkDeleteFailed", { count: 3 })).toBe("3 tasks couldn't be deleted");
    expect(t("bulkUpdateFailed", { count: 1 })).toBe("A task couldn't be updated");
    expect(t("bulkUpdateFailed", { count: 3 })).toBe("3 tasks couldn't be updated");
  });

  it("states both halves of the recurring-skipped line", () => {
    const t = i18n.getFixedT("en", "app");
    expect(t("bulkAppliesTo", { count: 2, total: 5 })).toBe(
      "Applies to 2 of 5 — repeating tasks are skipped.",
    );
  });
});
