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
                screenResumed = true,
                nowMs = now,
                windowMs = window,
            ),
        )
    }
}
