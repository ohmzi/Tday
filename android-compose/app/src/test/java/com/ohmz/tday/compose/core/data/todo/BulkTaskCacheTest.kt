package com.ohmz.tday.compose.core.data.todo

import com.ohmz.tday.compose.core.data.CachedCompletedRecord
import com.ohmz.tday.compose.core.data.CachedFloaterRecord
import com.ohmz.tday.compose.core.data.CachedTodoRecord
import com.ohmz.tday.compose.core.data.MutationKind
import com.ohmz.tday.compose.core.data.OfflineSyncState
import com.ohmz.tday.compose.core.data.PendingMutationRecord
import com.ohmz.tday.compose.core.model.TodoItem
import com.ohmz.tday.shared.bulk.BulkAction
import com.ohmz.tday.shared.bulk.BulkSelectionPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * The pure `OfflineSyncState` half of the bulk actions, folded the way
 * [BulkTaskRepository] folds it: one transform per selected row, all inside a
 * single cache write. Everything a bulk action can get wrong that does not need
 * Room, Hilt or a dispatcher to prove is pinned here.
 *
 * The `skipcq: KT-W1042` markers below are deliberate. Every literal they cover
 * is a per-test fixture identity, not a shared value: `"todo-1"` in the staging
 * test and `"todo-1"` in the delete test are unrelated rows that only happen to
 * share a name, so a shared constant would imply a coupling that does not exist.
 * The assertions are read as input/output pairs — `assertEquals(listOf("todo-3"),
 * pruned.todos.map { it.id })` shows at a glance which fixture survived — and the
 * title/description cases assert that a mutation echoes the row's own fields, so
 * seeing the same literal on the arrange and the assert side is the point.
 */
class BulkTaskCacheTest {

    @Test
    fun `staging a batch prunes every row and merges into one restorable snapshot`() {
        val state = OfflineSyncState(
            todos = listOf(
                cachedTodo(id = "todo-1", canonicalId = "todo-1"),  // skipcq: KT-W1042
                cachedTodo(id = "todo-2", canonicalId = "todo-2"),  // skipcq: KT-W1042
                cachedTodo(id = "todo-3", canonicalId = "todo-3"),  // skipcq: KT-W1042
            ),
            completedItems = listOf(
                cachedCompleted(id = "completed-1", originalTodoId = "todo-1"),  // skipcq: KT-W1042
                cachedCompleted(id = "completed-3", originalTodoId = "todo-3"),
            ),
        )

        val (pruned, staged) = stageBatch(state, listOf("todo-1", "todo-2"))

        assertEquals(listOf("todo-3"), pruned.todos.map { it.id })
        assertEquals(listOf("completed-3"), pruned.completedItems.map { it.id })
        // The merged snapshot carries both rows, so a single undo restores the
        // whole batch rather than the last of it.
        assertEquals(listOf("todo-1", "todo-2"), staged.removedTodos.map { it.id })
        assertEquals(listOf("completed-1"), staged.removedCompletedItems.map { it.id })
        // Staging never tells the server anything: that is what makes Undo free.
        assertTrue(pruned.pendingMutations.isEmpty())
    }

    @Test
    fun `staging a batch leaves the pending create of a task that was not selected`() {
        val state = OfflineSyncState(
            todos = listOf(
                cachedTodo(id = "local-todo-1", canonicalId = "local-todo-1"),  // skipcq: KT-W1042
                cachedTodo(id = "local-todo-2", canonicalId = "local-todo-2"),  // skipcq: KT-W1042
            ),
            pendingMutations = listOf(
                pendingCreate("local-todo-1"),
                pendingCreate("local-todo-2"),
            ),
        )

        val (pruned, staged) = stageBatch(state, listOf("local-todo-1"))

        assertEquals(listOf("local-todo-2"), pruned.todos.map { it.id })
        assertEquals(
            listOf("create-local-todo-2"),
            pruned.pendingMutations.map { it.mutationId },
        )
        assertEquals(
            listOf("create-local-todo-1"),
            staged.removedPendingMutations.map { it.mutationId },
        )
    }

