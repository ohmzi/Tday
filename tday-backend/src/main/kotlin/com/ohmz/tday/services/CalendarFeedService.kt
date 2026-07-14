package com.ohmz.tday.services

import arrow.core.Either
import arrow.core.right
import com.ohmz.tday.db.tables.CalendarFeedTokens
import com.ohmz.tday.db.tables.TodoInstances
import com.ohmz.tday.db.tables.Todos
import com.ohmz.tday.db.util.CuidGenerator
import com.ohmz.tday.domain.AppError
import com.ohmz.tday.security.FieldEncryption
import com.ohmz.tday.security.toHex
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
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Base64

@Serializable
data class CalendarFeedToken(
    /** The full path token (`<id>_<secret>`) to embed in `/calendar/<token>.ics`. Shown once. */
    val token: String,
    val tokenPreview: String,
    val createdAt: String,
)

@Serializable
data class CalendarFeedStatus(
    val enabled: Boolean,
    val tokenPreview: String? = null,
    val createdAt: String? = null,
)

@Serializable
data class CreateCalendarFeedResponse(
    val message: String,
    val feed: CalendarFeedToken,
)

interface CalendarFeedService {
    /** Generates a fresh feed token, rotating any previous one. Returns the plaintext once. */
    suspend fun generate(userId: String): Either<AppError, CalendarFeedToken>

    /** Reports whether the user has an enabled feed token (without exposing the secret). */
    suspend fun status(userId: String): Either<AppError, CalendarFeedStatus>

    /** Revokes (deletes) the user's feed token, if any. */
    suspend fun revoke(userId: String): Either<AppError, Unit>

    /**
     * Renders the ICS document for the token owner, or null when the token is malformed,
     * unknown, disabled, or the secret does not match. Bumps lastUsedAt.
     */
    suspend fun renderIcs(rawToken: String): String?
}

