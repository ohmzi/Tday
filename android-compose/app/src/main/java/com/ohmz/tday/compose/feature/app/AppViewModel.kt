package com.ohmz.tday.compose.feature.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ohmz.tday.compose.core.data.TdayRepository
import com.ohmz.tday.compose.core.data.ThemePreferenceStore
import com.ohmz.tday.compose.core.model.SessionUser
import com.ohmz.tday.compose.ui.theme.AppThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
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
                            )
                        }
                        return@launch
                    }

                    is com.ohmz.tday.compose.core.model.AuthResult.Error,
                    com.ohmz.tday.compose.core.model.AuthResult.PendingApproval -> {
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
                )
            }
        }
    }

    fun refreshSession() = bootstrap()

    fun saveServerUrl(
        rawUrl: String,
        onSuccess: () -> Unit,
    ) {
        viewModelScope.launch {
            val result = repository.saveServerUrl(rawUrl)
            result.onSuccess { normalized ->
                _uiState.update {
                    it.copy(
                        requiresServerSetup = false,
                        serverUrl = normalized,
                        error = null,
                    )
                }
                onSuccess()
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        error = error.message ?: "Invalid server URL",
                    )
                }
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
                )
            }
        }
    }

    fun setThemeMode(mode: AppThemeMode) {
        themePreferenceStore.setThemeMode(mode)
        _uiState.update { it.copy(themeMode = mode) }
    }
}
