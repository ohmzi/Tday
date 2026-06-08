package com.ohmz.tday.compose.feature.auth

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ohmz.tday.compose.R
import com.ohmz.tday.compose.core.data.AuthErrorCode
import com.ohmz.tday.compose.core.data.auth.AuthRepository
import com.ohmz.tday.compose.core.data.auth.LoginCredentialSource
import com.ohmz.tday.compose.core.data.auth.SystemCredential
import com.ohmz.tday.compose.core.data.auth.SystemCredentialSaveResult
import com.ohmz.tday.compose.core.data.auth.SystemCredentialServicing
import com.ohmz.tday.compose.core.model.AuthResult
import com.ohmz.tday.compose.core.model.SecurityAnswerInput
import com.ohmz.tday.compose.core.model.SecurityQuestion
import com.ohmz.tday.compose.core.ui.SnackbarManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

data class AuthUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
    val pendingApproval: Boolean = false,
    val savedUsername: String = "",
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val systemCredentialService: SystemCredentialServicing,
    private val snackbarManager: SnackbarManager,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        val lastUsername = authRepository.getLastUsername()
        if (!lastUsername.isNullOrBlank()) {
            _uiState.update { it.copy(savedUsername = lastUsername) }
        }
    }

    fun clearStatus() {
        _uiState.update {
            it.copy(
                errorMessage = null,
                infoMessage = null,
                pendingApproval = false,
            )
        }
    }

    fun setError(message: String) {
        _uiState.update {
            it.copy(
                errorMessage = message,
                infoMessage = null,
                pendingApproval = false,
                isLoading = false,
            )
        }
    }

    fun login(
        username: String,
        password: String,
        credentialContext: Context,
        source: LoginCredentialSource = LoginCredentialSource.MANUAL,
        onSuccess: () -> Unit,
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, infoMessage = null) }
            val normalizedUsername = username.trim().lowercase(Locale.US)

            val result = runCatching {
                authRepository.login(username = normalizedUsername, password = password)
            }.getOrElse { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        pendingApproval = false,
                        errorMessage = toFriendlyMessage(error.message),
                    )
                }
                return@launch
            }

            when (result) {
                AuthResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            pendingApproval = false,
                            savedUsername = normalizedUsername,
                        )
                    }
                    if (source == LoginCredentialSource.MANUAL) {
                        handleCredentialSaveResult(
                            systemCredentialService.offerSaveOrUpdateCredential(
                                context = credentialContext,
                                credential = SystemCredential(
                                    username = normalizedUsername,
                                    password = password,
                                ),
                            ),
                        )
                    }
                    onSuccess()
                }

                AuthResult.PendingApproval -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            pendingApproval = true,
                            errorMessage = null,
                            infoMessage = appContext.getString(R.string.auth_pending_admin_approval),
                        )
                    }
                }

                is AuthResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            pendingApproval = false,
                            errorMessage = toFriendlyMessage(result.message),
                        )
                    }
                }
            }
        }
    }

    fun register(
        firstName: String,
        lastName: String,
        username: String,
        password: String,
        securityAnswers: List<SecurityAnswerInput>,
        credentialContext: Context,
        onSuccess: () -> Unit,
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, infoMessage = null) }
            val normalizedUsername = username.trim().lowercase(Locale.US)

            val outcome = runCatching {
                authRepository.register(
                    firstName = firstName.trim(),
                    lastName = lastName.trim(),
                    username = normalizedUsername,
                    password = password,
                    securityAnswers = securityAnswers,
                )
            }.getOrElse { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = toFriendlyMessage(error.message),
                        infoMessage = null,
                        pendingApproval = false,
                    )
                }
                return@launch
            }

            _uiState.update {
                it.copy(
                    isLoading = false,
                    errorMessage = if (outcome.success) null else toFriendlyMessage(outcome.message),
                    infoMessage = if (outcome.success) outcome.message else null,
                    pendingApproval = outcome.requiresApproval,
                    savedUsername = if (outcome.success) normalizedUsername else it.savedUsername,
                )
            }

            if (outcome.success) {
                handleCredentialSaveResult(
                    systemCredentialService.offerSaveOrUpdateCredential(
                        context = credentialContext,
                        credential = SystemCredential(
                            username = normalizedUsername,
                            password = password,
                        ),
                    ),
                )
                onSuccess()
            }
        }
    }

    suspend fun fetchAllSecurityQuestions(): List<SecurityQuestion> =
        runCatching { authRepository.fetchAllSecurityQuestions() }.getOrElse { emptyList() }

    /**
     * Posts the chosen security questions for the signed-in user (the forced gate).
     * Invokes [onSuccess] only when the server accepts them so the caller can
     * refresh the session and dismiss the gate.
     */
    fun submitSecurityQuestions(
        answers: List<SecurityAnswerInput>,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        viewModelScope.launch {
            runCatching {
                authRepository.setSecurityQuestions(answers)
            }.onSuccess {
                onSuccess()
            }.onFailure { error ->
                onError(
                    error.message ?: appContext.getString(R.string.security_questions_save_failed)
                )
            }
        }
    }

    suspend fun requestSavedCredential(
        context: Context,
        preferredUsername: String?,
    ): SystemCredential? =
        systemCredentialService.requestSavedCredential(
            context = context,
            preferredUsername = preferredUsername,
        )

    suspend fun requestSavedServerUrl(context: Context): String? =
        systemCredentialService.requestSavedServerUrl(context)

    suspend fun offerSaveOrUpdateServerUrl(
        context: Context,
        serverUrl: String,
    ) {
        val result = systemCredentialService.offerSaveOrUpdateServerUrl(
            context = context,
            serverUrl = serverUrl,
        )
        if (result == SystemCredentialSaveResult.FAILED) {
            snackbarManager.showError(
                appContext.getString(R.string.auth_password_manager_save_server_url_failed),
            )
        }
    }

    private fun handleCredentialSaveResult(result: SystemCredentialSaveResult) {
        if (result == SystemCredentialSaveResult.FAILED) {
            snackbarManager.showError(
                appContext.getString(R.string.auth_password_manager_save_login_failed),
            )
        }
    }

    private fun toFriendlyMessage(message: String?): String {
        // Codes emitted by AuthRepository map 1:1 to a specific localized message
        // so the user knows exactly what's wrong (and what to tell an admin).
        when (message) {
            AuthErrorCode.CANNOT_REACH -> return appContext.getString(R.string.error_cannot_reach)
            AuthErrorCode.SERVER_UNAVAILABLE -> return appContext.getString(R.string.error_server_unavailable)
            AuthErrorCode.APP_OUTDATED -> return appContext.getString(R.string.error_app_outdated)
            AuthErrorCode.SERVER_OUTDATED -> return appContext.getString(R.string.error_server_outdated)
        }

        val raw = message.orEmpty()
        val lower = raw.lowercase()
        return when {
            lower.contains("invalid username or password") ||
                    lower.contains("incorrect username or password") ||
                    lower.contains("invalid credentials") ->
                appContext.getString(R.string.auth_error_incorrect_credentials)

            // Server answered but is unhealthy (proxy / gateway / origin down).
            lower.contains("bad gateway") ||
                    lower.contains("service unavailable") ||
                    lower.contains("gateway timeout") ||
                    lower.contains("origin unreachable") ||
                    lower.contains("web server is down") ->
                appContext.getString(R.string.error_server_unavailable)

            // Genuine transport failures — the request never reached the server.
            lower.contains("127.0.0.1") ||
                    lower.contains("localhost") ||
                    lower.contains("econnrefused") ||
                    lower.contains("failed to connect") ||
                    lower.contains("unable to resolve host") ||
                    lower.contains("timed out") ||
                    lower.contains("network is unreachable") ||
                    lower.contains("not connected") ||
                    lower.contains("connection refused") ->
                appContext.getString(R.string.error_cannot_reach)

            lower.contains("serial name") || lower.contains("serializationexception") ||
                    lower.contains("required for type") ->
                appContext.getString(R.string.error_app_outdated)
            else -> appContext.getString(R.string.error_generic)
        }
    }
}