class CalendarFeedServiceImpl(
    private val fieldEncryption: FieldEncryption,
) : CalendarFeedService {

    private val logger = LoggerFactory.getLogger(CalendarFeedServiceImpl::class.java)
    private val random = SecureRandom()
    private val isoFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    override suspend fun generate(userId: String): Either<AppError, CalendarFeedToken> {
        val id = CuidGenerator.newCuid()
        val secret = randomSecret()
        val createdAt = LocalDateTime.now(ZoneOffset.UTC)
        val hash = fastHash(secret)
        val preview = secret.takeLast(PREVIEW_LENGTH)

        newSuspendedTransaction(Dispatchers.IO) {
            // One active feed token per user — drop any previous token first.
            CalendarFeedTokens.deleteWhere { CalendarFeedTokens.userID eq userId }
            CalendarFeedTokens.insert {
                it[CalendarFeedTokens.id] = id
                it[CalendarFeedTokens.userID] = userId
                it[CalendarFeedTokens.tokenHash] = hash
                it[CalendarFeedTokens.tokenPreview] = preview
                it[CalendarFeedTokens.enabled] = true
                it[CalendarFeedTokens.createdAt] = createdAt
            }
        }

        return CalendarFeedToken(
            token = "${id}_$secret",
            tokenPreview = preview,
            createdAt = createdAt.format(isoFormatter),
        ).right()
    }

    override suspend fun status(userId: String): Either<AppError, CalendarFeedStatus> {
        val status = newSuspendedTransaction(Dispatchers.IO) {
            CalendarFeedTokens.selectAll()
                .where { (CalendarFeedTokens.userID eq userId) and (CalendarFeedTokens.enabled eq true) }
                .firstOrNull()
                ?.let { row ->
                    CalendarFeedStatus(
                        enabled = true,
                        tokenPreview = row[CalendarFeedTokens.tokenPreview],
                        createdAt = row[CalendarFeedTokens.createdAt].format(isoFormatter),
                    )
                }
                ?: CalendarFeedStatus(enabled = false)
        }
        return status.right()
    }

    override suspend fun revoke(userId: String): Either<AppError, Unit> {
        newSuspendedTransaction(Dispatchers.IO) {
            CalendarFeedTokens.deleteWhere { CalendarFeedTokens.userID eq userId }
        }
        return Unit.right()
    }

    override suspend fun renderIcs(rawToken: String): String? {
        val separator = rawToken.indexOf('_')
        if (separator <= 0 || separator >= rawToken.length - 1) return null
        val tokenId = rawToken.substring(0, separator)
        val secret = rawToken.substring(separator + 1)

        return try {
            newSuspendedTransaction(Dispatchers.IO) {
                val row = CalendarFeedTokens.selectAll()
                    .where { (CalendarFeedTokens.id eq tokenId) and (CalendarFeedTokens.enabled eq true) }
                    .firstOrNull() ?: return@newSuspendedTransaction null

                val storedHash = row[CalendarFeedTokens.tokenHash]
                val expected = storedHash.removePrefix(FAST_HASH_PREFIX)
                val valid = MessageDigest.isEqual(
                    sha256Hex(secret).toByteArray(Charsets.US_ASCII),
                    expected.toByteArray(Charsets.US_ASCII),
                )
                if (!valid) return@newSuspendedTransaction null

                val userId = row[CalendarFeedTokens.userID]
                val now = LocalDateTime.now(ZoneOffset.UTC)
                val lastUsed = row[CalendarFeedTokens.lastUsedAt]
                if (lastUsed == null || lastUsed.isBefore(now.minusSeconds(LAST_USED_THROTTLE_SECONDS))) {
                    CalendarFeedTokens.update({ CalendarFeedTokens.id eq tokenId }) {
                        it[CalendarFeedTokens.lastUsedAt] = now
                    }
                }

                buildIcs(userId, now)
            }
        } catch (e: Exception) {
            logger.warn("Failed to render calendar feed: {}", e.message)
            null
        }
    }

    /** Loads a user's dated todos + occurrence overrides and renders them as ICS. */
    private fun buildIcs(userId: String, stampUtc: LocalDateTime): String {
        val todoRows = Todos.selectAll().where { Todos.userID eq userId }.toList()
        val todoIds = todoRows.map { it[Todos.id] }.toSet()

        // Overrides (moved/renamed single occurrences) keyed by their original occurrence
        // date, so each becomes a RECURRENCE-ID VEVENT rather than an EXDATE.
        val overridesByTodo = mutableMapOf<String, MutableList<ResultRowOverride>>()
        if (todoIds.isNotEmpty()) {
            TodoInstances.selectAll()
                .where { TodoInstances.todoId inList todoIds }
                .forEach { row ->
                    val over = ResultRowOverride(
                        todoId = row[TodoInstances.todoId],
                        recurrenceDate = row[TodoInstances.instanceDate],
                        overriddenTitle = row[TodoInstances.overriddenTitle],
                        overriddenDescription = row[TodoInstances.overriddenDescription],
                        overriddenDue = row[TodoInstances.overriddenDue],
                    )
                    overridesByTodo.getOrPut(over.todoId) { mutableListOf() }.add(over)
                }
        }

        val events = buildList {
            todoRows.forEach { row ->
                val id = row[Todos.id]
                val title = row[Todos.title]
                val description = fieldEncryption.decryptIfEncrypted(row[Todos.description])
                val timeZone = row[Todos.timeZone].ifBlank { "UTC" }

                add(
                    IcsEvent(
                        uid = "$id@tday",
                        timeZone = timeZone,
                        start = row[Todos.due],
                        rrule = row[Todos.rrule]?.trim()?.ifEmpty { null },
                        exdates = row[Todos.exdates],
                        summary = title,
                        description = description,
                    ),
                )

                overridesByTodo[id]?.forEach { over ->
                    add(
                        IcsEvent(
                            uid = "$id@tday",
                            timeZone = timeZone,
                            start = over.overriddenDue ?: over.recurrenceDate,
                            rrule = null,
                            exdates = emptyList(),
                            summary = over.overriddenTitle ?: title,
                            description = fieldEncryption.decryptIfEncrypted(over.overriddenDescription)
                                ?: description,
                            recurrenceId = over.recurrenceDate,
                        ),
                    )
                }
            }
        }

        return CalendarIcs.document(events, stampUtc)
    }

    private data class ResultRowOverride(
        val todoId: String,
        val recurrenceDate: LocalDateTime,
        val overriddenTitle: String?,
        val overriddenDescription: String?,
        val overriddenDue: LocalDateTime?,
    )

    private fun randomSecret(): String {
        val bytes = ByteArray(SECRET_BYTES).also { random.nextBytes(it) }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun fastHash(secret: String): String = "$FAST_HASH_PREFIX${sha256Hex(secret)}"

    private fun sha256Hex(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)).toHex()

    private companion object {
        const val FAST_HASH_PREFIX = "s256$"
        const val SECRET_BYTES = 32
        const val PREVIEW_LENGTH = 4
        const val LAST_USED_THROTTLE_SECONDS = 300L
    }
}
