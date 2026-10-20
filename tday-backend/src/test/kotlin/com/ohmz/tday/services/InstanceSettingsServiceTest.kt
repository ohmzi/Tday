package com.ohmz.tday.services

import arrow.core.Either
import com.ohmz.tday.db.TestDatabase
import com.ohmz.tday.db.tables.InstanceSettings
import com.ohmz.tday.domain.AppError
import com.ohmz.tday.domain.AuthenticatedUser
import com.ohmz.tday.observability.TelemetryGate
import com.ohmz.tday.security.SecurityEventLogger
import com.ohmz.tday.security.testAppConfig
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.concurrent.Executors
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InstanceSettingsServiceTest {
    // JUnit builds a new instance per test method, so this is one empty database per test.
    private val db: Database = TestDatabase.fresh()
    private val gate = TelemetryGate()
    private val events = RecordingSecurityEventLogger()

    private val admin = AuthenticatedUser(id = "admin_1", role = "ADMIN", approvalStatus = "APPROVED", timeZone = null)
    private val member = AuthenticatedUser(id = "user_1", role = "USER", approvalStatus = "APPROVED", timeZone = null)

    @AfterEach
    fun tearDown() {
        TestDatabase.close(db)
    }

    @Test
    fun `reports reports as off with no update time before anyone chooses`() = runBlocking {
        val telemetry = service().serverTelemetry(admin).orFail()

        assertFalse(telemetry.enabled)
        assertNull(telemetry.updatedAt)
    }

    @Test
    fun `enabling stores the flag opens the gate and answers with the stored state`() = runBlocking {
        val telemetry = service().setServerTelemetry(enabled = true, admin).orFail()

        assertTrue(telemetry.enabled)
        assertTrue(gate.isOpen)
        assertNotNull(telemetry.updatedAt).let { Instant.parse(it) }
        assertEquals(telemetry, service().serverTelemetry(admin).orFail())
        transaction(db) {
            val row = InstanceSettings.selectAll().single()
            assertEquals("telemetry.sentry.enabled", row[InstanceSettings.settingKey])
            assertEquals("true", row[InstanceSettings.settingValue])
        }
    }

    @Test
    fun `disabling closes the gate and keeps a single row`() = runBlocking {
        service().setServerTelemetry(enabled = true, admin).orFail()

        val telemetry = service().setServerTelemetry(enabled = false, admin).orFail()

        assertFalse(telemetry.enabled)
        assertFalse(gate.isOpen)
        transaction(db) {
            val row = InstanceSettings.selectAll().single()
            assertEquals("false", row[InstanceSettings.settingValue])
        }
    }

    @Test
    fun `records only a reason code for each change`() = runBlocking {
        service().setServerTelemetry(enabled = true, admin).orFail()
        service().setServerTelemetry(enabled = false, admin).orFail()

        assertEquals(listOf("telemetry_enabled", "telemetry_disabled"), events.reasonCodes)
        assertTrue(events.details.all { it.isEmpty() })
    }

    @Test
    fun `a member cannot read or change it and the gate stays closed`() = runBlocking {
        val read = service().serverTelemetry(member)
        val write = service().setServerTelemetry(enabled = true, member)

        assertIs<AppError.Forbidden>(read.leftOrNull())
        assertIs<AppError.Forbidden>(write.leftOrNull())
        assertFalse(gate.isOpen)
        transaction(db) { assertEquals(0, InstanceSettings.selectAll().count()) }
        assertTrue(events.reasonCodes.isEmpty())
    }

    @Test
    fun `reports whether a dsn is configured without exposing it`() = runBlocking {
        val without = service(dsn = null).serverTelemetry(admin).orFail()
        val blank = service(dsn = "  ").serverTelemetry(admin).orFail()
        val with = service(dsn = DSN).serverTelemetry(admin).orFail()

        assertFalse(without.dsnConfigured)
        assertFalse(blank.dsnConfigured)
        assertTrue(with.dsnConfigured)
    }

    @Test
    fun `the flag is stored even when no dsn is configured`() = runBlocking {
        val telemetry = service(dsn = null).setServerTelemetry(enabled = true, admin).orFail()

        assertTrue(telemetry.enabled)
        assertFalse(telemetry.dsnConfigured)
    }

    @Test
    fun `boot opens the gate when an admin had enabled reports`() = runBlocking {
        service().setServerTelemetry(enabled = true, admin).orFail()
        val restarted = TelemetryGate()

        service(gate = restarted).loadTelemetryGate()

        assertTrue(restarted.isOpen)
    }

    @Test
    fun `boot leaves the gate closed when nothing was ever chosen or it was turned off`() = runBlocking {
        val fresh = TelemetryGate()
        service(gate = fresh).loadTelemetryGate()
        assertFalse(fresh.isOpen)

        service().setServerTelemetry(enabled = true, admin).orFail()
        service().setServerTelemetry(enabled = false, admin).orFail()
        val restarted = TelemetryGate()
        service(gate = restarted).loadTelemetryGate()
        assertFalse(restarted.isOpen)
    }

    @Test
    fun `boot leaves the gate closed when the setting cannot be read`() {
        val unreadable = TelemetryGate()
        transaction(db) { exec("DROP TABLE instance_settings") }

        service(gate = unreadable).loadTelemetryGate()

        assertFalse(unreadable.isOpen)
    }

    @Test
    fun `overlapping toggles leave the gate agreeing with the stored row`() = runBlocking {
        // The first toggle's caller is slow to resume after its commit. A toggle that is allowed
        // to overlap it commits its own row and sets the gate in that gap, and the older toggle
        // then overwrites the gate with its stale answer.
        val slowCaller = Executors.newSingleThreadExecutor()
        val fastCaller = Executors.newSingleThreadExecutor()
        try {
            val service = service()
            val first = async(slowCaller.asCoroutineDispatcher()) {
                service.setServerTelemetry(enabled = true, admin).orFail()
            }
            slowCaller.execute { Thread.sleep(400) }
            delay(100)
            val second = async(fastCaller.asCoroutineDispatcher()) {
                service.setServerTelemetry(enabled = false, admin).orFail()
            }
            first.await()
            second.await()

            val stored = transaction(db) { InstanceSettings.selectAll().single()[InstanceSettings.settingValue] }
            assertEquals("false", stored)
            assertEquals(stored == "true", gate.isOpen, "the gate disagrees with the stored row")
        } finally {
            slowCaller.shutdownNow()
            fastCaller.shutdownNow()
        }
    }

    @Test
    fun `a write that fails leaves the gate where it was and surfaces the error`() = runBlocking {
        val service = service()
        service.setServerTelemetry(enabled = true, admin).orFail()
        transaction(db) { exec("DROP TABLE instance_settings") }

        assertFails { service.setServerTelemetry(enabled = false, admin) }

        assertTrue(gate.isOpen, "a setting that was never stored must not close the gate")
        assertEquals(listOf("telemetry_enabled"), events.reasonCodes)
    }

    @Test
    fun `a failed first write leaves the gate closed`() = runBlocking {
        transaction(db) { exec("DROP TABLE instance_settings") }

        assertFails { service().setServerTelemetry(enabled = true, admin) }

        assertFalse(gate.isOpen, "a setting that was never stored must not open the gate")
        assertTrue(events.reasonCodes.isEmpty())
    }

    private fun <T> Either<AppError, T>.orFail(): T = fold({ error("expected success but got $it") }, { it })

    private fun service(
        gate: TelemetryGate = this.gate,
        dsn: String? = DSN,
    ) = InstanceSettingsServiceImpl(
        config = testAppConfig().copy(sentryDsn = dsn),
        telemetryGate = gate,
        securityEventLogger = events,
    )

    private class RecordingSecurityEventLogger : SecurityEventLogger {
        val reasonCodes = mutableListOf<String>()
        val details = mutableListOf<Map<String, Any?>>()

        override suspend fun log(reasonCode: String, details: Map<String, Any?>) {
            reasonCodes += reasonCode
            this.details += details
        }
    }

    private companion object {
        const val DSN = "https://publickey@o1.ingest.sentry.io/1"
    }
}
