package com.ohmz.tday.compose.feature.release

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.ohmz.tday.compose.core.ui.LocalSnackbarManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ohmz.tday.compose.R
import com.ohmz.tday.compose.core.data.server.VersionCheckResult
import com.ohmz.tday.compose.core.ui.TdayHaptics
import com.ohmz.tday.compose.core.ui.TdayHeroTitleBlock
import com.ohmz.tday.compose.core.ui.TdayHeroToolbar
import com.ohmz.tday.compose.core.ui.TdayMotionTokens
import com.ohmz.tday.compose.core.ui.rememberScrollHeroTitleCollapse
import com.ohmz.tday.compose.core.ui.tdayPressable
import com.ohmz.tday.compose.ui.theme.TdayDimens
import com.ohmz.tday.compose.ui.theme.TdayStatusSuccess
import kotlinx.coroutines.launch
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt

// What this screen draws that the scale has no rung for. Named here rather than snapped onto a
// neighbouring step, because the near misses are the point: 15 dp is not SpacingXl's 14, and the
// browse card's 20 dp corner is not RadiusLg's 18. Rounding either would be a redraw, not a
// migration.

// The state the screen spends its first second in, and the card it falls back to when the
// release feed cannot be reached at all.
private val LoadingStateTopInset = 48.dp
private val LoadingSpinnerSize = 32.dp
private val LoadingSpinnerStroke = 3.dp
private val LoadFailureCardVerticalPadding = 20.dp

/**
 * The content inset of every card here except `ReleaseSurfaceCard`, which sits at `SpacingXxl`
 * because it is the surface a section is drawn on rather than something drawn inside one.
 */
private val CardContentPadding = 16.dp

/**
 * The gap inside a group — label to badge, bullet to its text, bullet to the next bullet. It falls
 * between `SpacingMd` and `SpacingLg`, where the scale has no step, so it is named once instead of
 * being rounded six times.
 */
private val TightGroupSpacing = 10.dp

// The version pill. Its 10 dp is named apart from TightGroupSpacing because it is padding inside a
// shape rather than a gap between two of them, and the two would not move together.
private val VersionBadgeRadius = 12.dp
private val VersionBadgeHorizontalPadding = 10.dp
private val VersionBadgeVerticalPadding = 5.dp

/** A changelog bullet is a dot, not a glyph, so it claims no icon rung. */
private val ChangelogBulletSize = 5.dp

// The "view on GitHub" row, the one card on this screen that is also a button.
private val BrowserCardRadius = 20.dp
private val BrowserRowVerticalPadding = 15.dp
private val BrowserRowIconSize = 18.dp

// A `PressedSurfaceOffsetY` stood here, naming the 2 dp this header's buttons sank by. Its one
// call site is gone: the button now presses through `Modifier.tdayPressable`, whose `offsetY`
// already defaults to `TdayPress.SinkOffset` — the same 2 dp, named once for every surface
// instead of once per screen.

/** The back chevron outgrows IconLg because it is the only glyph inside a FabSize target. */
private val BackButtonIconSize = 36.dp

