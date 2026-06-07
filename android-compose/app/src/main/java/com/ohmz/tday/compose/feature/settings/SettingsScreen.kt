package com.ohmz.tday.compose.feature.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.core.os.LocaleListCompat
import androidx.core.view.HapticFeedbackConstantsCompat
import androidx.core.view.ViewCompat
import com.ohmz.tday.compose.BuildConfig
import com.ohmz.tday.compose.R
import com.ohmz.tday.compose.core.data.server.VersionCheckResult
import com.ohmz.tday.compose.core.model.SessionUser
import com.ohmz.tday.compose.core.notification.ReminderOption
import com.ohmz.tday.compose.core.ui.rememberScrollCollapsingTitleScrollBehavior
import com.ohmz.tday.compose.feature.app.MobileSyncStatus
import com.ohmz.tday.compose.feature.app.ProfileEditResult
import com.ohmz.tday.compose.ui.component.TdayCenteredSelectorDialog
import com.ohmz.tday.compose.ui.component.TdaySegmentedSlider
import com.ohmz.tday.compose.ui.theme.AppThemeMode
import com.ohmz.tday.compose.ui.theme.TdayDimens
import com.ohmz.tday.compose.ui.theme.TdayStatusSuccess
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun SettingsScreen(
    user: SessionUser?,
    isLocalMode: Boolean = false,
    selectedThemeMode: AppThemeMode,
    selectedReminder: ReminderOption,
    syncStatus: MobileSyncStatus,
    aiSummaryEnabled: Boolean,
    hasUpdate: Boolean = false,
    latestVersionName: String? = null,
    backendVersion: String? = null,
    versionCheckResult: VersionCheckResult? = null,
    onThemeModeSelected: (AppThemeMode) -> Unit,
    onReminderSelected: (ReminderOption) -> Unit,
    onSyncNow: () -> Unit,
    onToggleAiSummary: (Boolean) -> Unit,
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onOpenLatestRelease: () -> Unit,
    onUpdateName: suspend (String) -> ProfileEditResult,
    onChangePassword: suspend (String, String) -> ProfileEditResult,
    onForgotPassword: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val scrollState = rememberScrollState()
    val titleScrollBehavior = rememberScrollCollapsingTitleScrollBehavior(
        scrollState = scrollState,
        maxCollapseDistance = SETTINGS_TITLE_COLLAPSE_DISTANCE_DP.dp,
        label = "settingsTitleCollapseProgress",
    )

    Scaffold(
        containerColor = colorScheme.background,
        topBar = {
            SettingsTopBar(
                onBack = onBack,
                collapseProgress = titleScrollBehavior.collapseProgress,
            )
        },
        ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(colorScheme.background)
                .nestedScroll(titleScrollBehavior.nestedScrollConnection)
                .verticalScroll(scrollState)
                .padding(horizontal = 18.dp, vertical = 2.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!isLocalMode) {
                SettingsProfileCard(
                    user = user,
                    onUpdateName = onUpdateName,
                    onChangePassword = onChangePassword,
                    onForgotPassword = onForgotPassword,
                )
            }

            SettingsWorkspaceSection(
                syncStatus = syncStatus,
                onSyncNow = onSyncNow,
            )

            SettingsSectionCard {
                SettingsSectionTitle(title = stringResource(R.string.settings_appearance))
                ThemeModeSelector(
                    selectedThemeMode = selectedThemeMode,
                    onThemeModeSelected = onThemeModeSelected,
                )
                SettingsDivider()
                SettingsSectionTitle(title = stringResource(R.string.settings_reminders))
                ReminderSelector(
                    selectedReminder = selectedReminder,
                    onReminderSelected = onReminderSelected,
                )
                SettingsDivider()
                SettingsSectionTitle(title = stringResource(R.string.settings_language))
                LanguageSelector()
            }

            SettingsSectionCard {
                SettingsSectionTitle(title = stringResource(R.string.settings_feature_toggle))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.settings_ai_task_summary),
                            style = MaterialTheme.typography.titleMedium,
                            color = colorScheme.onSurface,
                            fontWeight = FontWeight.ExtraBold,
                        )
                    }
                    Switch(
                        checked = aiSummaryEnabled,
                        onCheckedChange = onToggleAiSummary,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = colorScheme.secondary,
                            checkedBorderColor = Color.Transparent,
                        ),
                    )
                }
            }

            SettingsSectionCard {
                SettingsListRow(
                    title = stringResource(R.string.release_title),
                    value = stringResource(R.string.label_version_name, BuildConfig.VERSION_NAME),
                    onClick = onOpenLatestRelease,
                )
                if (hasUpdate && latestVersionName != null) {
                    Text(
                        text = stringResource(
                            R.string.settings_update_available_version,
                            latestVersionName,
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = colorScheme.secondary,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
                if (!isLocalMode && backendVersion != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.label_server),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = colorScheme.onSurface,
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.label_version_name, backendVersion),
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onSurface.copy(alpha = 0.58f),
                            )
                            val isCompatible = versionCheckResult is VersionCheckResult.Compatible ||
                                versionCheckResult == null
                            Text(
                                text = if (isCompatible) {
                                    stringResource(R.string.settings_server_compatible)
                                } else {
                                    stringResource(R.string.settings_server_incompatible)
                                },
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isCompatible) {
                                    TdayStatusSuccess
                                } else {
                                    colorScheme.error
                                },
                            )
                        }
                    }
                }
                if (!isLocalMode) {
                    SettingsDivider()
                    SettingsListRow(
                        title = stringResource(R.string.action_sign_out),
                        value = null,
                        onClick = onLogout,
                        titleColor = colorScheme.error,
                        trailingTint = colorScheme.error.copy(alpha = 0.72f),
                        showChevron = false,
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

private enum class SettingsAccountEditor { None, Name, Password }

@Composable
private fun SettingsProfileCard(
    user: SessionUser?,
    onUpdateName: suspend (String) -> ProfileEditResult,
    onChangePassword: suspend (String, String) -> ProfileEditResult,
    onForgotPassword: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    var activeEditor by rememberSaveable { mutableStateOf(SettingsAccountEditor.None) }

    SettingsSectionCard {
        AccountNameSection(
            user = user,
            isEditing = activeEditor == SettingsAccountEditor.Name,
            onBeginEdit = { activeEditor = SettingsAccountEditor.Name },
            onDone = { activeEditor = SettingsAccountEditor.None },
            onUpdateName = onUpdateName,
        )

        if (!user?.username.isNullOrBlank()) {
            SettingsDivider()
            AccountUsernameRow(username = user?.username.orEmpty())
        }

        SettingsDivider()
        AccountPasswordSection(
            isEditing = activeEditor == SettingsAccountEditor.Password,
            onBeginEdit = { activeEditor = SettingsAccountEditor.Password },
            onDone = { activeEditor = SettingsAccountEditor.None },
            onChangePassword = onChangePassword,
            onForgotPassword = onForgotPassword,
        )

        SettingsDivider()
        Text(
            text = stringResource(
                R.string.settings_role_label,
                user?.role ?: stringResource(R.string.settings_role_default),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = colorScheme.onSurface.copy(alpha = 0.58f),
        )
    }
}

@Composable
private fun AccountNameSection(
    user: SessionUser?,
    isEditing: Boolean,
    onBeginEdit: () -> Unit,
    onDone: () -> Unit,
    onUpdateName: suspend (String) -> ProfileEditResult,
) {
    val colorScheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()

    var draft by remember(user?.name) { mutableStateOf(user?.name.orEmpty()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                AccountFieldLabel(stringResource(R.string.settings_account_name_label))
                Text(
                    text = user?.name ?: stringResource(R.string.settings_unknown_user),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = colorScheme.onSurface,
                )
            }
            if (!isEditing) {
                AccountInlineButton(
                    text = stringResource(R.string.action_edit),
                    icon = Icons.Rounded.Edit,
                    onClick = {
                        draft = user?.name.orEmpty()
                        error = null
                        onBeginEdit()
                    },
                )
            }
        }

        AnimatedVisibility(
            visible = isEditing,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = draft,
                    onValueChange = {
                        draft = it
                        error = null
                    },
                    label = { Text(stringResource(R.string.settings_account_name_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    shape = RoundedCornerShape(22.dp),
                )
                error?.let { AccountErrorText(it) }
                AccountEditorActions(
                    busy = busy,
                    canSave = !busy && draft.trim().isNotEmpty() && draft.trim() != user?.name,
                    onCancel = {
                        error = null
                        onDone()
                    },
                    onSave = {
                        scope.launch {
                            busy = true
                            error = null
                            when (val result = onUpdateName(draft.trim())) {
                                is ProfileEditResult.Success -> onDone()
                                is ProfileEditResult.Error -> error = result.message
                            }
                            busy = false
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun AccountUsernameRow(username: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        AccountFieldLabel(stringResource(R.string.settings_account_username_label))
        Text(
            text = username,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
        )
    }
}

@Composable
private fun AccountPasswordSection(
    isEditing: Boolean,
    onBeginEdit: () -> Unit,
    onDone: () -> Unit,
    onChangePassword: suspend (String, String) -> ProfileEditResult,
    onForgotPassword: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()

    var current by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val passwordMinError = stringResource(R.string.onboarding_validation_password_min)
    val passwordUppercaseError = stringResource(R.string.onboarding_validation_password_uppercase)
    val passwordSpecialError = stringResource(R.string.onboarding_validation_password_special)
    val passwordMismatchError = stringResource(R.string.onboarding_validation_password_mismatch)

    // Clear the sensitive fields whenever the editor collapses.
    LaunchedEffect(isEditing) {
        if (!isEditing) {
            current = ""
            newPassword = ""
            confirm = ""
            error = null
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                AccountFieldLabel(stringResource(R.string.settings_account_password_label))
                Text(
                    text = "••••••••",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = colorScheme.onSurface.copy(alpha = 0.8f),
                )
            }
            if (!isEditing) {
                AccountInlineButton(
                    text = stringResource(R.string.settings_account_change_password),
                    icon = Icons.Rounded.Lock,
                    onClick = onBeginEdit,
                )
            }
        }

        AnimatedVisibility(
            visible = isEditing,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AccountPasswordField(
                    value = current,
                    onValueChange = {
                        current = it
                        error = null
                    },
                    label = stringResource(R.string.settings_account_current_password),
                    imeAction = ImeAction.Next,
                )
                AccountPasswordField(
                    value = newPassword,
                    onValueChange = {
                        newPassword = it
                        error = null
                    },
                    label = stringResource(R.string.forgot_password_new_password),
                    imeAction = ImeAction.Next,
                )
                AccountPasswordField(
                    value = confirm,
                    onValueChange = {
                        confirm = it
                        error = null
                    },
                    label = stringResource(R.string.onboarding_confirm_password_label),
                    imeAction = ImeAction.Done,
                )
                Text(
                    text = stringResource(R.string.settings_account_password_requirement),
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurface.copy(alpha = 0.5f),
                )
                error?.let { AccountErrorText(it) }
                TextButton(
                    onClick = onForgotPassword,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                ) {
                    Text(
                        text = stringResource(R.string.settings_account_forgot_password),
                        fontWeight = FontWeight.ExtraBold,
                        color = colorScheme.secondary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                AccountEditorActions(
                    busy = busy,
                    canSave = !busy && current.isNotBlank() && newPassword.isNotBlank() && confirm.isNotBlank(),
                    onCancel = onDone,
                    onSave = {
                        val validation = when {
                            newPassword.length < 8 -> passwordMinError
                            !newPassword.any { it.isUpperCase() } -> passwordUppercaseError
                            !newPassword.any { !it.isLetterOrDigit() } -> passwordSpecialError
                            newPassword != confirm -> passwordMismatchError
                            else -> null
                        }
                        if (validation != null) {
                            error = validation
                        } else {
                            scope.launch {
                                busy = true
                                error = null
                                when (val result = onChangePassword(current, newPassword)) {
                                    is ProfileEditResult.Success -> onDone()
                                    is ProfileEditResult.Error -> error = result.message
                                }
                                busy = false
                            }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun AccountPasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    imeAction: ImeAction,
) {
    var revealed by remember { mutableStateOf(false) }
    OutlinedTextField(
        modifier = Modifier.fillMaxWidth(),
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (revealed) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(imeAction = imeAction),
        shape = RoundedCornerShape(22.dp),
        trailingIcon = {
            IconButton(onClick = { revealed = !revealed }) {
                Icon(
                    imageVector = if (revealed) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                    contentDescription = stringResource(
                        if (revealed) {
                            R.string.settings_account_hide_password
                        } else {
                            R.string.settings_account_show_password
                        },
                    ),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                )
            }
        },
    )
}

@Composable
private fun AccountInlineButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val view = LocalView.current
    TextButton(
        onClick = {
            ViewCompat.performHapticFeedback(view, HapticFeedbackConstantsCompat.CLOCK_TICK)
            onClick()
        },
        colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
            contentColor = colorScheme.secondary,
        ),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
        )
        Spacer(modifier = Modifier.size(4.dp))
        Text(
            text = text,
            fontWeight = FontWeight.ExtraBold,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun AccountEditorActions(
    busy: Boolean,
    canSave: Boolean,
    onCancel: () -> Unit,
    onSave: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(
            onClick = onCancel,
            enabled = !busy,
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = stringResource(R.string.action_cancel),
                fontWeight = FontWeight.ExtraBold,
            )
        }
        Button(
            onClick = onSave,
            enabled = canSave,
            modifier = Modifier
                .weight(1f)
                .height(48.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = colorScheme.primary,
                contentColor = colorScheme.onPrimary,
            ),
        ) {
            if (busy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = colorScheme.onPrimary,
                )
            } else {
                Text(
                    text = stringResource(R.string.action_save),
                    fontWeight = FontWeight.ExtraBold,
                )
            }
        }
    }
}

@Composable
private fun AccountFieldLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
    )
}

@Composable
private fun AccountErrorText(message: String) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun SettingsWorkspaceSection(
    syncStatus: MobileSyncStatus,
    onSyncNow: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    SettingsSectionCard {
        SettingsSectionTitle(title = stringResource(R.string.settings_workspace))
        if (syncStatus.isLocalMode) {
            Text(
                text = stringResource(R.string.settings_workspace_local_title),
                style = MaterialTheme.typography.titleMedium,
                color = colorScheme.onSurface,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                text = stringResource(R.string.settings_workspace_local_detail),
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurface.copy(alpha = 0.62f),
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.settings_workspace_server_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = colorScheme.onSurface,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    text = if (syncStatus.isOffline) {
                        stringResource(R.string.settings_sync_offline_short)
                    } else {
                        stringResource(R.string.settings_sync_up_to_date)
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = if (syncStatus.isOffline) colorScheme.error else TdayStatusSuccess,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
            if (syncStatus.isOffline) {
                SettingsDivider()
                SettingsSyncFactRow(
                    label = stringResource(R.string.settings_sync_last_synced_label),
                    value = lastSyncedText(syncStatus.lastSuccessfulSyncEpochMs),
                )
                Text(
                    text = stringResource(R.string.settings_sync_offline_message),
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurface.copy(alpha = 0.62f),
                )
                TextButton(
                    onClick = onSyncNow,
                    enabled = !syncStatus.isManualSyncing,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text(
                        text = if (syncStatus.isManualSyncing) {
                            stringResource(R.string.settings_syncing_now)
                        } else {
                            stringResource(R.string.settings_sync_now)
                        },
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsSyncFactRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.56f),
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
            fontWeight = FontWeight.ExtraBold,
        )
    }
}

@Composable
private fun lastSyncedText(epochMs: Long): String {
    return if (epochMs <= 0L) {
        stringResource(R.string.settings_sync_last_synced_never)
    } else {
        formatSyncTimestamp(epochMs)
    }
}

private fun formatSyncTimestamp(epochMs: Long): String {
    val zone = ZoneId.systemDefault()
    val dateTime = Instant.ofEpochMilli(epochMs).atZone(zone)
    val today = LocalDate.now(zone)
    val formatter = if (dateTime.toLocalDate() == today) {
        DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
    } else {
        DateTimeFormatter.ofPattern("MMM d, h:mm a", Locale.getDefault())
    }
    return dateTime.format(formatter)
}

@Composable
private fun SettingsSectionCard(
    modifier: Modifier = Modifier,
    borderColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
    content: @Composable ColumnScope.() -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, borderColor),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content,
        )
    }
}

@Composable
private fun SettingsSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun SettingsListRow(
    title: String,
    value: String?,
    onClick: () -> Unit,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    trailingTint: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.42f),
    showChevron: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold,
            color = titleColor,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            value?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f),
                )
            }
            if (showChevron) {
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = trailingTint,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun SettingsDivider(
    color: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(color),
    )
}

@Composable
private fun SettingsTopBar(
    onBack: () -> Unit,
    collapseProgress: Float,
) {
    val progress = collapseProgress.coerceIn(0f, 1f)
    val titleHandoffPoint = 0.9f
    val density = LocalDensity.current
    val expandedTitleHeight = lerp(56.dp, 0.dp, progress)
    val expandedTitleAlpha = ((titleHandoffPoint - progress) / titleHandoffPoint).coerceIn(0f, 1f)
    val collapsedTitleAlpha =
        ((progress - titleHandoffPoint) / (1f - titleHandoffPoint)).coerceIn(0f, 1f)
    val collapsedTitleShiftY = with(density) { (12.dp * (1f - collapsedTitleAlpha)).toPx() }
    val expandedTitleShiftY = with(density) { (-10.dp * (1f - expandedTitleAlpha)).toPx() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 2.dp),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            SettingsHeaderButton(
                icon = Icons.Rounded.ChevronLeft,
                contentDescription = stringResource(R.string.action_back),
                onClick = onBack,
                isBackButton = true,
            )
            if (collapsedTitleAlpha > 0.001f) {
                Row(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .graphicsLayer {
                            alpha = collapsedTitleAlpha
                            translationY = collapsedTitleShiftY
                        },
                ) {
                    Text(
                        text = stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(lerp(14.dp, 0.dp, progress)))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(expandedTitleHeight),
            contentAlignment = Alignment.BottomStart,
        ) {
            if (expandedTitleAlpha > 0.001f) {
                Box(
                    modifier = Modifier.graphicsLayer {
                        alpha = expandedTitleAlpha
                        translationY = expandedTitleShiftY
                    },
                ) {
                    Text(
                        text = stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsHeaderButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    isBackButton: Boolean = false,
) {
    val colorScheme = MaterialTheme.colorScheme
    val view = LocalView.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val isDarkTheme = colorScheme.background.luminance() < 0.5f
    val containerColor = if (isBackButton) {
        if (isDarkTheme) colorScheme.surface.copy(alpha = 0.94f) else Color.White.copy(alpha = 0.96f)
    } else {
        colorScheme.background
    }
    val buttonBorder = if (isBackButton) null else BorderStroke(1.dp, colorScheme.onSurface.copy(alpha = 0.38f))
    val buttonSize = if (isBackButton) TdayDimens.FabSize else 56.dp
    val iconSize = if (isBackButton) 36.dp else 28.dp
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.93f else 1f,
        label = "settingsHeaderButtonScale",
    )
    val offsetY by animateDpAsState(
        targetValue = if (pressed) 2.dp else 0.dp,
        label = "settingsHeaderButtonOffsetY",
    )

    Card(
        modifier = Modifier
            .offset(y = offsetY)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        onClick = {
            ViewCompat.performHapticFeedback(view, HapticFeedbackConstantsCompat.CLOCK_TICK)
            onClick()
        },
        interactionSource = interactionSource,
        shape = CircleShape,
        border = buttonBorder,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isBackButton) TdayDimens.FabElevation else 0.dp,
            pressedElevation = if (isBackButton) TdayDimens.FabPressedElevation else 0.dp,
        ),
    ) {
        Box(
            modifier = Modifier
                .size(buttonSize)
                .height(buttonSize),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = colorScheme.onSurface,
                modifier = Modifier.size(iconSize),
            )
        }
    }
}

private const val SETTINGS_TITLE_COLLAPSE_DISTANCE_DP = 180f

@Composable
private fun ThemeModeSelector(
    selectedThemeMode: AppThemeMode,
    onThemeModeSelected: (AppThemeMode) -> Unit,
) {
    val context = LocalContext.current
    TdaySegmentedSlider(
        options = AppThemeMode.entries,
        selectedOption = selectedThemeMode,
        onOptionSelected = onThemeModeSelected,
        label = { mode -> context.getString(mode.labelRes) },
    )
}

@Composable
private fun ReminderSelector(
    selectedReminder: ReminderOption,
    onReminderSelected: (ReminderOption) -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val view = LocalView.current
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .clickable {
                    ViewCompat.performHapticFeedback(view, HapticFeedbackConstantsCompat.CLOCK_TICK)
                    expanded = true
                }
                .padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.settings_default_reminder),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = colorScheme.onSurface,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = stringResource(selectedReminder.labelRes),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = colorScheme.secondary,
                )
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = colorScheme.onSurface.copy(alpha = 0.42f),
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        if (expanded) {
            TdayCenteredSelectorDialog(
                title = stringResource(R.string.settings_default_reminder),
                options = ReminderOption.entries,
                optionLabel = { option -> context.getString(option.labelRes) },
                optionSwatchColor = { option -> reminderSwatchColor(option) },
                isSelected = { option -> option == selectedReminder },
                onDismiss = { expanded = false },
                onOptionSelected = { option ->
                    ViewCompat.performHapticFeedback(
                        view,
                        HapticFeedbackConstantsCompat.CLOCK_TICK,
                    )
                    onReminderSelected(option)
                    expanded = false
                },
            )
        }
    }
}

/** Supported in-app languages (endonyms). `tag == null` = follow the device. */
private enum class AppLanguage(val tag: String?, val endonym: String) {
    SYSTEM(null, ""),
    EN("en", "English"),
    ES("es", "Español"),
    FR("fr", "Français"),
    DE("de", "Deutsch"),
    IT("it", "Italiano"),
    PT("pt", "Português"),
    RU("ru", "Русский"),
    ZH("zh", "中文"),
    JA("ja", "日本語"),
    MS("ms", "Bahasa Melayu"),
}

@Composable
private fun LanguageSelector() {
    val colorScheme = MaterialTheme.colorScheme
    val view = LocalView.current
    var expanded by remember { mutableStateOf(false) }
    val systemLabel = stringResource(R.string.settings_language_system_default)

    // AppCompatDelegate is the persisted source of truth (no extra store needed).
    val currentTag = AppCompatDelegate.getApplicationLocales().toLanguageTags()
    val current = AppLanguage.entries.firstOrNull {
        it.tag != null && currentTag.startsWith(it.tag!!)
    } ?: AppLanguage.SYSTEM

    fun labelFor(lang: AppLanguage) = if (lang == AppLanguage.SYSTEM) systemLabel else lang.endonym

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .clickable {
                    ViewCompat.performHapticFeedback(view, HapticFeedbackConstantsCompat.CLOCK_TICK)
                    expanded = true
                }
                .padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.settings_language),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = colorScheme.onSurface,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = labelFor(current),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = colorScheme.secondary,
                )
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = colorScheme.onSurface.copy(alpha = 0.42f),
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        if (expanded) {
            TdayCenteredSelectorDialog(
                title = stringResource(R.string.settings_language_dialog_title),
                options = AppLanguage.entries,
                optionLabel = { labelFor(it) },
                optionSwatchColor = { Color.Transparent },
                isSelected = { it == current },
                onDismiss = { expanded = false },
                onOptionSelected = { lang ->
                    ViewCompat.performHapticFeedback(view, HapticFeedbackConstantsCompat.CLOCK_TICK)
                    val locales = lang.tag
                        ?.let { LocaleListCompat.forLanguageTags(it) }
                        ?: LocaleListCompat.getEmptyLocaleList()
                    // Persists + recreates the activity instantly.
                    AppCompatDelegate.setApplicationLocales(locales)
                    expanded = false
                },
            )
        }
    }
}

private fun reminderSwatchColor(option: ReminderOption): Color {
    return when (option) {
        ReminderOption.NONE -> Color(0xFFB7BCC8)
        ReminderOption.AT_TIME -> Color(0xFF6EA8E1)
        ReminderOption.MINUTES_5 -> Color(0xFF7088C8)
        ReminderOption.MINUTES_10 -> Color(0xFF7D67B6)
        ReminderOption.MINUTES_15 -> Color(0xFFC7AA63)
        ReminderOption.MINUTES_30 -> Color(0xFFD39A82)
        ReminderOption.HOURS_1 -> Color(0xFF8DBB73)
        ReminderOption.HOURS_2 -> Color(0xFF67AAA7)
        ReminderOption.DAYS_1 -> Color(0xFF9A86CF)
        ReminderOption.DAYS_2 -> Color(0xFFC98299)
    }
}
