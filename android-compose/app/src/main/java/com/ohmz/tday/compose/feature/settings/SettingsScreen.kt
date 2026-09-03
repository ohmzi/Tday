package com.ohmz.tday.compose.feature.settings

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.biometric.BiometricManager
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
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import androidx.core.view.HapticFeedbackConstantsCompat
import androidx.core.view.ViewCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ohmz.tday.compose.BuildConfig
import com.ohmz.tday.compose.R
import com.ohmz.tday.compose.core.calendar.CalendarEntryPoint
import com.ohmz.tday.compose.core.data.AppSecurityPreferenceStore
import com.ohmz.tday.compose.core.data.auth.AuthRepository
import com.ohmz.tday.compose.core.data.db.hasUnmigratedPlaintextCache
import com.ohmz.tday.compose.core.data.server.VersionCheckResult
import com.ohmz.tday.compose.core.model.SecurityAnswerInput
import com.ohmz.tday.compose.core.model.SecurityQuestion
import com.ohmz.tday.compose.core.model.SecurityQuestionStatusResponse
import com.ohmz.tday.compose.core.model.SessionUser
import com.ohmz.tday.compose.core.notification.DayAheadOption
import com.ohmz.tday.compose.core.notification.NotificationOsState
import com.ohmz.tday.compose.core.notification.NotificationPreferenceStore
import com.ohmz.tday.compose.core.notification.NotificationToggleAction
import com.ohmz.tday.compose.core.notification.ReminderOption
import com.ohmz.tday.compose.core.notification.TaskReminderReceiver
import com.ohmz.tday.compose.core.notification.canPromptForNotificationPermission
import com.ohmz.tday.compose.core.notification.isNotificationOsAuthorized
import com.ohmz.tday.compose.core.notification.notificationToggleAction
import com.ohmz.tday.compose.core.notification.notificationToggleChecked
import com.ohmz.tday.compose.core.ui.LocalSnackbarManager
import com.ohmz.tday.compose.core.ui.TdayEmptyState
import com.ohmz.tday.compose.core.ui.TdayHeroTitleBlock
import com.ohmz.tday.compose.core.ui.TdayHeroToolbar
import com.ohmz.tday.compose.core.ui.TdaySearchCapsule
import com.ohmz.tday.compose.core.ui.rememberScrollHeroTitleCollapse
import com.ohmz.tday.compose.core.ui.tdayBarButtonContainerColor
import com.ohmz.tday.compose.core.ui.TdayHeroTitleMetrics
import com.ohmz.tday.compose.core.ui.tdayClosesSearchOnOutsideTap
import com.ohmz.tday.compose.feature.app.MobileSyncStatus
import com.ohmz.tday.compose.feature.app.ProfileEditResult
import com.ohmz.tday.compose.feature.auth.SecurityQuestionPicker
import com.ohmz.tday.compose.feature.guide.GuideHelpLink
import com.ohmz.tday.compose.feature.lock.canSatisfyAppLock
import com.ohmz.tday.compose.feature.settings.data.DataTransferCard
import com.ohmz.tday.compose.feature.widget.WidgetEntryPoint
import com.ohmz.tday.compose.ui.component.RootFeedTab
import com.ohmz.tday.compose.ui.component.TdayCenteredSelectorDialog
import com.ohmz.tday.compose.ui.component.TdaySegmentedSlider
import com.ohmz.tday.compose.ui.component.labelRes
import com.ohmz.tday.compose.ui.theme.AppThemeMode
import com.ohmz.tday.compose.ui.theme.TdayDimens
import com.ohmz.tday.compose.ui.theme.TdayStatusSuccess
import com.ohmz.tday.compose.ui.theme.TdayTitleIconDayAccent
import com.ohmz.tday.shared.guide.GuideTopicIds
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.unifiedpush.android.connector.UnifiedPush
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
    defaultHomeScreen: RootFeedTab,
    hasUpdate: Boolean = false,
    latestVersionName: String? = null,
    backendVersion: String? = null,
    versionCheckResult: VersionCheckResult? = null,
    onThemeModeSelected: (AppThemeMode) -> Unit,
    onDefaultHomeScreenSelected: (RootFeedTab) -> Unit,
    onReminderSelected: (ReminderOption) -> Unit,
    selectedDayAhead: DayAheadOption = DayAheadOption.OFF,
    onDayAheadSelected: (DayAheadOption) -> Unit = {},
    onSyncNow: () -> Unit,
    onToggleAiSummary: (Boolean) -> Unit,
    onBack: () -> Unit,
    onLogout: () -> Unit,
    /**
     * Leaving a local workspace is a MODE SWITCH, not a teardown — the rows stay
     * on the device. Separate from [onLogout] precisely because that one clears
     * them, which is what this row used to call.
     */
    onLeaveLocalWorkspace: () -> Unit,
    onOpenLatestRelease: () -> Unit,
    onOpenHelpGuide: () -> Unit,
    onUpdateName: suspend (String) -> ProfileEditResult,
    onChangePassword: suspend (String, String) -> ProfileEditResult,
    onForgotPassword: () -> Unit,
    onLoadSecurityQuestionStatus: suspend () -> SecurityQuestionStatusResponse?,
    onFetchSecurityQuestions: suspend () -> List<SecurityQuestion>,
    onUpdateSecurityQuestions: suspend (String, List<SecurityAnswerInput>) -> ProfileEditResult,
) {
    val colorScheme = MaterialTheme.colorScheme
    val scrollState = rememberScrollState()
    val heroCollapse = rememberScrollHeroTitleCollapse(scrollState = scrollState)
    val settingsTitle = stringResource(R.string.settings_title)
    // Scoped search: this screen's own rows, and nothing else. Settings has no
    // list to filter, so what the field narrows is the page itself — which also
    // means the only empty state it can reach is a search that matched nothing.
    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    // TdayHeroToolbar's row height, for the outside-tap guard: the bar is an
    // overlay on the same box as the content, so "below the bar" has to be
    // measured rather than inferred from the hierarchy.
    val pinnedToolbarHeightPx = with(LocalDensity.current) {
        TdayHeroTitleMetrics.ToolbarHeight.toPx()
    }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var searchNeedsFocus by remember { mutableStateOf(false) }
    val search = remember(searchQuery) { SettingsSearchScope(searchQuery) }
    val closeSearch = {
        searchExpanded = false
        searchQuery = ""
        searchNeedsFocus = false
    }
    BackHandler(enabled = searchExpanded) {
        closeSearch()
    }

    // A row is matched on the copy it actually shows, and on the heading it
    // sits under: searching "reminders" keeps the whole group rather than only
    // the one row that happens to draw the heading.
    val appearanceTitle = stringResource(R.string.settings_appearance)
    val behaviorTitle = stringResource(R.string.settings_behavior)
    val remindersTitle = stringResource(R.string.settings_reminders)
    // Whether a notification would actually arrive — the OS permission AND the
    // app's own switch. Owned here because the switch is in one card and the
    // settings it silences are in another; `NotificationsRow` is what keeps it
    // current. Seeded from the preference alone so the common case does not
    // flash dimmed before the OS status has been read.
    val notificationContext = LocalContext.current
    val notificationPreferenceStore = remember(notificationContext) {
        NotificationPreferenceStore(notificationContext.applicationContext)
    }
    // Read here rather than taken from `NotificationsRow`. That row lives in the
    // Feature-toggle card and drops out of composition whenever a search filters
    // it away, while the rows it dims live in another card — so a query like
    // "quiet" would keep a Reminders row on screen with nothing left to tell it
    // the switch is off. The row still reports its own flips for the immediate
    // case; this is the answer when the row is not there to.
    var notificationsDeliver by remember {
        mutableStateOf(
            notificationToggleChecked(
                readNotificationOsState(notificationContext, notificationPreferenceStore),
                notificationPreferenceStore.isEnabled(),
            ),
        )
    }
    val notificationsLifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(notificationsLifecycleOwner, notificationContext) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                // The OS permission is changed in Android's own Settings, with this
                // screen suspended; a resume is the only moment it can be noticed.
                notificationsDeliver = notificationToggleChecked(
                    readNotificationOsState(notificationContext, notificationPreferenceStore),
                    notificationPreferenceStore.isEnabled(),
                )
            }
        }
        notificationsLifecycleOwner.lifecycle.addObserver(observer)
        onDispose { notificationsLifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val languageTitle = stringResource(R.string.settings_language)
    val featureToggleTitle = stringResource(R.string.settings_feature_toggle)
    val privacyTitle = stringResource(R.string.settings_privacy)
    val aboutTitle = stringResource(R.string.settings_about)
    val releaseTitle = stringResource(R.string.release_title)
    val helpGuideTitle = stringResource(R.string.settings_help_guide)

    // The account card's rows edit one another's state — the open editor is
    // shared between them — so it is matched and kept as a whole rather than
    // being taken apart row by row.
    val showAccountCard = !isLocalMode && search.matches(
        stringResource(R.string.settings_account_name_label),
        stringResource(R.string.settings_account_username_label),
        stringResource(R.string.settings_account_password_label),
        stringResource(R.string.settings_account_security_questions_label),
    )
    val appearanceRows = listOf(
        SettingsEntry(
            key = "appearance",
            // The mode names as well as the heading: "dark" is what people type
            // here, and "Appearance" is not a word that occurs to anybody.
            visible = search.matches(
                appearanceTitle,
                *AppThemeMode.entries.map { stringResource(it.labelRes) }.toTypedArray(),
            ),
            // No guide topic covers theming — it is one control that explains itself.
            section = appearanceTitle,
        ) {
            ThemeModeSelector(
                selectedThemeMode = selectedThemeMode,
                onThemeModeSelected = onThemeModeSelected,
            )
        },
        SettingsEntry(
            key = "default-home-screen",
            visible = search.matches(
                behaviorTitle,
                stringResource(R.string.settings_default_home_screen),
                stringResource(R.string.root_feed_tab_scheduled_task_home),
                stringResource(R.string.root_feed_tab_floater),
            ),
            // The root feeds explain themselves from the same Home/Leaf language as the
            // in-app dock; no guide topic needed.
            section = behaviorTitle,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(TdayDimens.SpacingSm)) {
                Text(
                    text = stringResource(R.string.settings_default_home_screen),
                    style = MaterialTheme.typography.titleMedium,
                    color = colorScheme.onSurface,
                    fontWeight = FontWeight.ExtraBold,
                )
                RootFeedTabSelector(
                    selectedTab = defaultHomeScreen,
                    onTabSelected = onDefaultHomeScreenSelected,
                )
            }
        },
        SettingsEntry(
            key = "reminder",
            visible = search.matches(
                remindersTitle,
                stringResource(R.string.settings_default_reminder),
            ),
            section = remindersTitle,
            sectionHelpTopicId = GuideTopicIds.REMINDERS,
        ) {
            SettingsSilencedWhen(!notificationsDeliver) {
                ReminderSelector(
                    selectedReminder = selectedReminder,
                    onReminderSelected = onReminderSelected,
                )
            }
        },
        SettingsEntry(
            key = "day-ahead",
            visible = search.matches(remindersTitle, stringResource(R.string.day_ahead_setting)),
            section = remindersTitle,
            sectionHelpTopicId = GuideTopicIds.REMINDERS,
        ) {
            SettingsSilencedWhen(!notificationsDeliver) {
                DayAheadSelector(
                    selectedDayAhead = selectedDayAhead,
                    onDayAheadSelected = onDayAheadSelected,
                )
            }
        },
        SettingsEntry(
            key = "quiet-hours",
            visible = search.matches(remindersTitle, stringResource(R.string.settings_quiet_hours)),
            section = remindersTitle,
            sectionHelpTopicId = GuideTopicIds.REMINDERS,
        ) {
            SettingsSilencedWhen(!notificationsDeliver) {
                QuietHoursRow()
            }
        },
        SettingsEntry(
            key = "unified-push",
            // Server pushes via UnifiedPush are only meaningful in Server Mode.
            visible = !isLocalMode && search.matches(
                remindersTitle,
                stringResource(R.string.settings_unifiedpush_title),
            ),
            section = remindersTitle,
            sectionHelpTopicId = GuideTopicIds.REMINDERS,
        ) {
            // In the silenced group too: the in-app switch gates a UnifiedPush
            // delivery the same way it gates a local reminder (see
            // `UnifiedPushEntryPoint`), so leaving this one live would promise a
            // push that the switch above quietly drops.
            SettingsSilencedWhen(!notificationsDeliver) {
                UnifiedPushRow()
            }
        },
        SettingsEntry(
            key = "language",
            visible = search.matches(languageTitle),
            // Picking a language is picking a language; there is nothing to explain.
            section = languageTitle,
        ) {
            LanguageSelector()
        },
    ).filter { it.visible }

    val featureRows = listOf(
        SettingsEntry(
            key = "ai-summary",
            visible = search.matches(
                featureToggleTitle,
                stringResource(R.string.settings_ai_task_summary),
            ),
            section = featureToggleTitle,
            // One help link for the card, not one per row. It lands on the AI summary
            // topic — of this card's four rows only that one and the calendar mirror have
            // guide topics at all, and the summary is the row people arrive here asking
            // about.
            sectionHelpTopicId = GuideTopicIds.AI_SUMMARY,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SettingsRowIcon(R.drawable.ic_lucide_sparkles)
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
        },
        SettingsEntry(
            key = "resting-floaters",
            visible = search.matches(
                featureToggleTitle,
                stringResource(R.string.settings_resting_floaters),
            ),
            section = featureToggleTitle,
            sectionHelpTopicId = GuideTopicIds.AI_SUMMARY,
        ) {
            RestingFloatersRow()
        },
        SettingsEntry(
            key = "calendar-sync",
            visible = search.matches(
                featureToggleTitle,
                stringResource(R.string.settings_calendar_sync),
            ),
            section = featureToggleTitle,
            sectionHelpTopicId = GuideTopicIds.AI_SUMMARY,
        ) {
            DeviceCalendarSyncRow()
        },
        SettingsEntry(
            key = "notifications",
            visible = search.matches(
                featureToggleTitle,
                stringResource(R.string.settings_notifications),
            ),
            section = featureToggleTitle,
            sectionHelpTopicId = GuideTopicIds.AI_SUMMARY,
        ) {
            NotificationsRow(onDeliversChange = { notificationsDeliver = it })
        },
    ).filter { it.visible }

    val privacyRows = listOf(
        SettingsEntry(
            key = "screenshot-protection",
            visible = search.matches(
                privacyTitle,
                stringResource(R.string.settings_screenshot_protection),
            ),
            // Two device-local switches that say what they do on the row; the guide has
            // no topic for either.
            section = privacyTitle,
        ) {
            ScreenshotProtectionRow()
        },
        SettingsEntry(
            key = "app-lock",
            visible = search.matches(privacyTitle, stringResource(R.string.settings_app_lock)),
            section = privacyTitle,
        ) {
            AppLockRow()
            UnencryptedLegacyCacheWarning()
        },
    ).filter { it.visible }

    // What this install is: the sync state and the two version rows. Everything
    // that used to share the card with them — the data card, the guide, signing
    // out — now carries its own, so a query that hits one of those can no longer
    // drag this card open behind it.
    val aboutRows = listOf(
        SettingsEntry(
            key = "workspace",
            visible = search.matches(
                aboutTitle,
                stringResource(R.string.settings_workspace_local_title),
                stringResource(R.string.settings_workspace_server_title),
                stringResource(R.string.settings_sync_now),
            ),
            section = aboutTitle,
            // What this card explains is where the data lives and how it catches up —
            // which is the offline-sync topic, in both modes.
            sectionHelpTopicId = GuideTopicIds.OFFLINE_SYNC,
        ) {
            SettingsWorkspaceContent(
                syncStatus = syncStatus,
                onSyncNow = onSyncNow,
            )
        },
        SettingsEntry(
            key = "release",
            visible = search.matches(aboutTitle, releaseTitle),
            section = aboutTitle,
            sectionHelpTopicId = GuideTopicIds.OFFLINE_SYNC,
        ) {
            SettingsListRow(
                title = releaseTitle,
                value = stringResource(R.string.label_version_name, BuildConfig.VERSION_NAME),
                onClick = onOpenLatestRelease,
                icon = R.drawable.ic_lucide_info,
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
        },
        SettingsEntry(
            key = "server",
            visible = !isLocalMode && backendVersion != null &&
                search.matches(aboutTitle, stringResource(R.string.label_server)),
            section = aboutTitle,
            sectionHelpTopicId = GuideTopicIds.OFFLINE_SYNC,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Same lucide "server" rack the web app puts on this row, so the fact
                // reads identically across clients.
                SettingsRowIcon(R.drawable.ic_lucide_server)
                Text(
                    text = stringResource(R.string.label_server),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = colorScheme.onSurface,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(
                            R.string.label_version_name,
                            backendVersion.orEmpty(),
                        ),
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
        },
    ).filter { it.visible }

    // Alone on its card: the row already says "How-To & Tips", so a heading
    // over it would only say it twice.
    val helpGuideRows = listOf(
        SettingsEntry("help-guide", search.matches(helpGuideTitle)) {
            SettingsListRow(
                title = helpGuideTitle,
                value = null,
                onClick = onOpenHelpGuide,
                icon = R.drawable.ic_lucide_circle_help,
            )
        },
    ).filter { it.visible }

    // The way out of whichever mode this is. Local Mode has no session to end, so it gets
    // the two exits the web build already offers instead: leaving keeps the tasks in place,
    // deleting is the only thing on this screen with nothing behind it to recover from.
    var showDeleteLocalConfirm by rememberSaveable { mutableStateOf(false) }
    val signOutRows = listOf(
        SettingsEntry(
            key = "sign-out",
            visible = !isLocalMode && search.matches(stringResource(R.string.action_sign_out)),
        ) {
            SettingsListRow(
                title = stringResource(R.string.action_sign_out),
                value = null,
                onClick = onLogout,
                icon = R.drawable.ic_lucide_log_out,
                iconTint = colorScheme.error,
                titleColor = colorScheme.error,
                trailingTint = colorScheme.error.copy(alpha = 0.72f),
                showChevron = false,
            )
        },
        SettingsEntry(
            key = "leave-local",
            visible = isLocalMode &&
                search.matches(stringResource(R.string.settings_workspace_leave)),
        ) {
            SettingsListRow(
                title = stringResource(R.string.settings_workspace_leave),
                value = null,
                onClick = onLeaveLocalWorkspace,
                icon = R.drawable.ic_lucide_log_out,
                // Leaving really does keep every task on the device now — it drops
                // this session's hold and returns to mode selection, nothing more.
                // It was wired to `onLogout`, which clears all local data, so this
                // comment described the intent while the code did the opposite.
                iconTint = colorScheme.error,
                showChevron = false,
            )
        },
        SettingsEntry(
            key = "delete-local",
            visible = isLocalMode &&
                search.matches(stringResource(R.string.settings_workspace_delete)),
        ) {
            SettingsListRow(
                title = stringResource(R.string.settings_workspace_delete),
                value = null,
                onClick = { showDeleteLocalConfirm = true },
                icon = R.drawable.ic_lucide_trash_2,
                iconTint = colorScheme.error,
                titleColor = colorScheme.error,
                showChevron = false,
            )
        },
    ).filter { it.visible }

    // Server Mode only — see the web card's note: export and import move an
    // account's data, and a local workspace has no account to move it between.
    val showDataTransferCard = !isLocalMode && search.matches(
        stringResource(R.string.settings_data_title),
        stringResource(R.string.settings_data_download),
        stringResource(R.string.settings_data_import),
    )
    val hasMatches = showAccountCard || showDataTransferCard ||
        appearanceRows.isNotEmpty() || featureRows.isNotEmpty() ||
        privacyRows.isNotEmpty() || aboutRows.isNotEmpty() ||
        helpGuideRows.isNotEmpty() || signOutRows.isNotEmpty()

    Scaffold(containerColor = colorScheme.background) { padding ->
        Box(modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            // Tap the cards and the field goes away, as on the root feeds. The
            // toolbar is an overlay on this same box, so the guard is its row
            // height rather than a reported rect.
            .tdayClosesSearchOnOutsideTap(
                isSearchOpen = searchExpanded,
                barHeightPx = pinnedToolbarHeightPx,
                close = closeSearch,
            )) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colorScheme.background)
                .verticalScroll(scrollState)
                .padding(horizontal = 18.dp)
                .padding(bottom = 2.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TdayHeroTitleBlock(
                title = settingsTitle,
                icon = ImageVector.vectorResource(R.drawable.ic_lucide_sliders_horizontal),
                accentColor = MaterialTheme.colorScheme.primary,
                collapseProgress = heroCollapse.progress,
            )
            if (showAccountCard) {
                SettingsProfileCard(
                    user = user,
                    onUpdateName = onUpdateName,
                    onChangePassword = onChangePassword,
                    onForgotPassword = onForgotPassword,
                    onLoadSecurityQuestionStatus = onLoadSecurityQuestionStatus,
                    onFetchSecurityQuestions = onFetchSecurityQuestions,
                    onUpdateSecurityQuestions = onUpdateSecurityQuestions,
                )
            }

            SettingsFilteredCard(appearanceRows)
            SettingsFilteredCard(featureRows)
            SettingsFilteredCard(privacyRows)
            SettingsFilteredCard(aboutRows)

            if (showDataTransferCard) {
                DataTransferCard()
            }

            // The other platforms slot an Admin & Reset card in here. Android
            // has neither: no admin console entry point, and no cached-data
            // reset — so there is nothing for that card to hold.
            SettingsFilteredCard(helpGuideRows)
            SettingsFilteredCard(signOutRows)

            if (!hasMatches) {
                TdayEmptyState(
                    icon = R.drawable.ic_lucide_sliders_horizontal,
                    accentColor = colorScheme.primary,
                    title = stringResource(R.string.search_no_results_settings),
                    description = stringResource(R.string.search_no_results_body),
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        // Last, so it draws over the content passing behind it.
        TdayHeroToolbar(
            title = settingsTitle,
            collapseProgress = heroCollapse.progress,
            // Gone while the field is up: a back chevron beside an open search
            // is a second way out that leaves the screen rather than the query,
            // and it costs the field the width that makes a placeholder
            // readable.
            onBack = if (searchExpanded) null else onBack,
            backContentDescription = stringResource(R.string.action_back),
            modifier = Modifier.align(Alignment.TopStart),
            titleSuppressed = searchExpanded,
        ) {
            if (searchExpanded) {
                // The field takes the WHOLE bar — back chevron, title and action
                // cluster all give way to it, as they do on the root feeds and
                // on iOS's TimelineTopBar.
                val focusRequester = remember { FocusRequester() }
                LaunchedEffect(searchNeedsFocus) {
                    if (!searchNeedsFocus) return@LaunchedEffect
                    // Consumed on the way in, so returning to a screen that
                    // still has the field open does not re-open the keyboard
                    // with it.
                    searchNeedsFocus = false
                    focusRequester.requestFocus()
                }
                TdaySearchCapsule(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = stringResource(R.string.action_search_in, settingsTitle),
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                    // The one control in the row, so its X leaves the search —
                    // and leaving clears the query on the way out.
                    onClose = closeSearch,
                    trailingContentDescription = stringResource(R.string.action_close_search),
                )
            } else {
                SettingsBarButton(
                    // Only opens: the bar hands its row over to the field, so
                    // this button is not on screen to be tapped again.
                    onClick = {
                        searchExpanded = true
                        searchNeedsFocus = true
                    },
                    icon = ImageVector.vectorResource(R.drawable.ic_lucide_search),
                    contentDescription = stringResource(R.string.action_search),
                )
            }
        }

        if (showDeleteLocalConfirm) {
            DeleteLocalDataDialog(
                onDismiss = { showDeleteLocalConfirm = false },
                onDeleted = {
                    showDeleteLocalConfirm = false
                    // Nothing is left for this screen to show, and Local Mode is over —
                    // the same exit "Leave" takes, on top of an already-emptied
                    // workspace. The delete itself has happened by now; this is only
                    // the mode switch, so it must not be the clearing logout either.
                    onLeaveLocalWorkspace()
                },
            )
        }
        }
    }
}

/**
 * What the settings search matches a row against.
 *
 * The labels are the ones the row actually draws, passed in at the call site
 * rather than kept as a hand-written keyword list beside them: a keyword list
 * goes stale the first time a label is re-worded, and the copy on screen is the
 * copy people type.
 */
private class SettingsSearchScope(query: String) {
    private val needle = query.trim().lowercase(Locale.getDefault())

    fun matches(vararg labels: String): Boolean =
        needle.isBlank() || labels.any { it.lowercase(Locale.getDefault()).contains(needle) }
}

/**
 * One row of a settings card, the heading it sits under, and whether the search
 * left it standing.
 *
 * [section] belongs to the row rather than being drawn by whichever row happens
 * to come first: written inline, a search that filters that one row out takes the
 * heading — and the card's only "?" — with it, leaving the surviving rows in an
 * anonymous frame. Held here, the heading follows whichever row survives.
 */
private class SettingsEntry(
    val key: String,
    val visible: Boolean,
    val section: String? = null,
    val sectionHelpTopicId: String? = null,
    val content: @Composable ColumnScope.() -> Unit,
)

/**
 * A settings card whose rows the search can take out from under it.
 *
 * The dividers belong to the card rather than to the caller: written by hand
 * between the rows, a filtered-out row leaves its rule behind — a hairline
 * above nothing, or two of them stacked. A card the search emptied does not
 * draw at all, frame included.
 */
@Composable
private fun SettingsFilteredCard(rows: List<SettingsEntry>) {
    if (rows.isEmpty()) return

    // The first surviving row of each run of same-section rows draws the heading.
    val drawsHeading = rows.mapIndexed { index, row ->
        row.section != null && (index == 0 || rows[index - 1].section != row.section)
    }

    SettingsSectionCard {
        rows.forEachIndexed { index, row ->
            // Keyed, so a row that survives a change of query keeps the state
            // it was holding — an open editor, a half-typed field — instead of
            // inheriting the state of whichever row now sits at its index.
            key(row.key) {
                if (index > 0) {
                    SettingsDivider()
                }
                if (drawsHeading[index]) {
                    SettingsSectionTitle(
                        title = row.section.orEmpty(),
                        helpTopicId = row.sectionHelpTopicId,
                    )
                }
                row.content(this)
            }
        }
    }
}

/**
 * The circle this screen's toolbar actions sit in.
 *
 * A local copy of the timeline screen's `TodayHeaderButton`, which is private to
 * `TodoListScreen` and has no shared home yet — the fill, the size and the lift
 * come from the same tokens as the back button beside it, so the two match
 * whatever the scheme does with them.
 */
@Composable
private fun SettingsBarButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
) {
    val view = LocalView.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.93f else 1f,
        label = "settingsBarButtonScale",
    )
    val offsetY by animateDpAsState(
        targetValue = if (pressed) 2.dp else 0.dp,
        label = "settingsBarButtonOffsetY",
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
        colors = CardDefaults.cardColors(containerColor = tdayBarButtonContainerColor()),
        elevation = CardDefaults.cardElevation(
            defaultElevation = TdayDimens.BarButtonElevation,
            pressedElevation = 0.dp,
        ),
    ) {
        Box(
            modifier = Modifier.size(TdayDimens.FabSize),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

private enum class SettingsAccountEditor { None, Name, Password, SecurityQuestions }

@Composable
private fun SettingsProfileCard(
    user: SessionUser?,
    onUpdateName: suspend (String) -> ProfileEditResult,
    onChangePassword: suspend (String, String) -> ProfileEditResult,
    onForgotPassword: () -> Unit,
    onLoadSecurityQuestionStatus: suspend () -> SecurityQuestionStatusResponse?,
    onFetchSecurityQuestions: suspend () -> List<SecurityQuestion>,
    onUpdateSecurityQuestions: suspend (String, List<SecurityAnswerInput>) -> ProfileEditResult,
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
        AccountSecurityQuestionsSection(
            isEditing = activeEditor == SettingsAccountEditor.SecurityQuestions,
            onBeginEdit = { activeEditor = SettingsAccountEditor.SecurityQuestions },
            onDone = { activeEditor = SettingsAccountEditor.None },
            onLoadStatus = onLoadSecurityQuestionStatus,
            onFetchQuestions = onFetchSecurityQuestions,
            onSubmit = onUpdateSecurityQuestions,
        )

        SettingsDivider()
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Not tappable, so no glyph — but it keeps the slot so its label lines up with
            // the iconned rows above it.
            SettingsRowIcon(null)
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
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SettingsRowIcon(R.drawable.ic_lucide_user)
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
                SettingsPillButton(
                    text = stringResource(R.string.action_edit),
                    icon = R.drawable.ic_lucide_square_pen,
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
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsRowIcon(R.drawable.ic_lucide_at_sign)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            AccountFieldLabel(stringResource(R.string.settings_account_username_label))
            Text(
                text = username,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
            )
        }
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
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SettingsRowIcon(R.drawable.ic_lucide_lock)
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
                SettingsPillButton(
                    text = stringResource(R.string.settings_account_change_password),
                    icon = R.drawable.ic_lucide_lock,
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
                    label = stringResource(R.string.settings_account_confirm_new_password),
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
private fun AccountSecurityQuestionsSection(
    isEditing: Boolean,
    onBeginEdit: () -> Unit,
    onDone: () -> Unit,
    onLoadStatus: suspend () -> SecurityQuestionStatusResponse?,
    onFetchQuestions: suspend () -> List<SecurityQuestion>,
    onSubmit: suspend (String, List<SecurityAnswerInput>) -> ProfileEditResult,
) {
    val colorScheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()

    var status by remember { mutableStateOf<SecurityQuestionStatusResponse?>(null) }
    var questions by remember { mutableStateOf<List<SecurityQuestion>>(emptyList()) }
    var questionId1 by remember { mutableStateOf<Int?>(null) }
    var questionId2 by remember { mutableStateOf<Int?>(null) }
    var questionId3 by remember { mutableStateOf<Int?>(null) }
    var answer1 by remember { mutableStateOf("") }
    var answer2 by remember { mutableStateOf("") }
    var answer3 by remember { mutableStateOf("") }
    var current by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val distinctError = stringResource(R.string.security_questions_distinct_required)
    val answersError = stringResource(R.string.security_questions_answers_required)
    val passwordRequiredError = stringResource(R.string.settings_account_current_password_required)

    // Already-configured accounts must confirm with their password; legacy accounts
    // that never set questions can do so here without one.
    val configured = status?.let { !it.requireSecurityQuestions } ?: false

    LaunchedEffect(Unit) {
        if (status == null) status = onLoadStatus()
    }

    // On open: load the catalogue once, then seed the three selects from the user's
    // existing questions (filling gaps with the first unused entries). On close: wipe
    // the sensitive draft.
    LaunchedEffect(isEditing) {
        if (isEditing) {
            if (questions.isEmpty()) questions = onFetchQuestions()
            val preferred =
                status?.questionIds.orEmpty().filter { id -> questions.any { it.id == id } }
            val filler = questions.map { it.id }.filter { it !in preferred }
            val seeded = (preferred + filler).distinct().take(3)
            questionId1 = seeded.getOrNull(0)
            questionId2 = seeded.getOrNull(1)
            questionId3 = seeded.getOrNull(2)
        } else {
            current = ""
            answer1 = ""
            answer2 = ""
            answer3 = ""
            error = null
        }
    }

    val canSave = !busy &&
            questionId1 != null && questionId2 != null && questionId3 != null &&
            setOfNotNull(questionId1, questionId2, questionId3).size == 3 &&
            answer1.isNotBlank() && answer2.isNotBlank() && answer3.isNotBlank() &&
            (!configured || current.isNotBlank())

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SettingsRowIcon(R.drawable.ic_lucide_shield_question)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                AccountFieldLabel(stringResource(R.string.settings_account_security_questions_label))
                Text(
                    text = when {
                        status == null -> "—"
                        configured -> stringResource(R.string.settings_account_security_questions_configured)
                        else -> stringResource(R.string.settings_account_security_questions_not_configured)
                    },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = colorScheme.onSurface.copy(alpha = 0.8f),
                )
            }
            if (!isEditing) {
                SettingsPillButton(
                    text = stringResource(R.string.settings_account_change_security_questions),
                    icon = R.drawable.ic_lucide_shield,
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
                if (configured) {
                    AccountPasswordField(
                        value = current,
                        onValueChange = {
                            current = it
                            error = null
                        },
                        label = stringResource(R.string.settings_account_current_password),
                        imeAction = ImeAction.Next,
                    )
                }
                SecurityQuestionPicker(
                    label = stringResource(R.string.security_questions_question_1),
                    questions = questions,
                    excludeIds = setOfNotNull(questionId2, questionId3),
                    selectedId = questionId1,
                    onSelected = { questionId1 = it; error = null },
                    answer = answer1,
                    onAnswerChange = { answer1 = it; error = null },
                )
                SecurityQuestionPicker(
                    label = stringResource(R.string.security_questions_question_2),
                    questions = questions,
                    excludeIds = setOfNotNull(questionId1, questionId3),
                    selectedId = questionId2,
                    onSelected = { questionId2 = it; error = null },
                    answer = answer2,
                    onAnswerChange = { answer2 = it; error = null },
                )
                SecurityQuestionPicker(
                    label = stringResource(R.string.security_questions_question_3),
                    questions = questions,
                    excludeIds = setOfNotNull(questionId1, questionId2),
                    selectedId = questionId3,
                    onSelected = { questionId3 = it; error = null },
                    answer = answer3,
                    onAnswerChange = { answer3 = it; error = null },
                )
                error?.let { AccountErrorText(it) }
                AccountEditorActions(
                    busy = busy,
                    canSave = canSave,
                    onCancel = onDone,
                    onSave = {
                        val id1 = questionId1
                        val id2 = questionId2
                        val id3 = questionId3
                        when {
                            id1 == null || id2 == null || id3 == null ||
                                    setOf(id1, id2, id3).size != 3 -> error = distinctError

                            answer1.isBlank() || answer2.isBlank() || answer3.isBlank() ->
                                error = answersError

                            configured && current.isBlank() -> error = passwordRequiredError

                            else -> scope.launch {
                                busy = true
                                error = null
                                val answers = listOf(
                                    SecurityAnswerInput(questionId = id1, answer = answer1.trim()),
                                    SecurityAnswerInput(questionId = id2, answer = answer2.trim()),
                                    SecurityAnswerInput(questionId = id3, answer = answer3.trim()),
                                )
                                when (val result =
                                    onSubmit(if (configured) current else "", answers)) {
                                    is ProfileEditResult.Success -> {
                                        status = SecurityQuestionStatusResponse(
                                            questionIds = listOf(id1, id2, id3),
                                            requireSecurityQuestions = false,
                                        )
                                        onDone()
                                    }

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
                    imageVector = if (revealed) ImageVector.vectorResource(R.drawable.ic_lucide_eye_off) else ImageVector.vectorResource(
                        R.drawable.ic_lucide_eye
                    ),
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

/**
 * The one button shape this screen uses for "change this without leaving the row" — the
 * profile card's Edit and Change, and every row whose right-hand side is a value the user
 * picks. Ported measurement-for-measurement from iOS's `SettingsInlineEditButton`, which is
 * what the three platforms are being unified onto: a continuous capsule of the secondary
 * accent at 12%, heavy 12sp glyph and 14sp label 5dp apart, 34 tall on 14 of side padding.
 *
 * A chevron is left to rows that navigate somewhere. This shape says the value changes here.
 */
@Composable
private fun SettingsPillButton(
    text: String,
    @DrawableRes icon: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val view = LocalView.current
    Row(
        modifier = modifier
            // 34dp is the PILL, matching iOS. The touch target is not: a bare
            // `clickable` gets none of the `minimumInteractiveComponentSize()` an
            // M3 Button would apply for it, so the tappable area was the 34dp box
            // — under Android's 48dp minimum. The extra height is claimed outside
            // the painted shape so the pill still looks 34 tall.
            .sizeIn(minHeight = 48.dp)
            .wrapContentHeight()
            .clip(CircleShape)
            .background(colorScheme.secondary.copy(alpha = 0.12f))
            .clickable {
                ViewCompat.performHapticFeedback(view, HapticFeedbackConstantsCompat.CLOCK_TICK)
                onClick()
            }
            .height(34.dp)
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = ImageVector.vectorResource(icon),
            contentDescription = null, // decorative: the pill's label carries the meaning
            tint = colorScheme.secondary,
            modifier = Modifier.size(12.dp),
        )
        Text(
            text = text,
            fontSize = 14.sp,
            fontWeight = FontWeight.ExtraBold,
            color = colorScheme.secondary,
            maxLines = 1,
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
private fun SettingsWorkspaceContent(
    syncStatus: MobileSyncStatus,
    onSyncNow: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
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
internal fun SettingsSectionCard(
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
        elevation = CardDefaults.cardElevation(
            defaultElevation = TdayDimens.SettingsCardElevation,
        ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content,
        )
    }
}

@Composable
internal fun SettingsSectionTitle(title: String, helpTopicId: String? = null) {
    if (helpTopicId == null) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        return
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        GuideHelpLink(helpTopicId)
    }
}

@Composable
internal fun SettingsListRow(
    title: String,
    value: String?,
    onClick: () -> Unit,
    @DrawableRes icon: Int? = null,
    iconTint: Color = MaterialTheme.colorScheme.secondary,
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
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsRowIcon(icon, iconTint)
        Text(
            text = title,
            modifier = Modifier.weight(1f),
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
                    imageVector = ImageVector.vectorResource(R.drawable.ic_lucide_chevron_right),
                    contentDescription = null,
                    tint = trailingTint,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
internal fun SettingsDivider(
    color: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(color),
    )
}

/**
 * Leading glyph for a settings row. A null [icon] reserves the same width without drawing
 * anything, so a row that carries no glyph still lines its label up with the ones that do.
 */
@Composable
private fun SettingsRowIcon(
    @DrawableRes icon: Int?,
    tint: Color = MaterialTheme.colorScheme.secondary,
) {
    if (icon != null) {
        Icon(
            imageVector = ImageVector.vectorResource(icon),
            contentDescription = null, // decorative: the row's label carries the meaning
            tint = tint,
            modifier = Modifier.size(TdayDimens.IconSm),
        )
    } else {
        Spacer(modifier = Modifier.size(TdayDimens.IconSm))
    }
    Spacer(modifier = Modifier.width(TdayDimens.SpacingXl))
}


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

/**
 * Which root feed (Scheduled or Floaters) opens on a fresh cold launch. Same
 * [TdaySegmentedSlider] shape as [ThemeModeSelector] — a named, mutually-exclusive choice, not
 * a Switch — and reuses [RootFeedTab]'s own labels so the wording matches the in-app dock.
 */
@Composable
private fun RootFeedTabSelector(
    selectedTab: RootFeedTab,
    onTabSelected: (RootFeedTab) -> Unit,
) {
    val context = LocalContext.current
    TdaySegmentedSlider(
        options = listOf(RootFeedTab.SCHEDULED_TASK_HOME, RootFeedTab.FLOATER_TASK_HOME),
        selectedOption = selectedTab,
        onOptionSelected = onTabSelected,
        label = { tab -> context.getString(tab.labelRes()) },
    )
}

/**
 * "Hold reminders between HH:MM and HH:MM" — entirely local. The receiver re-arms any
 * reminder that would fire in the window for the window end.
 */
/** Toggle for the "resting floaters" display cue (dim untouched Anytime tasks). */
@Composable
private fun RestingFloatersRow() {
    val colorScheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val store = remember { com.ohmz.tday.compose.core.data.RestingFloatersPreferenceStore(context.applicationContext) }
    var enabled by remember { mutableStateOf(store.isEnabled()) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsRowIcon(R.drawable.ic_lucide_waves)
        Text(
            text = stringResource(R.string.settings_resting_floaters),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold,
            color = colorScheme.onSurface,
        )
        Switch(
            checked = enabled,
            onCheckedChange = {
                enabled = it
                store.setEnabled(it)
            },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = colorScheme.secondary,
                checkedBorderColor = Color.Transparent,
            ),
        )
    }
}

/**
 * FLAG_SECURE toggle. Default on; Android cannot hide only the recents thumbnail, so this also
 * blocks deliberate screenshots — which is exactly why it is a setting and not a hard-coded flag.
 * Takes effect on the next foreground (MainActivity re-applies it in onStart).
 */
@Composable
private fun ScreenshotProtectionRow() {
    val context = LocalContext.current
    val store = remember { AppSecurityPreferenceStore(context.applicationContext) }
    var enabled by remember { mutableStateOf(store.isScreenshotProtectionEnabled()) }

    SettingsToggleRow(
        icon = R.drawable.ic_lucide_eye_off,
        title = stringResource(R.string.settings_screenshot_protection),
        checked = enabled,
        onCheckedChange = {
            enabled = it
            store.setScreenshotProtectionEnabled(it)
        },
    )
}

/**
 * Opt-in biometric/device-credential gate, default off. Turning it on is refused when the device
 * has no screen lock to authenticate against, so the user cannot lock themselves out.
 */
@Composable
private fun AppLockRow() {
    val colorScheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val store = remember { AppSecurityPreferenceStore(context.applicationContext) }
    val widgetEntryPoint = remember {
        EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
    }
    var enabled by remember { mutableStateOf(store.isAppLockEnabled()) }
    var showUnavailable by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SettingsToggleRow(
            icon = R.drawable.ic_lucide_shield,
            title = stringResource(R.string.settings_app_lock),
            checked = enabled,
            onCheckedChange = { requested ->
                if (requested && !canSatisfyAppLock(BiometricManager.from(context))) {
                    showUnavailable = true
                    return@SettingsToggleRow
                }
                showUnavailable = false
                enabled = requested
                store.setAppLockEnabled(requested)
                // Immediate effect: the home-screen widgets read this flag fresh on every
                // render, but nothing else would prompt a render right now — without this
                // they'd keep showing task content until the next unrelated sync.
                widgetEntryPoint.todayTasksWidgetRefresher().requestRefresh()
                widgetEntryPoint.floaterTasksWidgetRefresher().requestRefresh()
            },
        )
        if (showUnavailable) {
            Text(
                text = stringResource(R.string.settings_app_lock_unavailable),
                style = MaterialTheme.typography.labelSmall,
                color = colorScheme.error,
            )
        }
    }
}

/**
 * Renders only when the pre-encryption cache file is still on disk — a migration that failed often
 * enough to be abandoned, or one still waiting for the pending-mutation queue to drain.
 *
 * It exists because that state is otherwise invisible: the file is readable to anyone who images the
 * device and it may hold offline edits the server has never seen, and a `Log.e` is not a way to tell
 * the owner of the data that it is exposed.
 */
@Composable
private fun UnencryptedLegacyCacheWarning() {
    val context = LocalContext.current
    // Read once per composition entry; the migration only ever runs at database injection time, so
    // this cannot change while Settings is open.
    val stranded = remember { hasUnmigratedPlaintextCache(context.applicationContext) }
    if (!stranded) return

    val colorScheme = MaterialTheme.colorScheme
    SettingsDivider()
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = stringResource(R.string.settings_legacy_cache_warning_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold,
            color = colorScheme.error,
        )
        Text(
            text = stringResource(R.string.settings_legacy_cache_warning_body),
            style = MaterialTheme.typography.bodySmall,
            color = colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Opt-in one-way mirror of scheduled tasks into a dedicated "T'Day" calendar on the device.
 *
 * Default off, and turning it on requests calendar permission first — a denied grant leaves the
 * toggle off rather than silently enabling a mirror that cannot write. Turning it off deletes the
 * calendar and everything T'Day put in it.
 *
 * Floaters are excluded by the sync itself: they have no due date, so they have nothing to place
 * on a calendar.
 */
@Composable
private fun DeviceCalendarSyncRow() {
    val colorScheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val entryPoint = remember {
        EntryPointAccessors.fromApplication(context.applicationContext, CalendarEntryPoint::class.java)
    }
    val syncManager = remember { entryPoint.calendarSyncManager() }
    val preferenceStore = remember { entryPoint.calendarSyncPreferenceStore() }

    var enabled by remember { mutableStateOf(preferenceStore.isEnabled()) }
    var showPermissionDenied by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        // Both are needed: the mirror reads the calendar to find its own calendar row before it
        // can write events into it.
        val granted = grants[Manifest.permission.READ_CALENDAR] == true &&
            grants[Manifest.permission.WRITE_CALENDAR] == true
        if (granted) {
            showPermissionDenied = false
            enabled = true
            scope.launch { syncManager.enable() }
        } else {
            showPermissionDenied = true
            enabled = false
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SettingsToggleRow(
            icon = R.drawable.ic_lucide_calendar,
            title = stringResource(R.string.settings_calendar_sync),
            checked = enabled,
            onCheckedChange = { requested ->
                if (!requested) {
                    showPermissionDenied = false
                    enabled = false
                    scope.launch { syncManager.disable() }
                    return@SettingsToggleRow
                }
                // Optimistically flip only after the grant lands, so a denied dialog does not
                // leave the switch on with nothing behind it.
                permissionLauncher.launch(
                    arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR),
                )
            },
        )
        if (showPermissionDenied) {
            Text(
                text = stringResource(R.string.settings_calendar_sync_permission_denied),
                style = MaterialTheme.typography.labelSmall,
                color = colorScheme.error,
            )
        }
    }
}

/**
 * T'Day's own notification gate, sitting on top of Android's.
 *
 * Two bits of state: the OS authorization and the stored preference. The switch shows the AND
 * of them, so a user whose OS permission was revoked in Settings sees OFF the next time they
 * come back — both are re-read on every resume, because nothing tells an app that its
 * notifications were turned off elsewhere. Flipping it OFF stores the preference and every
 * posting site drops its notification, which is what makes this a gate rather than a readout.
 *
 * Turning it ON with no permission asks the OS; where Android will no longer show that dialog
 * there is nothing to ask, so the tap opens the app's notification settings instead of failing
 * silently. Either way the tap is recorded as the opt-in, so the switch comes back ON by itself
 * once the grant lands rather than needing a second tap the user has no reason to expect.
 */
@Composable
private fun NotificationsRow(onDeliversChange: (Boolean) -> Unit = {}) {
    val colorScheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val store = remember { NotificationPreferenceStore(context.applicationContext) }
    var preferenceEnabled by remember { mutableStateOf(store.isEnabled()) }
    var osState by remember { mutableStateOf(readNotificationOsState(context, store)) }
    var hint by remember { mutableStateOf(NotificationHint.None) }

    // Mirrored out to the Reminders card, which holds the settings this switch
    // silences and has no other way to know that it is off. Both halves of the
    // answer live in here — the OS bit is re-read on every resume — so the card
    // reads it from here rather than assembling a second copy that could drift.
    val delivers = notificationToggleChecked(osState, preferenceEnabled)
    LaunchedEffect(delivers) { onDeliversChange(delivers) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                osState = readNotificationOsState(context, store)
                // Re-read alongside the OS bit rather than seeded once: this row is not
                // guaranteed to stay the only writer of the preference, and it survives a
                // navigation away and back within the same back-stack entry.
                preferenceEnabled = store.isEnabled()
                if (osState.authorized) hint = NotificationHint.None
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        store.markPermissionRequested()
        if (granted) {
            // The tap was the opt-in; the preference follows the grant rather than needing a
            // second flip once the dialog is gone.
            preferenceEnabled = true
            store.setEnabled(true)
            hint = NotificationHint.None
        } else {
            // Denied with the rationale still not on offer is the shape of a dialog Android
            // did not show, or will not show again; a denial that leaves the rationale
            // standing is just the first "no" and can be asked once more.
            val activity = context.findActivity()
            val rationaleStillOffered = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                activity != null &&
                ActivityCompat.shouldShowRequestPermissionRationale(
                    activity,
                    Manifest.permission.POST_NOTIFICATIONS,
                )
            if (!rationaleStillOffered) store.markPermissionPromptExhausted()
            hint = NotificationHint.Declined
        }
        osState = readNotificationOsState(context, store)
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SettingsRowIcon(R.drawable.ic_lucide_bell)
            Text(
                text = stringResource(R.string.settings_notifications),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = colorScheme.onSurface,
            )
            // The card's own "?" leads on ai-summary, which is a different section of the
            // guide; this switch gates everything under Recurrence & reminders, so it carries
            // the link to that section's head itself.
            GuideHelpLink(GuideTopicIds.NOTIFICATIONS)
            Switch(
                checked = notificationToggleChecked(osState, preferenceEnabled),
                onCheckedChange = { requested ->
                    when (notificationToggleAction(requested, osState)) {
                        NotificationToggleAction.EnablePreference -> {
                            preferenceEnabled = true
                            store.setEnabled(true)
                            hint = NotificationHint.None
                        }

                        NotificationToggleAction.DisablePreference -> {
                            preferenceEnabled = false
                            store.setEnabled(false)
                            hint = NotificationHint.None
                        }

                        NotificationToggleAction.RequestOsPermission -> {
                            store.markPermissionRequested()
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }

                        NotificationToggleAction.OpenOsSettings -> {
                            // The tap was the opt-in even though the grant has to happen in
                            // another app, so store it now: the switch stays visually OFF
                            // while Android says no, and flips itself ON on the resume after
                            // they allow it — without this they would come back to the same
                            // OFF switch and no reason why.
                            preferenceEnabled = true
                            store.setEnabled(true)
                            // The hint stays up after they come back: if nothing changed over
                            // there, the switch is still off and the reason is still true.
                            hint = NotificationHint.Blocked
                            openAppNotificationSettings(context)
                        }
                    }
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = colorScheme.secondary,
                    checkedBorderColor = Color.Transparent,
                ),
            )
        }
        if (hint != NotificationHint.None && !osState.authorized) {
            Text(
                text = stringResource(
                    when (hint) {
                        // Declining a dialog is not the same as having been switched off in
                        // Android settings, and the user was never there — saying so would
                        // point them at a screen they cannot reach from this row.
                        NotificationHint.Declined -> R.string.settings_notifications_declined
                        else -> R.string.settings_notifications_blocked
                    },
                ),
                style = MaterialTheme.typography.labelSmall,
                color = colorScheme.error,
            )
        }
    }
}

/**
 * Dims a settings row and stops it taking touches, for a setting the notification
 * switch further down the screen has silenced.
 *
 * Every row this wraps schedules a notification, and the master switch lives in
 * another card: without this a 7am digest and a default offset could be picked
 * and nothing would ever arrive, with nothing on screen saying why. Dimmed and
 * untappable says it in the one language everyone reads — the same bargain iOS
 * makes with `.opacity(0.45).disabled(...)`.
 *
 * Compose has no blanket `disabled`, so the alpha and the pointer trap live here
 * together rather than being repeated per row. The trap consumes on the INITIAL
 * pass, before anything inside can react.
 */
@Composable
private fun SettingsSilencedWhen(silenced: Boolean, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (silenced) 0.45f else 1f),
    ) {
        content()
        if (silenced) {
            // A transparent `clickable` over the row, NOT a pointer trap that consumes on
            // the Initial pass. Consuming there also reached the `verticalScroll` above —
            // `awaitPointerSlopOrCancellation` bails on an already-consumed change — so a
            // drag started anywhere on these four rows refused to scroll the page, turning
            // the middle of Settings into a dead band. `clickable` swallows the tap and
            // leaves the scroll alone, which is how every other row in a scrolling list
            // manages both at once.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClickLabel = null,
                    ) {
                        // Deliberately nothing: the switch two cards down is the only way
                        // to bring these back, and it says so on its own row.
                    },
            )
        }
    }
}

/** Why the notification switch is refusing to come on, in the words the row shows for it. */
private enum class NotificationHint { None, Declined, Blocked }

private fun readNotificationOsState(
    context: Context,
    store: NotificationPreferenceStore,
): NotificationOsState {
    val sdkInt = Build.VERSION.SDK_INT
    val permissionGranted = sdkInt < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
    val activity = context.findActivity()
    return NotificationOsState(
        authorized = isNotificationOsAuthorized(
            sdkInt = sdkInt,
            notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled(),
            permissionGranted = permissionGranted,
            reminderChannelEnabled = isReminderChannelEnabled(context),
        ),
        canPrompt = canPromptForNotificationPermission(
            sdkInt = sdkInt,
            permissionGranted = permissionGranted,
            promptExhausted = store.hasExhaustedPermissionPrompt(),
            // Below 33 the helper discards this outright, and POST_NOTIFICATIONS is not a
            // runtime permission there — asking anyway would be a binder round-trip per
            // resume for a value nothing reads. No Activity means no dialog either, so the
            // tap has to fall through to Settings.
            shouldShowRationale = sdkInt >= Build.VERSION_CODES.TIRAMISU &&
                activity != null &&
                ActivityCompat.shouldShowRequestPermissionRationale(
                    activity,
                    Manifest.permission.POST_NOTIFICATIONS,
                ),
        ),
    )
}

/**
 * The channel every gated post uses. TdayApplication creates it at startup, so a missing one
 * means the app has not finished booting rather than a user who switched it off — that reads
 * as enabled, because showing the switch OFF for it would be a lie about what the user did.
 */
private fun isReminderChannelEnabled(context: Context): Boolean {
    val channel = NotificationManagerCompat.from(context)
        .getNotificationChannel(TaskReminderReceiver.CHANNEL_ID)
        ?: return true
    return channel.importance != NotificationManager.IMPORTANCE_NONE
}

private fun openAppNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    // A device with that screen missing would otherwise crash the app on a settings tap.
    runCatching { context.startActivity(intent) }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun SettingsToggleRow(
    @DrawableRes icon: Int,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsRowIcon(icon)
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold,
            color = colorScheme.onSurface,
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = colorScheme.secondary,
                checkedBorderColor = Color.Transparent,
            ),
        )
    }
}

@Composable
private fun QuietHoursRow() {
    val colorScheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val store = remember { com.ohmz.tday.compose.core.notification.QuietHoursPreferenceStore(context.applicationContext) }
    var enabled by remember { mutableStateOf(store.isEnabled()) }
    var startMinute by remember { mutableStateOf(store.getStartMinute()) }
    var endMinute by remember { mutableStateOf(store.getEndMinute()) }

    fun fmt(minute: Int): String = "%02d:%02d".format(minute / 60, minute % 60)
    fun pickTime(current: Int, onPicked: (Int) -> Unit) {
        android.app.TimePickerDialog(
            context,
            { _, hour, min -> onPicked(hour * 60 + min) },
            current / 60,
            current % 60,
            true,
        ).show()
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SettingsRowIcon(R.drawable.ic_lucide_moon)
            Text(
                text = stringResource(R.string.settings_quiet_hours),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = colorScheme.onSurface,
            )
            Switch(
                checked = enabled,
                onCheckedChange = {
                    enabled = it
                    store.setEnabled(it)
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = colorScheme.secondary,
                    checkedBorderColor = Color.Transparent,
                ),
            )
        }
        if (enabled) {
            QuietHoursTimeRow(
                label = stringResource(R.string.settings_quiet_hours_start),
                value = fmt(startMinute),
                onClick = { pickTime(startMinute) { startMinute = it; store.setWindow(startMinute, endMinute) } },
            )
            QuietHoursTimeRow(
                label = stringResource(R.string.settings_quiet_hours_end),
                value = fmt(endMinute),
                onClick = { pickTime(endMinute) { endMinute = it; store.setWindow(startMinute, endMinute) } },
            )
        }
    }
}

@Composable
private fun QuietHoursTimeRow(label: String, value: String, onClick: () -> Unit) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Sub-row of Quiet hours: no glyph of its own, indented under its label instead.
            .padding(
                start = TdayDimens.IconSm + TdayDimens.SpacingXl,
                top = 4.dp,
                bottom = 4.dp,
            ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = colorScheme.onSurfaceVariant,
        )
        // The whole row used to be the tap target; the pill is the tap target now, which is
        // what the rest of this screen's changeable values look like.
        SettingsPillButton(
            text = value,
            icon = R.drawable.ic_lucide_clock,
            onClick = onClick,
        )
    }
}

/**
 * Server Mode only: enable/disable UnifiedPush so a self-hoster's distributor (e.g.
 * ntfy) delivers server pushes. Registration completes asynchronously — the endpoint
 * is sent to the backend by [com.ohmz.tday.compose.core.push.UnifiedPushReceiver].
 */
@Composable
private fun UnifiedPushRow() {
    val colorScheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val snackbarManager = LocalSnackbarManager.current
    var registered by remember { mutableStateOf(UnifiedPush.getAckDistributor(context) != null) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsRowIcon(R.drawable.ic_lucide_cloud)
        Text(
            text = stringResource(R.string.settings_unifiedpush_title),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold,
            color = colorScheme.onSurface,
        )
        SettingsPillButton(
            text = stringResource(
                if (registered) R.string.settings_unifiedpush_enabled
                else R.string.settings_unifiedpush_disabled,
            ),
            icon = R.drawable.ic_lucide_cloud,
            onClick = {
                if (registered) {
                    UnifiedPush.unregisterApp(context)
                    registered = false
                } else {
                    val distributors = UnifiedPush.getDistributors(context)
                    if (distributors.isEmpty()) {
                        snackbarManager?.showInfo(
                            context.getString(R.string.settings_unifiedpush_none),
                        )
                    } else {
                        UnifiedPush.registerAppWithDialog(context)
                        registered = true
                    }
                }
            },
        )
    }
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
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SettingsRowIcon(R.drawable.ic_lucide_bell)
            Text(
                text = stringResource(R.string.settings_default_reminder),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = colorScheme.onSurface,
            )
            SettingsPillButton(
                text = stringResource(selectedReminder.labelRes),
                icon = R.drawable.ic_lucide_clock,
                onClick = { expanded = true },
            )
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

@Composable
private fun DayAheadSelector(
    selectedDayAhead: DayAheadOption,
    onDayAheadSelected: (DayAheadOption) -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val view = LocalView.current
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SettingsRowIcon(R.drawable.ic_lucide_bell_ring)
            Text(
                text = stringResource(R.string.day_ahead_setting),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = colorScheme.onSurface,
            )
            SettingsPillButton(
                text = stringResource(selectedDayAhead.labelRes),
                icon = R.drawable.ic_lucide_clock_3,
                onClick = { expanded = true },
            )
        }

        if (expanded) {
            TdayCenteredSelectorDialog(
                title = stringResource(R.string.day_ahead_setting),
                options = DayAheadOption.entries,
                optionLabel = { option -> context.getString(option.labelRes) },
                optionSwatchColor = { option -> dayAheadSwatchColor(option) },
                isSelected = { option -> option == selectedDayAhead },
                onDismiss = { expanded = false },
                onOptionSelected = { option ->
                    ViewCompat.performHapticFeedback(
                        view,
                        HapticFeedbackConstantsCompat.CLOCK_TICK,
                    )
                    onDayAheadSelected(option)
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
    val current = AppLanguage.entries.firstOrNull { lang ->
        lang.tag?.let { currentTag.startsWith(it) } == true
    } ?: AppLanguage.SYSTEM

    fun labelFor(lang: AppLanguage) = if (lang == AppLanguage.SYSTEM) systemLabel else lang.endonym

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SettingsRowIcon(R.drawable.ic_lucide_languages)
            Text(
                text = stringResource(R.string.settings_language),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = colorScheme.onSurface,
            )
            SettingsPillButton(
                text = labelFor(current),
                icon = R.drawable.ic_lucide_globe,
                onClick = { expanded = true },
            )
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

/**
 * Reaches the local workspace's storage from a Settings row without threading it through the
 * app ViewModel, the way `CalendarEntryPoint` does for the calendar mirror. It lives beside
 * its one caller rather than in `core.data`, which this change is not allowed to touch.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface SettingsLocalWorkspaceEntryPoint {
    fun authRepository(): AuthRepository
}

/**
 * Confirms emptying a local workspace. The only destructive action on this screen with nothing
 * behind it — Local Mode has no server copy — so it spells that out and offers the export as
 * the way to keep anything, exactly as the web build does.
 */
@Composable
private fun DeleteLocalDataDialog(
    onDismiss: () -> Unit,
    onDeleted: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarManager = LocalSnackbarManager.current
    val deletedMessage = stringResource(R.string.settings_workspace_delete_done)
    // Reused rather than added: this is the generic failure the rest of the app
    // already shows, and it carries all ten locales today.
    val deleteFailedMessage = stringResource(R.string.error_generic)
    var busy by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = {
            Text(
                text = stringResource(R.string.settings_workspace_delete_title),
                fontWeight = FontWeight.ExtraBold,
            )
        },
        text = { Text(stringResource(R.string.settings_workspace_delete_body)) },
        confirmButton = {
            TextButton(
                enabled = !busy,
                onClick = {
                    busy = true
                    scope.launch {
                        val authRepository = EntryPointAccessors.fromApplication(
                            context.applicationContext,
                            SettingsLocalWorkspaceEntryPoint::class.java,
                        ).authRepository()
                        // Opens and clears the SQLCipher database, so it does not belong on
                        // the frame the dialog is dismissing on.
                        val cleared = withContext(Dispatchers.IO) {
                            runCatching {
                                authRepository.clearAllLocalUserDataForUnauthenticatedState()
                            }
                        }
                        // Only on success. Swallowing the failure and reporting
                        // "deleted" anyway would send the user away believing their
                        // data was gone while it is all still on the device — the
                        // worst possible direction for this particular lie.
                        if (cleared.isSuccess) {
                            snackbarManager?.showSuccess(deletedMessage)
                            onDeleted()
                        } else {
                            snackbarManager?.showError(deleteFailedMessage)
                        }
                    }
                },
            ) {
                Text(
                    text = stringResource(R.string.action_delete),
                    fontWeight = FontWeight.ExtraBold,
                    color = colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

private fun dayAheadSwatchColor(option: DayAheadOption): Color {
    return if (option == DayAheadOption.OFF) Color(0xFFB7BCC8) else TdayTitleIconDayAccent
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