@Composable
fun LatestReleaseScreen(
    uiState: LatestReleaseUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val snackbarManager = LocalSnackbarManager.current
    val view = LocalView.current
    val scrollState = rememberScrollState()
    val heroCollapse = rememberScrollHeroTitleCollapse(scrollState = scrollState)
    val installScope = rememberCoroutineScope()
    val installerEvent by InAppApkUpdater.installEvent.collectAsStateWithLifecycle()
    var installUiState by remember { mutableStateOf<ApkInstallUiState>(ApkInstallUiState.Idle) }
    var pendingInstallAsset by rememberSaveable(stateSaver = gitHubAssetSaver()) {
        mutableStateOf<GitHubAsset?>(null)
    }
    val installPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) {
        resumePendingInstallIfPossible(
            context = context,
            pendingInstallAsset = pendingInstallAsset,
            onPendingInstallAssetChange = { pendingInstallAsset = it },
            installUiState = installUiState,
            scope = installScope,
            onStateChange = { installUiState = it },
        )
    }
    val installConfirmationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { }

    LaunchedEffect(installerEvent) {
        when (val event = installerEvent) {
            is InAppApkUpdater.InstallEvent.PendingUserAction -> {
                installUiState = ApkInstallUiState.OpeningInstaller
                runCatching {
                    installConfirmationLauncher.launch(event.confirmationIntent)
                }.onSuccess {
                    InAppApkUpdater.clearPendingUserAction(event.sessionId)
                }.onFailure {
                    installUiState = ApkInstallUiState.Error(
                        context.getString(R.string.release_install_failed),
                    )
                }
            }

            is InAppApkUpdater.InstallEvent.Success -> {
                installUiState = ApkInstallUiState.Idle
                pendingInstallAsset = null
                InAppApkUpdater.clearInstallEvent()
            }

            is InAppApkUpdater.InstallEvent.Error -> {
                installUiState = ApkInstallUiState.Error(event.message)
                InAppApkUpdater.clearInstallEvent()
            }

            is InAppApkUpdater.InstallEvent.SignatureConflict -> {
                installUiState = ApkInstallUiState.SignatureConflict
                InAppApkUpdater.clearInstallEvent()
            }

            InAppApkUpdater.InstallEvent.Idle -> Unit
        }
    }
    OnLatestReleaseScreenResume {
        resumePendingInstallIfPossible(
            context = context,
            pendingInstallAsset = pendingInstallAsset,
            onPendingInstallAssetChange = { pendingInstallAsset = it },
            installUiState = installUiState,
            scope = installScope,
            onStateChange = { installUiState = it },
        )
    }

    Scaffold(
        containerColor = colorScheme.background,
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colorScheme.background)
                .verticalScroll(scrollState)
                .padding(horizontal = TdayDimens.ContentPaddingHorizontal)
                .padding(bottom = TdayDimens.SpacingXxs),
            verticalArrangement = Arrangement.spacedBy(TdayDimens.SpacingLg),
        ) {
            TdayHeroTitleBlock(
                title = stringResource(R.string.release_title),
                icon = ImageVector.vectorResource(R.drawable.ic_lucide_cloud_download),
                accentColor = MaterialTheme.colorScheme.primary,
                collapseProgress = heroCollapse.progress,
            )
            if (uiState.isLoading && uiState.currentRelease == null && uiState.latestRelease == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = LoadingStateTopInset),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(LoadingSpinnerSize),
                        strokeWidth = LoadingSpinnerStroke,
                    )
                }
            } else {
                if (uiState.error != null && uiState.currentRelease == null && uiState.latestRelease == null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(TdayDimens.RadiusXl),
                        colors = CardDefaults.cardColors(
                            containerColor = colorScheme.errorContainer.copy(alpha = 0.5f),
                        ),
                    ) {
                        Column(
                            modifier = Modifier.padding(
                                horizontal = CardContentPadding,
                                vertical = LoadFailureCardVerticalPadding,
                            ),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(TdayDimens.SpacingLg),
                        ) {
                            Text(
                                text = stringResource(R.string.release_error),
                                style = MaterialTheme.typography.bodyMedium,
                                color = colorScheme.onErrorContainer,
                            )
                            OutlinedButton(onClick = onRetry) {
                                Text(text = stringResource(R.string.action_retry))
                            }
                        }
                    }
                }

                ReleaseContent(
                    uiState = uiState,
                    apkInstallUiState = installUiState,
                    onDownloadApk = { asset ->
                        TdayHaptics.completion(view)
                        if (!InAppApkUpdater.canInstallPackages(context)) {
                            pendingInstallAsset = asset
                            installUiState = ApkInstallUiState.AwaitingPermission
                            snackbarManager?.showInfo(
                                context.getString(R.string.release_install_permission_return_hint),
                            )
                            installPermissionLauncher.launch(InAppApkUpdater.buildInstallPermissionIntent(context))
                        } else {
                            pendingInstallAsset = null
                            startApkInstall(
                                context = context,
                                asset = asset,
                                scope = installScope,
                                onStateChange = { installUiState = it },
                            )
                        }
                    },
                    onOpenInBrowser = { url ->
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    },
                )
            }
        }

        // Last, so it draws over the content passing behind it.
        TdayHeroToolbar(
            title = stringResource(R.string.release_title),
            collapseProgress = heroCollapse.progress,
            onBack = onBack,
            backContentDescription = stringResource(R.string.action_back),
            modifier = Modifier.align(Alignment.TopStart),
        )
        }
    }
}

