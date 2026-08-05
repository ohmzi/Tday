package com.ohmz.tday

import com.ohmz.tday.config.AppConfig
import com.ohmz.tday.config.DatabaseConfig
import com.ohmz.tday.di.configModule
import com.ohmz.tday.di.securityModule
import com.ohmz.tday.di.serviceModule
import com.ohmz.tday.plugins.SentryRequestPlugin
import com.ohmz.tday.plugins.configureCallLogging
import com.ohmz.tday.plugins.configureCors
import com.ohmz.tday.plugins.configureRateLimiting
import com.ohmz.tday.plugins.configureRouting
import com.ohmz.tday.plugins.configureSecurity
import com.ohmz.tday.plugins.configureSecurityHeaders
import com.ohmz.tday.plugins.configureSerialization
import com.ohmz.tday.plugins.configureStatusPages
import com.ohmz.tday.security.FieldEncryption
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.timeout
import io.sentry.Sentry
import kotlinx.coroutines.launch
import org.koin.ktor.ext.inject
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.seconds

private val logger = LoggerFactory.getLogger("com.ohmz.tday.Application")

fun main() {
    val config = AppConfig.load()

    Sentry.init { options ->
        options.dsn = config.sentryDsn.orEmpty()
        options.environment = if (config.isProduction) "production" else "development"
        options.release = "tday-backend@${config.backendVersion}"
        options.isSendDefaultPii = false
        options.serverName = "tday-backend"
        options.tracesSampleRate = config.sentryTracesSampleRate
        options.setBeforeSend { event, _ ->
            event.user?.ipAddress = null
            event.request?.url = event.request?.url?.let(com.ohmz.tday.observability.TdayObservability::sanitizePath)
            event.request?.queryString = null
            event
        }
    }

    logger.info("Starting Tday backend on port ${config.port}")
    embeddedServer(Netty, port = config.port, host = "0.0.0.0") {
        module(config)
    }.start(wait = true)
}

fun Application.module(config: AppConfig = AppConfig.load()) {
    install(Koin) {
        slf4jLogger()
        modules(configModule(config), securityModule, serviceModule)
    }

    val dbConfig by inject<DatabaseConfig>()
    try {
        dbConfig.init()
    } catch (e: Throwable) {
        logger.error("Database initialization failed during startup", e)
        throw e
    }

    install(WebSockets) {
        pingPeriod = 15.seconds
        timeout = 60.seconds
        maxFrameSize = 64 * 1024L
        masking = false
    }

    install(SentryRequestPlugin)

    configureCallLogging()
    configureSerialization()
    configureCors()
    configureSecurityHeaders()
    configureStatusPages()
    configureSecurity()
    val fieldEncryption by inject<FieldEncryption>()
    enforceStartupSecurityPolicy(config, fieldEncryption)
    configureRateLimiting()
    configureRouting()
    warmUpSummaryModel()
    startReminderPushScheduler()
    startRetentionScheduler()
    logger.info("Tday backend started successfully")
}

private fun Application.warmUpSummaryModel() {
    val todoSummaryService by inject<com.ohmz.tday.services.TodoSummaryService>()
    launch {
        runCatching { todoSummaryService.warmUp() }
            .onFailure { logger.warn("Summary model warm-up skipped: ${it.message}") }
    }
}

private fun Application.startReminderPushScheduler() {
    val scheduler by inject<com.ohmz.tday.services.ReminderPushScheduler>()
    launch {
        runCatching { scheduler.run() }
            .onFailure { logger.warn("Reminder push scheduler stopped: ${it.message}") }
    }
}

private fun Application.startRetentionScheduler() {
    val scheduler by inject<com.ohmz.tday.services.RetentionScheduler>()
    launch {
        runCatching { scheduler.run() }
            .onFailure { logger.warn("Retention scheduler stopped: ${it.message}") }
    }
}

/**
 * What a boot should do about field encryption.
 *
 * Split out as a pure function because the alternative — a control whose only failure mode is
 * "production refuses to start" — is otherwise impossible to exercise without a live Postgres,
 * and so ships never having run.
 */
