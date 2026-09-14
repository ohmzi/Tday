import { Home, Leaf, MoreHorizontal } from "lucide-react";
import { useTranslation } from "react-i18next";
import { usePathname, useRouter } from "@/lib/navigation";
import { hapticTick } from "@/lib/haptics";
import { scrollTo } from "@/lib/scroll";
import { useRef, useEffect, useCallback, useState } from "react";
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

/**
 * Which tab a path belongs to. Exported because `NativeAppShell` decides from
 * the same rule whether this dock may collapse at all, and two spellings of
 * "is this route a root feed" is one spelling too many — a shell that thought
 * a route was a feed while the dock did not would collapse a dock with no tab
 * selected in it.
 */
export function activeDockTab(pathname: string): DockTab {
  if (pathname.includes("/app/floater")) {
    return "floaterTaskHome";
  }
  if (pathname.includes("/app/tday") || pathname.includes("/app/today")) {
    return "scheduledTaskHome";
  }
  return "more";
}

// not a token — see docs/motion.md. A dwell, not a duration: for 2.4 seconds
// nothing moves and nobody is watching anything travel. It is the same family as
// the 600-620ms band that file's "Deliberate non-tokens" table excludes — time
// measured against what the user is doing rather than against the ladder — and
// both natives carry it as their own literal for the same reason
// (`RootFeedDock.kt:103`, `RootFeedDock.swift:132`). Android additionally
// stretches it through the OS accessibility timeout; web has no equivalent to
// read, so this is the plain window until it does.
const ROOT_DOCK_TAP_EXPAND_MS = 2400;

/**
 * The transitions whose landing means the pill's target has stopped moving.
 *
 * Both are read straight off the two class strings below rather than guessed at:
 * the wrapper span's `1fr` -> `0fr` track IS the scroll fold, and the button's
 * `sm:min-w-[104px]` -> `sm:min-w-12` is a selection change on a desktop dock.
 *
 * The tab row's `gap-1` -> `gap-0` moves a tab's rect too, and is deliberately
 * not a third entry: it is driven by the same `expanded` flip at the same rung
 * and the same curve as the track, so the two land together and the track's
 * event is already the last word. Retiming the gap off that rung would break
 * that, and this set is where it would have to be said.
 *
 * A filter rather than "any transition on the nav", because most of what fires
 * here moves nothing the pill is measured against — the wrapper's `opacity`, the
 * tab's hover tint, and the pill's own `width`/`transform`, which `updatePill`
 * starts every time it writes them and which would have it re-measure a rect it
 * did not change in order to rewrite values it already holds. The tab's padding
 * is absent for a different reason again: `padding` is not on the press layer's
 * `transition-property` list, which replaces whatever a pressable asked for (see
 * the `px-0` argument down in the fold's class string), so it never fires this
 * event at all. It lands in the commit frame instead — which is part of why the
 * measurement taken there is the wrong one.
 */
const PILL_SETTLE_PROPERTIES = new Set(["grid-template-columns", "min-width"]);

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
 * @param collapsed - Whether the feed under the dock has been scrolled past the
 *   fold (`rootDockCollapse.ts`). The shell asks the question because the dock
 *   cannot: the scroller belongs to the screen, not to this control. Defaults
 *   to false so a caller that never collapses it gets a normal dock.
 */