@Composable
private fun ReleaseTopBar(
    onBack: () -> Unit,
    collapseProgress: Float,
) {
    val progress = collapseProgress.coerceIn(0f, 1f)
    val titleHandoffPoint = 0.9f
    val density = LocalDensity.current
    val expandedTitleHeight = lerp(TdayDimens.ExpandedTitleHeight, TdayDimens.SpacingNone, progress)
    val expandedTitleAlpha = ((titleHandoffPoint - progress) / titleHandoffPoint).coerceIn(0f, 1f)
    val collapsedTitleAlpha =
        ((progress - titleHandoffPoint) / (1f - titleHandoffPoint)).coerceIn(0f, 1f)
    val collapsedTitleShiftY = with(density) {
        (TdayDimens.CollapsedTitleShiftOffset * (1f - collapsedTitleAlpha)).toPx()
    }
    val expandedTitleShiftY = with(density) {
        (-TdayDimens.ExpandedTitleShiftOffset * (1f - expandedTitleAlpha)).toPx()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(
                start = TdayDimens.ContentPaddingHorizontal,
                end = TdayDimens.ContentPaddingHorizontal,
                top = TdayDimens.TitleBarTopPadding,
                bottom = TdayDimens.TitleBarBottomPadding,
            ),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            ReleaseHeaderButton(
                onClick = onBack,
                icon = ImageVector.vectorResource(R.drawable.ic_lucide_chevron_left),
                contentDescription = stringResource(R.string.action_back),
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
                        text = stringResource(R.string.release_title),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(lerp(TdayDimens.SpacingXl, TdayDimens.SpacingNone, progress)))
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
                        text = stringResource(R.string.release_title),
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
private fun ReleaseHeaderButton(
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    isBackButton: Boolean = false,
) {
    val colorScheme = MaterialTheme.colorScheme
    val view = LocalView.current
    val interactionSource = remember { MutableInteractionSource() }
    val isDarkTheme = colorScheme.background.luminance() < 0.5f
    val containerColor = if (isBackButton) {
        if (isDarkTheme) colorScheme.surface.copy(alpha = 0.94f) else Color.White.copy(alpha = 0.96f)
    } else {
        colorScheme.background
    }
    val buttonBorder = if (isBackButton) {
        null
    } else {
        BorderStroke(TdayDimens.BorderWidth, colorScheme.onSurface.copy(alpha = 0.38f))
    }
    // Naming both branches showed them to be the same number; the condition was never a fork.
    val buttonSize = TdayDimens.FabSize
    val iconSize = if (isBackButton) BackButtonIconSize else TdayDimens.IconLg

    Card(
        modifier = Modifier
            .tdayPressable(interactionSource, scale = TdayMotionTokens.PressScales.Bar),
        onClick = {
            TdayHaptics.buttonPress(view)
            onClick()
        },
        interactionSource = interactionSource,
        shape = CircleShape,
        border = buttonBorder,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isBackButton) TdayDimens.FabElevation else TdayDimens.CardElevationDefault,
            pressedElevation = if (isBackButton) TdayDimens.FabPressedElevation else TdayDimens.CardElevationDefault,
        ),
    ) {
        Box(
            modifier = Modifier.size(buttonSize),
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

@Composable
private fun ReleaseContent(
    uiState: LatestReleaseUiState,
    apkInstallUiState: ApkInstallUiState,
    onDownloadApk: (GitHubAsset) -> Unit,
    onOpenInBrowser: (String) -> Unit,
) {
    val currentRelease = uiState.currentRelease
    val latestRelease = uiState.latestRelease
    val isInstallerBusy = apkInstallUiState is ApkInstallUiState.Downloading ||
        apkInstallUiState is ApkInstallUiState.PreparingInstaller ||
        apkInstallUiState is ApkInstallUiState.OpeningInstaller

    ReleaseOverviewCard(
        currentVersion = uiState.currentVersion,
        currentRelease = currentRelease,
        latestRelease = latestRelease,
        hasUpdate = uiState.hasUpdate,
        backendVersion = uiState.backendVersion,
        versionCheckResult = uiState.versionCheckResult,
        isLocalMode = uiState.isLocalMode,
        hasServerConfigured = uiState.hasServerConfigured,
    )

    if (uiState.hasUpdate && latestRelease != null) {
        UpdateAvailableCard(
            latestRelease = latestRelease,
            apkInstallUiState = apkInstallUiState,
            isInstallerBusy = isInstallerBusy,
            onDownloadApk = onDownloadApk,
        )
    }

    if (!uiState.hasUpdate) {
        InstalledVersionCard(
            currentVersion = uiState.currentVersion,
            hasUpdate = uiState.hasUpdate,
            currentRelease = currentRelease,
        )
    }

    val browseUrl = latestRelease?.htmlUrl ?: currentRelease?.htmlUrl
    if (browseUrl != null) {
        ReleaseBrowserButton(
            browseUrl = browseUrl,
            onOpenInBrowser = onOpenInBrowser,
        )
    }

    Spacer(modifier = Modifier.height(TdayDimens.Spacing3xl))
}

@Composable
private fun ReleaseOverviewCard(
    currentVersion: String,
    currentRelease: GitHubRelease?,
    latestRelease: GitHubRelease?,
    hasUpdate: Boolean,
    backendVersion: String? = null,
    versionCheckResult: VersionCheckResult? = null,
    isLocalMode: Boolean = false,
    hasServerConfigured: Boolean = false,
) {
    val colorScheme = MaterialTheme.colorScheme
    val isIncompatible = versionCheckResult is VersionCheckResult.AppUpdateRequired ||
        versionCheckResult is VersionCheckResult.ServerUpdateRequired
    val accent = when {
        isIncompatible -> colorScheme.error
        hasUpdate -> colorScheme.primary
        else -> colorScheme.onSurface
    }
    val title = when {
        isIncompatible -> stringResource(R.string.release_version_mismatch)
        hasUpdate -> stringResource(R.string.release_update_available)
        else -> stringResource(R.string.release_up_to_date)
    }
    val summary = when (versionCheckResult) {
        is VersionCheckResult.AppUpdateRequired ->
            stringResource(
                R.string.release_app_update_required_summary,
                versionCheckResult.requiredVersion,
            )
        is VersionCheckResult.ServerUpdateRequired ->
            stringResource(
                R.string.release_server_update_required_summary,
                currentVersion,
                versionCheckResult.serverVersion,
            )
        else -> if (hasUpdate) {
            latestRelease?.tagName?.let {
                stringResource(R.string.release_update_ready_version, it)
            } ?: stringResource(R.string.release_update_ready_generic)
        } else {
            stringResource(R.string.release_up_to_date_message)
        }
    }

    ReleaseSurfaceCard(
        borderColor = when {
            isIncompatible -> accent.copy(alpha = 0.12f)
            hasUpdate -> accent.copy(alpha = 0.12f)
            else -> colorScheme.onSurface.copy(alpha = 0.05f)
        },
    ) {
        ReleaseSectionTitle(
            title = title,
            color = accent,
        )
        Text(
            text = summary,
            style = MaterialTheme.typography.bodyMedium,
            color = colorScheme.onSurface.copy(alpha = 0.62f),
        )
        ReleasePublishedDate(
            publishedAt = latestRelease?.publishedAt ?: currentRelease?.publishedAt,
        )
        if (!isLocalMode) {
            val isCompatible = versionCheckResult is VersionCheckResult.Compatible ||
                versionCheckResult == null
            ReleaseVersionLine(
                label = stringResource(R.string.label_server),
                version = when {
                    backendVersion != null -> stringResource(
                        R.string.label_version_name,
                        backendVersion
                    )

                    !hasServerConfigured -> stringResource(R.string.release_server_not_connected)
                    else -> stringResource(R.string.release_server_version_unavailable)
                },
                tint = when {
                    backendVersion == null -> colorScheme.onSurfaceVariant
                    isCompatible -> TdayStatusSuccess
                    else -> colorScheme.error
                },
            )
        }
        ReleaseVersionLine(
            label = if (hasUpdate) {
                stringResource(R.string.release_installed_label)
            } else {
                stringResource(R.string.release_installed_version)
            },
            version = stringResource(R.string.label_version_name, currentVersion),
            tint = colorScheme.primary,
        )
        latestRelease?.takeIf { hasUpdate }?.let {
            ReleaseVersionLine(
                label = stringResource(R.string.release_latest_label),
                version = it.tagName,
                tint = colorScheme.tertiary,
            )
        }
    }
}

@Composable
private fun ReleaseSurfaceCard(
    borderColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
    content: @Composable ColumnScope.() -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme

    Card(
        modifier = Modifier.fillMaxWidth(),
        // RadiusXl, not RadiusCard: this card has always drawn at 24 and 26 would be a redraw.
        shape = RoundedCornerShape(TdayDimens.RadiusXl),
        border = BorderStroke(TdayDimens.BorderWidth, borderColor),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = TdayDimens.CardElevationDefault),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = TdayDimens.SpacingXxl, vertical = TdayDimens.SpacingXxl),
            verticalArrangement = Arrangement.spacedBy(TdayDimens.SpacingLg),
            content = content,
        )
    }
}

