package com.ohmz.tday.compose.core.data.cache

import com.ohmz.tday.compose.core.data.CachedCompletedFloaterRecord
import com.ohmz.tday.compose.core.data.CachedCompletedRecord
import com.ohmz.tday.compose.core.data.CachedFloaterListRecord
import com.ohmz.tday.compose.core.data.CachedFloaterRecord
import com.ohmz.tday.compose.core.data.CachedListRecord
import com.ohmz.tday.compose.core.data.CachedTodoRecord
import com.ohmz.tday.compose.core.model.CompletedFloaterDto
import com.ohmz.tday.compose.core.model.CompletedItem
import com.ohmz.tday.compose.core.model.CompletedTodoDto
import com.ohmz.tday.compose.core.model.FloaterDto
import com.ohmz.tday.compose.core.model.FloaterListDto
import com.ohmz.tday.compose.core.model.ListDto
import com.ohmz.tday.compose.core.model.ListSummary
import com.ohmz.tday.compose.core.model.TodoDto
import com.ohmz.tday.compose.core.model.TodoItem
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset

internal const val LOCAL_TODO_PREFIX = "local-todo-"
internal const val LOCAL_FLOATER_PREFIX = "local-floater-"
internal const val LOCAL_LIST_PREFIX = "local-list-"
internal const val LOCAL_FLOATER_LIST_PREFIX = "local-floater-list-"
internal const val LOCAL_COMPLETED_PREFIX = "local-completed-"
internal const val LOCAL_COMPLETED_FLOATER_PREFIX = "local-completed-floater-"

