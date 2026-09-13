package com.ohmz.tday.compose.core.ui

import android.view.View
import androidx.core.view.HapticFeedbackConstantsCompat
import androidx.core.view.ViewCompat

/**
 * The app's haptic vocabulary — eight names for eight *events*, not eight waveforms.
 *
 * Before this file, 55 of the app's 64 haptic call sites fired the same
 * `HapticFeedbackConstantsCompat.CLOCK_TICK`, which meant deleting a task, ticking one off,
 * picking a row up under the finger and tapping a bar button were all literally the same
 * buzz. That is not a vocabulary, it is one haptic wearing eight hats, and the hand cannot
 * tell those events apart. It also made every new call site a coin flip, because there was
 * no decision written down anywhere to copy.
 *
 * So call sites name the **event**, never the constant. `destructive(view)` is a decision
 * that survives; `performHapticFeedback(view, LONG_PRESS)` is a constant somebody once
 * picked. When the constant for an event turns out wrong, it is wrong in one place here
 * instead of in the twelve screens that copied it.
 *
 * ### The names
 *
 * `buttonPress` · `selection` · `toggle` · `completion` · `destructive` · `dragPickUp` ·
 * `dragDrop` · `reveal`
 *
 * iOS mirrors these names exactly (`HapticManager`), so a reviewer comparing the two
 * clients compares events rather than translating `UIImpactFeedbackGenerator` styles into
 * `HapticFeedbackConstantsCompat` ints in their head.
 *
 * ### Why every name here has a call site
 *
 * Two more names have an agreed constant and are deliberately **not** declared, because
 * nothing calls them yet:
 *
 * - `rejection` → `REJECT` — an action refused: an invalid drop, a copy that failed.
 * - `boundary` → `SEGMENT_FREQUENT_TICK` — a detent crossed *during* a continuous gesture,
 *   fired repeatedly inside one drag, so it has to be the lightest repeatable tick.
 *
 * They are written down rather than declared on purpose. A documented haptic with zero call
 * sites is the exact defect the iOS side of this work is fixing (four of them there), and a
 * function nobody calls reads as coverage while providing none. Add one when the call site
 * that needs it arrives, in the same commit.
 *
 * ### Compatibility
 *
 * `ViewCompat.performHapticFeedback` resolves each constant against the running API level
 * and substitutes the nearest older equivalent, so the API 30/34 constants below are safe
 * down to the app's `minSdk` and no call site needs a version check.
 */
object TdayHaptics {

    /**
     * A plain control was tapped: a bar button, a FAB, a card, an action tile, a sheet row,
     * a swipe action, a dialog's confirm button.
     *
     * `CLOCK_TICK` — the lightest acknowledgement the platform has. Tapping a control is not
     * an event, it is the cost of using the app, and it must stay under the ones that are.
     */
    fun buttonPress(view: View) {
        ViewCompat.performHapticFeedback(view, HapticFeedbackConstantsCompat.CLOCK_TICK)
    }

    /**
     * The highlight moved: a tab or segment changed, an option was picked out of a selector,
     * a colour or icon swatch was chosen, a row entered or left a multi-select.
     *
     * `SEGMENT_TICK` — the platform's "the selection advanced one notch", which is the detent
     * feel a segmented control or picker is supposed to have; distinct from [buttonPress] so a
     * tab that *changed* does not feel the same as a tab that was already selected.
     */
    fun selection(view: View) {
        ViewCompat.performHapticFeedback(view, HapticFeedbackConstantsCompat.SEGMENT_TICK)
    }

    /**
     * A two-state control flipped, and [on] says which way: a switch row, un-ticking a task
     * that was already complete.
     *
     * `TOGGLE_ON` / `TOGGLE_OFF` — the only pair Android ships whose two directions differ in
     * the hand, which is the whole point: turning something on must not feel identical to
     * turning it off, or the haptic carries no information the screen did not already carry.
     */
    fun toggle(view: View, on: Boolean) {
        ViewCompat.performHapticFeedback(
            view,
            if (on) {
                HapticFeedbackConstantsCompat.TOGGLE_ON
            } else {
                HapticFeedbackConstantsCompat.TOGGLE_OFF
            },
        )
    }

    /**
     * The thing the user set out to do landed: a task ticked off, the day's last task cleared,
     * an install accepted.
     *
     * `CONFIRM` — the platform's "that worked" pulse, heavier and rounder than a tick. This is
     * the one haptic in the app that is allowed to feel like a reward.
     */
    fun completion(view: View) {
        ViewCompat.performHapticFeedback(view, HapticFeedbackConstantsCompat.CONFIRM)
    }

    /**
     * Something is being destroyed: a task deleted, a list deleted.
     *
     * `LONG_PRESS` — the heaviest single thud in the set. Deletion should cost more in the hand
     * than the edit button sitting 16dp away from it, so that a mis-tap on the wrong swipe
     * action is felt before it is read.
     *
     * It fires at the moment of destruction, not on the button that asks about it: a control
     * that only opens a confirmation is a [buttonPress], and the thud belongs to the confirm
     * that commits.
     */
    fun destructive(view: View) {
        ViewCompat.performHapticFeedback(view, HapticFeedbackConstantsCompat.LONG_PRESS)
    }

    /**
     * A row left the list and is now under the finger — the long press that starts a drag.
     *
     * `DRAG_START` — named for this exact moment; it is the platform's lift, and it reads as
     * something coming loose rather than as a button firing.
     */
    fun dragPickUp(view: View) {
        ViewCompat.performHapticFeedback(view, HapticFeedbackConstantsCompat.DRAG_START)
    }

    /**
     * A drag committed: a task dropped onto a new day or a new hour.
     *
     * `CONFIRM` — the same pulse as [completion], deliberately. A drop that stuck and a task
     * that finished are the same class of event to the user, and the app should not invent a
     * distinction its own screens do not make.
     */
    fun dragDrop(view: View) {
        ViewCompat.performHapticFeedback(view, HapticFeedbackConstantsCompat.CONFIRM)
    }

    /**
     * A hidden surface came out: the feed dock expanded, a menu opened.
     *
     * `CONTEXT_CLICK` — Android's constant for "a context surface appeared", which is exactly
     * this. It is sharper than a tick, so expanding the dock does not feel like the tab switch
     * that shares the same target.
     */
    fun reveal(view: View) {
        ViewCompat.performHapticFeedback(view, HapticFeedbackConstantsCompat.CONTEXT_CLICK)
    }
}
