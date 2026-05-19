package com.ohmz.tday.compose.feature.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ohmz.tday.compose.core.data.ServerProbeException
import com.ohmz.tday.compose.core.data.auth.AuthRepository
import com.ohmz.tday.compose.core.data.cache.OfflineCacheManager
import com.ohmz.tday.compose.core.data.server.AppVersionManager
import com.ohmz.tday.compose.core.data.server.ServerConfigRepository
import com.ohmz.tday.compose.core.data.server.VersionCheckResult
import com.ohmz.tday.compose.core.data.settings.SettingsRepository
import com.ohmz.tday.compose.feature.release.GitHubRelease
import com.ohmz.tday.compose.core.data.sync.SyncManager
import com.ohmz.tday.compose.core.data.ApiCallException
import com.ohmz.tday.compose.core.data.ThemePreferenceStore
import com.ohmz.tday.compose.core.network.ConnectivityObserver
import com.ohmz.tday.compose.core.network.RealtimeClient
import com.ohmz.tday.compose.core.network.RealtimeEvent
import com.ohmz.tday.compose.core.data.isLikelyConnectivityIssue
import com.ohmz.tday.compose.core.ui.SnackbarManager
import com.ohmz.tday.compose.core.ui.userFacingMessage
import com.ohmz.tday.compose.core.model.SessionUser
import com.ohmz.tday.compose.core.notification.ReminderOption
import com.ohmz.tday.compose.core.notification.ReminderPreferenceStore
import com.ohmz.tday.compose.core.notification.TaskReminderScheduler
import com.ohmz.tday.compose.ui.theme.AppThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.net.ssl.SSLPeerUnverifiedException

data class AppUiState(
    val loading: Boolean = true,
    val authenticated: Boolean = false,
    val requiresServerSetup: Boolean = false,
    val requiresLogin: Boolean = false,
    val serverUrl: String? = null,
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val user: SessionUser? = null,
    val error: String? = null,
    val canResetServerTrust: Boolean = false,
    val pendingApprovalMessage: String? = null,
    val isManualSyncing: Boolean = false,
    val adminAiSummaryEnabled: Boolean? = null,
    val isAdminAiSummaryLoading: Boolean = false,
    val isAdminAiSummarySaving: Boolean = false,
    val adminAiSummaryError: String? = null,
    val aiSummaryValidationError: String? = null,
    val selectedReminder: ReminderOption = ReminderOption.DEFAULT,
    val isOffline: Boolean = false,
    val pendingMutationCount: Int = 0,
    val versionCheckResult: VersionCheckResult? = null,
    val backendVersion: String? = null,
    val requiredUpdateRelease: GitHubRelease? = null,
    val isCheckingUpdateRelease: Boolean = false,
)

