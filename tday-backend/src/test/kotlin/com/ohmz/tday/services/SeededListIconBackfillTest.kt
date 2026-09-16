package com.ohmz.tday.services

import com.ohmz.tday.db.TestDatabase
import com.ohmz.tday.db.bootstrapProductionPgEnums
import com.ohmz.tday.db.tables.*
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.MigrationVersion
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Proves `V29__clear_seeded_list_icons.sql` does what the v0.7.28 icon cutover needs it to.
 *
 * The feature's rule is that `iconKey IS NULL` means "the owner never chose". Nothing ever
 * wrote NULL before v0.7.28 -- every create sheet posted the picker's seeded default -- so
 * without this migration the name-derived icon can never fire on a list anybody already
 * owns, and the feature ships switched off for every existing account. That claim is about
 * rows in Postgres, so it is asserted against rows in Postgres.
 *
 * Structured like [PriorityLowestOrdinalTest] and for its reasons: the REAL Flyway chain
 * from `classpath:db/migration`, with the baseline settings `DatabaseConfig.init()` uses,
 * against a disposable container -- which is what proves V29 applies cleanly on top of every
 * prior migration, rather than only asserting an end state some hand-written DDL produced.
 * The two-phase replay (migrate to 18, let Exposed create its tables, continue) is that
 * test's discovery, not a quirk of this one: `floaterproject` is Exposed-owned and V19
 * ALTERs it unguarded, so a single uninterrupted replay from an empty schema cannot reach
 * V29 at all.
 *
 * Seeding happens BETWEEN V28 and V29, which is the whole point. Rows inserted after the
 * chain finished would prove nothing about a migration that only ever sees rows written
 * before it ran.
 *
 * Requires Docker; skipped (not failed) wherever it isn't available.
 */
class SeededListIconBackfillTest {

    companion object {
        private const val USER_ID = "user_owner_seeded_list_icons"

        private var postgresOrNull: PostgreSQLContainer<Nothing>? = null
        private val postgres: PostgreSQLContainer<Nothing>
            get() = checkNotNull(postgresOrNull) { "startContainer() must run before postgres is used" }

        @JvmStatic
        @BeforeAll
        fun startContainer() {
            val dockerAvailable = runCatching { DockerClientFactory.instance().isDockerAvailable }.getOrDefault(false)
            Assumptions.assumeTrue(
                dockerAvailable,
                "Docker is not available in this environment -- skipping the real-Postgres list icon backfill test",
            )
            postgresOrNull = PostgreSQLContainer<Nothing>(DockerImageName.parse("postgres:16-alpine")).apply { start() }
        }

        @JvmStatic
        @AfterAll
        fun stopContainer() {
            postgresOrNull?.stop()
        }
    }

    private var dbOrNull: Database? = null
    private val db: Database
        get() = checkNotNull(dbOrNull) { "setUp() must run before db is used" }

    @BeforeEach
    fun setUp() {
        // See the class KDoc, and PriorityLowestOrdinalTest's setUp() for the long form:
        // Exposed has to create floaterproject before V19's unguarded ALTER reaches it.
        runFlyway(targetVersion = "18")
        connectAndBootstrapExposedTables()
        runFlyway(targetVersion = "28")
        connectAndBootstrapExposedTables()

        TestDatabase.insertUser(USER_ID, username = "owner-seeded-list-icons@tday.test")
    }

    @AfterEach
    fun tearDown() {
        transaction(db) {
            exec("DROP SCHEMA public CASCADE")
            exec("CREATE SCHEMA public")
        }
        TransactionManager.closeAndUnregister(db)
    }

    @Test
    fun `the seeded default is cleared on both list namespaces and every real choice survives`() {
        // Exactly the shapes a pre-v0.7.28 database holds. 'inbox' is what every create
        // sheet posted for a picker nobody touched; 'work' and 'cart' are real choices;
        // NULL is what an MCP-created list has always stored, since tday_create_list has
        // never sent an iconKey.
        seedList("l_seeded", "Groceries", "'inbox'")
        seedList("l_chosen", "Groceries", "'work'")
        seedList("l_other", "Reading", "'cart'")
        seedList("l_null", "Gym", "NULL")
        seedFloaterList("f_seeded", "Groceries", "'inbox'")
        seedFloaterList("f_chosen", "Groceries", "'work'")
        seedFloaterList("f_null", "Gym", "NULL")

        val applied = runFlyway(targetVersion = null)
        assertTrue(
            applied.migrations.any { it.version == "29" },
            "expected V29 (clear seeded list icons) to run; versions actually applied: " +
                applied.migrations.map { it.version },
        )

        assertEquals(
            mapOf("l_seeded" to null, "l_chosen" to "work", "l_other" to "cart", "l_null" to null),
            iconKeys("project"),
        )
        assertEquals(
            mapOf("f_seeded" to null, "f_chosen" to "work", "f_null" to null),
            iconKeys("floaterproject"),
        )
    }

