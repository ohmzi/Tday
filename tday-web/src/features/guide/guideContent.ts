// Typed view over the committed, generated guide structure. Strings are NOT here
// — they resolve from the i18next `guide` namespace at runtime, so the guide stays
// fully offline (bundled JS) and localized. Regenerate the JSON with
// `./gradlew :shared:exportGuideContent`.
import structure from "@/generated/guide-structure.json";

export type GuideBlockType = "PARAGRAPH" | "STEPS" | "TIP" | "KBD" | "EXAMPLE";
export type GuideBadge = "HIDDEN_GEM" | "PRO_TIP";

export interface GuideBlockDef {
  type: GuideBlockType;
  keys: string[];
}

export interface GuideDeepLinkDef {
  web: string | null;
  android: string | null;
  ios: string | null;
}

export interface GuideTopicDef {
  id: string;
  section: string;
  icon: string;
  platforms: string[];
  titleKey: string;
  summaryKey: string;
  keywordsKey: string;
  body: GuideBlockDef[];
  badge: GuideBadge | null;
  deepLink: GuideDeepLinkDef | null;
  helpAnchors: string[];
  serverOnly: boolean;
  sinceVersion: string | null;
}

export interface GuideSectionDef {
  id: string;
  titleKey: string;
  order: number;
}

export interface GuideStructure {
  version: number;
  currentVersion: string;
  sections: GuideSectionDef[];
  topics: GuideTopicDef[];
}

const guide = structure as GuideStructure;

export const GUIDE_CURRENT_VERSION = guide.currentVersion;
export const GUIDE_SECTIONS = [...guide.sections].sort((a, b) => a.order - b.order);
export const GUIDE_TOPICS = guide.topics;

/** Topics in [sectionId], preserving catalog order. */
export function topicsInSection(sectionId: string): GuideTopicDef[] {
  return GUIDE_TOPICS.filter((t) => t.section === sectionId);
}

/** Topics introduced in the running app version — the "What's New" group. */
export function whatsNewTopics(): GuideTopicDef[] {
  return GUIDE_TOPICS.filter((t) => t.sinceVersion === GUIDE_CURRENT_VERSION);
}
