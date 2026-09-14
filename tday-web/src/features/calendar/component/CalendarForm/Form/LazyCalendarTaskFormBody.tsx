import { Suspense, lazy } from "react";
import FormBodyPlaceholder from "@/features/calendar/component/LoadingPlaceholders/FormBodyPlaceholder";
import type { CalendarTaskFormBodyProps } from "./CalendarTaskFormBody";

/**
 * The form body, split out of the bundle and waited for *inside* the sheet.
 *
 * The split used to be a level up: `EditFormContainer` and
 * `CreateFormContainer` lazily imported whole shells, so the boundary sat
 * around the drawer and the modal themselves and the Suspense fallback had to
 * be a surface. It could not be the right one — one `DrawerPlaceholder`
 * answered for both branches, so a desktop tap drew a bottom sheet for an
 * arriving centred modal — and it could not hand over, because the only thing
 * a fallback can do when its chunk lands is be removed. The sheet arrived
 * twice.
 *
 * Moving the boundary here fixes both by construction rather than by matching:
 * the shell the user is looking at *is* the real one, so it is the right shape
 * on both breakpoints without anybody choosing, and it mounts once and slides
 * in once while its contents are swapped underneath. This is the shape
 * `TaskFormSheet` already uses — an eager `AppBottomSheet` around a suspended
 * `TodoFormContainer` — and there is now one fewer way to spell it.
 *
 * It also puts the boundary where the weight is. The shells are chrome plus a
 * mutation hook, and `rrule` was already eager in the containers via
 * `useCalendarTaskFormState`; what is actually worth deferring is this body's
 * subtree — the chrono-parsing title field, the TipTap notes editor and the
 * selector overlays.
 *
 * No transition on the swap, deliberately. `lazy` renders an already-resolved
 * module without suspending, so on every open after the first the placeholder
 * never appears — a fade declared here would then play over a sheet that is
 * itself still arriving, animating the same content twice for the common case
 * in order to smooth the rare one.
 */
const CalendarTaskFormBody = lazy(() => import("./CalendarTaskFormBody"));

export default function LazyCalendarTaskFormBody(props: CalendarTaskFormBodyProps) {
  return (
    <Suspense fallback={<FormBodyPlaceholder />}>
      <CalendarTaskFormBody {...props} />
    </Suspense>
  );
}