internal fun todoToCache(todo: TodoItem): CachedTodoRecord {
    return CachedTodoRecord(
        id = todo.id,
        canonicalId = todo.canonicalId,
        title = todo.title,
        description = todo.description,
        priority = todo.priority,
        dueEpochMs = todo.due?.toEpochMilli(),
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
        due = cache.dueEpochMs?.let(Instant::ofEpochMilli),
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

internal fun floaterToCache(floater: TodoItem): CachedFloaterRecord {
    return CachedFloaterRecord(
        id = floater.id,
        canonicalId = floater.canonicalId,
        title = floater.title,
        description = floater.description,
        priority = floater.priority,
        pinned = floater.pinned,
        completed = floater.completed,
        listId = floater.listId,
        updatedAtEpochMs = floater.updatedAt?.toEpochMilli() ?: 0L,
    )
}

internal fun floaterFromCache(cache: CachedFloaterRecord): TodoItem {
    return TodoItem(
        id = cache.id,
        canonicalId = cache.canonicalId,
        title = cache.title,
        description = cache.description,
        priority = cache.priority,
        due = null,
        rrule = null,
        instanceDate = null,
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
        createdAtEpochMs = list.createdAt?.toEpochMilli() ?: 0L,
        myRole = list.myRole,
        isShared = list.isShared,
        memberCount = list.memberCount,
        ownerUsername = list.ownerUsername,
    )
}

internal fun orderListsLikeWeb(lists: List<CachedListRecord>): List<CachedListRecord> {
    if (lists.none { it.createdAtEpochMs > 0L }) return lists
    return lists.withIndex()
        .sortedWith(
            compareByDescending<IndexedValue<CachedListRecord>> { it.value.createdAtEpochMs }
                .thenBy { it.index },
        )
        .map { it.value }
}

internal fun orderFloaterListsLikeWeb(lists: List<CachedFloaterListRecord>): List<CachedFloaterListRecord> {
    if (lists.none { it.createdAtEpochMs > 0L }) return lists
    return lists.withIndex()
        .sortedWith(
            compareByDescending<IndexedValue<CachedFloaterListRecord>> { it.value.createdAtEpochMs }
                .thenBy { it.index },
        )
        .map { it.value }
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
        createdAt = if (cache.createdAtEpochMs > 0L) {
            Instant.ofEpochMilli(cache.createdAtEpochMs)
        } else {
            null
        },
        myRole = cache.myRole,
        isShared = cache.isShared,
        memberCount = cache.memberCount,
        ownerUsername = cache.ownerUsername,
    )
}

internal fun floaterListToCache(list: ListSummary): CachedFloaterListRecord {
    return CachedFloaterListRecord(
        id = list.id,
        name = list.name,
        color = list.color,
        iconKey = list.iconKey,
        todoCount = list.todoCount,
        updatedAtEpochMs = list.updatedAt?.toEpochMilli() ?: 0L,
        createdAtEpochMs = list.createdAt?.toEpochMilli() ?: 0L,
        myRole = list.myRole,
        isShared = list.isShared,
        memberCount = list.memberCount,
        ownerUsername = list.ownerUsername,
    )
}

internal fun floaterListFromCache(
    cache: CachedFloaterListRecord,
    todoCountOverride: Int,
): ListSummary {
    return ListSummary(
        id = cache.id,
        name = cache.name,
        color = cache.color,
        iconKey = cache.iconKey,
        todoCount = todoCountOverride,
        updatedAt = if (cache.updatedAtEpochMs > 0L) Instant.ofEpochMilli(cache.updatedAtEpochMs) else null,
        createdAt = if (cache.createdAtEpochMs > 0L) Instant.ofEpochMilli(cache.createdAtEpochMs) else null,
        myRole = cache.myRole,
        isShared = cache.isShared,
        memberCount = cache.memberCount,
        ownerUsername = cache.ownerUsername,
    )
}

internal fun completedToCache(item: CompletedItem): CachedCompletedRecord {
    return CachedCompletedRecord(
        id = item.id,
        originalTodoId = item.originalTodoId,
        title = item.title,
        description = item.description,
        priority = item.priority,
        dueEpochMs = item.due?.toEpochMilli(),
        completedAtEpochMs = item.completedAt?.toEpochMilli() ?: 0L,
        rrule = item.rrule,
        instanceDateEpochMs = item.instanceDate?.toEpochMilli(),
        listId = item.listId,
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
        due = cache.dueEpochMs?.let(Instant::ofEpochMilli),
        completedAt = if (cache.completedAtEpochMs > 0L) {
            Instant.ofEpochMilli(cache.completedAtEpochMs)
        } else {
            null
        },
        rrule = cache.rrule,
        instanceDate = cache.instanceDateEpochMs?.let(Instant::ofEpochMilli),
        listId = cache.listId,
        listName = cache.listName,
        listColor = cache.listColor,
    )
}

internal fun completedFloaterToCache(item: CompletedItem): CachedCompletedFloaterRecord {
    return CachedCompletedFloaterRecord(
        id = item.id,
        originalFloaterId = item.originalTodoId,
        title = item.title,
        description = item.description,
        priority = item.priority,
        completedAtEpochMs = item.completedAt?.toEpochMilli() ?: 0L,
        listId = item.listId,
        listName = item.listName,
        listColor = item.listColor,
    )
}

internal fun completedFloaterFromCache(cache: CachedCompletedFloaterRecord): CompletedItem {
    return CompletedItem(
        id = cache.id,
        originalTodoId = cache.originalFloaterId,
        title = cache.title,
        description = cache.description,
        priority = cache.priority,
        due = null,
        completedAt = if (cache.completedAtEpochMs > 0L) {
            Instant.ofEpochMilli(cache.completedAtEpochMs)
        } else {
            null
        },
        rrule = null,
        instanceDate = null,
        listId = cache.listId,
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
        due = parseOptionalInstant(dto.due),
        rrule = dto.rrule,
        instanceDate = explicitInstance ?: suffixInstance,
        pinned = dto.pinned,
        completed = dto.completed,
        listId = dto.listID,
        updatedAt = parseOptionalInstant(dto.updatedAt),
    )
}

internal fun mapFloaterDto(dto: FloaterDto): TodoItem {
    return TodoItem(
        id = dto.id,
        canonicalId = dto.id,
        title = dto.title,
        description = dto.description,
        priority = dto.priority,
        due = null,
        rrule = null,
        instanceDate = null,
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
        due = parseOptionalInstant(dto.due),
        completedAt = parseOptionalInstant(dto.completedAt),
        rrule = dto.rrule,
        instanceDate = parseOptionalInstant(dto.instanceDate),
        listId = dto.listID,
        listName = dto.listName,
        listColor = dto.listColor,
    )
}

internal fun mapCompletedFloaterDto(dto: CompletedFloaterDto): CompletedItem {
    return CompletedItem(
        id = dto.id,
        originalTodoId = dto.originalFloaterID,
        title = dto.title,
        description = dto.description,
        priority = dto.priority,
        due = null,
        completedAt = parseOptionalInstant(dto.completedAt),
        rrule = null,
        instanceDate = null,
        listId = dto.listID,
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
        createdAt = parseOptionalInstant(dto.createdAt),
        myRole = dto.myRole ?: "OWNER",
        isShared = dto.isShared,
        memberCount = dto.memberCount,
        ownerUsername = dto.ownerUsername,
    )
}

internal fun mapFloaterListDto(dto: FloaterListDto, iconFallback: String? = null): ListSummary {
    return ListSummary(
        id = dto.id,
        name = dto.name,
        color = dto.color,
        iconKey = dto.iconKey ?: iconFallback,
        todoCount = dto.todoCount,
        updatedAt = parseOptionalInstant(dto.updatedAt),
        createdAt = parseOptionalInstant(dto.createdAt),
        myRole = dto.myRole ?: "OWNER",
        isShared = dto.isShared,
        memberCount = dto.memberCount,
        ownerUsername = dto.ownerUsername,
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
