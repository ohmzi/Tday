import { prefersReducedMotion } from "@/lib/prefersReducedMotion";

/**
 * Programmatic scrolling, with the one thing every call site was getting wrong
 * decided in a single place.
 *
 * A scroll is motion — the biggest single piece of motion this app ever plays,
 * since the whole viewport travels — and `behavior: "smooth"` is the only
 * animation in the web client that CSS cannot reach. `globals.css`'s floor
 * cannot touch it: `scrollIntoView({ behavior: "smooth" })` is documented to
 * override the `scroll-behavior` property rather than defer to it, so a script
 * asking for smooth gets smooth no matter what the stylesheet or the user says.
 * The preference therefore has to be read here, in JS, or not at all.
 *
 * Smooth is the default because it is what every caller already wanted; what
 * they could not be trusted to remember is the downgrade. It is a downgrade
 * only, never an upgrade: a caller that asks for `"auto"` has decided the jump
 * is the point (`GeneralLayout` resetting a fresh route to the top), and a
 * preference for less motion is not a reason to give it more.
 *
 * That default is also why not every scroll in `src/` belongs here.
 * `CommandPalette`'s keyboard cursor calls `scrollIntoView({ block: "nearest" })`
 * with no behavior at all, which the spec already resolves to a jump; routing it
 * would hand it the default and turn a keystroke into a slide. An upgrade is the
 * one thing this module must not perform, and the quietest way to perform one is
 * to route a caller that never asked.
 *
 * The fifth idiom rule (`docs/motion.md`) is satisfied by the shape of the
 * thing rather than by anything written here — a scroll's destination is a
 * scroll position, and an instant scroll lands on it. There is no waiting half
 * to accidentally keep.
 */

/**
 * Reads the preference and resolves the behavior one call will actually use.
 *
 * Read per call rather than once at module load: the preference can flip
 * mid-session, and a scroll that consulted a value captured at import time
 * would be answering a question asked before the app started.
 */
function resolveBehavior(requested: ScrollBehavior | undefined): ScrollBehavior {
  if (prefersReducedMotion()) return "auto";
  return requested ?? "smooth";
}

/**
 * `Element.scrollIntoView`, honouring the reduced-motion preference.
 *
 * Accepts a missing target and does nothing with it, because every call site
 * that reaches this is holding the result of a ref or a `getElementById` and
 * would otherwise spell the same `?.` out again on the way in.
 */
export function scrollIntoView(
  target: Element | null | undefined,
  options: ScrollIntoViewOptions = {},
): void {
  target?.scrollIntoView({ ...options, behavior: resolveBehavior(options.behavior) });
}

/** `Element.scrollTo`, honouring the reduced-motion preference. */
export function scrollTo(
  scroller: Element | null | undefined,
  options: ScrollToOptions,
): void {
  scroller?.scrollTo({ ...options, behavior: resolveBehavior(options.behavior) });
}

/** `Element.scrollBy`, honouring the reduced-motion preference. */
export function scrollBy(
  scroller: Element | null | undefined,
  options: ScrollToOptions,
): void {
  scroller?.scrollBy({ ...options, behavior: resolveBehavior(options.behavior) });
}
