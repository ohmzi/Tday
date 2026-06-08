package com.ohmz.tday.compose.feature.onboarding

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldColors
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalAutofill
import androidx.compose.ui.platform.LocalAutofillTree
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.ohmz.tday.compose.R
import com.ohmz.tday.compose.core.data.auth.LoginCredentialSource
import com.ohmz.tday.compose.core.data.auth.SystemCredential
import com.ohmz.tday.compose.core.model.SecurityAnswerInput
import com.ohmz.tday.compose.core.model.SecurityQuestion
import com.ohmz.tday.compose.feature.auth.AuthUiState
import com.ohmz.tday.compose.feature.auth.ForgotPasswordPanel
import com.ohmz.tday.compose.feature.auth.LoginCredentialCoordinator
import com.ohmz.tday.compose.ui.theme.TdayTitleIconDayAccent
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class WizardStep {
    MODE,
    SERVER,
    LOGIN,
}

private enum class WizardViewState {
    MODE,
    SERVER,
    CONNECTING,
    LOGIN,
    AUTHENTICATING,
}

private enum class AuthPanelMode {
    SIGN_IN,
    CREATE_ACCOUNT,
    CREATE_ACCOUNT_SECURITY,
    FORGOT_PASSWORD,
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
        username: String,
        password: String,
        securityAnswers: List<SecurityAnswerInput>,
        onSuccess: () -> Unit,
    ) -> Unit,
    onFetchSecurityQuestions: suspend () -> List<SecurityQuestion>,
    onRequestSavedCredential: suspend (Context, String?) -> SystemCredential?,
    onRequestSavedServerUrl: suspend (Context) -> String?,
    onSaveServerUrlCredential: suspend (Context, String) -> Unit,
    onUseLocalMode: () -> Unit,
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
        mutableStateOf(if (initialServerUrl.isNullOrBlank()) WizardStep.MODE else WizardStep.LOGIN)
    }
    var serverUrl by rememberSaveable { mutableStateOf(initialServerUrl.orEmpty()) }
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var firstName by rememberSaveable { mutableStateOf("") }
    var registerPassword by rememberSaveable { mutableStateOf("") }
    var confirmRegisterPassword by rememberSaveable { mutableStateOf("") }
    var securityQuestions by remember { mutableStateOf<List<SecurityQuestion>>(emptyList()) }
    var securityQuestionId1 by rememberSaveable { mutableStateOf<Int?>(null) }
    var securityQuestionId2 by rememberSaveable { mutableStateOf<Int?>(null) }
    var securityQuestionId3 by rememberSaveable { mutableStateOf<Int?>(null) }
    var securityAnswer1 by rememberSaveable { mutableStateOf("") }
    var securityAnswer2 by rememberSaveable { mutableStateOf("") }
    var securityAnswer3 by rememberSaveable { mutableStateOf("") }
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
    val connectErrorFallback = stringResource(R.string.onboarding_error_connect_fallback)
    val resetTrustErrorFallback = stringResource(R.string.onboarding_error_reset_trust_fallback)
    val appOutdatedError = stringResource(R.string.error_app_outdated)
    val firstNameMinError = stringResource(R.string.onboarding_validation_first_name_min)
    val usernameRequiredError = stringResource(R.string.onboarding_validation_username_required)
    val usernameInvalidError = stringResource(R.string.onboarding_validation_username_invalid)
    val passwordRequiredError = stringResource(R.string.onboarding_validation_password_required)
    val passwordMinError = stringResource(R.string.onboarding_validation_password_min)
    val passwordUppercaseError = stringResource(R.string.onboarding_validation_password_uppercase)
    val passwordSpecialError = stringResource(R.string.onboarding_validation_password_special)
    val passwordMismatchError = stringResource(R.string.onboarding_validation_password_mismatch)
    val securityQuestionsDistinctError =
        stringResource(R.string.security_questions_distinct_required)
    val securityAnswersRequiredError =
        stringResource(R.string.security_questions_answers_required)

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
                    fallback = connectErrorFallback,
                    appOutdatedMessage = appOutdatedError,
                )
            }
        }
    }
    val signIn: () -> Unit = signIn@{
        if (authUiState.isLoading) return@signIn
        val userUsername = username.trim()
        if (userUsername.isBlank() || password.isBlank()) return@signIn
        keyboardController?.hide()
        focusManager.clearFocus(force = true)
        localAuthError = null
        onClearAuthStatus()
        onLogin(userUsername, password, LoginCredentialSource.MANUAL)
    }
    val createAccount: () -> Unit = createAccount@{
        if (authUiState.isLoading || isRegisterInFlight) return@createAccount
        val normalizedFirst = firstName.trim()
        val normalizedUsername = username.trim().lowercase(java.util.Locale.US)
        val hasUppercase = registerPassword.any { it.isUpperCase() }
        val hasSpecial = registerPassword.any { !it.isLetterOrDigit() || it == '_' }

        when {
            normalizedFirst.length < 2 -> {
                localAuthError = firstNameMinError
                return@createAccount
            }
            normalizedUsername.isBlank() -> {
                localAuthError = usernameRequiredError
                return@createAccount
            }

            !USERNAME_REGEX.matches(normalizedUsername) -> {
                localAuthError = usernameInvalidError
                return@createAccount
            }
            registerPassword.isBlank() || confirmRegisterPassword.isBlank() -> {
                localAuthError = passwordRequiredError
                return@createAccount
            }
            registerPassword.length < 8 -> {
                localAuthError = passwordMinError
                return@createAccount
            }
            !hasUppercase -> {
                localAuthError = passwordUppercaseError
                return@createAccount
            }
            !hasSpecial -> {
                localAuthError = passwordSpecialError
                return@createAccount
            }
            registerPassword != confirmRegisterPassword -> {
                localAuthError = passwordMismatchError
                return@createAccount
            }
        }

        keyboardController?.hide()
        focusManager.clearFocus(force = true)
        localAuthError = null
        onClearAuthStatus()
        // Advance to the security-questions sub-step; the account is only created
        // once two distinct questions are chosen and answered.
        authMode = AuthPanelMode.CREATE_ACCOUNT_SECURITY
        if (securityQuestions.isEmpty()) {
            coroutineScope.launch {
                val fetched = onFetchSecurityQuestions()
                securityQuestions = fetched
                if (securityQuestionId1 == null) securityQuestionId1 = fetched.getOrNull(0)?.id
                if (securityQuestionId2 == null) securityQuestionId2 = fetched.getOrNull(1)?.id
                if (securityQuestionId3 == null) securityQuestionId3 = fetched.getOrNull(2)?.id
            }
        }
    }

    val submitSecurityAndRegister: () -> Unit = submit@{
        if (authUiState.isLoading || isRegisterInFlight) return@submit
        val normalizedFirst = firstName.trim()
        val normalizedUsername = username.trim().lowercase(java.util.Locale.US)
        val id1 = securityQuestionId1
        val id2 = securityQuestionId2
        val id3 = securityQuestionId3

        if (id1 == null || id2 == null || id3 == null || setOf(id1, id2, id3).size != 3) {
            localAuthError = securityQuestionsDistinctError
            return@submit
        }
        if (securityAnswer1.isBlank() || securityAnswer2.isBlank() || securityAnswer3.isBlank()) {
            localAuthError = securityAnswersRequiredError
            return@submit
        }

        keyboardController?.hide()
        focusManager.clearFocus(force = true)
        localAuthError = null
        isRegisterInFlight = true
        onClearAuthStatus()
        onRegister(
            normalizedFirst,
            normalizedUsername,
            registerPassword,
            listOf(
                SecurityAnswerInput(questionId = id1, answer = securityAnswer1.trim()),
                SecurityAnswerInput(questionId = id2, answer = securityAnswer2.trim()),
                SecurityAnswerInput(questionId = id3, answer = securityAnswer3.trim()),
            ),
        ) {
            isRegisterInFlight = false
            authMode = AuthPanelMode.SIGN_IN
            password = registerPassword
            registerPassword = ""
            confirmRegisterPassword = ""
            securityAnswer1 = ""
            securityAnswer2 = ""
            securityAnswer3 = ""
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

    LaunchedEffect(authUiState.savedUsername) {
        if (username.isBlank() && authUiState.savedUsername.isNotBlank()) {
            username = authUiState.savedUsername
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
            currentUsername = username,
            currentPassword = password,
            isCreatingAccount = false,
            isAuthLoading = authUiState.isLoading,
            requestSavedCredential = onRequestSavedCredential,
        ) { credential ->
            username = credential.username
            password = credential.password
            keyboardController?.hide()
            focusManager.clearFocus(force = true)
            localAuthError = null
            onClearAuthStatus()
            delay(CREDENTIAL_PROMPT_SETTLE_DELAY_MS)
            onLogin(
                credential.username,
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
        step == WizardStep.SERVER -> WizardViewState.SERVER
        else -> WizardViewState.MODE
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
        focusedContainerColor = colorScheme.surface,
        unfocusedContainerColor = colorScheme.surface,
        errorContainerColor = colorScheme.surface,
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
            shape = RoundedCornerShape(34.dp),
            colors = CardDefaults.cardColors(containerColor = colorScheme.background),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
            border = BorderStroke(1.dp, colorScheme.onSurface.copy(alpha = 0.08f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .drawWithCache {
                        val tint = colorScheme.onSurface
                        val wash = Brush.linearGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.18f),
                                tint.copy(alpha = 0.015f),
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
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = ImageVector.vectorResource(R.drawable.ic_lucide_sun),
                            contentDescription = null,
                            tint = TdayTitleIconDayAccent,
                            modifier = Modifier.size(27.dp),
                        )
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.headlineMedium,
                            color = colorScheme.onSurface,
                            fontWeight = FontWeight.Black,
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        WizardStepChip(
                            modifier = Modifier.weight(1f),
                            title = stringResource(R.string.onboarding_step_mode),
                            imageVector = ImageVector.vectorResource(R.drawable.ic_lucide_smartphone),
                            color = Color(0xFF7FB78A),
                            active = step == WizardStep.MODE,
                            completed = step == WizardStep.SERVER || step == WizardStep.LOGIN,
                        )
                        WizardStepChip(
                            modifier = Modifier.weight(1f),
                            title = stringResource(R.string.onboarding_step_server),
                            imageVector = ImageVector.vectorResource(R.drawable.ic_lucide_languages),
                            color = Color(0xFF6EA8E1),
                            active = step == WizardStep.SERVER,
                            completed = step == WizardStep.LOGIN,
                        )
                        WizardStepChip(
                            modifier = Modifier.weight(1f),
                            title = stringResource(R.string.onboarding_step_login),
                            imageVector = ImageVector.vectorResource(R.drawable.ic_lucide_user),
                            color = Color(0xFFD48A8C),
                            active = step == WizardStep.LOGIN,
                        )
                    }

                    AnimatedContent(targetState = viewState, label = "wizardState") { state ->
                        when (state) {
                            WizardViewState.MODE -> {
                                Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
                                    WizardHeroTile(
                                        title = stringResource(R.string.onboarding_mode_title),
                                        subtitle = stringResource(R.string.onboarding_mode_subtitle),
                                        imageVector = ImageVector.vectorResource(R.drawable.ic_lucide_smartphone),
                                        color = Color(0xFF6EA8E1),
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        WizardModeChoiceButton(
                                            modifier = Modifier.weight(1f),
                                            title = stringResource(R.string.onboarding_mode_server_short_title),
                                            subtitle = stringResource(R.string.onboarding_mode_server_short_subtitle),
                                            imageVector = ImageVector.vectorResource(R.drawable.ic_lucide_languages),
                                            color = Color(0xFF6EA8E1),
                                            enabled = !isResettingTrust,
                                            onClick = {
                                                step = WizardStep.SERVER
                                                serverError = null
                                                localAuthError = null
                                                onClearAuthStatus()
                                            },
                                        )
                                        WizardModeChoiceButton(
                                            modifier = Modifier.weight(1f),
                                            title = stringResource(R.string.onboarding_mode_local_short_title),
                                            subtitle = stringResource(R.string.onboarding_mode_local_short_subtitle),
                                            imageVector = ImageVector.vectorResource(R.drawable.ic_lucide_smartphone),
                                            color = Color(0xFF719F84),
                                            enabled = !isResettingTrust,
                                            onClick = {
                                                keyboardController?.hide()
                                                focusManager.clearFocus(force = true)
                                                serverError = null
                                                localAuthError = null
                                                onClearAuthStatus()
                                                onUseLocalMode()
                                            },
                                        )
                                    }
                                }
                            }

                            WizardViewState.SERVER -> {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    WizardHeroTile(
                                        title = stringResource(R.string.onboarding_mode_server_title),
                                        subtitle = stringResource(R.string.onboarding_server_hero_subtitle),
                                        imageVector = ImageVector.vectorResource(R.drawable.ic_lucide_languages),
                                        color = Color(0xFF6EA8E1),
                                    )
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
                                        shape = RoundedCornerShape(22.dp),
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
                                                                        fallback = connectErrorFallback,
                                                                        appOutdatedMessage = appOutdatedError,
                                                                    )
                                                                }
                                                            }
                                                        }.onFailure { error ->
                                                            serverError = onboardingServerErrorMessage(
                                                                error = error,
                                                                fallback = resetTrustErrorFallback,
                                                                appOutdatedMessage = appOutdatedError,
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
                                            .height(48.dp),
                                        enabled = serverUrl.isNotBlank() && !isResettingTrust,
                                        onClick = connectToServer,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = colorScheme.primary,
                                            contentColor = colorScheme.onPrimary,
                                        ),
                                    ) {
                                        Text(stringResource(R.string.onboarding_connect))
                                    }

                                    TextButton(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 4.dp),
                                        enabled = !isResettingTrust,
                                        onClick = {
                                            keyboardController?.hide()
                                            focusManager.clearFocus(force = true)
                                            serverError = null
                                            localAuthError = null
                                            onClearAuthStatus()
                                            step = WizardStep.MODE
                                        },
                                    ) {
                                        Text(stringResource(R.string.onboarding_change_setup))
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
                                Column(modifier = Modifier.animateContentSize()) {
                                    when (authMode) {
                                        AuthPanelMode.SIGN_IN -> {
                                            WizardHeroTile(
                                                title = stringResource(R.string.onboarding_sign_in),
                                                imageVector = ImageVector.vectorResource(R.drawable.ic_lucide_user),
                                                color = Color(0xFFC97880),
                                            )
                                            OutlinedTextField(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(top = 10.dp)
                                                    .tdayAutofill(
                                                        autofillTypes = listOf(
                                                            AutofillType.Username,
                                                        ),
                                                    ) {
                                                        username = it
                                                        localAuthError = null
                                                        onClearAuthStatus()
                                                    },
                                                value = username,
                                                onValueChange = {
                                                    username = it
                                                    localAuthError = null
                                                    onClearAuthStatus()
                                                },
                                                label = { Text(stringResource(R.string.onboarding_username_label)) },
                                                singleLine = true,
                                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                                keyboardActions = KeyboardActions(
                                                    onNext = { passwordFocusRequester.requestFocus() },
                                                ),
                                                shape = RoundedCornerShape(22.dp),
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
                                                shape = RoundedCornerShape(22.dp),
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
                                                    .padding(top = 4.dp)
                                                    .height(48.dp),
                                                enabled = username.isNotBlank() && password.isNotBlank() && !authUiState.isLoading,
                                                onClick = signIn,
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = colorScheme.primary,
                                                    contentColor = colorScheme.onPrimary,
                                                ),
                                            ) {
                                                Text(stringResource(R.string.onboarding_sign_in))
                                            }

                                            TextButton(
                                                modifier = Modifier.fillMaxWidth(),
                                                onClick = {
                                                    localAuthError = null
                                                    onClearAuthStatus()
                                                    authMode = AuthPanelMode.FORGOT_PASSWORD
                                                },
                                            ) {
                                                Text(stringResource(R.string.forgot_password_entry))
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
                                                        step = WizardStep.MODE
                                                        canRequestSavedLoginCredential = false
                                                        localAuthError = null
                                                        onClearAuthStatus()
                                                    },
                                                ) {
                                                    Text(stringResource(R.string.onboarding_change_setup))
                                                }
                                            }
                                        }

                                        AuthPanelMode.FORGOT_PASSWORD -> {
                                            ForgotPasswordPanel(
                                                initialUsername = username,
                                                onBackToLogin = {
                                                    authMode = AuthPanelMode.SIGN_IN
                                                    localAuthError = null
                                                    onClearAuthStatus()
                                                },
                                                onResetComplete = { resetUsername ->
                                                    authMode = AuthPanelMode.SIGN_IN
                                                    username = resetUsername
                                                    password = ""
                                                    localAuthError = null
                                                    onClearAuthStatus()
                                                },
                                            )
                                        }

                                        AuthPanelMode.CREATE_ACCOUNT -> {
                                            WizardHeroTile(
                                                title = stringResource(R.string.onboarding_create_account),
                                                imageVector = ImageVector.vectorResource(R.drawable.ic_lucide_user),
                                                color = Color(0xFFC97880),
                                            )
                                            OutlinedTextField(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(top = 10.dp),
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
                                                shape = RoundedCornerShape(22.dp),
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
                                                        ),
                                                    ) {
                                                        username = it
                                                        localAuthError = null
                                                        onClearAuthStatus()
                                                    },
                                                value = username,
                                                onValueChange = {
                                                    username = it
                                                    localAuthError = null
                                                    onClearAuthStatus()
                                                },
                                                label = { Text(stringResource(R.string.onboarding_username_label)) },
                                                singleLine = true,
                                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                                keyboardActions = KeyboardActions(
                                                    onNext = { registerPasswordFocusRequester.requestFocus() },
                                                ),
                                                shape = RoundedCornerShape(22.dp),
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
                                                shape = RoundedCornerShape(22.dp),
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
                                                shape = RoundedCornerShape(22.dp),
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
                                                    .padding(top = 4.dp)
                                                    .height(48.dp),
                                                enabled = firstName.isNotBlank() &&
                                                        username.isNotBlank() &&
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
                                                        step = WizardStep.MODE
                                                        authMode = AuthPanelMode.SIGN_IN
                                                        canRequestSavedLoginCredential = false
                                                        localAuthError = null
                                                        onClearAuthStatus()
                                                    },
                                                ) {
                                                    Text(stringResource(R.string.onboarding_change_setup))
                                                }
                                            }
                                        }

                                        AuthPanelMode.CREATE_ACCOUNT_SECURITY -> {
                                            WizardHeroTile(
                                                title = stringResource(R.string.security_questions_title),
                                                imageVector = ImageVector.vectorResource(R.drawable.ic_lucide_user),
                                                color = Color(0xFFC97880),
                                            )

                                            SecurityQuestionPicker(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(top = 10.dp),
                                                label = stringResource(R.string.security_questions_question_1),
                                                questions = securityQuestions,
                                                excludeIds = setOfNotNull(
                                                    securityQuestionId2,
                                                    securityQuestionId3
                                                ),
                                                selectedId = securityQuestionId1,
                                                onSelected = {
                                                    securityQuestionId1 = it
                                                    localAuthError = null
                                                },
                                                answer = securityAnswer1,
                                                onAnswerChange = {
                                                    securityAnswer1 = it
                                                    localAuthError = null
                                                },
                                                fieldColors = fieldColors,
                                            )
                                            SecurityQuestionPicker(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(top = 10.dp),
                                                label = stringResource(R.string.security_questions_question_2),
                                                questions = securityQuestions,
                                                excludeIds = setOfNotNull(
                                                    securityQuestionId1,
                                                    securityQuestionId3
                                                ),
                                                selectedId = securityQuestionId2,
                                                onSelected = {
                                                    securityQuestionId2 = it
                                                    localAuthError = null
                                                },
                                                answer = securityAnswer2,
                                                onAnswerChange = {
                                                    securityAnswer2 = it
                                                    localAuthError = null
                                                },
                                                fieldColors = fieldColors,
                                            )
                                            SecurityQuestionPicker(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(top = 10.dp),
                                                label = stringResource(R.string.security_questions_question_3),
                                                questions = securityQuestions,
                                                excludeIds = setOfNotNull(
                                                    securityQuestionId1,
                                                    securityQuestionId2
                                                ),
                                                selectedId = securityQuestionId3,
                                                onSelected = {
                                                    securityQuestionId3 = it
                                                    localAuthError = null
                                                },
                                                answer = securityAnswer3,
                                                onAnswerChange = {
                                                    securityAnswer3 = it
                                                    localAuthError = null
                                                },
                                                fieldColors = fieldColors,
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

                                            Button(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(top = 4.dp)
                                                    .height(48.dp),
                                                enabled = securityQuestionId1 != null &&
                                                        securityQuestionId2 != null &&
                                                        securityQuestionId3 != null &&
                                                        setOfNotNull(
                                                            securityQuestionId1,
                                                            securityQuestionId2,
                                                            securityQuestionId3
                                                        ).size == 3 &&
                                                        securityAnswer1.isNotBlank() &&
                                                        securityAnswer2.isNotBlank() &&
                                                        securityAnswer3.isNotBlank() &&
                                                        !authUiState.isLoading &&
                                                        !isRegisterInFlight,
                                                onClick = submitSecurityAndRegister,
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

                                            TextButton(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(top = 4.dp),
                                                onClick = {
                                                    authMode = AuthPanelMode.CREATE_ACCOUNT
                                                    localAuthError = null
                                                    onClearAuthStatus()
                                                },
                                            ) {
                                                Text(stringResource(R.string.security_questions_back))
                                            }
                                        }
                                    }
                                }
                            }

                            WizardViewState.AUTHENTICATING -> {
                                val isRegistering = authMode == AuthPanelMode.CREATE_ACCOUNT ||
                                        authMode == AuthPanelMode.CREATE_ACCOUNT_SECURITY
                                WizardLoading(
                                    title = if (isRegistering) {
                                        stringResource(R.string.onboarding_creating_account)
                                    } else {
                                        stringResource(R.string.onboarding_authenticating_title)
                                    },
                                    subtitle = if (isRegistering) {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SecurityQuestionPicker(
    label: String,
    questions: List<SecurityQuestion>,
    excludeIds: Set<Int>,
    selectedId: Int?,
    onSelected: (Int) -> Unit,
    answer: String,
    onAnswerChange: (String) -> Unit,
    fieldColors: TextFieldColors,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectableQuestions = questions.filter { it.id == selectedId || it.id !in excludeIds }
    val selectedText = questions.firstOrNull { it.id == selectedId }?.text.orEmpty()

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
        ) {
            OutlinedTextField(
                modifier = Modifier
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth(),
                value = selectedText,
                onValueChange = {},
                readOnly = true,
                label = { Text(label) },
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                },
                singleLine = true,
                shape = RoundedCornerShape(22.dp),
                colors = fieldColors,
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                selectableQuestions.forEach { question ->
                    DropdownMenuItem(
                        text = { Text(question.text) },
                        onClick = {
                            onSelected(question.id)
                            expanded = false
                        },
                    )
                }
            }
        }
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = answer,
            onValueChange = onAnswerChange,
            label = { Text(stringResource(R.string.security_questions_answer_label)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            shape = RoundedCornerShape(22.dp),
            colors = fieldColors,
        )
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
    appOutdatedMessage: String,
): String {
    val message = error.message?.trim().orEmpty()
    if (message.isBlank()) return fallback
    if (message.contains("serial", ignoreCase = true)) {
        return appOutdatedMessage
    }
    return message
}

@Composable
private fun WizardLoading(
    title: String,
    subtitle: String,
) {
    val colorScheme = MaterialTheme.colorScheme
    val transition = rememberInfiniteTransition(label = "wizardLoading")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1300, easing = LinearEasing)),
        label = "wizardRotation",
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
        border = BorderStroke(1.dp, colorScheme.onSurface.copy(alpha = 0.08f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = ImageVector.vectorResource(R.drawable.ic_lucide_languages),
                contentDescription = null,
                tint = colorScheme.primary,
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
                color = colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurface.copy(alpha = 0.6f),
            )
        }
    }
}

