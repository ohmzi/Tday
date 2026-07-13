package com.ohmz.tday.compose.feature.app

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ohmz.tday.compose.R
import com.ohmz.tday.compose.core.data.ApiCallException
import com.ohmz.tday.compose.core.data.AppDataMode
import com.ohmz.tday.compose.core.data.ServerProbeException
import com.ohmz.tday.compose.core.data.ThemePreferenceStore
import com.ohmz.tday.compose.core.data.auth.AuthRepository
import com.ohmz.tday.compose.core.data.auth.SystemCredentialServicing
import com.ohmz.tday.compose.core.data.cache.OfflineCacheManager
import com.ohmz.tday.compose.core.data.isLikelyConnectivityIssue
import com.ohmz.tday.compose.core.data.isSessionAuthenticationIssue
import com.ohmz.tday.compose.core.data.server.AppVersionManager
import com.ohmz.tday.compose.core.data.server.ServerConfigRepository
import com.ohmz.tday.compose.core.data.server.VersionCheckResult
import com.ohmz.tday.compose.core.data.settings.SettingsRepository
import com.ohmz.tday.compose.core.data.sync.SyncManager
import com.ohmz.tday.compose.core.model.AuthResult
import com.ohmz.tday.compose.core.model.SecurityAnswerInput
import com.ohmz.tday.compose.core.model.SecurityQuestion
import com.ohmz.tday.compose.core.model.SecurityQuestionStatusResponse
import com.ohmz.tday.compose.core.model.SessionUser
import com.ohmz.tday.compose.core.network.ConnectivityObserver
import com.ohmz.tday.compose.core.network.RealtimeClient
import com.ohmz.tday.compose.core.network.RealtimeEvent
import com.ohmz.tday.compose.core.notification.DayAheadOption
import com.ohmz.tday.compose.core.notification.DayAheadPreferenceStore
import com.ohmz.tday.compose.core.notification.DayAheadScheduling
import com.ohmz.tday.compose.core.notification.ReminderOption
import com.ohmz.tday.compose.core.notification.ReminderPreferenceStore
import com.ohmz.tday.compose.core.notification.TaskReminderScheduler
import com.ohmz.tday.compose.core.observability.TdayTelemetry
import com.ohmz.tday.compose.core.ui.SnackbarEvent
import com.ohmz.tday.compose.core.ui.SnackbarKind
import com.ohmz.tday.compose.core.ui.SnackbarManager
import com.ohmz.tday.compose.core.ui.userFacingMessage
import com.ohmz.tday.compose.feature.release.GitHubRelease
import com.ohmz.tday.compose.ui.theme.AppThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.net.ssl.SSLPeerUnverifiedException

data class AppUiState(
    val loading: Boolean = true,
    val authenticated: Boolean = false,
    val requiresServerSetup: Boolean = false,
    val requiresLogin: Boolean = false,
    val serverUrl: String? = null,
    val dataMode: AppDataMode = AppDataMode.UNSET,
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val user: SessionUser? = null,
    val error: String? = null,
    val canResetServerTrust: Boolean = false,
    val pendingApprovalMessage: String? = null,
    // Persistent "waiting for admin approval" holding screen (survives relaunch via the
    // secure pending marker; cleared once approved or signed out).
    val pendingApproval: Boolean = false,
    val pendingApprovalUsername: String? = null,
    val isCheckingApproval: Boolean = false,
    val isManualSyncing: Boolean = false,
    val aiSummaryEnabled: Boolean = true,
    val selectedReminder: ReminderOption = ReminderOption.DEFAULT,
    val selectedDayAhead: DayAheadOption = DayAheadOption.OFF,
    val isOffline: Boolean = false,
    val pendingMutationCount: Int = 0,
    val lastSuccessfulSyncEpochMs: Long = 0L,
    val lastSyncAttemptEpochMs: Long = 0L,
    val offlineNoticeId: Long = 0L,
    val versionCheckResult: VersionCheckResult? = null,
    val backendVersion: String? = null,
    val requiredUpdateRelease: GitHubRelease? = null,
    val isCheckingUpdateRelease: Boolean = false,
) {
    val isLocalMode: Boolean
        get() = dataMode == AppDataMode.LOCAL

    val isWorkspaceAvailable: Boolean
        get() = authenticated || isLocalMode

    val syncStatus: MobileSyncStatus
        get() = MobileSyncStatus(
            dataMode = dataMode,
            isOffline = isOffline,
            isManualSyncing = isManualSyncing,
            pendingMutationCount = pendingMutationCount,
            lastSuccessfulSyncEpochMs = lastSuccessfulSyncEpochMs,
            lastSyncAttemptEpochMs = lastSyncAttemptEpochMs,
        )
}

data class MobileSyncStatus(
    val dataMode: AppDataMode = AppDataMode.UNSET,
    val isOffline: Boolean = false,
    val isManualSyncing: Boolean = false,
    val pendingMutationCount: Int = 0,
    val lastSuccessfulSyncEpochMs: Long = 0L,
    val lastSyncAttemptEpochMs: Long = 0L,
) {
    val isLocalMode: Boolean
        get() = dataMode == AppDataMode.LOCAL
}

private data class SyncMetadataSnapshot(
    val pendingMutationCount: Int,
    val lastSuccessfulSyncEpochMs: Long,
    val lastSyncAttemptEpochMs: Long,
)

