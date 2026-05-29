package com.ohmz.tday.compose.core.data.todo

import com.ohmz.tday.compose.core.data.CachedCompletedRecord
import com.ohmz.tday.compose.core.data.CachedTodoRecord
import com.ohmz.tday.compose.core.data.MutationKind
import com.ohmz.tday.compose.core.data.OfflineSyncState
import com.ohmz.tday.compose.core.data.PendingMutationRecord
import org.junit.Assert.assertEquals
import org.junit.Test

class TodoRepositoryDeleteCacheTest {

    @Test
    fun `recurring instance delete prunes only the targeted occurrence`() {
        val state = OfflineSyncState(
            todos = listOf(
                cachedTodo(id = "todo-1:1000", instanceDateEpochMs = 1000L),
                cachedTodo(id = "todo-1:2000", instanceDateEpochMs = 2000L),
                cachedTodo(id = "todo-2:1000", canonicalId = "todo-2", instanceDateEpochMs = 1000L),
            ),
            completedItems = listOf(
                cachedCompleted(id = "completed-1", instanceDateEpochMs = 1000L),
                cachedCompleted(id = "completed-2", instanceDateEpochMs = 2000L),
            ),
        )

        val updated = state.withDeletedTodoCached(
            canonicalId = "todo-1",
            instanceDateEpochMs = 1000L,
            isRecurringInstanceDelete = true,
            isLocalOnly = false,
            mutationId = "mutation-delete",
            timestampEpochMs = 1234L,
        )

        assertEquals(listOf("todo-1:2000", "todo-2:1000"), updated.todos.map { it.id })
        assertEquals(listOf("completed-2"), updated.completedItems.map { it.id })
        assertEquals(1, updated.pendingMutations.size)
        assertEquals(MutationKind.DELETE_TODO, updated.pendingMutations.single().kind)
        assertEquals(1000L, updated.pendingMutations.single().instanceDateEpochMs)
    }

    @Test
    fun `local recurring instance delete keeps pending create for the series`() {
        val state = OfflineSyncState(
            todos = listOf(
                cachedTodo(
                    id = "local-todo-1:1000",
                    canonicalId = "local-todo-1",
                    instanceDateEpochMs = 1000L,
                ),
                cachedTodo(
                    id = "local-todo-1:2000",
                    canonicalId = "local-todo-1",
                    instanceDateEpochMs = 2000L,
                ),
            ),
            pendingMutations = listOf(
                PendingMutationRecord(
                    mutationId = "mutation-create",
                    kind = MutationKind.CREATE_TODO,
                    targetId = "local-todo-1",
                    timestampEpochMs = 1000L,
                ),
            ),
        )

        val updated = state.withDeletedTodoCached(
            canonicalId = "local-todo-1",
            instanceDateEpochMs = 1000L,
            isRecurringInstanceDelete = true,
            isLocalOnly = true,
            mutationId = "mutation-delete",
            timestampEpochMs = 1234L,
        )

        assertEquals(listOf("local-todo-1:2000"), updated.todos.map { it.id })
        assertEquals(listOf("mutation-create"), updated.pendingMutations.map { it.mutationId })
    }

    private fun cachedTodo(
        id: String,
        canonicalId: String = "todo-1",
        instanceDateEpochMs: Long? = null,
    ): CachedTodoRecord {
        return CachedTodoRecord(
            id = id,
            canonicalId = canonicalId,
            title = id,
            dueEpochMs = instanceDateEpochMs,
            rrule = "FREQ=WEEKLY",
            instanceDateEpochMs = instanceDateEpochMs,
        )
    }

    private fun cachedCompleted(
        id: String,
        instanceDateEpochMs: Long?,
    ): CachedCompletedRecord {
        return CachedCompletedRecord(
            id = id,
            originalTodoId = "todo-1",
            title = id,
            priority = "Low",
            dueEpochMs = instanceDateEpochMs,
            instanceDateEpochMs = instanceDateEpochMs,
        )
    }
}
