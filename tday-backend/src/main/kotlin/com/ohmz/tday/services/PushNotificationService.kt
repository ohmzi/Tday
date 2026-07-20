package com.ohmz.tday.services

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.ohmz.tday.config.AppConfig
import com.ohmz.tday.db.tables.PushSubscriptions
import com.ohmz.tday.db.util.CuidGenerator
import com.ohmz.tday.domain.AppError
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import nl.martijndwars.webpush.Notification
import nl.martijndwars.webpush.PushService
import nl.martijndwars.webpush.Subscription
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.slf4j.LoggerFactory
import java.security.Security
import java.time.LocalDateTime
import java.time.ZoneOffset

/** Push delivery transports. */
const val TRANSPORT_WEBPUSH = "webpush"
const val TRANSPORT_UNIFIEDPUSH = "unifiedpush"

interface PushNotificationService {
    suspend fun subscribe(
        userId: String,
        endpoint: String,
        p256dh: String,
        auth: String,
        transport: String = TRANSPORT_WEBPUSH,
    ): Either<AppError, Unit>
    suspend fun unsubscribe(userId: String, endpoint: String): Either<AppError, Unit>
    suspend fun sendToUser(userId: String, title: String, body: String, url: String? = null, todoId: String? = null): Either<AppError, Unit>

    /**
     * Fire-and-forget SILENT "data changed" ping so a backgrounded device can refresh its
     * home-screen widgets even when the app process is dead. Delivered ONLY to UnifiedPush
     * endpoints (a plain POST the device turns into a widget sync — no user-visible notification);
     * Web Push endpoints are skipped so browsers don't surface a junk notification. Returns
     * immediately; delivery runs on a detached scope so a mutation's response never waits on it.
     */
    fun notifyDataChanged(userIds: Collection<String>)

    fun isConfigured(): Boolean
    fun getVapidPublicKey(): String?
}

class PushNotificationServiceImpl(private val config: AppConfig) : PushNotificationService {
    private val logger = LoggerFactory.getLogger(PushNotificationServiceImpl::class.java)

    // Used only for UnifiedPush (plain POST to the distributor endpoint); Web Push has
    // its own blocking Apache client inside the webpush library.
    private val httpClient = HttpClient(CIO) { engine { requestTimeout = UNIFIEDPUSH_TIMEOUT_MS } }

