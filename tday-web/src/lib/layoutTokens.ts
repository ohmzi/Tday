import { LAYOUT_TOKENS } from "@/generated/layout-tokens";

/**
 * The web's reading of the shared layout vocabulary: where a titled page's content
 * starts under its hero title, and how tall the slot an empty scene sits in is.
 *
 * `src/generated/layout-tokens.ts` is written by `:shared:exportLayoutTokens` from
 * `LayoutTokens.kt`; Android reads the Kotlin object directly and iOS has its own
 * generated copy. All three spend the same ratios, so the first row — and the scene
 * that stands in for it — lands at the same height on every client.
 *
 * The rule is the Kotlin `LayoutTokens.contentGap`, ported: measure where the title
 * block ends, then spend exactly what reaches the shared line, held between a floor
 * (the fade band plus 6, so the first row rests below the band that dissolves rows
 * under the pinned bar) and a cap (so a tall window does not strand the title).
 */

/** The gap, in px, that reaches the content line from [restTop]. */
export function contentGapPx(viewportHeight: number, restTop: number): number {
  const { contentStartRatio, contentGapMin, contentGapMax } = LAYOUT_TOKENS;
  const wanted = contentStartRatio * viewportHeight - restTop;
  return Math.min(contentGapMax, Math.max(contentGapMin, wanted));
}

const trim = (value: number) => `${+value.toFixed(2)}`;

/**
 * The inline style that applies the gap — in `svh` rather than a measured px, because
 * the small viewport does not change as a mobile browser's toolbar slides away, where
 * `innerHeight` does and would make the first row jump on the first scroll. The plain
 * px height first is the answer for an engine without `svh`: the floor.
 *
 * @param restTop where the title block ends, in px from the top of the viewport, with
 *   the page scrolled to its top.
 */
export function contentGapStyle(restTop: number): string {
  const { contentStartRatio, contentGapMin, contentGapMax } = LAYOUT_TOKENS;
  return (
    `height:${contentGapMin}px;` +
    `height:clamp(${contentGapMin}px,calc(${trim(contentStartRatio * 100)}svh - ${trim(restTop)}px),${contentGapMax}px);`
  );
}

/** The empty-scene slot's minimum height. Same ratio and same unit as the gap above. */
export const EMPTY_SLOT_MIN_HEIGHT = `${trim(LAYOUT_TOKENS.emptySlotRatio * 100)}svh`;
