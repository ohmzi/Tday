package com.ohmz.tday.compose.core.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
}