@HiltViewModel
class AppViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val serverConfigRepository: ServerConfigRepository,
    private val syncManager: SyncManager,
    private val settingsRepository: SettingsRepository,
    private val cacheManager: OfflineCacheManager,
    private val themePreferenceStore: ThemePreferenceStore,
    private val reminderScheduler: TaskReminderScheduler,
    private val reminderPreferenceStore: ReminderPreferenceStore,
    val snackbarManager: SnackbarManager,
    private val realtimeClient: RealtimeClient,
    private val connectivityObserver: ConnectivityObserver,
    private val appVersionManager: AppVersionManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()
    private var resyncJob: Job? = null
    private var realtimeJob: Job? = null
    private var connectivityJob: Job? = null

    init {
        _uiState.update {
            it.copy(
                themeMode = themePreferenceStore.getThemeMode(),
                selectedReminder = reminderPreferenceStore.getDefaultReminder(),
            )
        }
        viewModelScope.launch {
            appVersionManager.state.collect { vs ->
                _uiState.update {
                    it.copy(
                        versionCheckResult = vs.versionCheckResult,
                        backendVersion = vs.backendVersion,
                        requiredUpdateRelease = vs.requiredUpdateRelease,
                        isCheckingUpdateRelease = vs.isCheckingUpdateRelease,
                    )
                }
            }
        }
        bootstrap()
    }

    fun bootstrap() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null, isManualSyncing = false) }
            if (!serverConfigRepository.hasServerConfigured()) {
                authRepository.clearAllLocalUserDataForUnauthenticatedState()
                _uiState.update {
                    it.copy(
                        loading = false,
                        authenticated = false,
                        requiresServerSetup = true,
                        requiresLogin = false,
                        serverUrl = null,
                        user = null,
                        error = null,
                        canResetServerTrust = false,
                        pendingApprovalMessage = null,
                        isManualSyncing = false,
                        adminAiSummaryEnabled = null,
                        isAdminAiSummaryLoading = false,
                        isAdminAiSummarySaving = false,
                        adminAiSummaryError = null,
                    )
                }
                ensureResyncLoop(authenticated = false)
                return@launch
            }

            val sessionUser = restoreSessionAndPrimeData()
            appVersionManager.refreshServerCompatibility()
            val vs = appVersionManager.state.value
            val versionResult = vs.versionCheckResult
            val isBlocking = versionResult is VersionCheckResult.AppUpdateRequired ||
                versionResult is VersionCheckResult.ServerUpdateRequired

            if (sessionUser != null) {
                val adminUser = isAdmin(sessionUser)
                _uiState.update {
                    it.copy(
                        loading = false,
                        authenticated = true,
                        requiresServerSetup = false,
                        requiresLogin = false,
                        serverUrl = serverConfigRepository.getServerUrl(),
                        user = sessionUser,
                        error = null,
                        canResetServerTrust = false,
                        pendingApprovalMessage = null,
                        isManualSyncing = false,
                        adminAiSummaryEnabled = if (adminUser) {
                            settingsRepository.isAiSummaryEnabledSnapshot()
                        } else {
                            null
                        },
                        isAdminAiSummaryLoading = adminUser,
                        isAdminAiSummarySaving = false,
                        adminAiSummaryError = null,
                        versionCheckResult = vs.versionCheckResult,
                        backendVersion = vs.backendVersion,
                        requiredUpdateRelease = vs.requiredUpdateRelease,
                        isCheckingUpdateRelease = vs.isCheckingUpdateRelease,
                    )
                }
                ensureResyncLoop(authenticated = true)
                if (adminUser) {
                    refreshAdminAiSummarySetting()
                }
                return@launch
            }

            if (isBlocking) {
                _uiState.update {
                    it.copy(
                        loading = false,
                        authenticated = false,
                        requiresServerSetup = false,
                        requiresLogin = false,
                        serverUrl = serverConfigRepository.getServerUrl(),
                        user = null,
                        error = null,
                        canResetServerTrust = false,
                        pendingApprovalMessage = null,
                        isManualSyncing = false,
                        adminAiSummaryEnabled = null,
                        isAdminAiSummaryLoading = false,
                        isAdminAiSummarySaving = false,
                        adminAiSummaryError = null,
                        versionCheckResult = vs.versionCheckResult,
                        backendVersion = vs.backendVersion,
                        requiredUpdateRelease = vs.requiredUpdateRelease,
                        isCheckingUpdateRelease = vs.isCheckingUpdateRelease,
                    )
                }
                return@launch
            }

            authRepository.clearSessionOnly()

            _uiState.update {
                it.copy(
                    loading = false,
                    authenticated = false,
                    requiresServerSetup = false,
                    requiresLogin = true,
                    serverUrl = serverConfigRepository.getServerUrl(),
                    user = null,
                    error = null,
                    canResetServerTrust = false,
                    pendingApprovalMessage = null,
                    isManualSyncing = false,
                    adminAiSummaryEnabled = null,
                    isAdminAiSummaryLoading = false,
                    isAdminAiSummarySaving = false,
                    adminAiSummaryError = null,
                )
            }
            ensureResyncLoop(authenticated = false)
        }
    }

    private suspend fun restoreSessionAndPrimeData(): SessionUser? {
        val user = runCatching { authRepository.restoreSession() }.getOrNull()
            ?: return null
        if (user.id == null) return null

        coroutineScope {
            launch { runCatching { syncManager.syncCachedData(force = true, replayPendingMutations = true) } }
            launch { runCatching { authRepository.syncTimezone() } }
            launch(Dispatchers.Default) { runCatching { reminderScheduler.rescheduleAll() } }
        }

        return user
    }

    fun refreshSession() = bootstrap()

    fun refreshAdminAiSummarySetting() {
        val current = _uiState.value
        if (!isAdmin(current.user)) {
            _uiState.update {
                it.copy(
                    adminAiSummaryEnabled = null,
                    isAdminAiSummaryLoading = false,
                    isAdminAiSummarySaving = false,
                    adminAiSummaryError = null,
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isAdminAiSummaryLoading = true,
                    adminAiSummaryError = null,
                    adminAiSummaryEnabled = it.adminAiSummaryEnabled
                        ?: settingsRepository.isAiSummaryEnabledSnapshot(),
                )
            }
            runCatching { settingsRepository.fetchAdminAiSummaryEnabled() }
                .onSuccess { enabled ->
                    _uiState.update {
                        it.copy(
                            adminAiSummaryEnabled = enabled,
                            isAdminAiSummaryLoading = false,
                            adminAiSummaryError = null,
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isAdminAiSummaryLoading = false,
                            adminAiSummaryError = friendlyAdminError(error, "Could not load admin settings"),
                        )
                    }
                }
        }
    }

    fun setAdminAiSummaryEnabled(enabled: Boolean) {
        val current = _uiState.value
        if (!isAdmin(current.user) || current.isAdminAiSummarySaving) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    adminAiSummaryEnabled = enabled,
                    isAdminAiSummarySaving = true,
                    adminAiSummaryError = null,
                )
            }
            runCatching { settingsRepository.updateAdminAiSummaryEnabled(enabled) }
                .onSuccess { response ->
                    val validationFailed = response.validationError != null
                    if (validationFailed) {
                        android.util.Log.e(
                            "AppViewModel",
                            "AI summary validation failed: ${response.validationError}",
                        )
                    }
                    _uiState.update {
                        it.copy(
                            adminAiSummaryEnabled = response.aiSummaryEnabled,
                            isAdminAiSummaryLoading = false,
                            isAdminAiSummarySaving = false,
                            adminAiSummaryError = null,
                            aiSummaryValidationError = response.validationError,
                        )
                    }
                }
                .onFailure { error ->
                    android.util.Log.e("AppViewModel", "AI summary toggle failed", error)
                    _uiState.update {
                        it.copy(
                            isAdminAiSummarySaving = false,
                            adminAiSummaryError = friendlyAdminError(error, "Could not update admin settings"),
                        )
                    }
                    refreshAdminAiSummarySetting()
                }
        }
    }

    fun dismissAiSummaryValidationError() {
        _uiState.update { it.copy(aiSummaryValidationError = null) }
    }

    fun saveServerUrl(
        rawUrl: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit = {},
    ) {
        viewModelScope.launch {
            val result = probeAndSaveWithAutomaticTrustRecovery(rawUrl)
            result.onSuccess { probeResult ->
                val versionResult = probeResult.versionCheck
                val isBlocking = versionResult is VersionCheckResult.AppUpdateRequired ||
                    versionResult is VersionCheckResult.ServerUpdateRequired
                _uiState.update {
                    it.copy(
                        requiresServerSetup = false,
                        requiresLogin = !isBlocking,
                        serverUrl = probeResult.serverUrl,
                        error = null,
                        canResetServerTrust = false,
                        pendingApprovalMessage = null,
                        versionCheckResult = versionResult,
                        backendVersion = probeResult.backendVersion,
                    )
                }
                onSuccess()
            }.onFailure { error ->
                val message = toServerSetupMessage(error)
                _uiState.update {
                    it.copy(
                        error = message,
                        canResetServerTrust = false,
                        pendingApprovalMessage = null,
                    )
                }
                onFailure(message)
            }
            val probeResult = result.getOrNull() ?: return@launch
            appVersionManager.applyServerCompatibility(
                probeResult.versionCheck,
                probeResult.backendVersion,
            )
        }
    }

    fun recheckVersion() {
        viewModelScope.launch {
            appVersionManager.refreshServerCompatibility()
            if (appVersionManager.state.value.versionCheckResult is VersionCheckResult.Compatible &&
                !_uiState.value.authenticated
            ) {
                bootstrap()
            }
        }
    }

    fun refreshVersionInfo() {
        viewModelScope.launch {
            appVersionManager.refreshAll()
        }
    }

    fun resetTrustedServer(
        rawUrl: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit = {},
    ) {
        viewModelScope.launch {
            val result = serverConfigRepository.resetTrustedServer(rawUrl)
            result.onSuccess {
                _uiState.update {
                    it.copy(
                        error = null,
                        canResetServerTrust = false,
                        pendingApprovalMessage = null,
                    )
                }
                onSuccess()
            }.onFailure { error ->
                val message = error.userFacingMessage("Could not reset trusted server.")
                _uiState.update {
                    it.copy(
                        error = message,
                        canResetServerTrust = false,
                        pendingApprovalMessage = null,
                    )
                }
                onFailure(message)
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            runCatching { authRepository.logout() }
            runCatching { reminderScheduler.cancelAll() }
            ensureResyncLoop(authenticated = false)
            _uiState.update {
                it.copy(
                    authenticated = false,
                    requiresServerSetup = true,
                    requiresLogin = false,
                    serverUrl = null,
                    user = null,
                    error = null,
                    loading = false,
                    canResetServerTrust = false,
                    pendingApprovalMessage = null,
                    isManualSyncing = false,
                    adminAiSummaryEnabled = null,
                    isAdminAiSummaryLoading = false,
                    isAdminAiSummarySaving = false,
                    adminAiSummaryError = null,
                )
            }
        }
    }

    fun syncNow() {
        if (!_uiState.value.authenticated) return
        if (_uiState.value.isManualSyncing) return

        viewModelScope.launch {
            _uiState.update { it.copy(isManualSyncing = true) }
            val result = syncManager.syncCachedData(
                force = true,
                replayPendingMutations = true,
            )
            val syncError = result.exceptionOrNull()
            _uiState.update {
                it.copy(
                    isManualSyncing = false,
                    isOffline = syncError != null && isLikelyConnectivityIssue(syncError),
                    pendingMutationCount = runCatching {
                        cacheManager.loadOfflineState().pendingMutations.size
                    }.getOrDefault(it.pendingMutationCount),
                )
            }
            syncError?.let(::classifyAndShowError)
            launch(Dispatchers.Default) { runCatching { reminderScheduler.rescheduleAll() } }
        }
    }

    fun setThemeMode(mode: AppThemeMode) {
        themePreferenceStore.setThemeMode(mode)
        _uiState.update { it.copy(themeMode = mode) }
    }

    fun setDefaultReminder(option: ReminderOption) {
        reminderPreferenceStore.setDefaultReminder(option)
        _uiState.update { it.copy(selectedReminder = option) }
        viewModelScope.launch(Dispatchers.Default) {
            runCatching { reminderScheduler.rescheduleAll() }
        }
    }

    fun rescheduleReminders() {
        viewModelScope.launch(Dispatchers.Default) {
            runCatching { reminderScheduler.rescheduleAll() }
        }
    }

    fun clearPendingApprovalNotice() {
        _uiState.update { it.copy(pendingApprovalMessage = null) }
    }

    private fun ensureResyncLoop(authenticated: Boolean) {
        if (!authenticated) {
            resyncJob?.cancel()
            resyncJob = null
            realtimeJob?.cancel()
            realtimeJob = null
            connectivityJob?.cancel()
            connectivityJob = null
            realtimeClient.disconnect()
            return
        }

        startRealtimeListener()
        startConnectivityListener()

        if (resyncJob?.isActive == true) return

        resyncJob = viewModelScope.launch {
            while (isActive) {
                val hasPending = runCatching { syncManager.hasPendingMutations() }.getOrDefault(false)
                val pendingCount = runCatching {
                    cacheManager.loadOfflineState().pendingMutations.size
                }.getOrDefault(0)
                _uiState.update { it.copy(pendingMutationCount = pendingCount) }

                val delayMs = if (hasPending) PENDING_RESYNC_INTERVAL_MS else RESYNC_INTERVAL_MS
                delay(delayMs)

                syncAndUpdateOfflineState(replayPending = hasPending)
            }
        }
    }

    private suspend fun syncAndUpdateOfflineState(replayPending: Boolean) {
        val result = syncManager.syncCachedData(
            force = true,
            replayPendingMutations = replayPending,
        )
        val syncError = result.exceptionOrNull()
        _uiState.update {
            it.copy(
                isOffline = syncError != null && isLikelyConnectivityIssue(syncError),
                pendingMutationCount = runCatching {
                    cacheManager.loadOfflineState().pendingMutations.size
                }.getOrDefault(it.pendingMutationCount),
            )
        }
        if (syncError != null) {
            classifyAndShowError(syncError)
        }
        viewModelScope.launch(Dispatchers.Default) { runCatching { reminderScheduler.rescheduleAll() } }
    }

    private fun classifyAndShowError(error: Throwable) {
        if (isLikelyConnectivityIssue(error)) return
        snackbarManager.showError(error.userFacingMessage()) {
            if (error !is ApiCallException || error.statusCode != 401) syncNow()
        }
    }

    private fun startRealtimeListener() {
        if (realtimeJob?.isActive == true) return

        realtimeClient.connect()
        realtimeJob = viewModelScope.launch {
            realtimeClient.events.collect { event ->
                when (event) {
                    is RealtimeEvent.Connected -> {
                        if (_uiState.value.isOffline) {
                            syncAndUpdateOfflineState(replayPending = true)
                        }
                    }
                    is RealtimeEvent.TodoChanged,
                    is RealtimeEvent.ListChanged,
                    is RealtimeEvent.CompletedChanged,
                    -> {
                        syncAndUpdateOfflineState(replayPending = false)
                    }
                    is RealtimeEvent.Disconnected -> {
                        delay(REALTIME_RECONNECT_DELAY_MS)
                        if (_uiState.value.authenticated) realtimeClient.connect()
                    }
                    else -> {}
                }
            }
        }
    }

    private fun startConnectivityListener() {
        if (connectivityJob?.isActive == true) return

        connectivityJob = viewModelScope.launch {
            connectivityObserver.connectivityChanges.collectLatest { isConnected ->
                if (isConnected && _uiState.value.isOffline && _uiState.value.authenticated) {
                    delay(CONNECTIVITY_RESTORED_DEBOUNCE_MS)
                    syncAndUpdateOfflineState(replayPending = true)
                    if (!realtimeClient.isConnected) {
                        realtimeClient.connect()
                    }
                }
                if (!isConnected && _uiState.value.authenticated) {
                    _uiState.update { it.copy(isOffline = true) }
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        realtimeClient.disconnect()
    }

    private fun isAdmin(user: SessionUser?): Boolean {
        return user?.role?.equals("ADMIN", ignoreCase = true) == true
    }

    private fun friendlyAdminError(error: Throwable, fallback: String): String {
        return error.userFacingMessage(fallback)
    }

    private suspend fun probeAndSaveWithAutomaticTrustRecovery(
        rawUrl: String,
    ): Result<ServerConfigRepository.ProbeResult> {
        val firstAttempt = serverConfigRepository.probeAndSave(rawUrl)
        val firstError = firstAttempt.exceptionOrNull() ?: return firstAttempt
        if (!isServerTrustMismatch(firstError)) return firstAttempt

        val resetAttempt = serverConfigRepository.resetTrustedServer(rawUrl)
        if (resetAttempt.isFailure) {
            return Result.failure(AutomaticTrustRefreshFailedException(resetAttempt.exceptionOrNull()))
        }

        val secondAttempt = serverConfigRepository.probeAndSave(rawUrl)
        val secondError = secondAttempt.exceptionOrNull() ?: return secondAttempt
        return if (isServerTrustMismatch(secondError)) {
            Result.failure(AutomaticTrustRefreshFailedException(secondError))
        } else {
            secondAttempt
        }
    }

    private fun toServerSetupMessage(error: Throwable): String {
        return when (error) {
            is TimeoutCancellationException -> "Server probe timed out. Check URL and network, then try again."
            is ServerProbeException.InvalidUrl -> "Invalid server URL."
            is ServerProbeException.InsecureTransport -> "Use HTTPS for remote server URLs."
            is ServerProbeException.NotTdayServer ->
                "Server is reachable but does not appear to be a T'Day server."
            is AutomaticTrustRefreshFailedException ->
                staleServerTrustMessage()
            is ServerProbeException.CertificateChanged ->
                automaticTrustRefreshMessage()
            is SSLPeerUnverifiedException ->
                if (isServerTrustMismatch(error)) {
                    automaticTrustRefreshMessage()
                } else {
                    error.userFacingMessage("Could not connect to server.")
                }
            else -> error.userFacingMessage("Could not connect to server.")
        }
    }

    private fun isServerTrustMismatch(error: Throwable): Boolean {
        return error is ServerProbeException.CertificateChanged || isPinnedCertificateMismatch(error)
    }

    private fun isPinnedCertificateMismatch(error: Throwable): Boolean {
        return error.message?.contains("Pinned certificate mismatch", ignoreCase = true) == true
    }

    private fun automaticTrustRefreshMessage(): String {
        return "Saved server trust changed. Trying again with a refreshed certificate."
    }

    private fun staleServerTrustMessage(): String {
        return "The app could not recover from a saved server trust mismatch automatically. Clear app storage or reinstall the app, then try again."
    }

    private class AutomaticTrustRefreshFailedException(
        cause: Throwable?,
    ) : IllegalStateException("Automatic server trust refresh failed.", cause)

    private companion object {
        const val PENDING_RESYNC_INTERVAL_MS = 20 * 1000L
        const val RESYNC_INTERVAL_MS = 5 * 60 * 1000L
        const val REALTIME_RECONNECT_DELAY_MS = 5_000L
        const val CONNECTIVITY_RESTORED_DEBOUNCE_MS = 1_500L
    }
}
