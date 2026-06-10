package com.ohmz.tday.compose.core.data.db

import com.ohmz.tday.compose.core.data.CachedCompletedFloaterRecord
import com.ohmz.tday.compose.core.data.CachedCompletedRecord
import com.ohmz.tday.compose.core.data.CachedFloaterListRecord
import com.ohmz.tday.compose.core.data.CachedFloaterRecord
import com.ohmz.tday.compose.core.data.CachedListRecord
import com.ohmz.tday.compose.core.data.CachedTodoRecord
import com.ohmz.tday.compose.core.data.MutationKind
import com.ohmz.tday.compose.core.data.PendingMutationRecord

fun CachedTodoRecord.toEntity() = CachedTodoEntity(
    id = id,
    canonicalId = canonicalId,
    title = title,
    description = description,
    priority = priority,
    dueEpochMs = dueEpochMs,
    rrule = rrule,
    instanceDateEpochMs = instanceDateEpochMs,
    pinned = pinned,
    completed = completed,
    listId = listId,
    updatedAtEpochMs = updatedAtEpochMs,
)

fun CachedTodoEntity.toRecord() = CachedTodoRecord(
    id = id,
    canonicalId = canonicalId,
    title = title,
    description = description,
    priority = priority,
    dueEpochMs = dueEpochMs,
    rrule = rrule,
    instanceDateEpochMs = instanceDateEpochMs,
    pinned = pinned,
    completed = completed,
    listId = listId,
    updatedAtEpochMs = updatedAtEpochMs,
)

fun CachedFloaterRecord.toEntity() = CachedFloaterEntity(
    id = id,
    canonicalId = canonicalId,
    title = title,
    description = description,
    priority = priority,
    pinned = pinned,
    completed = completed,
    listId = listId,
    updatedAtEpochMs = updatedAtEpochMs,
)

fun CachedFloaterEntity.toRecord() = CachedFloaterRecord(
    id = id,
    canonicalId = canonicalId,
    title = title,
    description = description,
    priority = priority,
    pinned = pinned,
    completed = completed,
    listId = listId,
    updatedAtEpochMs = updatedAtEpochMs,
)

fun CachedListRecord.toEntity() = CachedListEntity(
    id = id,
    name = name,
    color = color,
    iconKey = iconKey,
    todoCount = todoCount,
    updatedAtEpochMs = updatedAtEpochMs,
    createdAtEpochMs = createdAtEpochMs,
    myRole = myRole,
    isShared = isShared,
    memberCount = memberCount,
    ownerUsername = ownerUsername,
)

fun CachedListEntity.toRecord() = CachedListRecord(
    id = id,
    name = name,
    color = color,
    iconKey = iconKey,
    todoCount = todoCount,
    updatedAtEpochMs = updatedAtEpochMs,
    createdAtEpochMs = createdAtEpochMs,
    myRole = myRole,
    isShared = isShared,
    memberCount = memberCount,
    ownerUsername = ownerUsername,
)

fun CachedFloaterListRecord.toEntity() = CachedFloaterListEntity(
    id = id,
    name = name,
    color = color,
    iconKey = iconKey,
    todoCount = todoCount,
    updatedAtEpochMs = updatedAtEpochMs,
    createdAtEpochMs = createdAtEpochMs,
    myRole = myRole,
    isShared = isShared,
    memberCount = memberCount,
    ownerUsername = ownerUsername,
)

fun CachedFloaterListEntity.toRecord() = CachedFloaterListRecord(
    id = id,
    name = name,
    color = color,
    iconKey = iconKey,
    todoCount = todoCount,
    updatedAtEpochMs = updatedAtEpochMs,
    createdAtEpochMs = createdAtEpochMs,
    myRole = myRole,
    isShared = isShared,
    memberCount = memberCount,
    ownerUsername = ownerUsername,
)

fun CachedCompletedRecord.toEntity() = CachedCompletedEntity(
    id = id,
    originalTodoId = originalTodoId,
    title = title,
    description = description,
    priority = priority,
    dueEpochMs = dueEpochMs,
    completedAtEpochMs = completedAtEpochMs,
    rrule = rrule,
    instanceDateEpochMs = instanceDateEpochMs,
    listId = listId,
    listName = listName,
    listColor = listColor,
)

fun CachedCompletedEntity.toRecord() = CachedCompletedRecord(
    id = id,
    originalTodoId = originalTodoId,
    title = title,
    description = description,
    priority = priority,
    dueEpochMs = dueEpochMs,
    completedAtEpochMs = completedAtEpochMs,
    rrule = rrule,
    instanceDateEpochMs = instanceDateEpochMs,
    listId = listId,
    listName = listName,
    listColor = listColor,
)

fun CachedCompletedFloaterRecord.toEntity() = CachedCompletedFloaterEntity(
    id = id,
    originalFloaterId = originalFloaterId,
    title = title,
    description = description,
    priority = priority,
    completedAtEpochMs = completedAtEpochMs,
    listId = listId,
    listName = listName,
    listColor = listColor,
)

fun CachedCompletedFloaterEntity.toRecord() = CachedCompletedFloaterRecord(
    id = id,
    originalFloaterId = originalFloaterId,
    title = title,
    description = description,
    priority = priority,
    completedAtEpochMs = completedAtEpochMs,
    listId = listId,
    listName = listName,
    listColor = listColor,
)

fun PendingMutationRecord.toEntity() = PendingMutationEntity(
    mutationId = mutationId,
    kind = kind.name,
    targetId = targetId,
    timestampEpochMs = timestampEpochMs,
    title = title,
    description = description,
    priority = priority,
    dueEpochMs = dueEpochMs,
    rrule = rrule,
    listId = listId,
    pinned = pinned,
    completed = completed,
    instanceDateEpochMs = instanceDateEpochMs,
    name = name,
    color = color,
    iconKey = iconKey,
)

fun PendingMutationEntity.toRecord() = PendingMutationRecord(
    mutationId = mutationId,
    kind = MutationKind.valueOf(kind),
    targetId = targetId,
    timestampEpochMs = timestampEpochMs,
    title = title,
    description = description,
    priority = priority,
    dueEpochMs = dueEpochMs,
    rrule = rrule,
    listId = listId,
    pinned = pinned,
    completed = completed,
    instanceDateEpochMs = instanceDateEpochMs,
    name = name,
    color = color,
    iconKey = iconKey,
)