    @Test
    fun `bulk delete queues exactly one DELETE_TODO per selected row`() {
        val state = OfflineSyncState(
            todos = listOf(
                cachedTodo(id = "todo-1", canonicalId = "todo-1"),
                cachedTodo(id = "todo-2", canonicalId = "todo-2"),
                cachedTodo(id = "todo-3", canonicalId = "todo-3"),
            ),
        )

        val next = listOf("todo-1", "todo-2").foldIndexed(state) { index, current, id ->
            current.withDeletedTodoCached(
                canonicalId = id,
                instanceDateEpochMs = null,
                isRecurringInstanceDelete = false,
                isLocalOnly = false,
                mutationId = "delete-$index",
                timestampEpochMs = 1_000L,
            )
        }

        assertEquals(listOf("todo-3"), next.todos.map { it.id })
        assertEquals(2, next.pendingMutations.size)
        assertTrue(next.pendingMutations.all { it.kind == MutationKind.DELETE_TODO })
        assertEquals(listOf("todo-1", "todo-2"), next.pendingMutations.map { it.targetId })
    }

    @Test
    fun `bulk complete of a recurring occurrence carries its instance date`() {
        val state = OfflineSyncState(
            todos = listOf(
                cachedTodo(
                    id = "todo-1:1000",
                    canonicalId = "todo-1",
                    rrule = "FREQ=WEEKLY",  // skipcq: KT-W1042
                    instanceDateEpochMs = 1_000L,
                ),
                cachedTodo(
                    id = "todo-1:2000",
                    canonicalId = "todo-1",
                    rrule = "FREQ=WEEKLY",
                    instanceDateEpochMs = 2_000L,
                ),
            ),
        )

        val next = state.withCompletedTodoCached(
            todo = todoItem(
                id = "todo-1:1000",
                canonicalId = "todo-1",
                rrule = "FREQ=WEEKLY",
                instanceDate = Instant.ofEpochMilli(1_000L),
            ),
            timestampEpochMs = 5_000L,
            mutationId = "complete-1",  // skipcq: KT-W1042
            completedRecordId = "local-completed-1",
        )

        // Only the occurrence that was completed flips; the rest of the series
        // is untouched.
        assertEquals(
            listOf(true, false),
            next.todos.map { it.completed },
        )
        val mutation = next.pendingMutations.single()
        assertEquals(MutationKind.COMPLETE_TODO_INSTANCE, mutation.kind)
        // Without this the server writes a history row and leaves the task
        // standing — the trap the design note calls out.
        assertEquals(1_000L, mutation.instanceDateEpochMs)
        assertEquals(1_000L, next.completedItems.single().instanceDateEpochMs)
    }

    @Test
    fun `bulk complete of a plain task queues COMPLETE_TODO with no instance date`() {
        val state = OfflineSyncState(todos = listOf(cachedTodo(id = "todo-1", canonicalId = "todo-1")))

        val next = state.withCompletedTodoCached(
            todo = todoItem(id = "todo-1", canonicalId = "todo-1"),
            timestampEpochMs = 5_000L,
            mutationId = "complete-1",
            completedRecordId = "local-completed-1",
        )

        val mutation = next.pendingMutations.single()
        assertEquals(MutationKind.COMPLETE_TODO, mutation.kind)
        assertNull(mutation.instanceDateEpochMs)
        assertTrue(next.todos.single().completed)
    }

    @Test
    fun `bulk priority updates the cached rows and queues one SET_PRIORITY each`() {
        val state = OfflineSyncState(
            todos = listOf(
                cachedTodo(id = "todo-1", canonicalId = "todo-1"),
                cachedTodo(id = "todo-2", canonicalId = "todo-2"),
            ),
        )

        val next = listOf("todo-1", "todo-2").foldIndexed(state) { index, current, id ->
            current.withTodoPriorityCached(
                todo = todoItem(id = id, canonicalId = id),
                priority = "High",
                mutationId = "priority-$index",
                timestampEpochMs = 2_000L,
            )
        }

        assertEquals(listOf("High", "High"), next.todos.map { it.priority })
        assertEquals(2, next.pendingMutations.size)
        assertTrue(next.pendingMutations.all { it.kind == MutationKind.SET_PRIORITY })
        assertEquals(listOf("High", "High"), next.pendingMutations.map { it.priority })
    }

