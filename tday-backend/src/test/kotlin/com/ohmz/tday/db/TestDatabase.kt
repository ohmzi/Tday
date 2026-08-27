package com.ohmz.tday.db

import com.ohmz.tday.db.tables.Accounts
import com.ohmz.tday.db.tables.CalendarFeedTokens
import com.ohmz.tday.db.tables.CompletedFloaters
import com.ohmz.tday.db.tables.CompletedTodos
import com.ohmz.tday.db.tables.Files
import com.ohmz.tday.db.tables.FloaterListShares
import com.ohmz.tday.db.tables.FloaterLists
import com.ohmz.tday.db.tables.Floaters
import com.ohmz.tday.db.tables.ListShares
import com.ohmz.tday.db.tables.Lists
import com.ohmz.tday.db.tables.PushSubscriptions
import com.ohmz.tday.db.tables.TodoInstances
import com.ohmz.tday.db.tables.Todos
import com.ohmz.tday.db.tables.UserApiKeys
import com.ohmz.tday.db.tables.UserPreferences
import com.ohmz.tday.db.tables.UserSecurityQuestions
import com.ohmz.tday.db.tables.Users
import com.ohmz.tday.db.tables.WebhookSubscriptions
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import org.postgresql.util.PGobject
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.util.concurrent.atomic.AtomicInteger

/**
 * A disposable in-memory database built from the production Exposed tables, so the constraints a
 * test runs into are the ones the server really creates.
 *
 * Most tests here use hand-written doubles instead of a database. Two behaviours cannot be
 * covered that way, because the database is what decides them: which child rows an account purge
 * has to clear before `"User"` can go, and which columns the member search matches. H2 stands in
 * for PostgreSQL, so assertions should stay on portable behaviour — referential integrity, LIKE,
 * COALESCE — and off Postgres-specific SQL.
 */
object TestDatabase {
    private val counter = AtomicInteger()

    /** Postgres enum types, declared as domains so the DDL Exposed emits for `pgEnum` resolves. */
    private val pgEnumTypes = listOf(
        "UserRole", "ApprovalStatus", "Priority", "ProjectColor", "SortBy", "GroupBy", "Direction",
    )

    private val tables: Array<Table> = arrayOf(
        Users, Accounts, Lists, FloaterLists, Todos, TodoInstances, CompletedTodos,
        CompletedFloaters, Floaters, Files, UserPreferences, UserSecurityQuestions,
        ListShares, FloaterListShares, PushSubscriptions, UserApiKeys, CalendarFeedTokens,
        WebhookSubscriptions,
    )

    /**
     * Connects an empty database and makes it the default for the calling test.
     * `DATABASE_TO_LOWER` mirrors Postgres identifier folding, so raw SQL in a test can be
     * written the way it would be written against production.
     */
    fun fresh(): Database {
        val url = "jdbc:h2:mem:tday_${counter.incrementAndGet()};DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
        val db = Database.connect(getNewConnection = { openConnection(url) })
        TransactionManager.defaultDatabase = db
        transaction(db) {
            pgEnumTypes.forEach { exec("""CREATE DOMAIN IF NOT EXISTS "$it" AS VARCHAR(64)""") }
            SchemaUtils.createStatements(tables = tables).forEach { exec(it.forTestDialect()) }
        }
        return db
    }

    fun close(db: Database) {
        transaction(db) { exec("DROP ALL OBJECTS") }
        TransactionManager.closeAndUnregister(db)
    }

    /**
     * Inserts an account with raw SQL. `role` and `approvalStatus` are Postgres enum columns, and
     * Exposed binds them through a driver-specific value class that H2 will not accept.
     */
    fun insertUser(
        id: String,
        username: String,
        name: String? = null,
        role: String = "USER",
        approvalStatus: String = "APPROVED",
    ) {
        val displayName = name?.let { "'${it.sqlEscaped()}'" } ?: "NULL"
        transaction {
            exec(
                """
                INSERT INTO "User" (id, "name", username, "createdAt", "updatedAt", "role", "approvalStatus")
                VALUES ('${id.sqlEscaped()}', $displayName, '${username.sqlEscaped()}', now(), now(), '$role', '$approvalStatus')
                """.trimIndent(),
            )
        }
    }

    private fun String.sqlEscaped(): String = replace("'", "''")

    private fun openConnection(url: String): Connection =
        EnumUnwrappingConnection(DriverManager.getConnection(url))

    /**
     * H2 with one adjustment: `pgEnum` columns bind their value as a `PGobject`, which only the
     * Postgres driver understands. Unwrapping it back to a plain string on the way to the
     * statement is what lets a query comparing an enum column run here at all.
     */
    private class EnumUnwrappingConnection(private val delegate: Connection) : Connection by delegate {
        override fun prepareStatement(sql: String): PreparedStatement =
            EnumUnwrappingStatement(delegate.prepareStatement(sql))

        override fun prepareStatement(sql: String, autoGeneratedKeys: Int): PreparedStatement =
            EnumUnwrappingStatement(delegate.prepareStatement(sql, autoGeneratedKeys))

        override fun prepareStatement(sql: String, columnNames: Array<out String>): PreparedStatement =
            EnumUnwrappingStatement(delegate.prepareStatement(sql, columnNames))
    }

    private class EnumUnwrappingStatement(
        private val delegate: PreparedStatement,
    ) : PreparedStatement by delegate {
        override fun setObject(parameterIndex: Int, x: Any?) {
            if (x is PGobject) {
                delegate.setString(parameterIndex, x.value)
            } else {
                delegate.setObject(parameterIndex, x)
            }
        }
    }

    /**
     * `Todos.exdates` is a Postgres array, which H2 spells differently. The column plays no part
     * in anything asserted here — the table only has to exist so the purge can delete from it.
     */
    private fun String.forTestDialect(): String = replace("timestamp(3)[]", "TIMESTAMP(3) ARRAY")
}
