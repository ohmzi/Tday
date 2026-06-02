package com.ohmz.tday.compose.feature.app

import android.content.Context
import app.cash.turbine.test
import com.ohmz.tday.compose.core.data.ApiCallException
import com.ohmz.tday.compose.core.data.AppDataMode
import com.ohmz.tday.compose.core.data.MutationKind
import com.ohmz.tday.compose.core.data.OfflineSyncState
import com.ohmz.tday.compose.core.data.PendingMutationRecord
import com.ohmz.tday.compose.core.data.ThemePreferenceStore
import com.ohmz.tday.compose.core.data.auth.AuthRepository
import com.ohmz.tday.compose.core.data.auth.SystemCredentialServicing
import com.ohmz.tday.compose.core.data.cache.OfflineCacheManager
import com.ohmz.tday.compose.core.data.server.AppVersionManager
import com.ohmz.tday.compose.core.data.server.ServerConfigRepository
import com.ohmz.tday.compose.core.data.settings.SettingsRepository
import com.ohmz.tday.compose.core.data.sync.SyncManager
import com.ohmz.tday.compose.core.model.SessionUser
import com.ohmz.tday.compose.core.network.ConnectivityObserver
import com.ohmz.tday.compose.core.network.RealtimeClient
import com.ohmz.tday.compose.core.network.RealtimeEvent
import com.ohmz.tday.compose.core.notification.ReminderOption
import com.ohmz.tday.compose.core.notification.ReminderPreferenceStore
import com.ohmz.tday.compose.core.notification.TaskReminderScheduler
import com.ohmz.tday.compose.core.ui.SnackbarManager
import com.ohmz.tday.compose.feature.auth.MainDispatcherRule
import com.ohmz.tday.compose.ui.theme.AppThemeMode
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val authRepository = mockk<AuthRepository>()
    private val serverConfigRepository = mockk<ServerConfigRepository>()
    private val syncManager = mockk<SyncManager>()
    private val settingsRepository = mockk<SettingsRepository>()
    private val cacheManager = mockk<OfflineCacheManager>()
    private val themePreferenceStore = mockk<ThemePreferenceStore>()
    private val reminderScheduler = mockk<TaskReminderScheduler>()
    private val reminderPreferenceStore = mockk<ReminderPreferenceStore>()
    private val realtimeClient = mockk<RealtimeClient>()
    private val connectivityObserver = mockk<ConnectivityObserver>()
    private val appVersionManager = mockk<AppVersionManager>()
    private val systemCredentialService = mockk<SystemCredentialServicing>()
    private val appContext = mockk<Context>(relaxed = true)
    private val snackbarManager = SnackbarManager(appContext)

    private val versionState = MutableStateFlow(
        AppVersionManager.VersionState(isLoadingReleases = false),
    )
    private val realtimeEvents = MutableSharedFlow<RealtimeEvent>()
    private val offlineSyncFailures = MutableSharedFlow<Unit>()
    private val offlineSyncSuccesses = MutableSharedFlow<Unit>()
    private val syncMetadataVersion = MutableStateFlow(0L)
    private val restoredUser = SessionUser(
        id = "user-1",
        name = "Taylor",
        email = "user@example.com",
        role = "USER",
    )

    @Before
    fun setUp() {
        every { appContext.getString(any()) } answers { "res-${firstArg<Int>()}" }
        every { themePreferenceStore.getThemeMode() } returns AppThemeMode.SYSTEM
        every { reminderPreferenceStore.getDefaultReminder() } returns ReminderOption.DEFAULT
        every { appVersionManager.state } returns versionState
        coEvery { appVersionManager.refreshServerCompatibility() } returns Unit
        every { serverConfigRepository.getAppDataMode() } returns AppDataMode.SERVER
        every { serverConfigRepository.hasServerConfigured() } returns true
        every { serverConfigRepository.getServerUrl() } returns "https://tday.example.com"
        every { cacheManager.loadOfflineState() } returns OfflineSyncState()
        every { cacheManager.syncMetadataVersion } returns syncMetadataVersion
        every { syncManager.offlineSyncFailures } returns offlineSyncFailures
        every { syncManager.offlineSyncSuccesses } returns offlineSyncSuccesses
        every { syncManager.hasPendingMutations() } returns false
        every { realtimeClient.events } returns realtimeEvents
        every { realtimeClient.isConnected } returns false
        every { realtimeClient.connect() } returns Unit
        every { realtimeClient.disconnect() } returns Unit
        every { connectivityObserver.connectivityChanges } returns emptyFlow()
        every { settingsRepository.isAiSummaryEnabledSnapshot() } returns true
        coEvery { authRepository.syncTimezone() } returns Unit
        every { reminderScheduler.rescheduleAll() } returns Unit
        every { reminderScheduler.cancelAll() } returns Unit
    }

    @Test
    fun `local bootstrap opens workspace without server session or sync`() = runTest {
        every { serverConfigRepository.getAppDataMode() } returns AppDataMode.LOCAL
        every { cacheManager.updateOfflineState(any()) } answers {
            firstArg<(OfflineSyncState) -> OfflineSyncState>().invoke(
                OfflineSyncState(
                    pendingMutations = listOf(
                        com.ohmz.tday.compose.core.data.PendingMutationRecord(
                            mutationId = "mutation-1",
                            kind = com.ohmz.tday.compose.core.data.MutationKind.CREATE_TODO,
                            targetId = "local-todo-1",
                            timestampEpochMs = 1L,
                        ),
                    ),
                ),
            )
        }

        val viewModel = makeViewModel()
        runCurrent()

        assertTrue(viewModel.uiState.value.isLocalMode)
        assertTrue(viewModel.uiState.value.isWorkspaceAvailable)
        assertFalse(viewModel.uiState.value.authenticated)
        assertFalse(viewModel.uiState.value.requiresServerSetup)
        assertFalse(viewModel.uiState.value.requiresLogin)
        assertEquals(0, viewModel.uiState.value.pendingMutationCount)
        assertTrue(viewModel.uiState.value.syncStatus.isLocalMode)
        assertEquals(0L, viewModel.uiState.value.syncStatus.lastSuccessfulSyncEpochMs)
        assertEquals(0L, viewModel.uiState.value.syncStatus.lastSyncAttemptEpochMs)

        coVerify(exactly = 0) { authRepository.restoreSessionForBootstrap() }
        coVerify(exactly = 0) { appVersionManager.refreshServerCompatibility() }
        coVerify(exactly = 0) { syncManager.syncCachedData(any(), any(), any(), any()) }
        verify(exactly = 0) { realtimeClient.connect() }
    }

    @Test
    fun `server bootstrap exposes sync metadata from cache`() = runTest {
        val restoredSession = AuthRepository.RestoredSession(
            user = restoredUser,
            usedCachedSession = false,
        )
        val cachedState = OfflineSyncState(
            lastSuccessfulSyncEpochMs = 1_000L,
            lastSyncAttemptEpochMs = 2_000L,
            pendingMutations = listOf(pendingMutation("mutation-1")),
        )
        every { cacheManager.loadOfflineState() } returns cachedState
        coEvery { authRepository.restoreSessionForBootstrap() } returns restoredSession
        coEvery {
            syncManager.syncCachedData(
                force = true,
                replayPendingMutations = true,
                notifyOfflineFailure = false,
                connectionProbeTimeoutMs = null,
            )
        } returns Result.success(Unit)

        val viewModel = makeViewModel()
        runCurrent()

        val syncStatus = viewModel.uiState.value.syncStatus
        assertFalse(syncStatus.isLocalMode)
        assertEquals(1, syncStatus.pendingMutationCount)
        assertEquals(1_000L, syncStatus.lastSuccessfulSyncEpochMs)
        assertEquals(2_000L, syncStatus.lastSyncAttemptEpochMs)
    }

    @Test
    fun `manual sync sets syncing state and ignores duplicate taps`() = runTest {
        val restoredSession = AuthRepository.RestoredSession(
            user = restoredUser,
            usedCachedSession = false,
        )
        var cachedState = OfflineSyncState(
            lastSuccessfulSyncEpochMs = 1_000L,
            lastSyncAttemptEpochMs = 1_000L,
        )
        every { cacheManager.loadOfflineState() } answers { cachedState }
        coEvery { authRepository.restoreSessionForBootstrap() } returns restoredSession
        coEvery {
            syncManager.syncCachedData(
                force = true,
                replayPendingMutations = true,
                notifyOfflineFailure = false,
                connectionProbeTimeoutMs = null,
            )
        } returns Result.success(Unit)
        coEvery {
            syncManager.syncCachedData(
                force = true,
                replayPendingMutations = true,
                notifyOfflineFailure = false,
                connectionProbeTimeoutMs = SyncManager.USER_REFRESH_CONNECTION_TIMEOUT_MS,
            )
        } coAnswers {
            kotlinx.coroutines.delay(100)
            cachedState = OfflineSyncState(
                lastSuccessfulSyncEpochMs = 3_000L,
                lastSyncAttemptEpochMs = 3_000L,
            )
            Result.success(Unit)
        }

        val viewModel = makeViewModel()
        runCurrent()

        viewModel.syncNow()
        runCurrent()
        assertTrue(viewModel.uiState.value.isManualSyncing)

        viewModel.syncNow()
        runCurrent()
        coVerify(exactly = 1) {
            syncManager.syncCachedData(
                force = true,
                replayPendingMutations = true,
                notifyOfflineFailure = false,
                connectionProbeTimeoutMs = SyncManager.USER_REFRESH_CONNECTION_TIMEOUT_MS,
            )
        }

        advanceTimeBy(100)
        runCurrent()

        assertFalse(viewModel.uiState.value.isManualSyncing)
        assertEquals(3_000L, viewModel.uiState.value.syncStatus.lastSuccessfulSyncEpochMs)
        assertEquals(3_000L, viewModel.uiState.value.syncStatus.lastSyncAttemptEpochMs)
    }

    @Test
    fun `foreground reconnect retries sync after restoring session`() = runTest {
        val restoredSession = AuthRepository.RestoredSession(
            user = restoredUser,
            usedCachedSession = false,
        )
        coEvery { authRepository.restoreSessionForBootstrap() } returnsMany listOf(
            restoredSession,
            restoredSession,
        )
        coEvery {
            syncManager.syncCachedData(
                force = true,
                replayPendingMutations = true,
                notifyOfflineFailure = false,
                connectionProbeTimeoutMs = null,
            )
        } returns Result.success(Unit)
        coEvery {
            syncManager.syncCachedData(
                force = true,
                replayPendingMutations = true,
                notifyOfflineFailure = false,
                connectionProbeTimeoutMs = SyncManager.USER_REFRESH_CONNECTION_TIMEOUT_MS,
            )
        } returnsMany listOf(
            Result.failure(ApiCallException(statusCode = 401, message = "Unauthorized")),
            Result.success(Unit),
        )

        val viewModel = makeViewModel()
        runCurrent()

        snackbarManager.events.test {
            viewModel.reconnectAfterForeground()
            runCurrent()
            runCurrent()
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }

        assertTrue(viewModel.uiState.value.authenticated)
        assertFalse(viewModel.uiState.value.isOffline)
        assertEquals(restoredUser, viewModel.uiState.value.user)

        viewModel.logout()
        runCurrent()
    }

    @Test
    fun `foreground reconnect marks app offline when session cannot be restored`() = runTest {
        val restoredSession = AuthRepository.RestoredSession(
            user = restoredUser,
            usedCachedSession = false,
        )
        coEvery { authRepository.restoreSessionForBootstrap() } returnsMany listOf(
            restoredSession,
            null,
        )
        coEvery {
            syncManager.syncCachedData(
                force = true,
                replayPendingMutations = true,
                notifyOfflineFailure = false,
                connectionProbeTimeoutMs = null,
            )
        } returns Result.success(Unit)
        coEvery {
            syncManager.syncCachedData(
                force = true,
                replayPendingMutations = true,
                notifyOfflineFailure = false,
                connectionProbeTimeoutMs = SyncManager.USER_REFRESH_CONNECTION_TIMEOUT_MS,
            )
        } returns Result.failure(ApiCallException(statusCode = 401, message = "Unauthorized"))

        val viewModel = makeViewModel()
        runCurrent()

        snackbarManager.events.test {
            viewModel.reconnectAfterForeground()
            runCurrent()
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }

        assertTrue(viewModel.uiState.value.authenticated)
        assertTrue(viewModel.uiState.value.isOffline)
        assertEquals(restoredUser, viewModel.uiState.value.user)

        viewModel.logout()
        runCurrent()
    }

    @Test
    fun `offline notice cooldown suppresses repeat notices for ten minutes`() {
        var now = 1_000L
        val cooldown = OfflineNoticeCooldown { now }

        assertTrue(cooldown.shouldShowNotice())
        assertFalse(cooldown.shouldShowNotice())

        now += OFFLINE_NOTICE_COOLDOWN_MS - 1
        assertFalse(cooldown.shouldShowNotice())

        now += 1
        assertTrue(cooldown.shouldShowNotice())
    }

    private fun pendingMutation(id: String): PendingMutationRecord =
        PendingMutationRecord(
            mutationId = id,
            kind = MutationKind.CREATE_TODO,
            targetId = "local-todo-1",
            timestampEpochMs = 1L,
        )

    private fun makeViewModel(): AppViewModel =
        AppViewModel(
            authRepository = authRepository,
            serverConfigRepository = serverConfigRepository,
            syncManager = syncManager,
            settingsRepository = settingsRepository,
            cacheManager = cacheManager,
            themePreferenceStore = themePreferenceStore,
            reminderScheduler = reminderScheduler,
            reminderPreferenceStore = reminderPreferenceStore,
            snackbarManager = snackbarManager,
            realtimeClient = realtimeClient,
            connectivityObserver = connectivityObserver,
            appVersionManager = appVersionManager,
            systemCredentialService = systemCredentialService,
            appContext = appContext,
        )
}
