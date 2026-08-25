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
 * Two deliberate differences here. `horizontalPadding` is 0 because the app
 * shell's scroll container already provides the page gutter. And the title is
 * no longer scaled to fit: it holds the morph's own two sizes and is cut off
 * with an ellipsis at whatever width is free — see [titleRooms]. The travel
 * distances and the painted extents at both ends are the ones the search
 * solved, so that swap keeps every clearance it bought.
 */
export const rootFeedHeroHeaderMetrics = {
  horizontalPadding: 0,
  topInset: 8,
  barButtonSize: 56,
  barButtonSpacing: 8,

  /** Always-visible toolbar strip. The feed scrolls out of sight behind it. */
  barHeight: 8 + 56,
  /** Extra height the hero title block claims while the feed sits at the top. */
  heroTitleHeight: 78,
  /** Scroll distance over which the hero folds into the toolbar. */
  collapseDistance: 78,
  /** Gradient below the strip that dissolves rows as they pass under it. */
  contentFadeHeight: 24,

  compactRowCenterY: 8 + 28,

  heroMarkBox: 72,
  compactMarkBox: 30,
  markLeading: 2,
  heroMarkCenterY: 8 + 28 + 10,

  heroTitleFontSize: 40,
  heroTitleLineHeight: 48,
  /**
   * The docked end of the title's size morph — 40px folding down to 32px, the
   * same handoff a non-root page's title makes into its bar.
   *
   * A fixed step, not a fit-to-space cap. iOS and Android still carry a
   * `maxCompactTitleScale`/`minTitleScale` pair that shrinks a title until it
   * fits (and, below the 0.5 floor, lets it run under the search button anyway);
   * the web clips instead — see [titleRooms]. That is the one place the three
   * are deliberately out of step, until the native sides follow.
   */
  compactTitleScale: 0.8,
  heroTitleCenterY: 64 + 39,
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
 * How much width the title may paint into at each end of the morph. A title
 * wider than its room is cut off there with an ellipsis; it is never shrunk to
 * fit, so the name reads at the size the morph asked for however long it is.
 *
 * - `hero`: symmetric about the header's centre, stopping `titleGap` short of
 *   the hero mark. The mark is the only thing at that height — the search field
 *   sits a whole row above the centred title — so this is purely mark clearance.
 * - `compact`: what the toolbar row has left once the docked mark on the left
 *   and the folded search button plus the two round buttons on the right have
 *   taken theirs, less a `titleGap` at each end.
 */
function titleRooms(availableWidth: number) {
  const m = rootFeedHeroHeaderMetrics;
  return {
    hero: Math.max(0, availableWidth - m.heroSearchLeading * 2),
    compact: Math.max(
      0,
      availableWidth -
        m.searchTrailingInset -
        m.barButtonSize -
        (m.markLeading + m.compactMarkBox + m.titleGap) -
        m.titleGap,
    ),
  };
}

export type RootFeedHeroMark = "timeOfDay" | "floaterLeaf";

/**
 * The app's search field, in its open state. The root feeds fold theirs down
 * into a round button and so own the width themselves; anywhere else — the
 * guide — takes the chrome as-is at full width, so the two read as one control.
 */
export const tdaySearchCapsuleClass =
  "flex items-center gap-2 overflow-hidden rounded-full border border-white/70 bg-card/90 px-3 shadow-[0_12px_28px_-22px_hsl(var(--shadow)/0.55)] dark:border-white/10";

/** Its inner parts, so a second field cannot drift from the first. */
export const tdaySearchCapsuleIconClass = "h-5 w-5 shrink-0 text-muted-foreground";
export const tdaySearchCapsuleInputClass =
  "h-full min-w-0 flex-1 bg-transparent text-base font-extrabold text-foreground outline-none placeholder:text-muted-foreground/50 md:text-sm";
export const tdaySearchCapsuleClearClass =
  "flex h-8 w-8 shrink-0 items-center justify-center rounded-full text-muted-foreground transition-colors hover:bg-accent/15 hover:text-foreground";

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
  const titleMeasureRef = useRef<HTMLSpanElement | null>(null);
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
      const titleMeasureEl = titleMeasureRef.current;
      const capsuleEl = capsuleRef.current;
      if (!markEl || !titleEl || !titleTextEl || !capsuleEl) return;

      const width = header.clientWidth;
      if (width <= 0) return;
      // Read before anything below writes, so the frame does not force a second
      // layout. Taken off the hidden copy because the visible node's own width
      // is the answer being computed here, and fractional because an integer
      // `offsetWidth` rounds a title that fits down into one that ellipsizes.
      const naturalTitleWidth = titleMeasureEl
        ? titleMeasureEl.getBoundingClientRect().width
        : 0;
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
      // The size step is the morph and is unconditional; the rooms only decide
      // where the text is cut off.
      const scale = lerp(1, m.compactTitleScale, travel);
      const rooms = titleRooms(width);
      const heroPainted = Math.min(naturalTitleWidth, rooms.hero);
      const compactPainted = Math.min(naturalTitleWidth * m.compactTitleScale, rooms.compact);
      // Interpolated as a painted extent rather than as a width and a scale
      // separately: the product of two lerps is a curve, and this has to be the
      // straight line between the two ends the collision-free set was solved on.
      const painted = lerp(heroPainted, compactPainted, travel);
      // Bounded only when the text genuinely has to be cut. An exact-fit width
      // written back in px re-enters layout a hair narrow and ellipsizes a title
      // that fits; left as `auto` the node shrink-wraps the way it always did.
      titleTextEl.style.width =
        painted < naturalTitleWidth * scale - 0.5 ? `${painted / scale}px` : "";
      const compactCenterX =
        m.markLeading + m.compactMarkBox + m.titleGap + compactPainted / 2;
      const centerX = lerp(width / 2, compactCenterX, travel);
      const centerY = lerp(m.heroTitleCenterY, m.compactRowCenterY, drop);
      // Positioned by the PAINTED extent, not the node's own width: the node is
      // origin-top-left, so scaling pulls its box toward that corner. Using the
      // unscaled half-width lands the left edge painted*(1-scale)/2/scale too
      // far left, which at the compact end is straight on top of the mark.
      titleEl.style.transform =
        `translate(${centerX - painted / 2}px, ${
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
    // A webfont swapping in changes the title's natural width, and nothing else
    // here would hear about it — a font swap fires neither scroll nor resize.
    // Unmeasured it would leave the title mis-centred, or ellipsized when it
    // fits, until the next scroll.
    void document.fonts?.ready.then(schedule);
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
        //
        // Pinned at the safe-area inset, so the toolbar row lands exactly where
        // a non-root page's back button does — `topInset` + this has to equal
        // that bar's `0.5rem + env(safe-area-inset-top)`. The margin matches the
        // offset on purpose: pinning without it leaves the flow slot where it
        // was and the title overlaps the first row by the inset. Nothing here
        // read `env()` before, so the row was a fixed 50px that already sat
        // under a Dynamic Island.
        className="sticky top-[env(safe-area-inset-top)] z-30 mt-[env(safe-area-inset-top)] shrink-0 lg:top-0 lg:mt-0"
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
          {/* The clipper, and so the node the rAF sizes: `text-overflow` only
              acts on inline content of the box that overflows, which rules out
              putting the width on the button and letting this block spill. */}
          <span
            ref={titleTextRef}
            className="block overflow-hidden text-ellipsis whitespace-nowrap font-black leading-none text-foreground"
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
              <Search className={tdaySearchCapsuleIconClass} />
              <input
                autoFocus
                type="search"
                value={searchQuery}
                onChange={(event) => onSearchQueryChange(event.target.value)}
                placeholder={searchPlaceholder}
                aria-label={searchAriaLabel}
                className={tdaySearchCapsuleInputClass}
              />
              <button
                type="button"
                onClick={() => onSearchOpenChange(false)}
                aria-label={searchAriaLabel}
                className={tdaySearchCapsuleClearClass}
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
        {/* The same trick for the title: once the visible node is bounded to the
            width it may paint into, its own width no longer says how wide the
            name wants to be. Unscaled, so the number is in the coordinates the
            transform is written in. */}
        <span
          ref={titleMeasureRef}
          aria-hidden
          className="pointer-events-none invisible absolute left-0 top-0 whitespace-nowrap font-black"
          style={{ fontSize: m.heroTitleFontSize, lineHeight: `${m.heroTitleLineHeight}px` }}
        >
          {title}
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
