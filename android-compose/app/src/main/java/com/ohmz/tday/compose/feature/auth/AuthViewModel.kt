package com.ohmz.tday.compose.feature.auth

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ohmz.tday.compose.core.data.auth.AuthRepository
import com.ohmz.tday.compose.core.data.auth.LoginCredentialSource
import com.ohmz.tday.compose.core.data.auth.SystemCredential
import com.ohmz.tday.compose.core.data.auth.SystemCredentialSaveResult
import com.ohmz.tday.compose.core.data.auth.SystemCredentialServicing
import com.ohmz.tday.compose.core.model.AuthResult
import com.ohmz.tday.compose.core.ui.SnackbarManager
import dagger.hilt.android.lifecycle.HiltViewModel
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
    val savedEmail: String = "",
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val systemCredentialService: SystemCredentialServicing,
    private val snackbarManager: SnackbarManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        val lastEmail = authRepository.getLastEmail()
        if (!lastEmail.isNullOrBlank()) {
            _uiState.update { it.copy(savedEmail = lastEmail) }
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
        email: String,
        password: String,
        credentialContext: Context,
        source: LoginCredentialSource = LoginCredentialSource.MANUAL,
        onSuccess: () -> Unit,
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, infoMessage = null) }
            val normalizedEmail = email.trim().lowercase(Locale.US)

            val result = runCatching {
                authRepository.login(email = normalizedEmail, password = password)
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
                            savedEmail = normalizedEmail,
                        )
                    }
                    if (source == LoginCredentialSource.MANUAL) {
                        handleCredentialSaveResult(
                            systemCredentialService.offerSaveOrUpdateCredential(
                                context = credentialContext,
                                credential = SystemCredential(
                                    email = normalizedEmail,
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
                            infoMessage = "Account pending admin approval.",
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
        email: String,
        password: String,
        credentialContext: Context,
        onSuccess: () -> Unit,
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, infoMessage = null) }
            val normalizedEmail = email.trim().lowercase(Locale.US)

            val outcome = runCatching {
                authRepository.register(
                    firstName = firstName.trim(),
                    lastName = lastName.trim(),
                    email = normalizedEmail,
                    password = password,
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
                    savedEmail = if (outcome.success) normalizedEmail else it.savedEmail,
                )
            }

            if (outcome.success) {
                handleCredentialSaveResult(
                    systemCredentialService.offerSaveOrUpdateCredential(
                        context = credentialContext,
                        credential = SystemCredential(
                            email = normalizedEmail,
                            password = password,
                        ),
                    ),
                )
                onSuccess()
            }
        }
    }

    suspend fun requestSavedCredential(context: Context): SystemCredential? =
        systemCredentialService.requestSavedCredential(context)

    private fun handleCredentialSaveResult(result: SystemCredentialSaveResult) {
        if (result == SystemCredentialSaveResult.FAILED) {
            snackbarManager.showError(
                "Android Password Manager could not save this login. Check that a password manager is enabled.",
            )
        }
    }

    private fun toFriendlyMessage(message: String?): String {
        val raw = message.orEmpty()
        val lower = raw.lowercase()
        return when {
            lower.contains("invalid email or password") ||
                    lower.contains("incorrect email or password") ||
                    lower.contains("invalid credentials") ->
                "Incorrect email or password"

            lower.contains("127.0.0.1") ||
                    lower.contains("localhost") ||
                    lower.contains("econnrefused") ||
                    lower.contains("failed to connect") ||
                    lower.contains("unable to resolve host") ||
                    lower.contains("timed out") ||
                    lower.contains("network is unreachable") ||
                    lower.contains("not connected") ||
                    lower.contains("connection refused") ||
                    lower.contains("bad gateway") ||
                    lower.contains("service unavailable") ||
                    lower.contains("gateway timeout") ||
                    lower.contains("origin unreachable") ||
                    lower.contains("web server is down") ->
                SERVER_UNREACHABLE_MESSAGE

            lower.contains("serial name") || lower.contains("serializationexception") ||
                    lower.contains("required for type") ->
                "This version of the app is out of date. Please update to continue."
            else -> "Something went wrong. Please try again."
        }
    }

    private companion object {
        const val SERVER_UNREACHABLE_MESSAGE =
            "Cannot reach server. Check your server URL and try again."
    }
}
