package com.ohmz.tday.compose.core.data.completed

import com.ohmz.tday.compose.core.data.CachedCompletedFloaterRecord
import com.ohmz.tday.compose.core.data.CachedFloaterListRecord
import com.ohmz.tday.compose.core.data.CachedFloaterRecord
import com.ohmz.tday.compose.core.data.OfflineSyncState
import com.ohmz.tday.compose.core.model.CompletedItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [OfflineSyncState.withFloaterUncompletedLocally] is the local-cache-only mirror
 * of the backend's uncompleteFloater() find-or-create (used by
 * [CompletedRepository.uncompleteFloater] for Local Mode and a not-yet-synced
 * floater — see docs/design/completed-floaters-durability.md §2/§6). Pinned here
 * the way [com.ohmz.tday.compose.core.data.todo.BulkTaskCacheTest] pins the
 * equivalent pure `OfflineSyncState` transforms: no Room, Hilt or network needed
 * to prove this logic.
 */
class CompletedFloaterUncompleteCacheTest {

    private companion object {
        // Shared test vocabulary — hoisted so the same literal isn't repeated
        // across every test/helper call site in this file.
        const val LIST_ID = "list-1"
        const val LIST_NAME = "Groceries"
        const val LIST_COLOR = "TEAL"
        const val RECREATED_LIST_ID = "local-floater-list-recreated"
        const val FLOATER_ID_1 = "floater-1"
        const val COMPLETED_ID_1 = "completed-1"
        const val FLOATER_ID_2 = "floater-2"
        const val COMPLETED_ID_2 = "completed-2"
        const val FLOATER_ID_3 = "floater-3"
        const val COMPLETED_ID_3 = "completed-3"
        const val DEFAULT_TITLE = "Buy milk"
        const val DEFAULT_PRIORITY = "Low"
    }

    @Test
    fun `restores the floater in place when its list was never deleted`() {
        val state = OfflineSyncState(
            floaters = listOf(
                cachedFloater(id = FLOATER_ID_1, listId = LIST_ID, completed = true),
            ),
            floaterLists = listOf(cachedFloaterList(id = LIST_ID, name = LIST_NAME)),
            completedFloaters = listOf(
                cachedCompletedFloater(id = COMPLETED_ID_1, originalFloaterId = FLOATER_ID_1, listId = LIST_ID, listName = LIST_NAME),
            ),
        )
        val item = completedItem(id = COMPLETED_ID_1, originalFloaterId = FLOATER_ID_1, listName = LIST_NAME, listColor = LIST_COLOR)

        val (next, outcome) = state.withFloaterUncompletedLocally(item, originalFloaterId = FLOATER_ID_1)

        assertFalse(outcome.listRecreated)
        assertEquals(LIST_NAME, outcome.listName)
        assertFalse(next.floaters.single { it.canonicalId == FLOATER_ID_1 }.completed)
        assertTrue(next.completedFloaters.none { it.id == COMPLETED_ID_1 })
        // No list churn: the list that was already there is untouched, not duplicated.
        assertEquals(listOf(LIST_ID), next.floaterLists.map { it.id })
    }

    @Test
    fun `recreates the original list under its original name and color when the floater row is gone`() {
        // The list delete already removed the live Floaters row (see
        // FloaterListRepository.deleteList) — only the completion record survives,
        // carrying the denormalized listName/listColor snapshot.
        val state = OfflineSyncState(
            floaters = emptyList(),
            floaterLists = emptyList(),
            completedFloaters = listOf(
                cachedCompletedFloater(id = COMPLETED_ID_1, originalFloaterId = FLOATER_ID_1, listId = null, listName = LIST_NAME, listColor = LIST_COLOR, listDeleted = true),
            ),
        )
        val item = completedItem(id = COMPLETED_ID_1, originalFloaterId = FLOATER_ID_1, listName = LIST_NAME, listColor = LIST_COLOR)

        val (next, outcome) = state.withFloaterUncompletedLocally(item, originalFloaterId = FLOATER_ID_1)

        assertTrue(outcome.listRecreated)
        assertEquals(LIST_NAME, outcome.listName)
        assertTrue(next.completedFloaters.none { it.id == COMPLETED_ID_1 })
        val recreatedList = next.floaterLists.singleOrNull()
        assertNotNull(recreatedList)
        assertEquals(LIST_NAME, recreatedList!!.name)
        assertEquals(LIST_COLOR, recreatedList.color)
        val restoredFloater = next.floaters.singleOrNull()
        assertNotNull(restoredFloater)
        assertFalse(restoredFloater!!.completed)
        assertEquals(recreatedList.id, restoredFloater.listId)
    }