internal const val OFFLINE_NOTICE_COOLDOWN_MS = 10 * 60 * 1000L

internal class OfflineNoticeCooldown(
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
) {
    private var lastNoticeShownAtMs: Long? = null

    fun shouldShowNotice(): Boolean {
        val now = nowMillis()
        val lastShownAt = lastNoticeShownAtMs
        if (lastShownAt != null && now - lastShownAt < OFFLINE_NOTICE_COOLDOWN_MS) {
            return false
        }

        lastNoticeShownAtMs = now
        return true
    }
}

/** Outcome of an inline account edit (display name / password change). */
sealed interface ProfileEditResult {
    data object Success : ProfileEditResult
    data class Error(val message: String) : ProfileEditResult
}

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
    private val dayAheadPreferenceStore: DayAheadPreferenceStore,
    val snackbarManager: SnackbarManager,
    private val realtimeClient: RealtimeClient,
    private val connectivityObserver: ConnectivityObserver,
    private val appVersionManager: AppVersionManager,
    private val systemCredentialService: SystemCredentialServicing,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()
    private var resyncJob: Job? = null
    private var realtimeJob: Job? = null
    private var connectivityJob: Job? = null
    private var foregroundReconnectJob: Job? = null
    private val offlineNoticeCooldown = OfflineNoticeCooldown()

    // The silent pending-approval re-login runs at most once per process (first launch),
    // so an explicit logout is never undone by a later bootstrap.
    private var hasAttemptedLaunchReauth = false

    init {
        _uiState.update {
            it.copy(
                themeMode = themePreferenceStore.getThemeMode(),
                selectedReminder = reminderPreferenceStore.getDefaultReminder(),
                selectedDayAhead = dayAheadPreferenceStore.getOption(),
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
        observeOfflineSyncFailures()
        observeOfflineSyncSuccesses()
        observeSyncMetadataChanges()
        bootstrap()
    }

    fun bootstrap() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null, isManualSyncing = false) }
            val dataMode = serverConfigRepository.getAppDataMode()
            if (dataMode == AppDataMode.LOCAL) {
                enterLocalWorkspace()
                return@launch
            }

            if (!serverConfigRepository.hasServerConfigured()) {
                authRepository.clearAllLocalUserDataForUnauthenticatedState()
                _uiState.update {
                    it.copy(
                        loading = false,
                        authenticated = false,
                        requiresServerSetup = true,
                        requiresLogin = false,
                        serverUrl = null,
                        dataMode = AppDataMode.UNSET,
                        user = null,
                        error = null,
                        canResetServerTrust = false,
                        pendingApprovalMessage = null,
                        isManualSyncing = false,
                        aiSummaryEnabled = true,
                        isOffline = false,
                        pendingMutationCount = 0,
                        lastSuccessfulSyncEpochMs = 0L,
                        lastSyncAttemptEpochMs = 0L,
                        versionCheckResult = VersionCheckResult.Compatible,
                        backendVersion = null,
                        requiredUpdateRelease = null,
                        isCheckingUpdateRelease = false,
                    )
                }
                ensureResyncLoop(authenticated = false)
                return@launch
            }

            val sessionResult = restoreSessionAndPrimeData()
            appVersionManager.refreshServerCompatibility()
            val vs = appVersionManager.state.value
            val versionResult = vs.versionCheckResult
            val isBlocking = versionResult is VersionCheckResult.AppUpdateRequired ||
                versionResult is VersionCheckResult.ServerUpdateRequired

            if (sessionResult != null) {
                val sessionUser = sessionResult.user
                val shouldShowOfflineNotice = sessionResult.isOffline &&
                        offlineNoticeCooldown.shouldShowNotice()
                val syncMetadata = syncMetadataSnapshot(AppDataMode.SERVER)
                _uiState.update {
                    it.copy(
                        loading = false,
                        authenticated = true,
                        requiresServerSetup = false,
                        requiresLogin = false,
                        // Approval came through: clear the holding screen in the same
                        // update that flips `authenticated`, so login never flashes.
                        pendingApproval = false,
                        pendingApprovalUsername = null,
                        isCheckingApproval = false,
                        serverUrl = serverConfigRepository.getServerUrl(),
                        dataMode = AppDataMode.SERVER,
                        user = sessionUser,
                        error = null,
                        canResetServerTrust = false,
                        pendingApprovalMessage = null,
                        isManualSyncing = false,
                        aiSummaryEnabled = settingsRepository.isAiSummaryEnabledSnapshot(),
                        isOffline = sessionResult.isOffline,
                        pendingMutationCount = syncMetadata.pendingMutationCount,
                        lastSuccessfulSyncEpochMs = syncMetadata.lastSuccessfulSyncEpochMs,
                        lastSyncAttemptEpochMs = syncMetadata.lastSyncAttemptEpochMs,
                        offlineNoticeId = if (shouldShowOfflineNotice) {
                            it.offlineNoticeId + 1L
                        } else {
                            it.offlineNoticeId
                        },
                        versionCheckResult = vs.versionCheckResult,
                        backendVersion = vs.backendVersion,
                        requiredUpdateRelease = vs.requiredUpdateRelease,
                        isCheckingUpdateRelease = vs.isCheckingUpdateRelease,
                    )
                }
                ensureResyncLoop(authenticated = true)
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
                        dataMode = AppDataMode.SERVER,
                        user = null,
                        error = null,
                        canResetServerTrust = false,
                        pendingApprovalMessage = null,
                        isManualSyncing = false,
                        aiSummaryEnabled = true,
                        versionCheckResult = vs.versionCheckResult,
                        backendVersion = vs.backendVersion,
                        requiredUpdateRelease = vs.requiredUpdateRelease,
                        isCheckingUpdateRelease = vs.isCheckingUpdateRelease,
                    )
                }
                return@launch
            }

            authRepository.clearSessionOnly()

            // No active session. If a pending-approval marker exists, silently re-attempt
            // login: an approved account falls through to a fresh authenticated bootstrap;
            // otherwise show the persistent holding screen.
            val pending = authRepository.loadPendingApproval()
            if (pending != null) {
                val (pendingUser, pendingPass) = pending
                // Only silently re-attempt login on the first launch this process —
                // never on a later bootstrap, so an explicit logout can't be undone.
                if (!hasAttemptedLaunchReauth) {
                    hasAttemptedLaunchReauth = true
                    if (authRepository.login(pendingUser, pendingPass) is AuthResult.Success) {
                        authRepository.clearPendingApproval()
                        // Keep the holding screen visible through the re-bootstrap; the
                        // authenticated branch clears it on session restore (no login flash).
                        _uiState.update {
                            it.copy(pendingApproval = true, pendingApprovalUsername = pendingUser)
                        }
                        bootstrap()
                        return@launch
                    }
                }
                _uiState.update {
                    it.copy(
                        loading = false,
                        authenticated = false,
                        requiresServerSetup = false,
                        requiresLogin = false,
                        pendingApproval = true,
                        pendingApprovalUsername = pendingUser,
                        isCheckingApproval = false,
                        serverUrl = serverConfigRepository.getServerUrl(),
                        dataMode = AppDataMode.SERVER,
                        user = null,
                        error = null,
                        canResetServerTrust = false,
                        pendingApprovalMessage = null,
                        isManualSyncing = false,
                        aiSummaryEnabled = true,
                    )
                }
                ensureResyncLoop(authenticated = false)
                return@launch
            }

            _uiState.update {
                it.copy(
                    loading = false,
                    authenticated = false,
                    requiresServerSetup = false,
                    requiresLogin = true,
                    pendingApproval = false,
                    serverUrl = serverConfigRepository.getServerUrl(),
                    dataMode = AppDataMode.SERVER,
                    user = null,
                    error = null,
                    canResetServerTrust = false,
                    pendingApprovalMessage = null,
                    isManualSyncing = false,
                    aiSummaryEnabled = true,
                )
            }
            ensureResyncLoop(authenticated = false)
        }
    }

    /** Persist the pending marker and switch to the holding screen after a register/login
     *  that returned "pending approval". */
    fun enterPendingApproval(username: String, password: String) {
        authRepository.savePendingApproval(username, password)
        _uiState.update {
            it.copy(
                pendingApproval = true,
                pendingApprovalUsername = username,
                authenticated = false,
                requiresLogin = false,
                isCheckingApproval = false,
            )
        }
    }

    /** Re-attempt login with the stored pending credentials; on approval, route to Home
     *  via a fresh bootstrap. */
    fun checkPendingApproval() {
        if (_uiState.value.isCheckingApproval) return
        val pending = authRepository.loadPendingApproval() ?: run {
            _uiState.update { it.copy(pendingApproval = false) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isCheckingApproval = true) }
            val result = authRepository.login(pending.first, pending.second)
            if (result is AuthResult.Success) {
                authRepository.clearPendingApproval()
                // Keep the holding screen up through the re-bootstrap; the authenticated
                // branch clears it on session restore, so login never flashes.
                _uiState.update { it.copy(isCheckingApproval = false) }
                bootstrap()
            } else {
                _uiState.update { it.copy(isCheckingApproval = false) }
            }
        }
    }

    /** Abandon the pending account and return to sign-in / onboarding. */
    fun cancelPendingApproval() {
        authRepository.clearPendingApproval()
        _uiState.update {
            it.copy(pendingApproval = false, pendingApprovalUsername = null, requiresLogin = true)
        }
    }

    fun useLocalMode() {
        viewModelScope.launch {
            TdayTelemetry.addBreadcrumb("local_mode.enter")
            runCatching { authRepository.clearAllLocalUserDataForUnauthenticatedState() }
            runCatching { systemCredentialService.clearCredentialState() }
            runCatching { reminderScheduler.cancelAll() }
            serverConfigRepository.enableLocalMode()
            enterLocalWorkspace()
        }
    }

    private suspend fun enterLocalWorkspace() {
        ensureResyncLoop(authenticated = false)
        runCatching {
            cacheManager.updateOfflineState { state ->
                state.copy(
                    lastSuccessfulSyncEpochMs = 0L,
                    lastSyncAttemptEpochMs = 0L,
                    pendingMutations = emptyList(),
                )
            }
        }
        _uiState.update {
            it.copy(
                loading = false,
                authenticated = false,
                requiresServerSetup = false,
                requiresLogin = false,
                serverUrl = null,
                dataMode = AppDataMode.LOCAL,
                user = null,
                error = null,
                canResetServerTrust = false,
                pendingApprovalMessage = null,
                isManualSyncing = false,
                aiSummaryEnabled = true,
                isOffline = false,
                pendingMutationCount = 0,
                lastSuccessfulSyncEpochMs = 0L,
                lastSyncAttemptEpochMs = 0L,
                versionCheckResult = VersionCheckResult.Compatible,
                backendVersion = null,
                requiredUpdateRelease = null,
                isCheckingUpdateRelease = false,
            )
        }
        viewModelScope.launch(Dispatchers.Default) {
            runCatching { reminderScheduler.rescheduleAll() }
        }
    }

    private suspend fun restoreSessionAndPrimeData(): SessionBootstrapResult? {
        val restored = authRepository.restoreSessionForBootstrap() ?: return null
        val user = restored.user
        if (user.id == null) return null

        val syncResult = coroutineScope {
            val sync = async {
                syncManager.syncCachedData(
                    force = true,
                    replayPendingMutations = true,
                    notifyOfflineFailure = false,
                    connectionProbeTimeoutMs = if (restored.usedCachedSession) {
                        SyncManager.USER_REFRESH_CONNECTION_TIMEOUT_MS
                    } else {
                        null
                    },
                )
            }
            launch { runCatching { authRepository.syncTimezone() } }
            launch(Dispatchers.Default) { runCatching { reminderScheduler.rescheduleAll() } }
            sync.await()
        }

        val syncError = syncResult.exceptionOrNull()
        return SessionBootstrapResult(
            user = user,
            isOffline = syncError != null && isLikelyConnectivityIssue(syncError),
        )
    }

    fun refreshSession() = bootstrap()

    fun refreshAiSummaryPreference() {
        if (_uiState.value.isLocalMode) return
        viewModelScope.launch {
            val enabled = settingsRepository.refreshAiSummaryPreference()
            _uiState.update {
                if (it.aiSummaryEnabled == enabled) it else it.copy(aiSummaryEnabled = enabled)
            }
        }
    }

    fun setAiSummaryEnabled(enabled: Boolean) {
        val previous = _uiState.value.aiSummaryEnabled
        if (previous == enabled) return

        _uiState.update { it.copy(aiSummaryEnabled = enabled) }
        viewModelScope.launch {
            runCatching { settingsRepository.setAiSummaryEnabled(enabled) }
                .onFailure { error ->
                    android.util.Log.e("AppViewModel", "AI summary toggle failed", error)
                    _uiState.update { it.copy(aiSummaryEnabled = previous) }
                }
        }
    }

    // MARK: Account (profile name + password)

    /// Saves a new display name, then re-fetches the session so the change is
    /// confirmed by the server and reflected app-wide (the profile card observes
    /// `uiState.user`).
    suspend fun updateDisplayName(newName: String): ProfileEditResult {
        val trimmed = newName.trim()
        val state = _uiState.value
        if (state.isLocalMode || !state.authenticated) {
            return ProfileEditResult.Error(appContext.getString(R.string.settings_account_not_signed_in))
        }
        if (trimmed.isEmpty()) {
            return ProfileEditResult.Error(appContext.getString(R.string.settings_account_name_required))
        }
        return runCatching {
            authRepository.updateProfileName(trimmed)
            val refreshed = runCatching { authRepository.restoreSession() }.getOrNull()
            _uiState.update { it.copy(user = refreshed ?: it.user?.copy(name = trimmed)) }
            ProfileEditResult.Success
        }.getOrElse { error ->
            ProfileEditResult.Error(error.accountEditMessage(R.string.error_update_name_failed))
        }
    }

    /// Changes the password (the server enforces the strength rules and rotates
    /// the session cookie on the same response), then re-fetches the session.
    suspend fun changePassword(currentPassword: String, newPassword: String): ProfileEditResult {
        val state = _uiState.value
        if (state.isLocalMode || !state.authenticated) {
            return ProfileEditResult.Error(appContext.getString(R.string.settings_account_not_signed_in))
        }
        return runCatching {
            authRepository.changePassword(currentPassword, newPassword)
            runCatching { authRepository.restoreSession() }.getOrNull()?.let { refreshed ->
                _uiState.update { it.copy(user = refreshed) }
            }
            ProfileEditResult.Success
        }.getOrElse { error ->
            ProfileEditResult.Error(error.accountEditMessage(R.string.error_change_password_failed))
        }
    }

    /// The signed-in user's security-question status (chosen ids + whether they
    /// still need to be set), or null when unavailable / not in server mode.
    suspend fun securityQuestionStatus(): SecurityQuestionStatusResponse? {
        val state = _uiState.value
        if (state.isLocalMode || !state.authenticated) return null
        return runCatching { authRepository.getUserSecurityQuestionStatus() }.getOrNull()
    }

    /// The full catalogue of security questions for the settings pickers.
    suspend fun fetchSecurityQuestions(): List<SecurityQuestion> =
        runCatching { authRepository.fetchAllSecurityQuestions() }.getOrElse { emptyList() }

    /// Replaces the user's security questions. When they're already configured the
    /// server requires the current password (passed through here); a blank password
    /// is sent as null so the first-time path still works.
    suspend fun updateSecurityQuestions(
        currentPassword: String,
        answers: List<SecurityAnswerInput>,
    ): ProfileEditResult {
        val state = _uiState.value
        if (state.isLocalMode || !state.authenticated) {
            return ProfileEditResult.Error(appContext.getString(R.string.settings_account_not_signed_in))
        }
        return runCatching {
            authRepository.setSecurityQuestions(answers, currentPassword.ifBlank { null })
            ProfileEditResult.Success
        }.getOrElse { error ->
            ProfileEditResult.Error(error.accountEditMessage(R.string.error_update_security_questions_failed))
        }
    }

    /// Prefers the server-supplied validation message for client-correctable
    /// (4xx) errors — e.g. "current password is incorrect" — and otherwise maps
    /// to a generic user-facing message.
    private fun Throwable.accountEditMessage(@androidx.annotation.StringRes fallbackRes: Int): String {
        if (this is ApiCallException && (statusCode == 400 || statusCode == 422) && !message.isNullOrBlank()) {
            return message!!
        }
        return userFacingMessage(appContext, fallbackRes)
    }

    fun saveServerUrl(
        rawUrl: String,
        onSuccess: (String) -> Unit,
        onFailure: (String) -> Unit = {},
    ) {
        viewModelScope.launch {
            TdayTelemetry.addBreadcrumb("server.probe", data = mapOf("phase" to "start"))
            val result = probeAndSaveWithAutomaticTrustRecovery(rawUrl)
            result.onSuccess { probeResult ->
                TdayTelemetry.addBreadcrumb(
                    "server.probe",
                    data = mapOf("phase" to "success", "version" to probeResult.versionCheck::class.simpleName),
                )
                val versionResult = probeResult.versionCheck
                val isBlocking = versionResult is VersionCheckResult.AppUpdateRequired ||
                    versionResult is VersionCheckResult.ServerUpdateRequired
                _uiState.update {
                    it.copy(
                        requiresServerSetup = false,
                        requiresLogin = !isBlocking,
                        serverUrl = probeResult.serverUrl,
                        dataMode = AppDataMode.SERVER,
                        error = null,
                        canResetServerTrust = false,
                        pendingApprovalMessage = null,
                        versionCheckResult = versionResult,
                        backendVersion = probeResult.backendVersion,
                    )
                }
                onSuccess(probeResult.serverUrl)
            }.onFailure { error ->
                TdayTelemetry.addBreadcrumb(
                    "server.probe",
                    level = io.sentry.SentryLevel.WARNING,
                    data = mapOf("phase" to "failure", "error" to error.javaClass.simpleName),
                )
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
        if (_uiState.value.isLocalMode) return
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
            if (_uiState.value.isLocalMode) {
                appVersionManager.refreshGitHubReleases()
            } else {
                appVersionManager.refreshAll()
            }
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
                val message = error.userFacingMessage(appContext, R.string.error_reset_trusted_server_failed)
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
            // Hard-clear the pending-approval marker + holding state so an explicit
            // logout can never be undone by the silent re-login on the next launch.
            runCatching { authRepository.clearPendingApproval() }
            runCatching { systemCredentialService.clearCredentialState() }
            runCatching { reminderScheduler.cancelAll() }
            ensureResyncLoop(authenticated = false)
            _uiState.update {
                it.copy(
                    authenticated = false,
                    requiresServerSetup = true,
                    requiresLogin = false,
                    pendingApproval = false,
                    pendingApprovalUsername = null,
                    isCheckingApproval = false,
                    serverUrl = null,
                    dataMode = AppDataMode.UNSET,
                    user = null,
                    error = null,
                    loading = false,
                    canResetServerTrust = false,
                    pendingApprovalMessage = null,
                    isManualSyncing = false,
                    aiSummaryEnabled = true,
                    isOffline = false,
                    pendingMutationCount = 0,
                    lastSuccessfulSyncEpochMs = 0L,
                    lastSyncAttemptEpochMs = 0L,
                    versionCheckResult = VersionCheckResult.Compatible,
                    backendVersion = null,
                    requiredUpdateRelease = null,
                    isCheckingUpdateRelease = false,
                )
            }
        }
    }

    /**
     * Forcibly ends a server session whose authentication has expired and routes the
     * user back to login. Unlike [logout] this keeps the configured server so the
     * onboarding overlay lands on the sign-in step rather than server setup. No-op if a
     * session is no longer active, which dedupes a burst of 401s into a single expiry.
     */
    private fun expireSession() {
        if (!_uiState.value.authenticated) return
        viewModelScope.launch {
            runCatching { authRepository.logout() }
            runCatching { systemCredentialService.clearCredentialState() }
            runCatching { reminderScheduler.cancelAll() }
            ensureResyncLoop(authenticated = false)
            _uiState.update {
                it.copy(
                    authenticated = false,
                    requiresLogin = true,
                    requiresServerSetup = false,
                    user = null,
                    error = null,
                    loading = false,
                    pendingApprovalMessage = null,
                    isManualSyncing = false,
                    isOffline = false,
                    pendingMutationCount = 0,
                )
            }
            snackbarManager.show(
                SnackbarEvent(
                    message = appContext.getString(R.string.error_auth_expired),
                    kind = SnackbarKind.ERROR,
                ),
            )
        }
    }

    fun syncNow() {
        if (!_uiState.value.authenticated || _uiState.value.isLocalMode) return
        if (_uiState.value.isManualSyncing) return

        viewModelScope.launch {
            TdayTelemetry.addBreadcrumb("sync.manual", data = mapOf("phase" to "start"))
            _uiState.update { it.copy(isManualSyncing = true) }
            val result = recoverSessionAndRetrySyncIfNeeded(
                after = syncManager.syncCachedData(
                    force = true,
                    replayPendingMutations = true,
                    notifyOfflineFailure = false,
                    connectionProbeTimeoutMs = SyncManager.USER_REFRESH_CONNECTION_TIMEOUT_MS,
                ),
                connectionProbeTimeoutMs = SyncManager.USER_REFRESH_CONNECTION_TIMEOUT_MS,
            )
            val syncError = result.exceptionOrNull()
            val isOffline = syncError != null &&
                    shouldTreatSyncFailureAsOffline(
                        error = syncError,
                        suppressAuthenticationExpired = true,
                    )
            val shouldShowOfflineNotice = isOffline && offlineNoticeCooldown.shouldShowNotice()
            val syncMetadata = syncMetadataSnapshot(AppDataMode.SERVER)
            _uiState.update {
                it.copy(
                    isManualSyncing = false,
                    isOffline = isOffline,
                    offlineNoticeId = if (shouldShowOfflineNotice) {
                        it.offlineNoticeId + 1L
                    } else {
                        it.offlineNoticeId
                    },
                    pendingMutationCount = syncMetadata.pendingMutationCount,
                    lastSuccessfulSyncEpochMs = syncMetadata.lastSuccessfulSyncEpochMs,
                    lastSyncAttemptEpochMs = syncMetadata.lastSyncAttemptEpochMs,
                )
            }
            if (syncError == null && !realtimeClient.isConnected && _uiState.value.authenticated) {
                realtimeClient.connect()
            }
            syncError?.let {
                classifyAndShowError(
                    error = it,
                    suppressAuthenticationExpired = true,
                )
            }
            launch(Dispatchers.Default) { runCatching { reminderScheduler.rescheduleAll() } }
        }
    }

    fun reconnectAfterForeground() {
        if (!_uiState.value.authenticated || _uiState.value.isLocalMode) return
        if (foregroundReconnectJob?.isActive == true) return

        foregroundReconnectJob = viewModelScope.launch {
            val result = syncAndUpdateOfflineState(
                replayPending = true,
                markOfflineOnConnectivityFailure = false,
                connectionProbeTimeoutMs = SyncManager.USER_REFRESH_CONNECTION_TIMEOUT_MS,
                suppressAuthenticationExpired = true,
            )
            val syncError = result.exceptionOrNull()
            if (syncError == null || !isLikelyConnectivityIssue(syncError)) return@launch

            delay(FOREGROUND_RECONNECT_OFFLINE_GRACE_MS)
            if (!_uiState.value.authenticated) return@launch

            syncAndUpdateOfflineState(
                replayPending = true,
                showOfflineNotice = true,
                connectionProbeTimeoutMs = SyncManager.USER_REFRESH_CONNECTION_TIMEOUT_MS,
                suppressAuthenticationExpired = true,
            )
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

    fun setDayAhead(option: DayAheadOption) {
        dayAheadPreferenceStore.setOption(option)
        _uiState.update { it.copy(selectedDayAhead = option) }
        DayAheadScheduling.scheduleNext(appContext, option)
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
            foregroundReconnectJob?.cancel()
            foregroundReconnectJob = null
            realtimeClient.disconnect()
            return
        }

        startRealtimeListener()
        startConnectivityListener()

        if (resyncJob?.isActive == true) return

        resyncJob = viewModelScope.launch {
            while (isActive) {
                val hasPending = runCatching { syncManager.hasPendingMutations() }.getOrDefault(false)
                val syncMetadata = syncMetadataSnapshot(AppDataMode.SERVER)
                _uiState.update {
                    it.copy(
                        pendingMutationCount = syncMetadata.pendingMutationCount,
                        lastSuccessfulSyncEpochMs = syncMetadata.lastSuccessfulSyncEpochMs,
                        lastSyncAttemptEpochMs = syncMetadata.lastSyncAttemptEpochMs,
                    )
                }

                val delayMs = if (hasPending) PENDING_RESYNC_INTERVAL_MS else RESYNC_INTERVAL_MS
                delay(delayMs)

                syncAndUpdateOfflineState(
                    replayPending = hasPending,
                    suppressAuthenticationExpired = true,
                )
            }
        }
    }

    private suspend fun syncAndUpdateOfflineState(
        replayPending: Boolean,
        showOfflineNotice: Boolean = false,
        connectionProbeTimeoutMs: Long? = null,
        markOfflineOnConnectivityFailure: Boolean = true,
        suppressAuthenticationExpired: Boolean = false,
    ): Result<Unit> {
        val result = recoverSessionAndRetrySyncIfNeeded(
            after = syncManager.syncCachedData(
                force = true,
                replayPendingMutations = replayPending,
                notifyOfflineFailure = false,
                connectionProbeTimeoutMs = connectionProbeTimeoutMs,
            ),
            connectionProbeTimeoutMs = connectionProbeTimeoutMs,
        )
        val syncError = result.exceptionOrNull()
        val isOffline = syncError != null &&
                shouldTreatSyncFailureAsOffline(
                    error = syncError,
                    suppressAuthenticationExpired = suppressAuthenticationExpired,
                )
        val shouldDeferOfflineState = syncError != null &&
                isLikelyConnectivityIssue(syncError) &&
                !markOfflineOnConnectivityFailure
        val shouldShowOfflineNotice = isOffline &&
                showOfflineNotice &&
                !shouldDeferOfflineState &&
                offlineNoticeCooldown.shouldShowNotice()
        val syncMetadata = syncMetadataSnapshot(AppDataMode.SERVER)
        _uiState.update {
            it.copy(
                isOffline = when {
                    syncError == null -> false
                    shouldDeferOfflineState -> it.isOffline
                    isOffline -> true
                    else -> false
                },
                offlineNoticeId = if (shouldShowOfflineNotice) {
                    it.offlineNoticeId + 1L
                } else {
                    it.offlineNoticeId
                },
                pendingMutationCount = syncMetadata.pendingMutationCount,
                lastSuccessfulSyncEpochMs = syncMetadata.lastSuccessfulSyncEpochMs,
                lastSyncAttemptEpochMs = syncMetadata.lastSyncAttemptEpochMs,
            )
        }
        if (syncError != null) {
            classifyAndShowError(
                error = syncError,
                suppressAuthenticationExpired = suppressAuthenticationExpired,
            )
        } else if (!realtimeClient.isConnected && _uiState.value.authenticated) {
            realtimeClient.connect()
        }
        viewModelScope.launch(Dispatchers.Default) { runCatching { reminderScheduler.rescheduleAll() } }
        return result
    }

    private suspend fun recoverSessionAndRetrySyncIfNeeded(
        after: Result<Unit>,
        connectionProbeTimeoutMs: Long?,
    ): Result<Unit> {
        val error = after.exceptionOrNull() ?: return after
        if (!isSessionAuthenticationIssue(error)) return after

        val restoredSession = authRepository.restoreSessionForBootstrap()
        if (restoredSession == null) {
            // A confirmed 401 that silent recovery could not heal, and not a mere
            // connectivity blip, means the session is truly gone — expire it and send
            // the user back to login. Connectivity issues stay in offline mode.
            if (!isLikelyConnectivityIssue(error) && !_uiState.value.isLocalMode) {
                expireSession()
            }
            return after
        }
        _uiState.update {
            it.copy(
                authenticated = true,
                requiresServerSetup = false,
                requiresLogin = false,
                serverUrl = serverConfigRepository.getServerUrl(),
                dataMode = AppDataMode.SERVER,
                user = restoredSession.user,
                error = null,
                pendingApprovalMessage = null,
                isOffline = restoredSession.usedCachedSession,
            )
        }

        if (restoredSession.usedCachedSession) {
            return after
        }

        return syncManager.syncCachedData(
            force = true,
            replayPendingMutations = true,
            notifyOfflineFailure = false,
            connectionProbeTimeoutMs = connectionProbeTimeoutMs,
        )
    }

    private fun shouldTreatSyncFailureAsOffline(
        error: Throwable,
        suppressAuthenticationExpired: Boolean,
    ): Boolean {
        return isLikelyConnectivityIssue(error) ||
                (suppressAuthenticationExpired && isSessionAuthenticationIssue(error))
    }

    private fun classifyAndShowError(
        error: Throwable,
        suppressAuthenticationExpired: Boolean = false,
    ) {
        if (isVersionGateError(error)) {
            viewModelScope.launch { appVersionManager.refreshServerCompatibility() }
            return
        }
        if (shouldTreatSyncFailureAsOffline(error, suppressAuthenticationExpired)) return
        snackbarManager.showError(error.userFacingMessage(appContext)) {
            if (error !is ApiCallException || error.statusCode != 401) syncNow()
        }
    }

    private fun isVersionGateError(error: Throwable): Boolean {
        var current: Throwable? = error
        while (current != null) {
            if (current is ApiCallException) {
                return current.statusCode == 426 ||
                        current.reason == "app_update_required" ||
                        current.reason == "server_update_required"
            }
            current = current.cause?.takeIf { it !== current }
        }
        return false
    }

    private fun startRealtimeListener() {
        if (realtimeJob?.isActive == true) return

        realtimeClient.connect()
        realtimeJob = viewModelScope.launch {
            realtimeClient.events.collect { event ->
                when (event) {
                    is RealtimeEvent.Connected -> {
                        if (_uiState.value.isOffline) {
                            syncAndUpdateOfflineState(
                                replayPending = true,
                                suppressAuthenticationExpired = true,
                            )
                        }
                    }
                    is RealtimeEvent.TodoChanged,
                    is RealtimeEvent.FloaterChanged,
                    is RealtimeEvent.ListChanged,
                    is RealtimeEvent.CompletedChanged,
                    -> {
                        syncAndUpdateOfflineState(
                            replayPending = false,
                            suppressAuthenticationExpired = true,
                        )
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
                    syncAndUpdateOfflineState(
                        replayPending = true,
                        connectionProbeTimeoutMs = SyncManager.USER_REFRESH_CONNECTION_TIMEOUT_MS,
                        suppressAuthenticationExpired = true,
                    )
                    if (!realtimeClient.isConnected) {
                        realtimeClient.connect()
                    }
                }
            }
        }
    }

    private fun observeOfflineSyncFailures() {
        viewModelScope.launch {
            syncManager.offlineSyncFailures.collect {
                if (_uiState.value.authenticated) {
                    syncAndUpdateOfflineState(
                        replayPending = true,
                        showOfflineNotice = true,
                        connectionProbeTimeoutMs = SyncManager.USER_REFRESH_CONNECTION_TIMEOUT_MS,
                        suppressAuthenticationExpired = true,
                    )
                }
            }
        }
    }

    private fun observeOfflineSyncSuccesses() {
        viewModelScope.launch {
            syncManager.offlineSyncSuccesses.collect {
                val syncMetadata = syncMetadataSnapshot(AppDataMode.SERVER)
                _uiState.update {
                    if (!it.authenticated) {
                        it
                    } else {
                        it.copy(
                            isOffline = false,
                            pendingMutationCount = syncMetadata.pendingMutationCount,
                            lastSuccessfulSyncEpochMs = syncMetadata.lastSuccessfulSyncEpochMs,
                            lastSyncAttemptEpochMs = syncMetadata.lastSyncAttemptEpochMs,
                        )
                    }
                }
            }
        }
    }

    private fun observeSyncMetadataChanges() {
        viewModelScope.launch {
            cacheManager.syncMetadataVersion.collect {
                refreshSyncMetadataFromCache()
            }
        }
    }

    private fun refreshSyncMetadataFromCache() {
        val mode = _uiState.value.dataMode
        val syncMetadata = syncMetadataSnapshot(mode)
        _uiState.update {
            it.copy(
                pendingMutationCount = syncMetadata.pendingMutationCount,
                lastSuccessfulSyncEpochMs = syncMetadata.lastSuccessfulSyncEpochMs,
                lastSyncAttemptEpochMs = syncMetadata.lastSyncAttemptEpochMs,
            )
        }
    }

    private fun syncMetadataSnapshot(mode: AppDataMode): SyncMetadataSnapshot {
        if (mode != AppDataMode.SERVER) {
            return SyncMetadataSnapshot(
                pendingMutationCount = 0,
                lastSuccessfulSyncEpochMs = 0L,
                lastSyncAttemptEpochMs = 0L,
            )
        }
        val state = runCatching { cacheManager.loadOfflineStateBlocking() }.getOrNull()
        return SyncMetadataSnapshot(
            pendingMutationCount = state?.pendingMutations?.size
                ?: _uiState.value.pendingMutationCount,
            lastSuccessfulSyncEpochMs = state?.lastSuccessfulSyncEpochMs
                ?: _uiState.value.lastSuccessfulSyncEpochMs,
            lastSyncAttemptEpochMs = state?.lastSyncAttemptEpochMs
                ?: _uiState.value.lastSyncAttemptEpochMs,
        )
    }

    override fun onCleared() {
        super.onCleared()
        realtimeClient.disconnect()
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
            is TimeoutCancellationException -> appContext.getString(R.string.server_setup_error_probe_timeout)
            is ServerProbeException.InvalidUrl -> appContext.getString(R.string.server_setup_error_invalid_url)
            is ServerProbeException.InsecureTransport ->
                appContext.getString(R.string.server_setup_error_insecure_transport)
            is ServerProbeException.NotTdayServer ->
                appContext.getString(R.string.server_setup_error_not_tday_server)
            is AutomaticTrustRefreshFailedException ->
                staleServerTrustMessage()
            is ServerProbeException.CertificateChanged ->
                automaticTrustRefreshMessage()
            is SSLPeerUnverifiedException ->
                if (isServerTrustMismatch(error)) {
                    automaticTrustRefreshMessage()
                } else {
                    error.userFacingMessage(appContext, R.string.error_connect_server_failed)
                }
            else -> error.userFacingMessage(appContext, R.string.error_connect_server_failed)
        }
    }

    private fun isServerTrustMismatch(error: Throwable): Boolean {
        return error is ServerProbeException.CertificateChanged || isPinnedCertificateMismatch(error)
    }

    private fun isPinnedCertificateMismatch(error: Throwable): Boolean {
        return error.message?.contains("Pinned certificate mismatch", ignoreCase = true) == true
    }

    private fun automaticTrustRefreshMessage(): String {
        return appContext.getString(R.string.server_setup_trust_refreshing)
    }

    private fun staleServerTrustMessage(): String {
        return appContext.getString(R.string.server_setup_trust_refresh_failed)
    }

    private class AutomaticTrustRefreshFailedException(
        cause: Throwable?,
    ) : IllegalStateException("Automatic server trust refresh failed.", cause)

    private data class SessionBootstrapResult(
        val user: SessionUser,
        val isOffline: Boolean,
    )

    private companion object {
        const val PENDING_RESYNC_INTERVAL_MS = 20 * 1000L
        const val RESYNC_INTERVAL_MS = 5 * 60 * 1000L
        const val REALTIME_RECONNECT_DELAY_MS = 5_000L
        const val CONNECTIVITY_RESTORED_DEBOUNCE_MS = 1_500L
        const val FOREGROUND_RECONNECT_OFFLINE_GRACE_MS = 3_000L
    }
}
