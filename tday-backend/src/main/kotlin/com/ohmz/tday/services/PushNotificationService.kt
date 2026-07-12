package com.ohmz.tday.services

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.ohmz.tday.config.AppConfig
import com.ohmz.tday.db.tables.PushSubscriptions
import com.ohmz.tday.db.util.CuidGenerator
import com.ohmz.tday.domain.AppError
import kotlinx.coroutines.Dispatchers
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

interface PushNotificationService {
    suspend fun subscribe(userId: String, endpoint: String, p256dh: String, auth: String): Either<AppError, Unit>
    suspend fun unsubscribe(userId: String, endpoint: String): Either<AppError, Unit>
    suspend fun sendToUser(userId: String, title: String, body: String, url: String? = null, todoId: String? = null): Either<AppError, Unit>
    fun isConfigured(): Boolean
    fun getVapidPublicKey(): String?
}

class PushNotificationServiceImpl(private val config: AppConfig) : PushNotificationService {
    private val logger = LoggerFactory.getLogger(PushNotificationServiceImpl::class.java)

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

    override fun getVapidPublicKey(): String? = config.vapidPublicKey

    override suspend fun subscribe(userId: String, endpoint: String, p256dh: String, auth: String): Either<AppError, Unit> {
        if (endpoint.isBlank() || p256dh.isBlank() || auth.isBlank()) {
            return AppError.BadRequest("endpoint, p256dh and auth are required").left()
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
        val svc = pushService ?: return AppError.BadRequest("Push notifications not configured").left()

        val subscriptions = newSuspendedTransaction(Dispatchers.IO) {
            PushSubscriptions.selectAll()
                .where { PushSubscriptions.userID eq userId }
                .map { row ->
                    Triple(
                        row[PushSubscriptions.id],
                        row[PushSubscriptions.endpoint],
                        Subscription.Keys(row[PushSubscriptions.p256dh], row[PushSubscriptions.auth]),
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

        val staleIds = mutableListOf<String>()

        // svc.send() is a synchronous, blocking Apache HttpClient call; running the
        // loop on the request coroutine would tie up a dispatcher/event-loop thread
        // per slow endpoint. Push it onto the IO dispatcher.
        withContext(Dispatchers.IO) {
            for ((subId, endpoint, keys) in subscriptions) {
                try {
                    val sub = Subscription(endpoint, keys)
                    val notification = Notification(sub, payload)
                    val response = svc.send(notification)
                    val statusCode = response.statusLine.statusCode
                    if (statusCode in listOf(404, 410)) {
                        staleIds.add(subId)
                        logger.debug("Push endpoint gone ({}), marking stale: {}", statusCode, endpoint.take(60))
                    }
                } catch (e: Exception) {
                    logger.warn("Failed to send push to {}: {}", endpoint.take(60), e.message)
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
}