internal enum class StartupEncryptionVerdict {
    /** Not production: local and CI runs are unaffected. */
    NotApplicable,
    Ok,
    /** No key configured, and the operator has not asked for one. Boots; says so once. */
    ProceedWithPlaintextNotice,
    /** The operator asked for encryption at rest and there is no usable key. */
    RefuseBoot,
}

/**
 * Field encryption is OPT-IN.
 *
 * The polarity here is deliberate and was chosen the hard way. Refusing to boot whenever a key is
 * absent sounds safer, but on a single-operator self-hosted box it protects almost nothing — anyone
 * who can read the database already has the host, and the key lives in the same `.env.docker` as
 * the Postgres volume. What it *does* reliably produce is an outage: a missing variable turns into
 * a container that exits on start, and `restart: always` turns that into a crash loop on a machine
 * reachable only over SSH.
 *
 * So the default boots and states plainly that content is plaintext. Operators who actually want
 * the guarantee set REQUIRE_ENCRYPTION_AT_REST=true, and then a missing key is a hard failure —
 * which is the case where failing closed is genuinely worth an outage.
 *
 * The threat this trades away is a stolen database dump. That is covered better by encrypting
 * backups (scripts/backup-database.sh), because the backup is the copy that leaves the host.
 */
internal fun startupEncryptionVerdict(
    isProduction: Boolean,
    encryptionConfigured: Boolean,
    requireEncryptionAtRest: Boolean,
): StartupEncryptionVerdict = when {
    !isProduction -> StartupEncryptionVerdict.NotApplicable
    encryptionConfigured -> StartupEncryptionVerdict.Ok
    requireEncryptionAtRest -> StartupEncryptionVerdict.RefuseBoot
    else -> StartupEncryptionVerdict.ProceedWithPlaintextNotice
}

private const val MISSING_ENCRYPTION_KEY_MESSAGE =
    "REQUIRE_ENCRYPTION_AT_REST=true but no usable DATA_ENCRYPTION_KEY/DATA_ENCRYPTION_KEYS is set. " +
        "Set DATA_ENCRYPTION_KEY to a 32-byte base64 or 64-char hex key, " +
        "or unset REQUIRE_ENCRYPTION_AT_REST to run with plaintext storage."

private fun enforceStartupSecurityPolicy(config: AppConfig, fieldEncryption: FieldEncryption) {
    when (startupEncryptionVerdict(config.isProduction, fieldEncryption.isConfigured(), config.requireEncryptionAtRest)) {
        StartupEncryptionVerdict.NotApplicable -> return
        StartupEncryptionVerdict.Ok -> Unit
        StartupEncryptionVerdict.RefuseBoot -> error(MISSING_ENCRYPTION_KEY_MESSAGE)
        // Not a warning: this is a supported configuration, not a mistake. It is logged every boot
        // so the state is never a surprise when reading the logs later.
        StartupEncryptionVerdict.ProceedWithPlaintextNotice -> logger.info(
            "Field encryption at rest is OFF (no DATA_ENCRYPTION_KEY). Task titles and descriptions " +
                "are stored as plaintext in Postgres — encrypt your backups. " +
                "Set REQUIRE_ENCRYPTION_AT_REST=true to make a missing key a startup failure.",
        )
    }

    if (config.credentialsPrivateKeyPem.isNullOrBlank()) {
        logger.warn("AUTH_CREDENTIALS_PRIVATE_KEY is unset in production; credential envelope encryption will use an ephemeral key")
    }

    if (config.appleTeamId.isNullOrBlank()) {
        logger.warn("APPLE_TEAM_ID is unset in production; iOS webcredentials association will be incomplete")
    }

    if (config.androidSha256CertFingerprints.isEmpty()) {
        logger.warn("ANDROID_SHA256_CERT_FINGERPRINTS is unset in production; Android web credential sharing will be incomplete")
    }
}
