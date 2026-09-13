import { DURATION_MS, EASE } from "@/lib/motion";

/**
 * Web's reading of `TdayFeedItemMotion` — the one set of specs a task feed moves by.
 *
 * The Android object
 * (`android-compose/app/src/main/java/com/ohmz/tday/compose/core/ui/TdayFeedItemMotion.kt`)
 * is the original and carries the argument in full: a feed's rows and everything a
 * row's departure displaces have to travel on the same clock, or the screen comes
 * apart at the moment it should feel finished. Until `useRowPlacement` there was no
 * such clock on web at all — every neighbour of a removed row was put in its new slot
 * in a single frame — so this module exists to give the web half the same three
 * motions under the same three names, rather than a fourth set invented per call site.
 *
 * Nothing here copies a number out of the Kotlin file. Those constants are
 * hand-written literals the vocabulary has since caught up with, and restating
 * `190`/`320`/`150` on this side would mint a source of truth that neither
 * `verifyMotionTokens` nor `motion-parity.test.ts` can see. Each export names the
 * constant it mirrors instead, so the two files can be read against each other and a
 * rung that moves in `MotionTokens.kt` moves on both clients at once.
 *
 * One constant is deliberately not mirrored. `CelebrationStartDelayMillis` is how long
 * a feed that hosts its empty state inline holds the burst back, and Android derives it
 * from `PlacementMillis` because it exists to outlast exactly that travel. Web has the
 * same wait — `AllTasksTimelineContainer` and `NativeFloaterTaskHomeDashboard` hand it
 * to `EmptyState` as `celebrationStartDelayMs` — but spells it `DELAY_MS.placementLead`,
 * which is the vocabulary's own name for it and is `Emphasis` by construction upstream.
 * A constant here would be a third spelling of one number, and the one most likely to be
 * retimed alone.
 */

/**
 * An item arriving — mirrors `TdayFeedItemMotion.FadeInMillis`.
 *
 * The one place the mirror is not a copy: Android's constant is 190 and the Enter rung
 * is 200. `docs/motion.md` settled that deliberately — 190 is two hand-written Android
 * tweens against 200's fifty-seven sites across two clients — which makes the Android
 * literal the one that moves, not this.
 */
export const FEED_ITEM_FADE_IN_MS = DURATION_MS.enter;

/**
 * An item taking a new slot, and everything moved by that — mirrors
 * `TdayFeedItemMotion.PlacementMillis`.
 *
 * Emphasis rather than Change because the boundary is geometry and not importance
 * (`docs/motion.md`'s second idiom rule): a slot is a position. `useRowPlacement` is
 * the web surface that plays it, the way `Modifier.animateItem` plays `Placement` on
 * Android.
 */
export const FEED_ITEM_PLACEMENT_MS = DURATION_MS.emphasis;

/**
 * An item leaving — mirrors `TdayFeedItemMotion.FadeOutMillis`. Shorter than the
 * arrival, which is the first idiom rule and the reason Android's own comment gives:
 * an absence should not linger.
 *
 * No web surface plays this yet, and the reason is worth writing down: a ticked row
 * leaves on the staged check-off sequence (`taskCompletionTiming.ts`), a longer
 * choreographed departure that fades the ink and then shuts the box. This is the plain
 * fade for a row that goes for some other reason — a filter, a remote delete — and it
 * is carried ahead of its caller for the same reason `motion.ts` carries the springs
 * web cannot spend yet: so the day something needs it, it starts from the vocabulary.
 */
export const FEED_ITEM_FADE_OUT_MS = DURATION_MS.quick;

/**
 * The curve all three take. Android writes `FastOutSlowInEasing`, which `docs/motion.md`
 * proves byte-identical to `Standard` — the unmarked curve, both ends eased.
 *
 * Deliberately one curve and not three: an arrival, a travel and a departure timed on
 * the same easing read as one feed behaving consistently, where three curves read as
 * three animations that happen to share a list.
 */
export const FEED_ITEM_EASING = EASE.standard;