@Composable
private fun InstalledVersionCard(
    currentVersion: String,
    hasUpdate: Boolean,
    currentRelease: GitHubRelease?,
) {
    MaterialTheme.colorScheme
    val currentChangelog = parseChangelog(currentRelease?.body)

    ReleaseSurfaceCard {
        ReleaseSectionTitle(
            title = stringResource(R.string.release_installed_version),
        )
        InstalledVersionRow(
            currentVersion = currentVersion,
            hasUpdate = hasUpdate,
        )
        ReleasePublishedDate(publishedAt = currentRelease?.publishedAt)
        ReleaseNotesSection(
            versionLabel = "v$currentVersion",
            changelog = currentChangelog,
            emptyMessage = currentRelease?.let { null }
                ?: stringResource(R.string.release_no_notes_for_version),
        )
    }
}

@Composable
private fun UpdateAvailableCard(
    latestRelease: GitHubRelease,
    apkInstallUiState: ApkInstallUiState,
    isInstallerBusy: Boolean,
    onDownloadApk: (GitHubAsset) -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val latestChangelog = parseChangelog(latestRelease.body)

    ReleaseSurfaceCard(borderColor = colorScheme.primary.copy(alpha = 0.12f)) {
        ReleaseSectionTitle(
            title = stringResource(R.string.release_update_available),
            color = colorScheme.primary,
        )
        VersionBadge(
            text = latestRelease.tagName,
            backgroundColor = colorScheme.primary.copy(alpha = 0.08f),
            textColor = colorScheme.primary,
        )
        ReleasePublishedDate(publishedAt = latestRelease.publishedAt)
        ReleaseNotesSection(
            versionLabel = latestRelease.tagName,
            changelog = latestChangelog,
            emptyMessage = null,
        )
        ApkDownloadSection(
            apk = latestRelease.apkAsset,
            apkInstallUiState = apkInstallUiState,
            isInstallerBusy = isInstallerBusy,
            onDownloadApk = onDownloadApk,
        )
    }
}

