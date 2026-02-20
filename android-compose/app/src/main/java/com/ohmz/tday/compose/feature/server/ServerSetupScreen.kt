package com.ohmz.tday.compose.feature.server

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.ohmz.tday.compose.feature.auth.AuthUiState

private enum class SetupStep {
    SERVER,
    LOGIN,
}

private enum class WizardStatus {
    SERVER,
    CONNECTING,
    LOGIN,
    AUTHENTICATING,
}

@Composable
fun ServerSetupScreen(
    initialServerUrl: String?,
    serverErrorMessage: String?,
    authUiState: AuthUiState,
    onConnectServer: (String, (Result<Unit>) -> Unit) -> Unit,
    onLogin: (String, String) -> Unit,
    onNavigateRegister: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    var setupStep by rememberSaveable(initialServerUrl) {
        mutableStateOf(
            if (initialServerUrl.isNullOrBlank()) SetupStep.SERVER else SetupStep.LOGIN,
        )
    }
    var serverUrl by rememberSaveable { mutableStateOf(initialServerUrl.orEmpty()) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var serverError by rememberSaveable { mutableStateOf<String?>(null) }
    var isConnecting by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(initialServerUrl) {
        if (!initialServerUrl.isNullOrBlank()) {
            if (serverUrl.isBlank()) {
                serverUrl = initialServerUrl
            }
            if (!isConnecting) {
                setupStep = SetupStep.LOGIN
            }
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

    LaunchedEffect(serverErrorMessage) {
        if (!serverErrorMessage.isNullOrBlank() && setupStep == SetupStep.SERVER) {
            serverError = serverErrorMessage
        }
    }

    val wizardStatus = when {
        isConnecting -> WizardStatus.CONNECTING
        authUiState.isLoading -> WizardStatus.AUTHENTICATING
        setupStep == SetupStep.LOGIN -> WizardStatus.LOGIN
        else -> WizardStatus.SERVER
    }

    Scaffold(
        containerColor = colorScheme.background,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
            contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Tday",
                        style = MaterialTheme.typography.headlineLarge,
                        color = colorScheme.onBackground,
                        fontWeight = FontWeight.Bold,
                    )

                    Row(
                        modifier = Modifier
                            .background(
                                color = colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(26.dp),
                            )
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Language,
                            contentDescription = null,
                            tint = colorScheme.onSurface,
                        )
                        Icon(
                            imageVector = Icons.Rounded.Lock,
                            contentDescription = null,
                            tint = colorScheme.onSurface,
                        )
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SetupMiniTile(
                        modifier = Modifier.weight(1f),
                        color = Color(0xFF6EA8E1),
                        title = "Server",
                        subtitle = if (setupStep == SetupStep.SERVER) "Required" else "Connected",
                        active = setupStep == SetupStep.SERVER,
                    )
                    SetupMiniTile(
                        modifier = Modifier.weight(1f),
                        color = Color(0xFFD48A8C),
                        title = "Login",
                        subtitle = if (setupStep == SetupStep.LOGIN) "Sign in" else "Next step",
                        active = setupStep == SetupStep.LOGIN,
                    )
                }
            }

            item {
                Card(
                    shape = RoundedCornerShape(26.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = lerp(colorScheme.surfaceVariant, colorScheme.onSurface, 0.2f),
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 14.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.14f),
                                        Color.White.copy(alpha = 0.06f),
                                        Color.Transparent,
                                    ),
                                ),
                            )
                            .padding(18.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Language,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(124.dp)
                                .graphicsLayer(alpha = 0.22f),
                            contentDescription = null,
                            tint = Color.White,
                        )

                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Icon(
                                    imageVector = if (setupStep == SetupStep.SERVER) {
                                        Icons.Rounded.Language
                                    } else {
                                        Icons.Rounded.Lock
                                    },
                                    contentDescription = null,
                                    tint = Color.White,
                                )
                                Text(
                                    text = if (setupStep == SetupStep.SERVER) {
                                        "Connect to server"
                                    } else {
                                        "Sign in"
                                    },
                                    style = MaterialTheme.typography.titleLarge,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                )
                            }

                            Text(
                                modifier = Modifier.padding(top = 6.dp),
                                text = if (setupStep == SetupStep.SERVER) {
                                    "Enter your workspace domain (example: app.example.com)"
                                } else {
                                    "Continue with your account on this server"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.9f),
                            )

                            AnimatedContent(
                                targetState = wizardStatus,
                                label = "setupWizardState",
                            ) { state ->
                                when (state) {
                                    WizardStatus.SERVER -> {
                                        Column {
                                            OutlinedTextField(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(top = 14.dp),
                                                value = serverUrl,
                                                onValueChange = { serverUrl = it },
                                                label = { Text("Server URL") },
                                                placeholder = { Text("https://app.example.com") },
                                                singleLine = true,
                                                colors = OutlinedTextFieldDefaults.colors(
                                                    focusedBorderColor = Color.Black,
                                                    focusedLabelColor = Color.Black,
                                                    cursorColor = Color.Black,
                                                ),
                                            )

                                            serverError?.let { message ->
                                                Text(
                                                    modifier = Modifier.padding(top = 10.dp),
                                                    text = message,
                                                    color = colorScheme.error,
                                                    style = MaterialTheme.typography.bodySmall,
                                                )
                                            }

                                            Button(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(top = 16.dp),
                                                onClick = {
                                                    val normalized = serverUrl.trim()
                                                    if (normalized.isBlank()) return@Button
                                                    serverError = null
                                                    isConnecting = true
                                                    onConnectServer(normalized) { result ->
                                                        isConnecting = false
                                                        result.onSuccess {
                                                            setupStep = SetupStep.LOGIN
                                                        }.onFailure { error ->
                                                            setupStep = SetupStep.SERVER
                                                            serverError =
                                                                error.message ?: "Could not connect to server"
                                                        }
                                                    }
                                                },
                                                enabled = serverUrl.isNotBlank(),
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = colorScheme.primary,
                                                    contentColor = colorScheme.onPrimary,
                                                ),
                                            ) {
                                                Text("Connect")
                                            }
                                        }
                                    }

                                    WizardStatus.CONNECTING -> {
                                        WizardLoadingState(
                                            title = "Connecting...",
                                            subtitle = "Validating server and syncing workspace settings",
                                        )
                                    }

                                    WizardStatus.LOGIN -> {
                                        Column {
                                            OutlinedTextField(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(top = 14.dp),
                                                value = email,
                                                onValueChange = { email = it },
                                                label = { Text("Email") },
                                                singleLine = true,
                                                colors = OutlinedTextFieldDefaults.colors(
                                                    focusedBorderColor = Color.Black,
                                                    focusedLabelColor = Color.Black,
                                                    cursorColor = Color.Black,
                                                ),
                                            )
                                            OutlinedTextField(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(top = 10.dp),
                                                value = password,
                                                onValueChange = { password = it },
                                                label = { Text("Password") },
                                                visualTransformation = PasswordVisualTransformation(),
                                                singleLine = true,
                                                colors = OutlinedTextFieldDefaults.colors(
                                                    focusedBorderColor = Color.Black,
                                                    focusedLabelColor = Color.Black,
                                                    cursorColor = Color.Black,
                                                ),
                                            )

                                            authUiState.errorMessage?.let { message ->
                                                Text(
                                                    modifier = Modifier.padding(top = 10.dp),
                                                    text = message,
                                                    color = colorScheme.error,
                                                    style = MaterialTheme.typography.bodySmall,
                                                )
                                            }
                                            authUiState.infoMessage?.let { message ->
                                                Text(
                                                    modifier = Modifier.padding(top = 10.dp),
                                                    text = message,
                                                    color = colorScheme.tertiary,
                                                    style = MaterialTheme.typography.bodySmall,
                                                )
                                            }

                                            Button(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(top = 16.dp),
                                                onClick = { onLogin(email.trim(), password) },
                                                enabled = email.isNotBlank() && password.isNotBlank(),
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = colorScheme.primary,
                                                    contentColor = colorScheme.onPrimary,
                                                ),
                                            ) {
                                                Text("Sign in")
                                            }

                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(top = 4.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                TextButton(onClick = onNavigateRegister) {
                                                    Text("Create account", color = Color.White)
                                                }
                                                TextButton(onClick = { setupStep = SetupStep.SERVER }) {
                                                    Text("Change server", color = Color.White)
                                                }
                                            }
                                        }
                                    }

                                    WizardStatus.AUTHENTICATING -> {
                                        WizardLoadingState(
                                            title = "Authenticating...",
                                            subtitle = "Securing session and loading your workspace",
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        tint = colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = "Connection and credentials are stored securely on this device",
                        color = colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun WizardLoadingState(
    title: String,
    subtitle: String,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "wizardProgress")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(animation = tween(1200, easing = LinearEasing)),
        label = "wizardRotation",
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = Icons.Rounded.Language,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier
                .size(34.dp)
                .graphicsLayer(rotationZ = rotation),
        )
        CircularProgressIndicator(
            modifier = Modifier.size(22.dp),
            color = Color.White,
            strokeWidth = 2.5.dp,
        )
        Text(
            text = title,
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = subtitle,
            color = Color.White.copy(alpha = 0.9f),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun SetupMiniTile(
    modifier: Modifier,
    color: Color,
    title: String,
    subtitle: String,
    active: Boolean,
) {
    val tileColor = if (active) color else lerp(color, Color.Black, 0.28f)
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (active) 10.dp else 4.dp),
        colors = CardDefaults.cardColors(containerColor = tileColor),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.9f),
            )
        }
    }
}
