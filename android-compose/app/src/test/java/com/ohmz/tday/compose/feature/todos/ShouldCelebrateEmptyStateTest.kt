package com.ohmz.tday.compose.feature.todos

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Requirement 1's root-cause finding, pinned as tests: [shouldCelebrateEmptyState]
 * only ever looks at `itemsEmpty` (== `uiState.items.isEmpty()`), and Today mode's
 * `items` has never included overdue/Earlier tasks (`TodoRepository.buildTodosForMode`'s
 * `isTodayTodo` filter predates this feature). So the confetti gate was already
 * "zero pending today tasks" before requirement 2 existed -- it did not need to
 * change, and does not take an Earlier-aware parameter at all. These tests would
 * catch a future regression where someone folds overdue tasks into `items` to
 * satisfy requirement 2 and, in doing so, breaks requirement 1 by making the
 * empty check require zero overdue tasks too.
 *
 * The `cancelledAtMs` block at the bottom is the undo bug, pinned the same way.
 * Its first case is the screenshot: an UNDONE OVERDUE task, where `itemsEmpty`
 * is true on both sides of the arrival because this predicate excludes Earlier
 * on purpose -- so a fix written as "the scope stopped being empty" passes every
 * other test here and misses the only one that was reported. Every assertion in
 * that block was perturbed and seen to fail before being left green.
 */
class ShouldCelebrateEmptyStateTest {

    private val now = 100_000L
    private val window = 4_000L

    @Test
    fun `does not celebrate while items remain, no matter how recent the tap`() {
        assertFalse(
            shouldCelebrateEmptyState(
                itemsEmpty = false,
                lastCompletionAtMs = now,
                remoteEmptiedAtMs = 0L,
                cancelledAtMs = 0L,
                screenResumed = true,
                nowMs = now,
                windowMs = window,
            ),
        )
    }

    @Test
    fun `celebrates the tap that just emptied the list`() {
        assertTrue(
            shouldCelebrateEmptyState(
                itemsEmpty = true,
                lastCompletionAtMs = now,
                remoteEmptiedAtMs = 0L,
                cancelledAtMs = 0L,
                screenResumed = true,
                nowMs = now + 500,
                windowMs = window,
            ),
        )
    }

    @Test
    fun `stops celebrating once the completion window has elapsed`() {
        assertFalse(
            shouldCelebrateEmptyState(
                itemsEmpty = true,
                lastCompletionAtMs = now,
                remoteEmptiedAtMs = 0L,
                cancelledAtMs = 0L,
                screenResumed = true,
                nowMs = now + window + 1,
                windowMs = window,
            ),
        )
    }

    @Test
    fun `a plain arrival at an already-empty screen never celebrates`() {
        // lastCompletionAtMs == 0 -- this composable's own tap never fired --
        // and no remote signal either: opening an already-empty Today, or
        // deleting the last task, gets the plain illustration.
        assertFalse(
            shouldCelebrateEmptyState(
                itemsEmpty = true,
                lastCompletionAtMs = 0L,
                remoteEmptiedAtMs = 0L,
                cancelledAtMs = 0L,
                screenResumed = true,
                nowMs = now,
                windowMs = window,
            ),
        )
    }

    @Test
    fun `a remote completion celebrates only while the screen is resumed`() {
        assertFalse(
            "backgrounded or not-on-top should not burst confetti nobody sees",
            shouldCelebrateEmptyState(
                itemsEmpty = true,
                lastCompletionAtMs = 0L,
                remoteEmptiedAtMs = now,
                cancelledAtMs = 0L,
                screenResumed = false,
                nowMs = now + 100,
                windowMs = window,
            ),
        )
        assertTrue(
            shouldCelebrateEmptyState(
                itemsEmpty = true,
                lastCompletionAtMs = 0L,
                remoteEmptiedAtMs = now,
                cancelledAtMs = 0L,
                screenResumed = true,
                nowMs = now + 100,
                windowMs = window,
            ),
        )
    }

    @Test
    fun `is independent of Earlier -- itemsEmpty alone gates the burst`() {
        // No earlierItems parameter exists: Today mode's `items` has always
        // excluded overdue tasks (see TodoRepository.buildTodosForMode), so
        // this function -- and requirement 1 -- never needed to know about
        // Earlier at all, before or after requirement 2's section landed.
        assertTrue(
            shouldCelebrateEmptyState(
                itemsEmpty = true,
                lastCompletionAtMs = now,
                remoteEmptiedAtMs = 0L,
                cancelledAtMs = 0L,
                screenResumed = true,
                nowMs = now,
                windowMs = window,
            ),
        )
    }