@Composable
internal fun WizardHeroTile(
    title: String,
    subtitle: String? = null,
    imageVector: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(WIZARD_HERO_TILE_HEIGHT),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = color),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithCache {
                    val glow = Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.24f),
                            Color.White.copy(alpha = 0.08f),
                            Color.Transparent,
                        ),
                        center = Offset(size.width * 0.18f, size.height * 0.18f),
                        radius = size.width * 0.72f,
                    )
                    onDrawWithContent {
                        drawRect(glow)
                        drawContent()
                    }
                },
        ) {
            Icon(
                imageVector = imageVector,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.2f),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(86.dp)
                    .offset(x = 22.dp, y = 12.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(Color.White.copy(alpha = 0.18f), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = imageVector,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(23.dp),
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        maxLines = 1,
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.82f),
                            maxLines = 2,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WizardModeChoiceButton(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    imageVector: ImageVector,
    color: Color,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = modifier
            .height(WIZARD_MODE_TILE_HEIGHT)
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = if (enabled) 1f else 0.55f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (enabled) 8.dp else 0.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithCache {
                    val glow = Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.22f),
                            Color.White.copy(alpha = 0.08f),
                            Color.Transparent,
                        ),
                        center = Offset(size.width * 0.22f, size.height * 0.18f),
                        radius = size.maxDimension * 0.9f,
                    )
                    val wash = Brush.linearGradient(
                        colors = listOf(Color.White.copy(alpha = 0.12f), Color.Transparent),
                    )
                    onDrawWithContent {
                        drawRect(glow)
                        drawRect(wash)
                        drawContent()
                    }
                },
        ) {
            Icon(
                imageVector = imageVector,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.22f),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(76.dp)
                    .offset(x = 18.dp, y = 16.dp),
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(13.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Icon(
                    imageVector = imageVector,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    maxLines = 2,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.82f),
                    maxLines = 2,
                )
            }
        }
    }
}

