package com.ohmz.tday.services

import arrow.core.Either
import com.ohmz.tday.db.TestDatabase
import com.ohmz.tday.db.bootstrapProductionPgEnums
import com.ohmz.tday.db.tables.*
import kotlinx.coroutines.runBlocking
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
 * Proves the ordinal claim behind `V28__add_lowest_priority.sql`: adding `'Lowest'` to the
 * native Postgres `"Priority"` enum BEFORE `'Low'` keeps every existing raw-SQL
 * `ORDER BY priority DESC` site ([FloaterService], [FloaterListService]) correctly sorting
 * High, Medium, Low, Lowest with ZERO query changes -- because Postgres orders a native enum
 * by its internal creation ordinal, never alphabetically and never by row insertion order.
 *
 * Unlike [CompletedFloaterConcurrencyTest] (which hand-recreates the enum with a hardcoded
 * value list, matching production's `DatabaseConfig` bootstrap path), this test runs the REAL
 * Flyway migration chain -- same `classpath:db/migration` location and baseline settings
 * `DatabaseConfig.init()` uses in production -- against a disposable real Postgres container.
 * That is what actually proves V28 applies cleanly on top of every prior migration and
 * produces the intended enum ordinal order, rather than only asserting the end state.
 *
 * It then calls the real production [FloaterService.getAll] -- the exact
 * `.orderBy(Floaters.priority to SortOrder.DESC, ...)` call site the migration exists to keep
 * correct -- and asserts the returned order, so this is proof against the production query,
 * not a hand-rolled substitute for it.
 *
 * Requires Docker; skipped (not failed) wherever it isn't available.
 */
class PriorityLowestOrdinalTest {

    companion object {
        private const val USER_ID = "user_owner_priority_ordinal"

        // Nullable backing field + non-null accessor instead of `lateinit`: JUnit5 guarantees
        // startContainer() (below) runs before any test method, but a plain `lateinit var`
        // would throw an opaque UninitializedPropertyAccessException if that ordering were
        // ever violated -- this fails with a clear message instead.
        private var postgresOrNull: PostgreSQLContainer<Nothing>? = null
        private val postgres: PostgreSQLContainer<Nothing>
            get() = checkNotNull(postgresOrNull) { "startContainer() must run before postgres is used" }

        @JvmStatic
        @BeforeAll
        fun startContainer() {
            val dockerAvailable = runCatching { DockerClientFactory.instance().isDockerAvailable }.getOrDefault(false)
            Assumptions.assumeTrue(
                dockerAvailable,
                "Docker is not available in this environment -- skipping the real-Postgres priority ordinal test",
            )
            postgresOrNull = PostgreSQLContainer<Nothing>(DockerImageName.parse("postgres:16-alpine")).apply { start() }
        }

        @JvmStatic
        @AfterAll
        fun stopContainer() {
            postgresOrNull?.stop()
        }
    }

    // Same nullable-backing-field pattern as `postgres` above, reassigned twice per setUp()
    // (see connectAndBootstrapExposedTables()).
    private var dbOrNull: Database? = null
    private val db: Database
        get() = checkNotNull(dbOrNull) { "setUp() must run before db is used" }
    private val push = NoOpPushNotificationService()
    private val cache = CacheServiceImpl()
    private val realtime = RealtimeServiceImpl()
    private val shareService = ListShareServiceImpl(cache, realtime, push)
    private val publisher = RealtimePublisher(realtime, shareService, cache, NoOpWebhookDispatchService, push)
    private val floaterService: FloaterService = FloaterServiceImpl(PassthroughFieldEncryption, cache, shareService, publisher)

    @BeforeEach
    fun setUp() {
        // V19__floaterproject_reusable.sql ALTERs the "floaterproject" table without an
        // `IF EXISTS` guard -- that table is entirely Exposed-owned (SchemaUtils, never a
        // Flyway CREATE TABLE), so on the real server it has only ever existed because some
        // earlier boot's DatabaseConfig.init() already ran Flyway-then-Exposed at least once
        // before V19 shipped. A single, uninterrupted Flyway replay from a truly empty schema
        // can't reproduce that -- it is not this migration's gap, or this test's, but it does
        // mean a faithful "fresh database, real migration chain" proof has to reproduce the
        // same two boots the real timeline had: migrate to the pre-V19 state, let Exposed
        // create its tables (exactly as DatabaseConfig.init() does on every real boot), THEN
        // continue the Flyway chain through V28 -- rather than asserting a single-pass replay
        // that production itself has never actually relied on since before V19 existed.
        runFlyway(targetVersion = "18")
        connectAndBootstrapExposedTables()

        val flywayResult = runFlyway(targetVersion = null)
        assertTrue(
            flywayResult.migrations.any { it.version == "28" },
            "expected V28 (add Lowest priority) to run; versions actually applied this phase: " +
                flywayResult.migrations.map { it.version },
        )

        // DatabaseConfig.init() runs this same Exposed bootstrap unconditionally after every
        // Flyway migrate() call, migration count notwithstanding -- repeat it here so the final
        // state matches a real second boot, even though every table already exists by now.
        connectAndBootstrapExposedTables()

        TestDatabase.insertUser(USER_ID, username = "owner-priority-ordinal@tday.test")
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
            // The exact enum-type bootstrap DatabaseConfig.init() runs -- "DefaultHomeScreen"
            // in particular is never created by a Flyway migration (see the task contract:
            // that IF NOT EXISTS block is what a fresh dev bootstrap relies on for it), so
            // skipping this here would fail UserPreferences' defaultHomeScreen column below
            // for a reason that has nothing to do with Priority/Lowest.
            bootstrapProductionPgEnums()

            // The exact table set DatabaseConfig.init() bootstraps in production.
            SchemaUtils.createMissingTablesAndColumns(
                Users, Accounts, VerificationTokens, Lists, FloaterLists, Todos, TodoInstances,
                CompletedTodos, Floaters, CompletedFloaters, Files, UserPreferences,
                EventLogs, CronLogs, AuthThrottles, AuthSignals, PushSubscriptions,
                ListShares, FloaterListShares,
            )
        }
    }

    @AfterEach
    fun tearDown() {
        // Full reset, including Flyway's own schema_history table -- each test method must see
        // a genuinely empty database, or a later test's Flyway phases silently no-op against
        // the previous test's already-applied history.
        transaction(db) {
            exec("DROP SCHEMA public CASCADE")
            exec("CREATE SCHEMA public")
        }
        TransactionManager.closeAndUnregister(db)
    }

    @Test
    fun `pg_enum ordinal order is Lowest, Low, Medium, High after the migration`() {
        val labels = transaction(db) {
            exec<List<String>>(
                """
                SELECT e.enumlabel
                FROM pg_enum e
                JOIN pg_type t ON t.oid = e.enumtypid
                WHERE t.typname = 'Priority'
                ORDER BY e.enumsortorder
                """.trimIndent(),
            ) { rs ->
                val out = mutableListOf<String>()
                while (rs.next()) out += rs.getString(1)
                out
            }
        }
        assertEquals(listOf("Lowest", "Low", "Medium", "High"), labels)
    }

    @Test
    fun `the real FloaterService orderBy(priority DESC) query returns High, Medium, Low, Lowest`() = runBlocking {
        // Inserted in scrambled order on purpose: if the assertion below only passed because
        // of insertion order rather than the enum ordinal, scrambling the insert order would
        // expose that.
        val lowest = createFloater("Lowest item", "Lowest")
        val high = createFloater("High item", "High")
        val low = createFloater("Low item", "Low")
        val medium = createFloater("Medium item", "Medium")

        val result = floaterService.getAll(USER_ID)
        assertTrue(result.isRight(), "getAll failed: $result")
        val ids = (result as Either.Right).value.map { it.id }

        assertEquals(listOf(high, medium, low, lowest), ids)
    }

    private suspend fun createFloater(title: String, priority: String): String {
        val result = floaterService.create(USER_ID, title, description = null, priority = priority, listID = null)
        check(result.isRight()) { "failed to create floater with priority=$priority: $result" }
        return (result as Either.Right).value.id
    }
}
