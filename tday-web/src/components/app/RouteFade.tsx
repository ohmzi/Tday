import { Outlet, useLocation } from "react-router-dom";

/**
 * Fades each screen in as it arrives.
 *
 * Every screen draws its toolbar in the same row at the same coordinates, so
 * nothing in the bar needs to travel between routes — the back chevron, the
 * search capsule and the action cluster simply become the next screen's.
 * Before this they swapped on a single frame, which read as a flicker rather
 * than a handover. Fading the arriving screen in hands each button over in
 * place, which is what Android does with its own crossfade.
 *
 * Only the arriving half fades. Fading the LEAVING screen out as well would mean
 * holding its whole tree mounted for the length of the animation, and a page
 * here is not an inert snapshot — it is live queries, realtime subscriptions and
 * focus effects, all of which would run in duplicate for those milliseconds. The
 * background is constant across every route, so a fade from it looks the same as
 * a fade through it, without any of that.
 *
 * Keyed on `pathname`, not on the full location: a query-string change (the
 * task-focus params the timeline pages use) is the same screen answering itself,
 * and re-running the fade there would flash the page under the user.
 */
export default function RouteFade() {
  const { pathname } = useLocation();

  return (
    <div
      key={pathname}
      className="tday-route-fade flex min-w-0 flex-1 flex-col overflow-hidden"
    >
      <Outlet />
    </div>
  );
}
