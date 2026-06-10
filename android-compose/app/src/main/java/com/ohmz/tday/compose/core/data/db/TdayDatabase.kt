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
    version = 8,
    // Schema is exported to app/schemas so version bumps ship real Migration
    // objects instead of destroying the DB (which holds unsynced pending
    // mutations, not just re-fetchable cache). See DatabaseModule.
    exportSchema = true,
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
