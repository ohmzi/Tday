package com.ohmz.tday.config

import com.ohmz.tday.security.testAppConfig
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A clean install must come up in a single pass: an EMPTY database, then
 * [DatabaseConfig.init] exactly as the server runs it at startup -- Flyway's whole chain, then
 * the Exposed bootstrap.
 *
 * That is not a given. Several tables (`floaterproject`, `completedfloaters`, ...) are owned by
 * Exposed's `SchemaUtils.createMissingTablesAndColumns`, which runs AFTER Flyway, so on a
 * database that has never booted the app they do not exist while the migrations run. A
 * migration that touches one without a guard (`ALTER TABLE IF EXISTS`, or a `to_regclass`
 * test around an `UPDATE`) fails the whole install -- V19, V27 and V31 each did, which left
 * every new self-hosted install unable to start while upgraded databases (that had booted
 * once before those migrations shipped) were fine. Nothing caught it, because the other
 * real-Postgres migration tests replay the chain in two phases to model an old database.
 *
 * This is the guard against the next migration doing the same: it runs the real
 * `classpath:db/migration` chain through the real `DatabaseConfig.init()` against a disposable
 * `postgres:15` (the version `docker-compose.yaml` pins), then boots a second time to prove the
 * restart path is a no-op.
 *
 * Requires Docker; skipped (not failed) wherever it isn't available.
 */
class FreshInstallMigrationTest {

    companion object {
        // Nullable backing field + non-null accessor instead of `lateinit`, as in the sibling
        // real-Postgres tests: a violated @BeforeAll ordering fails with a clear message.
        private var postgresOrNull: PostgreSQLContainer<Nothing>? = null
        private val postgres: PostgreSQLContainer<Nothing>
            get() = checkNotNull(postgresOrNull) { "startContainer() must run before postgres is used" }

        @JvmStatic
        @BeforeAll
        fun startContainer() {
            val dockerAvailable = runCatching { DockerClientFactory.instance().isDockerAvailable }.getOrDefault(false)
            Assumptions.assumeTrue(
                dockerAvailable,
                "Docker is not available in this environment -- skipping the real-Postgres fresh-install test",
            )
            postgresOrNull = PostgreSQLContainer<Nothing>(DockerImageName.parse("postgres:15-alpine")).apply { start() }
        }

        @JvmStatic
        @AfterAll
        fun stopContainer() {
            postgresOrNull?.stop()
        }
    }

    @AfterEach
    fun tearDown() {
        // DatabaseConfig.init() registers the Exposed database as the process-wide default.
        TransactionManager.defaultDatabase?.let { TransactionManager.closeAndUnregister(it) }
    }

    @Test
    fun `an empty database migrates and boots in a single pass`() {
        // DatabaseConfig accepts a jdbc: URL as-is, so the container's credentials ride in it.
        val config = testAppConfig().copy(
            databaseUrl = "${postgres.jdbcUrl}&user=${postgres.username}&password=${postgres.password}",
        )

        DatabaseConfig(config).init()

        connect().use { conn ->
            val latest = latestMigrationVersion()
            assertEquals(
                0,
                conn.scalar("SELECT count(*) FROM flyway_schema_history WHERE success = false").toInt(),
                "no migration may be recorded as failed",
            )
            assertEquals(
                latest.toString(),
                conn.scalar(
                    "SELECT version FROM flyway_schema_history WHERE success " +
                        "ORDER BY installed_rank DESC LIMIT 1",
                ),
                "the whole chain must have been applied, up to the newest migration file",
            )

            // Exposed creates these AFTER Flyway; the guarded migrations skipped them, so the
            // columns those migrations would have added must come from the Kotlin declarations.
            val columns = conn.column("SELECT table_name || '.' || column_name FROM information_schema.columns WHERE table_schema = 'public'")
            val expected = listOf(
                "floaterproject.reusable",
                "floaterproject.defaultPriority",
                "floaterproject.recreatedFromListID",
                "completedfloaters.originalListID",
                "project.defaultPriority",
            )
            assertTrue(
                columns.containsAll(expected),
                "missing columns after a fresh install: ${expected - columns.toSet()}",
            )
        }

        val historyRows = connect().use { it.scalar("SELECT count(*) FROM flyway_schema_history") }
        TransactionManager.defaultDatabase?.let { TransactionManager.closeAndUnregister(it) }

        // The second boot every real server does: nothing left to migrate, bootstrap is a no-op.
        DatabaseConfig(config).init()
        assertEquals(
            historyRows,
            connect().use { it.scalar("SELECT count(*) FROM flyway_schema_history") },
            "a restart must not apply anything new",
        )
    }

    private fun connect(): Connection =
        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password)

    private fun Connection.scalar(sql: String): String =
        createStatement().use { st -> st.executeQuery(sql).use { rs -> rs.next(); rs.getString(1) } }

    private fun Connection.column(sql: String): List<String> =
        createStatement().use { st ->
            st.executeQuery(sql).use { rs -> generateSequence { if (rs.next()) rs.getString(1) else null }.toList() }
        }

    /** Highest `V<n>__*.sql` on the classpath, so the assertion tracks new migrations by itself. */
    private fun latestMigrationVersion(): Int {
        val dir = File(checkNotNull(javaClass.classLoader.getResource("db/migration")).toURI())
        val pattern = Regex("""V(\d+)__.*\.sql""")
        return checkNotNull(dir.listFiles()).mapNotNull { pattern.matchEntire(it.name)?.groupValues?.get(1)?.toInt() }.max()
    }
}
