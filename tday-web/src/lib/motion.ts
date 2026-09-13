import {
  DELAYS,
  DURATIONS,
  EASINGS,
  PRESS_SCALES,
  SPRINGS,
} from "@/generated/motion-tokens";

/**
 * The web's reading of the shared motion vocabulary.
 *
 * `src/generated/motion-tokens.ts` is written by `:shared:exportMotionTokens` from
 * `MotionTokens.kt` and carries raw values only — four bare numbers for an easing,
 * an integer for a duration. This module is the shape web code actually consumes:
 * a `cubic-bezier(…)` string an inline style can be handed, and plain millisecond
 * numbers for the timers that gate a JS-driven sequence, the way
 * `taskCompletionTiming.ts` and `surfaceTransitionTiming.ts` already hand theirs out.
 *
 * Nothing here restates a number. Every value is imported, so a rung that moves in
 * the source of truth moves here on the next export, and `./gradlew
 * :shared:verifyMotionTokens` fails CI if it did not. A literal typed into this file
 * would be a fourth client nobody generated and no gate can see.
 *
 * Most web motion should never reach for this module. The generated stylesheet puts
 * every rung on a `--tday-*` custom property, and `globals.css` turns those into
 * `duration-*` utilities and `ease-*` utilities for the two curves Tailwind lacks;
 * CSS that can name a token should name it there. These exports are for the values
 * that have to cross into JS.
 */

/** The four control points of a cubic-bezier, in CSS order. */
export type EasingCurve = readonly [number, number, number, number];

/** Every easing the vocabulary names. */
export type EaseName = keyof typeof EASINGS;

/** Formats a generated curve as the `cubic-bezier(…)` a style property takes. */
export function cubicBezier(curve: EasingCurve): string {
  return `cubic-bezier(${curve.join(", ")})`;
}

/**
 * The easings as CSS strings.
 *
 * Spelled out one key at a time rather than mapped over `EASINGS`, which would be
 * shorter and would type as `Record<string, string>`. Naming each key against
 * `Record<EaseName, …>` is what turns a sixth curve arriving in the source of truth
 * into a compile error here, instead of a token the web silently never offers.
 *
 * `gesture` is web-only by design: Android and iOS express the same intent with the
 * Gesture spring, which is a different thing under a shared name.
 */
export const EASE: Record<EaseName, string> = {
  standard: cubicBezier(EASINGS.standard),
  enter: cubicBezier(EASINGS.enter),
  exit: cubicBezier(EASINGS.exit),
  scene: cubicBezier(EASINGS.scene),
  gesture: cubicBezier(EASINGS.gesture),
};

/**
 * How long a motion runs, in milliseconds. The names are rungs rather than
 * adjectives — `change` is the user's own edit replayed in place, `emphasis` is
 * something changing where or how big it is — and `MotionTokens.kt` argues each one.
 *
 * Note what is deliberately absent: a helper that turns one of these into `"260ms"`.
 * A duration that ends up in a CSS string wants `var(--tday-duration-change)` or a
 * `duration-*` utility instead; only a timer wants the number. Offering both shapes
 * is how one rung comes to live in two places.
 */
export const DURATION_MS = DURATIONS;

/**
 * How long something waits before it starts, in milliseconds.
 *
 * Kept apart from [DURATION_MS] because a delay is not a duration: the two 320s
 * below are the same integer and not the same decision, and merging the namespaces
 * would make `emphasis` and `celebrationLead` look interchangeable to every reader
 * after this one.
 */
export const DELAY_MS = DELAYS;

/**
 * How far a surface squashes under a finger, by surface class — a bare ratio,
 * because that is what a `scale` style property takes.
 *
 * These are for the press a component drives itself. The CSS side does not read
 * them yet: `globals.css`'s blanket `:active` rule writes `scale: 0.985` inline,
 * which is byte-for-byte the Row rung. Pointing it at `var(--tday-press-row)`
 * belongs to the PR that takes press scales on, not to this one — three surface
 * classes against one global rule is a migration, not a substitution.
 */
export const PRESS_SCALE = PRESS_SCALES;

/**
 * The springs, carried but not yet spendable.
 *
 * Web has no spring runtime: the app animates with CSS transitions and keyframes,
 * and where the native clients reach for the Gesture spring, web reaches for the
 * Gesture easing — `MotionTokens.kt` says exactly that. They are re-exported anyway
 * so that the day web does gain a spring (a `linear()` approximation, or whatever
 * library lands) it starts from the vocabulary's numbers under the vocabulary's
 * names, rather than from a fresh pair invented at the call site.
 *
 * `response`/`damping` are SwiftUI's parameters and `stiffness` is Compose's;
 * `MotionTokens.Spring` proves at construction that the two describe one spring, so
 * a converter here would be re-deriving something already checked upstream.
 */
export const SPRING = SPRINGS;