export default function RootDock({
  onOpenMore,
  moreOpen,
  duckClassName,
  duckInteractive = true,
  collapsed = false,
}: {
  onOpenMore: () => void;
  moreOpen: boolean;
  duckClassName?: string;
  duckInteractive?: boolean;
  collapsed?: boolean;
}) {
  const router = useRouter();
  const pathname = usePathname();
  const { t: appDict } = useTranslation("app");
  const activeTab = moreOpen ? "more" : activeDockTab(pathname);

  // Tapping a collapsed dock opens it where it stands, so the tab you were NOT
  // on is reachable without scrolling a feed back to the top first — the same
  // two lines as `RootFeedDock.kt:175-176` and `RootFeedDock.swift:73-74`.
  const [expandedByTap, setExpandedByTap] = useState(false);
  const expanded = !collapsed || expandedByTap;

  useEffect(() => {
    // Scrolling back to the top expands the dock on its own, so the override has
    // nothing left to override. Left armed, the next scroll past the fold would
    // find it still set and the dock would refuse to collapse once.
    if (!collapsed) setExpandedByTap(false);
  }, [collapsed]);

  useEffect(() => {
    if (!expandedByTap) return;
    const timer = window.setTimeout(() => setExpandedByTap(false), ROOT_DOCK_TAP_EXPAND_MS);
    return () => window.clearTimeout(timer);
  }, [expandedByTap]);

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
    //
    // Run on `expanded` as well as on selection, because the FOLD moves the
    // active tab without changing which tab is active: on the Anytime feed the
    // tab that closes is the one to its left, so the tab you are on slides 52px
    // inwards — measured in Chromium, 59 → 7 from the capsule's edge — while
    // nothing about the selection has changed. Keyed on selection alone the pill
    // does not lag and recover, it simply stays where the open dock left it for
    // as long as the dock stays folded: most of it outside a 62px capsule that
    // clips, and the rest beside the tab instead of under it. Android's selector
    // re-derives its offset from the shrinking dock for the same reason
    // (`RootFeedDock.kt:269-289`: `maxWidth - selectorWidth`, sprung); this is
    // the same answer given by re-measuring, which the pill is already set up to
    // do once a frame.
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
  }, [updatePill, expanded]);

  useEffect(() => {
    const nav = navRef.current;
    if (!nav) return;
    // The half the follower above cannot do, and the half reduced motion needs.
    //
    // `updatePill` runs inside the effect that COMMITS the fold, and the
    // `getBoundingClientRect` it does there is what forces the layout that starts
    // the transition — so the rect it reads back is that transition's first frame,
    // which is the shape the dock is leaving rather than the one it is going to.
    // With motion on, the follower re-reads until that stops being true. With
    // motion off it returns immediately and nothing re-reads at all, so the pill
    // keeps the open dock's slot for as long as the dock stays folded: the exact
    // defect the follower exists to prevent, surviving in the one branch that
    // cannot use it. Removing the travel must not mean keeping the wrong frame.
    //
    // The event is what closes it. `globals.css` floors every transition on the
    // page to 1ms instead of switching it off precisely so the completion still
    // reports itself, and this is the listener that was missing to hear it. On the
    // animated path it costs one measurement and is the settle: the last word on
    // where the tab stopped comes from the tab, not from a clock that ran beside
    // it.
    const settle = (event: TransitionEvent) => {
      if (!PILL_SETTLE_PROPERTIES.has(event.propertyName)) return;
      updatePill();
    };
    // On the nav rather than on the active button: `transitionend` bubbles, the
    // fold is declared on a wrapper the button does not own, and which element is
    // active changes under this listener while the nav does not.
    nav.addEventListener("transitionend", settle);
    return () => nav.removeEventListener("transitionend", settle);
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
            // out past the dock's right edge from a transient or stale
            // measurement. Two cases now, and the second is not the same size as
            // the first. The More sheet opening collapses the one tab that held
            // selection, which is what this was written for. The scroll fold
            // collapses every tab but one, and there the pill is outside the
            // capsule by construction rather than occasionally: on the Anytime
            // feed the active tab is the second, so its slot ends 52px to the LEFT
            // of where it starts while the capsule closes 114 -> 62 around it, and
            // for most of the fold the pill's right edge is past the border it is
            // supposed to be inside. Clipped, that reads as the pill shutting with
            // the capsule; unclipped it is a white bar lying across the dock.
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
              label is hidden and 48px is all the content asks for.

              Not a token — see docs/motion.md. The curve, unlike the rung, has
              nothing to be a token WITH: it is a hard-out with no counterpart on
              Android or iOS, both of which express this dock with a spring. It is
              the third and last of web's orphan curves, and with the other two
              argued where they are written the ceiling and the list of sites are
              now the same three. */}
          <div
            ref={pillRef}
            className="pointer-events-none absolute left-1.5 top-1.5 rounded-[20px] bg-card shadow-[0_10px_24px_-18px_hsl(var(--shadow)/0.7)] transition-all duration-emphasis ease-[cubic-bezier(0.25,1,0.5,1)]"
          />
          <div
            className={cn(
              "relative flex h-full items-center transition-all duration-emphasis ease-in-out",
              // The gap collapses with the tabs it separates. Left at 4px it
              // would still be spacing tabs that are no longer there, and the
              // collapsed capsule would carry one dead gap per hidden tab —
              // read as the pill being off-centre in its own border rather than
              // as a gap, because nothing is left in it to be spaced.
              expanded ? "gap-1" : "gap-0",
            )}
          >
            {dockTabs.map((tab) => {
              const Icon = tab.icon;
              const selected = tab.id === activeTab;
              const label = appDict(tab.labelKey);
              // The "More" button is hidden on mobile and only shown on desktop.
              const isMore = tab.id === "more";
              // A collapsed dock keeps the tab you are on and gives up the rest.
              const folded = !expanded && !selected;

              return (
                <span
                  key={tab.id}
                  className={cn(
                    isMore ? "hidden sm:grid" : "grid",
                    // The collapse rides on this wrapper and not on the button
                    // inside it. `globals.css`'s press layer REPLACES
                    // `transition-property` on anything pressable with a closed
                    // list, and that list leaves `width` out by name — so a
                    // width declared on the button would be deleted from above
                    // and the tab would vanish in one frame beside a capsule
                    // still gliding: the jolt-under-a-slow-companion that layer
                    // exists to remove, relocated rather than removed. A span is
                    // not pressable and keeps the transition it declares.
                    //
                    // `1fr` -> `0fr`, the same interpolable spelling of "as wide
                    // as whatever is inside it" that every collapsing task row
                    // uses on the other axis (`TodoItemContainer`). A width
                    // cannot be animated away from `auto`, and a measured one
                    // would want re-measuring every time a locale changes the
                    // label under it.
                    //
                    // Emphasis: the tabs change size and the capsule around them
                    // changes size with them, which the second idiom rule
                    // (docs/motion.md) puts on that rung without asking how
                    // important the change is. It is also the rung the pill and
                    // the tabs already share, and this is the same row moving.
                    // `ease-in-out` is the Standard curve spelled the way
                    // `globals.css`'s theme block says to spell it.
                    //
                    // No reduced-motion branch, and none is wanted: the blanket
                    // floor at the top of `globals.css` pins every
                    // `transition-duration` on the page to 1ms, so this becomes a
                    // state change with no travel for free — the dock is simply
                    // already the shape it is going to be. A second guard here
                    // could only disagree with that one.
                    "overflow-hidden transition-all duration-emphasis ease-in-out",
                    folded ? "grid-cols-[0fr] opacity-0" : "grid-cols-[1fr]",
                  )}
                >
                  <button
                    ref={(el) => {
                      if (el) buttonRefs.current.set(tab.id, el);
                    }}
                    type="button"
                    aria-label={label}
                    onClick={() => {
                      hapticTick();
                      // A collapsed dock is its own expand affordance: the only
                      // button still reachable is the active one, and while the
                      // dock is folded that tap opens it instead of doing what the
                      // tab does. Same trade as `RootFeedDock.swift:141-143` —
                      // scrolling to the top is available from the feed, and the
                      // tab you cannot see is not available from anywhere else.
                      if (!expanded) {
                        setExpandedByTap(true);
                        return;
                      }
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
                      "relative z-[1] flex h-12 min-w-12 items-center justify-center gap-2 rounded-[20px] px-3",
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
                      // Scoped to the fold rather than left on, for the reason
                      // `.tday-empty-slot-closing > *` gives about its own track: a
                      // grid item that is not a scroll container keeps its content
                      // size as the track's floor, so without these the `0fr`
                      // above would have nothing to shrink to — and left on, the
                      // tab would be free to squash under its own label.
                      //
                      // `px-0` is half of that floor and the half that is easy to
                      // miss. `min-width: 0` lets the CONTENT go to nothing; the
                      // padding is not content, and under `box-sizing: border-box`
                      // a box cannot be used narrower than its own padding — so
                      // `px-3` alone leaves every folded tab a 24px stub, one per
                      // tab that closed. Measured in Chromium against the built
                      // stylesheet: a phone dock folds 114 → 86 with the padding
                      // and 114 → 62 without it, and a desktop dock 233 → 177
                      // against 233 → 129 — so the capsule stops a stub short of
                      // closed, with the stub sitting where a tab used to be. Unlike a width, the padding does not
                      // need to be transitionable — which is just as well, since
                      // the press layer would delete that too: the track above is
                      // what travels, and dropping symmetric padding under a
                      // centred icon moves nothing.
                      folded && "min-w-0 overflow-hidden px-0 sm:min-w-0",
                      // Not `hidden`: a tab removed from the flow takes the
                      // transition with it and the dock snaps shut. The button is
                      // still laid out, still being clipped, and merely unable to
                      // take a tap meant for the one tab that is left.
                      folded && "pointer-events-none",
                    )}
                    style={selected && tab.accentColor ? { color: tab.accentColor } : undefined}
                  >
                    <Icon className="h-6 w-6 shrink-0 stroke-[2.6]" />
                    <span className={cn("hidden", selected && !isMore && "sm:inline")}>
                      {label}
                    </span>
                  </button>
                </span>
              );
            })}
          </div>
        </nav>
      </div>
    </div>
  );
}
