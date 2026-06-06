package com.ohmz.tday.compose.core.data.db

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): TdayDatabase {
        return Room.databaseBuilder(
            context,
            TdayDatabase::class.java,
            "tday_offline_cache.db",
        )
            // TODO: this destroys the DB (including unsynced pending mutations) on any
            // schema bump. Schema export is now enabled (see TdayDatabase) — add proper
            // Migration objects via .addMigrations(...) before the next version bump and
            // drop this destructive fallback so offline edits survive upgrades.
            .fallbackToDestructiveMigration()
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
