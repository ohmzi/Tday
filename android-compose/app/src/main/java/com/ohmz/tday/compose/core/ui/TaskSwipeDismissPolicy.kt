package com.ohmz.tday.compose.core.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The one swipe slot a screen has to give out, and who is holding it.
 *
 * A holder rather than a hoisted `String?`, and that is the whole point of the
 * type. `openSwipeTaskId` used to be a `var … by rememberSaveable` read inside
 * the screen's own composable body — the `by` delegate, the orphan cleanup, the
 * argument pass — and a `MutableState` read invalidates the scope that read it.
 * Every open and every close therefore recomposed a ~6000-line composable
 * including the whole `LazyColumn` content lambda, on a feed that can hold
 * hundreds of rows. That was survivable while the only way to close a row was to
 * tap one of its own pills; an outside tap, a scroll and a back press make it
 * three more ways to pay it.
 *
 * The object's identity never changes, so passing it down as a parameter changes
 * no row's arguments when the value inside it does. Nothing reads [openId] in
 * composition at all: the screen reads it from a side effect, each row observes
 * it through `snapshotFlow`, and the gesture handlers read and write it from
 * lambdas. Opening or closing a row now recomposes zero composables — which is
 * strictly better than the tree this replaced, not merely no worse.
 *
 * Remembered with `remember`, never `rememberSaveable`, and that is a
 * correctness fix as much as a shape change. A row's own
 * [TaskSwipeRevealState] is a plain `remember`
 * ([rememberTaskSwipeRevealState]), so a saved id came back after process death
 * naming a row that had rebuilt at `offsetX == 0f`: the screen believed a closed
 * row was open and the slot was held by nobody. Both halves being
 * non-saveable makes them agree by construction, and answers "is the row still
 * open after navigating away and back" with the only answer anybody wants —
 * no, because nobody returns to a screen expecting an armed Delete pill under
 * their thumb.
 */
@Stable
class TaskSwipeSlot {
    /** The id of the row whose actions are revealed, or `null` when none is. */
    var openId by mutableStateOf<String?>(null)
}

/**
 * Back closes the revealed row before it leaves the screen.
 *
 * A revealed row is a transient, modal-ish state on one row, and back is this
 * app's universal "undo the state I am in" — the handlers it sits beside spend
 * it on exactly that: a floater search, a scoped search, selection mode,
 * exit-to-launcher.
 *
 * WHERE IT IS CALLED IS PART OF THE BEHAVIOUR, and is the reason this is a
 * composable of its own rather than a `BackHandler` written inline four times.
 * `OnBackPressedDispatcher` gives priority to the most recently added *enabled*
 * callback, which in Compose means composition order: LAST WINS. Called before
 * a screen's existing handlers this one is shadowed by all of them and appears
 * to do nothing at all, which is the kind of thing that gets tidied upward later
 * by someone grouping the back handlers together. Call it after them.
 *
 * The other half of its own guard is [enabled], which every caller passes
 * `!selectionActive`: back must cancel a bulk selection before it starts
 * closing rows, because selection mode is the larger state to be got out of.
 *
 * A composable rather than a plain `BackHandler(slot.openId != null)` at the
 * call site for the reason [TaskSwipeSlot] exists: the read has to happen
 * *somewhere* in composition to be a `BackHandler` argument at all, and putting
 * it in a four-line composable means the invalidation it causes stops here
 * instead of taking a ~6000-line screen with it. Recomposing this flips one
 * `isEnabled` on a callback that is already registered; it neither re-registers
 * it nor moves it in the dispatcher, so the ordering above survives.
 */
@Composable
fun TaskSwipeSlotBackHandler(slot: TaskSwipeSlot, enabled: Boolean = true) {
    BackHandler(enabled = enabled && slot.openId != null) {
        slot.openId = null
    }
}

/**
 * Whether this row should shut, given who holds the screen's swipe slot.
 *
 * The whole dismissal decision, as one expression, so that it can be proven on a
 * bare JVM the way [TaskSwipeRevealState] and `shouldCelebrateEmptyState` are —
 * there is no emulator on this repository's gates and a policy that can only be
 * checked by hand is a policy that drifts. iOS and web carry the same function
 * under the same name and the same truth table.
 *
 * The clause that is deliberately *absent* is the one this function was written
 * to delete. Every row used to ask
 * `openSwipeTaskId != null && openSwipeTaskId != id && isOpen`, and that first
 * term meant the slot could be handed from one row to another but never
 * revoked: writing `null` closed nothing. Every dismissal this app now performs
 * — an outside tap, a scroll, back — is a write of `null`, so all of them would
 * have been silent no-ops. The tree already contained the proof, as a
 * workaround: entering bulk selection wrote `null` and then closed the row a
 * second time through a separate effect, because the first write did nothing.
 *
 * @param openRowId the id in the screen's [TaskSwipeSlot], `null` when the slot
 *   is free — which now means "everybody closes" rather than "nobody moves".
 * @param thisRowId the row asking. A row never closes itself out from under its
 *   own finger: the row that holds the slot is the row the user is working.
 * @param isOpenOrDragging from [TaskSwipeRevealState.isOpenOrDragging]. A row
 *   that is already home has nothing to close, and calling `close()` on it would
 *   start a spring to where it already is.
 */
internal fun shouldCloseSwipeRow(
    openRowId: String?,
    thisRowId: String,
    isOpenOrDragging: Boolean,
): Boolean = openRowId != thisRowId && isOpenOrDragging
