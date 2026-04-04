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
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset

internal const val LOCAL_TODO_PREFIX = "local-todo-"
internal const val LOCAL_LIST_PREFIX = "local-list-"
internal const val LOCAL_COMPLETED_PREFIX = "local-completed-"

internal fun todoToCache(todo: TodoItem): CachedTodoRecord {
    return CachedTodoRecord(
        id = todo.id,
        canonicalId = todo.canonicalId,
        title = todo.title,
        description = todo.description,
        priority = todo.priority,
        dueEpochMs = todo.due.toEpochMilli(),
        rrule = todo.rrule,
        instanceDateEpochMs = todo.instanceDateEpochMillis,
        pinned = todo.pinned,
        completed = todo.completed,
        listId = todo.listId,
        updatedAtEpochMs = todo.updatedAt?.toEpochMilli() ?: 0L,
    )
}

internal fun todoFromCache(cache: CachedTodoRecord): TodoItem {
    return TodoItem(
        id = cache.id,
        canonicalId = cache.canonicalId,
        title = cache.title,
        description = cache.description,
        priority = cache.priority,
        due = Instant.ofEpochMilli(cache.dueEpochMs),
        rrule = cache.rrule,
        instanceDate = cache.instanceDateEpochMs?.let(Instant::ofEpochMilli),
        pinned = cache.pinned,
        completed = cache.completed,
        listId = cache.listId,
        updatedAt = if (cache.updatedAtEpochMs > 0L) {
            Instant.ofEpochMilli(cache.updatedAtEpochMs)
        } else {
            null
        },
    )
}

internal fun listToCache(list: ListSummary): CachedListRecord {
    return CachedListRecord(
        id = list.id,
        name = list.name,
        color = list.color,
        iconKey = list.iconKey,
        todoCount = list.todoCount,
        updatedAtEpochMs = list.updatedAt?.toEpochMilli() ?: 0L,
    )
}

internal fun listFromCache(
    cache: CachedListRecord,
    todoCountOverride: Int,
): ListSummary {
    return ListSummary(
        id = cache.id,
        name = cache.name,
        color = cache.color,
        iconKey = cache.iconKey,
        todoCount = todoCountOverride,
        updatedAt = if (cache.updatedAtEpochMs > 0L) {
            Instant.ofEpochMilli(cache.updatedAtEpochMs)
        } else {
            null
        },
    )
}

internal fun completedToCache(item: CompletedItem): CachedCompletedRecord {
    return CachedCompletedRecord(
        id = item.id,
        originalTodoId = item.originalTodoId,
        title = item.title,
        description = item.description,
        priority = item.priority,
        dueEpochMs = item.due.toEpochMilli(),
        completedAtEpochMs = item.completedAt?.toEpochMilli() ?: 0L,
        rrule = item.rrule,
        instanceDateEpochMs = item.instanceDate?.toEpochMilli(),
        listName = item.listName,
        listColor = item.listColor,
    )
}

internal fun completedFromCache(cache: CachedCompletedRecord): CompletedItem {
    return CompletedItem(
        id = cache.id,
        originalTodoId = cache.originalTodoId,
        title = cache.title,
        description = cache.description,
        priority = cache.priority,
        due = Instant.ofEpochMilli(cache.dueEpochMs),
        completedAt = if (cache.completedAtEpochMs > 0L) {
            Instant.ofEpochMilli(cache.completedAtEpochMs)
        } else {
            null
        },
        rrule = cache.rrule,
        instanceDate = cache.instanceDateEpochMs?.let(Instant::ofEpochMilli),
        listName = cache.listName,
        listColor = cache.listColor,
    )
}

internal fun mapTodoDto(dto: TodoDto): TodoItem {
    val canonicalId = dto.id.substringBefore(':')
    val suffixInstance = dto.id.substringAfter(':', "")
        .toLongOrNull()
        ?.let(Instant::ofEpochMilli)
    val explicitInstance = parseOptionalInstant(dto.instanceDate)

    return TodoItem(
        id = dto.id,
        canonicalId = canonicalId,
        title = dto.title,
        description = dto.description,
        priority = dto.priority,
        due = parseInstant(dto.due),
        rrule = dto.rrule,
        instanceDate = explicitInstance ?: suffixInstance,
        pinned = dto.pinned,
        completed = dto.completed,
        listId = dto.listID,
        updatedAt = parseOptionalInstant(dto.updatedAt),
    )
}

internal fun mapCompletedDto(dto: CompletedTodoDto): CompletedItem {
    return CompletedItem(
        id = dto.id,
        originalTodoId = dto.originalTodoID,
        title = dto.title,
        description = dto.description,
        priority = dto.priority,
        due = parseInstant(dto.due),
        completedAt = parseOptionalInstant(dto.completedAt),
        rrule = dto.rrule,
        instanceDate = parseOptionalInstant(dto.instanceDate),
        listName = dto.listName,
        listColor = dto.listColor,
    )
}

internal fun mapListDto(dto: ListDto, iconFallback: String? = null): ListSummary {
    return ListSummary(
        id = dto.id,
        name = dto.name,
        color = dto.color,
        iconKey = dto.iconKey ?: iconFallback,
        todoCount = dto.todoCount,
        updatedAt = parseOptionalInstant(dto.updatedAt),
    )
}

internal fun matchesCompletedRecord(
    record: CachedCompletedRecord,
    itemId: String,
    resolvedItemId: String,
    canonicalTodoId: String?,
    instanceDateEpochMs: Long?,
): Boolean {
    if (record.id == itemId || record.id == resolvedItemId) return true
    if (canonicalTodoId.isNullOrBlank()) return false
    if (record.originalTodoId != canonicalTodoId) return false
    return record.instanceDateEpochMs == instanceDateEpochMs
}

internal fun todoMergeKey(todo: CachedTodoRecord): String {
    return todoMergeKey(
        canonicalId = todo.canonicalId,
        instanceDateEpochMs = todo.instanceDateEpochMs,
    )
}

internal fun todoMergeKey(
    canonicalId: String,
    instanceDateEpochMs: Long?,
): String {
    return "$canonicalId::${instanceDateEpochMs ?: Long.MIN_VALUE}"
}

private fun parseUtcLikeInstant(value: String): Instant? {
    return runCatching { Instant.parse(value) }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(value).toInstant() }.getOrNull()
        ?: runCatching { LocalDateTime.parse(value).toInstant(ZoneOffset.UTC) }.getOrNull()
}

internal fun parseInstant(value: String): Instant {
    return parseUtcLikeInstant(value) ?: Instant.now()
}

internal fun parseOptionalInstant(value: String?): Instant? {
    if (value.isNullOrBlank()) return null
    return parseUtcLikeInstant(value)
}
