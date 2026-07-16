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
        // Minute-separated dues (60_000 = minute 1, 180_000 = minute 3) so due sorts BEFORE
        // priority. Within the same minute, priority (High before Low) then modified DESC decide.
        val items = listOf(
            task("late", due = 180_000, priority = "High"),
            task("early-low", due = 60_000, priority = "Low"),
            task("early-high", due = 60_000, priority = "High"),
            task("early-high-newer", due = 60_000, priority = "High", updated = 900),
        )
        assertEquals(
            listOf("early-high-newer", "early-high", "early-low", "late"),
            todoIds(items),
        )
    }

    @Test
    fun sameMinuteDifferentSecondsTiebreaksByPriority() {
        // Real reported bug: two overdue tasks both shown at "9:41 PM" but differing by a few
        // seconds. Same displayed minute => priority must win (High first), not the 5s-earlier
        // low-priority one.
        val biotech = task("biotech", due = 6_029_000, priority = "Low") // 9:41:29
        val go = task("go", due = 6_034_000, priority = "High")          // 9:41:34
        assertEquals(listOf("go", "biotech"), todoIds(listOf(biotech, go)))
    }

    @Test
    fun differentMinutesStillOrderByDue() {
        val earlier = task("earlier", due = 6_000_000, priority = "Low")  // 9:40:00
        val later = task("later", due = 6_070_000, priority = "High")     // 9:41:10 (next minute)
        assertEquals(listOf("earlier", "later"), todoIds(listOf(earlier, later)))
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

    @Test
    fun priorityRankToleratesServerVocabularyAndCase() {
        // The realtime re-sort bug: un-normalized server rows carry these spellings and must
        // NOT all collapse to Low, or a priority change never moves the row.
        for (high in listOf("High", "high", "HIGH", "urgent", "Urgent", " urgent ")) {
            assertEquals(0, TaskSortEngine.priorityRank(high), "high vocab: '$high'")
        }
        for (medium in listOf("Medium", "medium", "important", "IMPORTANT")) {
            assertEquals(1, TaskSortEngine.priorityRank(medium), "medium vocab: '$medium'")
        }
        for (low in listOf("Low", "low", "normal", "NORMAL", null, "", "weird")) {
            assertEquals(2, TaskSortEngine.priorityRank(low), "low/default vocab: '$low'")
        }
    }

    @Test
    fun sameMinuteTodosOrderByPriorityEvenWithServerVocabularyAndOlderModified() {
        // The exact Android report: two todos at the same due-minute, one flagged "important"
        // (foreign edit, server vocabulary). Priority must beat the modified-time tiebreak, so
        // "important" leads even though the un-flagged "normal" one was modified more recently.
        val normal = task("normal", due = 90_000, priority = "normal", updated = 500)
        val important = task("important", due = 90_000, priority = "important", updated = 100)
        assertEquals(listOf("important", "normal"), todoIds(listOf(normal, important)))
    }

    @Test
    fun floatersReorderWhenPriorityChangesFromServerVocabulary() {
        // Two floaters; the "urgent" one must lead even though it isn't spelled "High".
        val normal = task("normal", priority = "normal", updated = 900)
        val urgent = task("urgent", priority = "urgent", updated = 100)
        assertEquals(listOf("urgent", "normal"), floaterIds(listOf(normal, urgent)))
    }
}
