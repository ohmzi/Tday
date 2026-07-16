package com.ohmz.tday.shared.sort

import com.ohmz.tday.shared.model.Priority
import kotlin.test.Test
import kotlin.test.assertEquals

class TaskSortEngineTest {

    private fun task(
        id: String,
        pinned: Boolean = false,
        due: Long? = null,
        priority: String = "Low",
        updated: Long? = null,
    ) = TaskSortKey(
        id = id,
        pinned = pinned,
        dueEpochMs = due,
        priorityRank = TaskSortEngine.priorityRank(priority),
        updatedAtEpochMs = updated,
    )

    private fun todoIds(items: List<TaskSortKey>) =
        TaskSortEngine.sortedTodos(items) { it }.map { it.id }

    private fun floaterIds(items: List<TaskSortKey>) =
        TaskSortEngine.sortedFloaters(items) { it }.map { it.id }

    @Test
    fun todosSortByDueThenPriorityThenModified() {
        val items = listOf(
            task("late", due = 300, priority = "High"),
            task("early-low", due = 100, priority = "Low"),
            task("early-high", due = 100, priority = "High"),
            task("early-high-newer", due = 100, priority = "High", updated = 900),
        )
        // due asc first (100 group before 300); within 100: priority High before Low;
        // within same due+High: most recently modified first.
        assertEquals(
            listOf("early-high-newer", "early-high", "early-low", "late"),
            todoIds(items),
        )
    }

    @Test
    fun todosPutUndatedLastAndPinnedFirst() {
        val items = listOf(
            task("undated", due = null, priority = "High"),
            task("dated", due = 500, priority = "Low"),
            task("pinned-late", pinned = true, due = 9_000, priority = "Low"),
        )
        assertEquals(listOf("pinned-late", "dated", "undated"), todoIds(items))
    }

    @Test
    fun floatersSortByPriorityThenModified() {
        val items = listOf(
            task("low", priority = "Low", updated = 100),
            task("high-old", priority = "High", updated = 100),
            task("high-new", priority = "High", updated = 500),
            task("medium", priority = "Medium", updated = 999),
        )
        assertEquals(listOf("high-new", "high-old", "medium", "low"), floaterIds(items))
    }

    @Test
    fun floatersLeadWithPinnedRegardlessOfPriority() {
        val items = listOf(
            task("high-unpinned", priority = "High", updated = 100),
            task("low-pinned", pinned = true, priority = "Low", updated = 100),
        )
        assertEquals(listOf("low-pinned", "high-unpinned"), floaterIds(items))
    }

    @Test
    fun idBreaksFullTies() {
        val items = listOf(
            task("b", due = 1, priority = "Low", updated = 1),
            task("a", due = 1, priority = "Low", updated = 1),
        )
        assertEquals(listOf("a", "b"), todoIds(items))
    }

    @Test
    fun priorityRankOrdersHighFirst() {
        assertEquals(0, TaskSortEngine.priorityRank(Priority.High))
        assertEquals(1, TaskSortEngine.priorityRank(Priority.Medium))
        assertEquals(2, TaskSortEngine.priorityRank(Priority.Low))
        assertEquals(2, TaskSortEngine.priorityRank("nonsense"))
    }
}
