package com.ohmz.tday.compose.feature.todos

import com.ohmz.tday.compose.core.model.TodoListMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Whether the timeline's rows carry `animateItem` from the screen's first composition.
 *
 * The rows of a tile's screen used to arrive one frame after it was first drawn, and every one of
 * them faded in as the zoom landed. `TodosRoute` now loads the cache before the state is first read,
 * so a cached workspace composes with its rows already there. What is left for this gate is not to
 * undo that on Today: a scope that holds `animateItem` back for a frame makes every row fade in,
 * because on Compose 1.7.6 a measure with no animated item resets the item animator's key map to
 * empty. So Today may animate from the first composition once its snapshot is in hand, and only a
 * Today still waiting for its first snapshot keeps the hold.
 *
 * Pinned as a function for this package's usual reason: `TodoListScreen` has no Compose UI test,
 * and a guard written inline in its body is a decision nothing can check. That the rows present at
 * a list's first measure do not fade was confirmed on an emulator (the ledger row has how).
 */
class TimelineAnimationsGateTest {

    @Test
    fun `should animate from the first composition in every scope but Today`() {
        TodoListMode.entries.filter { it != TodoListMode.TODAY }.forEach { mode ->
            assertTrue("$mode, before its snapshot", timelineAnimationsInitiallyReady(mode, hasHydratedSnapshot = false))
            assertTrue("$mode, with its snapshot", timelineAnimationsInitiallyReady(mode, hasHydratedSnapshot = true))
        }
    }

    @Test
    fun `should animate Today from the first composition once its snapshot is in hand`() {
        assertTrue(timelineAnimationsInitiallyReady(TodoListMode.TODAY, hasHydratedSnapshot = true))
    }

    @Test
    fun `should hold Today back while its first snapshot is still on its way`() {
        assertFalse(timelineAnimationsInitiallyReady(TodoListMode.TODAY, hasHydratedSnapshot = false))
    }
}
