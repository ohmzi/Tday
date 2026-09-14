package com.ohmz.tday.compose.core.ui

import android.content.Context
import android.os.Build
import android.view.accessibility.AccessibilityManager

/**
 * How long a toast stays up for a user who has asked for nothing — the number every
 * window in this file is measured up from, and the only one this app used to have.
 *
 * Four seconds to read something and eight to reach a button are not neutral numbers.
 * Someone driving the phone with a switch, a head pointer or a screen reader can spend
 * most of that second window simply arriving at the control: TalkBack has to be told the
 * toast is there, reach it, announce it and be told to activate it, and a switch scanner
 * has to come round to it. The one toast in this app that carries a button is the Undo on
 * a delete — so the person least able to press it in time was the one the window was
 * shortest for, and what they lose by missing it is their data. That is why this file
 * exists, and why it is a correctness bug rather than a comfort setting.
 *
 * Android already knows the answer, because the user gave it to Settings under "Time to
 * take action". Ask [AccessibilityManager.getRecommendedTimeoutMillis] for it rather than
 * reading `ACCESSIBILITY_INTERACTIVE_UI_TIMEOUT_MS`: the framework takes the larger of
 * our number and the user's, does it separately for interactive and non-interactive
 * content, and folds in a timeout an accessibility service asked for on the user's behalf
 * — which is where a value meaning "do not take it away at all" comes from, the Settings
 * ladder itself stopping at two minutes. Reading the setting directly would reproduce two
 * of those three rules and miss the third.
 *
 * None of this is motion and none of it scales with motion: a timeout is time the user
 * needs, not time they spend watching something move, so it is deliberately not
 * [scaledDelay]'s business (see its doc) and a device at 0x still gets the whole window.
 */
const val TOAST_AUTO_DISMISS_SHORT_MS = 4_000L

/** The same base, for a toast that also offers a button to press. */
const val TOAST_AUTO_DISMISS_WITH_ACTION_MS = 8_000L

/**
 * The longest a control that takes itself away is allowed to stay, however long was
 * asked for.
 *
 * Not a second-guess of the user's setting — it is the limit of what this app can
 * honestly promise. The Undo button is an offer to put something back, and the offer is
 * only good while the delete is still staged and un-committed; everything below the
 * ceiling is honoured exactly, including every value Settings' own ladder can ask for,
 * which tops out here. Past it the number is not a duration at all but a service saying
 * "never", and a delete that never commits is a row that silently comes back on the next
 * sync. So an interactive window is bounded and an informational one is not: the second
 * is only text, and text can sit there as long as the user wants it to.
 */
internal const val INTERACTIVE_TIMEOUT_CEILING_MS = 120_000L

/**
 * How far the staged delete outlives the button that undoes it.
 *
 * The invariant this protects is that the Undo can never be tapped after the commit has
 * already run — that tap resolves to nothing at all, so the user is told their delete was
 * reversed by a button that did not reverse it. Half a second of slack is enough because
 * both timers start in the same frame; what used to hold it up was two hardcoded numbers
 * that happened to be 500 apart, which stopped being true the moment one of them started
 * following a setting.
 */
internal const val UNDO_COMMIT_GRACE_MS = 500L

/**
 * How long a control the user has to reach stays reachable, given what it would have
 * stayed for anyone who asked for nothing.
 *
 * This is the general question and there are two of it in the app — the Undo toast, and
 * the root feed dock, which opens on a tap and takes itself away again before the user
 * has picked a tab. Both are a thing that appears, waits, and leaves whether or not it
 * was used, which is exactly what "Time to take action" is the user's answer to.
 * Always finite — see [INTERACTIVE_TIMEOUT_CEILING_MS].
 */
fun interactiveTimeoutMillis(context: Context, baseMillis: Long): Long = boundedTimeout(
    recommendedTimeoutMillis(context, baseMillis, hasAction = true),
    baseMillis,
)

/**
 * The window for a toast that only says something. `null` means the user asked for no
 * timeout, and the toast stays until it is dismissed — which is why [TdayToastCard]
 * carries a `dismiss` semantics action: a toast that does not leave on its own has to be
 * reachable by whatever the user is driving the phone with, not only by the drag gesture.
 */
fun informationalToastTimeoutMillis(context: Context): Long? = timeoutOrNever(
    recommendedTimeoutMillis(context, TOAST_AUTO_DISMISS_SHORT_MS, hasAction = false),
    TOAST_AUTO_DISMISS_SHORT_MS,
)

/** The window for a toast that offers a button. */
fun actionToastTimeoutMillis(context: Context): Long =
    interactiveTimeoutMillis(context, TOAST_AUTO_DISMISS_WITH_ACTION_MS)

/**
 * How long [UndoableDeleteCoordinator] holds a staged delete before committing it.
 *
 * Derived from the toast's own window rather than declared beside it, so the two cannot
 * drift: whatever the user's setting stretches the Undo button's life to, the thing it
 * would put back is still there.
 */
fun undoCommitDelayMillis(context: Context): Long =
    undoCommitDelayFor(actionToastTimeoutMillis(context))

/**
 * The arithmetic half of [undoCommitDelayMillis], split out so the derivation can be
 * tested without a device — which is the half that can silently go back to being a
 * constant, and the half nobody would see go wrong until an Undo did nothing.
 */
internal fun undoCommitDelayFor(toastTimeoutMillis: Long): Long =
    toastTimeoutMillis + UNDO_COMMIT_GRACE_MS

/**
 * What the platform recommends for this surface, or [baseMillis] where it cannot say.
 *
 * The flags describe the content, which is what the API asks for: every one of these
 * surfaces is text or a control, and the control flag is the one that reaches the
 * *interactive* half of the setting, the half the user set because they need time to
 * press things.
 *
 * API 29 is where the setting was introduced; below it there is nothing to ask and the
 * base value is already the whole answer.
 */
private fun recommendedTimeoutMillis(
    context: Context,
    baseMillis: Long,
    hasAction: Boolean,
): Long {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return baseMillis
    val manager = context.getSystemService(AccessibilityManager::class.java) ?: return baseMillis
    val flags = if (hasAction) {
        AccessibilityManager.FLAG_CONTENT_TEXT or AccessibilityManager.FLAG_CONTENT_CONTROLS
    } else {
        AccessibilityManager.FLAG_CONTENT_TEXT
    }
    return manager.getRecommendedTimeoutMillis(baseMillis.toInt(), flags).toLong()
}

/**
 * The informational window: the recommendation, or `null` where it is a "never".
 *
 * Floored at [baseMillis] — as is [boundedTimeout] — because this whole file exists to
 * give a user *more* time. The framework's own implementation returns max(ours, theirs)
 * and so can only ever lengthen ours; the floor is here for the build that does not,
 * where handing back less would take time away from exactly the person it was asked on
 * behalf of.
 */
internal fun timeoutOrNever(recommendedMillis: Long, baseMillis: Long): Long? {
    val window = recommendedMillis.coerceAtLeast(baseMillis)
    return if (window > INTERACTIVE_TIMEOUT_CEILING_MS) null else window
}

/** The interactive window: the same, except that past the ceiling "never" is not on offer. */
internal fun boundedTimeout(recommendedMillis: Long, baseMillis: Long): Long =
    recommendedMillis.coerceIn(baseMillis, INTERACTIVE_TIMEOUT_CEILING_MS)
