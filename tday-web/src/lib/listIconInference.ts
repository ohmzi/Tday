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
 * The split is on anything that is not a LETTER or a NUMBER in the Unicode sense, and
 * the `u` flag is what makes `\p{L}`/`\p{N}` mean that rather than two literal `p`s.
 * This is not decoration: the Kotlin twin splits on `Char.isLetterOrDigit` and the Swift
 * one on `isLetter || isNumber`, both Unicode, and an ASCII class here would not be a
 * narrower version of the same rule — it would be a DIFFERENT rule, because it also cuts
 * *inside* a run the other two keep whole. A list named "仕事Work" is one word to Kotlin
 * and Swift, which recognise none of it and correctly say nothing; under `[^a-z0-9]+` it
 * became the single word "work" and the browser drew a confident briefcase the phones
 * did not. The same for "Gymé" → `gym` → a dumbbell. That divergence lands hardest on
 * ja and zh, where an unspaced CJK+English list name is the ordinary shape, and it is
 * one-directional: only web could be confidently wrong. The parity gate now compares the
 * three case tables, and `"仕事Work"` is in all three so this cannot come back.
 *
 * What the split still does is everything it did before: a title is cut at spaces,
 * punctuation and emoji, so "Groceries 🛒" and "Work/Home" yield the words they
 * obviously contain, and "Работа Work" still finds `work` because a space is a boundary
 * in any script.
 */
function wordsOf(title: string): string[] {
  return title
    .toLowerCase()
    .split(/[^\p{L}\p{N}]+/u)
    .filter(Boolean);
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
