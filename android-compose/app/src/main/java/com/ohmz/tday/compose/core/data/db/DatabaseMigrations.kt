package com.ohmz.tday.compose.core.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Drops `dtstartEpochMs` from cached todos, completed rows, and pending mutations.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `cached_todos_new` (
                `id` TEXT NOT NULL PRIMARY KEY,
                `canonicalId` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `description` TEXT,
                `priority` TEXT NOT NULL,
                `dueEpochMs` INTEGER NOT NULL,
                `rrule` TEXT,
                `instanceDateEpochMs` INTEGER,
                `pinned` INTEGER NOT NULL,
                `completed` INTEGER NOT NULL,
                `listId` TEXT,
                `updatedAtEpochMs` INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO `cached_todos_new` (`id`,`canonicalId`,`title`,`description`,`priority`,`dueEpochMs`,`rrule`,`instanceDateEpochMs`,`pinned`,`completed`,`listId`,`updatedAtEpochMs`)
            SELECT `id`,`canonicalId`,`title`,`description`,`priority`,`dueEpochMs`,`rrule`,`instanceDateEpochMs`,`pinned`,`completed`,`listId`,`updatedAtEpochMs` FROM `cached_todos`
            """.trimIndent(),
        )
        db.execSQL("DROP TABLE `cached_todos`")
        db.execSQL("ALTER TABLE `cached_todos_new` RENAME TO `cached_todos`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_todos_listId` ON `cached_todos` (`listId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_todos_dueEpochMs` ON `cached_todos` (`dueEpochMs`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_todos_completed` ON `cached_todos` (`completed`)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `cached_completed_new` (
                `id` TEXT NOT NULL PRIMARY KEY,
                `originalTodoId` TEXT,
                `title` TEXT NOT NULL,
                `description` TEXT,
                `priority` TEXT NOT NULL,
                `dueEpochMs` INTEGER NOT NULL,
                `completedAtEpochMs` INTEGER NOT NULL,
                `rrule` TEXT,
                `instanceDateEpochMs` INTEGER,
                `listName` TEXT,
                `listColor` TEXT
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO `cached_completed_new` (`id`,`originalTodoId`,`title`,`description`,`priority`,`dueEpochMs`,`completedAtEpochMs`,`rrule`,`instanceDateEpochMs`,`listName`,`listColor`)
            SELECT `id`,`originalTodoId`,`title`,`description`,`priority`,`dueEpochMs`,`completedAtEpochMs`,`rrule`,`instanceDateEpochMs`,`listName`,`listColor` FROM `cached_completed`
            """.trimIndent(),
        )
        db.execSQL("DROP TABLE `cached_completed`")
        db.execSQL("ALTER TABLE `cached_completed_new` RENAME TO `cached_completed`")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_cached_completed_completedAtEpochMs` ON `cached_completed` (`completedAtEpochMs`)",
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `pending_mutations_new` (
                `mutationId` TEXT NOT NULL PRIMARY KEY,
                `kind` TEXT NOT NULL,
                `targetId` TEXT,
                `timestampEpochMs` INTEGER NOT NULL,
                `title` TEXT,
                `description` TEXT,
                `priority` TEXT,
                `dueEpochMs` INTEGER,
                `rrule` TEXT,
                `listId` TEXT,
                `pinned` INTEGER,
                `completed` INTEGER,
                `instanceDateEpochMs` INTEGER,
                `name` TEXT,
                `color` TEXT,
                `iconKey` TEXT
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO `pending_mutations_new` (`mutationId`,`kind`,`targetId`,`timestampEpochMs`,`title`,`description`,`priority`,`dueEpochMs`,`rrule`,`listId`,`pinned`,`completed`,`instanceDateEpochMs`,`name`,`color`,`iconKey`)
            SELECT `mutationId`,`kind`,`targetId`,`timestampEpochMs`,`title`,`description`,`priority`,`dueEpochMs`,`rrule`,`listId`,`pinned`,`completed`,`instanceDateEpochMs`,`name`,`color`,`iconKey` FROM `pending_mutations`
            """.trimIndent(),
        )
        db.execSQL("DROP TABLE `pending_mutations`")
        db.execSQL("ALTER TABLE `pending_mutations_new` RENAME TO `pending_mutations`")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_pending_mutations_timestampEpochMs` ON `pending_mutations` (`timestampEpochMs`)",
        )
    }
}