    @Test
    fun `converges a second undo from the same deleted list onto the already-recreated list`() {
        // First undo already ran and inserted the recreated list into this cache.
        val state = OfflineSyncState(
            floaters = emptyList(),
            floaterLists = listOf(cachedFloaterList(id = RECREATED_LIST_ID, name = LIST_NAME, color = LIST_COLOR)),
            completedFloaters = listOf(
                cachedCompletedFloater(id = COMPLETED_ID_2, originalFloaterId = FLOATER_ID_2, listId = null, listName = LIST_NAME, listColor = LIST_COLOR, listDeleted = true),
            ),
        )
        val item = completedItem(id = COMPLETED_ID_2, originalFloaterId = FLOATER_ID_2, listName = LIST_NAME, listColor = LIST_COLOR)

        val (next, outcome) = state.withFloaterUncompletedLocally(item, originalFloaterId = FLOATER_ID_2)

        assertTrue(outcome.listRecreated)
        // No duplicate — still exactly the one recreated list.
        assertEquals(listOf(RECREATED_LIST_ID), next.floaterLists.map { it.id })
        assertEquals(RECREATED_LIST_ID, next.floaters.single().listId)
    }

    @Test
    fun `restores a listless floater without recreating anything`() {
        val state = OfflineSyncState(
            floaters = emptyList(),
            floaterLists = emptyList(),
            completedFloaters = listOf(
                cachedCompletedFloater(id = COMPLETED_ID_3, originalFloaterId = FLOATER_ID_3, listId = null, listName = null, listColor = null),
            ),
        )
        val item = completedItem(id = COMPLETED_ID_3, originalFloaterId = FLOATER_ID_3, listName = null, listColor = null)

        val (next, outcome) = state.withFloaterUncompletedLocally(item, originalFloaterId = FLOATER_ID_3)

        assertFalse(outcome.listRecreated)
        assertTrue(next.floaterLists.isEmpty())
        val restoredFloater = next.floaters.singleOrNull()
        assertNotNull(restoredFloater)
        assertFalse(restoredFloater!!.completed)
        assertEquals(null, restoredFloater.listId)
    }

    private fun cachedFloater(
        id: String,
        listId: String? = null,
        completed: Boolean = false,
    ) = CachedFloaterRecord(
        id = id,
        canonicalId = id,
        title = DEFAULT_TITLE,
        priority = DEFAULT_PRIORITY,
        completed = completed,
        listId = listId,
    )

    private fun cachedFloaterList(
        id: String,
        name: String,
        color: String? = null,
    ) = CachedFloaterListRecord(
        id = id,
        name = name,
        color = color,
    )

    private fun cachedCompletedFloater(
        id: String,
        originalFloaterId: String,
        listId: String?,
        listName: String?,
        listColor: String? = null,
        listDeleted: Boolean = false,
    ) = CachedCompletedFloaterRecord(
        id = id,
        originalFloaterId = originalFloaterId,
        title = DEFAULT_TITLE,
        priority = DEFAULT_PRIORITY,
        listId = listId,
        listName = listName,
        listColor = listColor,
        listDeleted = listDeleted,
    )

    private fun completedItem(
        id: String,
        originalFloaterId: String,
        listName: String?,
        listColor: String?,
    ) = CompletedItem(
        id = id,
        originalTodoId = originalFloaterId,
        title = "Buy milk",
        priority = "Low",
        due = null,
        rrule = null,
        instanceDate = null,
        listName = listName,
        listColor = listColor,
        isFloater = true,
    )
}
