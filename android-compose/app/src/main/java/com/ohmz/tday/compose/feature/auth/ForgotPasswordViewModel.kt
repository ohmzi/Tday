package com.ohmz.tday.compose.feature.auth

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ohmz.tday.compose.R
import com.ohmz.tday.compose.core.data.auth.AuthRepository
import com.ohmz.tday.compose.core.model.PasswordResetOutcome
import com.ohmz.tday.compose.core.model.SecurityAnswerInput
import com.ohmz.tday.compose.core.model.SecurityQuestion
import com.ohmz.tday.compose.core.model.VerifyAnswersOutcome
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// Staged reset wizard, mirroring iOS/Web:
//   USERNAME  -> look up the account's stored questions
//   CHALLENGE -> answer the 2 shown questions; verified against the server before proceeding
//   PASSWORD  -> only reachable once the answers verify; set a new password
//   SUCCESS   -> "Password changed"; the caller returns to sign in after a short pause
//   LOCKED / REQUESTED -> too many misses -> ask an administrator
enum class ForgotPasswordStep {
    USERNAME,
    CHALLENGE,
    PASSWORD,
    SUCCESS,
    LOCKED,
    REQUESTED,
}

data class ForgotPasswordUiState(
    val step: ForgotPasswordStep = ForgotPasswordStep.USERNAME,
    val username: String = "",
    val questions: List<SecurityQuestion> = emptyList(),
    // The (up to) two question ids currently shown; cycled in on a wrong answer.
    val shownIds: List<Int> = emptyList(),
    val isBusy: Boolean = false,
    val errorMessage: String? = null,
) {
    val shownQuestions: List<SecurityQuestion>
        get() = shownIds.mapNotNull { id -> questions.firstOrNull { it.id == id } }
}

@HiltViewModel
class ForgotPasswordViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ForgotPasswordUiState())
    val uiState: StateFlow<ForgotPasswordUiState> = _uiState.asStateFlow()

    private var failedAttempts = 0
    private var verifiedAnswers: List<SecurityAnswerInput> = emptyList()

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    // Start the flow over (called when the reset panel is (re)opened from the login dialog).
    fun reset() {
        failedAttempts = 0
        verifiedAnswers = emptyList()
        _uiState.value = ForgotPasswordUiState()
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
                            shownIds = listOf(questions[0].id, questions[1].id),
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

    // Step 2: verify the answers only. Advances to the password step once correct.
    fun verifyChallenge(answers: List<SecurityAnswerInput>) {
        val state = _uiState.value
        if (state.isBusy) return
        if (answers.any { it.answer.isBlank() }) {
            _uiState.update {
                it.copy(errorMessage = appContext.getString(R.string.security_questions_answers_required))
            }
            return
        }
        _uiState.update { it.copy(isBusy = true, errorMessage = null) }
        viewModelScope.launch {
            when (val outcome = authRepository.verifyAnswers(state.username, answers)) {
                VerifyAnswersOutcome.Valid -> {
                    verifiedAnswers = answers
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            step = ForgotPasswordStep.PASSWORD,
                            errorMessage = null
                        )
                    }
                }

                is VerifyAnswersOutcome.Invalid -> {
                    failedAttempts += 1
                    val wrongIds = outcome.results.filter { !it.correct }.map { it.questionId }
                    cycleFailedQuestions(wrongIds.ifEmpty { state.shownIds })
                    val baseMessage = appContext.getString(R.string.forgot_password_reset_failed)
                    val message = if (failedAttempts > 2) {
                        appContext.getString(
                            R.string.forgot_password_reset_failed_contact_admin,
                            baseMessage,
                        )
                    } else {
                        baseMessage
                    }
                    _uiState.update { it.copy(isBusy = false, errorMessage = message) }
                }

                VerifyAnswersOutcome.Locked -> {
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            step = ForgotPasswordStep.LOCKED,
                            errorMessage = null
                        )
                    }
                }

                is VerifyAnswersOutcome.Error -> {
                    _uiState.update { it.copy(isBusy = false, errorMessage = outcome.message) }
                }
            }
        }
    }

    // Swap each wrongly-answered question for a random not-yet-shown one (cycling the 3rd
    // question in). With only two stored questions there's nothing to swap.
    private fun cycleFailedQuestions(wrongIds: List<Int>) {
        val state = _uiState.value
        val pool = state.questions.map { it.id }.filter { it !in state.shownIds }.toMutableList()
        if (pool.isEmpty()) return
        val next = state.shownIds.map { id ->
            if (id in wrongIds && pool.isNotEmpty()) {
                pool.removeAt((pool.indices).random())
            } else {
                id
            }
        }
        _uiState.update { it.copy(shownIds = next) }
    }

    // Step 3: only reachable once the answers verify.
    fun submitReset(newPassword: String) {
        val state = _uiState.value
        if (state.isBusy) return
        if (verifiedAnswers.isEmpty()) return

        _uiState.update { it.copy(isBusy = true, errorMessage = null) }
        viewModelScope.launch {
            val outcome = runCatching {
                authRepository.resetPassword(
                    username = state.username,
                    answers = verifiedAnswers,
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
                        it.copy(
                            isBusy = false,
                            step = ForgotPasswordStep.SUCCESS,
                            errorMessage = null
                        )
                    }
                }

                PasswordResetOutcome.Locked -> {
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            step = ForgotPasswordStep.LOCKED,
                            errorMessage = null
                        )
                    }
                }

                is PasswordResetOutcome.Failed -> {
                    // Answers no longer line up (e.g. cycled mid-flight) — back to the questions.
                    verifiedAnswers = emptyList()
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            step = ForgotPasswordStep.CHALLENGE,
                            errorMessage = appContext.getString(R.string.forgot_password_reenter_answers),
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
