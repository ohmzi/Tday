package com.ohmz.tday.services

import com.ohmz.tday.shared.model.CompletedTodoDto
import com.ohmz.tday.shared.model.ExportedTodoDto
import com.ohmz.tday.shared.model.FloaterDto
import com.ohmz.tday.shared.model.ListDto
import com.ohmz.tday.shared.model.TdayExport
import com.ohmz.tday.shared.model.TodoDto
import com.ohmz.tday.shared.model.TodoInstanceDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class ExportRemapTest {
    private var counter = 0
    private fun sequentialId(): String = "new-${counter++}"

    @Test
    fun `no collision keeps every id and reports zero remaps`() {
        val export = TdayExport(
            lists = listOf(ListDto(id = "list-1", name = "Work")),
            todos = listOf(
                ExportedTodoDto(
                    todo = TodoDto(id = "todo-1", due = "2026-01-01T00:00:00", listID = "list-1"),
                ),
            ),
        )

        val result = ExportRemap.remapCollisions(export, idExists = { false }, newId = ::sequentialId)

        assertEquals(0, result.remappedIds)
        assertEquals("list-1", result.export.lists[0].id)
        assertEquals("todo-1", result.export.todos[0].todo.id)
        assertEquals("list-1", result.export.todos[0].todo.listID)
    }

    @Test
    fun `colliding ids are minted fresh and every reference follows`() {
        val export = TdayExport(
            lists = listOf(ListDto(id = "list-1", name = "Work")),
            todos = listOf(
                ExportedTodoDto(
                    todo = TodoDto(id = "todo-1", due = "2026-01-01T00:00:00", listID = "list-1"),
                    instances = listOf(TodoInstanceDto(id = "inst-1", instanceDate = "2026-01-02T00:00:00")),
                ),
            ),
            completedTodos = listOf(
                CompletedTodoDto(
                    id = "ct-1",
                    title = "done",
                    due = "2026-01-01T00:00:00",
                    completedOnTime = true,
                    originalTodoID = "todo-1",
                    listID = "list-1",
                ),
            ),
        )

        // Every original id already exists on the server (freshly minted ids do not).
        val existing = setOf("list-1", "todo-1", "inst-1", "ct-1")
        val result = ExportRemap.remapCollisions(export, idExists = existing::contains, newId = ::sequentialId)

        val newListId = result.export.lists[0].id
        val newTodoId = result.export.todos[0].todo.id
        assertNotEquals("list-1", newListId)
        assertNotEquals("todo-1", newTodoId)
        // The todo's list reference is rewritten to the list's new id.
        assertEquals(newListId, result.export.todos[0].todo.listID)
        // The nested instance's own PK is remapped but stays under its parent.
        assertNotEquals("inst-1", result.export.todos[0].instances[0].id)
        // The completed row: PK remapped, and both its list ref and the soft
        // back-reference to the source todo follow their new ids.
        assertNotEquals("ct-1", result.export.completedTodos[0].id)
        assertEquals(newTodoId, result.export.completedTodos[0].originalTodoID)
        assertEquals(newListId, result.export.completedTodos[0].listID)
        // list + todo + instance + completed = 4 PKs actually changed.
        assertEquals(4, result.remappedIds)
    }

    @Test
    fun `only the colliding id is remapped`() {
        val export = TdayExport(
            lists = listOf(ListDto(id = "keep", name = "A"), ListDto(id = "clash", name = "B")),
        )

        val result = ExportRemap.remapCollisions(export, idExists = { it == "clash" }, newId = ::sequentialId)

        assertEquals("keep", result.export.lists[0].id)
        assertNotEquals("clash", result.export.lists[1].id)
        assertEquals(1, result.remappedIds)
    }

    @Test
    fun `a primary key that repeats within the bundle is remapped`() {
        val export = TdayExport(
            floaters = listOf(FloaterDto(id = "dup"), FloaterDto(id = "dup")),
        )

        val result = ExportRemap.remapCollisions(export, idExists = { false }, newId = ::sequentialId)

        assertEquals("dup", result.export.floaters[0].id)
        assertNotEquals("dup", result.export.floaters[1].id)
        assertEquals(1, result.remappedIds)
    }

    @Test
    fun `a freshly minted id that itself collides is skipped`() {
        val minted = ArrayDeque(listOf("taken", "fresh"))
        val export = TdayExport(lists = listOf(ListDto(id = "list-1", name = "x")))

        val result = ExportRemap.remapCollisions(
            export,
            idExists = { it == "list-1" || it == "taken" },
            newId = { minted.removeFirst() },
        )

        assertEquals("fresh", result.export.lists[0].id)
    }
}
