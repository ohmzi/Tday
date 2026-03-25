package com.ohmz.tday.config

import java.io.File

object AppConfig {
    val port: Int = env("PORT", "8080").toInt()

    val databaseUrl: String = secret("DATABASE_URL", "DATABASE_URL_FILE")
        ?: error("DATABASE_URL is required")

    val authSecret: String = secret("AUTH_SECRET", "AUTH_SECRET_FILE")
        ?: error("AUTH_SECRET is required")

    val pbkdf2Iterations: Int = envInt("AUTH_PBKDF2_ITERATIONS", 310_000)
        .coerceIn(100_000, 2_000_000)

    val sessionMaxAgeSec: Int = envInt("AUTH_SESSION_MAX_AGE_SEC", 86400)
        .coerceIn(3600, 2_592_000)

    val credentialsPrivateKeyPem: String? =
        secret("AUTH_CREDENTIALS_PRIVATE_KEY", "AUTH_CREDENTIALS_PRIVATE_KEY_FILE")

    val dataEncryptionKeyId: String = env("DATA_ENCRYPTION_KEY_ID", "primary")
    val dataEncryptionKey: String? = secret("DATA_ENCRYPTION_KEY", "DATA_ENCRYPTION_KEY_FILE")
    val dataEncryptionKeys: String? = secret("DATA_ENCRYPTION_KEYS", "DATA_ENCRYPTION_KEYS_FILE")
    val dataEncryptionAad: String? = env("DATA_ENCRYPTION_AAD")

    val ollamaUrl: String = env("OLLAMA_URL", "http://ollama:11434")
    val ollamaModel: String = env("OLLAMA_MODEL", "qwen2.5:0.5b")
    val ollamaTimeoutMs: Long = env("OLLAMA_TIMEOUT_MS", "15000").toLong()

    val limitCsrfWindowSec: Int = envInt("AUTH_LIMIT_CSRF_WINDOW_SEC", 60)
    val limitCsrfMax: Int = envInt("AUTH_LIMIT_CSRF_MAX", 40)
    val limitCredentialsWindowSec: Int = envInt("AUTH_LIMIT_CREDENTIALS_WINDOW_SEC", 300)
    val limitCredentialsMax: Int = envInt("AUTH_LIMIT_CREDENTIALS_MAX", 12)
    val limitRegisterWindowSec: Int = envInt("AUTH_LIMIT_REGISTER_WINDOW_SEC", 3600)
    val limitRegisterMax: Int = envInt("AUTH_LIMIT_REGISTER_MAX", 6)
    val lockoutFailThreshold: Int = envInt("AUTH_LOCKOUT_FAIL_THRESHOLD", 5)
    val lockoutBaseSec: Int = envInt("AUTH_LOCKOUT_BASE_SEC", 30)
    val lockoutMaxSec: Int = envInt("AUTH_LOCKOUT_MAX_SEC", 1800)
    val lockoutResetSec: Int = envInt("AUTH_LOCKOUT_RESET_SEC", 86400)
    val captchaTriggerFailures: Int = envInt("AUTH_CAPTCHA_TRIGGER_FAILURES", 3)
    val captchaSecret: String? = secret("AUTH_CAPTCHA_SECRET", "AUTH_CAPTCHA_SECRET_FILE")
    val alertIpFailureThreshold: Int = envInt("AUTH_ALERT_IP_FAILURE_THRESHOLD", 12)
    val alertLockoutBurstSec: Int = envInt("AUTH_ALERT_LOCKOUT_BURST_SEC", 900)
    val signalAnomalyWindowSec: Int = envInt("AUTH_SIGNAL_ANOMALY_WINDOW_SEC", 86400)
    val passwordProofChallengeTtlSec: Int = envInt("AUTH_PASSWORD_PROOF_CHALLENGE_TTL_SEC", 120)
    val passwordProofMaxActive: Int = envInt("AUTH_PASSWORD_PROOF_MAX_ACTIVE", 5000)

    fun env(key: String, default: String = ""): String =
        System.getenv(key)?.trim()?.ifEmpty { null } ?: default

    fun env(key: String): String? =
        System.getenv(key)?.trim()?.ifEmpty { null }

    fun envInt(key: String, default: Int): Int {
        val raw = System.getenv(key)?.trim() ?: return default
        return raw.toIntOrNull()?.takeIf { it > 0 } ?: default
    }

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