    @Test
    fun `bulk move rewrites the list and replays the row's own fields, not blanks`() {
        val state = OfflineSyncState(
            todos = listOf(cachedTodo(id = "todo-1", canonicalId = "todo-1", listId = "list-a")),  // skipcq: KT-W1042
        )

        val next = state.withTodoListCached(
            todo = todoItem(
                id = "todo-1",
                canonicalId = "todo-1",
                title = "Buy milk",
                description = "semi-skimmed",
                priority = "Medium",
                due = Instant.ofEpochMilli(9_000L),
                listId = "list-a",
            ),
            listId = "list-b",  // skipcq: KT-W1042
            mutationId = "move-1",
            timestampEpochMs = 3_000L,
        )

        assertEquals("list-b", next.todos.single().listId)
        val mutation = next.pendingMutations.single()
        assertEquals(MutationKind.UPDATE_TODO, mutation.kind)
        assertEquals("list-b", mutation.listId)
        // A whole-record patch that dropped these would clear them on replay.
        assertEquals("Buy milk", mutation.title)
        assertEquals("semi-skimmed", mutation.description)
        assertEquals("Medium", mutation.priority)
        assertEquals(9_000L, mutation.dueEpochMs)
    }

    @Test
    fun `moving to no list clears the list assignment`() {
        val state = OfflineSyncState(
            todos = listOf(cachedTodo(id = "todo-1", canonicalId = "todo-1", listId = "list-a")),
        )

        val next = state.withTodoListCached(
            todo = todoItem(id = "todo-1", canonicalId = "todo-1", listId = "list-a"),
            listId = null,
            mutationId = "move-1",
            timestampEpochMs = 3_000L,
        )

        assertNull(next.todos.single().listId)
        assertNull(next.pendingMutations.single().listId)
    }

    @Test
    fun `a floater edit keeps the fields it is not changing`() {
        val state = OfflineSyncState(
            floaters = listOf(
                CachedFloaterRecord(
                    id = "floater-1",  // skipcq: KT-W1042
                    canonicalId = "floater-1",
                    title = "Water the plants",  // skipcq: KT-W1042
                    description = "the big one too",  // skipcq: KT-W1042
                    priority = "Low",
                    listId = "floater-list-a",  // skipcq: KT-W1042
                ),
            ),
        )

        val next = state.withFloaterEditCached(
            floater = todoItem(
                id = "floater-1",
                canonicalId = "floater-1",
                title = "Water the plants",
                description = "the big one too",
                priority = "Low",
                listId = "floater-list-a",
            ),
            priority = "High",
            listId = "floater-list-a",
            mutationId = "edit-1",
            timestampEpochMs = 4_000L,
        )

        assertEquals("High", next.floaters.single().priority)
        assertEquals("floater-list-a", next.floaters.single().listId)
        val mutation = next.pendingMutations.single()
        assertEquals(MutationKind.UPDATE_FLOATER, mutation.kind)
        assertEquals("High", mutation.priority)
        // The replay clears anything the mutation leaves null, so the title and
        // notes have to ride along even though this is "just" a priority change.
        assertEquals("Water the plants", mutation.title)
        assertEquals("the big one too", mutation.description)
    }

    @Test
    fun `the effective set the screen hands the repository never carries a series into delete`() {
        val selection = listOf(
            todoItem(id = "todo-1", canonicalId = "todo-1"),
            todoItem(
                id = "todo-2:1000",
                canonicalId = "todo-2",
                rrule = "FREQ=DAILY",  // skipcq: KT-W1042
                instanceDate = Instant.ofEpochMilli(1_000L),
            ),
        )

        val completeTargets = BulkSelectionPolicy.effectiveSelection(
            action = BulkAction.COMPLETE,
            selection = selection,
            hasInstanceDate = { it.instanceDate != null },
            isRecurring = { it.isRecurring },
        )
        val deleteTargets = BulkSelectionPolicy.effectiveSelection(
            action = BulkAction.DELETE,
            selection = selection,
            isRecurring = { it.isRecurring },
        )

        assertEquals(listOf("todo-1", "todo-2:1000"), completeTargets.map { it.id })
        assertEquals(listOf("todo-1"), deleteTargets.map { it.id })
    }

