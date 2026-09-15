package com.ohmz.tday.compose.feature.todos

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The other half of the undo fix, pinned: [pendingRowArrived] is what decides
 * that a row came back, and `TodoListScreen.shouldCelebrateEmptyState` is what
 * does something about it.
 *
 * Every case here is a way of saying the same thing -- the cancel is an
 * ARRIVAL, counted across all buckets, and not the emptiness predicate read
 * backwards. The first test is the reported bug and the one an emptiness mirror
 * gets wrong: both sides non-empty, both sides "finished" by the screen's own
 * Earlier-excluding predicate, and the only evidence that anything happened is
 * that the total went up by one.
 */
class PendingRowArrivedTest {

    @Test
    fun `an undone overdue task is an arrival even though nothing about the screen emptied`() {
        // Today, mid-celebration: the scope's own bucket is empty and stays
        // empty, and the row comes back into Earlier. `items` is 0 on both
        // sides; `earlierItems` goes 3 -> 4. An implementation that compared
        // `items.isEmpty()` before and after -- the obvious one -- sees nothing
        // at all here, which is exactly the burst the user watched play out over
        // a restored row.
        assertTrue(
            pendingRowArrived(
                previousItems = 0,
                previousEarlierItems = 3,
                nextItems = 0,
                nextEarlierItems = 4,
            ),
        )
    }

    @Test
    fun `an undone task on the plain path is an arrival too`() {
        assertTrue(
            pendingRowArrived(
                previousItems = 0,
                previousEarlierItems = 0,
                nextItems = 1,
                nextEarlierItems = 0,
            ),
        )
    }

    @Test
    fun `a refetch that changes nothing is not an arrival`() {
        // The common case by a long way: the cache version bumps because this
        // device's own write echoed back. A celebration in flight must survive
        // it.
        assertFalse(
            pendingRowArrived(
                previousItems = 2,
                previousEarlierItems = 1,
                nextItems = 2,
                nextEarlierItems = 1,
            ),
        )
    }

    @Test
    fun `a completion is not an arrival`() {
        // The signal has to be directional. A row LEAVING is what opens a
        // celebration; if this answered true for that too, the gate would cancel
        // every burst on the frame it was earned.
        assertFalse(
            pendingRowArrived(
                previousItems = 1,
                previousEarlierItems = 0,
                nextItems = 0,
                nextEarlierItems = 0,
            ),
        )
    }

    @Test
    fun `a row moving from Earlier into the scope is not an arrival`() {
        // Sweeping an overdue task into today moves it between the two buckets
        // and adds nothing. Counting the buckets separately would report an
        // arrival in `items` and end a celebration nobody interrupted; counting
        // the total is what makes the two buckets one screen.
        assertFalse(
            pendingRowArrived(
                previousItems = 0,
                previousEarlierItems = 1,
                nextItems = 1,
                nextEarlierItems = 0,
            ),
        )
    }

    @Test
    fun `a bulk undo is one arrival, not none`() {
        assertTrue(
            pendingRowArrived(
                previousItems = 0,
                previousEarlierItems = 0,
                nextItems = 5,
                nextEarlierItems = 0,
            ),
        )
    }
}
