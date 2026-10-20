package com.ohmz.tday.compose.feature.todos

import com.ohmz.tday.compose.core.model.TodoItem
import com.ohmz.tday.compose.core.model.TodoListMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

// The three Today bucket keys, test-local for the same reason
// [TodoTimelineSectionsTest] keeps its own copies: asserting against the
// real literal rather than re-exporting a production constant just for tests.
private const val MORNING_BUCKET_KEY = "today-morning"
private const val AFTERNOON_BUCKET_KEY = "today-afternoon"
private const val TONIGHT_BUCKET_KEY = "today-tonight"

/**
 * A drag has to keep somewhere to land for as long as it lasts.
 *
 * Today's three buckets are the only drop targets that screen has, and they
 * used to be hidden by the very state that makes a drag most necessary: a day
 * with nothing pending left in it. Clear the last Today task and the whole
 * Morning/Afternoon/Tonight scaffold came down, so picking up an overdue row
 * out of Earlier -- to pull it back into today, the one thing that screen
 * exists to let you do -- found no target under the thumb at all and the
 * gesture died on release. The two halves of that are pinned separately here
 * because they failed separately:
 *
 *  - [buildTimelineSections] dropped the three buckets whenever all three were
 *    empty, `isDragActive` or not, while every other scope already had an
 *    explicit drag-time restore for exactly this;
 *  - [draggedTimelineTodo]'s two callers looked only in `items`, and Today
 *    keeps its overdue rows in [TodoListUiState.earlierItems] instead -- so a
 *    drag that began on an Earlier row resolved to no task, which meant no
 *    drop-eligible section, which meant no registered target bounds even had
 *    the buckets been on screen.
 *
 * Both are pure functions, so this is real coverage of the gesture's
 * preconditions rather than a device check standing in for one.
 */
class TodayDragDropTargetsTest {

    private val zone: ZoneId = ZoneId.systemDefault()

    private fun todo(
        id: String,
        due: Instant,
        canonicalId: String = id,
    ): TodoItem = TodoItem(
        id = id,
        canonicalId = canonicalId,
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

    private fun overdue(id: String = "overdue-1"): TodoItem = todo(
        id = id,
        due = Instant.now().minus(2, ChronoUnit.DAYS),
    )

    private fun todayAt(hour: Int, id: String): TodoItem = todo(
        id = id,
        due = ZonedDateTime.now(zone)
            .withHour(hour).withMinute(0).withSecond(0).withNano(0)
            .toInstant(),
    )

    @Test
    fun `a live drag restores all three Today buckets on a day with nothing pending`() {
        val sections = buildTimelineSections(
            mode = TodoListMode.TODAY,
            items = emptyList(),
            isDragActive = true,
            earlierItems = listOf(overdue()),
        )

        // The regression itself: an overdue row is being dragged, and all three
        // places it could be dropped have to be on screen to catch it.
        assertEquals(
            listOf(
                MORNING_BUCKET_KEY,
                AFTERNOON_BUCKET_KEY,
                TONIGHT_BUCKET_KEY,
                EARLIER_SECTION_KEY,
            ),
            sections.map { it.key },
        )
    }

    @Test
    fun `the restored buckets are real drop targets, not bare headers`() {
        val sections = buildTimelineSections(
            mode = TodoListMode.TODAY,
            items = emptyList(),
            isDragActive = true,
            earlierItems = listOf(overdue()),
        )

        // `timelineInAppDropTarget` refuses to register bounds for a section
        // with a null `targetDate`, and `canDropTodoInTimelineSection` reads
        // `targetHour` to decide a same-day time move. A restored bucket
        // missing either would be a header that merely looks droppable.
        listOf(
            MORNING_BUCKET_KEY to 9,
            AFTERNOON_BUCKET_KEY to 15,
            TONIGHT_BUCKET_KEY to 20,
        ).forEach { (key, expectedHour) ->
            val section = sections.first { it.key == key }
            assertNotNull("$key must carry a target date to be droppable", section.targetDate)
            assertEquals(expectedHour, section.targetHour)
        }
    }

    @Test
    fun `the restored buckets stay empty, so the day still reads as done`() {
        val sections = buildTimelineSections(
            mode = TodoListMode.TODAY,
            items = emptyList(),
            isDragActive = true,
            earlierItems = listOf(overdue()),
        )

        // Restoring a bucket must never look like restoring a task. If an
        // empty drop target counted as pending work, starting a drag would
        // yank the "day is done" scene out from under the user's thumb and
        // put it back on release.
        assertTrue(sections.none { it.key != EARLIER_SECTION_KEY && it.items.isNotEmpty() })
        assertTrue(nonEarlierSectionsEmpty(sections))
    }

    @Test
    fun `with no drag the same day still shows Earlier alone`() {
        val sections = buildTimelineSections(
            mode = TodoListMode.TODAY,
            items = emptyList(),
            isDragActive = false,
            earlierItems = listOf(overdue()),
        )

        // The other half of the rule, and the reason the restore is keyed on
        // the drag rather than made unconditional: at rest an emptied day
        // shows its empty-state scene with Earlier under it, not three
        // headers with nothing beneath any of them.
        assertEquals(listOf(EARLIER_SECTION_KEY), sections.map { it.key })
    }

    @Test
    fun `a drag on an Earlier row resolves, even though Today keeps it out of items`() {
        val earlierRow = overdue()

        // Exactly the Today shape: `items` is pending-today only and the
        // overdue rows live in their own array. An items-only lookup answered
        // null here, and a null dragged task is indistinguishable from no drag.
        assertEquals(
            earlierRow,
            draggedTimelineTodo(
                draggedTodoId = earlierRow.id,
                items = emptyList(),
                earlierItems = listOf(earlierRow),
            ),
        )
    }

    @Test
    fun `a drag on a pending Today row still resolves from items`() {
        val morningRow = todayAt(hour = 9, id = "morning-1")

        assertEquals(
            morningRow,
            draggedTimelineTodo(
                draggedTodoId = morningRow.id,
                items = listOf(morningRow),
                earlierItems = listOf(overdue()),
            ),
        )
    }

    @Test
    fun `a recurring occurrence resolves by canonical id`() {
        val occurrence = todo(
            id = "series-1@2026-09-12",
            due = Instant.now().minus(2, ChronoUnit.DAYS),
            canonicalId = "series-1",
        )

        assertEquals(
            occurrence,
            draggedTimelineTodo(
                draggedTodoId = "series-1",
                items = emptyList(),
                earlierItems = listOf(occurrence),
            ),
        )
    }

    @Test
    fun `an id naming nothing on screen resolves to null`() {
        // `draggedScheduledTodoId` is rememberSaveable, so it outlives the
        // gesture across a rotation. The liveness check has to be able to go
        // false again, or the restored buckets would sit on screen at rest --
        // which is the failure this whole restore is careful not to cause.
        assertNull(
            draggedTimelineTodo(
                draggedTodoId = "no-such-task",
                items = listOf(todayAt(hour = 9, id = "morning-1")),
                earlierItems = listOf(overdue()),
            ),
        )
    }

    @Test
    fun `no drag in hand resolves to null`() {
        assertNull(
            draggedTimelineTodo(
                draggedTodoId = null,
                items = listOf(todayAt(hour = 9, id = "morning-1")),
                earlierItems = listOf(overdue()),
            ),
        )
    }
}
