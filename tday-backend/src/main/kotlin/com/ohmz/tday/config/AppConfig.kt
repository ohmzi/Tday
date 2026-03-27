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
    val credentialsPrivateKeyPem: String?,
    val dataEncryptionKeyId: String,
    val dataEncryptionKey: String?,
    val dataEncryptionKeys: String?,
    val dataEncryptionAad: String?,
    val ollamaUrl: String,
    val ollamaModel: String,
    val ollamaTimeoutMs: Long,
    val limitCsrfWindowSec: Int,
    val limitCsrfMax: Int,
    val limitCredentialsWindowSec: Int,
    val limitCredentialsMax: Int,
    val limitSessionWindowSec: Int,
    val limitSessionMax: Int,
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
) {
    companion object {
        fun load(): AppConfig = AppConfig(
            port = env("PORT", "8080").toInt(),
            databaseUrl = secret("DATABASE_URL", "DATABASE_URL_FILE")
                ?: error("DATABASE_URL is required"),
            authSecret = secret("AUTH_SECRET", "AUTH_SECRET_FILE")
                ?: error("AUTH_SECRET is required"),
            isProduction = resolveEnvironmentName().equals("production", ignoreCase = true),
            corsAllowedOrigins = envCsv("CORS_ALLOWED_ORIGINS"),
            pbkdf2Iterations = envInt("AUTH_PBKDF2_ITERATIONS", 310_000)
                .coerceIn(100_000, 2_000_000),
            sessionMaxAgeSec = envInt("AUTH_SESSION_MAX_AGE_SEC", 86400)
                .coerceIn(3600, 2_592_000),
            credentialsPrivateKeyPem = secret("AUTH_CREDENTIALS_PRIVATE_KEY", "AUTH_CREDENTIALS_PRIVATE_KEY_FILE"),
            dataEncryptionKeyId = env("DATA_ENCRYPTION_KEY_ID", "primary"),
            dataEncryptionKey = secret("DATA_ENCRYPTION_KEY", "DATA_ENCRYPTION_KEY_FILE"),
            dataEncryptionKeys = secret("DATA_ENCRYPTION_KEYS", "DATA_ENCRYPTION_KEYS_FILE"),
            dataEncryptionAad = env("DATA_ENCRYPTION_AAD"),
            ollamaUrl = env("OLLAMA_URL", "http://ollama:11434"),
            ollamaModel = env("OLLAMA_MODEL", "qwen2.5:0.5b"),
            ollamaTimeoutMs = env("OLLAMA_TIMEOUT_MS", "15000").toLong(),
            limitCsrfWindowSec = envInt("AUTH_LIMIT_CSRF_WINDOW_SEC", 60),
            limitCsrfMax = envInt("AUTH_LIMIT_CSRF_MAX", 40),
            limitCredentialsWindowSec = envInt("AUTH_LIMIT_CREDENTIALS_WINDOW_SEC", 300),
            limitCredentialsMax = envInt("AUTH_LIMIT_CREDENTIALS_MAX", 12),
            limitSessionWindowSec = envInt("AUTH_LIMIT_SESSION_WINDOW_SEC", 60),
            limitSessionMax = envInt("AUTH_LIMIT_SESSION_MAX", 60),
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
        )

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
