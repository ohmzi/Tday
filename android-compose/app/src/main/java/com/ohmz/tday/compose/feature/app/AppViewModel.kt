package com.ohmz.tday.compose.feature.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ohmz.tday.compose.core.data.ServerProbeException
import com.ohmz.tday.compose.core.data.TdayRepository
import com.ohmz.tday.compose.core.data.ThemePreferenceStore
import com.ohmz.tday.compose.core.model.SessionUser
import com.ohmz.tday.compose.ui.theme.AppThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

data class AppUiState(
    val loading: Boolean = true,
    val authenticated: Boolean = false,
    val requiresServerSetup: Boolean = false,
    val serverUrl: String? = null,
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val user: SessionUser? = null,
    val error: String? = null,
    val canResetServerTrust: Boolean = false,
    val pendingApprovalMessage: String? = null,
    val isManualSyncing: Boolean = false,
)

@HiltViewModel
class AppViewModel @Inject constructor(
    private val repository: TdayRepository,
    private val themePreferenceStore: ThemePreferenceStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()
    private var resyncJob: Job? = null

    init {
        _uiState.update {
            it.copy(themeMode = themePreferenceStore.getThemeMode())
        }
        bootstrap()
    }

    fun bootstrap() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null, isManualSyncing = false) }
            if (!repository.hasServerConfigured()) {
                _uiState.update {
                    it.copy(
                        loading = false,
                        authenticated = false,
                        requiresServerSetup = true,
                        serverUrl = null,
                        user = null,
                        error = null,
                        canResetServerTrust = false,
                        pendingApprovalMessage = null,
                        isManualSyncing = false,
                    )
                }
                ensureResyncLoop(authenticated = false)
                return@launch
            }

            val sessionUser = runCatching { repository.restoreSession() }.getOrNull()
            if (sessionUser?.id != null) {
                _uiState.update {
                    it.copy(
                        loading = false,
                        authenticated = true,
                        requiresServerSetup = false,
                        serverUrl = repository.getServerUrl(),
                        user = sessionUser,
                        error = null,
                        canResetServerTrust = false,
                        pendingApprovalMessage = null,
                        isManualSyncing = false,
                    )
                }
                ensureResyncLoop(authenticated = true)
                launchStartupSync()
                return@launch
            }

            val savedCredentials = repository.getSavedCredentials()
            if (savedCredentials != null) {
                when (
                    repository.login(
                        email = savedCredentials.email,
                        password = savedCredentials.password,
                    )
                ) {
                    com.ohmz.tday.compose.core.model.AuthResult.Success -> {
                        val authed = repository.restoreSession()
                        _uiState.update {
                            it.copy(
                                loading = false,
                                authenticated = authed?.id != null,
                                requiresServerSetup = false,
                                serverUrl = repository.getServerUrl(),
                                user = authed,
                                error = null,
                                canResetServerTrust = false,
                                pendingApprovalMessage = null,
                                isManualSyncing = false,
                            )
                        }
                        ensureResyncLoop(authenticated = authed?.id != null)
                        if (authed?.id != null) {
                            launchStartupSync()
                        }
                        return@launch
                    }

                    com.ohmz.tday.compose.core.model.AuthResult.PendingApproval -> {
                        _uiState.update {
                            it.copy(
                                loading = false,
                                authenticated = false,
                                requiresServerSetup = false,
                                serverUrl = repository.getServerUrl(),
                                user = null,
                                error = null,
                                canResetServerTrust = false,
                                pendingApprovalMessage = "Account pending admin approval.",
                                isManualSyncing = false,
                            )
                        }
                        ensureResyncLoop(authenticated = false)
                        return@launch
                    }

                    is com.ohmz.tday.compose.core.model.AuthResult.Error -> {
                        // fall through to login screen
                    }
                }
            }

            if (savedCredentials != null && repository.hasCachedData()) {
                val offlineName = savedCredentials.email
                    .substringBefore('@')
                    .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                    .ifBlank { "Offline" }
                _uiState.update {
                    it.copy(
                        loading = false,
                        authenticated = true,
                        requiresServerSetup = false,
                        serverUrl = repository.getServerUrl(),
                        user = SessionUser(
                            id = "offline-cached-user",
                            name = offlineName,
                            email = savedCredentials.email,
                        ),
                        error = null,
                        canResetServerTrust = false,
                        pendingApprovalMessage = null,
                        isManualSyncing = false,
                    )
                }
                ensureResyncLoop(authenticated = true)
                launchStartupSync()
                return@launch
            }

            _uiState.update {
                it.copy(
                    loading = false,
                    authenticated = false,
                    requiresServerSetup = false,
                    serverUrl = repository.getServerUrl(),
                    user = null,
                    error = null,
                    canResetServerTrust = false,
                    pendingApprovalMessage = null,
                    isManualSyncing = false,
                )
            }
            ensureResyncLoop(authenticated = false)
        }
    }

    fun refreshSession() = bootstrap()

    fun saveServerUrl(
        rawUrl: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit = {},
    ) {
        viewModelScope.launch {
            val result = repository.saveServerUrl(rawUrl)
            result.onSuccess { normalized ->
                _uiState.update {
                    it.copy(
                        requiresServerSetup = false,
                        serverUrl = normalized,
                        error = null,
                        canResetServerTrust = false,
                        pendingApprovalMessage = null,
                    )
                }
                onSuccess()
            }.onFailure { error ->
                val message = toServerSetupMessage(error)
                _uiState.update {
                    it.copy(
                        error = message,
                        canResetServerTrust = error is ServerProbeException.CertificateChanged,
                        pendingApprovalMessage = null,
                    )
                }
                onFailure(message)
            }
        }
    }

    fun resetTrustedServer(
        rawUrl: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit = {},
    ) {
        viewModelScope.launch {
            val result = repository.resetTrustedServer(rawUrl)
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
                val message = error.message ?: "Could not reset trusted server"
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
            runCatching { repository.logout() }
            ensureResyncLoop(authenticated = false)
            _uiState.update {
                it.copy(
                    authenticated = false,
                    requiresServerSetup = false,
                    user = null,
                    error = null,
                    loading = false,
                    canResetServerTrust = false,
                    pendingApprovalMessage = null,
                    isManualSyncing = false,
                )
            }
        }
    }

    fun syncNow() {
        if (!_uiState.value.authenticated) return
        if (_uiState.value.isManualSyncing) return

        viewModelScope.launch {
            _uiState.update { it.copy(isManualSyncing = true) }
            runCatching {
                repository.syncCachedData(
                    force = true,
                    replayPendingMutations = false,
                )
            }
            _uiState.update { it.copy(isManualSyncing = false) }
        }
    }

    fun setThemeMode(mode: AppThemeMode) {
        themePreferenceStore.setThemeMode(mode)
        _uiState.update { it.copy(themeMode = mode) }
    }

    fun clearPendingApprovalNotice() {
        _uiState.update { it.copy(pendingApprovalMessage = null) }
    }

    private fun ensureResyncLoop(authenticated: Boolean) {
        if (!authenticated) {
            resyncJob?.cancel()
            resyncJob = null
            return
        }

        if (resyncJob?.isActive == true) return

        resyncJob = viewModelScope.launch {
            while (isActive) {
                val delayMs = runCatching {
                    if (repository.hasPendingMutations()) {
                        PENDING_RESYNC_INTERVAL_MS
                    } else {
                        RESYNC_INTERVAL_MS
                    }
                }.getOrDefault(RESYNC_INTERVAL_MS)
                delay(delayMs)
                runCatching { repository.syncCachedData(force = true) }
            }
        }
    }

    private fun launchStartupSync() {
        viewModelScope.launch {
            runCatching { repository.syncCachedData(force = true) }
            runCatching { repository.syncTimezone() }
        }
    }

    private fun toServerSetupMessage(error: Throwable): String {
        return when (error) {
            is TimeoutCancellationException -> "Server probe timed out. Check URL and network, then try again."
            is ServerProbeException.InvalidUrl -> "Invalid server URL"
            is ServerProbeException.InsecureTransport -> error.message ?: "Use HTTPS for remote server URLs."
            is ServerProbeException.NotTdayServer ->
                "Server is reachable but does not expose a valid T'Day auth probe."
            is ServerProbeException.AuthContractMismatch ->
                "Server probe is incomplete. Required auth endpoints are missing."
            is ServerProbeException.CertificateChanged ->
                error.message ?: "Server certificate changed. Reset trusted server to continue."
            else -> error.message ?: "Could not connect to server"
        }
    }

    private companion object {
        const val PENDING_RESYNC_INTERVAL_MS = 20 * 1000L
        const val RESYNC_INTERVAL_MS = 5 * 60 * 1000L
    }
}
