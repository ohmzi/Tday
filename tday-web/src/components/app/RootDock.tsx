import { Home, Leaf, MoreHorizontal } from "lucide-react";
import { useTranslation } from "react-i18next";
import { usePathname, useRouter } from "@/lib/navigation";
import { hapticTick } from "@/lib/haptics";
import { scrollTo } from "@/lib/scroll";
import { useRef, useEffect, useCallback } from "react";
import { DURATION_MS } from "@/lib/motion";
import { prefersReducedMotion } from "@/lib/prefersReducedMotion";
import {
  nativeAppContentClassName,
  nativeAppHorizontalPaddingClassName,
  nativeAppScrollAttribute,
} from "@/components/app/nativeAppLayout";
import { nativeScreenAccentColors } from "@/components/app/nativeScreenTheme";
import { cn } from "@/lib/utils";

type DockTab = "scheduledTaskHome" | "floaterTaskHome" | "more";

const dockTabs: Array<{
  id: DockTab;
  labelKey: "scheduledTaskHome" | "root_feed_tab_floater" | "more";
  icon: typeof Home;
  path?: string;
  accentColor?: string;
}> = [
  { id: "scheduledTaskHome", labelKey: "scheduledTaskHome", icon: Home, path: "/app/tday", accentColor: nativeScreenAccentColors.today },
  {
    id: "floaterTaskHome",
    labelKey: "root_feed_tab_floater",
    icon: Leaf,
    path: "/app/floater",
    accentColor: nativeScreenAccentColors.floater,
  },
  { id: "more", labelKey: "more", icon: MoreHorizontal },
];

// Scrolls the currently-visible screen's scroll container back to the top.
function scrollActiveScreenToTop() {
  const container = document.querySelector<HTMLElement>(`[${nativeAppScrollAttribute}]`);
  scrollTo(container, { top: 0 });
}

function activeDockTab(pathname: string): DockTab {
  if (pathname.includes("/app/floater")) {
    return "floaterTaskHome";
  }
  if (pathname.includes("/app/tday") || pathname.includes("/app/today")) {
    return "scheduledTaskHome";
  }
  return "more";
}

/**
 * The root feed's bottom navigation.
 *
 * @param onOpenMore - Opens the More sheet; the third tab is the only one that
 *   does not navigate.
 * @param moreOpen - Whether that sheet is open, which is what makes More the
 *   selected tab while it is.
 * @param duckClassName - The `.tday-duck-*` class from `useDuckPresence`,
 *   applied to the fixed wrapper because that is the element the travel moves.
 *   The shell owns it: the dock leaves when selection mode takes its slot, and
 *   a control cannot animate its own unmounting.
 * @param duckInteractive - Whether the tabs may still be tapped, from the same
 *   hook. False while the dock is ducking out, and the default is true so a
 *   caller that never ducks it gets a normal dock.
 */
