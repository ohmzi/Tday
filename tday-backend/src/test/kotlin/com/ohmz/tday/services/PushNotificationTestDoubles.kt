package com.ohmz.tday.services

import arrow.core.Either
import arrow.core.right
import com.ohmz.tday.domain.AppError

/** Does nothing, successfully — for tests that need a [PushNotificationService] wired in but
 * assert nothing about push delivery. */
class NoOpPushNotificationService : PushNotificationService {
    override suspend fun subscribe(
        userId: String,
        endpoint: String,
        p256dh: String,
        auth: String,
        transport: String,
    ): Either<AppError, Unit> = Unit.right()

    override suspend fun unsubscribe(userId: String, endpoint: String): Either<AppError, Unit> = Unit.right()

    override suspend fun sendToUser(
        userId: String,
        title: String,
        body: String,
        url: String?,
        todoId: String?,
        listId: String?,
        listType: String?,
        listName: String?,
    ): Either<AppError, Unit> = Unit.right()

    override fun notifyDataChanged(userIds: Collection<String>) = Unit

    override fun isConfigured(): Boolean = false

    override fun getVapidPublicKey(): String? = null
}

/** One captured [PushNotificationService.sendToUser] call. */
data class RecordedPush(
    val userId: String,
    val title: String,
    val body: String,
    val url: String?,
    val todoId: String?,
    val listId: String?,
    val listType: String?,
    val listName: String?,
)

/** Records every [sendToUser] call instead of delivering anything, so a test can assert exactly
 * who was pushed, and with what. [result] controls what each call reports back to the caller. */
class RecordingPushNotificationService(
    private val result: Either<AppError, Unit> = Unit.right(),
) : PushNotificationService {
    val sent = mutableListOf<RecordedPush>()

    override suspend fun subscribe(
        userId: String,
        endpoint: String,
        p256dh: String,
        auth: String,
        transport: String,
    ): Either<AppError, Unit> = Unit.right()

    override suspend fun unsubscribe(userId: String, endpoint: String): Either<AppError, Unit> = Unit.right()

    override suspend fun sendToUser(
        userId: String,
        title: String,
        body: String,
        url: String?,
        todoId: String?,
        listId: String?,
        listType: String?,
        listName: String?,
    ): Either<AppError, Unit> {
        sent += RecordedPush(userId, title, body, url, todoId, listId, listType, listName)
        return result
    }

    override fun notifyDataChanged(userIds: Collection<String>) = Unit

    override fun isConfigured(): Boolean = true

    override fun getVapidPublicKey(): String? = null
}
