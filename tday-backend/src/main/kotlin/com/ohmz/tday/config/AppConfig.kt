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
    val lockoutFailThreshold: Int,
    val lockoutBaseSec: Int,
    val lockoutMaxSec: Int,
    val lockoutResetSec: Int,
    val captchaTriggerFailures: Int,
    val captchaSecret: String?,
    val alertIpFailureThreshold: Int,
    val alertLockoutBurstSec: Int,
    val signalAnomalyWindowSec: Int,
    val passwordProofChallengeTtlSec: Int,
    val passwordProofMaxActive: Int,
    val probeAppVersion: String?,
    val probeUpdateRequired: Boolean,
    val probeEncryptionKey: String?,
    val sentryDsn: String?,
) {
    companion object {
        fun load(): AppConfig {
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
                ollamaUrl = env("OLLAMA_URL", "http://ollama:11434"),
                ollamaModel = env("OLLAMA_MODEL", "qwen2.5:0.5b"),
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
                lockoutFailThreshold = envInt("AUTH_LOCKOUT_FAIL_THRESHOLD", 5),
                lockoutBaseSec = envInt("AUTH_LOCKOUT_BASE_SEC", 30),
                lockoutMaxSec = envInt("AUTH_LOCKOUT_MAX_SEC", 1800),
                lockoutResetSec = envInt("AUTH_LOCKOUT_RESET_SEC", 86400),
                captchaTriggerFailures = envInt("AUTH_CAPTCHA_TRIGGER_FAILURES", 3),
                captchaSecret = secret("AUTH_CAPTCHA_SECRET", "AUTH_CAPTCHA_SECRET_FILE"),
                alertIpFailureThreshold = envInt("AUTH_ALERT_IP_FAILURE_THRESHOLD", 12),
                alertLockoutBurstSec = envInt("AUTH_ALERT_LOCKOUT_BURST_SEC", 900),
                signalAnomalyWindowSec = envInt("AUTH_SIGNAL_ANOMALY_WINDOW_SEC", 86400),
                passwordProofChallengeTtlSec = envInt("AUTH_PASSWORD_PROOF_CHALLENGE_TTL_SEC", 120),
                passwordProofMaxActive = envInt("AUTH_PASSWORD_PROOF_MAX_ACTIVE", 5000),
                probeAppVersion = env("TDAY_APP_VERSION"),
                probeUpdateRequired = env("TDAY_UPDATE_REQUIRED", "false")
                    .equals("true", ignoreCase = true),
                probeEncryptionKey = secret("TDAY_PROBE_ENCRYPTION_KEY", "TDAY_PROBE_ENCRYPTION_KEY_FILE"),
                sentryDsn = env("SENTRY_DSN"),
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
