package com.ohmz.tday.compose.core.data.todo

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

/**
 * The task sheet's Schedule toggle turned OFF on an existing task asks for an unscheduled
 * task. A scheduled task and a Floater are two entities in two tables (`todos.due` is NOT
 * NULL; `floaters` has no due, no rrule and its own list type), so that save cannot be a
 * `PATCH /api/todo` — there is no kind field for it to carry — and the task must be
 * converted (demoted) instead.
 */
class TaskSaveRoutingTest {

    private val due = Instant.parse("2026-09-17T15:00:00Z")

    @Test
    fun `a save that drops the due converts the task into a floater`() {
        assertEquals(
            TaskSave.CONVERT_TO_FLOATER,
            taskSaveFor(todoDue = due, todoIsRecurring = false, payloadDue = null),
        )
    }

    @Test
    fun `a save that keeps a due is a field update`() {
        assertEquals(
            TaskSave.UPDATE,
            taskSaveFor(todoDue = due, todoIsRecurring = false, payloadDue = due),
        )
    }

    @Test
    fun `a recurring task is never converted, even when its due is dropped`() {
        // The backend refuses to demote a recurring todo — its series would be destroyed —
        // so the save keeps the schedule rather than clearing the recurrence it could not
        // have been honoured for.
        assertEquals(
            TaskSave.UPDATE_KEEPING_SCHEDULE,
            taskSaveFor(todoDue = due, todoIsRecurring = true, payloadDue = null),
        )
    }

    @Test
    fun `a task that has no due is never converted`() {
        assertEquals(
            TaskSave.UPDATE_KEEPING_SCHEDULE,
            taskSaveFor(todoDue = null, todoIsRecurring = false, payloadDue = null),
        )
    }
}
