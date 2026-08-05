// @vitest-environment jsdom
import { beforeEach, describe, expect, it } from "vitest";

import {
  dismissRepeatSuggestion,
  isRepeatSuggestionDismissed,
} from "@/lib/repeatSuggestionDismissal";

/**
 * The behaviour is a one-liner ("don't nag about a title I dismissed"), so what
 * these cover is the storage contract underneath it: a stolen browser profile
 * must not yield a single task title, and the plaintext an older build wrote
 * must be gone after one load.
 */

// The key is asserted literally: a rename here would silently strand the old
// plaintext document under the previous key.
const KEY = "tday.repeatSuggestion.dismissed";
const TITLE = "call the clinic about the results";
const OTHER_TITLE = "book flights to lisbon";

function stored(): string {
  return localStorage.getItem(KEY) ?? "";
}

describe("repeat suggestion dismissal", () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it("remembers a dismissal without writing the title", () => {
    expect(isRepeatSuggestionDismissed(TITLE)).toBe(false);

    dismissRepeatSuggestion(TITLE);

    expect(isRepeatSuggestionDismissed(TITLE)).toBe(true);
    expect(isRepeatSuggestionDismissed(OTHER_TITLE)).toBe(false);
    expect(stored()).not.toContain(TITLE);
    expect(stored()).not.toContain("clinic");
  });

  it("mints a distinct salt per workspace, so digests are not portable", () => {
    dismissRepeatSuggestion(TITLE);
    const first = JSON.parse(stored()) as { salt: string; digests: string[] };

    localStorage.clear();
    dismissRepeatSuggestion(TITLE);
    const second = JSON.parse(stored()) as { salt: string; digests: string[] };

    expect(first.salt).not.toBe(second.salt);
    expect(first.digests[0]).not.toBe(second.digests[0]);
    // A digest, not an encoding of the title.
    expect(first.digests[0]).toMatch(/^[0-9a-f]{32}$/);
  });

  it("migrates a legacy plaintext list on first read and leaves no plaintext", () => {
    localStorage.setItem(KEY, JSON.stringify([TITLE, OTHER_TITLE]));

    // A plain read is enough — a user who never dismisses again must still not
    // be left with their old titles on disk.
    expect(isRepeatSuggestionDismissed(TITLE)).toBe(true);

    expect(stored()).not.toContain(TITLE);
    expect(stored()).not.toContain(OTHER_TITLE);
    expect(stored()).not.toContain("lisbon");
    expect(JSON.parse(stored())).toMatchObject({ version: 2 });
    // Dismissal state survived the conversion.
    expect(isRepeatSuggestionDismissed(OTHER_TITLE)).toBe(true);
    expect(isRepeatSuggestionDismissed("something never dismissed")).toBe(false);
  });

  it("caps the list and keeps the most recent dismissals", () => {
    for (let index = 0; index < 205; index += 1) {
      dismissRepeatSuggestion(`task ${index}`);
    }

    const doc = JSON.parse(stored()) as { digests: string[] };
    expect(doc.digests).toHaveLength(200);
    expect(isRepeatSuggestionDismissed("task 204")).toBe(true);
    expect(isRepeatSuggestionDismissed("task 0")).toBe(false);
  });

  it("re-dismissing a title keeps one entry and refreshes its recency", () => {
    dismissRepeatSuggestion(TITLE);
    const titleDigest = (JSON.parse(stored()) as { digests: string[] }).digests[0];
    dismissRepeatSuggestion(OTHER_TITLE);
    dismissRepeatSuggestion(TITLE);

    const doc = JSON.parse(stored()) as { digests: string[] };
    expect(doc.digests).toHaveLength(2);
    // Re-dismissing moves it to the newest slot, which is what the cap keeps.
    expect(doc.digests.at(-1)).toBe(titleDigest);
    expect(isRepeatSuggestionDismissed(TITLE)).toBe(true);
    expect(isRepeatSuggestionDismissed(OTHER_TITLE)).toBe(true);
  });

  it("discards a corrupt or future-version document rather than trusting it", () => {
    localStorage.setItem(KEY, `["${TITLE}"`);
    expect(isRepeatSuggestionDismissed(TITLE)).toBe(false);
    // A truncated legacy array is still readable titles — it must not survive.
    expect(stored()).toBe("");

    localStorage.setItem(KEY, JSON.stringify({ version: 99, salt: "ff", digests: [TITLE] }));
    expect(isRepeatSuggestionDismissed(TITLE)).toBe(false);
    expect(stored()).toBe("");
  });

  it("recovers from a well-shaped document whose salt is not hex", () => {
    // Right version, right salt length, unusable bytes. Without a hex check the
    // HMAC throws on every call, so the document is neither readable nor
    // replaceable and dismissal stays dead for the life of the profile.
    localStorage.setItem(KEY, JSON.stringify({ version: 2, salt: "z".repeat(32), digests: [] }));

    expect(isRepeatSuggestionDismissed(TITLE)).toBe(false);
    expect(stored()).toBe("");

    dismissRepeatSuggestion(TITLE);
    expect(isRepeatSuggestionDismissed(TITLE)).toBe(true);
  });

  it("ignores empty titles", () => {
    dismissRepeatSuggestion("");
    expect(isRepeatSuggestionDismissed("")).toBe(false);
    expect(stored()).toBe("");
  });
});
