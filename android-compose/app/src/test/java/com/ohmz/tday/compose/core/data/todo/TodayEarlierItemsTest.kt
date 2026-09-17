package com.ohmz.tday.compose.core.data.todo

import com.ohmz.tday.compose.core.model.TodoItem
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/**
 * The bug these pin: Today's "Earlier" section used to be sourced from the raw
 * overdue set, `due < now`, while Today's own `items` is the whole calendar day.
 * A task due earlier today and still pending satisfied both predicates, so it
 * was rendered in its time-of-day bucket and again under Earlier on one screen.
 *
 * [todayEarlierItems] clips the overdue set to the day boundary, which makes the
 * two lists disjoint by construction. Both the clock and the zone are fixed
 * here, so these assert the boundary itself rather than the wall clock the suite
 * happens to run at.
 */
class TodayEarlierItemsTest {

    private val zone = ZoneId.of("America/New_York")

    /** 3pm on a fixed day: late enough that a 9am task on the same day is "overdue". */
    private val now: Instant = ZonedDateTime.of(2026, 3, 10, 15, 0, 0, 0, zone).toInstant()

    private fun todo(id: String, due: Instant?): TodoItem = TodoItem(
        id = id,
        canonicalId = id,
        title = "task-$id",
        description = null,
        priority = "Low",
        due = due,
        rrule = null,
        instanceDate = null,
        pinned = false,
        completed = false,
        listId = null,
        updatedAt = null,
    )

    private fun todayAtHour(hour: Int): Instant =
        ZonedDateTime.of(2026, 3, 10, hour, 0, 0, 0, zone).toInstant()

    private fun earlierItems(vararg todos: TodoItem): List<TodoItem> =
        todayEarlierItems(overdueTodos = todos.toList(), zoneId = zone, now = now)

    @Test
    fun `the same day earlier than now is not an Earlier row`() {
        // 9am today, seen at 3pm. This is the exact task the old source drew
        // twice: `due < now` accepted it, and `isTodayTodo` accepts it too.
        val earlierToday = todo(id = "earlier-today", due = todayAtHour(9))

        assertEquals(emptyList<TodoItem>(), earlierItems(earlierToday))
    }

    @Test
    fun `a previous day is an Earlier row`() {
        val yesterday = todo(id = "yesterday", due = todayAtHour(9).minus(1, ChronoUnit.DAYS))

        assertEquals(listOf(yesterday), earlierItems(yesterday))
    }

    @Test
    fun `the very start of today is not an Earlier row`() {
        // Midnight belongs to today, so it is `items`, not Earlier. The
        // boundary is exclusive on this side, which is what keeps it disjoint
        // from `isTodayTodo`'s inclusive `due >= startOfToday`.
        val midnight = todo(id = "midnight", due = todayAtHour(0))

        assertEquals(emptyList<TodoItem>(), earlierItems(midnight))
    }

    @Test
    fun `a later day is not an Earlier row`() {
        val tomorrow = todo(id = "tomorrow", due = todayAtHour(9).plus(1, ChronoUnit.DAYS))

        assertEquals(emptyList<TodoItem>(), earlierItems(tomorrow))
    }

    @Test
    fun `an undated task is not an Earlier row`() {
        assertEquals(emptyList<TodoItem>(), earlierItems(todo(id = "undated", due = null)))
    }

    @Test
    fun `ordering and unrelated rows survive the clip`() {
        // The clip is a filter, not a sort: it must leave the caller's order
        // (the shared cross-platform todo ordering) and every genuinely past
        // row exactly as they were.
        val twoDaysAgo = todo(id = "two-days-ago", due = todayAtHour(9).minus(2, ChronoUnit.DAYS))
        val yesterday = todo(id = "yesterday", due = todayAtHour(9).minus(1, ChronoUnit.DAYS))
        val earlierToday = todo(id = "earlier-today", due = todayAtHour(9))

        assertEquals(
            listOf(twoDaysAgo, yesterday),
            earlierItems(twoDaysAgo, earlierToday, yesterday),
        )
    }
}
