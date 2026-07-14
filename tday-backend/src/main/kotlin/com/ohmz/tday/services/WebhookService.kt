package com.ohmz.tday.services

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.ohmz.tday.db.tables.WebhookSubscriptions
import com.ohmz.tday.db.util.CuidGenerator
import com.ohmz.tday.domain.AppError
import com.ohmz.tday.security.FieldEncryption
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.security.SecureRandom
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Base64

/** All valid webhook event types (the DomainEvent @SerialName wire contract). */
val WEBHOOK_EVENT_TYPES: List<String> = listOf(
    "todo.changed",
    "floater.changed",
    "list.changed",
    "floaterList.changed",
    "list.members",
    "completed.changed",
)

@Serializable
data class WebhookInfo(
    val id: String,
    val url: String,
    val events: List<String>,
    val enabled: Boolean,
    val consecutiveFailures: Int,
    val lastStatus: Int? = null,
    val lastAttemptAt: String? = null,
    val createdAt: String,
)

@Serializable
data class CreatedWebhook(
    val id: String,
    val url: String,
    val events: List<String>,
    /** The signing secret, returned once at creation and never again. */
    val secret: String,
    val createdAt: String,
)

@Serializable
data class CreateWebhookResponse(
    val message: String,
    val webhook: CreatedWebhook,
)

interface WebhookService {
    suspend fun list(userId: String): Either<AppError, List<WebhookInfo>>
    suspend fun create(userId: String, url: String, events: List<String>): Either<AppError, CreatedWebhook>
    suspend fun delete(userId: String, id: String): Either<AppError, Unit>
}

class WebhookServiceImpl(
    private val fieldEncryption: FieldEncryption,
) : WebhookService {

    private val random = SecureRandom()
    private val isoFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    override suspend fun list(userId: String): Either<AppError, List<WebhookInfo>> {
        val hooks = newSuspendedTransaction(Dispatchers.IO) {
            WebhookSubscriptions.selectAll()
                .where { WebhookSubscriptions.userID eq userId }
                .orderBy(WebhookSubscriptions.createdAt to SortOrder.DESC)
                .map { row ->
                    WebhookInfo(
                        id = row[WebhookSubscriptions.id],
                        url = row[WebhookSubscriptions.url],
                        events = parseEvents(row[WebhookSubscriptions.eventFilter]),
                        enabled = row[WebhookSubscriptions.enabled],
                        consecutiveFailures = row[WebhookSubscriptions.consecutiveFailures],
                        lastStatus = row[WebhookSubscriptions.lastStatus],
                        lastAttemptAt = row[WebhookSubscriptions.lastAttemptAt]?.format(isoFormatter),
                        createdAt = row[WebhookSubscriptions.createdAt].format(isoFormatter),
                    )
                }
        }
        return hooks.right()
    }

    override suspend fun create(
        userId: String,
        url: String,
        events: List<String>,
    ): Either<AppError, CreatedWebhook> {
        val cleanUrl = url.trim()
        if (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://")) {
            return AppError.BadRequest("url must be an http(s) URL").left()
        }
        if (cleanUrl.length > MAX_URL_LENGTH) {
            return AppError.BadRequest("url is too long").left()
        }
        // Unknown event names are dropped; an empty selection means "all events".
        val cleanEvents = events.map { it.trim() }.filter { it in WEBHOOK_EVENT_TYPES }.distinct()

        val id = CuidGenerator.newCuid()
        val secret = randomSecret()
        val createdAt = LocalDateTime.now(ZoneOffset.UTC)

        newSuspendedTransaction(Dispatchers.IO) {
            WebhookSubscriptions.insert {
                it[WebhookSubscriptions.id] = id
                it[WebhookSubscriptions.userID] = userId
                it[WebhookSubscriptions.url] = cleanUrl
                it[WebhookSubscriptions.secret] = fieldEncryption.encryptIfSensitive("webhookSecret", secret) ?: secret
                it[WebhookSubscriptions.eventFilter] = cleanEvents.takeIf { list -> list.isNotEmpty() }?.joinToString(",")
                it[WebhookSubscriptions.enabled] = true
                it[WebhookSubscriptions.createdAt] = createdAt
            }
        }

        return CreatedWebhook(
            id = id,
            url = cleanUrl,
            events = cleanEvents,
            secret = secret,
            createdAt = createdAt.format(isoFormatter),
        ).right()
    }

    override suspend fun delete(userId: String, id: String): Either<AppError, Unit> {
        val deleted = newSuspendedTransaction(Dispatchers.IO) {
            WebhookSubscriptions.deleteWhere {
                (WebhookSubscriptions.id eq id) and (WebhookSubscriptions.userID eq userId)
            }
        }
        return if (deleted > 0) Unit.right() else AppError.NotFound("webhook not found").left()
    }

    private fun randomSecret(): String {
        val bytes = ByteArray(SECRET_BYTES).also { random.nextBytes(it) }
        return "whsec_${Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)}"
    }

    private companion object {
        const val SECRET_BYTES = 32
        const val MAX_URL_LENGTH = 2048
    }
}

/** Splits the stored comma-separated filter; empty/null means "all events". */
fun parseEvents(filter: String?): List<String> =
    filter?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
