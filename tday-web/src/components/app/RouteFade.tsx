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
 * Only the arriving half fades, and the tree being left is never held mounted to
 * fade out: a page here is not an inert snapshot — it is live queries, realtime
 * subscriptions and focus effects, all of which would run in duplicate for those
 * milliseconds. The background is constant across every route, so a fade from it
 * looks the same as a fade through it, without any of that.
 *
 * That argument is about the live tree, and it is exactly the opening a view
 * transition walks through: an inert snapshot is what the browser hands back for
 * free. Where `document.startViewTransition` exists, `lib/navigation.tsx` asks for
 * one and `globals.css` lays that snapshot over the top to fade out, so the route
 * change gets its outgoing half at zero cost to this component. Nothing here
 * changes on that path and nothing here has to know it is on it — this fade is
 * still the arriving half either way, which is why there is no branch in the
 * markup below.
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
