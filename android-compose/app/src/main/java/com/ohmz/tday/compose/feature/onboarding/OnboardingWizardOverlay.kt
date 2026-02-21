package com.ohmz.tday.compose.feature.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
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

private enum class AuthPanelMode {
    SIGN_IN,
    CREATE_ACCOUNT,
}

@Composable
fun OnboardingWizardOverlay(
    initialServerUrl: String?,
    serverErrorMessage: String?,
    serverCanResetTrust: Boolean,
    pendingApprovalMessage: String?,
    authUiState: AuthUiState,
    onConnectServer: (String, (Result<Unit>) -> Unit) -> Unit,
    onResetServerTrust: (String, (Result<Unit>) -> Unit) -> Unit,
    onLogin: (String, String) -> Unit,
    onRegister: (
        firstName: String,
        email: String,
        password: String,
        onSuccess: () -> Unit,
    ) -> Unit,
    onClearAuthStatus: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val consumeAllTouchesSource = remember { MutableInteractionSource() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    var step by rememberSaveable(initialServerUrl) {
        mutableStateOf(if (initialServerUrl.isNullOrBlank()) WizardStep.SERVER else WizardStep.LOGIN)
    }
    var serverUrl by rememberSaveable { mutableStateOf(initialServerUrl.orEmpty()) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var firstName by rememberSaveable { mutableStateOf("") }
    var registerPassword by rememberSaveable { mutableStateOf("") }
    var confirmRegisterPassword by rememberSaveable { mutableStateOf("") }
    var authMode by rememberSaveable { mutableStateOf(AuthPanelMode.SIGN_IN) }
    var localAuthError by rememberSaveable { mutableStateOf<String?>(null) }
    var serverError by rememberSaveable { mutableStateOf<String?>(null) }
    var isConnecting by rememberSaveable { mutableStateOf(false) }
    var isResettingTrust by rememberSaveable { mutableStateOf(false) }
    var isRegisterInFlight by rememberSaveable { mutableStateOf(false) }
    val passwordFocusRequester = remember { FocusRequester() }
    val registerPasswordFocusRequester = remember { FocusRequester() }
    val registerConfirmFocusRequester = remember { FocusRequester() }

    val connectToServer: () -> Unit = connect@{
        if (isResettingTrust) return@connect
        val value = serverUrl.trim()
        if (value.isBlank()) return@connect
        keyboardController?.hide()
        focusManager.clearFocus(force = true)
        serverError = null
        isConnecting = true
        onConnectServer(value) { result ->
            isConnecting = false
            result.onSuccess {
                step = WizardStep.LOGIN
                authMode = AuthPanelMode.SIGN_IN
                onClearAuthStatus()
            }.onFailure { error ->
                step = WizardStep.SERVER
                serverError = error.message ?: "Could not connect to server"
            }
        }
    }
    val signIn: () -> Unit = signIn@{
        if (authUiState.isLoading) return@signIn
        val userEmail = email.trim()
        if (userEmail.isBlank() || password.isBlank()) return@signIn
        keyboardController?.hide()
        focusManager.clearFocus(force = true)
        localAuthError = null
        onClearAuthStatus()
        onLogin(userEmail, password)
    }
    val createAccount: () -> Unit = createAccount@{
        if (authUiState.isLoading || isRegisterInFlight) return@createAccount
        val normalizedFirst = firstName.trim()
        val normalizedEmail = email.trim()
        val hasUppercase = registerPassword.any { it.isUpperCase() }
        val hasSpecial = registerPassword.any { !it.isLetterOrDigit() || it == '_' }

        when {
            normalizedFirst.length < 2 -> {
                localAuthError = "First name must be at least 2 characters"
                return@createAccount
            }
            normalizedEmail.isBlank() -> {
                localAuthError = "Email is required"
                return@createAccount
            }
            !EMAIL_REGEX.matches(normalizedEmail) -> {
                localAuthError = "Please enter a valid email address"
                return@createAccount
            }
            registerPassword.isBlank() || confirmRegisterPassword.isBlank() -> {
                localAuthError = "Password and confirmation are required"
                return@createAccount
            }
            registerPassword.length < 8 -> {
                localAuthError = "Password must be at least 8 characters"
                return@createAccount
            }
            !hasUppercase -> {
                localAuthError = "Password must include at least one uppercase letter"
                return@createAccount
            }
            !hasSpecial -> {
                localAuthError = "Password must include at least one special character"
                return@createAccount
            }
            registerPassword != confirmRegisterPassword -> {
                localAuthError = "Passwords do not match"
                return@createAccount
            }
        }

        keyboardController?.hide()
        focusManager.clearFocus(force = true)
        localAuthError = null
        isRegisterInFlight = true
        onClearAuthStatus()
        onRegister(
            normalizedFirst,
            normalizedEmail,
            registerPassword,
        ) {
            isRegisterInFlight = false
            authMode = AuthPanelMode.SIGN_IN
            password = registerPassword
            registerPassword = ""
            confirmRegisterPassword = ""
        }
    }

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
        if (authMode == AuthPanelMode.SIGN_IN && password.isBlank() && authUiState.savedPassword.isNotBlank()) {
            password = authUiState.savedPassword
        }
    }

    LaunchedEffect(authUiState.isLoading) {
        if (!authUiState.isLoading) {
            isRegisterInFlight = false
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
                        text = "Set Up T'Day",
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
                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                                        keyboardActions = KeyboardActions(
                                            onGo = { connectToServer() },
                                            onDone = { connectToServer() },
                                        ),
                                        colors = focusColors,
                                    )

                                    serverError?.let { message ->
                                        Text(
                                            modifier = Modifier.padding(top = 8.dp),
                                            text = message,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colorScheme.error,
                                        )

                                        if (serverCanResetTrust) {
                                            TextButton(
                                                modifier = Modifier.padding(top = 4.dp),
                                                onClick = {
                                                    val value = serverUrl.trim()
                                                    if (value.isBlank()) return@TextButton
                                                    isResettingTrust = true
                                                    onResetServerTrust(value) { resetResult ->
                                                        isResettingTrust = false
                                                        resetResult.onSuccess {
                                                            serverError = null
                                                            isConnecting = true
                                                            onConnectServer(value) { connectResult ->
                                                                isConnecting = false
                                                                connectResult.onSuccess {
                                                                    step = WizardStep.LOGIN
                                                                    onClearAuthStatus()
                                                                }.onFailure { error ->
                                                                    step = WizardStep.SERVER
                                                                    serverError = error.message
                                                                        ?: "Could not connect to server"
                                                                }
                                                            }
                                                        }.onFailure { error ->
                                                            serverError = error.message
                                                                ?: "Could not reset trusted server"
                                                        }
                                                    }
                                                },
                                            ) {
                                                Text(if (isResettingTrust) "Resetting trust..." else "Reset trusted server")
                                            }
                                        }
                                    }

                                    Button(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 14.dp),
                                        enabled = serverUrl.isNotBlank() && !isResettingTrust,
                                        onClick = connectToServer,
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
                                    when (authMode) {
                                        AuthPanelMode.SIGN_IN -> {
                                            OutlinedTextField(
                                                modifier = Modifier.fillMaxWidth(),
                                                value = email,
                                                onValueChange = {
                                                    email = it
                                                    localAuthError = null
                                                    onClearAuthStatus()
                                                },
                                                label = { Text("Email") },
                                                singleLine = true,
                                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                                keyboardActions = KeyboardActions(
                                                    onNext = { passwordFocusRequester.requestFocus() },
                                                ),
                                                colors = focusColors,
                                            )
                                            OutlinedTextField(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(top = 10.dp)
                                                    .focusRequester(passwordFocusRequester),
                                                value = password,
                                                onValueChange = {
                                                    password = it
                                                    localAuthError = null
                                                    onClearAuthStatus()
                                                },
                                                label = { Text("Password") },
                                                singleLine = true,
                                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                                keyboardActions = KeyboardActions(
                                                    onDone = { signIn() },
                                                ),
                                                visualTransformation = PasswordVisualTransformation(),
                                                colors = focusColors,
                                            )

                                            localAuthError?.let { message ->
                                                Text(
                                                    modifier = Modifier.padding(top = 8.dp),
                                                    text = message,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = colorScheme.error,
                                                )
                                            }
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
                                            pendingApprovalMessage
                                                ?.takeIf { it != authUiState.infoMessage }
                                                ?.let { message ->
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
                                                onClick = signIn,
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
                                                TextButton(
                                                    onClick = {
                                                        authMode = AuthPanelMode.CREATE_ACCOUNT
                                                        localAuthError = null
                                                        onClearAuthStatus()
                                                    },
                                                ) {
                                                    Text("Create account")
                                                }
                                                TextButton(
                                                    onClick = {
                                                        step = WizardStep.SERVER
                                                        localAuthError = null
                                                        onClearAuthStatus()
                                                    },
                                                ) {
                                                    Text("Change server")
                                                }
                                            }
                                        }

                                        AuthPanelMode.CREATE_ACCOUNT -> {
                                            OutlinedTextField(
                                                modifier = Modifier.fillMaxWidth(),
                                                value = firstName,
                                                onValueChange = {
                                                    firstName = it
                                                    localAuthError = null
                                                    onClearAuthStatus()
                                                },
                                                label = { Text("First name") },
                                                singleLine = true,
                                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                                keyboardActions = KeyboardActions(
                                                    onNext = { passwordFocusRequester.requestFocus() },
                                                ),
                                                colors = focusColors,
                                            )
                                            OutlinedTextField(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(top = 10.dp)
                                                    .focusRequester(passwordFocusRequester),
                                                value = email,
                                                onValueChange = {
                                                    email = it
                                                    localAuthError = null
                                                    onClearAuthStatus()
                                                },
                                                label = { Text("Email") },
                                                singleLine = true,
                                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                                keyboardActions = KeyboardActions(
                                                    onNext = { registerPasswordFocusRequester.requestFocus() },
                                                ),
                                                colors = focusColors,
                                            )
                                            OutlinedTextField(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(top = 10.dp)
                                                    .focusRequester(registerPasswordFocusRequester),
                                                value = registerPassword,
                                                onValueChange = {
                                                    registerPassword = it
                                                    localAuthError = null
                                                    onClearAuthStatus()
                                                },
                                                label = { Text("Password") },
                                                singleLine = true,
                                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                                keyboardActions = KeyboardActions(
                                                    onNext = { registerConfirmFocusRequester.requestFocus() },
                                                ),
                                                visualTransformation = PasswordVisualTransformation(),
                                                colors = focusColors,
                                            )
                                            OutlinedTextField(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(top = 10.dp)
                                                    .focusRequester(registerConfirmFocusRequester),
                                                value = confirmRegisterPassword,
                                                onValueChange = {
                                                    confirmRegisterPassword = it
                                                    localAuthError = null
                                                    onClearAuthStatus()
                                                },
                                                label = { Text("Confirm password") },
                                                singleLine = true,
                                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                                keyboardActions = KeyboardActions(
                                                    onDone = { createAccount() },
                                                ),
                                                visualTransformation = PasswordVisualTransformation(),
                                                colors = focusColors,
                                            )

                                            localAuthError?.let { message ->
                                                Text(
                                                    modifier = Modifier.padding(top = 8.dp),
                                                    text = message,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = colorScheme.error,
                                                )
                                            }
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
                                                enabled = firstName.isNotBlank() &&
                                                    email.isNotBlank() &&
                                                    registerPassword.isNotBlank() &&
                                                    confirmRegisterPassword.isNotBlank() &&
                                                    !authUiState.isLoading &&
                                                    !isRegisterInFlight,
                                                onClick = createAccount,
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = colorScheme.primary,
                                                    contentColor = colorScheme.onPrimary,
                                                ),
                                            ) {
                                                Text(
                                                    if (authUiState.isLoading || isRegisterInFlight) {
                                                        "Creating account..."
                                                    } else {
                                                        "Create account"
                                                    },
                                                )
                                            }

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                TextButton(
                                                    onClick = {
                                                        authMode = AuthPanelMode.SIGN_IN
                                                        localAuthError = null
                                                        onClearAuthStatus()
                                                    },
                                                ) {
                                                    Text("Back to sign in")
                                                }
                                                TextButton(
                                                    onClick = {
                                                        step = WizardStep.SERVER
                                                        authMode = AuthPanelMode.SIGN_IN
                                                        localAuthError = null
                                                        onClearAuthStatus()
                                                    },
                                                ) {
                                                    Text("Change server")
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            WizardViewState.AUTHENTICATING -> {
                                WizardLoading(
                                    title = if (authMode == AuthPanelMode.CREATE_ACCOUNT) {
                                        "Creating account..."
                                    } else {
                                        "Authenticating..."
                                    },
                                    subtitle = if (authMode == AuthPanelMode.CREATE_ACCOUNT) {
                                        "Submitting registration and waiting for approval status"
                                    } else {
                                        "Encrypting session and loading your workspace"
                                    },
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
    val scale by animateFloatAsState(
        targetValue = if (active) 1.04f else 1f,
        animationSpec = tween(durationMillis = 180),
        label = "wizardStepChipScale",
    )
    val borderWidth by animateDpAsState(
        targetValue = if (active) 2.dp else 0.dp,
        animationSpec = tween(durationMillis = 180),
        label = "wizardStepChipBorderWidth",
    )
    val ringColor = lerp(color, Color.Black, 0.35f)

    Card(
        modifier = modifier.graphicsLayer(
            scaleX = scale,
            scaleY = scale,
        ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = color),
        elevation = CardDefaults.cardElevation(defaultElevation = if (active) 12.dp else 8.dp),
        border = if (active) BorderStroke(borderWidth, ringColor) else null,
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

private val EMAIL_REGEX = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$")
