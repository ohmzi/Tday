package com.ohmz.tday.compose.feature.auth

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ohmz.tday.compose.R
import com.ohmz.tday.compose.core.model.SecurityAnswerInput
import com.ohmz.tday.compose.feature.onboarding.WizardHeroTile
import kotlinx.coroutines.delay

private const val RESET_RETURN_DELAY_MS = 2000L

/**
 * Staged self-service reset flow, rendered bare so it lives inside the login dialog's
 * card (reusing the same dialog, like the create-account panel does).
 *   username  -> look up the account's questions
 *   challenge -> answer them; verified before proceeding
 *   password  -> set a new password
 *   success   -> "Password changed"; auto-returns to sign in after a short pause (OK skips it)
 *   locked / requested -> too many misses -> ask an administrator
 */
@Composable
fun ForgotPasswordPanel(
    initialUsername: String,
    onBackToLogin: () -> Unit,
    onResetComplete: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ForgotPasswordViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val colorScheme = MaterialTheme.colorScheme

    // Fresh start each time the panel is opened from the dialog.
    LaunchedEffect(Unit) { viewModel.reset() }

    var username by rememberSaveable { mutableStateOf(initialUsername) }
    var newPassword by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }
    var localError by rememberSaveable { mutableStateOf<String?>(null) }
    // Answer text per shown question; cleared whenever the shown questions cycle.
    val answers = remember(uiState.shownIds) { mutableStateMapOf<Int, String>() }

    val passwordMinError = stringResource(R.string.onboarding_validation_password_min)
    val passwordUppercaseError = stringResource(R.string.onboarding_validation_password_uppercase)
    val passwordSpecialError = stringResource(R.string.onboarding_validation_password_special)
    val passwordMismatchError = stringResource(R.string.onboarding_validation_password_mismatch)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Same red hero tile the Sign in panel uses, so the reset flow reads as the same dialog.
        WizardHeroTile(
            title = stringResource(R.string.forgot_password_title),
            imageVector = ImageVector.vectorResource(R.drawable.ic_lucide_shield),
            color = Color(0xFFC97880),
        )

        when (uiState.step) {
            ForgotPasswordStep.USERNAME -> {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = username,
                    onValueChange = {
                        username = it
                        viewModel.clearError()
                    },
                    label = { Text(stringResource(R.string.onboarding_username_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    shape = RoundedCornerShape(22.dp),
                )
                ErrorText(uiState.errorMessage)
                PrimaryButton(
                    label = stringResource(R.string.forgot_password_continue),
                    enabled = username.isNotBlank() && !uiState.isBusy,
                    busy = uiState.isBusy,
                    onClick = { viewModel.lookupQuestions(username) },
                )
            }

            ForgotPasswordStep.CHALLENGE -> {
                uiState.shownQuestions.forEach { question ->
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = answers[question.id].orEmpty(),
                        onValueChange = {
                            answers[question.id] = it
                            viewModel.clearError()
                        },
                        label = { Text(question.text) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        shape = RoundedCornerShape(22.dp),
                    )
                }
                ErrorText(uiState.errorMessage)
                PrimaryButton(
                    label = stringResource(R.string.forgot_password_verify),
                    enabled = uiState.shownIds.all { answers[it].orEmpty().isNotBlank() } &&
                            !uiState.isBusy,
                    busy = uiState.isBusy,
                    onClick = {
                        val payload = uiState.shownIds.map { id ->
                            SecurityAnswerInput(
                                questionId = id,
                                answer = answers[id].orEmpty().trim()
                            )
                        }
                        viewModel.verifyChallenge(payload)
                    },
                )
            }

            ForgotPasswordStep.PASSWORD -> {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = newPassword,
                    onValueChange = {
                        newPassword = it
                        localError = null
                    },
                    label = { Text(stringResource(R.string.forgot_password_new_password)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    shape = RoundedCornerShape(22.dp),
                )
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = confirmPassword,
                    onValueChange = {
                        confirmPassword = it
                        localError = null
                    },
                    label = { Text(stringResource(R.string.onboarding_confirm_password_label)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    shape = RoundedCornerShape(22.dp),
                )
                ErrorText(localError ?: uiState.errorMessage)
                PrimaryButton(
                    label = stringResource(R.string.forgot_password_reset),
                    enabled = newPassword.isNotBlank() && confirmPassword.isNotBlank() &&
                            !uiState.isBusy,
                    busy = uiState.isBusy,
                    onClick = {
                        val error = when {
                            newPassword.length < 8 -> passwordMinError
                            !newPassword.any { it.isUpperCase() } -> passwordUppercaseError
                            !newPassword.any { !it.isLetterOrDigit() } -> passwordSpecialError
                            newPassword != confirmPassword -> passwordMismatchError
                            else -> null
                        }
                        if (error != null) {
                            localError = error
                        } else {
                            localError = null
                            viewModel.submitReset(newPassword)
                        }
                    },
                )
            }

            ForgotPasswordStep.SUCCESS -> {
                LaunchedEffect(Unit) {
                    delay(RESET_RETURN_DELAY_MS)
                    onResetComplete(uiState.username)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = ImageVector.vectorResource(R.drawable.ic_lucide_circle_check_big),
                        contentDescription = null,
                        tint = colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                    Text(
                        text = stringResource(R.string.forgot_password_success_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = colorScheme.onSurface,
                    )
                }
                PrimaryButton(
                    label = stringResource(R.string.forgot_password_ok),
                    enabled = true,
                    busy = false,
                    onClick = { onResetComplete(uiState.username) },
                )
            }

            ForgotPasswordStep.LOCKED -> {
                Text(
                    text = stringResource(R.string.forgot_password_subtitle_locked),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.onSurface.copy(alpha = 0.7f),
                )
                ErrorText(uiState.errorMessage)
                Button(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    enabled = !uiState.isBusy,
                    onClick = { viewModel.requestAdminReset() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colorScheme.error,
                        contentColor = colorScheme.onError,
                    ),
                ) {
                    BusyContent(
                        uiState.isBusy,
                        stringResource(R.string.forgot_password_request_admin)
                    )
                }
            }

            ForgotPasswordStep.REQUESTED -> {
                Text(
                    text = stringResource(R.string.forgot_password_subtitle_requested),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.onSurface.copy(alpha = 0.7f),
                )
                ErrorText(uiState.errorMessage)
            }
        }

        if (uiState.step != ForgotPasswordStep.SUCCESS &&
            uiState.step != ForgotPasswordStep.REQUESTED
        ) {
            TextButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = onBackToLogin,
            ) {
                Icon(
                    imageVector = ImageVector.vectorResource(R.drawable.ic_lucide_arrow_left),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    modifier = Modifier.padding(start = 6.dp),
                    text = stringResource(R.string.forgot_password_back_to_sign_in),
                )
            }
        }
    }
}

@Composable
private fun PrimaryButton(
    label: String,
    enabled: Boolean,
    busy: Boolean,
    onClick: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    Button(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        enabled = enabled,
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = colorScheme.primary,
            contentColor = colorScheme.onPrimary,
        ),
    ) {
        BusyContent(busy, label)
    }
}

@Composable
private fun ErrorText(message: String?) {
    message?.let {
        Text(
            text = it,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun BusyContent(busy: Boolean, label: String) {
    if (busy) {
        CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.onPrimary,
        )
    } else {
        Text(label)
    }
}
