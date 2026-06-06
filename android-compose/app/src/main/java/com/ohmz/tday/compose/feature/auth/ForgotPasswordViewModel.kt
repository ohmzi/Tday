package com.ohmz.tday.compose.feature.auth

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ohmz.tday.compose.R
import com.ohmz.tday.compose.core.data.auth.AuthRepository
import com.ohmz.tday.compose.core.model.PasswordResetOutcome
import com.ohmz.tday.compose.core.model.SecurityAnswerInput
import com.ohmz.tday.compose.core.model.SecurityQuestion
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ForgotPasswordStep {
    USERNAME,
    CHALLENGE,
    LOCKED,
    REQUESTED,
}

data class ForgotPasswordUiState(
    val step: ForgotPasswordStep = ForgotPasswordStep.USERNAME,
    val username: String = "",
    val questions: List<SecurityQuestion> = emptyList(),
    val isBusy: Boolean = false,
    val errorMessage: String? = null,
    val resetSucceeded: Boolean = false,
)

@HiltViewModel
class ForgotPasswordViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ForgotPasswordUiState())
    val uiState: StateFlow<ForgotPasswordUiState> = _uiState.asStateFlow()

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun lookupQuestions(rawUsername: String) {
        val normalized = rawUsername.trim()
        if (normalized.isEmpty()) {
            _uiState.update {
                it.copy(errorMessage = appContext.getString(R.string.forgot_password_username_required))
            }
            return
        }
        if (_uiState.value.isBusy) return
        _uiState.update { it.copy(isBusy = true, errorMessage = null, username = normalized) }
        viewModelScope.launch {
            runCatching {
                authRepository.fetchQuestionsForUsername(normalized)
            }.onSuccess { questions ->
                if (questions.size < 2) {
                    // The server always returns two questions; fail safe otherwise.
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            errorMessage = appContext.getString(R.string.forgot_password_questions_unavailable),
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            questions = questions,
                            step = ForgotPasswordStep.CHALLENGE,
                        )
                    }
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        errorMessage = error.message
                            ?: appContext.getString(R.string.forgot_password_questions_unavailable),
                    )
                }
            }
        }
    }

    fun submitReset(answer1: String, answer2: String, newPassword: String) {
        val state = _uiState.value
        if (state.isBusy) return
        val questions = state.questions
        if (questions.size < 2) return

        _uiState.update { it.copy(isBusy = true, errorMessage = null) }
        viewModelScope.launch {
            val answers = listOf(
                SecurityAnswerInput(questionId = questions[0].id, answer = answer1.trim()),
                SecurityAnswerInput(questionId = questions[1].id, answer = answer2.trim()),
            )
            val outcome = runCatching {
                authRepository.resetPassword(
                    username = state.username,
                    answers = answers,
                    newPassword = newPassword,
                )
            }.getOrElse { error ->
                PasswordResetOutcome.Failed(
                    error.message ?: appContext.getString(R.string.forgot_password_reset_failed),
                )
            }

            when (outcome) {
                PasswordResetOutcome.Success -> {
                    _uiState.update {
                        it.copy(isBusy = false, resetSucceeded = true, errorMessage = null)
                    }
                }

                PasswordResetOutcome.Locked -> {
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            step = ForgotPasswordStep.LOCKED,
                            errorMessage = null,
                        )
                    }
                }

                is PasswordResetOutcome.Failed -> {
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            errorMessage = appContext.getString(R.string.forgot_password_reset_failed),
                        )
                    }
                }
            }
        }
    }

    fun requestAdminReset() {
        val state = _uiState.value
        if (state.isBusy) return
        _uiState.update { it.copy(isBusy = true, errorMessage = null) }
        viewModelScope.launch {
            runCatching {
                authRepository.requestAdminReset(state.username)
            }.onSuccess {
                _uiState.update {
                    it.copy(isBusy = false, step = ForgotPasswordStep.REQUESTED)
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        errorMessage = error.message
                            ?: appContext.getString(R.string.forgot_password_admin_request_failed),
                    )
                }
            }
        }
    }
}