    // --- The cancel: a pending row arrived, so the scope is not finished ----

    @Test
    fun `an undone OVERDUE task cancels the burst even though the scope still reads empty`() {
        // The reported bug, and the one a naive fix misses. The restored row is
        // overdue, so it lands in Earlier -- which `itemsEmpty` excludes by
        // design (requirement 4: finishing today's work while overdue tasks wait
        // still earns the payoff). Nothing about emptiness moves. The only thing
        // that happened is that a row arrived, and that is what ends this.
        assertFalse(
            "an undone overdue row leaves itemsEmpty true; only the arrival can end the burst",
            shouldCelebrateEmptyState(
                itemsEmpty = true,
                lastCompletionAtMs = now,
                remoteEmptiedAtMs = 0L,
                cancelledAtMs = now + 900,
                screenResumed = true,
                nowMs = now + 1_000,
                windowMs = window,
            ),
        )
    }

    @Test
    fun `an undo landing in the same millisecond as its own completion still cancels`() {
        // `>=`, not `>`. An undo always follows the completion it undoes, so a
        // stamp that ties with it has to win: on a monotonic clock read twice in
        // a row, a tie is the ordinary case rather than the strange one.
        assertFalse(
            shouldCelebrateEmptyState(
                itemsEmpty = true,
                lastCompletionAtMs = now,
                remoteEmptiedAtMs = 0L,
                cancelledAtMs = now,
                screenResumed = true,
                nowMs = now,
                windowMs = window,
            ),
        )
    }

    @Test
    fun `a new completion after a cancel re-opens the window without clearing anything`() {
        // Why the cancel is a stamp compared against the opening stamps rather
        // than a reset of them: the later completion is simply the newer stamp,
        // so the window re-opens with no mutation, no effect, and no ordering
        // question left over for the next reader.
        assertTrue(
            shouldCelebrateEmptyState(
                itemsEmpty = true,
                lastCompletionAtMs = now + 2_000,
                remoteEmptiedAtMs = 0L,
                cancelledAtMs = now + 1_000,
                screenResumed = true,
                nowMs = now + 2_100,
                windowMs = window,
            ),
        )
    }

    @Test
    fun `a cancel ends a remote emptying just as it ends this screen's own tap`() {
        // A collaborator emptying the list still celebrates (the case above it in
        // this file); a collaborator -- or a sync, or a remote undo -- putting
        // something back cancels, on the same comparison. The remote stamp is the
        // later of the two openings here, and the cancel still has to beat it.
        assertFalse(
            shouldCelebrateEmptyState(
                itemsEmpty = true,
                lastCompletionAtMs = 0L,
                remoteEmptiedAtMs = now,
                cancelledAtMs = now + 50,
                screenResumed = true,
                nowMs = now + 100,
                windowMs = window,
            ),
        )
    }

    @Test
    fun `a cancel older than the completion it precedes changes nothing`() {
        // The cancel is never read as "a celebration is forbidden from now on".
        // A stamp left behind by an earlier arrival is simply the older one, and
        // the completion that came after it celebrates exactly as before.
        assertTrue(
            shouldCelebrateEmptyState(
                itemsEmpty = true,
                lastCompletionAtMs = now,
                remoteEmptiedAtMs = 0L,
                cancelledAtMs = now - 1,
                screenResumed = true,
                nowMs = now + 500,
                windowMs = window,
            ),
        )
    }

    @Test
    fun `a screen that has never had a row arrive is the case every test above ran`() {
        // cancelledAtMs == 0 is "no arrival has ever been seen on this screen",
        // and it must leave the gate byte-for-byte as it was -- which is what the
        // 0L threaded through every case above this block is asserting, once,
        // here, on the one case that would be a silent regression if the sentinel
        // were ever read as a real timestamp.
        assertTrue(
            shouldCelebrateEmptyState(
                itemsEmpty = true,
                lastCompletionAtMs = now,
                remoteEmptiedAtMs = 0L,
                cancelledAtMs = 0L,
                screenResumed = true,
                nowMs = now,
                windowMs = window,
            ),
        )
    }
}