    @Test
    fun `bulk complete skips a repeating row that has no occurrence to complete`() {
        // What the cache actually holds for a repeating task: the server sends one
        // row per recurring template and never an instanceDate, and an offline
        // create stores instanceDateEpochMs = null. Queuing COMPLETE_TODO for it
        // replays as a complete with no instanceDate, which the backend turns into
        // a CompletedTodos row that marks nothing complete — history gains an entry
        // for something that never happened and the task stays on screen.
        val selection = listOf(
            todoItem(id = "todo-1", canonicalId = "todo-1"),
            todoItem(id = "todo-2", canonicalId = "todo-2", rrule = "FREQ=DAILY"),
        )

        val completeTargets = BulkSelectionPolicy.effectiveSelection(
            action = BulkAction.COMPLETE,
            selection = selection,
            hasInstanceDate = { it.instanceDate != null },
            isRecurring = { it.isRecurring },
        )

        assertEquals(listOf("todo-1"), completeTargets.map { it.id })
    }

    @Test
    fun `a repeating row with no occurrence would queue a plain complete if it slipped through`() {
        // Pins why the filter above matters, at the layer underneath it.
        val state = OfflineSyncState(
            todos = listOf(
                cachedTodo(id = "todo-2", canonicalId = "todo-2", rrule = "FREQ=DAILY"),
            ),
        )

        val next = state.withCompletedTodoCached(
            todo = todoItem(id = "todo-2", canonicalId = "todo-2", rrule = "FREQ=DAILY"),
            timestampEpochMs = 5_000L,
            mutationId = "complete-1",
            completedRecordId = "completed-1",
        )

        assertEquals(MutationKind.COMPLETE_TODO, next.pendingMutations.single().kind)
        assertNull(next.pendingMutations.single().instanceDateEpochMs)
    }

    private fun stageBatch(
        state: OfflineSyncState,
        canonicalIds: List<String>,
    ): Pair<OfflineSyncState, StagedTodoDeletion> {
        var staged = StagedTodoDeletion()
        val next = canonicalIds.fold(state) { current, id ->
            val (pruned, removed) = current.withStagedTodoDeletion(
                canonicalId = id,
                instanceDateEpochMs = null,
                isRecurringInstanceDelete = false,
                isLocalOnly = id.startsWith("local-todo-"),
            )
            staged = staged + removed
            pruned
        }
        return next to staged
    }

    private fun pendingCreate(targetId: String): PendingMutationRecord = PendingMutationRecord(
        mutationId = "create-$targetId",
        kind = MutationKind.CREATE_TODO,
        targetId = targetId,
        timestampEpochMs = 1L,
    )

    private fun cachedTodo(
        id: String,
        canonicalId: String,
        rrule: String? = null,
        instanceDateEpochMs: Long? = null,
        listId: String? = null,
    ): CachedTodoRecord = CachedTodoRecord(
        id = id,
        canonicalId = canonicalId,
        title = id,
        priority = "Low",
        dueEpochMs = 1L,
        rrule = rrule,
        instanceDateEpochMs = instanceDateEpochMs,
        listId = listId,
    )

    private fun cachedCompleted(id: String, originalTodoId: String): CachedCompletedRecord =
        CachedCompletedRecord(
            id = id,
            originalTodoId = originalTodoId,
            title = id,
            priority = "Low",
        )

    private fun todoItem(
        id: String,
        canonicalId: String,
        title: String = id,
        description: String? = null,
        priority: String = "Low",
        due: Instant? = null,
        rrule: String? = null,
        instanceDate: Instant? = null,
        listId: String? = null,
    ): TodoItem = TodoItem(
        id = id,
        canonicalId = canonicalId,
        title = title,
        description = description,
        priority = priority,
        due = due,
        rrule = rrule,
        instanceDate = instanceDate,
        pinned = false,
        completed = false,
        listId = listId,
    )
}
