// TypeScript port of the shared Kotlin GuideSearch (shared/.../guide/GuideSearch.kt).
// It MUST stay behaviourally identical to the Kotlin engine — the generated
// fixtures in tests/fixtures/guide-search-vectors.json are produced by the Kotlin
// side and replayed here in tests/unit/guide-search.test.ts to catch any drift.

export interface GuideSearchDoc {
  topicId: string;
  titleNorm: string;
  keywordsNorm: string;
  bodyNorm: string;
}

const TITLE_WEIGHT = 3;
const KEYWORD_WEIGHT = 2;
const BODY_WEIGHT = 1;

// Same Latin diacritic groups as the Kotlin DIACRITIC_FOLD.
const FOLD_GROUPS: Array<[string, string]> = [
  ["àáâãäåāăą", "a"],
  ["çćčĉ", "c"],
  ["ďđ", "d"],
  ["èéêëēĕėęě", "e"],
  ["ìíîïĩīĭįı", "i"],
  ["ñńņň", "n"],
  ["òóôõöøōŏő", "o"],
  ["ùúûüũūŭůűų", "u"],
  ["ýÿŷ", "y"],
  ["śšşŝ", "s"],
  ["žźż", "z"],
  ["ğĝ", "g"],
  ["ß", "s"],
];

const DIACRITIC_FOLD: Map<string, string> = (() => {
  const map = new Map<string, string>();
  for (const [chars, base] of FOLD_GROUPS) {
    for (const ch of chars) map.set(ch, base);
  }
  return map;
})();

/** Lowercase, fold Latin diacritics, collapse whitespace runs to one space. */
export function normalize(input: string): string {
  const lowered = input.toLowerCase();
  let out = "";
  let pendingSpace = false;
  for (const ch of lowered) {
    if (/\s/.test(ch)) {
      pendingSpace = out.length > 0;
      continue;
    }
    if (pendingSpace) {
      out += " ";
      pendingSpace = false;
    }
    out += DIACRITIC_FOLD.get(ch) ?? ch;
  }
  return out;
}

export function buildDoc(
  topicId: string,
  title: string,
  keywords: string,
  body: string,
): GuideSearchDoc {
  return {
    topicId,
    titleNorm: normalize(title),
    keywordsNorm: normalize(keywords),
    bodyNorm: normalize(body),
  };
}

/**
 * Rank docs against a query, best-first. Every query token must appear somewhere
 * in a doc (token-AND); score sums per-token title(3)/keyword(2)/body(1) hits;
 * ties fall back to input order. Empty query returns []. Returns topic ids.
 */
export function rank(query: string, docs: GuideSearchDoc[]): string[] {
  const tokens = normalize(query)
    .split(" ")
    .filter((t) => t.length > 0);
  if (tokens.length === 0) return [];

  const scored: Array<{ topicId: string; score: number; index: number }> = [];
  docs.forEach((doc, index) => {
    let total = 0;
    let allMatched = true;
    for (const token of tokens) {
      let tokenScore = 0;
      if (doc.titleNorm.includes(token)) tokenScore += TITLE_WEIGHT;
      if (doc.keywordsNorm.includes(token)) tokenScore += KEYWORD_WEIGHT;
      if (doc.bodyNorm.includes(token)) tokenScore += BODY_WEIGHT;
      if (tokenScore === 0) {
        allMatched = false;
        break;
      }
      total += tokenScore;
    }
    if (allMatched) scored.push({ topicId: doc.topicId, score: total, index });
  });

  scored.sort((a, b) => b.score - a.score || a.index - b.index);
  return scored.map((s) => s.topicId);
}