export default function RootDock({
  onOpenMore,
  moreOpen,
  duckClassName,
  duckInteractive = true,
}: {
  onOpenMore: () => void;
  moreOpen: boolean;
  duckClassName?: string;
  duckInteractive?: boolean;
}) {
  const router = useRouter();
  const pathname = usePathname();
  const { t: appDict } = useTranslation("app");
  const activeTab = moreOpen ? "more" : activeDockTab(pathname);

  // Sliding indicator pill
  const navRef = useRef<HTMLDivElement>(null);
  const buttonRefs = useRef<Map<DockTab, HTMLButtonElement>>(new Map());
  const pillRef = useRef<HTMLDivElement>(null);

  // Written straight onto the node rather than held as state, because the
  // follower below measures once per frame and a `setState` from inside a rAF
  // lands on the NEXT one — a pill a frame behind the tab it is marking is the
  // whole defect this is here to avoid, reintroduced by the plumbing.
  const updatePill = useCallback(() => {
    const btn = buttonRefs.current.get(activeTab);
    const nav = navRef.current;
    const pill = pillRef.current;
    if (!btn || !nav || !pill) return;
    const navRect = nav.getBoundingClientRect();
    const btnRect = btn.getBoundingClientRect();
    pill.style.transform = `translateX(${btnRect.left - navRect.left - 6}px)`;
    pill.style.width = `${btnRect.width}px`;
    pill.style.height = `${btnRect.height}px`;
    pill.style.opacity = "1";
  }, [activeTab]);

  useEffect(() => {
    // The pill's target is a tab's rect, and on a selection change that rect is
    // still moving when the change commits: the tab LOSING selection collapses
    // `sm:min-w-[104px]` → `sm:min-w-12` over Emphasis and slides every tab to
    // its right along with it. Measured once up front, the pill sets off for
    // where the arriving tab was standing BEFORE the collapse — which, measured
    // in Chromium, is 56px past where it is going — and then has to come back.
    // A single re-measure on a timer was doing the coming back, and it showed:
    // the pill reached the stale slot, turned round, and landed a quarter of a
    // second after the tabs had stopped.
    //
    // So follow the rect for as long as it can still be moving, instead of
    // sampling it twice and hoping. The pill keeps its own transition, which
    // absorbs the per-frame re-targeting into one unbroken path rather than a
    // stutter: same harness, the 56px round trip is gone, the pill settles onto
    // its mark from about five past it, and it is within a pixel of resting on
    // the frame the tabs stop on. Chasing a moving target under an ease-out is
    // what leaves those five, and they read as momentum rather than as a second
    // journey — which is the difference the 260 could not make however it was
    // timed.
    updatePill();
    // Nothing is in flight under reduced motion — `globals.css` floors every
    // transition on the page to 1ms — so the first measurement is already the
    // finished state, and a follower would be twenty forced layouts spent
    // watching a rect that cannot change.
    if (prefersReducedMotion()) return;
    let start: number | null = null;
    let raf = 0;
    const follow = (now: number) => {
      // Clocked from the first animation frame rather than from this effect:
      // the transition starts when that frame does. `<=` runs one frame past
      // the rung, so the last measurement is taken after the tab has landed and
      // the pill inherits the tab's own idea of where it stopped.
      start ??= now;
      updatePill();
      if (now - start <= DURATION_MS.emphasis) raf = requestAnimationFrame(follow);
    };
    raf = requestAnimationFrame(follow);
    return () => cancelAnimationFrame(raf);
  }, [updatePill]);

  return (
    <div
      className={cn(
        "pointer-events-none fixed inset-x-0 bottom-[calc(18px+env(safe-area-inset-bottom))] z-40",
        nativeAppHorizontalPaddingClassName,
        duckClassName,
      )}
    >
      <div
        className={cn(
          nativeAppContentClassName,
          // Left-aligned on mobile (matching native), centered on desktop.
          "flex items-center justify-start sm:justify-center",
        )}
      >
        <nav
          ref={navRef}
          aria-label="Primary app navigation"
          className={cn(
            // overflow-hidden clips the sliding indicator so it can never poke
            // out past the dock's right edge from a transient/stale measurement
            // (e.g. when the More sheet opens and the active tab collapses).
            "relative h-16 overflow-hidden rounded-[25px] border border-white/70 bg-muted/80 p-1.5",
            // Only while the dock is really the dock. On the way out it is a
            // picture of one, and the selection bar it is handing the row to
            // is painted UNDERNEATH it — the shell puts these controls outside
            // the stacking context the bar lives in — so a tap meant for Delete
            // would navigate the app away instead.
            duckInteractive && "pointer-events-auto",
            "shadow-[0_18px_42px_-24px_hsl(var(--shadow)/0.65)] backdrop-blur-xl",
            "dark:border-white/10 dark:bg-muted/80",
          )}
        >
          {/* Sliding indicator pill.

              Emphasis, and the same rung as the tabs below it — that pairing is
              the point, not a coincidence. The pill travels to the new tab AND
              resizes to it, which rule 2 calls geometry twice over.

              What it is paired WITH is the tab that just LOST selection. That one
              collapses `sm:min-w-[104px]` → `sm:min-w-12` once its label is gone,
              and in doing so slides every tab to its right — the arriving tab
              included. The pill's journey and the tabs' journey are the same
              journey seen from two ends, so they answer to one rung; they ran on
              300 and 200, two lengths neither of which named one, close enough to
              look deliberate and far enough apart that the tabs had stopped while
              the pill was still moving.

              Not the arriving tab's own width, which is what a `min-width` pair
              looks like it ought to be timing. That tab's label makes it wider
              than the 104px floor — measured in Chromium, 128px for "Scheduled"
              and within about five of the floor for "Floater" — so on the way IN
              the floor never binds and the tab has its width in the first frame,
              at any duration. The floor earns its keep on the way OUT, where the
              label is hidden and 48px is all the content asks for. */}
          <div
            ref={pillRef}
            className="pointer-events-none absolute left-1.5 top-1.5 rounded-[20px] bg-card shadow-[0_10px_24px_-18px_hsl(var(--shadow)/0.7)] transition-all duration-emphasis ease-[cubic-bezier(0.25,1,0.5,1)]"
          />
          <div className="relative flex h-full items-center gap-1">
            {dockTabs.map((tab) => {
              const Icon = tab.icon;
              const selected = tab.id === activeTab;
              const label = appDict(tab.labelKey);
              // The "More" button is hidden on mobile and only shown on desktop.
              const isMore = tab.id === "more";

              return (
                <button
                  key={tab.id}
                  ref={(el) => {
                    if (el) buttonRefs.current.set(tab.id, el);
                  }}
                  type="button"
                  aria-label={label}
                  onClick={() => {
                    hapticTick();
                    if (isMore) {
                      onOpenMore();
                      return;
                    }
                    // Re-tapping the tab of the screen you're already on scrolls
                    // that screen back to the top instead of a no-op navigation.
                    if (selected) {
                      scrollActiveScreenToTop();
                      return;
                    }
                    router.push(tab.path!);
                  }}
                  aria-current={selected ? "page" : undefined}
                  className={cn(
                    isMore ? "hidden sm:flex" : "flex",
                    "relative z-[1] h-12 min-w-12 items-center justify-center gap-2 rounded-[20px] px-3",
                    // Emphasis, paired with the indicator pill above — see the
                    // comment there. `all` is not the mechanism: the press layer
                    // in `globals.css` replaces `transition-property` wholesale
                    // on anything pressable, and enumerates `min-width` by hand
                    // for exactly this element because this call site says `all`.
                    // What the call site still owns is the LENGTH, and one
                    // duration covers every property on that list — so the hover
                    // tint and the press squash come up to Emphasis from 200 with
                    // the width. They cannot be split from here: a per-property
                    // duration would have to be positional against a fifteen-entry
                    // list this file cannot see. The width is the half that was
                    // visibly wrong, so the trade is taken here and the other half
                    // is a device row of its own — whether the tab still reads as
                    // answering a finger at 320.
                    "text-sm font-black transition-all duration-emphasis",
                    selected
                      ? ""
                      : "text-muted-foreground hover:bg-card/55 hover:text-foreground",
                    selected && !isMore ? "sm:min-w-[104px]" : "sm:min-w-12",
                  )}
                  style={selected && tab.accentColor ? { color: tab.accentColor } : undefined}
                >
                  <Icon className="h-6 w-6 stroke-[2.6]" />
                  <span className={cn("hidden", selected && !isMore && "sm:inline")}>
                    {label}
                  </span>
                </button>
              );
            })}
          </div>
        </nav>
      </div>
    </div>
  );
}
