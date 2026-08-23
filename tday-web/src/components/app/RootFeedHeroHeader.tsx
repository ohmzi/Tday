import { Ellipsis, Leaf, ListPlus, Moon, Search, Sun, X } from "lucide-react";
import type { ReactNode } from "react";
import { useCallback, useEffect, useLayoutEffect, useRef } from "react";
import { cn } from "@/lib/utils";
import { nativeAppScrollAttribute } from "./nativeAppLayout";
import { clamp01, stagger } from "./nativeHeaderEasing";

/**
 * Geometry for the root-feed hero header shared by the Scheduled and Floater
 * home screens.
 *
 * The header is pinned above the feed: the toolbar strip ([barHeight]) stays put
 * while the feed scrolls out of sight behind it. As the feed scrolls the mark
 * shrinks into the toolbar glyph, the title slides up from its centred hero
 * position to sit beside it, and the search field folds down into a round button
 * to make room for the title.
 *
 * These are the iOS numbers (`RootFeedHeroHeaderMetrics` in
 * `ios-swiftUI/Tday/Core/UI/RootFeedHeroHeader.swift`, mirrored in
 * `core/ui/RootFeedHeroHeader.kt` on Android), solved by search rather than by
 * eye: across every supported width crossed with the longest localised titles,
 * the title never crosses the mark, the search field or the buttons, and the
 * rising feed never clips it. Keep the three platforms in step.
 *
 * The one deliberate difference: `horizontalPadding` is 0 here because the app
 * shell's scroll container already provides the page gutter.
 */
export const rootFeedHeroHeaderMetrics = {
  horizontalPadding: 0,
  topInset: 18,
  barButtonSize: 56,
  barButtonSpacing: 8,

  /** Always-visible toolbar strip. The feed scrolls out of sight behind it. */
  barHeight: 18 + 56,
  /** Extra height the hero title block claims while the feed sits at the top. */
  heroTitleHeight: 78,
  /** Scroll distance over which the hero folds into the toolbar. */
  collapseDistance: 78,
  /** Gradient below the strip that dissolves rows as they pass under it. */
  contentFadeHeight: 24,

  compactRowCenterY: 18 + 28,

  heroMarkBox: 72,
  compactMarkBox: 30,
  markLeading: 2,
  heroMarkCenterY: 18 + 28 + 10,

  heroTitleFontSize: 40,
  heroTitleLineHeight: 48,
  maxCompactTitleScale: 0.8,
  minTitleScale: 0.5,
  heroTitleCenterY: 74 + 39,
  titleGap: 8,

  /**
   * The field's trailing edge is fixed just inside the two round buttons, so
   * only its leading edge travels — it folds down into a button in place rather
   * than sliding across the toolbar.
   */
  searchTrailingInset: 0 + 56 * 2 + 8 * 2,
  heroSearchLeading: 2 + 72 + 8,
  searchIconSlot: 30,
  searchLeadingPadding: 13,
  searchLabelFadeStart: 100,
  searchLabelFadeEnd: 124,
  /** Right-hand breathing room so an ellipsis is not clipped by the capsule cap. */
  searchLabelTrailingPadding: 14,
  /** Dead band around the long/short swap so it cannot flicker. */
  searchLabelSwapHysteresis: 6,

  // Staggered curve endpoints, as a fraction of collapseDistance. Flatter easing
  // widens the collision-free set, which is why each leg runs this long: cubic
  // admitted 98 endpoint combinations, quintic 187, septic 255.
  markCollapseEnd: 0.65,
  searchCollapseEnd: 0.45,
  titleTravelEnd: 0.5,
} as const;

const lerp = (from: number, to: number, fraction: number) => from + (to - from) * fraction;

/**
 * Fit-to-space caps for both ends of the title morph. A long localised title
 * would otherwise sit under the mark while centred, and under the search button
 * once docked beside it.
 */
