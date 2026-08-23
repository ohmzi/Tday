import type { ElementType, ReactNode, RefObject } from "react";
import { useEffect, useLayoutEffect, useMemo, useRef } from "react";
import { nativeAppScrollAttribute } from "./nativeAppLayout";
import { clamp01, smootherstep } from "./nativeHeaderEasing";
import { cn } from "@/lib/utils";

/**
 * Geometry for the screen header used by every titled page that is not a root
 * feed: the page's own glyph in a tinted circle with the title beneath it,
 * folding away as the page scrolls until only the title is left in the pinned
 * bar.
 *
 * Distinct from `rootFeedHeroHeaderMetrics`, which describes the root feeds'
 * header — that one has a search field and morphs its title's size on the way
 * up. This one leads with the page's own glyph and keeps the title at one size
 * throughout: there is a single title element, living in the pinned bar and
 * translated down to sit under the circle at rest, so what travels up IS what
 * was down there rather than a second copy of it fading in. They share the
 * easing curve deliberately, so the two kinds of header feel like one family.
 *
 * The geometry below — the circle, its glyph, the echo and the fade band — is
 * the native numbers (`TdayHeroTitleMetrics` in `core/ui/TdayHeroTitleHeader.kt`,
 * `TodoTimelineMetrics` in `Feature/Todos/TodoListScreen.swift`); keep those
 * three in step. The timings are deliberately NOT shared: native drives them off
 * one collapse fraction because the block there gives its height back, while on
 * the web there is one title element that simply moves. There is no value to
 * copy across.
 */
export const nativePageHeaderMetrics = {
  markBox: 96,
  markGlyph: 44,
  /** The oversized echo of the glyph sitting behind it inside the circle. */
  markEchoGlyph: 108,
  markEchoOffsetX: 22,
  markEchoOffsetY: 26,

  /** Opacity range of the accent wash behind the glyph. */
  markWashTopAlpha: 0.24,
  markWashBottomAlpha: 0.07,
  markEchoAlpha: 0.17,

  /** Breathing room each side of the title once it has parked in the bar. */
  dockedTitleSideGap: 8,
  /**
   * The bar's minimum row height. With nothing on the left any more and the
   * title absolutely positioned, a page with no actions would leave the row with
   * no content at all and the bar would shrink to its own padding — taking the
   * title's parked position down with it. 56 clears the title at both type
   * sizes and matches the row the search-bar pages get from their search
   * button, so it parks at the same height on every page.
   */
  barRowHeight: 56,
  /**
   * Over how much of the last stretch of travel the bar's side reserve comes
   * in. Only the parked title has neighbours to keep clear of; down in the
   * block it has the full width, and charging it there truncated titles that
   * used to fit. Symmetric, so easing it in never moves the centre.
   */
  reserveRampDistance: 72,

  /** Gradient below the bar that dissolves content as it passes under it. */
  contentFadeHeight: 30,

  /**
   * At `lg` the bar is `relative`, not sticky — it scrolls away with the page,
   * so there is nothing to dock into and the title just rides its anchor.
   * (`relative`, not `static`: the title is absolutely positioned inside it and
   * needs it as a containing block.) Written the way Tailwind emits it so the
   * script and the stylesheet cannot disagree.
   */
  desktopBreakpointQuery: "(min-width: 64rem)",
} as const;

/** The pinned bar shared by every non-root page, and by MobileSearchHeader. */
export const nativePageBarClassName = cn(
  "sticky top-0 z-40 flex w-full items-center justify-between gap-2.5 bg-background",
  "pt-[calc(0.5rem+env(safe-area-inset-top))] pb-1.5",
  "lg:relative lg:bg-transparent lg:pt-2 lg:pb-2",
);

/**
 * The nodes in the pinned bar that the collapse drives. A page whose bar
 * is not this component's own — the floater list keeps its search bar — creates
 * these with [useNativePageBarSlots] and hands the same object to both, so the
 * wiring stays plain React rather than a DOM lookup.
 */
export type NativePageBarSlots = {
  /** The pinned bar itself. The collapse is measured against its bottom edge. */
  barRef: RefObject<HTMLElement | null>;
  /** The one title element. It lives in the bar and is translated into place. */
  dockedTitleRef: RefObject<HTMLHeadingElement | null>;
  fadeRef: RefObject<HTMLDivElement | null>;
  /** What sits beside the title in the bar, so it can keep clear of it. */
  trailingRef: RefObject<HTMLDivElement | null>;
};

export function useNativePageBarSlots(): NativePageBarSlots {
  const barRef = useRef<HTMLElement | null>(null);
  const dockedTitleRef = useRef<HTMLHeadingElement | null>(null);
  const fadeRef = useRef<HTMLDivElement | null>(null);
  const trailingRef = useRef<HTMLDivElement | null>(null);
  return useMemo(
    () => ({ barRef, dockedTitleRef, fadeRef, trailingRef }),
    [barRef, dockedTitleRef, fadeRef, trailingRef],
  );
}

