package com.ohmz.tday.config

import java.io.File

data class AppConfig(
    val port: Int,
    val databaseUrl: String,
    val authSecret: String,
    val isProduction: Boolean,
    val corsAllowedOrigins: List<String>,
    val pbkdf2Iterations: Int,
    val sessionMaxAgeSec: Int,
    val sessionAbsoluteMaxAgeSec: Int,
    val sessionRenewThresholdSec: Int,
    val credentialsPrivateKeyPem: String?,
    val dataEncryptionKeyId: String,
    val dataEncryptionKey: String?,
    val dataEncryptionKeys: String?,
    val dataEncryptionAad: String?,
    val requireEncryptionAtRest: Boolean,
    val ollamaUrl: String,
    val ollamaModel: String,
    val ollamaTimeoutMs: Long,
    val apiRateLimitWindowSec: Int,
    val apiRateLimitMax: Int,
    val infraRateLimitWindowSec: Int,
    val infraRateLimitMax: Int,
    val summaryRateLimitWindowSec: Int,
    val summaryRateLimitMax: Int,
    val changePasswordRateLimitWindowSec: Int,
    val changePasswordRateLimitMax: Int,
    val wsRateLimitWindowSec: Int,
    val wsRateLimitMax: Int,
    val limitCsrfWindowSec: Int,
    val limitCsrfMax: Int,
    val limitCredentialsWindowSec: Int,
    val limitCredentialsMax: Int,
    val limitSessionGetWindowSec: Int,
    val limitSessionGetMax: Int,
    val limitCredentialsKeyWindowSec: Int,
    val limitCredentialsKeyMax: Int,
    val limitRegisterWindowSec: Int,
    val limitRegisterMax: Int,
    val limitRegisterBurstWindowSec: Int,
    val limitRegisterBurstMax: Int,
    val limitCredentialsAccountWindowSec: Int,
    val limitCredentialsAccountMax: Int,
    val lockoutFailThreshold: Int,
    val lockoutBaseSec: Int,
    val lockoutMaxSec: Int,
    val lockoutResetSec: Int,
    val alertIpFailureThreshold: Int,
    val alertLockoutBurstSec: Int,
    val signalAnomalyWindowSec: Int,
    val abuseSignalWindowSec: Int,
    val abuseRegisterViolationMax: Int,
    val abuseRegisterPendingMax: Int,
    val abuseAuthLockoutMax: Int,
    val abuseBlockBaseSec: Int,
    val abuseBlockMaxSec: Int,
    val abuseStrikeDecaySec: Int,
    val securityAlertCooldownSec: Int,
    val securityAlertAnomalyMinCount: Int,
    val passwordProofChallengeTtlSec: Int,
    val passwordProofMaxActive: Int,
    val probeAppVersion: String?,
    val probeUpdateRequired: Boolean,
    val probeCompatibilityMode: String,
    val probeEncryptionKey: String?,
    val appleTeamId: String?,
    val iosBundleId: String,
    val androidPackageName: String,
    val androidSha256CertFingerprints: List<String>,
    val vapidPublicKey: String?,
    val vapidPrivateKey: String?,
    val retentionEventLogDays: Int,
    val retentionAuthThrottleDays: Int,
    val retentionAuthSignalDays: Int,
    val retentionCronLogDays: Int,
    val retentionDryRun: Boolean,
    val cspMode: String?,
    val cspConnectExtra: List<String>,
    val sentryDsn: String?,
    val sentryTracesSampleRate: Double,
    val backendVersion: String,
) {
    companion object {
        fun load(): AppConfig {
            val versionDefaults = AppVersionDefaultsLoader.load()
            val sessionMaxAgeSec = envInt("AUTH_SESSION_MAX_AGE_SEC", 2_592_000)
                .coerceIn(3600, 2_592_000)
            val sessionAbsoluteMaxAgeSec = envInt("AUTH_SESSION_ABSOLUTE_MAX_AGE_SEC", 7_776_000)
                .coerceIn(sessionMaxAgeSec, 31_536_000)
            val sessionRenewThresholdSec = envInt("AUTH_SESSION_RENEW_THRESHOLD_SEC", 604_800)
                .coerceIn(60, sessionMaxAgeSec)

            return AppConfig(
                port = env("PORT", "8080").toInt(),
                databaseUrl = secret("DATABASE_URL", "DATABASE_URL_FILE")
                    ?: error("DATABASE_URL is required"),
                authSecret = secret("AUTH_SECRET", "AUTH_SECRET_FILE")
                    ?: error("AUTH_SECRET is required"),
                isProduction = resolveEnvironmentName().equals("production", ignoreCase = true),
                corsAllowedOrigins = envCsv("CORS_ALLOWED_ORIGINS"),
                pbkdf2Iterations = envInt("AUTH_PBKDF2_ITERATIONS", 310_000)
                    .coerceIn(100_000, 2_000_000),
                sessionMaxAgeSec = sessionMaxAgeSec,
                sessionAbsoluteMaxAgeSec = sessionAbsoluteMaxAgeSec,
                sessionRenewThresholdSec = sessionRenewThresholdSec,
                credentialsPrivateKeyPem = secret("AUTH_CREDENTIALS_PRIVATE_KEY", "AUTH_CREDENTIALS_PRIVATE_KEY_FILE"),
                dataEncryptionKeyId = env("DATA_ENCRYPTION_KEY_ID", "primary"),
                dataEncryptionKey = secret("DATA_ENCRYPTION_KEY", "DATA_ENCRYPTION_KEY_FILE"),
                dataEncryptionKeys = secret("DATA_ENCRYPTION_KEYS", "DATA_ENCRYPTION_KEYS_FILE"),
                dataEncryptionAad = env("DATA_ENCRYPTION_AAD"),
                // Field encryption is opt-in. Off by default so a missing key can never take a
                // self-hosted server down; set this to make a missing key a hard startup failure.
                requireEncryptionAtRest = env("REQUIRE_ENCRYPTION_AT_REST", "false").equals("true", ignoreCase = true),
                ollamaUrl = env("OLLAMA_URL", ""),
                ollamaModel = env("OLLAMA_MODEL", "qwen3.5:0.8b"),
                ollamaTimeoutMs = env("OLLAMA_TIMEOUT_MS", "15000").toLong(),
                apiRateLimitWindowSec = envInt("API_RATE_LIMIT_WINDOW_SEC", 60),
                apiRateLimitMax = envInt("API_RATE_LIMIT_MAX", 180),
                infraRateLimitWindowSec = envInt("INFRA_RATE_LIMIT_WINDOW_SEC", 60),
                infraRateLimitMax = envInt("INFRA_RATE_LIMIT_MAX", 30),
                summaryRateLimitWindowSec = envInt("SUMMARY_RATE_LIMIT_WINDOW_SEC", 60),
                summaryRateLimitMax = envInt("SUMMARY_RATE_LIMIT_MAX", 10),
                changePasswordRateLimitWindowSec = envInt("CHANGE_PASSWORD_RATE_LIMIT_WINDOW_SEC", 300),
                changePasswordRateLimitMax = envInt("CHANGE_PASSWORD_RATE_LIMIT_MAX", 8),
                wsRateLimitWindowSec = envInt("WS_RATE_LIMIT_WINDOW_SEC", 60),
                wsRateLimitMax = envInt("WS_RATE_LIMIT_MAX", 30),
                limitCsrfWindowSec = envInt("AUTH_LIMIT_CSRF_WINDOW_SEC", 60),
                limitCsrfMax = envInt("AUTH_LIMIT_CSRF_MAX", 40),
                limitCredentialsWindowSec = envInt("AUTH_LIMIT_CREDENTIALS_WINDOW_SEC", 300),
                limitCredentialsMax = envInt("AUTH_LIMIT_CREDENTIALS_MAX", 12),
                limitSessionGetWindowSec = envInt("AUTH_LIMIT_SESSION_GET_WINDOW_SEC", 60),
                limitSessionGetMax = envInt("AUTH_LIMIT_SESSION_GET_MAX", 20),
                limitCredentialsKeyWindowSec = envInt("AUTH_LIMIT_CREDENTIALS_KEY_WINDOW_SEC", 60),
                limitCredentialsKeyMax = envInt("AUTH_LIMIT_CREDENTIALS_KEY_MAX", 20),
                limitRegisterWindowSec = envInt("AUTH_LIMIT_REGISTER_WINDOW_SEC", 3600),
                limitRegisterMax = envInt("AUTH_LIMIT_REGISTER_MAX", 6),
                limitRegisterBurstWindowSec = envInt("AUTH_LIMIT_REGISTER_BURST_WINDOW_SEC", 600),
                limitRegisterBurstMax = envInt("AUTH_LIMIT_REGISTER_BURST_MAX", 3),
                limitCredentialsAccountWindowSec = envInt("AUTH_LIMIT_CREDENTIALS_ACCOUNT_WINDOW_SEC", 900),
                limitCredentialsAccountMax = envInt("AUTH_LIMIT_CREDENTIALS_ACCOUNT_MAX", 50),
                lockoutFailThreshold = envInt("AUTH_LOCKOUT_FAIL_THRESHOLD", 5),
                lockoutBaseSec = envInt("AUTH_LOCKOUT_BASE_SEC", 30),
                lockoutMaxSec = envInt("AUTH_LOCKOUT_MAX_SEC", 1800),
                lockoutResetSec = envInt("AUTH_LOCKOUT_RESET_SEC", 86400),
                alertIpFailureThreshold = envInt("AUTH_ALERT_IP_FAILURE_THRESHOLD", 12),
                alertLockoutBurstSec = envInt("AUTH_ALERT_LOCKOUT_BURST_SEC", 900),
                signalAnomalyWindowSec = envInt("AUTH_SIGNAL_ANOMALY_WINDOW_SEC", 86400),
                // Adaptive abuse blocking. Registration stays open and the reset lookup keeps
                // telling callers whether a username exists — these knobs only decide when one
                // source has abused those paths enough to lose access to them for a while.
                // Deliberately longer than AUTH_LOCKOUT_RESET_SEC (24h): the failure counter resets after a
                // day of quiet, so a window of the same length let signals age out exactly as fast as
                // they could be earned and no auth block was ever reachable.
                abuseSignalWindowSec = envInt("ABUSE_SIGNAL_WINDOW_SEC", 604_800),
                abuseRegisterViolationMax = envInt("ABUSE_REGISTER_VIOLATION_MAX", 5),
                // A real person registers once. More than this many accounts from one source still
                // sitting unapproved is the strongest available signal of mass registration.
                abuseRegisterPendingMax = envInt("ABUSE_REGISTER_PENDING_MAX", 3),
                // Counts attempts made while ALREADY locked out. Set well above what a person who forgot
                // their password produces — they read "try again in 30s" and wait; a script does not.
                abuseAuthLockoutMax = envInt("ABUSE_AUTH_LOCKOUT_MAX", 10),
                // 1h, then x24 per strike, capped at 7d. Blocks always expire on their own.
                abuseBlockBaseSec = envInt("ABUSE_BLOCK_BASE_SEC", 3600),
                abuseBlockMaxSec = envInt("ABUSE_BLOCK_MAX_SEC", 604800),
                abuseStrikeDecaySec = envInt("ABUSE_STRIKE_DECAY_SEC", 2592000),
                // At most one admin push per alert type per cooldown, no matter the event volume.
                securityAlertCooldownSec = envInt("SECURITY_ALERT_COOLDOWN_SEC", 900),
                securityAlertAnomalyMinCount = envInt("SECURITY_ALERT_ANOMALY_MIN_COUNT", 3),
                passwordProofChallengeTtlSec = envInt("AUTH_PASSWORD_PROOF_CHALLENGE_TTL_SEC", 120),
                passwordProofMaxActive = envInt("AUTH_PASSWORD_PROOF_MAX_ACTIVE", 5000),
                probeAppVersion = env("TDAY_APP_VERSION") ?: versionDefaults.version,
                probeUpdateRequired = env("TDAY_UPDATE_REQUIRED")
                    ?.equals("true", ignoreCase = true)
                    ?: versionDefaults.updateRequired,
                probeCompatibilityMode = env("TDAY_COMPATIBILITY_MODE")
                    ?: versionDefaults.compatibilityMode,
                probeEncryptionKey = secret("TDAY_PROBE_ENCRYPTION_KEY", "TDAY_PROBE_ENCRYPTION_KEY_FILE"),
                appleTeamId = env("APPLE_TEAM_ID"),
                iosBundleId = env("IOS_BUNDLE_ID", "com.ohmz.tday.ios"),
                androidPackageName = env("ANDROID_PACKAGE_NAME", "com.ohmz.tday.compose"),
                androidSha256CertFingerprints = envCsv("ANDROID_SHA256_CERT_FINGERPRINTS"),
                vapidPublicKey = secret("VAPID_PUBLIC_KEY", "VAPID_PUBLIC_KEY_FILE"),
                vapidPrivateKey = secret("VAPID_PRIVATE_KEY", "VAPID_PRIVATE_KEY_FILE"),
                // enforce | report-only | off. The escape hatch: a missed directive white-screens
                // the SPA, and flipping this beats rebuilding the image to recover.
                // 0 disables retention for a table. cronLog is floored because it holds the
                // reminder scheduler's last-run bookmark — trimming it too hard drops reminders.
                retentionEventLogDays = envInt("RETENTION_EVENTLOG_DAYS", 90),
                retentionAuthThrottleDays = envInt("RETENTION_AUTHTHROTTLE_DAYS", 30),
                retentionAuthSignalDays = envInt("RETENTION_AUTHSIGNAL_DAYS", 180),
                retentionCronLogDays = envInt("RETENTION_CRONLOG_DAYS", 90).coerceAtLeast(7),
                // Defaults on for one release: the operator reads a cycle's CronLog output before
                // the first real DELETE runs against their only copy of the data.
                retentionDryRun = env("RETENTION_DRY_RUN", "true").equals("true", ignoreCase = true),
                cspMode = env("CSP_MODE"),
                cspConnectExtra = envCsv("CSP_CONNECT_EXTRA"),
                sentryDsn = env("SENTRY_DSN"),
                sentryTracesSampleRate = envDouble("SENTRY_TRACES_SAMPLE_RATE", if (resolveEnvironmentName().equals("production", ignoreCase = true)) 0.2 else 1.0)
                    .coerceIn(0.0, 1.0),
                backendVersion = env("TDAY_BACKEND_VERSION")
                    ?: env("TDAY_APP_VERSION")
                    ?: versionDefaults.version,
            )
        }

        fun env(key: String, default: String = ""): String =
            System.getenv(key)?.trim()?.ifEmpty { null } ?: default

        fun env(key: String): String? =
            System.getenv(key)?.trim()?.ifEmpty { null }

        fun envCsv(key: String): List<String> =
            env(key)
                ?.split(',')
                ?.map(String::trim)
                ?.filter(String::isNotEmpty)
                .orEmpty()

        fun envInt(key: String, default: Int): Int {
            val raw = System.getenv(key)?.trim() ?: return default
            return raw.toIntOrNull()?.takeIf { it > 0 } ?: default
        }

        fun envDouble(key: String, default: Double): Double {
            val raw = System.getenv(key)?.trim() ?: return default
            return raw.toDoubleOrNull() ?: default
        }

        private fun resolveEnvironmentName(): String =
            env("TDAY_ENV")
                ?: env("NODE_ENV")
                ?: "development"

        fun secret(envVar: String, fileEnvVar: String): String? {
            val direct = System.getenv(envVar)?.trim()?.ifEmpty { null }
            if (direct != null) return direct

            val filePath = System.getenv(fileEnvVar)?.trim()?.ifEmpty { null }
            if (filePath != null) {
                return try {
                    File(filePath).readText().trim().ifEmpty { null }
                } catch (e: Exception) {
                    System.err.println("[config] Unable to read secret file $fileEnvVar=$filePath: ${e.message}")
                    null
                }
            }
            return null
        }
    }
}
