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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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
)

@HiltViewModel
class AppViewModel @Inject constructor(
    private val repository: TdayRepository,
    private val themePreferenceStore: ThemePreferenceStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    init {
        _uiState.update {
            it.copy(themeMode = themePreferenceStore.getThemeMode())
        }
        bootstrap()
    }

    fun bootstrap() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
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
                    )
                }
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
                    )
                }
                repository.syncTimezone()
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
                            )
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
                            )
                        }
                        return@launch
                    }

                    is com.ohmz.tday.compose.core.model.AuthResult.Error -> {
                        // fall through to login screen
                    }
                }
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
                )
            }
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
            _uiState.update {
                it.copy(
                    authenticated = false,
                    requiresServerSetup = false,
                    user = null,
                    error = null,
                    loading = false,
                    canResetServerTrust = false,
                    pendingApprovalMessage = null,
                )
            }
        }
    }

    fun setThemeMode(mode: AppThemeMode) {
        themePreferenceStore.setThemeMode(mode)
        _uiState.update { it.copy(themeMode = mode) }
    }

    fun clearPendingApprovalNotice() {
        _uiState.update { it.copy(pendingApprovalMessage = null) }
    }

    private fun toServerSetupMessage(error: Throwable): String {
        return when (error) {
            is TimeoutCancellationException -> "Server probe timed out. Check URL and network, then try again."
            is ServerProbeException.InvalidUrl -> "Invalid server URL"
            is ServerProbeException.InsecureTransport -> error.message ?: "Use HTTPS for remote server URLs."
            is ServerProbeException.NotTdayServer ->
                "Server is reachable but does not expose a valid Tday auth probe."
            is ServerProbeException.AuthContractMismatch ->
                "Server probe is incomplete. Required auth endpoints are missing."
            is ServerProbeException.CertificateChanged ->
                error.message ?: "Server certificate changed. Reset trusted server to continue."
            else -> error.message ?: "Could not connect to server"
        }
    }
}
