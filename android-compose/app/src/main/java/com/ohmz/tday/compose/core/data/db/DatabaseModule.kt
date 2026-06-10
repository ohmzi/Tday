package com.ohmz.tday.compose.core.data.db

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    // v8: sharing metadata on cached lists (myRole/isShared/memberCount/ownerUsername).
    private val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            for (table in listOf("cached_lists", "cached_floater_lists")) {
                db.execSQL("ALTER TABLE $table ADD COLUMN myRole TEXT NOT NULL DEFAULT 'OWNER'")
                db.execSQL("ALTER TABLE $table ADD COLUMN isShared INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE $table ADD COLUMN memberCount INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE $table ADD COLUMN ownerUsername TEXT")
            }
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): TdayDatabase {
        return Room.databaseBuilder(
            context,
            TdayDatabase::class.java,
            "tday_offline_cache.db",
        )
            // The DB holds unsynced pending mutations, not just re-fetchable
            // cache, so schema bumps must ship a real Migration. Pre-v7 schemas
            // (no exported history) still fall back destructively.
            .addMigrations(MIGRATION_7_8)
            .fallbackToDestructiveMigrationFrom(1, 2, 3, 4, 5, 6)
            // Safety net: callers should run DAO access off the main thread (see
            // OfflineCacheManager / repositories using Dispatchers.IO). Kept so a missed
            // path (e.g. a Glance widget) degrades to a slow query rather than crashing.
            .allowMainThreadQueries()
            .build()
    }

    @Provides
    fun provideTodoDao(db: TdayDatabase): TodoDao = db.todoDao()

    @Provides
    fun provideFloaterDao(db: TdayDatabase): FloaterDao = db.floaterDao()

    @Provides
    fun provideListDao(db: TdayDatabase): ListDao = db.listDao()

    @Provides
    fun provideFloaterListDao(db: TdayDatabase): FloaterListDao = db.floaterListDao()

    @Provides
    fun provideCompletedDao(db: TdayDatabase): CompletedDao = db.completedDao()

    @Provides
    fun provideCompletedFloaterDao(db: TdayDatabase): CompletedFloaterDao = db.completedFloaterDao()

    @Provides
    fun provideMutationDao(db: TdayDatabase): MutationDao = db.mutationDao()

    @Provides
    fun provideSyncMetadataDao(db: TdayDatabase): SyncMetadataDao = db.syncMetadataDao()
}
