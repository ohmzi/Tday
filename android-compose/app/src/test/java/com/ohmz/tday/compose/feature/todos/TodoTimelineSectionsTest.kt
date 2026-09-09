package com.ohmz.tday.compose.feature.todos

import com.ohmz.tday.compose.core.model.TodoItem
import com.ohmz.tday.compose.core.model.TodoListMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
 * Requirement 2's core logic: a screen's "zero pending tasks" rule has to
 * hold regardless of what Earlier is carrying, and Earlier itself has to
 * stay visible whenever it holds tasks -- independent of whether the rest of
 * the screen has anything in it.
 *
 * [buildTimelineSections] is a pure function (no Compose, no clock reads
 * beyond `ZoneId.systemDefault()`), so this exercises requirement 2 for real
 * instead of only by device/emulator inspection.
 *
 * The Today-specific tests above pin `buildTodaySections`, whose Earlier
 * bucket comes from a separately-fetched `earlierItems` field. The
 * Scheduled/Priority/All/List tests further down pin `buildScheduledSections`
 * instead, whose Earlier bucket is carved out of the SAME `items` array
 * `buildTodosForMode` already mixed overdue tasks into -- see
 * [nonEarlierSectionsEmpty] for why that difference matters to the
 * empty-state gate built on top of these sections.
 */
class TodoTimelineSectionsTest {

    private val zone = ZoneId.systemDefault()

    private fun todayAt(hour: Int, id: String): TodoItem = todo(
        id = id,
        due = ZonedDateTime.now(zone)
            .withHour(hour).withMinute(0).withSecond(0).withNano(0)
            .toInstant(),
    )

    // Defaulted rather than passed explicitly at every call site: most tests
    // below need exactly one interchangeable overdue/upcoming task and don't
    // care what its id is, so a repeated literal argument would only trip
    // DeepSource's duplicate-string-literal check for no real benefit. A
    // test that genuinely needs a distinct id still passes one.
    private fun overdue(id: String = "overdue-1"): TodoItem = todo(
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
        val overdueTask = overdue()

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
        val overdueTask = overdue()

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

    // --- Scheduled/Priority/All/List parity ---------------------------
    //
    // Unlike Today, `TodoRepository.buildTodosForMode` mixes overdue tasks
    // straight into `items` for these four modes -- there is no separate
    // `earlierItems` field to pass in, so every test below passes
    // `emptyList()` for it and puts the overdue task in `items` instead.
    // `buildScheduledSections` is what actually carves "Earlier" back out
    // of that one array; these tests pin its output shape (and, via
    // [nonEarlierSectionsEmpty], the empty-state condition built on top of
    // it) the same way the Today tests above pin `buildTodaySections`.

    private fun tomorrow(id: String = "tomorrow-1"): TodoItem = todo(
        id = id,
        due = ZonedDateTime.now(zone).plusDays(1).withHour(9).withMinute(0).withSecond(0).withNano(0)
            .toInstant(),
    )

    @Test
    fun `All -- an overdue-only scope surfaces just Earlier, and reads as non-Earlier-empty`() {
        val overdueTask = overdue()

        val sections = buildTimelineSections(
            mode = TodoListMode.ALL,
            items = listOf(overdueTask),
            isDragActive = false,
        )

        assertEquals(listOf(EARLIER_SECTION_KEY), sections.map { it.key })
        assertTrue(nonEarlierSectionsEmpty(sections))
    }

    @Test
    fun `Priority -- an overdue-only scope surfaces just Earlier, and reads as non-Earlier-empty`() {
        val overdueTask = overdue()

        val sections = buildTimelineSections(
            mode = TodoListMode.PRIORITY,
            items = listOf(overdueTask),
            isDragActive = false,
        )

        assertEquals(listOf(EARLIER_SECTION_KEY), sections.map { it.key })
        assertTrue(nonEarlierSectionsEmpty(sections))
    }

    @Test
    fun `List -- an overdue-only scope surfaces just Earlier, and reads as non-Earlier-empty`() {
        val overdueTask = overdue()

        val sections = buildTimelineSections(
            mode = TodoListMode.LIST,
            items = listOf(overdueTask),
            isDragActive = false,
        )

        assertEquals(listOf(EARLIER_SECTION_KEY), sections.map { it.key })
        assertTrue(nonEarlierSectionsEmpty(sections))
    }

    @Test
    fun `All -- a real upcoming task alongside Earlier reads as not empty`() {
        val overdueTask = overdue()
        val upcomingTask = tomorrow()

        val sections = buildTimelineSections(
            mode = TodoListMode.ALL,
            items = listOf(overdueTask, upcomingTask),
            isDragActive = false,
        )

        assertTrue(sections.any { it.key == EARLIER_SECTION_KEY && it.items.isNotEmpty() })
        assertFalse(nonEarlierSectionsEmpty(sections))
    }

    @Test
    fun `Scheduled has no Earlier concept -- an overdue-only scope builds no sections at all`() {
        // Scheduled's own repository filter (`isScheduledTodo`) already
        // excludes anything due before now, the same structural shape Today
        // had before this feature -- so unlike All/Priority/List there is no
        // display-time Earlier bucket for `buildScheduledSections` to carve
        // out here, `futureOnly = true` returns before ever building one.
        // Passing an overdue task in anyway (as if the repository filter
        // were bypassed) pins that this mode still shows nothing for it,
        // rather than silently inventing an Earlier section it never had.
        val overdueTask = overdue()

        val sections = buildTimelineSections(
            mode = TodoListMode.SCHEDULED,
            items = listOf(overdueTask),
            isDragActive = false,
        )

        assertEquals(emptyList<TodoSection>(), sections)
        assertTrue(nonEarlierSectionsEmpty(sections))
    }

    @Test
    fun `Scheduled -- a real upcoming task reads as not empty, same as before this feature`() {
        val upcomingTask = tomorrow()

        val sections = buildTimelineSections(
            mode = TodoListMode.SCHEDULED,
            items = listOf(upcomingTask),
            isDragActive = false,
        )

        assertFalse(sections.any { it.key == EARLIER_SECTION_KEY })
        assertFalse(nonEarlierSectionsEmpty(sections))
    }
}