@Composable
private fun ReleaseSectionTitle(
    title: String,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.ExtraBold,
        color = color,
    )
}

@Composable
private fun InstalledVersionRow(
    currentVersion: String,
    hasUpdate: Boolean,
) {
    val colorScheme = MaterialTheme.colorScheme

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TightGroupSpacing),
    ) {
        VersionBadge(
            text = stringResource(R.string.label_version_name, currentVersion),
            backgroundColor = colorScheme.primary.copy(alpha = 0.08f),
            textColor = colorScheme.primary,
        )
        if (!hasUpdate) {
            Text(
                text = stringResource(R.string.release_up_to_date),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.ExtraBold,
                color = colorScheme.onSurface.copy(alpha = 0.6f),
            )
        }
    }
}

@Composable
private fun ReleasePublishedDate(publishedAt: String?) {
    val colorScheme = MaterialTheme.colorScheme

    publishedAt?.let { date ->
        Text(
            text = stringResource(R.string.release_published, formatIsoDate(date)),
            style = MaterialTheme.typography.bodySmall,
            color = colorScheme.onSurface.copy(alpha = 0.62f),
        )
    }
}

@Composable
private fun ReleaseNotesSection(
    versionLabel: String,
    changelog: List<String>,
    emptyMessage: String?,
) {
    val colorScheme = MaterialTheme.colorScheme

    if (changelog.isNotEmpty()) {
        Text(
            text = stringResource(R.string.release_whats_new_in_version, versionLabel),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold,
            color = colorScheme.onSurface,
        )
        Card(
            shape = RoundedCornerShape(TdayDimens.RadiusLg),
            colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant.copy(alpha = 0.6f)),
            elevation = CardDefaults.cardElevation(defaultElevation = TdayDimens.CardElevationDefault),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = TdayDimens.SpacingXl, vertical = TdayDimens.SpacingXl),
                verticalArrangement = Arrangement.spacedBy(TightGroupSpacing),
            ) {
                ChangelogList(items = changelog)
            }
        }
    } else if (emptyMessage != null) {
        Card(
            shape = RoundedCornerShape(TdayDimens.RadiusLg),
            colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant.copy(alpha = 0.6f)),
            elevation = CardDefaults.cardElevation(defaultElevation = TdayDimens.CardElevationDefault),
        ) {
            Text(
                text = emptyMessage,
                modifier = Modifier.padding(horizontal = TdayDimens.SpacingXl, vertical = TdayDimens.SpacingXl),
                style = MaterialTheme.typography.bodyMedium,
                color = colorScheme.onSurface.copy(alpha = 0.6f),
            )
        }
    }
}