    private val pushService: PushService? by lazy {
        val publicKey = config.vapidPublicKey
        val privateKey = config.vapidPrivateKey
        if (publicKey.isNullOrBlank() || privateKey.isNullOrBlank()) {
            logger.info("VAPID keys not configured — push notifications disabled")
            return@lazy null
        }
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(BouncyCastleProvider())
        }
        try {
            PushService(publicKey, privateKey, "mailto:noreply@tday.app")
        } catch (e: Exception) {
            logger.error("Failed to initialise PushService: {}", e.message, e)
            null
        }
    }

    override fun isConfigured(): Boolean = !config.vapidPublicKey.isNullOrBlank() && !config.vapidPrivateKey.isNullOrBlank()

    private companion object {
        const val UNIFIEDPUSH_TIMEOUT_MS = 10_000L
        const val DATA_CHANGED_TYPE = "data-changed"
    }

    override fun getVapidPublicKey(): String? = config.vapidPublicKey

    override suspend fun subscribe(
        userId: String,
        endpoint: String,
        p256dh: String,
        auth: String,
        transport: String,
    ): Either<AppError, Unit> {
        val normalizedTransport = if (transport == TRANSPORT_UNIFIEDPUSH) TRANSPORT_UNIFIEDPUSH else TRANSPORT_WEBPUSH
        if (endpoint.isBlank()) {
            return AppError.BadRequest("endpoint is required").left()
        }
        // Web Push needs the encryption keys; UnifiedPush is endpoint-only.
        if (normalizedTransport == TRANSPORT_WEBPUSH && (p256dh.isBlank() || auth.isBlank())) {
            return AppError.BadRequest("p256dh and auth are required for web push").left()
        }
        newSuspendedTransaction(Dispatchers.IO) {
            // Upsert: delete existing for same user+endpoint, then insert
            PushSubscriptions.deleteWhere {
                (PushSubscriptions.userID eq userId) and (PushSubscriptions.endpoint eq endpoint)
            }
            PushSubscriptions.insert {
                it[PushSubscriptions.id] = CuidGenerator.newCuid()
                it[PushSubscriptions.userID] = userId
                it[PushSubscriptions.endpoint] = endpoint
                it[PushSubscriptions.p256dh] = p256dh
                it[PushSubscriptions.auth] = auth
                it[PushSubscriptions.transport] = normalizedTransport
                it[PushSubscriptions.createdAt] = LocalDateTime.now(ZoneOffset.UTC)
            }
        }
        return Unit.right()
    }

    override suspend fun unsubscribe(userId: String, endpoint: String): Either<AppError, Unit> {
        newSuspendedTransaction(Dispatchers.IO) {
            PushSubscriptions.deleteWhere {
                (PushSubscriptions.userID eq userId) and (PushSubscriptions.endpoint eq endpoint)
            }
        }
        return Unit.right()
    }

    override suspend fun sendToUser(userId: String, title: String, body: String, url: String?, todoId: String?): Either<AppError, Unit> {
        val subscriptions = newSuspendedTransaction(Dispatchers.IO) {
            PushSubscriptions.selectAll()
                .where { PushSubscriptions.userID eq userId }
                .map { row ->
                    PushTarget(
                        id = row[PushSubscriptions.id],
                        endpoint = row[PushSubscriptions.endpoint],
                        p256dh = row[PushSubscriptions.p256dh],
                        auth = row[PushSubscriptions.auth],
                        transport = row[PushSubscriptions.transport],
                    )
                }
        }

        if (subscriptions.isEmpty()) return Unit.right()

        val payload = buildJsonObject {
            put("title", title)
            put("body", body)
            if (url != null) put("url", url)
            if (todoId != null) put("todoId", todoId)
        }.toString()

        val svc = pushService
        val staleIds = mutableListOf<String>()

        // Network sends are blocking/suspending; keep them off the request coroutine.
        withContext(Dispatchers.IO) {
            for (target in subscriptions) {
                try {
                    val statusCode = if (target.transport == TRANSPORT_UNIFIEDPUSH) {
                        // UnifiedPush: plain POST of the payload to the distributor endpoint.
                        httpClient.post(target.endpoint) {
                            contentType(ContentType.Application.Json)
                            setBody(payload)
                        }.status.value
                    } else {
                        // Web Push: VAPID-signed, encrypted. Requires configured keys.
                        if (svc == null) {
                            logger.debug("Skipping web-push send — VAPID not configured")
                            continue
                        }
                        val sub = Subscription(target.endpoint, Subscription.Keys(target.p256dh, target.auth))
                        svc.send(Notification(sub, payload)).statusLine.statusCode
                    }
                    if (statusCode in listOf(404, 410)) {
                        staleIds.add(target.id)
                        logger.debug("Push endpoint gone ({}), marking stale: {}", statusCode, target.endpoint.take(60))
                    }
                } catch (e: Exception) {
                    logger.warn("Failed to send push to {}: {}", target.endpoint.take(60), e.message)
                }
            }
        }

        if (staleIds.isNotEmpty()) {
            newSuspendedTransaction(Dispatchers.IO) {
                for (id in staleIds) {
                    PushSubscriptions.deleteWhere { PushSubscriptions.id eq id }
                }
            }
        }

        return Unit.right()
    }

    // Detached scope for fire-and-forget silent pushes so a mutation's response is never blocked
    // on the distributor POST (mirrors WebhookDispatchService).
    private val dataChangedScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun notifyDataChanged(userIds: Collection<String>) {
        val users = userIds.toSet()
        if (users.isEmpty()) return
        dataChangedScope.launch {
            runCatching { sendDataChanged(users) }
                .onFailure { logger.warn("data-changed push failed: {}", it.message) }
        }
    }

    private suspend fun sendDataChanged(userIds: Set<String>) {
        val userIdList = userIds.toList()
        // UnifiedPush only: a plain POST the device converts into a widget sync. Web Push endpoints
        // are excluded so browsers never surface a silent-notification placeholder.
        val targets = newSuspendedTransaction(Dispatchers.IO) {
            PushSubscriptions.selectAll()
                .where {
                    (PushSubscriptions.userID inList userIdList) and
                        (PushSubscriptions.transport eq TRANSPORT_UNIFIEDPUSH)
                }
                .map { row ->
                    PushTarget(
                        id = row[PushSubscriptions.id],
                        endpoint = row[PushSubscriptions.endpoint],
                        p256dh = row[PushSubscriptions.p256dh],
                        auth = row[PushSubscriptions.auth],
                        transport = row[PushSubscriptions.transport],
                    )
                }
        }

        if (targets.isEmpty()) return

        val payload = buildJsonObject { put("type", DATA_CHANGED_TYPE) }.toString()
        val staleIds = mutableListOf<String>()

        withContext(Dispatchers.IO) {
            for (target in targets) {
                try {
                    val statusCode = httpClient.post(target.endpoint) {
                        contentType(ContentType.Application.Json)
                        setBody(payload)
                    }.status.value
                    if (statusCode in listOf(404, 410)) {
                        staleIds.add(target.id)
                        logger.debug("Push endpoint gone ({}), marking stale: {}", statusCode, target.endpoint.take(60))
                    }
                } catch (e: Exception) {
                    logger.warn("Failed to send data-changed push to {}: {}", target.endpoint.take(60), e.message)
                }
            }
        }

        if (staleIds.isNotEmpty()) {
            newSuspendedTransaction(Dispatchers.IO) {
                for (id in staleIds) {
                    PushSubscriptions.deleteWhere { PushSubscriptions.id eq id }
                }
            }
        }
    }

    private data class PushTarget(
        val id: String,
        val endpoint: String,
        val p256dh: String,
        val auth: String,
        val transport: String,
    )
}
