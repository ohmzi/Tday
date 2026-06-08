package com.ohmz.tday.compose.feature.app

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ohmz.tday.compose.R
import com.ohmz.tday.compose.core.data.server.VersionCheckResult
import com.ohmz.tday.compose.feature.release.GitHubAsset
import com.ohmz.tday.compose.feature.release.GitHubRelease
import com.ohmz.tday.compose.feature.release.InAppApkUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.IOException
import kotlin.math.roundToInt

@Composable
fun UpdateRequiredOverlay(
    versionCheckResult: VersionCheckResult,
    requiredUpdateRelease: GitHubRelease?,
    isCheckingRelease: Boolean,
    onRetry: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val installPermissionToast = stringResource(R.string.install_permission_return_to_app)
    val consumeAllTouchesSource = remember { MutableInteractionSource() }
    val scope = rememberCoroutineScope()

    val installerEvent by InAppApkUpdater.installEvent.collectAsStateWithLifecycle()
    var installState by remember { mutableStateOf<OverlayInstallState>(OverlayInstallState.Idle) }
    var pendingAsset by remember { mutableStateOf<GitHubAsset?>(null) }

    val installPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) {
        val asset = pendingAsset ?: return@rememberLauncherForActivityResult
        if (InAppApkUpdater.canInstallPackages(context)) {
            pendingAsset = null
            startOverlayInstall(context, asset, scope) { installState = it }
        }
    }

    val installConfirmationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { }

    OnOverlayResume {
        val asset = pendingAsset ?: return@OnOverlayResume
        if (!InAppApkUpdater.canInstallPackages(context)) return@OnOverlayResume
        pendingAsset = null
        startOverlayInstall(context, asset, scope) { installState = it }
    }

    LaunchedEffect(installerEvent) {
        when (val event = installerEvent) {
            is InAppApkUpdater.InstallEvent.PendingUserAction -> {
                installState = OverlayInstallState.Installing
                runCatching {
                    installConfirmationLauncher.launch(event.confirmationIntent)
                }.onSuccess {
                    InAppApkUpdater.clearPendingUserAction(event.sessionId)
                }.onFailure {
                    installState = OverlayInstallState.Error(
                        "Could not open the system installer.",
                    )
                }
            }

            is InAppApkUpdater.InstallEvent.Success -> {
                installState = OverlayInstallState.Idle
                pendingAsset = null
                InAppApkUpdater.clearInstallEvent()
            }

            is InAppApkUpdater.InstallEvent.Error -> {
                installState = OverlayInstallState.Error(event.message)
                InAppApkUpdater.clearInstallEvent()
            }

            is InAppApkUpdater.InstallEvent.SignatureConflict -> {
                installState = OverlayInstallState.Error(
                    "Signature conflict. Uninstall the current app first, then install the update.",
                )
                InAppApkUpdater.clearInstallEvent()
            }

            InAppApkUpdater.InstallEvent.Idle -> Unit
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
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
                .widthIn(max = 420.dp)
                .padding(horizontal = 24.dp),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
        ) {
            Column(
                modifier = Modifier
                    .padding(28.dp)
                    .animateContentSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                when (versionCheckResult) {
                    is VersionCheckResult.AppUpdateRequired -> {
                        val apkAsset = requiredUpdateRelease?.apkAsset
                        val isInstallerBusy =
                            installState is OverlayInstallState.Downloading ||
                                installState is OverlayInstallState.Installing

                        Icon(
                            imageVector = ImageVector.vectorResource(R.drawable.ic_lucide_download),
                            contentDescription = null,
                            tint = colorScheme.primary,
                            modifier = Modifier.size(48.dp),
                        )
                        Text(
                            text = stringResource(R.string.app_update_required_title),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = colorScheme.onSurface,
                        )
                        Text(
                            text = stringResource(
                                R.string.app_update_required_message,
                                versionCheckResult.requiredVersion,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colorScheme.onSurface.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center,
                        )

                        when {
                            isCheckingRelease -> {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp,
                                )
                            }

                            apkAsset != null -> {
                                Button(
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = {
                                        if (!InAppApkUpdater.canInstallPackages(context)) {
                                            pendingAsset = apkAsset
                                            installState =
                                                OverlayInstallState.AwaitingPermission
                                            Toast.makeText(
                                                context,
                                                installPermissionToast,
                                                Toast.LENGTH_LONG,
                                            ).show()
                                            installPermissionLauncher.launch(
                                                InAppApkUpdater
                                                    .buildInstallPermissionIntent(context),
                                            )
                                        } else {
                                            startOverlayInstall(
                                                context,
                                                apkAsset,
                                                scope,
                                            ) { installState = it }
                                        }
                                    },
                                    enabled = !isInstallerBusy,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = colorScheme.primary,
                                        contentColor = colorScheme.onPrimary,
                                    ),
                                ) {
                                    Text(overlayInstallButtonLabel(installState))
                                }
                                OverlayInstallStatus(installState = installState)
                            }

                            else -> {
                                Text(
                                    text = stringResource(
                                        R.string.app_update_download_not_ready,
                                        versionCheckResult.requiredVersion,
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colorScheme.onSurface.copy(alpha = 0.7f),
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }

                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = onRetry,
                        ) {
                            Text(stringResource(R.string.action_retry))
                        }
                    }

                    is VersionCheckResult.ServerUpdateRequired -> {
                        Icon(
                            imageVector = ImageVector.vectorResource(R.drawable.ic_lucide_triangle_alert),
                            contentDescription = null,
                            tint = colorScheme.error,
                            modifier = Modifier.size(48.dp),
                        )
                        Text(
                            text = stringResource(R.string.server_update_required_title),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = colorScheme.onSurface,
                        )
                        Text(
                            text = stringResource(
                                R.string.server_update_required_message,
                                versionCheckResult.serverVersion,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colorScheme.onSurface.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center,
                        )
                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = onRetry,
                        ) {
                            Text(stringResource(R.string.action_retry))
                        }
                    }

                    is VersionCheckResult.Compatible -> {}
                }
            }
        }
    }
}

