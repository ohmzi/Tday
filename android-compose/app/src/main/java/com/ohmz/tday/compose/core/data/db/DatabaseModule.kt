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
        ).allowMainThreadQueries().build()
    }

    @Provides
    fun provideTodoDao(db: TdayDatabase): TodoDao = db.todoDao()

    @Provides
    fun provideListDao(db: TdayDatabase): ListDao = db.listDao()

    @Provides
    fun provideCompletedDao(db: TdayDatabase): CompletedDao = db.completedDao()

    @Provides
    fun provideMutationDao(db: TdayDatabase): MutationDao = db.mutationDao()

    @Provides
    fun provideSyncMetadataDao(db: TdayDatabase): SyncMetadataDao = db.syncMetadataDao()
}
