import { describe, expect, it } from "vitest";
import { GUIDE_TOPICS } from "@/features/guide/guideContent";
import { buildDoc, rank } from "@/features/guide/guideSearch";
import en from "../../messages/en.json";
import fixtures from "../fixtures/guide-search-vectors.json";

// Resolve a full "guide.topics.x.title" key against the English message tree.
function resolve(key: string): string {
  let node: unknown = en;
  for (const segment of key.split(".")) {
    if (node && typeof node === "object") {
      node = (node as Record<string, unknown>)[segment];
    } else {
      node = undefined;
    }
  }
  return typeof node === "string" ? node : key;
}

// Build docs exactly as the exporter (and GuideScreen) do, so ranking must match
// the fixtures produced by the shared Kotlin GuideSearch.
const docs = GUIDE_TOPICS.map((topic) =>
  buildDoc(
    topic.id,
    resolve(topic.titleKey),
    resolve(topic.keywordsKey),
    [resolve(topic.summaryKey), ...topic.body.flatMap((b) => b.keys.map(resolve))].join(" "),
  ),
);

describe("guide search parity with the shared Kotlin engine", () => {
  const vectors = fixtures.vectors as Record<string, string[]>;

  it("has fixtures to check", () => {
    expect(Object.keys(vectors).length).toBeGreaterThan(0);
  });

  for (const [query, expected] of Object.entries(vectors)) {
    it(`ranks "${query}" identically to the Kotlin engine`, () => {
      expect(rank(query, docs)).toEqual(expected);
    });
  }
});
