package com.ohmz.tday.compose.core.data.todo

import com.ohmz.tday.compose.core.data.CachedFloaterRecord
import com.ohmz.tday.shared.summary.SummaryEngine
import com.ohmz.tday.shared.summary.SummaryScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The wiring test the Anytime summary was missing.
 *
 * Every dormancy case in the shared `FloaterSummaryTest` builds a `SummaryTaskInput` by hand, so
 * the suite proved the planner and nothing about what the app actually hands it. Offline Android
 * built its inputs without `updatedAtEpochMs`, which made `FloaterResting.tierFor` answer ACTIVE
 * for every row and left three of the eight notes unreachable in the shipped app. These assert the
 * cache row -> engine journey end to end, which is the only place that bug was visible.
 */
class FloaterSummaryInputTest {

    private val nowMs = 1_780_390_800_000L
    private val day = 86_400_000L

    private fun cached(
        id: String,
        title: String,
        updatedAtEpochMs: Long = nowMs,
        pinned: Boolean = false,
    ) = CachedFloaterRecord(
        id = id,
        canonicalId = id,
        title = title,
        pinned = pinned,
        updatedAtEpochMs = updatedAtEpochMs,
    )

    private fun summarize(rows: List<CachedFloaterRecord>) = SummaryEngine.summarize(
        tasks = rows.map(CachedFloaterRecord::toSummaryInput),
        scope = SummaryScope.FLOATER,
        nowEpochMs = nowMs,
        timeZoneId = "UTC",
        locale = "en",
    )

    @Test
    fun `a floater untouched for months reaches the engine as dormant`() {
        val rows = (1..3).map { cached("f$it", "Waiting $it", updatedAtEpochMs = nowMs - 200 * day) }
        val fresh = rows.map { it.copy(updatedAtEpochMs = nowMs - day) }

        assertEquals(nowMs - 200 * day, rows.first().toSummaryInput().updatedAtEpochMs)
        assertNotEquals(
            "the cached last-write clock never reached the engine",
            summarize(fresh),
            summarize(rows),
        )
    }

    @Test
    fun `an unstamped cache row reads as active rather than as 1970`() {
        // 0L is the cache's "never synced" sentinel. Passed through as-is it would be an epoch
        // timestamp 56 years old, and every unsynced floater would be reported as dormant.
        assertNull(cached("f1", "Waiting", updatedAtEpochMs = 0L).toSummaryInput().updatedAtEpochMs)
        assertEquals(
            summarize(listOf(cached("f1", "Waiting", updatedAtEpochMs = nowMs))),
            summarize(listOf(cached("f1", "Waiting", updatedAtEpochMs = 0L))),
        )
    }

    @Test
    fun `the mapping carries the pin the note reads`() {
        val rows = listOf(
            cached("f1", "Renew passport", pinned = true),
            cached("f2", "Fix the bike light"),
            cached("f3", "Sort the loft boxes"),
        )
        assertNotEquals(
            "pinning changed nothing, so the pin is not crossing the mapping",
            summarize(rows.map { it.copy(pinned = false) }),
            summarize(rows),
        )
    }
}