@Composable
private fun ApkDownloadSection(
    apk: GitHubAsset?,
    apkInstallUiState: ApkInstallUiState,
    isInstallerBusy: Boolean,
    onDownloadApk: (GitHubAsset) -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme

    if (apk == null) {
        Text(
            text = stringResource(R.string.release_no_apk),
            style = MaterialTheme.typography.bodyMedium,
            color = colorScheme.onSurface.copy(alpha = 0.6f),
        )
        return
    }

    ApkAssetCard(apk = apk)
    ApkInstallButton(
        apk = apk,
        apkInstallUiState = apkInstallUiState,
        isInstallerBusy = isInstallerBusy,
        onDownloadApk = onDownloadApk,
    )
    ApkInstallStatus(apkInstallUiState = apkInstallUiState)
}

@Composable
private fun ApkAssetCard(apk: GitHubAsset) {
    val colorScheme = MaterialTheme.colorScheme

    Card(
        shape = RoundedCornerShape(TdayDimens.RadiusLg),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant.copy(alpha = 0.7f)),
        elevation = CardDefaults.cardElevation(defaultElevation = TdayDimens.CardElevationDefault),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CardContentPadding, vertical = TdayDimens.SpacingXl),
            horizontalArrangement = Arrangement.spacedBy(TdayDimens.SpacingLg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = apk.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = colorScheme.onSurface,
                )
                Text(
                    text = formatBytes(apk.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurface.copy(alpha = 0.62f),
                )
            }
            Icon(
                imageVector = ImageVector.vectorResource(R.drawable.ic_lucide_cloud_download),
                contentDescription = null,
                tint = colorScheme.onSurface.copy(alpha = 0.5f),
                modifier = Modifier.size(TdayDimens.IconSm),
            )
        }
    }
}

@Composable
private fun ApkInstallButton(
    apk: GitHubAsset,
    apkInstallUiState: ApkInstallUiState,
    isInstallerBusy: Boolean,
    onDownloadApk: (GitHubAsset) -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme

    Button(
        onClick = { onDownloadApk(apk) },
        enabled = !isInstallerBusy,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(TdayDimens.RadiusLg),
        colors = ButtonDefaults.buttonColors(containerColor = colorScheme.primary),
    ) {
        Icon(
            imageVector = ImageVector.vectorResource(R.drawable.ic_lucide_cloud_download),
            contentDescription = null,
            modifier = Modifier.size(TdayDimens.IconSm),
        )
        Spacer(modifier = Modifier.width(TdayDimens.SpacingMd))
        Text(
            text = apkInstallButtonLabel(apkInstallUiState = apkInstallUiState),
            fontWeight = FontWeight.ExtraBold,
        )
    }
}

