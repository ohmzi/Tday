import type { ElementType, ReactNode, RefObject } from "react";
import { useEffect, useLayoutEffect, useMemo, useRef } from "react";
import NativeAppBrandButton from "./NativeAppBrandButton";
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
 * header — that one is pinned, has a search field and centres its title. This
 * one scrolls away and leads with the page's own glyph. They share the easing
 * curve deliberately, so the two kinds of header feel like one family.
 *
 * The geometry below — the circle, its glyph, the echo and the fade band — is
 * the native numbers (`TdayHeroTitleMetrics` in `core/ui/TdayHeroTitleHeader.kt`,
 * `TodoTimelineMetrics` in `Feature/Todos/TodoListScreen.swift`); keep those
 * three in step. The timings are deliberately NOT shared: native drives them off
 * one collapse fraction because the block there gives its height back, while on
 * the web the block scrolls behind the bar and each piece is driven by its own
 * position. There is no value to copy across — with the exception of the
 * handoff's travel, which is in the same units on all three and is shared.
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

  /**
   * How far BELOW the bar the hero title stops counting as readable. It is
   * already inside the gradient band there, so measuring the handoff from the
   * bar's own edge would hand over too late and leave a window — reachable as a
   * resting state on a page that scrolls just this far — with the hero title
   * dissolved and the bar's copy not yet in. Six tenths of the band is where
   * the veil has taken most of it.
   */
  dockHandoffLead: 18,

  /**
   * The handoff's elastic travel, shared with native: the hero copy is pulled
   * up as it goes ([expandedTitleLiftDistance] on iOS), and the bar's copy
   * rises into place from just below it while scaling the last 1.5% up
   * ([collapsedTitleRevealDistance]). Both ride the septic curve, so the title
   * leaves and arrives faster than the finger without ever snapping — which is
   * the whole difference between the two reading as one title moving and as two
   * titles cross-fading.
   */
  heroTitleLift: 14,
  dockedTitleRise: 10,
  dockedTitleScaleFrom: 0.985,

  /** Gradient below the bar that dissolves content as it passes under it. */
  contentFadeHeight: 30,

  /**
   * At `lg` the bar is `static`, not sticky — there is nothing to dock into, so
   * the header simply renders expanded and the collapse never runs. Written the
   * way Tailwind emits it so the script and the stylesheet cannot disagree.
   */
  desktopBreakpointQuery: "(min-width: 64rem)",
} as const;

/** The pinned bar every non-root page already had, kept class-for-class. */
export const nativePageBarClassName = cn(
  "sticky top-0 z-40 flex w-full items-center justify-between gap-2.5 bg-background",
  "pt-[calc(0.5rem+env(safe-area-inset-top))] pb-1.5",
  "lg:static lg:bg-transparent lg:pt-2 lg:pb-2",
);

/**
 * The three nodes in the pinned bar that the collapse drives. A page whose bar
 * is not this component's own — the floater list keeps its search bar — creates
 * these with [useNativePageBarSlots] and hands the same object to both, so the
 * wiring stays plain React rather than a DOM lookup.
 */
export type NativePageBarSlots = {
  /** The pinned bar itself. The collapse is measured against its bottom edge. */
  barRef: RefObject<HTMLElement | null>;
  dockedTitleRef: RefObject<HTMLSpanElement | null>;
  wordmarkRef: RefObject<HTMLSpanElement | null>;
  fadeRef: RefObject<HTMLDivElement | null>;
};

