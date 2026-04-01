package com.ohmz.tday.compose.feature.release

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.core.view.HapticFeedbackConstantsCompat
import androidx.core.view.ViewCompat
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
    val installScope = rememberCoroutineScope()
    var installUiState by remember { mutableStateOf<ApkInstallUiState>(ApkInstallUiState.Idle) }
    var pendingInstallAsset by remember { mutableStateOf<GitHubAsset?>(null) }
    val installPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) {
        val asset = pendingInstallAsset ?: return@rememberLauncherForActivityResult
        pendingInstallAsset = null
        if (InAppApkUpdater.canInstallPackages(context)) {
            startApkInstall(
                context = context,
                asset = asset,
                scope = installScope,
                onStateChange = { installUiState = it },
            )
        } else {
            installUiState = ApkInstallUiState.Error(
                context.getString(R.string.release_install_permission_denied),
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Card(
                onClick = {
                    ViewCompat.performHapticFeedback(view, HapticFeedbackConstantsCompat.CLOCK_TICK)
                    onBack()
                },
                shape = CircleShape,
                border = BorderStroke(1.dp, colorScheme.onSurface.copy(alpha = 0.15f)),
                colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
            ) {
                Box(
                    modifier = Modifier.size(56.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.action_back),
                        tint = colorScheme.onSurface,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Info,
                contentDescription = null,
                tint = colorScheme.onBackground,
                modifier = Modifier.size(28.dp),
            )
            Text(
                text = stringResource(R.string.release_title),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = colorScheme.onBackground,
            )
        }

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
                            Toast.makeText(
                                context,
                                context.getString(R.string.release_install_permission_required),
                                Toast.LENGTH_LONG,
                            ).show()
                            installPermissionLauncher.launch(InAppApkUpdater.buildInstallPermissionIntent(context))
                        } else {
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

@Composable
private fun ReleaseContent(
    uiState: LatestReleaseUiState,
    apkInstallUiState: ApkInstallUiState,
    onDownloadApk: (GitHubAsset) -> Unit,
    onOpenInBrowser: (String) -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val currentRelease = uiState.currentRelease
    val latestRelease = uiState.latestRelease
    val currentChangelog = parseChangelog(currentRelease?.body)
    val isInstallerBusy = apkInstallUiState is ApkInstallUiState.Downloading ||
        apkInstallUiState is ApkInstallUiState.Installing

    // ── Installed version ──────────────────────────────────
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Info,
                    contentDescription = null,
                    tint = colorScheme.onSurface,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = stringResource(R.string.release_installed_version),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = colorScheme.onSurface,
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                VersionBadge(
                    text = "v${uiState.currentVersion}",
                    backgroundColor = colorScheme.primary.copy(alpha = 0.12f),
                    textColor = colorScheme.primary,
                )
                if (!uiState.hasUpdate) {
                    Text(
                        text = stringResource(R.string.release_up_to_date),
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(colorScheme.tertiary.copy(alpha = 0.12f))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = colorScheme.tertiary,
                    )
                }
            }

            currentRelease?.publishedAt?.let { date ->
                Text(
                    text = stringResource(R.string.release_published, formatIsoDate(date)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.onSurface.copy(alpha = 0.7f),
                )
            }

            if (currentChangelog.isNotEmpty()) {
                HorizontalDivider(color = colorScheme.onSurface.copy(alpha = 0.08f))
                Text(
                    text = stringResource(R.string.release_whats_new_in_version, "v${uiState.currentVersion}"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colorScheme.onSurface,
                )
                ChangelogList(items = currentChangelog)
            } else if (currentRelease == null) {
                Text(
                    text = stringResource(R.string.release_no_notes_for_version),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.onSurface.copy(alpha = 0.55f),
                )
            }
        }
    }

    // ── Up to date / Update available ──────────────────────
    if (uiState.hasUpdate && latestRelease != null) {
        val latestChangelog = parseChangelog(latestRelease.body)

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            border = BorderStroke(1.dp, colorScheme.primary.copy(alpha = 0.25f)),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.NewReleases,
                        contentDescription = null,
                        tint = colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = stringResource(R.string.release_update_available),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = colorScheme.primary,
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    VersionBadge(
                        text = latestRelease.tagName,
                        backgroundColor = colorScheme.primary.copy(alpha = 0.12f),
                        textColor = colorScheme.primary,
                    )
                }

                latestRelease.publishedAt?.let { date ->
                    Text(
                        text = stringResource(R.string.release_published, formatIsoDate(date)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                }

                if (latestChangelog.isNotEmpty()) {
                    HorizontalDivider(color = colorScheme.onSurface.copy(alpha = 0.08f))
                    Text(
                        text = stringResource(R.string.release_whats_new_in_version, latestRelease.tagName),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = colorScheme.onSurface,
                    )
                    ChangelogList(items = latestChangelog)
                }

                HorizontalDivider(color = colorScheme.onSurface.copy(alpha = 0.08f))

                val apk = latestRelease.apkAsset
                if (apk != null) {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = apk.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = colorScheme.onSurface,
                                )
                                Text(
                                    text = formatBytes(apk.size),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colorScheme.onSurface.copy(alpha = 0.6f),
                                )
                            }
                        }
                    }

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
                            text = when (val installerState = apkInstallUiState) {
                                is ApkInstallUiState.Downloading -> {
                                    installerState.progress
                                        ?.let { progress ->
                                            stringResource(
                                                R.string.release_downloading_apk_progress,
                                                (progress * 100).roundToInt().coerceIn(0, 100),
                                            )
                                        }
                                        ?: stringResource(R.string.release_downloading_apk)
                                }

                                ApkInstallUiState.Installing ->
                                    stringResource(R.string.release_opening_installer)

                                else -> stringResource(R.string.release_download_and_install)
                            },
                            fontWeight = FontWeight.SemiBold,
                        )
                    }

                    when (val installerState = apkInstallUiState) {
                        is ApkInstallUiState.Downloading -> {
                            val progress = installerState.progress
                            if (progress != null) {
                                LinearProgressIndicator(
                                    progress = { progress.coerceIn(0f, 1f) },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            } else {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            }
                        }

                        ApkInstallUiState.Installing -> {
                            Text(
                                text = stringResource(R.string.release_opening_installer),
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onSurface.copy(alpha = 0.65f),
                            )
                        }

                        is ApkInstallUiState.Error -> {
                            Text(
                                text = installerState.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.error,
                            )
                        }

                        ApkInstallUiState.Idle -> Unit
                    }
                } else {
                    Text(
                        text = stringResource(R.string.release_no_apk),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }
            }
        }
    } else if (!uiState.hasUpdate && latestRelease != null) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = colorScheme.tertiary,
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    text = stringResource(R.string.release_up_to_date_message),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = colorScheme.onSurface,
                )
            }
        }
    }

    // ── View on GitHub ─────────────────────────────────────
    val browseUrl = latestRelease?.htmlUrl ?: currentRelease?.htmlUrl
    if (browseUrl != null) {
        OutlinedButton(
            onClick = { onOpenInBrowser(browseUrl) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = stringResource(R.string.release_view_on_github))
        }
    }

    Spacer(modifier = Modifier.height(24.dp))
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
            .padding(horizontal = 12.dp, vertical = 6.dp),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = textColor,
    )
}

@Composable
private fun ChangelogList(items: List<String>) {
    val colorScheme = MaterialTheme.colorScheme
    items.forEach { item ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "→",
                style = MaterialTheme.typography.bodyMedium,
                color = colorScheme.primary,
            )
            Text(
                text = item,
                style = MaterialTheme.typography.bodyMedium,
                color = colorScheme.onSurface,
            )
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
        var resetToIdle = true
        try {
            onStateChange(ApkInstallUiState.Downloading(progress = 0f))
            InAppApkUpdater.downloadAndInstall(appContext, asset) { progress ->
                onStateChange(ApkInstallUiState.Downloading(progress = progress))
            }
            onStateChange(ApkInstallUiState.Installing)
        } catch (error: IOException) {
            resetToIdle = false
            onStateChange(buildApkInstallErrorState(context, error))
        } finally {
            if (resetToIdle) {
                onStateChange(ApkInstallUiState.Idle)
            }
        }
    }
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

private sealed interface ApkInstallUiState {
    data object Idle : ApkInstallUiState

    data class Downloading(val progress: Float?) : ApkInstallUiState

    data object Installing : ApkInstallUiState

    data class Error(val message: String) : ApkInstallUiState
}
