import { DURATION_MS } from "@/lib/motion";

/**
 * What a swipe on this app is made of: the slop it starts at, the resistance it
 * meets at a limit, the speed it was let go at, and the two settles that answer
 * a release.
 *
 * There are four swipeable surfaces on web — the calendar's pager and the three
 * task rows (scheduled, calendar, Anytime) — and until now each one carried its
 * own arithmetic. That is how they came to disagree: the pager learned to give
 * at its limits while the rows still stopped dead at theirs, and all four decided
 * a release on position alone, which is the one thing a finger never tells you on
 * its own. A flick is short and fast; a drag is long and slow; both end at a
 * position, and a rule that reads only the position cannot tell them apart.
 *
 * So this module holds the gesture and not the surface. Nothing here knows what
 * is being swiped: it takes numbers and gives numbers back, and the four call
 * sites keep their own thresholds, their own limits and their own reasons. Two
 * things follow from that which are worth saying out loud. The maths is testable
 * without a DOM (`tests/unit/swipe-gesture.test.ts`), and a fifth swipeable
 * surface starts from this vocabulary instead of a fifth set of constants.
 */

/** One reading of where the finger was, and when. */
type Sample = { x: number; t: number };

/**
 * Below this, a move is a hand steadying rather than a gesture starting.
 *
 * One number for every swipe on the app, which is not tidiness: the calendar's
 * grid sits directly above a scrolling task list whose rows swipe too, so the
 * two gestures are an inch apart on the same screen and a finger that means one
 * very often lands on the other first. Whichever of them claimed the axis sooner
 * would take swipes the other was about to get.
 */
export const AXIS_SLOP_PX = 8;

/**
 * How far back a release is measured.
 *
 * A release is one event and therefore has no speed of its own — speed is two
 * readings and the gap between them. Taking the last two would measure the
 * platform's event delivery as much as the finger (a browser coalescing moves
 * can put two of them a fraction of a millisecond apart), so the window is wide
 * enough to hold several moves and short enough that only the end of the gesture
 * is in it. The number is Android's `VelocityTracker` horizon, which is the same
 * quantity solved by people with far more device data than this repo has; it is
 * a measurement window rather than a motion anybody watches, so it is not a rung
 * and does not want one.
 */
const VELOCITY_WINDOW_MS = 100;

/**
 * Under this much elapsed time there is no velocity to read, only noise.
 *
 * One frame at 60 Hz — the same floor `docs/motion.md` builds the five-rung
 * ladder on, used here for the other half of the same fact: a finger cannot be
 * observed to do anything inside one frame, so a large distance divided by a
 * sub-frame gap is a measurement artefact and not a flick. Answering zero is the
 * conservative reading, and the one that leaves the release to its position.
 */
const VELOCITY_FLOOR_MS = 16;

/**
 * How far a pull travels once it is past a limit it cannot pass.
 *
 * Exponential and not linear, so the give approaches `give` and never reaches
 * it: the surface keeps answering the finger for as long as it is pulled, and
 * never once claims to be most of the way to something that is not there. A
 * linear give with a clamp on the end is the same defect as a hard stop, moved
 * a few pixels further out.
 *
 * @param beyond - How far past the limit the finger has asked to go.
 * @param give - The travel this limit is willing to spend in total. The caller
 *   owns it, because what a limit is worth is a property of the surface: a pager
 *   measures it against the distance that turns a page, a row against the width
 *   of the actions behind it.
 */
export function rubberBand(beyond: number, give: number): number {
  if (beyond <= 0 || give <= 0) return 0;
  return give * (1 - Math.exp(-beyond / give));
}

/** Collects a gesture's readings and answers what speed it ended at. */
export type SwipeSampler = {
  /** Records where the finger is. Cheap enough to call on every move. */
  sample(x: number, t: number): void;
  /** The last sample, and the gesture's closing speed in px/ms, signed. */
  release(x: number, t: number): number;
};

/**
 * One gesture's worth of readings.
 *
 * Deliberately per-gesture rather than per-surface: a sampler that outlived its
 * gesture would answer the next release with the speed of the last one, which is
 * precisely the class of bug the pager's `endGesture` exists to prevent on the
 * other refs.
 */
export function createSwipeSampler(): SwipeSampler {
  const samples: Sample[] = [];
  const keep = (x: number, t: number) => {
    samples.push({ x, t });
    while (samples.length > 1 && t - samples[0].t > VELOCITY_WINDOW_MS) samples.shift();
  };
  return {
    sample: keep,
    release(x, t) {
      keep(x, t);
      const first = samples[0];
      const last = samples[samples.length - 1];
      const elapsed = last.t - first.t;
      // A finger that stopped and then lifted has just dropped every older
      // sample out of the window, leaving the release alone in it — which reads
      // as no speed at all, and is exactly right. A hold is not a flick.
      if (elapsed < VELOCITY_FLOOR_MS) return 0;
      return (last.x - first.x) / elapsed;
    },
  };
}

/**
 * Where the finger was going to put this surface, rather than where it let go of
 * it.
 *
 * This is the whole of the velocity fix, and it is one rule instead of two. A
 * fast flick that stopped short of the threshold projects past it and commits;
 * a long drag that crept past the threshold and was already easing back projects
 * short of it and does not. Both of those are wrong under a position-only rule,
 * and both are wrong in the same way — the rule was reading the last frame of a
 * gesture as though it were the end of one.
 *
 * The horizon is `Quick` and not a constant of its own. `docs/motion.md` defines
 * that rung as the app answering a finger that is on it, and this is that same
 * question asked one frame after the finger left: how much of this gesture does
 * the app still owe? A number of its own here would be a second clock for a
 * single release, free to drift away from the settle that answers it.
 */
export function projectedRest(position: number, velocity: number): number {
  return position + velocity * DURATION_MS.quick;
}

/**
 * A surface going back to where it was, because the release did not carry it.
 *
 * Quick, because this is the tail of a gesture rather than a state change, and
 * an undoing that nobody is meant to watch go. The Gesture curve for the reason
 * `docs/motion.md` gives it: web has no spring runtime, and a surface continuing
 * under its own momentum after a finger lets go is exactly what it names — the
 * same intent Android and iOS spend the Gesture *spring* on at their own row
 * releases.
 */
export const SWIPE_SETTLE_HOME = "transform var(--tday-duration-quick) var(--tday-ease-gesture)";

/**
 * A surface finishing the trip the release committed it to.
 *
 * Emphasis, on two arguments that agree. Geometry, which is the second idiom
 * rule: the surface ends somewhere other than where it started, and that is the
 * rung anything changing position takes. And parity: Android and iOS release
 * this same row on the Gesture spring, whose response is 0.34 s, and Emphasis at
 * 320 is the nearest rung web can answer that with. The calendar's turned page
 * already arrives on exactly this pair.
 *
 * Longer than [SWIPE_SETTLE_HOME] on purpose, and that is the first idiom rule
 * read the right way round: the trip home is the one that undoes this, so it is
 * the shorter of the two.
 */
export const SWIPE_SETTLE_OPEN = "transform var(--tday-duration-emphasis) var(--tday-ease-gesture)";

/**
 * The same settle for a reader who has asked not to be moved.
 *
 * Not the absence of a transition, which would be the same thing said less
 * clearly to the next person reading the style: reduced motion removes the trip
 * and keeps the destination, and the destination here is the resting place the
 * release chose. Written as a clause rather than as `none` so it composes into
 * the same whitelist the other two do — a row's colours are not motion and go on
 * fading either way.
 */
export const SWIPE_SETTLE_INSTANT = "transform 0s";
