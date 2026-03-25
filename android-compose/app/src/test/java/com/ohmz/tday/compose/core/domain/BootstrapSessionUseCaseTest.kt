package com.ohmz.tday.compose.core.domain

import com.ohmz.tday.compose.core.data.auth.AuthRepository
import com.ohmz.tday.compose.core.data.sync.SyncManager
import com.ohmz.tday.compose.core.model.SessionUser
import com.ohmz.tday.compose.core.notification.TaskReminderScheduler
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.Runs
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BootstrapSessionUseCaseTest {

    private val authRepository = mockk<AuthRepository>()
    private val syncManager = mockk<SyncManager>()
    private val reminderScheduler = mockk<TaskReminderScheduler>()
    private val useCase = BootstrapSessionUseCase(authRepository, syncManager, reminderScheduler)

    @Test
    fun `invoke returns user and triggers sync when session is valid`() = runTest {
        val user = SessionUser(id = "u1", name = "Test", email = "test@example.com")
        coEvery { authRepository.restoreSession() } returns user
        coEvery { syncManager.syncCachedData(force = true) } returns Result.success(Unit)
        coEvery { authRepository.syncTimezone() } just Runs
        every { reminderScheduler.rescheduleAll() } just Runs

        val result = useCase()

        assertEquals(user, result)
        coVerify(exactly = 1) { syncManager.syncCachedData(force = true) }
        coVerify(exactly = 1) { authRepository.syncTimezone() }
        coVerify(exactly = 1) { reminderScheduler.rescheduleAll() }
    }

    @Test
    fun `invoke returns null when restoreSession throws`() = runTest {
        coEvery { authRepository.restoreSession() } throws RuntimeException("no session")

        val result = useCase()

        assertNull(result)
        coVerify(exactly = 0) { syncManager.syncCachedData(any()) }
    }

    @Test
    fun `invoke returns null when restoreSession returns null`() = runTest {
        coEvery { authRepository.restoreSession() } returns null

        val result = useCase()

        assertNull(result)
    }

    @Test
    fun `invoke returns null when user id is null`() = runTest {
        coEvery { authRepository.restoreSession() } returns SessionUser(id = null)

        val result = useCase()

        assertNull(result)
        coVerify(exactly = 0) { syncManager.syncCachedData(any()) }
    }

    @Test
    fun `invoke still returns user when sync fails`() = runTest {
        val user = SessionUser(id = "u1", name = "Test")
        coEvery { authRepository.restoreSession() } returns user
        coEvery { syncManager.syncCachedData(force = true) } throws RuntimeException("offline")
        coEvery { authRepository.syncTimezone() } just Runs
        every { reminderScheduler.rescheduleAll() } just Runs

        val result = useCase()

        assertEquals(user, result)
    }
}
