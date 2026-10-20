import { prefersReducedMotion } from "@/lib/prefersReducedMotion";

/**
 * Whether this navigation is one the route hand-over has anything to say about,
 * one the user wants drawn, and one the browser can actually play.
 *
 * Three questions, and all three have to be answered here rather than in
 * `globals.css`, because a view transition is started by the navigation and not by
 * the stylesheet.
 *
 * The first is the same question `RouteFade` answers when it keys its fade on
 * `pathname` and not on the full location: a query-string change — the task-focus
 * params the timeline pages push — is the same screen answering itself. A view
 * transition snapshots the whole document, so starting one there would crossfade a
 * page with itself, which is the flash `RouteFade` already refuses to draw.
 *
 * The second is reduced motion, and it is here rather than only in the stylesheet
 * because the CSS override arrives too late to be the whole answer. `globals.css`
 * can pin the finished frame, but it cannot stop React Router taking the opt-in's
 * slower path: with `viewTransition` on, the new route is not committed in the
 * navigation — it is parked in `pendingState`, picked up two effect passes later
 * and applied inside the `startViewTransition` callback. A user who asked for less
 * motion would pay that deferral to be shown nothing. Off means off before the
 * route change is delayed, not after.
 *
 * The third is a feature test React Router does not need — it falls back to a
 * plain state update on its own — but does warn about, once per app, on every
 * environment that cannot honour the opt-in. jsdom is one of those. Asking a
 * question we can answer for free is cheaper than a warning nobody can act on.
 *
 * All asked per call rather than captured at module scope, for the reason
 * `prefersReducedMotion` gives about `matchMedia`: a module-scope read binds to
 * whatever the document was at import time, which in a test is before anything has
 * had a chance to stub it.
 *
 * @param target - The destination, already localized, exactly as it is handed to
 *   React Router.
 * @param current - The pathname being left.
 * @returns Whether to ask React Router for a view transition.
 */
export function startsRouteHandover(target: string, current: string): boolean {
  if (typeof document === "undefined") return false;
  if (typeof document.startViewTransition !== "function") return false;
  if (prefersReducedMotion()) return false;
  const targetPath = target.split(/[?#]/)[0];
  // A `to` that is only a query string or only a fragment resolves to the page it
  // was clicked on, and splits to the empty string rather than to that page's path —
  // the one way this comparison can say "different" about a destination that is not.
  if (targetPath === "") return false;
  return targetPath !== current;
}
