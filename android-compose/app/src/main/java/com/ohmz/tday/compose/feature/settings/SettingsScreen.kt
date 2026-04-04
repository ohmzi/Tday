package com.ohmz.tday.compose.feature.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.geometry.Offset
import androidx.core.view.HapticFeedbackConstantsCompat
import androidx.core.view.ViewCompat
import com.ohmz.tday.compose.R
import com.ohmz.tday.compose.BuildConfig
import com.ohmz.tday.compose.core.model.SessionUser
import com.ohmz.tday.compose.core.notification.ReminderOption
import com.ohmz.tday.compose.ui.theme.AppThemeMode

@Composable
fun SettingsScreen(
    user: SessionUser?,
    selectedThemeMode: AppThemeMode,
    selectedReminder: ReminderOption,
    adminAiSummaryEnabled: Boolean?,
    isAdminAiSummaryLoading: Boolean,
    isAdminAiSummarySaving: Boolean,
    adminAiSummaryError: String?,
    aiSummaryValidationError: String?,
    hasUpdate: Boolean = false,
    latestVersionName: String? = null,
    onThemeModeSelected: (AppThemeMode) -> Unit,
    onReminderSelected: (ReminderOption) -> Unit,
    onToggleAdminAiSummary: (Boolean) -> Unit,
    onDismissAiValidationError: () -> Unit,
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onOpenLatestRelease: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val isAdminUser = user?.role?.equals("ADMIN", ignoreCase = true) == true
    val scrollState = rememberScrollState()
    val density = LocalDensity.current
    val maxCollapsePx = with(density) { SETTINGS_TITLE_COLLAPSE_DISTANCE_DP.dp.toPx() }
    var headerCollapsePx by rememberSaveable { mutableFloatStateOf(0f) }
    val collapseProgressTarget = if (maxCollapsePx > 0f) {
        (headerCollapsePx / maxCollapsePx).coerceIn(0f, 1f)
    } else {
        0f
    }
    val nestedScrollConnection = remember(scrollState, maxCollapsePx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val deltaY = available.y
                if (deltaY < 0f) {
                    val previous = headerCollapsePx
                    val next = (previous - deltaY).coerceIn(0f, maxCollapsePx)
                    val consumed = next - previous
                    if (consumed > 0f) {
                        headerCollapsePx = next
                        return Offset(0f, -consumed)
                    }
                    return Offset.Zero
                }

                if (deltaY > 0f) {
                    if (scrollState.value > 0) return Offset.Zero
                    val previous = headerCollapsePx
                    val next = (previous - deltaY).coerceIn(0f, maxCollapsePx)
                    val consumed = previous - next
                    if (consumed > 0f) {
                        headerCollapsePx = next
                        return Offset(0f, consumed)
                    }
                }

                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (available.y < 0f && headerCollapsePx < maxCollapsePx) {
                    headerCollapsePx = maxCollapsePx
                    return available
                }
                if (available.y > 0f && scrollState.value == 0 && headerCollapsePx > 0f) {
                    headerCollapsePx = 0f
                    return available
                }
                return Velocity.Zero
            }
        }
    }
    val collapseProgress by animateFloatAsState(
        targetValue = collapseProgressTarget,
        label = "settingsTitleCollapseProgress",
    )

    Scaffold(
        containerColor = colorScheme.background,
        topBar = {
            SettingsTopBar(
                onBack = onBack,
                collapseProgress = collapseProgress,
            )
        },
        ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(colorScheme.background)
                .nestedScroll(nestedScrollConnection)
                .verticalScroll(scrollState)
                .padding(horizontal = 18.dp, vertical = 2.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SettingsProfileCard(
                user = user,
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
            }

            if (isAdminUser) {
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
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        if (isAdminAiSummaryLoading || adminAiSummaryEnabled == null) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Switch(
                                checked = adminAiSummaryEnabled,
                                onCheckedChange = onToggleAdminAiSummary,
                                enabled = !isAdminAiSummarySaving,
                            )
                        }
                    }
                    if (isAdminAiSummarySaving) {
                        Text(
                            text = stringResource(R.string.settings_saving_admin),
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurface.copy(alpha = 0.58f),
                        )
                    }
                    if (!adminAiSummaryError.isNullOrBlank()) {
                        Text(
                            text = adminAiSummaryError,
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.error,
                        )
                    }
                }
            }

            SettingsSectionCard {
                SettingsListRow(
                    title = stringResource(R.string.release_title),
                    value = "v${BuildConfig.VERSION_NAME}",
                    onClick = onOpenLatestRelease,
                )
                if (hasUpdate && latestVersionName != null) {
                    Text(
                        text = "v$latestVersionName available",
                        style = MaterialTheme.typography.labelSmall,
                        color = colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                    )
                }
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

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (aiSummaryValidationError != null) {
        AlertDialog(
            onDismissRequest = onDismissAiValidationError,
            title = {
                Text(
                    text = stringResource(R.string.settings_ai_unavailable_title),
                    fontWeight = FontWeight.SemiBold,
                )
            },
            text = {
                Text(text = aiSummaryValidationError)
            },
            confirmButton = {
                TextButton(onClick = onDismissAiValidationError) {
                    Text(stringResource(R.string.action_ok))
                }
            },
        )
    }
}

