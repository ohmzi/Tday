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
        val unavailableStatuses = listOf(408, 502, 503, 504, 520, 521, 522, 523, 524)

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
    fun `generic server errors are not treated as connectivity issues`() {
        assertFalse(
            isLikelyConnectivityIssue(
                ApiCallException(
                    statusCode = 500,
                    message = "Internal Server Error",
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
