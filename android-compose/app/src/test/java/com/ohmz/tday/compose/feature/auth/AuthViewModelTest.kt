package com.ohmz.tday.compose.feature.auth

import android.content.Context
import com.ohmz.tday.compose.core.data.auth.AuthRepository
import com.ohmz.tday.compose.core.data.auth.LoginCredentialSource
import com.ohmz.tday.compose.core.data.auth.SystemCredential
import com.ohmz.tday.compose.core.data.auth.SystemCredentialSaveResult
import com.ohmz.tday.compose.core.data.auth.SystemCredentialServicing
import com.ohmz.tday.compose.core.model.AuthResult
import com.ohmz.tday.compose.core.model.RegisterOutcome
import com.ohmz.tday.compose.core.ui.SnackbarManager
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val credentialContext = mockk<Context>(relaxed = true)
    private val authRepository = mockk<AuthRepository>()
    private val credentialService = FakeSystemCredentialService()

    @Before
    fun setUp() {
        every { authRepository.getLastEmail() } returns null
        credentialService.reset()
    }

    @Test
    fun `manual login saves credential after success`() = runTest {
        coEvery {
            authRepository.login(email = "user@example.com", password = "newPassword")
        } returns AuthResult.Success
        val viewModel = makeViewModel()
        var didSucceed = false

        viewModel.login(
            email = " User@Example.com ",
            password = "newPassword",
            credentialContext = credentialContext,
            source = LoginCredentialSource.MANUAL,
        ) {
            didSucceed = true
        }
        advanceUntilIdle()

        assertTrue(didSucceed)
        assertEquals(
            listOf(SystemCredential(email = "user@example.com", password = "newPassword")),
            credentialService.savedCredentials,
        )
    }

    @Test
    fun `password manager login does not save credential again`() = runTest {
        coEvery {
            authRepository.login(email = "user@example.com", password = "storedPassword")
        } returns AuthResult.Success
        val viewModel = makeViewModel()

        viewModel.login(
            email = "user@example.com",
            password = "storedPassword",
            credentialContext = credentialContext,
            source = LoginCredentialSource.SYSTEM_PASSWORD_MANAGER,
        ) {}
        advanceUntilIdle()

        assertTrue(credentialService.savedCredentials.isEmpty())
    }

    @Test
    fun `failed and pending logins do not save credentials`() = runTest {
        coEvery {
            authRepository.login(email = "failed@example.com", password = "badPassword")
        } returns AuthResult.Error("Invalid credentials")
        coEvery {
            authRepository.login(email = "pending@example.com", password = "goodPassword")
        } returns AuthResult.PendingApproval
        val viewModel = makeViewModel()
        var didSucceed = false

        viewModel.login(
            email = "failed@example.com",
            password = "badPassword",
            credentialContext = credentialContext,
        ) {
            didSucceed = true
        }
        advanceUntilIdle()
        viewModel.login(
            email = "pending@example.com",
            password = "goodPassword",
            credentialContext = credentialContext,
        ) {
            didSucceed = true
        }
        advanceUntilIdle()

        assertFalse(didSucceed)
        assertTrue(credentialService.savedCredentials.isEmpty())
    }

    @Test
    fun `registration saves credential after success`() = runTest {
        coEvery {
            authRepository.register(
                firstName = "Taylor",
                lastName = "",
                email = "user@example.com",
                password = "createdPassword",
            )
        } returns RegisterOutcome(
            success = true,
            requiresApproval = false,
            message = "Account created",
        )
        val viewModel = makeViewModel()
        var didSucceed = false

        viewModel.register(
            firstName = "Taylor",
            lastName = "",
            email = " User@Example.com ",
            password = "createdPassword",
            credentialContext = credentialContext,
        ) {
            didSucceed = true
        }
        advanceUntilIdle()

        assertTrue(didSucceed)
        assertEquals(
            listOf(SystemCredential(email = "user@example.com", password = "createdPassword")),
            credentialService.savedCredentials,
        )
    }

    private fun makeViewModel(): AuthViewModel =
        AuthViewModel(
            authRepository = authRepository,
            systemCredentialService = credentialService,
            snackbarManager = SnackbarManager(),
        )
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    private val dispatcher: TestDispatcher = UnconfinedTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}

private class FakeSystemCredentialService : SystemCredentialServicing {
    val savedCredentials = mutableListOf<SystemCredential>()

    override suspend fun requestSavedCredential(context: Context): SystemCredential? = null

    override suspend fun offerSaveOrUpdateCredential(
        context: Context,
        credential: SystemCredential,
    ): SystemCredentialSaveResult {
        savedCredentials += credential
        return SystemCredentialSaveResult.SAVED
    }

    override suspend fun clearCredentialState() = Unit

    fun reset() {
        savedCredentials.clear()
    }
}