@Composable
private fun SettingsProfileCard(
    user: SessionUser?,
) {
    val colorScheme = MaterialTheme.colorScheme

    SettingsSectionCard {
        Text(
            text = user?.name ?: stringResource(R.string.settings_unknown_user),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = colorScheme.onSurface,
        )
        if (!user?.email.isNullOrBlank()) {
            Text(
                text = user?.email.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = colorScheme.onSurface.copy(alpha = 0.72f),
            )
        }
        Text(
            text = stringResource(R.string.settings_role_prefix) +
                (user?.role ?: stringResource(R.string.settings_role_default)),
            style = MaterialTheme.typography.bodySmall,
            color = colorScheme.onSurface.copy(alpha = 0.58f),
        )
    }
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
        fontWeight = FontWeight.SemiBold,
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
            fontWeight = FontWeight.SemiBold,
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
                icon = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.action_back),
                onClick = onBack,
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
                        fontWeight = FontWeight.Bold,
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
                        fontWeight = FontWeight.Bold,
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
) {
    val colorScheme = MaterialTheme.colorScheme
    val view = LocalView.current
    Card(
        onClick = {
            ViewCompat.performHapticFeedback(view, HapticFeedbackConstantsCompat.CLOCK_TICK)
            onClick()
        },
        shape = CircleShape,
        border = BorderStroke(1.dp, colorScheme.onSurface.copy(alpha = 0.38f)),
        colors = CardDefaults.cardColors(containerColor = colorScheme.background),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .height(56.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = colorScheme.onSurface,
                modifier = Modifier.size(28.dp),
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
    val colorScheme = MaterialTheme.colorScheme
    val view = LocalView.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = colorScheme.surfaceVariant.copy(alpha = 0.76f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppThemeMode.entries.forEach { mode ->
                val selected = mode == selectedThemeMode
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            color = if (selected) {
                                colorScheme.surface
                            } else {
                                Color.Transparent
                            },
                        )
                        .clickable {
                            ViewCompat.performHapticFeedback(view, HapticFeedbackConstantsCompat.CLOCK_TICK)
                            onThemeModeSelected(mode)
                        },
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = mode.label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (selected) colorScheme.onSurface else colorScheme.onSurface.copy(alpha = 0.58f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReminderSelector(
    selectedReminder: ReminderOption,
    onReminderSelected: (ReminderOption) -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val view = LocalView.current
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
                text = "Default reminder",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = colorScheme.onSurface,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = selectedReminder.label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = colorScheme.primary,
                )
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = colorScheme.onSurface.copy(alpha = 0.42f),
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            ReminderOption.entries.forEach { option ->
                val isSelected = option == selectedReminder
                DropdownMenuItem(
                    text = {
                        Text(
                            text = option.label,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    },
                    onClick = {
                        ViewCompat.performHapticFeedback(
                            view,
                            HapticFeedbackConstantsCompat.CLOCK_TICK,
                        )
                        onReminderSelected(option)
                        expanded = false
                    },
                    trailingIcon = if (isSelected) {
                        {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                tint = colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    } else {
                        null
                    },
                )
            }
        }
    }
}
