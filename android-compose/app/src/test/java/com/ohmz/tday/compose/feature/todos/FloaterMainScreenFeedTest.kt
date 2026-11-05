package com.ohmz.tday.compose.feature.todos

import com.ohmz.tday.compose.core.data.CachedFloaterListRecord
import com.ohmz.tday.compose.core.data.CachedFloaterRecord
import com.ohmz.tday.compose.core.data.OfflineSyncState
import com.ohmz.tday.compose.core.data.todo.floatersForSummary
import com.ohmz.tday.compose.core.model.ListSummary
import com.ohmz.tday.compose.core.model.TodoItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FloaterMainScreenFeedTest {
    private val lists = listOf(
        list(id = "hidden", name = "Someday", showOnMainScreen = false),
        list(id = "shown", name = "Errands"),
    )

    private fun list(id: String, name: String, showOnMainScreen: Boolean = true) = ListSummary(
        id = id,
        name = name,
        color = null,
        iconKey = null,
        todoCount = 0,
        showOnMainScreen = showOnMainScreen,
    )

    private fun todo(id: String, listId: String?) = TodoItem(
        id = id,
        canonicalId = id,
        title = id,
        description = null,
        priority = "Low",
        due = null,
        rrule = null,
        instanceDate = null,
        pinned = false,
        completed = false,
        listId = listId,
    )

    @Test
    fun `the feed leaves out a hidden list and keeps everything else`() {
        val items = listOf(todo("a", "hidden"), todo("b", "shown"), todo("c", null), todo("d", "unknown"))

        assertEquals(listOf("b", "c", "d"), floaterTaskHomeFeedItems(items, lists).map { it.id })
    }

    @Test
    fun `a search result in a hidden list opens that list`() {
        assertEquals("hidden", hiddenListForSearchResult(todo("a", "hidden"), lists)?.id)
    }

    @Test
    fun `a search result in the feed stays in the feed`() {
        assertNull(hiddenListForSearchResult(todo("b", "shown"), lists))
        assertNull(hiddenListForSearchResult(todo("c", null), lists))
    }

    private val state = OfflineSyncState(
        floaterLists = listOf(CachedFloaterListRecord(id = "hidden", name = "Someday", showOnMainScreen = false)),
        floaters = listOf(
            CachedFloaterRecord(id = "a", canonicalId = "a", title = "Tucked", listId = "hidden"),
            CachedFloaterRecord(id = "b", canonicalId = "b", title = "Open"),
        ),
    )

    @Test
    fun `the on-device Floater summary skips a hidden list`() {
        assertEquals(listOf("b"), floatersForSummary(state, listId = null).map { it.id })
    }

    @Test
    fun `a hidden list's own summary keeps its tasks`() {
        assertEquals(listOf("a", "b"), floatersForSummary(state, listId = "hidden").map { it.id })
    }
}
