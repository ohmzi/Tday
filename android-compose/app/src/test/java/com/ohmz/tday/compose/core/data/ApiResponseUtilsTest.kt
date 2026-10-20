package com.ohmz.tday.compose.core.data

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class ApiResponseUtilsTest {

    @Test
    fun `server unavailable responses are treated as connectivity issues`() {
        // 500 = database down (backend up), 502/503/504 = backend container down behind a
        // live proxy — all are "server can't sync right now", treated the same as offline.
        val unavailableStatuses = listOf(408, 500, 501, 502, 503, 504, 520, 521, 522, 523, 524)

        unavailableStatuses.forEach { statusCode ->
            assertTrue(
                "Expected HTTP $statusCode to be treated as offline",
                isLikelyConnectivityIssue(
                    ApiCallException(
                        statusCode = statusCode,
                        message = "Server unavailable",
                    ),
                ),
            )
        }
    }

    @Test
    fun `client validation errors are not treated as connectivity issues`() {
        // 4xx is a real client-side problem (bad request / validation), not an outage.
        assertFalse(
            isLikelyConnectivityIssue(
                ApiCallException(
                    statusCode = 422,
                    message = "Unprocessable",
                ),
            ),
        )
    }

    @Test
    fun `unauthorized responses are treated as session auth issues not connectivity`() {
        val error = ApiCallException(
            statusCode = 401,
            message = "Unauthorized",
        )

        assertTrue(isSessionAuthenticationIssue(error))
        assertFalse(isLikelyConnectivityIssue(error))
    }

    /**
     * A 429 has to answer two different questions differently, and this is the pair
     * that pins it. The app must RETRY anything it queued — the server will run the
     * request shortly — but it must not claim the user is offline, because the server
     * just answered and the problem clears itself. Collapsing the two would tell
     * someone whose own burst of edits tripped the limiter that they had lost their
     * connection.
     */
    @Test
    fun `rate limited responses are retryable but are not an offline failure`() {
        val error = ApiCallException(
            statusCode = 429,
            message = "Too many requests",
            reason = "api_rate_limit",
            retryAfterSeconds = 12,
        )

        assertEquals(ConnectionFailureKind.RATE_LIMITED, classifyConnectionFailure(error))
        assertTrue(
            "a queued mutation must survive a rate limit rather than be dropped as final",
            isLikelyConnectivityIssue(error),
        )
        assertFalse(
            "the device is demonstrably online — the server answered",
            isAppOfflineFailure(error),
        )
    }

    @Test
    fun `genuine outages are still offline failures`() {
        // The narrower predicate must not have narrowed the offline state itself.
        assertTrue(isAppOfflineFailure(ApiCallException(statusCode = 503, message = "Unavailable")))
        assertTrue(isAppOfflineFailure(ApiCallException(statusCode = 500, message = "Server error")))
        assertFalse(isAppOfflineFailure(ApiCallException(statusCode = 422, message = "Unprocessable")))
    }

    @Test
    fun `api error details preserve field and retry metadata`() {
        val response = Response.error<Unit>(
            429,
            """{"message":"Too many requests","reason":"api_rate_limit","field":"priority","retryAfterSeconds":12}"""
                .toResponseBody("application/json".toMediaType()),
        )

        val details = extractApiErrorDetails(response, fallback = "Request failed")

        assertEquals("Too many requests", details.message)
        assertEquals("api_rate_limit", details.reason)
        assertEquals("priority", details.field)
        assertEquals(12, details.retryAfterSeconds)
    }
}
