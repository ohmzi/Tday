package com.ohmz.tday.services

import com.ohmz.tday.db.TestDatabase
import com.ohmz.tday.db.tables.CalendarFeedTokens
import com.ohmz.tday.db.tables.Lists
import com.ohmz.tday.db.tables.PushSubscriptions
import com.ohmz.tday.db.tables.UserApiKeys
import com.ohmz.tday.db.tables.Users
import com.ohmz.tday.db.tables.WebhookSubscriptions
import com.ohmz.tday.domain.AppError
import com.ohmz.tday.domain.AuthenticatedUser
import com.ohmz.tday.security.PasswordServiceImpl
import com.ohmz.tday.security.SessionControl
import com.ohmz.tday.security.testAppConfig
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Deleting an account has to clear every row that references it first: those foreign keys are
 * ON DELETE RESTRICT, so one that the purge does not know about turns the admin panel's delete
 * into a 500 for anyone holding such a row.
 */
class AdminPurgeTest {
    // JUnit builds a new instance per test method, so this is one empty database per test.
    private val db: Database = TestDatabase.fresh()
    private val revokedSessions = mutableListOf<String>()

    private val service = AdminServiceImpl(
        passwordService = PasswordServiceImpl(testAppConfig()),
        sessionControl = object : SessionControl {
            override suspend fun revokeUserSessions(userId: String, revokeApiKeys: Boolean) {
                revokedSessions += userId
            }
        },
    )

    private val admin = AuthenticatedUser(id = ADMIN_ID, role = "ADMIN", approvalStatus = "APPROVED", timeZone = null)

    @BeforeEach
    fun setUp() {
        TestDatabase.insertUser(ADMIN_ID, username = "admin@tday.test", role = "ADMIN")
        TestDatabase.insertUser(TARGET_ID, username = "z@a.com", name = "Zed Adams")
    }

    @AfterEach
    fun tearDown() {
        TestDatabase.close(db)
    }

    @Test
    fun `a user holding a push subscription can be deleted`() = runBlocking {
        transaction(db) {
            PushSubscriptions.insert {
                it[id] = "push_1"
                it[userID] = TARGET_ID
                it[endpoint] = "https://push.example/subscription"
                it[p256dh] = "public-key"
                it[auth] = "auth-secret"
                it[createdAt] = LocalDateTime.now()
            }
        }

        val result = service.deleteUser(TARGET_ID, admin)

        assertTrue(result.isRight(), "delete failed: $result")
        transaction(db) {
            assertEquals(0, Users.selectAll().where { Users.id eq TARGET_ID }.count())
            assertEquals(0, PushSubscriptions.selectAll().count())
        }
        assertEquals(listOf(TARGET_ID), revokedSessions)
    }

    @Test
    fun `standing credentials are removed with the account`() = runBlocking {
        transaction(db) {
            UserApiKeys.insert {
                it[id] = "key_1"
                it[userID] = TARGET_ID
                it[keyHash] = "hash"
                it[keyPreview] = "tday_key_1"
                it[createdAt] = LocalDateTime.now()
            }
            CalendarFeedTokens.insert {
                it[id] = "feed_1"
                it[userID] = TARGET_ID
                it[tokenHash] = "hash"
                it[tokenPreview] = "feed_1"
                it[createdAt] = LocalDateTime.now()
            }
            WebhookSubscriptions.insert {
                it[id] = "hook_1"
                it[userID] = TARGET_ID
                it[url] = "https://hook.example/tday"
                it[secret] = "secret"
                it[createdAt] = LocalDateTime.now()
            }
        }

        val result = service.deleteUser(TARGET_ID, admin)

        assertTrue(result.isRight(), "delete failed: $result")
        transaction(db) {
            assertEquals(0, UserApiKeys.selectAll().count())
            assertEquals(0, CalendarFeedTokens.selectAll().count())
            assertEquals(0, WebhookSubscriptions.selectAll().count())
        }
    }

    @Test
    fun `a reference the purge cannot clear is reported instead of thrown`() = runBlocking {
        // A member's task living in a list the target owns. The purge deletes the target's own
        // todos and then the list, and "todos"."projectID" is ON DELETE RESTRICT, so the
        // stranger's row blocks it. Deleting somebody else's tasks to remove an account is a
        // product decision, so this asserts the failure is legible rather than that it succeeds.
        TestDatabase.insertUser(OTHER_ID, username = "member@tday.test")
        transaction(db) {
            Lists.insert {
                it[id] = "list_1"
                it[name] = "Shared list"
                it[userID] = TARGET_ID
                it[createdAt] = LocalDateTime.now()
                it[updatedAt] = LocalDateTime.now()
            }
            exec(
                """
                INSERT INTO todos (id, title, "createdAt", "updatedAt", "userID", priority, due, exdates, "projectID")
                VALUES ('todo_1', 'Their task', now(), now(), '$OTHER_ID', 'Medium', now(), ARRAY[], 'list_1')
                """.trimIndent(),
            )
        }

        val result = service.deleteUser(TARGET_ID, admin)

        assertEquals(
            AppError.Conflict(
                "this account still has data referencing it and was not deleted; " +
                    "the server log names the constraint that blocked it",
            ),
            result.leftOrNull(),
        )
        transaction(db) {
            assertEquals(1, Users.selectAll().where { Users.id eq TARGET_ID }.count(), "the purge rolled back")
        }
        assertEquals(emptyList(), revokedSessions, "a failed purge must not revoke sessions")
    }

    @Test
    fun `every column referencing a user is covered by the purge`() {
        val covered: Set<Column<*>> = USER_OWNED_CHILD_COLUMNS.toSet()

        val uncovered = productionTables()
            .flatMap { table -> table.columns.filter { it.referee == Users.id } }
            .filterNot { it in covered }
            // The self-reference is cleared rather than deleted, so an admin who approved
            // somebody can still be removed.
            .filterNot { it == Users.approvedById }
            .map { "${it.table.tableName}.${it.name}" }

        assertEquals(emptyList(), uncovered, "these references would block a user delete")
    }

    /** Every `object : Table` under `com.ohmz.tday.db.tables`, read off the compiled classpath. */
    private fun productionTables(): List<Table> {
        val classesRoot = File(Users::class.java.protectionDomain.codeSource.location.toURI())
        val classFiles = classesRoot.resolve("com/ohmz/tday/db/tables")
            .listFiles { file -> file.name.endsWith(".class") }
            .orEmpty()
        assertTrue(classFiles.size > 10, "expected the table classes on the classpath, found ${classFiles.size}")

        return classFiles.mapNotNull { file ->
            val className = "com.ohmz.tday.db.tables." + file.name.removeSuffix(".class")
            runCatching { Class.forName(className).getDeclaredField("INSTANCE").get(null) as? Table }.getOrNull()
        }
    }

    private companion object {
        const val ADMIN_ID = "admin_1"
        const val TARGET_ID = "user_1"
        const val OTHER_ID = "user_2"
    }
}
