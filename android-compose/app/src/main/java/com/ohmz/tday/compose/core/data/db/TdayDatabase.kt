package com.ohmz.tday.compose.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        CachedTodoEntity::class,
        CachedListEntity::class,
        CachedCompletedEntity::class,
        PendingMutationEntity::class,
        SyncMetadataEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class TdayDatabase : RoomDatabase() {
    abstract fun todoDao(): TodoDao
    abstract fun listDao(): ListDao
    abstract fun completedDao(): CompletedDao
    abstract fun mutationDao(): MutationDao
    abstract fun syncMetadataDao(): SyncMetadataDao
}
