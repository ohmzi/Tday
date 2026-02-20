package com.ohmz.tday.compose.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ohmz.tday.compose.core.data.TdayRepository
import com.ohmz.tday.compose.core.model.AuthResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
    val pendingApproval: Boolean = false,
    val savedEmail: String = "",
    val savedPassword: String = "",
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val repository: TdayRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        val saved = repository.getSavedCredentials()
        if (saved != null) {
            _uiState.update {
                it.copy(
                    savedEmail = saved.email,
                    savedPassword = saved.password,
                )
            }
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
        onSuccess: () -> Unit,
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, infoMessage = null) }

            val result = runCatching {
                repository.login(
                    email = email.trim(),
                    password = password,
                )
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
                    _uiState.update { it.copy(isLoading = false, pendingApproval = false) }
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
                            errorMessage = result.message,
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
        onSuccess: () -> Unit,
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, infoMessage = null) }

            val outcome = runCatching {
                repository.register(
                    firstName = firstName.trim(),
                    lastName = lastName.trim(),
                    email = email.trim(),
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
                    errorMessage = if (outcome.success) null else outcome.message,
                    infoMessage = if (outcome.success) outcome.message else null,
                    pendingApproval = outcome.requiresApproval,
                )
            }

            if (outcome.success) {
                onSuccess()
            }
        }
    }

    private fun toFriendlyMessage(message: String?): String {
        val raw = message.orEmpty()
        return when {
            raw.contains("127.0.0.1") || raw.contains("localhost") || raw.contains("ECONNREFUSED") ->
                "Cannot reach backend. Check Server URL (for local Docker emulator use http://10.0.2.2:2525)."
            raw.isNotBlank() -> raw
            else -> "Network error. Please try again."
        }
    }
}