function titleScales(titleWidth: number, availableWidth: number) {
  const m = rootFeedHeroHeaderMetrics;
  if (titleWidth <= 0) return { hero: 1, compact: m.maxCompactTitleScale };

  const heroRoom = availableWidth - m.heroSearchLeading * 2;
  const hero = Math.max(m.minTitleScale, Math.min(1, heroRoom / titleWidth));

  const compactRoom =
    availableWidth -
    m.searchTrailingInset -
    m.barButtonSize -
    (m.markLeading + m.compactMarkBox + m.titleGap) -
    m.titleGap;
  const compact = Math.max(
    m.minTitleScale,
    Math.min(m.maxCompactTitleScale * hero, compactRoom / titleWidth),
  );

  return { hero, compact: Math.min(compact, hero) };
}

export type RootFeedHeroMark = "timeOfDay" | "floaterLeaf";

export const rootFeedHeaderButtonClass =
  "flex h-14 w-14 items-center justify-center rounded-full border border-white/70 bg-card/90 text-foreground shadow-[0_12px_28px_-22px_hsl(var(--shadow)/0.55)] transition-all duration-200 hover:-translate-y-0.5 hover:bg-card active:translate-y-0 dark:border-white/10";

const floaterAccent = "#4D8F83";

type Props = {
  title: string;
  mark: RootFeedHeroMark;
  searchOpen: boolean;
  searchQuery: string;
  searchPlaceholder: string;
  /** Shown in place of searchPlaceholder when the folded capsule is too narrow. */
  searchPlaceholderShort: string;
  searchAriaLabel: string;
  createListAriaLabel: string;
  settingsAriaLabel: string;
  onSearchQueryChange: (value: string) => void;
  onSearchOpenChange: (open: boolean) => void;
  onCreateList: () => void;
  onOpenSettings: () => void;
  /** Rendered directly under the capsule while the field is open. */
  results?: ReactNode;
};

