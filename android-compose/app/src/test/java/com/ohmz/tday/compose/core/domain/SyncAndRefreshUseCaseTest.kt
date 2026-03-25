package com.ohmz.tday.compose.core.domain

import com.ohmz.tday.compose.core.data.sync.SyncManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncAndRefreshUseCaseTest {

    private val syncManager = mockk<SyncManager>()
    private val useCase = SyncAndRefreshUseCase(syncManager)

    @Test
    fun `invoke delegates to syncManager with correct parameters`() = runTest {
        coEvery {
            syncManager.syncCachedData(force = true, replayPendingMutations = true)
        } returns Result.success(Unit)

        val result = useCase(force = true, replayPendingMutations = true)

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) {
            syncManager.syncCachedData(force = true, replayPendingMutations = true)
        }
    }

    @Test
    fun `invoke uses default parameters`() = runTest {
        coEvery {
            syncManager.syncCachedData(force = true, replayPendingMutations = false)
        } returns Result.success(Unit)

        val result = useCase()

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) {
            syncManager.syncCachedData(force = true, replayPendingMutations = false)
        }
    }

    @Test
    fun `invoke propagates failure from syncManager`() = runTest {
        val error = RuntimeException("sync failed")
        coEvery {
            syncManager.syncCachedData(force = true, replayPendingMutations = false)
        } returns Result.failure(error)

        val result = useCase()

        assertTrue(result.isFailure)
    }
}
