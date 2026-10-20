import { useEffect, useRef, useState } from "react";
import { nativeAppScrollAttribute } from "@/components/app/nativeAppLayout";
import { usePathname } from "@/lib/navigation";
import { nextRootDockCollapsed } from "@/lib/rootDockCollapse";

/**
 * Whether the root dock should be collapsed, read off whichever screen is on
 * the glass.
 *
 * The scroller is found by attribute rather than by ref because the shell does
 * not own it: `NativeAppPageLayout` puts `data-native-scroll` on the box each
 * screen scrolls inside, and the dock is a sibling of the whole route tree, not
 * of the screen. `RootDock` already asks the same question the same way when a
 * re-tapped tab scrolls its screen to the top.
 *
 * @param enabled - Whether this route is one the dock collapses on at all. When
 *   false the hook returns "expanded" and subscribes to nothing, rather than
 *   subscribing and ignoring the answer: a listener on a screen whose dock
 *   cannot collapse is a scroll handler running for no one.
 */
export function useRootDockCollapsed(enabled: boolean): boolean {
  const pathname = usePathname();
  const [collapsed, setCollapsed] = useState(false);
  // The fold needs the PREVIOUS answer, and a scroll frame cannot read it out of
  // state: `setCollapsed` lands on the next render, so two scroll events inside
  // one render would both fold over the same stale value and the dead band would
  // be computed against an offset the user has already left.
  const collapsedRef = useRef(false);

  useEffect(() => {
    // A screen that has just arrived is at the top, which is what both natives
    // draw, so start from expanded on every route change rather than carrying
    // the last screen's answer into the new one.
    collapsedRef.current = false;
    setCollapsed(false);
    if (!enabled) return;

    // Found here rather than a frame later, because a frame later is not a
    // different answer. `RouteFade` keys its wrapper on the pathname and
    // deliberately refuses to hold the outgoing tree mounted — a screen here is
    // live queries and subscriptions, not an inert snapshot — so React unmounts
    // the screen being left and mounts the arriving one in a single commit, and
    // this effect runs after it. There is never a second `data-native-scroll`
    // box for `querySelector` to reach first: the outgoing half, where the
    // browser gives one at all, is `::view-transition-old(root)`, which is a
    // pseudo-element and belongs to no selector.
    const scroller = document.querySelector<HTMLElement>(`[${nativeAppScrollAttribute}]`);
    if (!scroller) return;

    let frame = 0;

    const apply = () => {
      frame = 0;
      const next = nextRootDockCollapsed(collapsedRef.current, scroller.scrollTop);
      if (next === collapsedRef.current) return;
      collapsedRef.current = next;
      setCollapsed(next);
    };

    // One frame handle, the shape `RootFeedHeroHeader` already uses on this same
    // scroller: a scroll event can fire several times between paints and the
    // answer can only change once per paint, so the extra reads would be layout
    // work nobody sees the result of.
    const schedule = () => {
      if (frame) return;
      frame = requestAnimationFrame(apply);
    };

    // Read once on arrival instead of trusting the reset above. A route the
    // browser restores mid-feed is at the offset it was left at, and a dock
    // drawn expanded over a feed that is not at its top is the finished state of
    // a scroll that already happened — the fifth idiom rule's "draw the
    // destination" read for a surface that never animated at all.
    apply();
    scroller.addEventListener("scroll", schedule, { passive: true });

    return () => {
      if (frame) cancelAnimationFrame(frame);
      scroller.removeEventListener("scroll", schedule);
    };
  }, [enabled, pathname]);

  return collapsed;
}