export default function RootFeedHeroHeader({
  title,
  mark,
  searchOpen,
  searchQuery,
  searchPlaceholder,
  searchPlaceholderShort,
  searchAriaLabel,
  createListAriaLabel,
  settingsAriaLabel,
  onSearchQueryChange,
  onSearchOpenChange,
  onCreateList,
  onOpenSettings,
  results,
}: Props) {
  const m = rootFeedHeroHeaderMetrics;
  const headerRef = useRef<HTMLElement | null>(null);
  const markRef = useRef<HTMLDivElement | null>(null);
  const titleRef = useRef<HTMLButtonElement | null>(null);
  const titleTextRef = useRef<HTMLSpanElement | null>(null);
  const capsuleRef = useRef<HTMLDivElement | null>(null);
  const labelRef = useRef<HTMLSpanElement | null>(null);
  const longLabelRef = useRef<HTMLSpanElement | null>(null);
  const shortLabelRef = useRef<HTMLSpanElement | null>(null);
  const measureRef = useRef<HTMLSpanElement | null>(null);
  const resultsRef = useRef<HTMLDivElement | null>(null);
  const longVisibleRef = useRef(true);
  const scrollerRef = useRef<HTMLElement | null>(null);
  const searchOpenRef = useRef(searchOpen);
  const hasQueryRef = useRef(false);

  const hasQuery = searchQuery.trim().length > 0;
  searchOpenRef.current = searchOpen;
  hasQueryRef.current = hasQuery;

  const isDaytime = (() => {
    const hour = new Date().getHours();
    return hour >= 6 && hour < 18;
  })();

  const scrollToTop = useCallback(() => {
    scrollerRef.current?.scrollTo({ top: 0, behavior: "smooth" });
  }, []);

  // The morph is applied by writing styles straight onto the nodes inside a
  // rAF, never through React state. A scroll frame must not re-render the feed
  // behind the header — that is the whole reason this is not a useState.
  useLayoutEffect(() => {
    const header = headerRef.current;
    if (!header) return;

    const scroller =
      (header.closest(`[${nativeAppScrollAttribute}]`) as HTMLElement | null) ??
      (document.querySelector(`[${nativeAppScrollAttribute}]`) as HTMLElement | null);
    scrollerRef.current = scroller;
    if (!scroller) return;

    let frame = 0;

    const apply = () => {
      frame = 0;
      const markEl = markRef.current;
      const titleEl = titleRef.current;
      const titleTextEl = titleTextRef.current;
      const capsuleEl = capsuleRef.current;
      if (!markEl || !titleEl || !titleTextEl || !capsuleEl) return;

      const width = header.clientWidth;
      if (width <= 0) return;
      const progress = clamp01(scroller.scrollTop / m.collapseDistance);

      const markCollapse = stagger(progress, m.markCollapseEnd);
      const markBox = lerp(m.heroMarkBox, m.compactMarkBox, markCollapse);
      const markCenterY = lerp(m.heroMarkCenterY, m.compactRowCenterY, markCollapse);
      markEl.style.width = `${markBox}px`;
      markEl.style.height = `${markBox}px`;
      markEl.style.transform = `translate(${m.markLeading}px, ${markCenterY - markBox / 2}px)`;

      const travel = stagger(progress, m.titleTravelEnd);
      // The title's vertical travel is deliberately NOT staggered: the feed
      // rises 78px while the title only rises 67px, so any delay there lets the
      // first card cut into the title's descenders.
      const drop = stagger(progress, 1);
      const titleWidth = titleTextEl.offsetWidth;
      const { hero, compact } = titleScales(titleWidth, width);
      const scale = lerp(hero, compact, travel);
      const compactCenterX =
        m.markLeading + m.compactMarkBox + m.titleGap + (titleWidth * compact) / 2;
      const centerX = lerp(width / 2, compactCenterX, travel);
      const centerY = lerp(m.heroTitleCenterY, m.compactRowCenterY, drop);
      // Positioned by the SCALED extent, not the raw offsetWidth: the node is
      // origin-top-left, so scaling pulls its box toward that corner. Using the
      // unscaled half-width lands the left edge titleWidth*(1-scale)/2 too far
      // left, which at the compact end is straight on top of the mark.
      titleEl.style.transform =
        `translate(${centerX - (titleWidth * scale) / 2}px, ${
          centerY - (m.heroTitleLineHeight * scale) / 2
        }px) scale(${scale})`;
      // An open field does not by itself hide the title — down in its hero
      // position it sits clear of the toolbar row, so there is nothing to hide
      // it for. It fades as it docks (where it WOULD collide with the expanded
      // field), and goes entirely once a query starts and the results take over.
      titleEl.style.opacity = String(
        searchOpenRef.current ? (hasQueryRef.current ? 0 : 1 - drop) : 1,
      );

      const searchCollapse = stagger(progress, m.searchCollapseEnd);
      const trailingX = width - m.searchTrailingInset;
      const heroWidth = Math.max(m.barButtonSize, trailingX - m.heroSearchLeading);
      const restingWidth = lerp(heroWidth, m.barButtonSize, searchCollapse);
      const open = searchOpenRef.current;
      const fieldWidth = open ? Math.max(m.barButtonSize, width) : restingWidth;
      const leadingX = open ? 0 : trailingX - restingWidth;
      capsuleEl.style.width = `${fieldWidth}px`;
      capsuleEl.style.transform = `translateX(${leadingX}px)`;

      const labelEl = labelRef.current;
      const longEl = longLabelRef.current;
      const shortEl = shortLabelRef.current;
      const measureEl = measureRef.current;
      if (labelEl && longEl && shortEl && measureEl) {
        labelEl.style.opacity = String(
          clamp01(
            (restingWidth - m.searchLabelFadeStart) /
              (m.searchLabelFadeEnd - m.searchLabelFadeStart),
          ),
        );

        // Fall back to the short word rather than letting CSS chop the long one
        // mid-word. Measured off a hidden non-shrinking copy so the decision
        // does not depend on the width it is deciding.
        const room =
          restingWidth - m.searchLeadingPadding - m.searchIconSlot - m.searchLabelTrailingPadding;
        const longWidth = measureEl.offsetWidth;
        const wasLong = longVisibleRef.current;
        // Hysteresis, so the swap does not flicker on the boundary pixel.
        const showLong = wasLong
          ? room >= longWidth - m.searchLabelSwapHysteresis
          : room >= longWidth + m.searchLabelSwapHysteresis;
        if (showLong !== wasLong) {
          longVisibleRef.current = showLong;
          longEl.style.display = showLong ? "" : "none";
          shortEl.style.display = showLong ? "none" : "";
        }
      }
    };

    const schedule = () => {
      if (frame) return;
      frame = requestAnimationFrame(apply);
    };

    apply();
    scroller.addEventListener("scroll", schedule, { passive: true });
    window.addEventListener("resize", schedule);
    return () => {
      if (frame) cancelAnimationFrame(frame);
      scroller.removeEventListener("scroll", schedule);
      window.removeEventListener("resize", schedule);
    };
  }, [m]);

  // Tapping anywhere outside the field closes it. Guarded on the capsule and
  // the results panel rather than a measured rect: on iOS a rect-based guard
  // closed the field on the very tap that opened it, because the rect had not
  // been reported for the new state yet.
  useEffect(() => {
    if (!searchOpen) return;
    const onPointerDown = (event: PointerEvent) => {
      const target = event.target as Node | null;
      if (!target) return;
      if (capsuleRef.current?.contains(target)) return;
      if (resultsRef.current?.contains(target)) return;
      onSearchOpenChange(false);
    };
    document.addEventListener("pointerdown", onPointerDown, true);
    return () => document.removeEventListener("pointerdown", onPointerDown, true);
  }, [searchOpen, onSearchOpenChange]);

  // Re-run the layout when the field opens or closes, and when the title text
  // changes width (locale switch).
  useEffect(() => {
    const event = new Event("scroll");
    scrollerRef.current?.dispatchEvent(event);
  }, [searchOpen, title]);

  const MarkIcon = mark === "floaterLeaf" ? Leaf : isDaytime ? Sun : Moon;
  const markColor =
    mark === "floaterLeaf" ? floaterAccent : isDaytime ? "#F4C542" : "#A8B8E8";

  return (
    <>
      {searchOpen && hasQuery ? (
        // Blanks the feed out so only the results remain, and takes the tap that
        // dismisses the field. Sits under the header (z-30), so the toolbar and
        // the results panel stay above it.
        // Purely visual: the outside-tap listener above already closes on a tap
        // here, since this is neither the capsule nor the results panel.
        <div className="fixed inset-0 z-20 bg-background" aria-hidden />
      ) : null}

      <header
        ref={headerRef}
        // Must stay a DIRECT child of the page's full-height flex column: a
        // sticky element only sticks within its containing block, so wrapping
        // it with the spacer would unpin it as soon as that wrapper scrolled by.
        className="sticky top-4 z-30 shrink-0 sm:top-6"
        style={{ height: m.barHeight }}
      >
        {/* Opaque toolbar strip, overscanning upward so the feed never shows
            through above it. */}
        <div
          aria-hidden
          className="absolute bg-background"
          style={{ left: "50%", transform: "translateX(-50%)", width: "100vw", top: -240, height: 240 + m.barHeight }}
        />
        {/* Rows dissolve into the strip instead of being cut by its edge. */}
        <div
          aria-hidden
          className="pointer-events-none absolute bg-gradient-to-b from-background to-transparent"
          style={{ left: "50%", transform: "translateX(-50%)", width: "100vw", top: m.barHeight, height: m.contentFadeHeight }}
        />

        {/* Deliberately not clickable: the header sits above the feed, so
            anything here that takes a pointer is a dead zone for scrolling. The
            title alone carries the scroll-to-top tap. */}
        <div
          ref={markRef}
          aria-hidden
          className={cn(
            "pointer-events-none absolute left-0 top-0 origin-top-left transition-opacity duration-200",
            searchOpen ? "opacity-0" : "opacity-100",
          )}
        >
          <MarkIcon className="h-full w-full" style={{ color: markColor, fill: markColor }} />
        </div>

        <button
          ref={titleRef}
          type="button"
          onClick={scrollToTop}
          className={cn(
            "absolute left-0 top-0 origin-top-left whitespace-nowrap transition-opacity duration-200",
            searchOpen ? "pointer-events-none" : "",
          )}
          // Hidden until the first frame positions it: with no transform yet it
          // would paint at the header's top-left corner and then jump to centre.
          style={{ height: m.heroTitleLineHeight, opacity: 0 }}
        >
          <span
            ref={titleTextRef}
            className="block font-black leading-none text-foreground"
            style={{ fontSize: m.heroTitleFontSize, lineHeight: `${m.heroTitleLineHeight}px` }}
          >
            {title}
          </span>
        </button>

        <div
          className={cn(
            "absolute right-0 flex items-center gap-2 transition-opacity duration-200",
            searchOpen ? "pointer-events-none opacity-0" : "opacity-100",
          )}
          style={{ top: m.topInset }}
        >
          <button
            type="button"
            className={rootFeedHeaderButtonClass}
            onClick={onCreateList}
            aria-label={createListAriaLabel}
          >
            <ListPlus className="h-6 w-6 stroke-[2.6]" />
          </button>
          <button
            type="button"
            className={rootFeedHeaderButtonClass}
            onClick={onOpenSettings}
            aria-label={settingsAriaLabel}
          >
            <Ellipsis className="h-6 w-6 stroke-[2.6]" />
          </button>
        </div>

        <div
          ref={capsuleRef}
          className="absolute left-0 z-[2] overflow-hidden rounded-full border border-white/70 bg-card/90 shadow-[0_12px_28px_-22px_hsl(var(--shadow)/0.55)] dark:border-white/10"
          style={{ top: m.topInset, height: m.barButtonSize }}
        >
          {searchOpen ? (
            <div className="flex h-full w-full items-center gap-2 px-3">
              <Search className="h-5 w-5 shrink-0 text-muted-foreground" />
              <input
                autoFocus
                type="search"
                value={searchQuery}
                onChange={(event) => onSearchQueryChange(event.target.value)}
                placeholder={searchPlaceholder}
                aria-label={searchAriaLabel}
                className="h-full min-w-0 flex-1 bg-transparent text-base font-extrabold outline-none md:text-sm"
              />
              <button
                type="button"
                onClick={() => onSearchOpenChange(false)}
                aria-label={searchAriaLabel}
                className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full text-muted-foreground"
              >
                <X className="h-5 w-5 stroke-[2.6]" />
              </button>
            </div>
          ) : (
            // The glyph stays pinned at searchLeadingPadding, which centres it
            // once the capsule is a round button, while the placeholder simply
            // runs off the end and is clipped.
            <button
              type="button"
              onClick={() => onSearchOpenChange(true)}
              aria-label={searchAriaLabel}
              className="flex h-full w-full items-center text-left"
              style={{ paddingLeft: m.searchLeadingPadding }}
            >
              <span
                className="flex shrink-0 items-center justify-center"
                style={{ width: m.searchIconSlot }}
              >
                <Search className="h-[22px] w-[22px] stroke-[2.6] text-foreground" />
              </span>
              <span
                ref={labelRef}
                className="ml-0.5 min-w-0 flex-1 text-base font-bold text-muted-foreground"
                style={{ opacity: 0, paddingRight: m.searchLabelTrailingPadding }}
              >
                <span ref={longLabelRef} className="block truncate">
                  {searchPlaceholder}
                </span>
                <span ref={shortLabelRef} className="block truncate" style={{ display: "none" }}>
                  {searchPlaceholderShort}
                </span>
              </span>
            </button>
          )}
        </div>

        {searchOpen && results ? (
          <div
            ref={resultsRef}
            className="absolute inset-x-0 z-[1]"
            style={{ top: m.topInset + m.barButtonSize + 8 }}
          >
            {results}
          </div>
        ) : null}
        {/* Never shown. Gives the rAF the long label's natural width without
            letting the capsule it is sizing influence the measurement. */}
        <span
          ref={measureRef}
          aria-hidden
          className="pointer-events-none invisible absolute left-0 top-0 whitespace-nowrap text-base font-bold"
        >
          {searchPlaceholder}
        </span>
      </header>

      {/* Reserves the hero block's space. The feed scrolls behind the strip,
          folding the header down into it. The negative margin cancels the page
          column's flex gap so the reserved height is exactly the hero block's,
          matching iOS and Android. */}
      <div
        aria-hidden
        className="-mt-4 shrink-0 sm:-mt-5"
        style={{ height: m.heroTitleHeight }}
      />
    </>
  );
}