@Composable
private fun WizardStepChip(
    modifier: Modifier,
    title: String,
    imageVector: ImageVector,
    color: Color,
    active: Boolean,
    completed: Boolean = false,
) {
    // A step keeps its color once it is the active step or has been completed;
    // completed steps swap their glyph for a checkmark.
    val highlighted = active || completed
    val scale by animateFloatAsState(
        targetValue = if (active) 1.02f else 1f,
        animationSpec = tween(durationMillis = 180),
        label = "wizardStepChipScale",
    )
    val borderWidth by animateDpAsState(
        targetValue = 1.dp,
        animationSpec = tween(durationMillis = 180),
        label = "wizardStepChipBorderWidth",
    )
    val colorScheme = MaterialTheme.colorScheme
    val ringColor = lerp(color, MaterialTheme.colorScheme.onSurface, 0.35f)
    val contentColor = if (highlighted) Color.White else colorScheme.onSurface.copy(alpha = 0.68f)

    Card(
        modifier = modifier.graphicsLayer(
            scaleX = scale,
            scaleY = scale,
        ),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = if (highlighted) color else colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = if (highlighted) 8.dp else 0.dp),
        border = BorderStroke(
            borderWidth,
            if (highlighted) ringColor.copy(alpha = 0.62f) else colorScheme.onSurface.copy(alpha = 0.08f),
        ),
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (completed) ImageVector.vectorResource(R.drawable.ic_lucide_check) else imageVector,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(13.dp),
                )
                Text(
                    text = title,
                    color = contentColor,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }
    }
}

private const val CREDENTIAL_PROMPT_SETTLE_DELAY_MS = 600L
private val USERNAME_REGEX = Regex("^[a-z0-9](?:[a-z0-9._-]{1,28}[a-z0-9])$")
private val WIZARD_CARD_MAX_WIDTH = 440.dp
private val WIZARD_CARD_CONTENT_PADDING = 18.dp
private val WIZARD_SCREEN_EDGE_PADDING = 20.dp
private val WIZARD_WIDE_LAYOUT_BREAKPOINT = 600.dp
private val WIZARD_WIDE_CARD_WIDTH = 360.dp
private val WIZARD_HERO_TILE_HEIGHT = 78.dp
private val WIZARD_MODE_TILE_HEIGHT = 116.dp
