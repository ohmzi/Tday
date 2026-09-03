package com.ohmz.tday.db

import com.ohmz.tday.db.tables.PushSubscriptions
import com.ohmz.tday.db.tables.TodoInstances
import com.ohmz.tday.db.tables.Todos
import com.ohmz.tday.db.tables.Users
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals

/**
 * Guards the delete rules the Exposed table declarations assert.
 *
 * These two foreign keys are the ones `V26__align_cascade_delete_constraints.sql` realigns, and
 * both of their tables are in the `SchemaUtils.createMissingTablesAndColumns` list in
 * `DatabaseConfig` — so from the next boot onwards the live constraint is whatever the Kotlin
 * column says, not what the migration says. [TestDatabase] builds its schema from those same
 * declarations, which is what lets this assert on the rule that will really be in production.
 *
 * Dropping `onDelete` from either column would leave Exposed's `RESTRICT` default, silently undo
 * the migration on the next start, and fail here.
 */
class CascadeDeleteTest {
    private val db: Database = TestDatabase.fresh()

    @AfterEach
    fun tearDown() {
        TestDatabase.close(db)
    }

    @Test
    fun `deleting a todo takes its instances with it`() {
        TestDatabase.insertUser(USER_ID, username = "owner@tday.test")
        transaction(db) {
            insertTodo(TODO_ID)
            TodoInstances.insert {
                it[id] = "instance_1"
                it[todoId] = TODO_ID
                it[recurId] = "2026-08-27T09:00:00"
                it[instanceDate] = LocalDateTime.of(2026, 8, 27, 9, 0)
            }
        }

        // No explicit TodoInstances delete: the ordered deletes in ListService, TodoService and
        // AdminServiceImpl.purgeUser are deliberately not used here, because the point is that a
        // caller which does not know about them still cannot strand a row.
        transaction(db) { Todos.deleteWhere { Todos.id eq TODO_ID } }

        transaction(db) {
            assertEquals(0, TodoInstances.selectAll().count(), "the instance outlived its todo")
        }
    }

    @Test
    fun `deleting a user takes their push subscriptions with it`() {
        TestDatabase.insertUser(USER_ID, username = "owner@tday.test")
        transaction(db) {
            PushSubscriptions.insert {
                it[id] = "push_1"
                it[userID] = USER_ID
                it[endpoint] = "https://push.example/subscription"
                it[p256dh] = "public-key"
                it[auth] = "auth-secret"
                it[createdAt] = LocalDateTime.now()
            }
        }

        transaction(db) { Users.deleteWhere { Users.id eq USER_ID } }

        transaction(db) {
            assertEquals(0, PushSubscriptions.selectAll().count(), "the subscription outlived its user")
        }
    }

    /**
     * Raw SQL because `Todos.priority` is a Postgres enum column that Exposed binds through a
     * driver value class H2 will not accept — the same reason [TestDatabase.insertUser] does it
     * this way.
     */
    private fun org.jetbrains.exposed.sql.Transaction.insertTodo(todoId: String) {
        exec(
            """
            INSERT INTO todos (id, title, "createdAt", "updatedAt", "userID", priority, due, exdates)
            VALUES ('$todoId', 'Water the plants', now(), now(), '$USER_ID', 'Medium', now(), ARRAY[])
            """.trimIndent(),
        )
    }

    private companion object {
        const val USER_ID = "user_1"
        const val TODO_ID = "todo_1"
    }
}