@Composable
private fun apkInstallButtonLabel(apkInstallUiState: ApkInstallUiState): String {
    return when (apkInstallUiState) {
        ApkInstallUiState.AwaitingPermission -> stringResource(R.string.release_allow_install_permission)
        is ApkInstallUiState.Downloading -> {
            apkInstallUiState.progress
                ?.let { progress ->
                    stringResource(
                        R.string.release_downloading_apk_progress,
                        (progress * 100).roundToInt().coerceIn(0, 100),
                    )
                }
                ?: stringResource(R.string.release_downloading_apk)
        }

        ApkInstallUiState.PreparingInstaller -> stringResource(R.string.release_preparing_update)
        ApkInstallUiState.OpeningInstaller -> stringResource(R.string.release_opening_installer)
        ApkInstallUiState.Idle,
        is ApkInstallUiState.Error,
        ApkInstallUiState.SignatureConflict -> stringResource(R.string.release_download_and_install)
    }
}

@Composable
private fun ApkInstallStatus(apkInstallUiState: ApkInstallUiState) {
    val colorScheme = MaterialTheme.colorScheme

    when (apkInstallUiState) {
        ApkInstallUiState.AwaitingPermission -> {
            Text(
                text = stringResource(R.string.release_install_permission_return_hint),
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurface.copy(alpha = 0.7f),
            )
        }

        is ApkInstallUiState.Downloading -> {
            val progress = apkInstallUiState.progress
            if (progress != null) {
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }

        ApkInstallUiState.PreparingInstaller -> {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        ApkInstallUiState.OpeningInstaller -> Unit

        is ApkInstallUiState.Error -> {
            Text(
                text = apkInstallUiState.message,
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.error,
            )
        }

        ApkInstallUiState.SignatureConflict -> {
            SignatureConflictCard()
        }

        ApkInstallUiState.Idle -> Unit
    }
}

@Composable
private fun SignatureConflictCard() {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme

    Card(
        shape = RoundedCornerShape(TdayDimens.RadiusLg),
        colors = CardDefaults.cardColors(
            containerColor = colorScheme.errorContainer.copy(alpha = 0.5f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = TdayDimens.CardElevationDefault),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = CardContentPadding, vertical = CardContentPadding),
            verticalArrangement = Arrangement.spacedBy(TightGroupSpacing),
        ) {
            Text(
                text = stringResource(R.string.release_signature_conflict),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.ExtraBold,
                color = colorScheme.onErrorContainer,
            )
            Text(
                text = stringResource(R.string.release_signature_conflict_hint),
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onErrorContainer.copy(alpha = 0.8f),
            )
            OutlinedButton(
                onClick = {
                    context.startActivity(InAppApkUpdater.buildUninstallIntent(context))
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(TdayDimens.RadiusMd),
                border = BorderStroke(TdayDimens.BorderWidth, colorScheme.error.copy(alpha = 0.5f)),
            ) {
                Text(
                    text = stringResource(R.string.release_uninstall),
                    color = colorScheme.error,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
        }
    }
}

@Composable
private fun ReleaseBrowserButton(
    browseUrl: String,
    onOpenInBrowser: (String) -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme

    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = { onOpenInBrowser(browseUrl) },
        shape = RoundedCornerShape(BrowserCardRadius),
        border = BorderStroke(TdayDimens.BorderWidth, colorScheme.onSurface.copy(alpha = 0.06f)),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = TdayDimens.CardElevationDefault),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = TdayDimens.SpacingXxl, vertical = BrowserRowVerticalPadding),
            horizontalArrangement = Arrangement.spacedBy(TdayDimens.SpacingLg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = ImageVector.vectorResource(R.drawable.ic_lucide_square_arrow_out_up_right),
                contentDescription = null,
                tint = colorScheme.primary,
                modifier = Modifier.size(BrowserRowIconSize),
            )
            Text(
                text = stringResource(R.string.release_view_on_github),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = colorScheme.onSurface,
            )
            Icon(
                imageVector = ImageVector.vectorResource(R.drawable.ic_lucide_square_arrow_out_up_right),
                contentDescription = null,
                tint = colorScheme.primary,
                modifier = Modifier.size(BrowserRowIconSize),
            )
        }
    }
}

@Composable
private fun VersionBadge(
    text: String,
    backgroundColor: Color,
    textColor: Color,
) {
    Text(
        text = text,
        modifier = Modifier
            .clip(RoundedCornerShape(VersionBadgeRadius))
            .background(backgroundColor)
            .padding(horizontal = VersionBadgeHorizontalPadding, vertical = VersionBadgeVerticalPadding),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.ExtraBold,
        color = textColor,
    )
}

