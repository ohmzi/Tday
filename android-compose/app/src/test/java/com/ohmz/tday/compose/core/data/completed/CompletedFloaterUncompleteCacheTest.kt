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

    @Test
    fun `restores the floater in place when its list was never deleted`() {
        val state = OfflineSyncState(
            floaters = listOf(
                cachedFloater(id = "floater-1", listId = "list-1", completed = true),
            ),
            floaterLists = listOf(cachedFloaterList(id = "list-1", name = "Groceries")),
            completedFloaters = listOf(
                cachedCompletedFloater(id = "completed-1", originalFloaterId = "floater-1", listId = "list-1", listName = "Groceries"),
            ),
        )
        val item = completedItem(id = "completed-1", originalFloaterId = "floater-1", listName = "Groceries", listColor = "TEAL")

        val (next, outcome) = state.withFloaterUncompletedLocally(item, originalFloaterId = "floater-1")

        assertFalse(outcome.listRecreated)
        assertEquals("Groceries", outcome.listName)
        assertFalse(next.floaters.single { it.canonicalId == "floater-1" }.completed)
        assertTrue(next.completedFloaters.none { it.id == "completed-1" })
        // No list churn: the list that was already there is untouched, not duplicated.
        assertEquals(listOf("list-1"), next.floaterLists.map { it.id })
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
                cachedCompletedFloater(id = "completed-1", originalFloaterId = "floater-1", listId = null, listName = "Groceries", listColor = "TEAL", listDeleted = true),
            ),
        )
        val item = completedItem(id = "completed-1", originalFloaterId = "floater-1", listName = "Groceries", listColor = "TEAL")

        val (next, outcome) = state.withFloaterUncompletedLocally(item, originalFloaterId = "floater-1")

        assertTrue(outcome.listRecreated)
        assertEquals("Groceries", outcome.listName)
        assertTrue(next.completedFloaters.none { it.id == "completed-1" })
        val recreatedList = next.floaterLists.singleOrNull()
        assertNotNull(recreatedList)
        assertEquals("Groceries", recreatedList!!.name)
        assertEquals("TEAL", recreatedList.color)
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
            floaterLists = listOf(cachedFloaterList(id = "local-floater-list-recreated", name = "Groceries", color = "TEAL")),
            completedFloaters = listOf(
                cachedCompletedFloater(id = "completed-2", originalFloaterId = "floater-2", listId = null, listName = "Groceries", listColor = "TEAL", listDeleted = true),
            ),
        )
        val item = completedItem(id = "completed-2", originalFloaterId = "floater-2", listName = "Groceries", listColor = "TEAL")

        val (next, outcome) = state.withFloaterUncompletedLocally(item, originalFloaterId = "floater-2")

        assertTrue(outcome.listRecreated)
        // No duplicate — still exactly the one recreated list.
        assertEquals(listOf("local-floater-list-recreated"), next.floaterLists.map { it.id })
        assertEquals("local-floater-list-recreated", next.floaters.single().listId)
    }

    @Test
    fun `restores a listless floater without recreating anything`() {
        val state = OfflineSyncState(
            floaters = emptyList(),
            floaterLists = emptyList(),
            completedFloaters = listOf(
                cachedCompletedFloater(id = "completed-3", originalFloaterId = "floater-3", listId = null, listName = null, listColor = null),
            ),
        )
        val item = completedItem(id = "completed-3", originalFloaterId = "floater-3", listName = null, listColor = null)

        val (next, outcome) = state.withFloaterUncompletedLocally(item, originalFloaterId = "floater-3")

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
        title = "Buy milk",
        priority = "Low",
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
        title = "Buy milk",
        priority = "Low",
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
