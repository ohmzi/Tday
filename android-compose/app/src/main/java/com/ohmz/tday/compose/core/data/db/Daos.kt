package com.ohmz.tday.compose.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TodoDao {
    @Query("SELECT * FROM cached_todos")
    fun getAll(): List<CachedTodoEntity>

    @Query("SELECT * FROM cached_todos")
    fun observeAll(): Flow<List<CachedTodoEntity>>

    @Query("SELECT COUNT(*) FROM cached_todos")
    fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(todos: List<CachedTodoEntity>)

    @Query("DELETE FROM cached_todos")
    fun deleteAll()
}

@Dao
interface ListDao {
    @Query("SELECT * FROM cached_lists")
    fun getAll(): List<CachedListEntity>

    @Query("SELECT * FROM cached_lists")
    fun observeAll(): Flow<List<CachedListEntity>>

    @Query("SELECT COUNT(*) FROM cached_lists")
    fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(lists: List<CachedListEntity>)

    @Query("DELETE FROM cached_lists")
    fun deleteAll()
}

@Dao
interface CompletedDao {
    @Query("SELECT * FROM cached_completed")
    fun getAll(): List<CachedCompletedEntity>

    @Query("SELECT * FROM cached_completed")
    fun observeAll(): Flow<List<CachedCompletedEntity>>

    @Query("SELECT COUNT(*) FROM cached_completed")
    fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(items: List<CachedCompletedEntity>)

    @Query("DELETE FROM cached_completed")
    fun deleteAll()
}

@Dao
interface MutationDao {
    @Query("SELECT * FROM pending_mutations ORDER BY timestampEpochMs ASC")
    fun getAll(): List<PendingMutationEntity>

    @Query("SELECT COUNT(*) FROM pending_mutations")
    fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(mutations: List<PendingMutationEntity>)

    @Query("DELETE FROM pending_mutations")
    fun deleteAll()
}

@Dao
interface SyncMetadataDao {
    @Query("SELECT * FROM sync_metadata WHERE id = 1")
    fun get(): SyncMetadataEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(metadata: SyncMetadataEntity)

    @Query("DELETE FROM sync_metadata")
    fun deleteAll()
}