/**
 * Applied straight away rather than on the next animation frame, so a bar that
 * has just swapped its own contents is never painted with the styles it
 * remounted with.
 */
export const nativePageBarResyncEvent = "tday:page-bar-resync";

/**
 * Re-runs a header's collapse after the bar has swapped its own contents.
 * Remounting a node the collapse writes to — the floater list's search field
 * replaces the whole brand button — brings it back with no inline styles, and a
 * scroll frame is the only thing that would otherwise put them back.
 */
export function useNativePageBarResync(scope: HTMLElement | null, dependency: unknown) {
  // A layout effect, and the header answers it synchronously, so the corrected
  // styles are in place for the same paint that swapped the bar. A passive
  // effect plus the usual rAF would leave one frame showing the wordmark at
  // full size with no title docked.
  useLayoutEffect(() => {
    // No scope means either no header is listening or this is the first render,
    // where the bar's nodes are already correct straight from JSX and the
    // header applies itself on mount. Either way there is nothing to put back.
    const scroller = scope?.closest(`[${nativeAppScrollAttribute}]`) as HTMLElement | null;
    scroller?.dispatchEvent(new Event(nativePageBarResyncEvent));
  }, [scope, dependency]);
}

type Props = {
  title: string;
  accentColor: string;
  /** The page's own glyph. Rendered at full accent inside the tinted circle. */
  icon: ElementType;
  subtitle?: string;
  /** Trailing controls in the pinned bar, to the right of the title. */
  actions?: ReactNode;
  /** Rendered under the title, inside the block that scrolls away. */
  beneathTitle?: ReactNode;
  /**
   * Supplied when the page already owns its pinned bar. This component then
   * renders only the block that scrolls away, and drives the bar's nodes
   * through these refs instead of rendering a second bar of its own.
   */
  barSlots?: NativePageBarSlots;
  className?: string;
};

