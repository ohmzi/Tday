package com.ohmz.tday.compose.core.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring

/**
 * The motion vocabulary, in Compose types.
 *
 * [TdayMotionTokensGenerated] carries the raw numbers exported from
 * `shared/.../motion/MotionTokens.kt`; this file is the only place Android turns
 * them into things a call site can pass to an animation. Nothing here restates a
 * value — every number is read from the generated object, because a literal
 * copied into this file is exactly the drift the codegen and its CI gate exist
 * to prevent, and it would be invisible: the gate compares the generated file to
 * its source, not this one to either.
 *
 * Not to be confused with [rememberTdayMotionEnabled] in `TdayMotion.kt`, which
 * answers whether decorative motion should play at all. This answers what it
 * should look like when it does.
 *
 * Durations stay as millis rather than pre-built tweens, and springs are
 * functions rather than vals, for the same reason: a Compose animation spec is
 * typed by what it animates, so a `TweenSpec<Float>` is unusable on the `Dp` and
 * `IntOffset` sites that need the same rung. A surface that only served `Float`
 * would send every other call site back to re-typing the numbers. Pairing a
 * duration with an easing stays a call-site decision — the vocabulary fixes the
 * rungs and the curves, not which curve a given motion runs on.
 *
 * The normative table, the site counts each rung stands on, and the rule for
 * when a value is deliberately *not* a token live in `docs/motion.md`.
 */
object TdayMotionTokens {

    /** How long a motion runs. Five rungs; see `docs/motion.md` for what each is for. */
    object Durations {
        /** Press feedback, a hint retracting, a row dropping out. */
        const val Quick: Int = TdayMotionTokensGenerated.Durations.Quick

        /**
         * One element arriving, or a control changing state under its own steam.
         * The rung to reach for when a motion has no reason to be another length.
         */
        const val Enter: Int = TdayMotionTokensGenerated.Durations.Enter

        /** The user's own edit replayed in place, with nothing changing position. */
        const val Change: Int = TdayMotionTokensGenerated.Durations.Change

        /** Position or size changing: a row taking its new slot, a sheet arriving. */
        const val Emphasis: Int = TdayMotionTokensGenerated.Durations.Emphasis

        /**
         * The empty-state illustration rising or sinking. The longest motion the
         * app plays, and the only one at this length — NOT for route or tab
         * handovers, which are [Durations.Enter].
         */
        const val Scene: Int = TdayMotionTokensGenerated.Durations.Scene
    }

    /**
     * How long something waits before it starts.
     *
     * Separate from [Durations] because the two are not interchangeable even
     * where they carry the same number: [PlacementLead] is [Durations.Emphasis]
     * by construction, [CelebrationLead] only happens to equal it today.
     */
    object Delays {
        /** How long a feed holds its celebration back while displaced rows take their new slots. */
        const val PlacementLead: Int = TdayMotionTokensGenerated.Delays.PlacementLead

        /** How long the confetti has the screen to itself before the scene comes up behind it. */
        const val CelebrationLead: Int = TdayMotionTokensGenerated.Delays.CelebrationLead
    }

    /**
     * The curves, built from the named generated control points rather than by
     * index — `CubicBezierEasing(a, b, c, d)` takes CSS `(x1, y1, x2, y2)` order,
     * and naming each argument is what keeps that true through a regeneration.
     *
     * [Standard], [Enter] and [Exit] are byte-identical to Compose's
     * `FastOutSlowInEasing`, `LinearOutSlowInEasing` and `FastOutLinearInEasing`;
     * the built-ins are left alone at their call sites for that reason, and
     * [TdayMotionTokensTest] pins the equality so a regeneration that broke it
     * would fail rather than silently re-time every one of them.
     *
     * There is no `Gesture` curve here. It exists in the vocabulary as a web-only
     * easing; Android says the same thing with [Springs.gesture], which is a
     * different mechanism under a shared name and the right one on this platform.
     */
    object Easings {
        /** Both ends eased. The unmarked curve. */
        val Standard: Easing = CubicBezierEasing(
            a = TdayMotionTokensGenerated.Easings.StandardX1,
            b = TdayMotionTokensGenerated.Easings.StandardY1,
            c = TdayMotionTokensGenerated.Easings.StandardX2,
            d = TdayMotionTokensGenerated.Easings.StandardY2,
        )

