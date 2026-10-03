package com.ohmz.tday.compose.core.testcrash

// TEST-CRASH: safety net for the time-boxed freeze; removed with the TEST-CRASH commit.

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Drives the freeze loop through its injected block and schedule, so the same code path is exercised
 * without a looper and without blocking a test thread for the real 13 s.
 */
class TestCrashFreezeTest {

    /** Runs each slice inline, as the looper would one message later. */
    private val immediately = { next: () -> Unit -> next() }

    @Test
    fun `stop is honoured at the end of the slice it lands in once the hold-off has passed`() {
        val slices = mutableListOf<Long>()
        var releases = 0
        TestCrash.freeze(
            budgetMillis = 10_000L,
            sliceMillis = 100L,
            holdOffMillis = 100L,
            schedule = { next ->
                // The Stop tap is handled between the second and third slice.
                if (++releases == 2) TestCrash.cancelFreeze()
                next()
            },
            block = { slices += it },
        )

        assertEquals(listOf(100L, 100L), slices)
        assertEquals(FreezeState(blockedMillis = 200L, cancelled = true), TestCrash.freezeState.value)
    }

    @Test
    fun `a stop tapped during the solid window waits for the hold-off`() {
        val slices = mutableListOf<Long>()
        TestCrash.freeze(
            budgetMillis = 1_000L,
            sliceMillis = 100L,
            holdOffMillis = 600L,
            schedule = immediately,
            block = { millis ->
                slices += millis
                if (slices.size == 2) TestCrash.cancelFreeze() // tapped while nothing could be handled
            },
        )

        // The held thread never checks the flag, so the whole window is served before the stop counts.
        assertEquals(listOf(100L, 100L, 100L, 100L, 100L, 100L), slices)
        assertEquals(FreezeState(blockedMillis = 600L, cancelled = true), TestCrash.freezeState.value)
    }

    @Test
    fun `with the real hold-off the solid window is served in full before a stop counts`() {
        val slices = mutableListOf<Long>()
        TestCrash.freeze(
            schedule = immediately,
            block = { millis ->
                slices += millis
                if (slices.size == 2) TestCrash.cancelFreeze()
            },
        )

        assertEquals(TestCrash.FREEZE_HOLD_OFF_MILLIS, slices.sum())
        assertEquals(TestCrash.FREEZE_HOLD_OFF_MILLIS, slices.size * TestCrash.FREEZE_SLICE_MILLIS)
        assertEquals(
            FreezeState(blockedMillis = TestCrash.FREEZE_HOLD_OFF_MILLIS, cancelled = true),
            TestCrash.freezeState.value,
        )
    }

    @Test
    fun `an untouched run blocks for the full budget in slices and never overruns it`() {
        val slices = mutableListOf<Long>()
        var activeBetweenSlices = false
        TestCrash.freeze(
            schedule = { next ->
                activeBetweenSlices = TestCrash.freezeState.value.active
                next()
            },
            block = { slices += it },
        )

        assertTrue(activeBetweenSlices)
        assertTrue(slices.all { it <= TestCrash.FREEZE_SLICE_MILLIS })
        assertEquals(TestCrash.FREEZE_MILLIS, slices.sum())
        assertEquals(FreezeState(blockedMillis = TestCrash.FREEZE_MILLIS), TestCrash.freezeState.value)
    }

    @Test
    fun `a stop that lands before the first slice ends the block and is reported`() {
        val slices = mutableListOf<Long>()
        TestCrash.freeze(
            budgetMillis = 10_000L,
            sliceMillis = 100L,
            holdOffMillis = 0L,
            schedule = { next -> TestCrash.cancelFreeze(); next() },
            block = { slices += it },
        )

        assertTrue(slices.isEmpty())
        assertEquals(FreezeState(cancelled = true), TestCrash.freezeState.value)
    }

    @Test
    fun `stop reports whether a freeze was running`() {
        assertFalse(TestCrash.cancelFreeze())

        var reported = false
        TestCrash.freeze(
            budgetMillis = 200L,
            sliceMillis = 100L,
            holdOffMillis = 0L,
            schedule = immediately,
            block = { if (!reported) reported = TestCrash.cancelFreeze() },
        )

        assertTrue(reported)
        assertFalse(TestCrash.cancelFreeze())
    }

    @Test
    fun `a second freeze is ignored while one is running`() {
        val slices = mutableListOf<Long>()
        TestCrash.freeze(
            budgetMillis = 200L,
            sliceMillis = 100L,
            holdOffMillis = 0L,
            schedule = { next ->
                TestCrash.freeze(
                    budgetMillis = 10_000L,
                    sliceMillis = 100L,
                    holdOffMillis = 0L,
                    schedule = immediately,
                    block = { slices += -1L },
                )
                next()
            },
            block = { slices += it },
        )

        assertEquals(listOf(100L, 100L), slices)
    }

    @Test
    fun `the production freeze keeps its 13 s budget, half-second slices and ANR-sized hold-off`() {
        assertEquals(13_000L, TestCrash.FREEZE_MILLIS)
        assertTrue(TestCrash.FREEZE_SLICE_MILLIS in 1L..500L)
        assertTrue(TestCrash.FREEZE_SLICE_MILLIS < TestCrash.FREEZE_MILLIS)
        // Comfortably past the ~5 s input-dispatch timeout Android records an ANR on, and still
        // leaves time inside the budget for the yielding part that makes the trigger cancelable.
        assertTrue(TestCrash.FREEZE_HOLD_OFF_MILLIS > 5_000L)
        assertTrue(TestCrash.FREEZE_HOLD_OFF_MILLIS < TestCrash.FREEZE_MILLIS)
    }

    /**
     * The way out a device check found necessary: with no frame drawn during the block, the tap the
     * user makes lands on the frame that started the freeze — this button — so it has to cancel.
     */
    @Test
    fun `a second tap on the freeze button cancels the run instead of starting another`() {
        // A run whose slices are handed over by hand, so the "second tap" lands while it is going and
        // this test never blocks for the real budget.
        var pending: (() -> Unit)? = null
        TestCrash.freeze(
            budgetMillis = 10_000L,
            sliceMillis = 100L,
            holdOffMillis = 0L,
            schedule = { next -> pending = next },
            block = { },
        )

        assertFalse("a tap during a run must cancel it, not start another", TestCrash.fireFreeze())
        assertFalse("and a further tap still must not start one", TestCrash.fireFreeze())

        // Let the run finish, so the shared "running" flag is released for the other tests.
        pending?.invoke()
    }

    @Test
    fun `the pause between slices is long enough for a frame, and short against the budget`() {
        assertTrue("a frame or two of idle time", TestCrash.FREEZE_SLICE_GAP_MILLIS in 16L..100L)
        val totalGap = TestCrash.FREEZE_SLICE_GAP_MILLIS *
            (TestCrash.FREEZE_MILLIS / TestCrash.FREEZE_SLICE_MILLIS)
        assertTrue("gaps should stay a small share of the budget", totalGap < TestCrash.FREEZE_MILLIS / 10)
    }
}