    @Test
    fun `a seeded default is recognised however it was cased or padded`() {
        // The resolvers trim and lowercase before looking a key up, so all three of these
        // paint the inbox glyph. Matching the exact bytes would leave two of them frozen on
        // the value the migration exists to clear, and the miss would be invisible on every
        // client because all three render identically.
        seedList("l_padded", "Groceries", "' Inbox '")
        seedList("l_upper", "Groceries", "'INBOX'")
        seedList("l_exact", "Groceries", "'inbox'")
        seedFloaterList("f_padded", "Groceries", "'Inbox'")

        runFlyway(targetVersion = null)

        assertEquals(
            mapOf("l_padded" to null, "l_upper" to null, "l_exact" to null),
            iconKeys("project"),
        )
        assertEquals(mapOf("f_padded" to null), iconKeys("floaterproject"))
    }

    @Test
    fun `a database with nothing to clear still migrates`() {
        // V29 is a one-shot UPDATE against two tables, one of them behind a to_regclass
        // guard. An empty database is the path where that guard is the only thing running,
        // and a migration that threw here would take the whole server down on first boot.
        val applied = runFlyway(targetVersion = null)

        assertTrue(applied.migrations.any { it.version == "29" }, "V29 did not run")
        assertEquals(emptyMap(), iconKeys("project"))
        assertEquals(emptyMap(), iconKeys("floaterproject"))
    }

    /** Same Flyway settings [com.ohmz.tday.config.DatabaseConfig.init] uses in production. */
    private fun runFlyway(targetVersion: String?) = Flyway.configure()
        .dataSource(postgres.jdbcUrl, postgres.username, postgres.password)
        .locations("classpath:db/migration")
        .baselineOnMigrate(true)
        .baselineVersion(MigrationVersion.fromVersion("2"))
        .validateOnMigrate(false)
        .apply { targetVersion?.let { target(MigrationVersion.fromVersion(it)) } }
        .load()
        .migrate()

    private fun connectAndBootstrapExposedTables() {
        dbOrNull = Database.connect(
            url = postgres.jdbcUrl,
            driver = "org.postgresql.Driver",
            user = postgres.username,
            password = postgres.password,
        )
        TransactionManager.defaultDatabase = db
        transaction(db) {
            bootstrapProductionPgEnums()
            SchemaUtils.createMissingTablesAndColumns(
                Users, Accounts, VerificationTokens, Lists, FloaterLists, Todos, TodoInstances,
                CompletedTodos, Floaters, CompletedFloaters, Files, UserPreferences,
                EventLogs, CronLogs, AuthThrottles, AuthSignals, PushSubscriptions,
                ListShares, FloaterListShares,
            )
        }
    }

    /**
     * Raw SQL rather than Exposed, deliberately: the claim is about the COLUMN, and going
     * through `Lists.iconKey` would let a mapping change hide a migration that did nothing.
     * [iconKeySql] is a literal or `NULL`, never user input.
     */
    private fun seedList(id: String, name: String, iconKeySql: String) = transaction(db) {
        exec(
            """
            INSERT INTO project (id, "name", "iconKey", "userID", "createdAt", "updatedAt")
            VALUES ('$id', '$name', $iconKeySql, '$USER_ID', now(), now())
            """.trimIndent(),
        )
    }

    private fun seedFloaterList(id: String, name: String, iconKeySql: String) = transaction(db) {
        exec(
            """
            INSERT INTO floaterproject (id, "name", "iconKey", "userID", reusable, "createdAt", "updatedAt")
            VALUES ('$id', '$name', $iconKeySql, '$USER_ID', false, now(), now())
            """.trimIndent(),
        )
    }

    private fun iconKeys(table: String): Map<String, String?> = transaction(db) {
        exec<Map<String, String?>>("""SELECT id, "iconKey" FROM $table ORDER BY id""") { rs ->
            val out = linkedMapOf<String, String?>()
            while (rs.next()) out[rs.getString(1)] = rs.getString(2)
            out
        } ?: emptyMap()
    }
}