@Composable
private fun OverlayInstallStatus(installState: OverlayInstallState) {
    val colorScheme = MaterialTheme.colorScheme
    when (installState) {
        is OverlayInstallState.Downloading -> {
            val progress = installState.progress
            if (progress != null) {
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }

        OverlayInstallState.Installing -> {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        is OverlayInstallState.Error -> {
            Text(
                text = installState.message,
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.error,
                textAlign = TextAlign.Center,
            )
        }

        OverlayInstallState.AwaitingPermission -> {
            Text(
                text = stringResource(R.string.install_permission_return_here),
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurface.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
            )
        }

        OverlayInstallState.Idle -> {}
    }
}

@Composable
private fun overlayInstallButtonLabel(state: OverlayInstallState): String = when (state) {
    OverlayInstallState.AwaitingPermission ->
        stringResource(R.string.overlay_install_awaiting_permission)
    is OverlayInstallState.Downloading -> {
        state.progress
            ?.let {
                stringResource(
                    R.string.overlay_install_downloading_progress,
                    (it * 100).roundToInt().coerceIn(0, 100),
                )
            }
            ?: stringResource(R.string.overlay_install_downloading)
    }
    OverlayInstallState.Installing -> stringResource(R.string.overlay_install_installing)
    OverlayInstallState.Idle, is OverlayInstallState.Error ->
        stringResource(R.string.overlay_install_download_and_install)
}

private fun startOverlayInstall(
    context: Context,
    asset: GitHubAsset,
    scope: CoroutineScope,
    onStateChange: (OverlayInstallState) -> Unit,
) {
    val appContext = context.applicationContext
    scope.launch {
        try {
            onStateChange(OverlayInstallState.Downloading(progress = 0f))
            InAppApkUpdater.downloadAndInstall(appContext, asset) { progress ->
                onStateChange(OverlayInstallState.Downloading(progress = progress))
            }
            onStateChange(OverlayInstallState.Installing)
        } catch (error: IOException) {
            onStateChange(
                OverlayInstallState.Error(
                    error.message?.takeIf { it.isNotBlank() } ?: "Download failed.",
                ),
            )
        }
    }
}

private sealed interface OverlayInstallState {
    data object Idle : OverlayInstallState
    data object AwaitingPermission : OverlayInstallState
    data class Downloading(val progress: Float?) : OverlayInstallState
    data object Installing : OverlayInstallState
    data class Error(val message: String) : OverlayInstallState
}

@Composable
private fun OnOverlayResume(action: () -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentAction by rememberUpdatedState(action)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                currentAction()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}
