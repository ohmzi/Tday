package com.ohmz.tday.compose.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        CachedTodoEntity::class,
        CachedFloaterEntity::class,
        CachedListEntity::class,
        CachedFloaterListEntity::class,
        CachedCompletedEntity::class,
        CachedCompletedFloaterEntity::class,
        PendingMutationEntity::class,
        SyncMetadataEntity::class,
    ],
    version = 7,
    exportSchema = false,
)
abstract class TdayDatabase : RoomDatabase() {
    abstract fun todoDao(): TodoDao
    abstract fun floaterDao(): FloaterDao
    abstract fun listDao(): ListDao
    abstract fun floaterListDao(): FloaterListDao
    abstract fun completedDao(): CompletedDao
    abstract fun completedFloaterDao(): CompletedFloaterDao
    abstract fun mutationDao(): MutationDao
    abstract fun syncMetadataDao(): SyncMetadataDao
}
