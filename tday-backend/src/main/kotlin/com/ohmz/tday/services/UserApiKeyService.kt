package com.ohmz.tday.services

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.ohmz.tday.db.tables.UserApiKeys
import com.ohmz.tday.db.util.CuidGenerator
import com.ohmz.tday.domain.AppError
import com.ohmz.tday.security.PasswordService
import com.ohmz.tday.security.toHex
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.update
import org.slf4j.LoggerFactory
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Base64

/** Access scope for a personal API key. */
enum class ApiKeyScope {
    /** Read-only: the key may only issue safe (GET/HEAD) requests. */
    READ,

    /** Unrestricted account access. Default for keys created before V15. */
    FULL;

    companion object {
        fun fromStorage(value: String?): ApiKeyScope =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: FULL
    }
}

@Serializable
data class GeneratedApiKey(
    val id: String,
    val key: String,
    val keyPreview: String,
    val label: String? = null,
    val scope: String,
    val createdAt: String,
    val expiresAt: String? = null,
)

@Serializable
data class CreateApiKeyResponse(
    val message: String,
    val apiKey: GeneratedApiKey,
)

/** Metadata for a single key, safe to list (never exposes the secret). */
@Serializable
data class ApiKeyInfo(
    val id: String,
    val label: String? = null,
    val scope: String,
    val keyPreview: String,
    val createdAt: String,
    val lastUsedAt: String? = null,
    val expiresAt: String? = null,
    val expired: Boolean = false,
)

/** Resolved principal for an authenticated API-key request. */
data class ResolvedApiKey(
    val userId: String,
    val scope: ApiKeyScope,
)

interface UserApiKeyService {
    /**
     * Generates a fresh key for the user with the given label/scope/optional expiry.
     * Additive — existing keys are left intact. Returns the plaintext once.
     */
    suspend fun generate(
        userId: String,
        label: String? = null,
        scope: ApiKeyScope = ApiKeyScope.FULL,
        expiresInDays: Long? = null,
    ): Either<AppError, GeneratedApiKey>

    /** Lists metadata for every key the user owns, newest first. */
    suspend fun list(userId: String): Either<AppError, List<ApiKeyInfo>>

    /** Revokes (deletes) a single key owned by the user. */
    suspend fun revokeKey(userId: String, keyId: String): Either<AppError, Unit>

    /** Revokes (deletes) ALL of the user's API keys. Used on credential rotation. */
    suspend fun revoke(userId: String): Either<AppError, Unit>

    /**
     * Validates a raw `tday_<id>_<secret>` token and returns the owning user id + scope,
     * or null when the token is malformed, unknown, disabled, expired, or the secret does
     * not match. Bumps lastUsedAt.
     */
    suspend fun resolveKey(rawKey: String): ResolvedApiKey?
}

