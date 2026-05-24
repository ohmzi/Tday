package com.ohmz.tday.compose.feature.onboarding

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.AutofillNode
import androidx.compose.ui.autofill.AutofillType
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalAutofill
import androidx.compose.ui.platform.LocalAutofillTree
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.ohmz.tday.compose.R
import com.ohmz.tday.compose.core.data.auth.LoginCredentialSource
import com.ohmz.tday.compose.core.data.auth.SystemCredential
import com.ohmz.tday.compose.feature.auth.AuthUiState
import com.ohmz.tday.compose.feature.auth.LoginCredentialCoordinator
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun OnboardingWizardOverlay(
    initialServerUrl: String?,
    serverErrorMessage: String?,
    serverCanResetTrust: Boolean,
    pendingApprovalMessage: String?,
    authUiState: AuthUiState,
    onConnectServer: (String, (Result<String>) -> Unit) -> Unit,
    onResetServerTrust: (String, (Result<Unit>) -> Unit) -> Unit,
    onLogin: (String, String, LoginCredentialSource) -> Unit,
    onRegister: (
        firstName: String,
        email: String,
        password: String,
        onSuccess: () -> Unit,
    ) -> Unit,
    onRequestSavedCredential: suspend (Context, String?) -> SystemCredential?,
    onRequestSavedServerUrl: suspend (Context) -> String?,
    onSaveServerUrlCredential: suspend (Context, String) -> Unit,
    onClearAuthStatus: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val consumeAllTouchesSource = remember { MutableInteractionSource() }
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val coroutineScope = rememberCoroutineScope()
    val credentialCoordinator = remember { LoginCredentialCoordinator() }

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
    var hasRequestedSavedServerUrl by rememberSaveable { mutableStateOf(false) }
    var serverUrlLoadedFromSystemCredential by rememberSaveable { mutableStateOf(false) }
    var canRequestSavedLoginCredential by rememberSaveable(initialServerUrl) {
        mutableStateOf(!initialServerUrl.isNullOrBlank())
    }
    val passwordFocusRequester = remember { FocusRequester() }
    val registerPasswordFocusRequester = remember { FocusRequester() }
    val registerConfirmFocusRequester = remember { FocusRequester() }

    val finishServerConnection: (String) -> Unit = { savedServerUrl ->
        coroutineScope.launch {
            if (!serverUrlLoadedFromSystemCredential) {
                onSaveServerUrlCredential(context, savedServerUrl)
                delay(CREDENTIAL_PROMPT_SETTLE_DELAY_MS)
            }
            serverUrl = savedServerUrl
            serverUrlLoadedFromSystemCredential = false
            isConnecting = false
            canRequestSavedLoginCredential = true
            step = WizardStep.LOGIN
            authMode = AuthPanelMode.SIGN_IN
            onClearAuthStatus()
        }
    }

    val connectToServer: () -> Unit = connect@{
        if (isResettingTrust) return@connect
        val value = serverUrl.trim()
        if (value.isBlank()) return@connect
        keyboardController?.hide()
        focusManager.clearFocus(force = true)
        serverError = null
        isConnecting = true
        canRequestSavedLoginCredential = false
        onConnectServer(value) { result ->
            result.onSuccess {
                finishServerConnection(it)
            }.onFailure { error ->
                isConnecting = false
                canRequestSavedLoginCredential = false
                step = WizardStep.SERVER
                serverError = onboardingServerErrorMessage(
                    error = error,
                    fallback = "Could not connect to server.",
                )
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
        onLogin(userEmail, password, LoginCredentialSource.MANUAL)
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
            if (!isConnecting) {
                canRequestSavedLoginCredential = true
                step = WizardStep.LOGIN
            }
        }
    }

    LaunchedEffect(serverErrorMessage) {
        if (!serverErrorMessage.isNullOrBlank() && step == WizardStep.SERVER) {
            serverError = serverErrorMessage
        }
    }

    LaunchedEffect(authUiState.savedEmail) {
        if (email.isBlank() && authUiState.savedEmail.isNotBlank()) {
            email = authUiState.savedEmail
        }
    }

    LaunchedEffect(authUiState.isLoading) {
        if (!authUiState.isLoading) {
            isRegisterInFlight = false
        }
    }

    LaunchedEffect(step, isConnecting, serverUrl) {
        if (step != WizardStep.SERVER ||
            isConnecting ||
            serverUrl.isNotBlank() ||
            hasRequestedSavedServerUrl
        ) {
            return@LaunchedEffect
        }

        hasRequestedSavedServerUrl = true
        onRequestSavedServerUrl(context)?.let { savedServerUrl ->
            serverUrl = savedServerUrl
            serverUrlLoadedFromSystemCredential = true
            serverError = null
        }
    }

    LaunchedEffect(step, authMode, authUiState.isLoading, canRequestSavedLoginCredential) {
        if (step != WizardStep.LOGIN ||
            authMode != AuthPanelMode.SIGN_IN ||
            !canRequestSavedLoginCredential
        ) {
            return@LaunchedEffect
        }

        credentialCoordinator.requestSavedCredentialIfAvailable(
            context = context,
            currentEmail = email,
            currentPassword = password,
            isCreatingAccount = false,
            isAuthLoading = authUiState.isLoading,
            requestSavedCredential = onRequestSavedCredential,
        ) { credential ->
            email = credential.email
            password = credential.password
            keyboardController?.hide()
            focusManager.clearFocus(force = true)
            localAuthError = null
            onClearAuthStatus()
            delay(CREDENTIAL_PROMPT_SETTLE_DELAY_MS)
            onLogin(
                credential.email,
                credential.password,
                LoginCredentialSource.SYSTEM_PASSWORD_MANAGER,
            )
            true
        }
    }

    val viewState = when {
        isConnecting -> WizardViewState.CONNECTING
        authUiState.isLoading -> WizardViewState.AUTHENTICATING
        step == WizardStep.LOGIN -> WizardViewState.LOGIN
        else -> WizardViewState.SERVER
    }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = colorScheme.onSurface,
        unfocusedTextColor = colorScheme.onSurface,
        focusedBorderColor = colorScheme.onSurface,
        unfocusedBorderColor = colorScheme.onSurface.copy(alpha = 0.3f),
        focusedLabelColor = colorScheme.onSurface,
        unfocusedLabelColor = colorScheme.onSurface.copy(alpha = 0.6f),
        cursorColor = colorScheme.onSurface,
        focusedPlaceholderColor = colorScheme.onSurface.copy(alpha = 0.4f),
        unfocusedPlaceholderColor = colorScheme.onSurface.copy(alpha = 0.4f),
    )

    BoxWithConstraints(
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
        val targetCardWidth = if (maxWidth >= WIZARD_WIDE_LAYOUT_BREAKPOINT) {
            WIZARD_WIDE_CARD_WIDTH
        } else {
            WIZARD_CARD_MAX_WIDTH
        }
        val cardWidth = minOf(
            targetCardWidth,
            maxWidth - (WIZARD_SCREEN_EDGE_PADDING * 2),
        )

        Card(
            modifier = Modifier
                .width(cardWidth),
            shape = RoundedCornerShape(32.dp),
            colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .drawWithCache {
                        val tint = colorScheme.onSurface
                        val wash = Brush.linearGradient(
                            colors = listOf(
                                tint.copy(alpha = 0.06f),
                                tint.copy(alpha = 0.02f),
                                Color.Transparent,
                            ),
                        )
                        onDrawWithContent {
                            drawRect(wash)
                            drawContent()
                        }
                    }
                    .padding(WIZARD_CARD_CONTENT_PADDING),
            ) {
                Icon(
                    imageVector = if (step == WizardStep.SERVER) Icons.Rounded.Language else Icons.Rounded.Lock,
                    contentDescription = null,
                    tint = lerp(colorScheme.surface, colorScheme.primary, 0.3f).copy(alpha = 0.25f),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(WIZARD_WATERMARK_SIZE),
                )

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = stringResource(R.string.onboarding_title),
                        style = MaterialTheme.typography.headlineSmall,
                        color = colorScheme.onSurface,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        text = stringResource(R.string.onboarding_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurface.copy(alpha = 0.6f),
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        WizardStepChip(
                            modifier = Modifier.weight(1f),
                            title = stringResource(R.string.onboarding_step_server),
                            isServerStep = true,
                            color = Color(0xFF6EA8E1),
                            active = step == WizardStep.SERVER,
                        )
                        WizardStepChip(
                            modifier = Modifier.weight(1f),
                            title = stringResource(R.string.onboarding_step_login),
                            isServerStep = false,
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
                                            serverUrlLoadedFromSystemCredential = false
                                            serverError = null
                                        },
                                        label = { Text(stringResource(R.string.onboarding_server_url_label)) },
                                        placeholder = { Text(stringResource(R.string.onboarding_server_url_placeholder)) },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                                        keyboardActions = KeyboardActions(
                                            onGo = { connectToServer() },
                                            onDone = { connectToServer() },
                                        ),
                                        colors = fieldColors,
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
                                                            canRequestSavedLoginCredential = false
                                                            onConnectServer(value) { connectResult ->
                                                                connectResult.onSuccess {
                                                                    finishServerConnection(it)
                                                                }.onFailure { error ->
                                                                    isConnecting = false
                                                                    canRequestSavedLoginCredential = false
                                                                    step = WizardStep.SERVER
                                                                    serverError = onboardingServerErrorMessage(
                                                                        error = error,
                                                                        fallback = "Could not connect to server.",
                                                                    )
                                                                }
                                                            }
                                                        }.onFailure { error ->
                                                            serverError = onboardingServerErrorMessage(
                                                                error = error,
                                                                fallback = "Could not reset trusted server.",
                                                            )
                                                        }
                                                    }
                                                },
                                            ) {
                                                Text(
                                                    if (isResettingTrust) {
                                                        stringResource(R.string.onboarding_resetting_trust)
                                                    } else {
                                                        stringResource(R.string.onboarding_reset_trust)
                                                    },
                                                )
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
                                        Text(stringResource(R.string.onboarding_connect))
                                    }
                                }
                            }

                            WizardViewState.CONNECTING -> {
                                WizardLoading(
                                    title = stringResource(R.string.onboarding_connecting_title),
                                    subtitle = stringResource(R.string.onboarding_connecting_subtitle),
                                )
                            }

                            WizardViewState.LOGIN -> {
                                Column {
                                    when (authMode) {
                                        AuthPanelMode.SIGN_IN -> {
                                            OutlinedTextField(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .tdayAutofill(
                                                        autofillTypes = listOf(
                                                            AutofillType.Username,
                                                            AutofillType.EmailAddress,
                                                        ),
                                                    ) {
                                                        email = it
                                                        localAuthError = null
                                                        onClearAuthStatus()
                                                    },
                                                value = email,
                                                onValueChange = {
                                                    email = it
                                                    localAuthError = null
                                                    onClearAuthStatus()
                                                },
                                                label = { Text(stringResource(R.string.onboarding_email_label)) },
                                                singleLine = true,
                                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                                keyboardActions = KeyboardActions(
                                                    onNext = { passwordFocusRequester.requestFocus() },
                                                ),
                                                colors = fieldColors,
                                            )
                                            OutlinedTextField(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(top = 10.dp)
                                                    .focusRequester(passwordFocusRequester)
                                                    .tdayAutofill(
                                                        autofillTypes = listOf(AutofillType.Password),
                                                    ) {
                                                        password = it
                                                        localAuthError = null
                                                        onClearAuthStatus()
                                                    },
                                                value = password,
                                                onValueChange = {
                                                    password = it
                                                    localAuthError = null
                                                    onClearAuthStatus()
                                                },
                                                label = { Text(stringResource(R.string.onboarding_password_label)) },
                                                singleLine = true,
                                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                                keyboardActions = KeyboardActions(
                                                    onDone = { signIn() },
                                                ),
                                                visualTransformation = PasswordVisualTransformation(),
                                                colors = fieldColors,
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
                                                Text(stringResource(R.string.onboarding_sign_in))
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
                                                    Text(stringResource(R.string.onboarding_create_account))
                                                }
                                                TextButton(
                                                    onClick = {
                                                        step = WizardStep.SERVER
                                                        canRequestSavedLoginCredential = false
                                                        localAuthError = null
                                                        onClearAuthStatus()
                                                    },
                                                ) {
                                                    Text(stringResource(R.string.onboarding_change_server))
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
                                                label = { Text(stringResource(R.string.onboarding_first_name_label)) },
                                                singleLine = true,
                                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                                keyboardActions = KeyboardActions(
                                                    onNext = { passwordFocusRequester.requestFocus() },
                                                ),
                                                colors = fieldColors,
                                            )
                                            OutlinedTextField(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(top = 10.dp)
                                                    .focusRequester(passwordFocusRequester)
                                                    .tdayAutofill(
                                                        autofillTypes = listOf(
                                                            AutofillType.NewUsername,
                                                            AutofillType.EmailAddress,
                                                        ),
                                                    ) {
                                                        email = it
                                                        localAuthError = null
                                                        onClearAuthStatus()
                                                    },
                                                value = email,
                                                onValueChange = {
                                                    email = it
                                                    localAuthError = null
                                                    onClearAuthStatus()
                                                },
                                                label = { Text(stringResource(R.string.onboarding_email_label)) },
                                                singleLine = true,
                                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                                keyboardActions = KeyboardActions(
                                                    onNext = { registerPasswordFocusRequester.requestFocus() },
                                                ),
                                                colors = fieldColors,
                                            )
                                            OutlinedTextField(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(top = 10.dp)
                                                    .focusRequester(registerPasswordFocusRequester)
                                                    .tdayAutofill(
                                                        autofillTypes = listOf(AutofillType.NewPassword),
                                                    ) {
                                                        registerPassword = it
                                                        localAuthError = null
                                                        onClearAuthStatus()
                                                    },
                                                value = registerPassword,
                                                onValueChange = {
                                                    registerPassword = it
                                                    localAuthError = null
                                                    onClearAuthStatus()
                                                },
                                                label = { Text(stringResource(R.string.onboarding_password_label)) },
                                                singleLine = true,
                                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                                keyboardActions = KeyboardActions(
                                                    onNext = { registerConfirmFocusRequester.requestFocus() },
                                                ),
                                                visualTransformation = PasswordVisualTransformation(),
                                                colors = fieldColors,
                                            )
                                            OutlinedTextField(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(top = 10.dp)
                                                    .focusRequester(registerConfirmFocusRequester)
                                                    .tdayAutofill(
                                                        autofillTypes = listOf(AutofillType.NewPassword),
                                                    ) {
                                                        confirmRegisterPassword = it
                                                        localAuthError = null
                                                        onClearAuthStatus()
                                                    },
                                                value = confirmRegisterPassword,
                                                onValueChange = {
                                                    confirmRegisterPassword = it
                                                    localAuthError = null
                                                    onClearAuthStatus()
                                                },
                                                label = { Text(stringResource(R.string.onboarding_confirm_password_label)) },
                                                singleLine = true,
                                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                                keyboardActions = KeyboardActions(
                                                    onDone = { createAccount() },
                                                ),
                                                visualTransformation = PasswordVisualTransformation(),
                                                colors = fieldColors,
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
                                                        stringResource(R.string.onboarding_creating_account)
                                                    } else {
                                                        stringResource(R.string.onboarding_create_account)
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
                                                    Text(stringResource(R.string.onboarding_back_to_sign_in))
                                                }
                                                TextButton(
                                                    onClick = {
                                                        step = WizardStep.SERVER
                                                        authMode = AuthPanelMode.SIGN_IN
                                                        canRequestSavedLoginCredential = false
                                                        localAuthError = null
                                                        onClearAuthStatus()
                                                    },
                                                ) {
                                                    Text(stringResource(R.string.onboarding_change_server))
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            WizardViewState.AUTHENTICATING -> {
                                WizardLoading(
                                    title = if (authMode == AuthPanelMode.CREATE_ACCOUNT) {
                                        stringResource(R.string.onboarding_creating_account)
                                    } else {
                                        stringResource(R.string.onboarding_authenticating_title)
                                    },
                                    subtitle = if (authMode == AuthPanelMode.CREATE_ACCOUNT) {
                                        stringResource(R.string.onboarding_authenticating_register_subtitle)
                                    } else {
                                        stringResource(R.string.onboarding_authenticating_login_subtitle)
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

@OptIn(ExperimentalComposeUiApi::class)
private fun Modifier.tdayAutofill(
    autofillTypes: List<AutofillType>,
    onFill: (String) -> Unit,
): Modifier = composed {
    val autofill = LocalAutofill.current
    val autofillTree = LocalAutofillTree.current
    val autofillNode = remember(autofillTypes, onFill) {
        AutofillNode(
            autofillTypes = autofillTypes,
            onFill = onFill,
        )
    }

    DisposableEffect(autofillTree, autofillNode) {
        autofillTree += autofillNode
        onDispose {
            autofillTree.children.remove(autofillNode.id)
        }
    }

    this
        .onGloballyPositioned { coordinates ->
            autofillNode.boundingBox = coordinates.boundsInWindow()
        }
        .onFocusChanged { focusState ->
            if (focusState.isFocused) {
                autofill?.requestAutofillForNode(autofillNode)
            } else {
                autofill?.cancelAutofillForNode(autofillNode)
            }
        }
}

private fun onboardingServerErrorMessage(
    error: Throwable,
    fallback: String,
): String {
    val message = error.message?.trim().orEmpty()
    if (message.isBlank()) return fallback
    if (message.contains("serial", ignoreCase = true)) {
        return "This version of the app is out of date. Please update to continue."
    }
    return message
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
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
    }
}

@Composable
private fun WizardStepChip(
    modifier: Modifier,
    title: String,
    isServerStep: Boolean,
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
    val ringColor = lerp(color, MaterialTheme.colorScheme.onSurface, 0.35f)

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
                    imageVector = if (isServerStep) Icons.Rounded.Language else Icons.Rounded.Person,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = title,
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }
    }
}

private const val CREDENTIAL_PROMPT_SETTLE_DELAY_MS = 600L
private val EMAIL_REGEX = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$")
private val WIZARD_CARD_MAX_WIDTH = 440.dp
private val WIZARD_CARD_CONTENT_PADDING = 18.dp
private val WIZARD_SCREEN_EDGE_PADDING = 20.dp
private val WIZARD_WIDE_LAYOUT_BREAKPOINT = 600.dp
private val WIZARD_WIDE_CARD_WIDTH = 360.dp
private val WIZARD_WATERMARK_SIZE = 130.dp
