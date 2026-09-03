package com.ohmz.tday.compose.core.data.db

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ohmz.tday.compose.core.data.SecureConfigStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import javax.inject.Singleton

// v8: sharing metadata on cached lists (myRole/isShared/memberCount/ownerUsername).
// Named top-level class, NOT an anonymous object inside the Hilt module:
// Dagger's KSP validation NPEs on anonymous-class-typed fields in a @Module.
private class Migration7To8 : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        for (table in listOf("cached_lists", "cached_floater_lists")) {
            db.execSQL("ALTER TABLE $table ADD COLUMN myRole TEXT NOT NULL DEFAULT 'OWNER'")
            db.execSQL("ALTER TABLE $table ADD COLUMN isShared INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE $table ADD COLUMN memberCount INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE $table ADD COLUMN ownerUsername TEXT")
        }
    }
}

// v9: default home screen preference (Scheduled vs Floaters), settable in Settings.
private class Migration8To9 : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE sync_metadata ADD COLUMN defaultHomeScreen TEXT NOT NULL DEFAULT 'scheduled'")
    }
}

// v10: durable, browsable completed floaters (see docs/design/completed-floaters-durability.md).
// listDeleted mirrors the server-computed CompletedFloaterDto.listDeleted flag, populated on
// the next sync; a fresh migration still needs the column to exist with a safe default.
private class Migration9To10 : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE cached_completed_floaters ADD COLUMN listDeleted INTEGER NOT NULL DEFAULT 0")
    }
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
        secureConfigStore: SecureConfigStore,
    ): TdayDatabase {
        // Required once per process before any SQLCipher database is opened.
        System.loadLibrary("sqlcipher")

        val passphraseStore = DatabasePassphraseStore(context)

        // Runs before the builder so the encrypted file already holds the legacy rows by the time
        // Room opens it. One-time and on a small personal cache, so the cost of doing it on the
        // injecting thread is a single slow first launch rather than an ongoing tax.
        // Takes the store, not a key: the migration may need several passphrase copies in one pass
        // (inspecting the encrypted cache, then exporting) and SQLCipher zeroes each array it gets.
        // The data mode has to come in too: in local mode this database is the only copy of the
        // user's tasks, so the migration must never treat it as a re-fetchable cache.
        migrateLegacyPlaintextCacheIfNeeded(
            context = context,
            passphraseStore = passphraseStore,
            dataMode = secureConfigStore.getAppDataMode(),
        )

        return Room.databaseBuilder(
            context,
            TdayDatabase::class.java,
            ENCRYPTED_DB_NAME,
        )
            // Task titles, notes and unsynced mutations live here, so the file is encrypted at
            // rest; the key is Keystore-wrapped in DatabasePassphraseStore. SQLCipher zeroes the
            // array it is given, hence a fresh one here.
            .openHelperFactory(SupportOpenHelperFactory(passphraseStore.getOrCreatePassphrase()))
            // The DB holds unsynced pending mutations, not just re-fetchable
            // cache, so schema bumps must ship a real Migration. Pre-v7 schemas
            // (no exported history) still fall back destructively.
            .addMigrations(Migration7To8(), Migration8To9(), Migration9To10())
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
