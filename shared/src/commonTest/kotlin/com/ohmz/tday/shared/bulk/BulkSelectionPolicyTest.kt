package com.ohmz.tday.shared.bulk

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private data class Row(
    val id: String,
    val recurring: Boolean = false,
    val hasInstanceDate: Boolean = false,
)

class BulkSelectionPolicyTest {

    @Test
    fun capStaysUnderTheApiRateLimitWindow() {
        // API_RATE_LIMIT_MAX defaults to 180 per 60s; one bulk action must not be
        // able to spend the whole budget or it 429s partway through a destructive run.
        assertTrue(BulkSelectionPolicy.MAX_SELECTION < 180)
        assertTrue(BulkSelectionPolicy.MAX_CONCURRENCY in 1..8)
    }

    @Test
    fun onlyCompleteAppliesToRecurringOccurrences() {
        assertTrue(BulkSelectionPolicy.appliesToRecurring(BulkAction.COMPLETE))
        assertFalse(BulkSelectionPolicy.appliesToRecurring(BulkAction.DELETE))
        assertFalse(BulkSelectionPolicy.appliesToRecurring(BulkAction.SET_PRIORITY))
        assertFalse(BulkSelectionPolicy.appliesToRecurring(BulkAction.MOVE_TO_LIST))
    }

    @Test
    fun recurringRowsAreDroppedFromDeletePriorityAndMove() {
        val selection = listOf(Row("a"), Row("b", recurring = true), Row("c"))

        for (action in listOf(BulkAction.DELETE, BulkAction.SET_PRIORITY, BulkAction.MOVE_TO_LIST)) {
            assertEquals(
                listOf("a", "c"),
                BulkSelectionPolicy.effectiveSelection(action, selection) { it.recurring }.map { it.id },
                "$action must skip recurring occurrences",
            )
        }
    }

    @Test
    fun completeOnlyTakesARecurringRowThatIsARealOccurrence() {
        val selection = listOf(
            Row("a"),
            Row("occurrence", recurring = true, hasInstanceDate = true),
            Row("template", recurring = true),
            Row("c"),
        )

        // `template` has no instanceDate. TodoService.completeTodo would insert a
        // CompletedTodos row for it and take neither completion branch: nothing
        // marked complete, a phantom history entry, task still on screen.
        assertEquals(
            listOf("a", "occurrence", "c"),
            BulkSelectionPolicy.effectiveSelection(
                action = BulkAction.COMPLETE,
                selection = selection,
                hasInstanceDate = { it.hasInstanceDate },
                isRecurring = { it.recurring },
            ).map { it.id },
        )
    }

    @Test
    fun completeDefaultsToTreatingRecurringRowsAsNonOccurrences() {
        // A caller that has not supplied hasInstanceDate must get the safe answer.
        val selection = listOf(Row("a"), Row("b", recurring = true))

        assertEquals(
            listOf("a"),
            BulkSelectionPolicy.effectiveSelection(BulkAction.COMPLETE, selection) { it.recurring }.map { it.id },
        )
    }

    @Test
    fun effectiveSelectionTruncatesInDisplayOrder() {
        val selection = (0 until 250).map { Row("row-$it") }
        val effective = BulkSelectionPolicy.effectiveSelection(BulkAction.COMPLETE, selection) { it.recurring }

        assertEquals(BulkSelectionPolicy.MAX_SELECTION, effective.size)
        assertEquals("row-0", effective.first().id)
        assertEquals("row-${BulkSelectionPolicy.MAX_SELECTION - 1}", effective.last().id)
    }

    @Test
    fun deleteAlwaysConfirmsAndMoveOnlyConfirmsAcrossLists() {
        assertTrue(BulkSelectionPolicy.requiresConfirmation(BulkAction.DELETE, distinctSourceLists = 1))
        assertFalse(BulkSelectionPolicy.requiresConfirmation(BulkAction.MOVE_TO_LIST, distinctSourceLists = 1))
        assertTrue(BulkSelectionPolicy.requiresConfirmation(BulkAction.MOVE_TO_LIST, distinctSourceLists = 2))
        assertFalse(BulkSelectionPolicy.requiresConfirmation(BulkAction.COMPLETE, distinctSourceLists = 3))
        assertFalse(BulkSelectionPolicy.requiresConfirmation(BulkAction.SET_PRIORITY, distinctSourceLists = 3))
    }

    @Test
    fun deleteCarriesBothGuards() {
        // The safety invariant of the whole feature: bulk delete is never one tap.
        // A confirmation stating the count AND the existing delayed-commit undo.
        assertTrue(BulkSelectionPolicy.requiresConfirmation(BulkAction.DELETE, distinctSourceLists = 1))
        assertTrue(BulkSelectionPolicy.isUndoable(BulkAction.DELETE))
    }

    @Test
    fun priorityIsTheOnlyActionThatShipsUnguarded() {
        // Pinned deliberately: changing a flag is one tap to reverse. If any other
        // action ever lands in this list, it lost a guard it was supposed to have.
        val unguarded = BulkAction.entries.filterNot { action ->
            BulkSelectionPolicy.isUndoable(action) ||
                BulkSelectionPolicy.requiresConfirmation(action, distinctSourceLists = 2)
        }
        assertEquals(listOf(BulkAction.SET_PRIORITY), unguarded)
    }

    @Test
    fun capRefusesFurtherSelection() {
        assertFalse(BulkSelectionPolicy.isAtCap(0))
        assertFalse(BulkSelectionPolicy.isAtCap(BulkSelectionPolicy.MAX_SELECTION - 1))
        assertTrue(BulkSelectionPolicy.isAtCap(BulkSelectionPolicy.MAX_SELECTION))
        assertTrue(BulkSelectionPolicy.isAtCap(BulkSelectionPolicy.MAX_SELECTION + 5))
    }
}
