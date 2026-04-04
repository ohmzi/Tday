package com.ohmz.tday.compose.core.data.cache

import com.ohmz.tday.compose.core.data.CachedCompletedRecord
import com.ohmz.tday.compose.core.data.CachedListRecord
import com.ohmz.tday.compose.core.data.CachedTodoRecord
import com.ohmz.tday.compose.core.model.CompletedItem
import com.ohmz.tday.compose.core.model.CompletedTodoDto
import com.ohmz.tday.compose.core.model.ListDto
import com.ohmz.tday.compose.core.model.ListSummary
import com.ohmz.tday.compose.core.model.TodoDto
import com.ohmz.tday.compose.core.model.TodoItem
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CacheMappersTest {

    private val fixedInstant: Instant = Instant.parse("2025-06-15T10:30:00Z")
    private val dueInstant: Instant = Instant.parse("2025-06-15T13:30:00Z")
    private val completedInstant: Instant = Instant.parse("2025-06-15T14:00:00Z")
    private val updatedInstant: Instant = Instant.parse("2025-06-15T12:00:00Z")

    // --- todoToCache / todoFromCache round-trip ---

    @Test
    fun `todoToCache preserves all fields`() {
        val todo = makeTodoItem()
        val cached = todoToCache(todo)

        assertEquals(todo.id, cached.id)
        assertEquals(todo.canonicalId, cached.canonicalId)
        assertEquals(todo.title, cached.title)
        assertEquals(todo.description, cached.description)
        assertEquals(todo.priority, cached.priority)
        assertEquals(todo.due.toEpochMilli(), cached.dueEpochMs)
        assertEquals(todo.rrule, cached.rrule)
        assertEquals(todo.instanceDateEpochMillis, cached.instanceDateEpochMs)
        assertEquals(todo.pinned, cached.pinned)
        assertEquals(todo.completed, cached.completed)
        assertEquals(todo.listId, cached.listId)
        assertEquals(todo.updatedAt?.toEpochMilli() ?: 0L, cached.updatedAtEpochMs)
    }

    @Test
    fun `todoFromCache preserves all fields`() {
        val cached = makeCachedTodo()
        val todo = todoFromCache(cached)

        assertEquals(cached.id, todo.id)
        assertEquals(cached.canonicalId, todo.canonicalId)
        assertEquals(cached.title, todo.title)
        assertEquals(cached.description, todo.description)
        assertEquals(cached.priority, todo.priority)
        assertEquals(Instant.ofEpochMilli(cached.dueEpochMs), todo.due)
        assertEquals(cached.rrule, todo.rrule)
        assertNotNull(todo.instanceDate)
        assertEquals(cached.instanceDateEpochMs, todo.instanceDateEpochMillis)
        assertEquals(cached.pinned, todo.pinned)
        assertEquals(cached.completed, todo.completed)
        assertEquals(cached.listId, todo.listId)
        assertEquals(Instant.ofEpochMilli(cached.updatedAtEpochMs), todo.updatedAt)
    }

    @Test
    fun `todoFromCache round-trips through todoToCache`() {
        val original = makeTodoItem()
        val roundTripped = todoFromCache(todoToCache(original))

        assertEquals(original, roundTripped)
    }

    @Test
    fun `todoFromCache returns null updatedAt when epoch is zero`() {
        val cached = makeCachedTodo().copy(updatedAtEpochMs = 0L)
        val todo = todoFromCache(cached)

        assertNull(todo.updatedAt)
    }

    @Test
    fun `todoFromCache returns null instanceDate when epoch is null`() {
        val cached = makeCachedTodo().copy(instanceDateEpochMs = null)
        val todo = todoFromCache(cached)

        assertNull(todo.instanceDate)
    }

    // --- listToCache / listFromCache round-trip ---

    @Test
    fun `listToCache preserves all fields`() {
        val list = makeListSummary()
        val cached = listToCache(list)

        assertEquals(list.id, cached.id)
        assertEquals(list.name, cached.name)
        assertEquals(list.color, cached.color)
        assertEquals(list.iconKey, cached.iconKey)
        assertEquals(list.todoCount, cached.todoCount)
        assertEquals(list.updatedAt?.toEpochMilli() ?: 0L, cached.updatedAtEpochMs)
    }

    @Test
    fun `listFromCache uses todoCountOverride`() {
        val cached = makeCachedList()
        val list = listFromCache(cached, todoCountOverride = 42)

        assertEquals(42, list.todoCount)
    }

    @Test
    fun `listFromCache returns null updatedAt when epoch is zero`() {
        val cached = makeCachedList().copy(updatedAtEpochMs = 0L)
        val list = listFromCache(cached, todoCountOverride = 0)

        assertNull(list.updatedAt)
    }

    // --- completedToCache / completedFromCache round-trip ---

    @Test
    fun `completedToCache preserves all fields`() {
        val item = makeCompletedItem()
        val cached = completedToCache(item)

        assertEquals(item.id, cached.id)
        assertEquals(item.originalTodoId, cached.originalTodoId)
        assertEquals(item.title, cached.title)
        assertEquals(item.priority, cached.priority)
        assertEquals(item.due.toEpochMilli(), cached.dueEpochMs)
        assertEquals(item.completedAt?.toEpochMilli() ?: 0L, cached.completedAtEpochMs)
    }

    @Test
    fun `completedFromCache returns null completedAt when epoch is zero`() {
        val cached = makeCachedCompleted().copy(completedAtEpochMs = 0L)
        val item = completedFromCache(cached)

        assertNull(item.completedAt)
    }

    // --- mapTodoDto ---

    @Test
    fun `mapTodoDto extracts canonicalId from compound id`() {
        val dto = makeTodoDto(id = "abc123:1718444400000")
        val item = mapTodoDto(dto)

        assertEquals("abc123", item.canonicalId)
        assertNotNull(item.instanceDate)
        assertEquals(1718444400000L, item.instanceDate!!.toEpochMilli())
    }

    @Test
    fun `mapTodoDto uses full id as canonicalId when no colon`() {
        val dto = makeTodoDto(id = "simple-id")
        val item = mapTodoDto(dto)

        assertEquals("simple-id", item.canonicalId)
        assertNull(item.instanceDate)
    }

    @Test
    fun `mapTodoDto prefers explicit instanceDate over suffix`() {
        val explicitDate = "2025-07-01T00:00:00Z"
        val dto = makeTodoDto(id = "abc:9999999999999").copy(instanceDate = explicitDate)
        val item = mapTodoDto(dto)

        assertEquals(Instant.parse(explicitDate), item.instanceDate)
    }

    @Test
    fun `mapTodoDto parses backend local datetime strings as UTC`() {
        val dto = makeTodoDto().copy(
            due = "2025-06-14T19:30:00",
        )

        val item = mapTodoDto(dto)

        assertEquals(Instant.parse("2025-06-14T19:30:00Z"), item.due)
    }

    // --- mapCompletedDto ---

    @Test
    fun `mapCompletedDto maps all fields`() {
        val dto = makeCompletedTodoDto()
        val item = mapCompletedDto(dto)

        assertEquals(dto.id, item.id)
        assertEquals(dto.originalTodoID, item.originalTodoId)
        assertEquals(dto.title, item.title)
        assertEquals(dto.priority, item.priority)
    }

    // --- mapListDto ---

    @Test
    fun `mapListDto maps all fields`() {
        val dto = makeListDto()
        val item = mapListDto(dto)

        assertEquals(dto.id, item.id)
        assertEquals(dto.name, item.name)
        assertEquals(dto.color, item.color)
        assertEquals(dto.todoCount, item.todoCount)
    }

    @Test
    fun `mapListDto uses iconFallback when dto iconKey is null`() {
        val dto = makeListDto().copy(iconKey = null)
        val item = mapListDto(dto, iconFallback = "star")

        assertEquals("star", item.iconKey)
    }

    // --- matchesCompletedRecord ---

    @Test
    fun `matchesCompletedRecord matches on id`() {
        val record = makeCachedCompleted()
        assertTrue(matchesCompletedRecord(record, record.id, "other", null, null))
    }

    @Test
    fun `matchesCompletedRecord matches on resolvedItemId`() {
        val record = makeCachedCompleted()
        assertTrue(matchesCompletedRecord(record, "nope", record.id, null, null))
    }

    @Test
    fun `matchesCompletedRecord matches on originalTodoId with instance`() {
        val record = makeCachedCompleted().copy(
            originalTodoId = "todo-1",
            instanceDateEpochMs = 12345L,
        )
        assertTrue(matchesCompletedRecord(record, "nope", "also-nope", "todo-1", 12345L))
    }

    @Test
    fun `matchesCompletedRecord rejects mismatched originalTodoId`() {
        val record = makeCachedCompleted().copy(originalTodoId = "todo-1")
        assertFalse(matchesCompletedRecord(record, "nope", "also-nope", "todo-2", null))
    }

    // --- todoMergeKey ---

    @Test
    fun `todoMergeKey uses canonicalId and instanceDate`() {
        assertEquals("abc::1000", todoMergeKey("abc", 1000L))
    }

    @Test
    fun `todoMergeKey uses MIN_VALUE for null instanceDate`() {
        assertEquals("abc::${Long.MIN_VALUE}", todoMergeKey("abc", null))
    }

    // --- parseInstant ---

    @Test
    fun `parseInstant parses valid ISO string`() {
        val instant = parseInstant("2025-06-15T10:30:00Z")
        assertEquals(fixedInstant, instant)
    }

    @Test
    fun `parseInstant parses backend UTC local datetime string`() {
        val instant = parseInstant("2025-06-15T10:30:00")
        assertEquals(fixedInstant, instant)
    }

    @Test
    fun `parseInstant returns fallback for invalid string`() {
        val before = Instant.now()
        val result = parseInstant("not-a-date")
        val after = Instant.now()
        assertTrue(result in before..after)
    }

    // --- parseOptionalInstant ---

    @Test
    fun `parseOptionalInstant returns null for null input`() {
        assertNull(parseOptionalInstant(null))
    }

    @Test
    fun `parseOptionalInstant returns null for blank input`() {
        assertNull(parseOptionalInstant("  "))
    }

    @Test
    fun `parseOptionalInstant returns null for invalid input`() {
        assertNull(parseOptionalInstant("garbage"))
    }

    @Test
    fun `parseOptionalInstant parses valid string`() {
        assertEquals(fixedInstant, parseOptionalInstant("2025-06-15T10:30:00Z"))
    }

    @Test
    fun `parseOptionalInstant parses backend UTC local datetime string`() {
        assertEquals(fixedInstant, parseOptionalInstant("2025-06-15T10:30:00"))
    }

    // --- factory helpers ---

    private fun makeTodoItem() = TodoItem(
        id = "t1:${fixedInstant.toEpochMilli()}",
        canonicalId = "t1",
        title = "Buy groceries",
        description = "Milk, eggs, bread",
        priority = "High",
        due = dueInstant,
        rrule = "FREQ=WEEKLY",
        instanceDate = fixedInstant,
        pinned = true,
        completed = false,
        listId = "list-1",
        updatedAt = updatedInstant,
    )

    private fun makeCachedTodo() = CachedTodoRecord(
        id = "t1:${fixedInstant.toEpochMilli()}",
        canonicalId = "t1",
        title = "Buy groceries",
        description = "Milk, eggs, bread",
        priority = "High",
        dueEpochMs = dueInstant.toEpochMilli(),
        rrule = "FREQ=WEEKLY",
        instanceDateEpochMs = fixedInstant.toEpochMilli(),
        pinned = true,
        completed = false,
        listId = "list-1",
        updatedAtEpochMs = updatedInstant.toEpochMilli(),
    )

    private fun makeListSummary() = ListSummary(
        id = "list-1",
        name = "Shopping",
        color = "#FF0000",
        iconKey = "cart",
        todoCount = 5,
        updatedAt = updatedInstant,
    )

    private fun makeCachedList() = CachedListRecord(
        id = "list-1",
        name = "Shopping",
        color = "#FF0000",
        iconKey = "cart",
        todoCount = 5,
        updatedAtEpochMs = updatedInstant.toEpochMilli(),
    )

    private fun makeCompletedItem() = CompletedItem(
        id = "c1",
        originalTodoId = "t1",
        title = "Completed task",
        description = "desc",
        priority = "Low",
        due = dueInstant,
        completedAt = completedInstant,
        rrule = null,
        instanceDate = null,
        listName = "Work",
        listColor = "#00FF00",
    )

    private fun makeCachedCompleted() = CachedCompletedRecord(
        id = "c1",
        originalTodoId = "t1",
        title = "Completed task",
        description = "desc",
        priority = "Low",
        dueEpochMs = dueInstant.toEpochMilli(),
        completedAtEpochMs = completedInstant.toEpochMilli(),
        rrule = null,
        instanceDateEpochMs = null,
        listName = "Work",
        listColor = "#00FF00",
    )

    private fun makeTodoDto(id: String = "t1") = TodoDto(
        id = id,
        title = "Test todo",
        priority = "Medium",
        due = dueInstant.toString(),
        updatedAt = updatedInstant.toString(),
    )

    private fun makeCompletedTodoDto() = CompletedTodoDto(
        id = "c1",
        originalTodoID = "t1",
        title = "Done task",
        priority = "High",
        due = dueInstant.toString(),
        completedAt = completedInstant.toString(),
    )

    private fun makeListDto() = ListDto(
        id = "list-1",
        name = "Groceries",
        color = "#AABBCC",
        todoCount = 3,
        iconKey = "cart",
        updatedAt = updatedInstant.toString(),
    )
}
