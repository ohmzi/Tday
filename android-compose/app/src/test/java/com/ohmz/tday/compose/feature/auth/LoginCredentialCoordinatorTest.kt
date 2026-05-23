package com.ohmz.tday.compose.feature.auth

import android.content.Context
import com.ohmz.tday.compose.core.data.auth.SystemCredential
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoginCredentialCoordinatorTest {
    private val context = mockk<Context>(relaxed = true)

    @Test
    fun `requests saved credential only once`() = runTest {
        val coordinator = LoginCredentialCoordinator()
        val credential = SystemCredential(email = "test@example.com", password = "password")
        var requestCount = 0
        var loginCount = 0

        val firstResult = coordinator.requestSavedCredentialIfAvailable(
            context = context,
            currentEmail = "test@example.com",
            currentPassword = "",
            isCreatingAccount = false,
            isAuthLoading = false,
            requestSavedCredential = { _, _ ->
                requestCount += 1
                credential
            },
            login = {
                loginCount += 1
                true
            },
        )

        val secondResult = coordinator.requestSavedCredentialIfAvailable(
            context = context,
            currentEmail = "test@example.com",
            currentPassword = "",
            isCreatingAccount = false,
            isAuthLoading = false,
            requestSavedCredential = { _, _ ->
                requestCount += 1
                credential
            },
            login = {
                loginCount += 1
                true
            },
        )

        assertTrue(firstResult)
        assertFalse(secondResult)
        assertEquals(1, requestCount)
        assertEquals(1, loginCount)
    }

    @Test
    fun `does not request while creating account`() = runTest {
        val coordinator = LoginCredentialCoordinator()
        var requestCount = 0

        val result = coordinator.requestSavedCredentialIfAvailable(
            context = context,
            currentEmail = "",
            currentPassword = "",
            isCreatingAccount = true,
            isAuthLoading = false,
            requestSavedCredential = { _, _ ->
                requestCount += 1
                SystemCredential(email = "test@example.com", password = "password")
            },
            login = { true },
        )

        assertFalse(result)
        assertEquals(0, requestCount)
    }

    @Test
    fun `does not request while auth is loading`() = runTest {
        val coordinator = LoginCredentialCoordinator()
        var requestCount = 0

        val result = coordinator.requestSavedCredentialIfAvailable(
            context = context,
            currentEmail = "",
            currentPassword = "",
            isCreatingAccount = false,
            isAuthLoading = true,
            requestSavedCredential = { _, _ ->
                requestCount += 1
                SystemCredential(email = "test@example.com", password = "password")
            },
            login = { true },
        )

        assertFalse(result)
        assertEquals(0, requestCount)
    }

    @Test
    fun `does not request after password is typed`() = runTest {
        val coordinator = LoginCredentialCoordinator()
        var requestCount = 0

        val result = coordinator.requestSavedCredentialIfAvailable(
            context = context,
            currentEmail = "",
            currentPassword = "typed",
            isCreatingAccount = false,
            isAuthLoading = false,
            requestSavedCredential = { _, _ ->
                requestCount += 1
                SystemCredential(email = "test@example.com", password = "password")
            },
            login = { true },
        )

        assertFalse(result)
        assertEquals(0, requestCount)
    }

    @Test
    fun `passes current email as preferred credential id`() = runTest {
        val coordinator = LoginCredentialCoordinator()
        val credential = SystemCredential(email = "test@example.com", password = "password")
        var requestedEmail: String? = null

        val result = coordinator.requestSavedCredentialIfAvailable(
            context = context,
            currentEmail = "test@example.com",
            currentPassword = "",
            isCreatingAccount = false,
            isAuthLoading = false,
            requestSavedCredential = { _, preferredEmail ->
                requestedEmail = preferredEmail
                credential
            },
            login = { true },
        )

        assertTrue(result)
        assertEquals("test@example.com", requestedEmail)
    }
}
