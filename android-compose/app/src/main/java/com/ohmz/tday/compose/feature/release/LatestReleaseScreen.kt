package com.ohmz.tday.compose.feature.release

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.NewReleases
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.geometry.Offset
import androidx.core.net.toUri
import androidx.core.view.HapticFeedbackConstantsCompat
import androidx.core.view.ViewCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ohmz.tday.compose.R
import kotlinx.coroutines.launch
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt

@Composable
fun LatestReleaseScreen(
    uiState: LatestReleaseUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val view = LocalView.current
    val scrollState = rememberScrollState()
    val density = LocalDensity.current
    val maxCollapsePx = with(density) { RELEASE_TITLE_COLLAPSE_DISTANCE_DP.dp.toPx() }
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
        label = "releaseTitleCollapseProgress",
    )
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
        topBar = {
            ReleaseTopBar(
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
            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 48.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(32.dp),
                            strokeWidth = 3.dp,
                        )
                    }
                }

                uiState.error != null -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = colorScheme.errorContainer.copy(alpha = 0.5f),
                        ),
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
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

                else -> {
                    ReleaseContent(
                        uiState = uiState,
                        apkInstallUiState = installUiState,
                        onDownloadApk = { asset ->
                            ViewCompat.performHapticFeedback(view, HapticFeedbackConstantsCompat.CONFIRM)
                            if (!InAppApkUpdater.canInstallPackages(context)) {
                                pendingInstallAsset = asset
                                installUiState = ApkInstallUiState.AwaitingPermission
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.release_install_permission_return_hint),
                                    Toast.LENGTH_LONG,
                                ).show()
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
            ReleaseHeaderButton(
                onClick = onBack,
                icon = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.action_back),
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
                        text = stringResource(R.string.release_title),
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
private fun ReleaseHeaderButton(
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
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
            modifier = Modifier.size(56.dp),
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
    )

    if (uiState.hasUpdate && latestRelease != null) {
        UpdateAvailableCard(
            latestRelease = latestRelease,
            apkInstallUiState = apkInstallUiState,
            isInstallerBusy = isInstallerBusy,
            onDownloadApk = onDownloadApk,
        )
    }

    InstalledVersionCard(
        currentVersion = uiState.currentVersion,
        hasUpdate = uiState.hasUpdate,
        currentRelease = currentRelease,
    )

    val browseUrl = latestRelease?.htmlUrl ?: currentRelease?.htmlUrl
    if (browseUrl != null) {
        ReleaseBrowserButton(
            browseUrl = browseUrl,
            onOpenInBrowser = onOpenInBrowser,
        )
    }

    Spacer(modifier = Modifier.height(24.dp))
}

@Composable
private fun ReleaseOverviewCard(
    currentVersion: String,
    currentRelease: GitHubRelease?,
    latestRelease: GitHubRelease?,
    hasUpdate: Boolean,
) {
    val colorScheme = MaterialTheme.colorScheme
    val accent = if (hasUpdate) colorScheme.primary else colorScheme.onSurface
    val title = if (hasUpdate) stringResource(R.string.release_update_available) else stringResource(R.string.release_up_to_date)
    val summary = if (hasUpdate) {
        latestRelease?.tagName?.let { "Version $it is ready to install." }
            ?: "A newer version is ready to install."
    } else {
        stringResource(R.string.release_up_to_date_message)
    }

    ReleaseSurfaceCard(
        borderColor = if (hasUpdate) {
            accent.copy(alpha = 0.12f)
        } else {
            colorScheme.onSurface.copy(alpha = 0.05f)
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
        ReleaseVersionLine(
            label = if (hasUpdate) "Installed" else stringResource(R.string.release_installed_version),
            version = "v$currentVersion",
            tint = colorScheme.primary,
        )
        latestRelease?.takeIf { hasUpdate }?.let {
            ReleaseVersionLine(
                label = "Latest",
                version = it.tagName,
                tint = colorScheme.tertiary,
            )
        }
    }
}

@Composable
private fun ReleaseSurfaceCard(
    borderColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
    content: @Composable ColumnScope.() -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, borderColor),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
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
    val colorScheme = MaterialTheme.colorScheme
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
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
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
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        VersionBadge(
            text = "v$currentVersion",
            backgroundColor = colorScheme.primary.copy(alpha = 0.08f),
            textColor = colorScheme.primary,
        )
        if (!hasUpdate) {
            Text(
                text = stringResource(R.string.release_up_to_date),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
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
            fontWeight = FontWeight.SemiBold,
            color = colorScheme.onSurface,
        )
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant.copy(alpha = 0.6f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ChangelogList(items = changelog)
            }
        }
    } else if (emptyMessage != null) {
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant.copy(alpha = 0.6f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Text(
                text = emptyMessage,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
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
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant.copy(alpha = 0.7f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = apk.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colorScheme.onSurface,
                )
                Text(
                    text = formatBytes(apk.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurface.copy(alpha = 0.62f),
                )
            }
            Icon(
                imageVector = Icons.Rounded.CloudDownload,
                contentDescription = null,
                tint = colorScheme.onSurface.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp),
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
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(containerColor = colorScheme.primary),
    ) {
        Icon(
            imageVector = Icons.Rounded.CloudDownload,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = apkInstallButtonLabel(apkInstallUiState = apkInstallUiState),
            fontWeight = FontWeight.SemiBold,
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
        is ApkInstallUiState.Error -> stringResource(R.string.release_download_and_install)
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

        ApkInstallUiState.Idle -> Unit
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
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, colorScheme.onSurface.copy(alpha = 0.06f)),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 15.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                contentDescription = null,
                tint = colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = stringResource(R.string.release_view_on_github),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = colorScheme.onSurface,
            )
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                contentDescription = null,
                tint = colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun VersionBadge(
    text: String,
    backgroundColor: androidx.compose.ui.graphics.Color,
    textColor: androidx.compose.ui.graphics.Color,
) {
    Text(
        text = text,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = textColor,
    )
}

@Composable
private fun ReleaseVersionLine(
    label: String,
    version: String,
    tint: androidx.compose.ui.graphics.Color,
) {
    val colorScheme = MaterialTheme.colorScheme

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
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
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.forEach { item ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Box(
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .size(5.dp)
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
}

private const val RELEASE_TITLE_COLLAPSE_DISTANCE_DP = 180f