class UserApiKeyServiceImpl(
    private val passwordService: PasswordService,
) : UserApiKeyService {

    private val logger = LoggerFactory.getLogger(UserApiKeyServiceImpl::class.java)
    private val random = SecureRandom()
    private val isoFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    override suspend fun generate(
        userId: String,
        label: String?,
        scope: ApiKeyScope,
        expiresInDays: Long?,
    ): Either<AppError, GeneratedApiKey> {
        if (expiresInDays != null && expiresInDays <= 0) {
            return AppError.BadRequest("expiresInDays must be positive").left()
        }
        val cleanLabel = label?.trim()?.take(MAX_LABEL_LENGTH)?.ifEmpty { null }
        val id = CuidGenerator.newCuid()
        val secret = randomSecret()
        val createdAt = LocalDateTime.now(ZoneOffset.UTC)
        val expiresAt = expiresInDays?.let { createdAt.plusDays(it) }
        // The secret is 256 bits of CSPRNG output, so it is not brute-forceable and
        // needs no key-stretching. A single SHA-256 keeps verification at ~microseconds
        // (vs. hundreds of ms for PBKDF2) — important because this runs on EVERY
        // API-key request, where slow hashing is a latency + DoS amplifier.
        val hash = fastKeyHash(secret)
        val preview = secret.takeLast(PREVIEW_LENGTH)

        newSuspendedTransaction(Dispatchers.IO) {
            UserApiKeys.insert {
                it[UserApiKeys.id] = id
                it[UserApiKeys.userID] = userId
                it[UserApiKeys.keyHash] = hash
                it[UserApiKeys.keyPreview] = preview
                it[UserApiKeys.enabled] = true
                it[UserApiKeys.label] = cleanLabel
                it[UserApiKeys.scope] = scope.name
                it[UserApiKeys.expiresAt] = expiresAt
                it[UserApiKeys.createdAt] = createdAt
            }
        }

        return GeneratedApiKey(
            id = id,
            key = "$KEY_PREFIX${id}_$secret",
            keyPreview = preview,
            label = cleanLabel,
            scope = scope.name,
            createdAt = createdAt.format(isoFormatter),
            expiresAt = expiresAt?.format(isoFormatter),
        ).right()
    }

    override suspend fun list(userId: String): Either<AppError, List<ApiKeyInfo>> {
        val now = LocalDateTime.now(ZoneOffset.UTC)
        val keys = newSuspendedTransaction(Dispatchers.IO) {
            UserApiKeys.selectAll()
                .where { UserApiKeys.userID eq userId }
                .orderBy(UserApiKeys.createdAt to SortOrder.DESC)
                .map { row ->
                    val expiresAt = row[UserApiKeys.expiresAt]
                    ApiKeyInfo(
                        id = row[UserApiKeys.id],
                        label = row[UserApiKeys.label],
                        scope = ApiKeyScope.fromStorage(row[UserApiKeys.scope]).name,
                        keyPreview = row[UserApiKeys.keyPreview],
                        createdAt = row[UserApiKeys.createdAt].format(isoFormatter),
                        lastUsedAt = row[UserApiKeys.lastUsedAt]?.format(isoFormatter),
                        expiresAt = expiresAt?.format(isoFormatter),
                        expired = expiresAt != null && !expiresAt.isAfter(now),
                    )
                }
        }
        return keys.right()
    }

    override suspend fun revokeKey(userId: String, keyId: String): Either<AppError, Unit> {
        val deleted = newSuspendedTransaction(Dispatchers.IO) {
            UserApiKeys.deleteWhere {
                (UserApiKeys.id eq keyId) and (UserApiKeys.userID eq userId)
            }
        }
        return if (deleted > 0) Unit.right() else AppError.NotFound("api key not found").left()
    }

    override suspend fun revoke(userId: String): Either<AppError, Unit> {
        newSuspendedTransaction(Dispatchers.IO) {
            UserApiKeys.deleteWhere { UserApiKeys.userID eq userId }
        }
        return Unit.right()
    }

    override suspend fun resolveKey(rawKey: String): ResolvedApiKey? {
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

                val now = LocalDateTime.now(ZoneOffset.UTC)
                val expiresAt = row[UserApiKeys.expiresAt]
                if (expiresAt != null && !expiresAt.isAfter(now)) return@newSuspendedTransaction null

                val storedHash = row[UserApiKeys.keyHash]
                val valid = if (storedHash.startsWith(FAST_HASH_PREFIX)) {
                    val expected = storedHash.removePrefix(FAST_HASH_PREFIX)
                    MessageDigest.isEqual(
                        sha256Hex(secret).toByteArray(Charsets.US_ASCII),
                        expected.toByteArray(Charsets.US_ASCII),
                    )
                } else {
                    // Legacy PBKDF2-hashed key — still accepted; rotates to the fast
                    // format on next regenerate().
                    passwordService.verifyPassword(secret, storedHash).valid
                }
                if (!valid) return@newSuspendedTransaction null

                val userId = row[UserApiKeys.userID]
                val scope = ApiKeyScope.fromStorage(row[UserApiKeys.scope])
                // Throttle the lastUsedAt write: at most once per window, so a busy
                // integration doesn't issue a DB write on every single request.
                val lastUsed = row[UserApiKeys.lastUsedAt]
                if (lastUsed == null || lastUsed.isBefore(now.minusSeconds(LAST_USED_THROTTLE_SECONDS))) {
                    UserApiKeys.update({ UserApiKeys.id eq keyId }) {
                        it[UserApiKeys.lastUsedAt] = now
                    }
                }
                ResolvedApiKey(userId = userId, scope = scope)
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

    private fun fastKeyHash(secret: String): String = "$FAST_HASH_PREFIX${sha256Hex(secret)}"

    private fun sha256Hex(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)).toHex()

    private companion object {
        const val KEY_PREFIX = "tday_"
        const val FAST_HASH_PREFIX = "s256$"
        const val SECRET_BYTES = 32
        const val PREVIEW_LENGTH = 4
        const val MAX_LABEL_LENGTH = 60
        const val LAST_USED_THROTTLE_SECONDS = 300L
    }
}
