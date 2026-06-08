package com.ohmz.tday.compose.feature.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

@Composable
fun ForgotPasswordScreen(
    onBackToLogin: () -> Unit,
    onResetComplete: () -> Unit,
    viewModel: ForgotPasswordViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val colorScheme = MaterialTheme.colorScheme

    var username by rememberSaveable { mutableStateOf("") }
    var answer1 by rememberSaveable { mutableStateOf("") }
    var answer2 by rememberSaveable { mutableStateOf("") }
    var newPassword by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }
    var localError by rememberSaveable { mutableStateOf<String?>(null) }

    val passwordMinError = stringResource(R.string.onboarding_validation_password_min)
    val passwordUppercaseError = stringResource(R.string.onboarding_validation_password_uppercase)
    val passwordSpecialError = stringResource(R.string.onboarding_validation_password_special)
    val passwordMismatchError = stringResource(R.string.onboarding_validation_password_mismatch)

    LaunchedEffect(uiState.resetSucceeded) {
        if (uiState.resetSucceeded) {
            onResetComplete()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Matches the login wizard card chrome so the reset screen feels at home.
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 440.dp),
            shape = RoundedCornerShape(34.dp),
            colors = CardDefaults.cardColors(containerColor = colorScheme.background),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
            border = BorderStroke(1.dp, colorScheme.onSurface.copy(alpha = 0.08f)),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = ImageVector.vectorResource(R.drawable.ic_lucide_shield),
                    contentDescription = null,
                    tint = colorScheme.primary,
                    modifier = Modifier.size(32.dp),
                )
                Text(
                    text = stringResource(R.string.forgot_password_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    color = colorScheme.onSurface,
                )
                val subtitleText = when (uiState.step) {
                    ForgotPasswordStep.USERNAME -> null
                    ForgotPasswordStep.CHALLENGE -> null
                    ForgotPasswordStep.LOCKED -> stringResource(R.string.forgot_password_subtitle_locked)
                    ForgotPasswordStep.REQUESTED -> stringResource(R.string.forgot_password_subtitle_requested)
                }
                if (subtitleText != null) {
                    Text(
                        text = subtitleText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                }

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
                        Button(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            enabled = username.isNotBlank() && !uiState.isBusy,
                            onClick = { viewModel.lookupQuestions(username) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colorScheme.primary,
                                contentColor = colorScheme.onPrimary,
                            ),
                        ) {
                            BusyContent(
                                uiState.isBusy,
                                stringResource(R.string.forgot_password_continue)
                            )
                        }
                    }

                    ForgotPasswordStep.CHALLENGE -> {
                        OutlinedTextField(
                            modifier = Modifier.fillMaxWidth(),
                            value = answer1,
                            onValueChange = { answer1 = it },
                            label = { Text(uiState.questions.getOrNull(0)?.text.orEmpty()) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            shape = RoundedCornerShape(22.dp),
                        )
                        OutlinedTextField(
                            modifier = Modifier.fillMaxWidth(),
                            value = answer2,
                            onValueChange = { answer2 = it },
                            label = { Text(uiState.questions.getOrNull(1)?.text.orEmpty()) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            shape = RoundedCornerShape(22.dp),
                        )
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
                        Button(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            enabled = answer1.isNotBlank() &&
                                    answer2.isNotBlank() &&
                                    newPassword.isNotBlank() &&
                                    confirmPassword.isNotBlank() &&
                                    !uiState.isBusy,
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
                                    viewModel.submitReset(answer1, answer2, newPassword)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colorScheme.primary,
                                contentColor = colorScheme.onPrimary,
                            ),
                        ) {
                            BusyContent(
                                uiState.isBusy,
                                stringResource(R.string.forgot_password_reset)
                            )
                        }
                    }

                    ForgotPasswordStep.LOCKED -> {
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
                                stringResource(R.string.forgot_password_request_admin),
                            )
                        }
                    }

                    ForgotPasswordStep.REQUESTED -> {
                        ErrorText(uiState.errorMessage)
                    }
                }

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
