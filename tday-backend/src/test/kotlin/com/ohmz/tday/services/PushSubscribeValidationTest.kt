package com.ohmz.tday.services

import arrow.core.Either
import com.ohmz.tday.domain.AppError
import com.ohmz.tday.security.testAppConfig
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

/**
 * Exercises only the validation branches of subscribe() that short-circuit before any
 * DB access (the happy path needs Postgres, covered by the manual ntfy test).
 */
class PushSubscribeValidationTest {
    private val service = PushNotificationServiceImpl(testAppConfig())

    @Test
    fun `web push requires encryption keys`() = runBlocking {
        val result = service.subscribe(
            userId = "u1",
            endpoint = "https://push.example/abc",
            p256dh = "",
            auth = "",
            transport = TRANSPORT_WEBPUSH,
        )
        assertTrue(result is Either.Left && result.value is AppError.BadRequest)
    }

    @Test
    fun `a blank endpoint is rejected for any transport`() = runBlocking {
        val result = service.subscribe(
            userId = "u1",
            endpoint = "  ",
            p256dh = "",
            auth = "",
            transport = TRANSPORT_UNIFIEDPUSH,
        )
        assertTrue(result is Either.Left && result.value is AppError.BadRequest)
    }
}
