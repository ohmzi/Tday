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

/** Whether the current route is one of the app's list screens (scheduled or floater). */
export function isListScreenRoute(pathname: string): boolean {
  return LIST_SCREEN_ROUTE.test(pathname);
}

/** Whether the current route is anywhere inside the signed-in app, `/app/...` included. */
function isInAppRoute(pathname: string): boolean {
  return IN_APP_ROUTE.test(pathname);
}

/**
 * Set by {@link announceListScreenExit} for the route change it is about to cause,
 * and cleared by the next route change however it lands, so the hook does not
 * buzz the same exit a second time.
 */
let exitAnnounced = false;

/**
 * Buzzes the list handover for an exit the caller is about to make itself — the
 * back chevron, called from inside its click.
 *
 * The hook below can only see the route after it has changed, one commit later and
 * in an effect, by which point the tap that caused it is further away. The app's
 * other back and primary-action haptics fire inside their click handlers, and this
 * one does too. Browser back and the edge swipe never reach a click, so they still
 * go through the hook; that is why both paths exist.
 */
export function announceListScreenExit(fromPathname: string): void {
  if (!isListScreenRoute(fromPathname)) return;
  exitAnnounced = true;
  hapticScreenChange();
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
    const announced = exitAnnounced;
    exitAnnounced = false;
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
    if (announced || !isInAppRoute(pathname)) return;
    hapticScreenChange();
  }, [pathname]);
}
