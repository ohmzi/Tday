package com.ohmz.tday.services

import com.ohmz.tday.db.tables.CronLogs
import com.ohmz.tday.db.tables.WebhookSubscriptions
import com.ohmz.tday.db.util.CuidGenerator
import com.ohmz.tday.domain.DomainEvent
import com.ohmz.tday.security.FieldEncryption
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.update
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Serializable
private data class WebhookPayload(
    val event: String,
    val userId: String,
    val listId: String? = null,
    val timestamp: String,
)

/**
 * Delivers an ID-only "something changed" ping to a user's registered webhooks,
 * signed with each subscription's HMAC-SHA256 secret. Fire-and-forget: [dispatch]
 * returns immediately and delivery runs on a detached scope so a mutation's response
 * is never blocked on a slow endpoint. Repeated consecutive failures auto-disable a
 * subscription; outcomes land in [CronLogs].
 */
interface WebhookDispatchService {
    fun dispatch(recipientUserIds: Collection<String>, event: DomainEvent)
}

class WebhookDispatchServiceImpl(
    private val fieldEncryption: FieldEncryption,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val client: HttpClient = HttpClient(CIO) { engine { requestTimeout = REQUEST_TIMEOUT_MS } },
) : WebhookDispatchService {

    private val logger = LoggerFactory.getLogger(WebhookDispatchServiceImpl::class.java)
    private val json = Json { encodeDefaults = true }
    private val isoFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    override fun dispatch(recipientUserIds: Collection<String>, event: DomainEvent) {
        val eventType = event.wireType()
        val recipients = recipientUserIds.toSet()
        if (recipients.isEmpty()) return

        val listId = event.listId()
        val timestamp = LocalDateTime.now(ZoneOffset.UTC).format(isoFormatter)

        scope.launch {
            recipients.forEach { userId ->
                val targets = loadMatchingSubscriptions(userId, eventType)
                if (targets.isEmpty()) return@forEach
                val payload = json.encodeToString(
                    WebhookPayload.serializer(),
                    WebhookPayload(event = eventType, userId = userId, listId = listId, timestamp = timestamp),
                )
                targets.forEach { target -> deliver(target, payload) }
            }
        }
    }

    private suspend fun loadMatchingSubscriptions(userId: String, eventType: String): List<Target> =
        newSuspendedTransaction(Dispatchers.IO) {
            WebhookSubscriptions.selectAll()
                .where { (WebhookSubscriptions.userID eq userId) and (WebhookSubscriptions.enabled eq true) }
                .mapNotNull { row ->
                    val filter = parseEvents(row[WebhookSubscriptions.eventFilter])
                    if (filter.isNotEmpty() && eventType !in filter) return@mapNotNull null
                    Target(
                        id = row[WebhookSubscriptions.id],
                        url = row[WebhookSubscriptions.url],
                        secret = fieldEncryption.decryptIfEncrypted(row[WebhookSubscriptions.secret]).orEmpty(),
                    )
                }
        }

    private suspend fun deliver(target: Target, payload: String) {
        val signature = "sha256=${webhookSignature(target.secret, payload)}"
        val result = deliverWithRetry(MAX_ATTEMPTS, BASE_BACKOFF_MS) { attempt ->
            try {
                val response: HttpResponse = client.post(target.url) {
                    contentType(ContentType.Application.Json)
                    header("X-Tday-Signature", signature)
                    header("X-Tday-Event", "webhook")
                    setBody(payload)
                }
                response.status.value
            } catch (e: Exception) {
                logger.debug("Webhook {} attempt {} failed: {}", target.id, attempt, e.message)
                null
            }
        }
        recordOutcome(target, result.delivered, result.lastStatus)
    }

    private suspend fun recordOutcome(target: Target, delivered: Boolean, status: Int?) {
        val now = LocalDateTime.now(ZoneOffset.UTC)
        val disabled = newSuspendedTransaction(Dispatchers.IO) {
            val row = WebhookSubscriptions.selectAll()
                .where { WebhookSubscriptions.id eq target.id }
                .firstOrNull() ?: return@newSuspendedTransaction false
            val failures = if (delivered) 0 else row[WebhookSubscriptions.consecutiveFailures] + 1
            val shouldDisable = !delivered && failures >= MAX_CONSECUTIVE_FAILURES
            WebhookSubscriptions.update({ WebhookSubscriptions.id eq target.id }) {
                it[WebhookSubscriptions.consecutiveFailures] = failures
                it[WebhookSubscriptions.lastStatus] = status
                it[WebhookSubscriptions.lastAttemptAt] = now
                if (shouldDisable) it[WebhookSubscriptions.enabled] = false
            }
            shouldDisable
        }

        if (!delivered) {
            writeCronLog(
                now,
                success = false,
                "delivery to ${target.url} failed (status=$status)${if (disabled) " — auto-disabled" else ""}",
            )
        }
    }

    private suspend fun writeCronLog(runAt: LocalDateTime, success: Boolean, message: String) {
        newSuspendedTransaction(Dispatchers.IO) {
            CronLogs.insert {
                it[CronLogs.id] = CuidGenerator.newCuid()
                it[CronLogs.runAt] = runAt
                it[CronLogs.success] = success
                it[CronLogs.log] = "$JOB_LABEL $message"
            }
        }
    }

    private data class Target(val id: String, val url: String, val secret: String)

    private companion object {
        const val REQUEST_TIMEOUT_MS = 10_000L
        const val MAX_ATTEMPTS = 3
        const val BASE_BACKOFF_MS = 1_000L
        const val MAX_CONSECUTIVE_FAILURES = 15
        const val JOB_LABEL = "webhook-dispatch"
    }
}

/** The DomainEvent's `type` discriminator (its @SerialName), used for filtering. */
fun DomainEvent.wireType(): String = when (this) {
    is DomainEvent.TodoChanged -> "todo.changed"
    is DomainEvent.FloaterChanged -> "floater.changed"
    is DomainEvent.ListChanged -> "list.changed"
    is DomainEvent.FloaterListChanged -> "floaterList.changed"
    is DomainEvent.MembersChanged -> "list.members"
    is DomainEvent.CompletedChanged -> "completed.changed"
}

/** The optional list id carried by every DomainEvent variant. */
fun DomainEvent.listId(): String? = when (this) {
    is DomainEvent.TodoChanged -> listId
    is DomainEvent.FloaterChanged -> listId
    is DomainEvent.ListChanged -> listId
    is DomainEvent.FloaterListChanged -> listId
    is DomainEvent.MembersChanged -> listId
    is DomainEvent.CompletedChanged -> listId
}
