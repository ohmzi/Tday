package com.ohmz.tday.services

import arrow.core.Either
import arrow.core.raise.either
import com.ohmz.tday.config.AppConfig
import com.ohmz.tday.db.tables.InstanceSettings
import com.ohmz.tday.domain.AppError
import com.ohmz.tday.domain.AuthenticatedUser
import com.ohmz.tday.domain.requireAdminAccess
import com.ohmz.tday.models.response.InstanceTelemetryResponse
import com.ohmz.tday.models.response.ServerTelemetryResponse
import com.ohmz.tday.observability.TelemetryGate
import com.ohmz.tday.security.SecurityEventLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.upsert
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.time.ZoneOffset

private const val TELEMETRY_ENABLED_KEY = "telemetry.sentry.enabled"

interface InstanceSettingsService {
    suspend fun serverTelemetry(admin: AuthenticatedUser): Either<AppError, ServerTelemetryResponse>
    suspend fun setServerTelemetry(enabled: Boolean, admin: AuthenticatedUser): Either<AppError, ServerTelemetryResponse>

    /**
     * Sets the telemetry gate from the stored flag. Runs once at boot, right after migrations,
     * before any request is served. A flag that cannot be read leaves the gate closed.
     */
    fun loadTelemetryGate()

    /**
     * The instance answer a browser reads before it has a session: whether the admin allows reports,
     * and since when. It is the same setting [serverTelemetry] returns, minus the DSN state, so an
     * anonymous caller cannot learn anything about how this server is configured.
     */
    suspend fun instanceTelemetry(): InstanceTelemetryResponse
}

class InstanceSettingsServiceImpl(
    private val config: AppConfig,
    private val telemetryGate: TelemetryGate,
    private val securityEventLogger: SecurityEventLogger,
) : InstanceSettingsService {

    private val writeLock = Mutex()

    override suspend fun serverTelemetry(admin: AuthenticatedUser): Either<AppError, ServerTelemetryResponse> = either {
        admin.requireAdminAccess().bind()
        newSuspendedTransaction(Dispatchers.IO) { readTelemetry() }
    }

    override suspend fun setServerTelemetry(
        enabled: Boolean,
        admin: AuthenticatedUser,
    ): Either<AppError, ServerTelemetryResponse> = either {
        admin.requireAdminAccess().bind()

        // One writer at a time, so two admins toggling together cannot leave the gate holding
        // the answer the database does not.
        val response = writeLock.withLock {
            val stored = newSuspendedTransaction(Dispatchers.IO) {
                InstanceSettings.upsert {
                    it[settingKey] = TELEMETRY_ENABLED_KEY
                    it[settingValue] = enabled.toString()
                    it[updatedAt] = LocalDateTime.now(ZoneOffset.UTC)
                }
                readTelemetry()
            }
            // After the commit: a failed write must not leave the gate open on a setting that
            // was never stored.
            telemetryGate.set(enabled)
            stored
        }
        securityEventLogger.log(if (enabled) "telemetry_enabled" else "telemetry_disabled")
        response
    }

    override fun loadTelemetryGate() {
        try {
            telemetryGate.set(transaction { readTelemetry() }.enabled)
        } catch (e: Exception) {
            // Class only: a database driver's message can carry the host or a connection string.
            logger.warn("Server error reports stay off: the setting could not be read ({})", e.javaClass.simpleName)
        }
    }

    override suspend fun instanceTelemetry(): InstanceTelemetryResponse =
        try {
            val stored = newSuspendedTransaction(Dispatchers.IO) { readTelemetry() }
            InstanceTelemetryResponse(enabled = stored.enabled, updatedAt = stored.updatedAt)
        } catch (e: Exception) {
            // Fail closed: a browser that cannot read the answer does not start its SDK.
            logger.warn("Browser error reports stay off: the setting could not be read ({})", e.javaClass.simpleName)
            InstanceTelemetryResponse(enabled = false, updatedAt = null)
        }

    private fun readTelemetry(): ServerTelemetryResponse {
        val row = InstanceSettings.selectAll().where { InstanceSettings.settingKey eq TELEMETRY_ENABLED_KEY }.firstOrNull()
        return ServerTelemetryResponse(
            dsnConfigured = !config.sentryDsn.isNullOrBlank(),
            enabled = row?.get(InstanceSettings.settingValue) == "true",
            updatedAt = row?.get(InstanceSettings.updatedAt)?.toInstant(ZoneOffset.UTC)?.toString(),
        )
    }
    private companion object {
        val logger = LoggerFactory.getLogger(InstanceSettingsServiceImpl::class.java)
    }
}
