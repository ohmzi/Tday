package com.ohmz.tday.services

import arrow.core.Either
import com.ohmz.tday.config.DatabaseConfig
import com.ohmz.tday.domain.AppError
import com.ohmz.tday.domain.AuthenticatedUser
import com.ohmz.tday.observability.TelemetryGate
import com.ohmz.tday.security.SecurityEventLogger
import com.ohmz.tday.security.testAppConfig
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.sql.DriverManager
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [InstanceSettingsServiceTest] runs on H2 against the table Exposed derives from the Kotlin
 * object. This is the other half: the table Flyway's V32 really creates, on the Postgres the
 * server runs on, behind the real boot path. It is what proves the migration's column names and
 * the object agree, and that the upsert is valid Postgres.
 *
 * Requires Docker; skipped (not failed) wherever it isn't available.
 */
class InstanceSettingsPostgresTest {

    companion object {
        private var postgresOrNull: PostgreSQLContainer<Nothing>? = null
        private val postgres: PostgreSQLContainer<Nothing>
            get() = checkNotNull(postgresOrNull) { "startContainer() must run before postgres is used" }

        @JvmStatic
        @BeforeAll
        fun startContainer() {
            val dockerAvailable = runCatching { DockerClientFactory.instance().isDockerAvailable }.getOrDefault(false)
            Assumptions.assumeTrue(
                dockerAvailable,
                "Docker is not available in this environment -- skipping the real-Postgres instance settings test",
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
        TransactionManager.defaultDatabase?.let { TransactionManager.closeAndUnregister(it) }
    }

    @Test
    fun `the migrated table stores the flag and survives a restart`() = runBlocking {
        val config = testAppConfig().copy(
            databaseUrl = "${postgres.jdbcUrl}&user=${postgres.username}&password=${postgres.password}",
            sentryDsn = "https://publickey@o1.ingest.sentry.io/1",
        )
        DatabaseConfig(config).init()
        val admin = AuthenticatedUser(id = "admin_1", role = "ADMIN", approvalStatus = "APPROVED", timeZone = null)

        val gate = TelemetryGate()
        val service = InstanceSettingsServiceImpl(config, gate, NoOpSecurityEventLogger())
        service.loadTelemetryGate()
        assertFalse(gate.isOpen, "a fresh install must start with reports off")

        val enabled = service.setServerTelemetry(enabled = true, admin).orFail()
        assertTrue(enabled.enabled)
        assertTrue(enabled.dsnConfigured)
        assertTrue(gate.isOpen)

        // A second write is an update of the same row, not a second row.
        service.setServerTelemetry(enabled = false, admin).orFail()
        service.setServerTelemetry(enabled = true, admin).orFail()
        assertEquals("1", scalar("SELECT count(*) FROM instance_settings"))
        assertEquals("true", scalar("SELECT value FROM instance_settings WHERE key = 'telemetry.sentry.enabled'"))

        val afterRestart = TelemetryGate()
        InstanceSettingsServiceImpl(config, afterRestart, NoOpSecurityEventLogger()).loadTelemetryGate()
        assertTrue(afterRestart.isOpen, "the stored choice must come back at boot")
    }

    private fun <T> Either<AppError, T>.orFail(): T = fold({ error("expected success but got $it") }, { it })

    private fun scalar(sql: String): String =
        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { conn ->
            conn.createStatement().use { st -> st.executeQuery(sql).use { rs -> rs.next(); rs.getString(1) } }
        }

    private class NoOpSecurityEventLogger : SecurityEventLogger {
        override suspend fun log(reasonCode: String, details: Map<String, Any?>) = Unit
    }
}
