package com.ohmz.tday.compose.feature.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.ohmz.tday.compose.feature.auth.AuthUiState

private enum class WizardStep {
    SERVER,
    LOGIN,
}

private enum class WizardViewState {
    SERVER,
    CONNECTING,
    LOGIN,
    AUTHENTICATING,
}

@Composable
fun OnboardingWizardOverlay(
    initialServerUrl: String?,
    serverErrorMessage: String?,
    authUiState: AuthUiState,
    onConnectServer: (String, (Result<Unit>) -> Unit) -> Unit,
    onLogin: (String, String) -> Unit,
    onCreateAccount: () -> Unit,
    onClearAuthStatus: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val consumeAllTouchesSource = remember { MutableInteractionSource() }

    var step by rememberSaveable(initialServerUrl) {
        mutableStateOf(if (initialServerUrl.isNullOrBlank()) WizardStep.SERVER else WizardStep.LOGIN)
    }
    var serverUrl by rememberSaveable { mutableStateOf(initialServerUrl.orEmpty()) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var serverError by rememberSaveable { mutableStateOf<String?>(null) }
    var isConnecting by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(initialServerUrl) {
        if (!initialServerUrl.isNullOrBlank()) {
            if (serverUrl.isBlank()) serverUrl = initialServerUrl
            if (!isConnecting) step = WizardStep.LOGIN
        }
    }

    LaunchedEffect(serverErrorMessage) {
        if (!serverErrorMessage.isNullOrBlank() && step == WizardStep.SERVER) {
            serverError = serverErrorMessage
        }
    }

    LaunchedEffect(authUiState.savedEmail, authUiState.savedPassword) {
        if (email.isBlank() && authUiState.savedEmail.isNotBlank()) {
            email = authUiState.savedEmail
        }
        if (password.isBlank() && authUiState.savedPassword.isNotBlank()) {
            password = authUiState.savedPassword
        }
    }

    val viewState = when {
        isConnecting -> WizardViewState.CONNECTING
        authUiState.isLoading -> WizardViewState.AUTHENTICATING
        step == WizardStep.LOGIN -> WizardViewState.LOGIN
        else -> WizardViewState.SERVER
    }

    val focusColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = Color.Black,
        focusedLabelColor = Color.Black,
        cursorColor = Color.Black,
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.25f))
            .clickable(
                interactionSource = consumeAllTouchesSource,
                indication = null,
                onClick = {},
            ),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 460.dp)
                .padding(horizontal = 18.dp),
            shape = RoundedCornerShape(32.dp),
            colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 22.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .drawWithCache {
                        val wash = Brush.linearGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.16f),
                                Color.White.copy(alpha = 0.06f),
                                Color.Transparent,
                            ),
                        )
                        onDrawWithContent {
                            drawRect(wash)
                            drawContent()
                        }
                    }
                    .padding(18.dp),
            ) {
                Icon(
                    imageVector = if (step == WizardStep.SERVER) Icons.Rounded.Language else Icons.Rounded.Lock,
                    contentDescription = null,
                    tint = lerp(colorScheme.surfaceVariant, colorScheme.primary, 0.3f).copy(alpha = 0.35f),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(130.dp),
                )

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Set Up Tday",
                        style = MaterialTheme.typography.headlineSmall,
                        color = colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Secure onboarding wizard",
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant,
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        WizardStepChip(
                            modifier = Modifier.weight(1f),
                            title = "Server",
                            color = Color(0xFF6EA8E1),
                            active = step == WizardStep.SERVER,
                        )
                        WizardStepChip(
                            modifier = Modifier.weight(1f),
                            title = "Login",
                            color = Color(0xFFD48A8C),
                            active = step == WizardStep.LOGIN,
                        )
                    }

                    AnimatedContent(targetState = viewState, label = "wizardState") { state ->
                        when (state) {
                            WizardViewState.SERVER -> {
                                Column {
                                    OutlinedTextField(
                                        modifier = Modifier.fillMaxWidth(),
                                        value = serverUrl,
                                        onValueChange = {
                                            serverUrl = it
                                            serverError = null
                                        },
                                        label = { Text("Server URL") },
                                        placeholder = { Text("https://app.example.com") },
                                        singleLine = true,
                                        colors = focusColors,
                                    )

                                    serverError?.let { message ->
                                        Text(
                                            modifier = Modifier.padding(top = 8.dp),
                                            text = message,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colorScheme.error,
                                        )
                                    }

                                    Button(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 14.dp),
                                        enabled = serverUrl.isNotBlank(),
                                        onClick = {
                                            val value = serverUrl.trim()
                                            if (value.isBlank()) return@Button
                                            serverError = null
                                            isConnecting = true
                                            onConnectServer(value) { result ->
                                                isConnecting = false
                                                result.onSuccess {
                                                    step = WizardStep.LOGIN
                                                    onClearAuthStatus()
                                                }.onFailure { error ->
                                                    step = WizardStep.SERVER
                                                    serverError =
                                                        error.message ?: "Could not connect to server"
                                                }
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = colorScheme.primary,
                                            contentColor = colorScheme.onPrimary,
                                        ),
                                    ) {
                                        Text("Connect")
                                    }
                                }
                            }

                            WizardViewState.CONNECTING -> {
                                WizardLoading(
                                    title = "Connecting to server...",
                                    subtitle = "Checking endpoint, TLS, and workspace settings",
                                )
                            }

                            WizardViewState.LOGIN -> {
                                Column {
                                    OutlinedTextField(
                                        modifier = Modifier.fillMaxWidth(),
                                        value = email,
                                        onValueChange = {
                                            email = it
                                            onClearAuthStatus()
                                        },
                                        label = { Text("Email") },
                                        singleLine = true,
                                        colors = focusColors,
                                    )
                                    OutlinedTextField(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 10.dp),
                                        value = password,
                                        onValueChange = {
                                            password = it
                                            onClearAuthStatus()
                                        },
                                        label = { Text("Password") },
                                        singleLine = true,
                                        visualTransformation = PasswordVisualTransformation(),
                                        colors = focusColors,
                                    )

                                    authUiState.errorMessage?.let { message ->
                                        Text(
                                            modifier = Modifier.padding(top = 8.dp),
                                            text = message,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colorScheme.error,
                                        )
                                    }
                                    authUiState.infoMessage?.let { message ->
                                        Text(
                                            modifier = Modifier.padding(top = 8.dp),
                                            text = message,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colorScheme.tertiary,
                                        )
                                    }

                                    Button(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 14.dp),
                                        enabled = email.isNotBlank() && password.isNotBlank() && !authUiState.isLoading,
                                        onClick = {
                                            onClearAuthStatus()
                                            onLogin(email.trim(), password)
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = colorScheme.primary,
                                            contentColor = colorScheme.onPrimary,
                                        ),
                                    ) {
                                        Text("Sign in")
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        TextButton(onClick = onCreateAccount) {
                                            Text("Create account")
                                        }
                                        TextButton(
                                            onClick = {
                                                step = WizardStep.SERVER
                                                onClearAuthStatus()
                                            },
                                        ) {
                                            Text("Change server")
                                        }
                                    }
                                }
                            }

                            WizardViewState.AUTHENTICATING -> {
                                WizardLoading(
                                    title = "Authenticating...",
                                    subtitle = "Encrypting session and loading your workspace",
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WizardLoading(
    title: String,
    subtitle: String,
) {
    val transition = rememberInfiniteTransition(label = "wizardLoading")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1300, easing = LinearEasing)),
        label = "wizardRotation",
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = Icons.Rounded.Language,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(34.dp)
                .graphicsLayer(rotationZ = rotation),
        )
        CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            strokeWidth = 2.5.dp,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun WizardStepChip(
    modifier: Modifier,
    title: String,
    color: Color,
    active: Boolean,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = color),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (title == "Server") Icons.Rounded.Language else Icons.Rounded.Person,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = title,
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }
    }
}