export function useNativePageBarSlots(): NativePageBarSlots {
  const barRef = useRef<HTMLElement | null>(null);
  const dockedTitleRef = useRef<HTMLSpanElement | null>(null);
  const wordmarkRef = useRef<HTMLSpanElement | null>(null);
  const fadeRef = useRef<HTMLDivElement | null>(null);
  return useMemo(
    () => ({ barRef, dockedTitleRef, wordmarkRef, fadeRef }),
    [barRef, dockedTitleRef, wordmarkRef, fadeRef],
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
  /** Trailing controls in the pinned bar, beside the brand button. */
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
  const heroTitleRef = useRef<HTMLHeadingElement | null>(null);
  const ownSlots = useNativePageBarSlots();
  const slots = barSlots ?? ownSlots;
  const { barRef, dockedTitleRef, wordmarkRef, fadeRef } = slots;

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
      const heroTitleEl = heroTitleRef.current;
      const dockedEl = dockedTitleRef.current;
      const wordmarkEl = wordmarkRef.current;
      const fadeEl = fadeRef.current;
      if (!heroEl || !markEl || !markBoxEl || !heroTitleEl) return;

      // Every layout read happens before any style write, so a scroll frame
      // never forces a synchronous reflow.
      const barRect = barRef.current?.getBoundingClientRect();
      const heroRect = heroEl.getBoundingClientRect();
      // The untransformed wrapper, never the circle itself: the circle carries
      // the scale written below, so measuring it would feed its own output back
      // in and make its opacity depend on how the scroll got here.
      const markRect = markBoxEl.getBoundingClientRect();
      const titleRect = heroTitleEl.getBoundingClientRect();
      const wordmarkWidth = wordmarkRef.current?.scrollWidth ?? 0;
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
      // Measured against the bottom of the dissolve band rather than the bar's
      // own edge — see `dockHandoffLead`. The title is spoken for well before
      // its last pixel disappears.
      const titleGone =
        barBottom === null || titleRect.height <= 0
          ? 0
          : clamp01(
              (barBottom + m.dockHandoffLead - titleRect.top) / titleRect.height,
            );
      const dockFade = smootherstep(titleGone);

      markEl.style.opacity = String(markFade);
      markEl.style.transform = `scale(${0.85 + 0.15 * markFade})`;

      // On top of the travel the scroll already gives it, so the title leaves
      // faster than the finger over the handoff. Zero whenever the title is
      // still fully readable, so a page too short to hand over does not rest
      // with it nudged off its layout position.
      heroTitleEl.style.transform =
        dockFade <= 0 ? "" : `translateY(${-m.heroTitleLift * dockFade}px)`;

      if (dockedEl) {
        dockedEl.style.opacity = String(dockFade);
        // Invisible text must not be read out, nor eat taps meant for the bar.
        dockedEl.style.visibility = dockFade < 0.01 ? "hidden" : "visible";
        dockedEl.style.transform =
          `translateY(${m.dockedTitleRise * (1 - dockFade)}px) ` +
          `scale(${m.dockedTitleScaleFrom + (1 - m.dockedTitleScaleFrom) * dockFade})`;
      }

      if (wordmarkEl) {
        // Only the wordmark goes; the sun/moon glyph stays as the home
        // affordance, the way the native bars keep their back chevron. Its width
        // folds continuously rather than snapping at a threshold: the docked
        // title is a flex sibling, so a stepped width would jog it sideways by
        // the wordmark's whole length in a single frame.
        wordmarkEl.style.opacity = String(1 - dockFade);
        wordmarkEl.style.maxWidth =
          dockFade <= 0 ? "" : `${Math.max(0, (1 - dockFade) * wordmarkWidth)}px`;
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
  }, [m, barRef, dockedTitleRef, wordmarkRef, fadeRef, title, subtitle]);

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

        <NativeAppBrandButton
          className="min-w-0 max-w-[58%] sm:max-w-none"
          wordmarkRef={wordmarkRef}
        />

        {/* Only appears once the hero title has left, so the two never read as
            two titles at once. In flow rather than absolutely centred: it takes
            whatever the folded wordmark and the actions leave, and ellipsizes.
            Centring it on the bar let it paint over both on narrow phones. */}
        <span
          ref={dockedTitleRef}
          aria-hidden
          className="pointer-events-none min-w-0 flex-1 origin-center truncate text-center text-[1.4rem] font-black leading-none text-foreground lg:hidden"
          style={{ opacity: 0, visibility: "hidden" }}
        >
          {title}
        </span>

        {actions}

        {/* Content dissolves into the bar instead of being cut by its edge.
            Painted below the bar's own box, and hidden until the page moves so
            a page sitting at the top has no band across it. */}
        <div
          ref={fadeRef}
          aria-hidden
          className="pointer-events-none absolute inset-x-0 top-full bg-gradient-to-b from-background to-transparent lg:hidden"
          style={{ height: m.contentFadeHeight, opacity: 0 }}
        />
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

        <h1
          ref={heroTitleRef}
          className="mt-[18px] truncate text-[2.1rem] font-black leading-tight tracking-normal sm:text-[2.55rem]"
          style={{ color: accentColor }}
        >
          {title}
        </h1>
        {subtitle ? (
          <p className="mt-1.5 text-sm font-extrabold text-muted-foreground">{subtitle}</p>
        ) : null}
        {beneathTitle}
      </div>
    </>
  );
}
