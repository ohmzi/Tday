package com.ohmz.tday.compose.feature.todos

import com.ohmz.tday.compose.core.model.TodoItem
import com.ohmz.tday.compose.core.model.TodoListMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

// Test-local copies of the two [TodoSection.key] values this file asserts on
// more than once ("earlier" itself is [EARLIER_SECTION_KEY] from
// TodoListScreen.kt, the real production key rather than a third copy).
private const val TODAY_MORNING_KEY = "today-morning"
private const val TODAY_AFTERNOON_KEY = "today-afternoon"
private const val TODAY_TONIGHT_KEY = "today-tonight"

/**
 * Requirement 2's core logic: the Today screen's "zero pending tasks" rule
 * has to hold regardless of what Earlier is carrying, and Earlier itself has
 * to stay visible whenever it holds tasks -- independent of whether
 * Morning/Afternoon/Tonight have anything in them.
 *
 * [buildTimelineSections] is a pure function (no Compose, no clock reads
 * beyond `ZoneId.systemDefault()`), so this exercises requirement 2 for real
 * instead of only by device/emulator inspection.
 */
class TodoTimelineSectionsTest {

    private val zone = ZoneId.systemDefault()

    private fun todayAt(hour: Int, id: String): TodoItem = todo(
        id = id,
        due = ZonedDateTime.now(zone)
            .withHour(hour).withMinute(0).withSecond(0).withNano(0)
            .toInstant(),
    )

    private fun overdue(id: String): TodoItem = todo(
        id = id,
        due = Instant.now().minus(2, ChronoUnit.DAYS),
    )

    private fun todo(id: String, due: Instant): TodoItem = TodoItem(
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

    @Test
    fun `a fully empty Today with nothing in Earlier builds no sections`() {
        val sections = buildTimelineSections(
            mode = TodoListMode.TODAY,
            items = emptyList(),
            isDragActive = false,
            earlierItems = emptyList(),
        )

        assertEquals(emptyList<TodoSection>(), sections)
    }

    @Test
    fun `zero pending today tasks still surfaces Earlier when it holds overdue tasks`() {
        val overdueTask = overdue("overdue-1")

        val sections = buildTimelineSections(
            mode = TodoListMode.TODAY,
            items = emptyList(),
            isDragActive = false,
            earlierItems = listOf(overdueTask),
        )

        // Morning/Afternoon/Tonight stay hidden -- nothing pending today to
        // shape them around -- but Earlier is not, because requirement 2 is
        // "zero pending today", not "zero tasks anywhere on this screen".
        assertEquals(listOf(EARLIER_SECTION_KEY), sections.map { it.key })
        assertEquals(listOf(overdueTask), sections.single().items)
    }

    @Test
    fun `pending today tasks keep all three time buckets plus a trailing Earlier`() {
        val morningTask = todayAt(hour = 9, id = "morning-1")
        val overdueTask = overdue("overdue-1")

        val sections = buildTimelineSections(
            mode = TodoListMode.TODAY,
            items = listOf(morningTask),
            isDragActive = false,
            earlierItems = listOf(overdueTask),
        )

        assertEquals(
            listOf(TODAY_MORNING_KEY, TODAY_AFTERNOON_KEY, TODAY_TONIGHT_KEY, EARLIER_SECTION_KEY),
            sections.map { it.key },
        )
        assertEquals(listOf(morningTask), sections.first { it.key == TODAY_MORNING_KEY }.items)
        assertTrue(sections.first { it.key == TODAY_AFTERNOON_KEY }.items.isEmpty())
        assertEquals(listOf(overdueTask), sections.first { it.key == EARLIER_SECTION_KEY }.items)
    }

    @Test
    fun `no Earlier section is built when there is nothing overdue`() {
        val morningTask = todayAt(hour = 9, id = "morning-1")

        val sections = buildTimelineSections(
            mode = TodoListMode.TODAY,
            items = listOf(morningTask),
            isDragActive = false,
            earlierItems = emptyList(),
        )

        assertEquals(
            listOf(TODAY_MORNING_KEY, TODAY_AFTERNOON_KEY, TODAY_TONIGHT_KEY),
            sections.map { it.key },
        )
    }
}
