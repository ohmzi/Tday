package com.ohmz.tday.compose.core.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "cached_todos",
    indices = [
        Index("listId"),
        Index("dtstartEpochMs"),
        Index("dueEpochMs"),
        Index("completed"),
    ],
)
data class CachedTodoEntity(
    @PrimaryKey val id: String,
    val canonicalId: String,
    val title: String,
    val description: String?,
    val priority: String,
    val dtstartEpochMs: Long,
    val dueEpochMs: Long,
    val rrule: String?,
    val instanceDateEpochMs: Long?,
    val pinned: Boolean,
    val completed: Boolean,
    val listId: String?,
    val updatedAtEpochMs: Long,
)

@Entity(tableName = "cached_lists")
data class CachedListEntity(
    @PrimaryKey val id: String,
    val name: String,
    val color: String?,
    val iconKey: String?,
    val todoCount: Int,
    val updatedAtEpochMs: Long,
)

@Entity(
    tableName = "cached_completed",
    indices = [Index("completedAtEpochMs")],
)
data class CachedCompletedEntity(
    @PrimaryKey val id: String,
    val originalTodoId: String?,
    val title: String,
    val description: String?,
    val priority: String,
    val dtstartEpochMs: Long,
    val dueEpochMs: Long,
    val completedAtEpochMs: Long,
    val rrule: String?,
    val instanceDateEpochMs: Long?,
    val listName: String?,
    val listColor: String?,
)

@Entity(
    tableName = "pending_mutations",
    indices = [Index("timestampEpochMs")],
)
data class PendingMutationEntity(
    @PrimaryKey val mutationId: String,
    val kind: String,
    val targetId: String?,
    val timestampEpochMs: Long,
    val title: String?,
    val description: String?,
    val priority: String?,
    val dtstartEpochMs: Long?,
    val dueEpochMs: Long?,
    val rrule: String?,
    val listId: String?,
    val pinned: Boolean?,
    val completed: Boolean?,
    val instanceDateEpochMs: Long?,
    val name: String?,
    val color: String?,
    val iconKey: String?,
)

@Entity(tableName = "sync_metadata")
data class SyncMetadataEntity(
    @PrimaryKey val id: Int = 1,
    val lastSuccessfulSyncEpochMs: Long = 0L,
    val lastSyncAttemptEpochMs: Long = 0L,
    val aiSummaryEnabled: Boolean = true,
)
