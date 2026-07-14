package com.ohmz.tday.compose.core.data.export

import com.ohmz.tday.compose.core.data.CachedCompletedFloaterRecord
import com.ohmz.tday.compose.core.data.CachedCompletedRecord
import com.ohmz.tday.compose.core.data.CachedFloaterListRecord
import com.ohmz.tday.compose.core.data.CachedFloaterRecord
import com.ohmz.tday.compose.core.data.CachedListRecord
import com.ohmz.tday.compose.core.data.CachedTodoRecord
import com.ohmz.tday.compose.core.data.OfflineSyncState
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class LocalExportMapperTest {

    @Test
    fun `maps cache records into a portable bundle`() {
        val state = OfflineSyncState(
            todos = listOf(
                CachedTodoRecord(
                    id = "local-todo-1",
                    canonicalId = "todo-1",
                    title = "Plan",
                    priority = "High",
                    dueEpochMs = 1_000L,
                    listId = "list-1",
                    updatedAtEpochMs = 2_000L,
                ),
            ),
            floaters = listOf(
                CachedFloaterRecord(id = "local-fl-1", canonicalId = "fl-1", title = "Idea"),
            ),
            lists = listOf(CachedListRecord(id = "list-1", name = "Work", color = "BLUE")),
            floaterLists = listOf(CachedFloaterListRecord(id = "flist-1", name = "Ideas")),
            completedItems = listOf(
                CachedCompletedRecord(
                    id = "c-1",
                    originalTodoId = "todo-9",
                    title = "Done",
                    priority = "Low",
                    completedAtEpochMs = 3_000L,
                ),
            ),
            completedFloaters = listOf(
                CachedCompletedFloaterRecord(
                    id = "cf-1",
                    originalFloaterId = "fl-9",
                    title = "DoneFloater",
                    priority = "Low",
                    completedAtEpochMs = 4_000L,
                ),
            ),
            aiSummaryEnabled = false,
        )

        val export = LocalExportMapper.toExport(state, source = "local-android", exportedAtEpochMs = 5_000L)

        assertEquals("local-android", export.source)
        assertEquals(Instant.ofEpochMilli(5_000L).toString(), export.exportedAt)

        // Todo maps by canonical (server) id, not the interim local id.
        val todo = export.todos.single().todo
        assertEquals("todo-1", todo.id)
        assertEquals("High", todo.priority)
        assertEquals(Instant.ofEpochMilli(1_000L).toString(), todo.due)
        assertEquals("list-1", todo.listID)

        assertEquals("fl-1", export.floaters.single().id)
        assertEquals("Work", export.lists.single().name)
        assertEquals("BLUE", export.lists.single().color)
        assertEquals("Ideas", export.floaterLists.single().name)

        assertEquals("todo-9", export.completedTodos.single().originalTodoID)
        assertEquals(Instant.ofEpochMilli(3_000L).toString(), export.completedTodos.single().completedAt)
        assertEquals("fl-9", export.completedFloaters.single().originalFloaterID)

        assertEquals(false, export.preferences?.aiSummaryEnabled)
    }

    @Test
    fun `a todo with no due falls back to the export timestamp`() {
        val state = OfflineSyncState(
            todos = listOf(CachedTodoRecord(id = "l", canonicalId = "t", title = "x", dueEpochMs = null)),
        )
        val export = LocalExportMapper.toExport(state, source = "local-android", exportedAtEpochMs = 9_000L)
        assertEquals(Instant.ofEpochMilli(9_000L).toString(), export.todos.single().todo.due)
    }
}
