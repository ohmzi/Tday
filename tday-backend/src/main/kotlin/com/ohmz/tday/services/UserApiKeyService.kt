package com.ohmz.tday.services

import arrow.core.Either
import arrow.core.right
import com.ohmz.tday.db.tables.UserApiKeys
import com.ohmz.tday.db.util.CuidGenerator
import com.ohmz.tday.domain.AppError
import com.ohmz.tday.security.PasswordService
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.update
import org.slf4j.LoggerFactory
import java.security.SecureRandom
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Base64

@Serializable
data class GeneratedApiKey(
    val key: String,
    val keyPreview: String,
    val createdAt: String,
)

@Serializable
data class CreateApiKeyResponse(
    val message: String,
    val apiKey: GeneratedApiKey,
)

@Serializable
data class ApiKeyStatus(
    val enabled: Boolean,
    val keyPreview: String? = null,
    val createdAt: String? = null,
)

interface UserApiKeyService {
    /** Generates a fresh key for the user, revoking any previous one. Returns the plaintext once. */
    suspend fun generate(userId: String): Either<AppError, GeneratedApiKey>

    /** Revokes (deletes) the user's API key, if any. */
    suspend fun revoke(userId: String): Either<AppError, Unit>

    /** Reports whether the user currently has an enabled key (without exposing the secret). */
    suspend fun status(userId: String): Either<AppError, ApiKeyStatus>

    /**
     * Validates a raw `tday_<id>_<secret>` token and returns the owning user id, or null when
     * the token is malformed, unknown, disabled, or the secret does not match. Bumps lastUsedAt.
     */
    suspend fun resolveUserId(rawKey: String): String?
}

class UserApiKeyServiceImpl(
    private val passwordService: PasswordService,
) : UserApiKeyService {

    private val logger = LoggerFactory.getLogger(UserApiKeyServiceImpl::class.java)
    private val random = SecureRandom()
    private val isoFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    override suspend fun generate(userId: String): Either<AppError, GeneratedApiKey> {
        val id = CuidGenerator.newCuid()
        val secret = randomSecret()
        val createdAt = LocalDateTime.now()
        val hash = passwordService.hashPassword(secret)
        val preview = secret.takeLast(PREVIEW_LENGTH)

        newSuspendedTransaction(Dispatchers.IO) {
            // One active key per user — drop any previous keys first.
            UserApiKeys.deleteWhere { UserApiKeys.userID eq userId }
            UserApiKeys.insert {
                it[UserApiKeys.id] = id
                it[UserApiKeys.userID] = userId
                it[UserApiKeys.keyHash] = hash
                it[UserApiKeys.keyPreview] = preview
                it[UserApiKeys.enabled] = true
                it[UserApiKeys.createdAt] = createdAt
            }
        }

        return GeneratedApiKey(
            key = "$KEY_PREFIX${id}_$secret",
            keyPreview = preview,
            createdAt = createdAt.format(isoFormatter),
        ).right()
    }

    override suspend fun revoke(userId: String): Either<AppError, Unit> {
        newSuspendedTransaction(Dispatchers.IO) {
            UserApiKeys.deleteWhere { UserApiKeys.userID eq userId }
        }
        return Unit.right()
    }

    override suspend fun status(userId: String): Either<AppError, ApiKeyStatus> {
        val status = newSuspendedTransaction(Dispatchers.IO) {
            UserApiKeys.selectAll()
                .where { (UserApiKeys.userID eq userId) and (UserApiKeys.enabled eq true) }
                .firstOrNull()
                ?.let { row ->
                    ApiKeyStatus(
                        enabled = true,
                        keyPreview = row[UserApiKeys.keyPreview],
                        createdAt = row[UserApiKeys.createdAt].format(isoFormatter),
                    )
                }
                ?: ApiKeyStatus(enabled = false)
        }
        return status.right()
    }

    override suspend fun resolveUserId(rawKey: String): String? {
        if (!rawKey.startsWith(KEY_PREFIX)) return null
        val rest = rawKey.removePrefix(KEY_PREFIX)
        val separator = rest.indexOf('_')
        if (separator <= 0 || separator >= rest.length - 1) return null
        val keyId = rest.substring(0, separator)
        val secret = rest.substring(separator + 1)

        return try {
            newSuspendedTransaction(Dispatchers.IO) {
                val row = UserApiKeys.selectAll()
                    .where { (UserApiKeys.id eq keyId) and (UserApiKeys.enabled eq true) }
                    .firstOrNull() ?: return@newSuspendedTransaction null

                val verification = passwordService.verifyPassword(secret, row[UserApiKeys.keyHash])
                if (!verification.valid) return@newSuspendedTransaction null

                val userId = row[UserApiKeys.userID]
                UserApiKeys.update({ UserApiKeys.id eq keyId }) {
                    it[UserApiKeys.lastUsedAt] = LocalDateTime.now()
                }
                userId
            }
        } catch (e: Exception) {
            logger.warn("Failed to resolve API key: {}", e.message)
            null
        }
    }

    private fun randomSecret(): String {
        val bytes = ByteArray(SECRET_BYTES).also { random.nextBytes(it) }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private companion object {
        const val KEY_PREFIX = "tday_"
        const val SECRET_BYTES = 32
        const val PREVIEW_LENGTH = 4
    }
}
