import {
  LIST_ICON_EMITTABLE_KEYS,
  LIST_ICON_KEY_BY_KEYWORD,
} from "@/generated/list-icon-table";

/**
 * Guesses a list's glyph from its name, for lists whose owner never picked one.
 *
 * The WORDS are generated — `src/generated/list-icon-table.ts`, written from
 * `shared/.../listicon/ListIconInference.kt` by `:shared:exportListIconTable`, with
 * `verifyListIconTable` as the drift gate. This file is the RULE, hand-written, the
 * way `floaterResting.ts` is the hand-written twin of `FloaterResting.tierFor`: two
 * hundred words that grow every time someone names a list something new belong in one
 * place, fifteen lines of string handling want the platform's own. The cut is argued
 * once, in docs/ICONS.md, for all three clients.
 *
 * ENGLISH ONLY, and deliberately. The app ships ten locales and the i18n parity gate
 * compares KEY SETS, so it would green-light nine locales of untranslated English
 * keywords without a murmur — the limitation is stated here because no gate can state
 * it. The adoption path if ten locales are ever wanted is a `listIcons` namespace in
 * `messages/<locale>.json` exported back into `shared`, the way
 * `SummaryStringBundlesGenerated.kt` already goes the other way.
 *
 * WHOLE WORDS, NEVER SUBSTRINGS. A substring matcher reads "Carpentry" as `car`,
 * "Firewood" as `fire` and "Scarlett" as `car`, and a confidently wrong glyph is worse
 * than the generic one. So a title is cut into words and each whole word is looked up.
 *
 * UNSURE RETURNS null, AND null IS NOT `inbox`. `normalizeListIconKey` answers `inbox`
 * for null, blank and unknown alike and never says which it was, so a matcher that
 * answered "inbox" for "I don't know" would destroy the one distinction the whole
 * feature rests on: an icon the user chose always wins, including when they chose the
 * default. Two DIFFERENT keys matching is also unsure — "Work Travel" gets nothing
 * rather than a coin toss decided by reading order.
 */

/** Every key the table can emit, for the parity gate and for callers that validate. */
export const inferableListIconKeys: ReadonlySet<string> = new Set(
  LIST_ICON_EMITTABLE_KEYS,
);

/**
 * The title cut into lowercase alphanumeric runs.
 *
 * `toLowerCase()` rather than `toLocaleLowerCase()`: this is a lookup against an
 * English table, and the locale-sensitive form turns "I" into a dotless ı under a
 * Turkish locale, which would make one list infer differently in two browsers. The
 * Kotlin twin passes over `lowercase()` for exactly the same reason.
 *
 * The split is on anything that is not an ASCII letter or digit. Wider than it looks:
 * a title is cut at spaces, punctuation, emoji and accented characters alike, so
 * "Groceries 🛒" and "Work/Home" both still yield the words they obviously contain.
 * An accented word is simply cut where the accent is and matches nothing, which is
 * the correct answer from an English table.
 */
function wordsOf(title: string): string[] {
  return title.toLowerCase().split(/[^a-z0-9]+/).filter(Boolean);
}

/**
 * The icon key [title] evidences, or null when it evidences nothing or evidences two
 * different glyphs. Never returns the default key.
 */
export function inferListIconKey(title: string | null | undefined): string | null {
  if (!title) return null;

  let found: string | null = null;
  for (const word of wordsOf(title)) {
    const iconKey = LIST_ICON_KEY_BY_KEYWORD[word];
    if (!iconKey) continue;
    if (found === null) {
      found = iconKey;
    } else if (found !== iconKey) {
      // Two subjects, one glyph slot. "Work Travel" is a briefcase and a plane with
      // equal warrant, and taking the leftmost would only hide the coin toss behind
      // reading order.
      return null;
    }
  }
  return found;
}
