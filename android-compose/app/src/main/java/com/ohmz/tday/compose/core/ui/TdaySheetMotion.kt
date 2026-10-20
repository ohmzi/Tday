package com.ohmz.tday.compose.core.ui

import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.tween

/**
 * How a bottom sheet arrives and leaves: four specs, one per moving part.
 *
 * Android has two sheet mechanisms and only one of them is ours. `TdayModalBottomSheet`
 * hands the whole presentation to Material3, which animates its own scrim and its own
 * card on its own specs; this object is for the other one — the hand-built
 * `AnimatedVisibility`-inside-a-`Dialog` sheets (the create-task sheet, the create-list
 * sheet), where the scrim and the card are two separate composables and nothing makes
 * them agree unless something says so. That is what kept going wrong: the scrim had no
 * clock of its own. It was drawn and undrawn with the `Dialog` window, so it reached full
 * dim inside the window's own ~150 ms while the card was still most of the way through a
 * 320 ms arrival — and on the way out it could not leave until the host tore the `Dialog`
 * down, which `SheetDismissState` holds back until the card's exit has settled. The dim
 * led the card in and then stood over it after it had landed.
 *
 * The four specs are ported from iOS's `TdayBottomSheetMotion`, which is where this
 * language was first written down, and each one is put on the nearest rung rather than
 * transcribed:
 *
 * - **[scrimIn] is [TdayMotionTokens.Durations.Enter].** iOS runs 0.22, and its comment
 *   argues that number against the *keyboard's* ~0.25 s unwind — a coincidence of that
 *   platform's sheet, which lets go of the keyboard as it leaves. Android's sheet is
 *   pinned to the live IME inset exactly (`CreateTaskBottomSheet`'s height chain says
 *   why), so there is no unwind here to match and nothing arguing the rung down. The
 *   20 ms is inside a frame and a half either way.
 * - **[scrimOut] is [TdayMotionTokens.Durations.Enter] too.** iOS's scrimOut already
 *   *is* 0.20; its comment declines the token only because the token's name reads
 *   "arriving" and iOS had no second name to give it. This file does, so it takes it.
 * - **[cardIn] is [TdayMotionTokens.Durations.Emphasis].** A sheet crossing a screen
 *   height is position changing, and geometry is what puts a motion on this rung. It is
 *   also what both sheets already ran on, so the arrival is a zero-pixel change.
 * - **[cardOut] is [TdayMotionTokens.Durations.Change].** iOS's 0.24 falls between two
 *   rungs; Change is the nearer, and it is the one that keeps an exit from outlasting
 *   its own enter. This is the only spec here that a user can see change.
 *
 * The easings are split by direction, where the tree ran both halves on `Standard`. A card
 * arriving should settle rather than stop — [TdayMotionTokens.Easings.Enter], decelerate —
 * and a card leaving should commit rather than drift, which is
 * [TdayMotionTokens.Easings.Exit]; one symmetric curve in both directions is what makes a
 * dismissal read as a hesitation. The scrim takes the same pair because it is the same
 * surface's shadow, and two curves under one gesture is the mismatch this object removes.
 *
 * Generic factories rather than vals, for the reason [TdayMotionTokens.Springs] gives:
 * a Compose spec is typed by what it animates, and `slideInVertically` wants a spec over
 * `IntOffset` while the `fadeIn` beside it wants one over `Float`. A `TweenSpec<Float>`
 * held in a val would serve exactly half of each call site.
 */
object TdaySheetMotion {

    /** The dim arriving. */
    const val ScrimInMillis: Int = TdayMotionTokens.Durations.Enter

    /** The dim leaving, with the card rather than with the window. */
    const val ScrimOutMillis: Int = TdayMotionTokens.Durations.Enter

    /** The card crossing a screen height. */
    const val CardInMillis: Int = TdayMotionTokens.Durations.Emphasis

    /** The card going back down — shorter than [CardInMillis], and asserted to be. */
    const val CardOutMillis: Int = TdayMotionTokens.Durations.Change

    fun <T> scrimIn(): TweenSpec<T> = tween(
        durationMillis = ScrimInMillis,
        easing = TdayMotionTokens.Easings.Enter,
    )

    fun <T> scrimOut(): TweenSpec<T> = tween(
        durationMillis = ScrimOutMillis,
        easing = TdayMotionTokens.Easings.Exit,
    )

    fun <T> cardIn(): TweenSpec<T> = tween(
        durationMillis = CardInMillis,
        easing = TdayMotionTokens.Easings.Enter,
    )

    fun <T> cardOut(): TweenSpec<T> = tween(
        durationMillis = CardOutMillis,
        easing = TdayMotionTokens.Easings.Exit,
    )
}