        /** Decelerate. Something arriving, which should settle rather than stop. */
        val Enter: Easing = CubicBezierEasing(
            a = TdayMotionTokensGenerated.Easings.EnterX1,
            b = TdayMotionTokensGenerated.Easings.EnterY1,
            c = TdayMotionTokensGenerated.Easings.EnterX2,
            d = TdayMotionTokensGenerated.Easings.EnterY2,
        )

        /** Accelerate. Something leaving, which should commit rather than drift off. */
        val Exit: Easing = CubicBezierEasing(
            a = TdayMotionTokensGenerated.Easings.ExitX1,
            b = TdayMotionTokensGenerated.Easings.ExitY1,
            c = TdayMotionTokensGenerated.Easings.ExitX2,
            d = TdayMotionTokensGenerated.Easings.ExitY2,
        )

        /** Emphasised decelerate. Pairs with [Durations.Scene], and nothing else. */
        val Scene: Easing = CubicBezierEasing(
            a = TdayMotionTokensGenerated.Easings.SceneX1,
            b = TdayMotionTokensGenerated.Easings.SceneY1,
            c = TdayMotionTokensGenerated.Easings.SceneX2,
            d = TdayMotionTokensGenerated.Easings.SceneY2,
        )
    }

    /**
     * The springs, as factories.
     *
     * Generic because a `SpringSpec<T>` only animates the `T` it was built for,
     * and these three are wanted on `Float`, `Dp` and `IntOffset` alike. The
     * `visibilityThreshold` stays a parameter rather than a token: how close is
     * close enough to stopped is a question about the units being animated, not
     * about the spring.
     *
     * Compose fixes mass = 1, so stiffness alone fixes the frequency; the
     * matching SwiftUI `response` for each of these lives in the shared source
     * and is checked there, not here.
     */
    object Springs {
        /** Confirmation dialogs, selector overlays, a control committing to a new state. */
        fun <T> snappy(visibilityThreshold: T? = null): SpringSpec<T> = spring(
            dampingRatio = TdayMotionTokensGenerated.Springs.SnappyDamping,
            stiffness = TdayMotionTokensGenerated.Springs.SnappyStiffness,
            visibilityThreshold = visibilityThreshold,
        )

        /** A surface continuing under its own momentum after a finger lets go. */
        fun <T> gesture(visibilityThreshold: T? = null): SpringSpec<T> = spring(
            dampingRatio = TdayMotionTokensGenerated.Springs.GestureDamping,
            stiffness = TdayMotionTokensGenerated.Springs.GestureStiffness,
            visibilityThreshold = visibilityThreshold,
        )

        /** Something heavy coming to rest — a dock, a bar, a sheet finding its height. */
        fun <T> settle(visibilityThreshold: T? = null): SpringSpec<T> = spring(
            dampingRatio = TdayMotionTokensGenerated.Springs.SettleDamping,
            stiffness = TdayMotionTokensGenerated.Springs.SettleStiffness,
            visibilityThreshold = visibilityThreshold,
        )
    }

    /**
     * How far a surface squashes under a finger, by surface class. Smaller
     * surfaces move further, because the same absolute travel reads as a bigger
     * gesture on a bar button than on a full-width row.
     *
     * A scale that is multiplied by something else — a swipe action scaling in
     * from its slot, say — is not on this token however close the number looks;
     * `docs/motion.md` has the rule and the two Android sites it excludes.
     */
    object PressScales {
        /** Bar buttons. The FAB is NOT on this token yet — see `docs/motion.md`. */
        const val Bar: Float = TdayMotionTokensGenerated.PressScales.Bar

        /** Cards and tiles. */
        const val Card: Float = TdayMotionTokensGenerated.PressScales.Card

        /** Full-width rows, where more travel would read as the list moving. */
        const val Row: Float = TdayMotionTokensGenerated.PressScales.Row
    }
}
