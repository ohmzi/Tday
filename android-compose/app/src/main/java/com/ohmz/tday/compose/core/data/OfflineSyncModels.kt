package com.ohmz.tday.compose.core.data

import kotlinx.serialization.Serializable

@Serializable
internal data class OfflineSyncState(
    val lastSuccessfulSyncEpochMs: Long = 0L,
    val lastSyncAttemptEpochMs: Long = 0L,
    val todos: List<CachedTodoRecord> = emptyList(),
    val completedItems: List<CachedCompletedRecord> = emptyList(),
    val lists: List<CachedListRecord> = emptyList(),
    val pendingMutations: List<PendingMutationRecord> = emptyList(),
)

@Serializable
internal data class CachedTodoRecord(
    val id: String,
    val canonicalId: String,
    val title: String,
    val description: String? = null,
    val priority: String = "Low",
    val dtstartEpochMs: Long,
    val dueEpochMs: Long,
    val rrule: String? = null,
    val instanceDateEpochMs: Long? = null,
    val pinned: Boolean = false,
    val completed: Boolean = false,
    val listId: String? = null,
    val updatedAtEpochMs: Long = 0L,
)

@Serializable
internal data class CachedListRecord(
    val id: String,
    val name: String,
    val color: String? = null,
    val iconKey: String? = null,
    val todoCount: Int = 0,
    val updatedAtEpochMs: Long = 0L,
)

@Serializable
internal data class CachedCompletedRecord(
    val id: String,
    val originalTodoId: String? = null,
    val title: String,
    val priority: String,
    val dueEpochMs: Long,
    val rrule: String? = null,
    val instanceDateEpochMs: Long? = null,
)

@Serializable
internal data class PendingMutationRecord(
    val mutationId: String,
    val kind: MutationKind,
    val targetId: String? = null,
    val timestampEpochMs: Long,
    val title: String? = null,
    val description: String? = null,
    val priority: String? = null,
    val dtstartEpochMs: Long? = null,
    val dueEpochMs: Long? = null,
    val rrule: String? = null,
    val listId: String? = null,
    val pinned: Boolean? = null,
    val completed: Boolean? = null,
    val instanceDateEpochMs: Long? = null,
    val name: String? = null,
    val color: String? = null,
    val iconKey: String? = null,
)

@Serializable
internal enum class MutationKind {
    CREATE_LIST,
    UPDATE_LIST,
    CREATE_TODO,
    DELETE_TODO,
    SET_PINNED,
    SET_PRIORITY,
    COMPLETE_TODO,
    COMPLETE_TODO_INSTANCE,
    UNCOMPLETE_TODO,
}