export default function NativePageHeader({
  title,
  accentColor,
  icon: Icon,
  subtitle,
  actions,
  beneathTitle,
  barSlots,
  className,
}: Props) {
  const m = nativePageHeaderMetrics;
  const heroRef = useRef<HTMLDivElement | null>(null);
  const markBoxRef = useRef<HTMLDivElement | null>(null);
  const markRef = useRef<HTMLDivElement | null>(null);
  /** Zero-content stand-in holding the title's place in the block below. */
  const titleAnchorRef = useRef<HTMLDivElement | null>(null);
  const ownSlots = useNativePageBarSlots();
  const slots = barSlots ?? ownSlots;
  const { barRef, dockedTitleRef, fadeRef, trailingRef } = slots;

  // The collapse is applied by writing styles straight onto the nodes inside a
  // rAF, never through React state. A scroll frame must not re-render the page
  // behind the header — that is the whole reason this is not a useState.
  useEffect(() => {
    const hero = heroRef.current;
    if (!hero) return;

    const scroller =
      (hero.closest(`[${nativeAppScrollAttribute}]`) as HTMLElement | null) ??
      (document.querySelector(`[${nativeAppScrollAttribute}]`) as HTMLElement | null);
    if (!scroller) return;

    let frame = 0;

    const apply = () => {
      frame = 0;
      const heroEl = heroRef.current;
      const markEl = markRef.current;
      const markBoxEl = markBoxRef.current;
      const anchorEl = titleAnchorRef.current;
      const titleEl = dockedTitleRef.current;
      const fadeEl = fadeRef.current;
      if (!heroEl || !markEl || !markBoxEl || !anchorEl) return;

      // Every layout read happens before any style write, so a scroll frame
      // never forces a synchronous reflow.
      const barRect = barRef.current?.getBoundingClientRect();
      const heroRect = heroEl.getBoundingClientRect();
      // The untransformed wrapper, never the circle itself: the circle carries
      // the scale written below, so measuring it would feed its own output back
      // in and make its opacity depend on how the scroll got here.
      const markRect = markBoxEl.getBoundingClientRect();
      const anchorRect = anchorEl.getBoundingClientRect();
      const titleHeight = titleEl?.offsetHeight ?? 0;
      const trailingWidth = trailingRef.current?.offsetWidth ?? 0;
      // The bar centres its own contents in its CONTENT box, and its top padding
      // carries the safe-area inset. Centring the title on the border box would
      // float it up into the status bar by half that inset on a notched phone.
      const barStyle = barRect ? getComputedStyle(barRef.current as HTMLElement) : null;
      const barPadTop = barStyle ? parseFloat(barStyle.paddingTop) : 0;
      const barPadBottom = barStyle ? parseFloat(barStyle.paddingBottom) : 0;
      // matchMedia rather than an innerWidth comparison: Tailwind emits `lg` as
      // `min-width: 64rem`, which is 1024px only while the browser's default
      // font size is 16px. Asking the same question the stylesheet asks keeps
      // the two from disagreeing for anyone who has enlarged it.
      const wide = window.matchMedia(m.desktopBreakpointQuery).matches;

      // At lg the bar is static, so there is nothing to dock into. Reset rather
      // than freeze: the viewport can cross the breakpoint mid-scroll.
      const barBottom = wide ? null : (barRect?.bottom ?? null);

      // How much of a box has gone behind the bar, as a fraction of itself.
      //
      // Every fade below is driven by this, on the element it belongs to —
      // never by one global progress number. That is what makes the header
      // honest at rest: a page too short to scroll the block away stops with
      // each piece dimmed exactly as far as it is actually hidden, so nothing
      // is ever left ghosted while it sits fully on screen. It also needs to
      // know nothing about the page's padding, the bar's height, the safe-area
      // inset, or how far the page happens to be able to scroll.
      const hiddenFraction = (rect: DOMRect) =>
        barBottom === null || rect.height <= 0
          ? 0
          : clamp01((barBottom - rect.top) / rect.height);

      // The mark is above the title, so it goes first without being told to.
      const markFade = 1 - smootherstep(hiddenFraction(markRect));
      markEl.style.opacity = String(markFade);
      markEl.style.transform = `scale(${0.85 + 0.15 * markFade})`;

      if (titleEl && barRect) {
        // Everything here is in the bar's own coordinates: the title sits at
        // `top: 0` and is translated down, so no percentage is involved and the
        // padding that carries the safe-area inset is accounted for explicitly.
        //
        // Parked, it is centred in the bar's content box — the same box the
        // glyph is centred in. At rest it is pushed back down to the gap the
        // block keeps for it, so what rises is the same element at the same
        // size, moving straight up, rather than one copy fading out while
        // another fades in somewhere else.
        //
        // Straight subtraction, so it tracks the page one-for-one and simply
        // stops when it arrives. There is no progress fraction to be wrong
        // about, no threshold to snap across, and a page too short to finish
        // just rests with the title part way — exactly where the page left it.
        const parkedY =
          barPadTop + (barRect.height - barPadTop - barPadBottom - titleHeight) / 2;
        const restingY = anchorRect.top - barRect.top;
        // At lg the bar scrolls away with the page, so there is nothing to park
        // in and the title just rides its anchor the whole way.
        const y = barBottom === null ? restingY : Math.max(parkedY, restingY);
        titleEl.style.transform = `translateY(${y}px)`;
        titleEl.style.visibility = "visible";

        // Kept clear of the bar's actions, and by the same amount on the empty
        // side so the title stays centred on the bar rather than on the
        // leftovers — eased in only over the last stretch, because down in the
        // block there is nothing beside it and the full width is its to use.
        const docking = clamp01(1 - (y - parkedY) / m.reserveRampDistance);
        const reserve = (trailingWidth + m.dockedTitleSideGap) * docking;
        titleEl.style.paddingLeft = `${reserve}px`;
        titleEl.style.paddingRight = `${reserve}px`;
      }

      // On as soon as anything is passing under the bar, over 8px so it does not
      // pop. The block itself is never dimmed — it slides behind an opaque bar,
      // and this band is what dissolves its edge on the way.
      if (fadeEl) {
        fadeEl.style.opacity = String(
          barBottom === null ? 0 : clamp01((barBottom - heroRect.top) / 8),
        );
      }
    };

    const schedule = () => {
      if (frame) return;
      frame = requestAnimationFrame(apply);
    };

    // The block's height is the denominator and the bar's height sets where it
    // starts, so a change to either moves the collapse without any scrolling —
    // a subtitle arriving, a web font landing, the `sm:` type ramp.
    const observer =
      typeof ResizeObserver === "undefined" ? null : new ResizeObserver(schedule);
    observer?.observe(hero);
    if (barRef.current) observer?.observe(barRef.current);
    if (trailingRef.current) observer?.observe(trailingRef.current);

    apply();
    scroller.addEventListener("scroll", schedule, { passive: true });
    scroller.addEventListener(nativePageBarResyncEvent, apply);
    window.addEventListener("resize", schedule);
    return () => {
      if (frame) cancelAnimationFrame(frame);
      observer?.disconnect();
      scroller.removeEventListener("scroll", schedule);
      scroller.removeEventListener(nativePageBarResyncEvent, apply);
      window.removeEventListener("resize", schedule);
    };
    // `title`/`subtitle` are dependencies so the effect re-runs — and so
    // re-applies, and re-observes the bar — once text that resizes either box
    // has painted.
  }, [m, barRef, dockedTitleRef, fadeRef, trailingRef, title, subtitle]);

  return (
    <>
      {barSlots ? null : (
      <header ref={barRef} className={nativePageBarClassName}>
        {/* Opaque backing above the pinned bar, covering the scroll container's
            top padding and the status-bar area so nothing shows through. */}
        <div
          aria-hidden
          className="pointer-events-none absolute inset-x-0 bottom-full h-screen bg-background lg:hidden"
        />

        {/* Nothing on the left. The page's own name is the only thing that
            belongs at the top of it, and getting home is what the dock at the
            bottom is for. This holds the row's height open, since with the
            title absolutely positioned the bar could otherwise collapse to its
            own padding on a page with no actions. */}
        <div className="w-0 shrink-0" style={{ height: m.barRowHeight }} aria-hidden />

        <div ref={trailingRef} className="ml-auto flex shrink-0 items-center">
          {actions}
        </div>

        {/* Content dissolves into the bar instead of being cut by its edge.
            Painted below the bar's own box, and hidden until the page moves so
            a page sitting at the top has no band across it. */}
        <div
          ref={fadeRef}
          aria-hidden
          className="pointer-events-none absolute inset-x-0 top-full bg-gradient-to-b from-background to-transparent lg:hidden"
          style={{ height: m.contentFadeHeight, opacity: 0 }}
        />
        {/* The page's title — the only one there is. It lives here, in the
            pinned bar, and is translated down to the block's gap at rest, so
            what travels up is this element rather than a copy of it. Absolute,
            so nothing in the bar can shove it sideways as it arrives. Last, so
            it paints over the dissolve band rather than being erased by it, and
            hidden until the first frame has placed it. */}
        <h1
          ref={dockedTitleRef}
          className="pointer-events-none absolute inset-x-0 top-0 truncate text-center text-[2.1rem] font-black leading-tight tracking-normal sm:text-[2.55rem]"
          style={{ color: accentColor, visibility: "hidden" }}
        >
          {title}
        </h1>
      </header>
      )}

      <div ref={heroRef} className={cn("mt-4 text-center sm:mt-5", className)}>
        {/* A flat glyph on a flat disc reads as a utility icon. The wash is a
            gradient with an oversized echo of the same glyph bleeding out of the
            bottom-right — the motif the category tiles already use.
            `color-mix` rather than an alpha suffix, because an accent arrives
            either as a hex literal or as `hsl(var(--accent-teal))`; only
            color-mix takes both unchanged. */}
        {/* Two boxes, not one: the outer keeps the untransformed geometry the
            collapse measures itself against, the inner carries the fade and the
            scale. Measuring a box by its own transform would be circular. */}
        <div
          ref={markBoxRef}
          aria-hidden
          className="mx-auto w-fit"
          style={{ width: m.markBox, height: m.markBox }}
        >
        <div
          ref={markRef}
          className="relative overflow-hidden rounded-full"
          style={{
            width: m.markBox,
            height: m.markBox,
            backgroundImage: `linear-gradient(135deg, color-mix(in srgb, ${accentColor} ${
              m.markWashTopAlpha * 100
            }%, transparent), color-mix(in srgb, ${accentColor} ${
              m.markWashBottomAlpha * 100
            }%, transparent))`,
          }}
        >
          <Icon
            aria-hidden
            strokeWidth={2}
            className="pointer-events-none absolute left-1/2 top-1/2"
            style={{
              width: m.markEchoGlyph,
              height: m.markEchoGlyph,
              color: accentColor,
              opacity: m.markEchoAlpha,
              transform: `translate(-50%, -50%) translate(${m.markEchoOffsetX}px, ${m.markEchoOffsetY}px)`,
            }}
          />
          <Icon
            aria-hidden
            strokeWidth={2.2}
            className="pointer-events-none absolute left-1/2 top-1/2 -translate-x-1/2 -translate-y-1/2"
            style={{ width: m.markGlyph, height: m.markGlyph, color: accentColor }}
          />
        </div>
        </div>

        {/* Holds the title's place. The title itself is up in the bar; this is
            the gap it is translated down into, so the block's height — and
            everything below it — is unchanged by where the title lives. */}
        <div
          ref={titleAnchorRef}
          aria-hidden
          className="pointer-events-none invisible mt-[18px] truncate text-[2.1rem] font-black leading-tight tracking-normal sm:text-[2.55rem]"
        >
          {title}
        </div>
        {subtitle ? (
          <p className="mt-1.5 text-sm font-extrabold text-muted-foreground">{subtitle}</p>
        ) : null}
        {beneathTitle}
      </div>
    </>
  );
}