@Composable
private fun ReleaseVersionLine(
    label: String,
    version: String,
    tint: Color,
) {
    val colorScheme = MaterialTheme.colorScheme

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TightGroupSpacing),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = colorScheme.onSurface.copy(alpha = 0.58f),
        )
        VersionBadge(
            text = version,
            backgroundColor = tint.copy(alpha = 0.08f),
            textColor = tint,
        )
    }
}

@Composable
private fun ChangelogList(items: List<String>) {
    val colorScheme = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(TightGroupSpacing)) {
        items.forEach { item ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(TightGroupSpacing),
                verticalAlignment = Alignment.Top,
            ) {
                Box(
                    modifier = Modifier
                        .padding(top = TdayDimens.SpacingMd)
                        .size(ChangelogBulletSize)
                        .clip(CircleShape)
                        .background(colorScheme.onSurface.copy(alpha = 0.3f)),
                )
                Text(
                    text = item,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.onSurface,
                )
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.1f KB".format(kb)
    return "%.1f MB".format(kb / 1024.0)
}

private fun formatIsoDate(iso: String): String {
    return try {
        val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val date = parser.parse(iso) ?: return iso
        SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()).format(date)
    } catch (_: Exception) {
        iso
    }
}

private fun startApkInstall(
    context: Context,
    asset: GitHubAsset,
    scope: kotlinx.coroutines.CoroutineScope,
    onStateChange: (ApkInstallUiState) -> Unit,
) {
    val appContext = context.applicationContext
    scope.launch {
        try {
            onStateChange(ApkInstallUiState.Downloading(progress = 0f))
            InAppApkUpdater.downloadAndInstall(appContext, asset) { progress ->
                onStateChange(ApkInstallUiState.Downloading(progress = progress))
            }
            onStateChange(ApkInstallUiState.PreparingInstaller)
        } catch (error: IOException) {
            onStateChange(buildApkInstallErrorState(context, error))
        }
    }
}

private fun resumePendingInstallIfPossible(
    context: Context,
    pendingInstallAsset: GitHubAsset?,
    onPendingInstallAssetChange: (GitHubAsset?) -> Unit,
    installUiState: ApkInstallUiState,
    scope: kotlinx.coroutines.CoroutineScope,
    onStateChange: (ApkInstallUiState) -> Unit,
) {
    val asset = pendingInstallAsset ?: return
    if (!InAppApkUpdater.canInstallPackages(context)) {
        if (installUiState is ApkInstallUiState.AwaitingPermission) {
            onStateChange(ApkInstallUiState.AwaitingPermission)
        }
        return
    }

    onPendingInstallAssetChange(null)
    startApkInstall(
        context = context,
        asset = asset,
        scope = scope,
        onStateChange = onStateChange,
    )
}

private fun buildApkInstallErrorState(
    context: Context,
    error: IOException,
): ApkInstallUiState.Error {
    return ApkInstallUiState.Error(
        error.message?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.release_download_failed),
    )
}

@Composable
private fun OnLatestReleaseScreenResume(
    action: () -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentAction by rememberUpdatedState(action)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                currentAction()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
}

private fun gitHubAssetSaver() = listSaver<GitHubAsset?, Any>(
    save = { asset ->
        asset?.let {
            listOf(it.name, it.browserDownloadUrl, it.size, it.downloadCount)
        } ?: emptyList()
    },
    restore = { values ->
        if (values.isEmpty()) {
            null
        } else {
            GitHubAsset(
                name = values[0] as String,
                browserDownloadUrl = values[1] as String,
                size = values[2] as Long,
                downloadCount = values[3] as Int,
            )
        }
    },
)

private sealed interface ApkInstallUiState {
    data object Idle : ApkInstallUiState

    data object AwaitingPermission : ApkInstallUiState

    data class Downloading(val progress: Float?) : ApkInstallUiState

    data object PreparingInstaller : ApkInstallUiState

    data object OpeningInstaller : ApkInstallUiState

    data class Error(val message: String) : ApkInstallUiState

    data object SignatureConflict : ApkInstallUiState
}

private const val RELEASE_TITLE_COLLAPSE_DISTANCE_DP = 180f
