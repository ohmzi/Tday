import { useEffect, useRef } from "react";
import { usePathname } from "@/lib/navigation";
import { hapticScreenChange } from "@/lib/haptics";

/**
 * A custom list is a *screen* the user walks into and back out of, and both native
 * clients buzz the handover on the way in and on the way out — iOS from
 * `AppRootView.reportListScreenHandover`, Android from the same count over its
 * back stack. Web had no equivalent, so backing out of a list was the one screen
 * change in the app that arrived silently (issue: "Webapp not vibrating backing
 * out list").
 *
 * The native rule is "fire when the number of list screens in the navigation
 * stack changes", deliberately in both directions and exactly once each — not on
 * every navigation, and not for a push that is not a list screen. A URL only ever
 * shows the screen currently on top, so the web reading of that rule is the one
 * it can actually observe: fire when the current route enters or leaves a list
 * screen, which is the same open/close pair for the paths a browser can hold.
 *
 * A session ending is the one handover this must not announce: 401s bounce the
 * app to the sign-in overlay in the same turn, and a buzz there would read as
 * "you closed a screen" beside the "session expired" notice. Both natives guard
 * on the workspace still being available; the web equivalent is requiring the
 * route on BOTH sides of the change to be an in-app one, which drops the
 * list → /login transition without needing to know why the path moved.
 */
const LIST_SCREEN_ROUTE = /(?:^|\/)app\/(?:list|floater-list)\//;
const IN_APP_ROUTE = /(?:^|\/)app\//;

function isListScreenRoute(pathname: string): boolean {
  return LIST_SCREEN_ROUTE.test(pathname);
}

function isInAppRoute(pathname: string): boolean {
  return IN_APP_ROUTE.test(pathname);
}

/**
 * Fires {@link hapticScreenChange} once per list screen opened and once per list
 * screen closed, mirroring the native clients. Mount once, from the app shell.
 */
export function useListScreenHandoverHaptic(): void {
  const pathname = usePathname();
  // `null` until the first route is seen: the initial mount is not a handover,
  // it is where the user already is.
  const wasListScreen = useRef<boolean | null>(null);

  useEffect(() => {
    const isListScreen = isListScreenRoute(pathname);
    if (wasListScreen.current === null) {
      wasListScreen.current = isListScreen;
      return;
    }
    if (wasListScreen.current === isListScreen) return;
    // Advance the record first, then decide — a transition suppressed because it
    // left the app (the session ending) is still remembered, so the next real
    // open/close is measured from where the user actually is.
    wasListScreen.current = isListScreen;
    if (!isInAppRoute(pathname)) return;
    hapticScreenChange();
  }, [pathname]);
}
