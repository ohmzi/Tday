package com.ohmz.tday.compose.feature.release

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import java.io.IOException
import kotlin.coroutines.coroutineContext

internal object InAppApkUpdater {

    sealed interface InstallEvent {
        data object Idle : InstallEvent

        data class PendingUserAction(
            val sessionId: Int,
            val confirmationIntent: Intent,
        ) : InstallEvent

        data class Success(val sessionId: Int) : InstallEvent

        data class Error(
            val sessionId: Int,
            val message: String,
        ) : InstallEvent
    }

    private val httpClient = OkHttpClient()
    private val _installEvent = MutableStateFlow<InstallEvent>(InstallEvent.Idle)
    val installEvent: StateFlow<InstallEvent> = _installEvent.asStateFlow()

    fun canInstallPackages(context: Context): Boolean = context.packageManager.canRequestPackageInstalls()

    fun buildInstallPermissionIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    suspend fun downloadAndInstall(
        context: Context,
        asset: GitHubAsset,
        onProgress: suspend (Float?) -> Unit,
    ) {
        withContext(Dispatchers.IO) {
            clearInstallEvent()
            val packageInstaller = context.packageManager.packageInstaller
            val sessionId = packageInstaller.createSession(buildSessionParams(context))
            var committed = false

            packageInstaller.openSession(sessionId).use { session ->
                try {
                    writeAssetToSession(session, asset, onProgress)
                    notifyDownloadComplete(onProgress)
                    session.commit(buildStatusPendingIntent(context, sessionId).intentSender)
                    committed = true
                } finally {
                    if (!committed) {
                        session.abandon()
                    }
                }
            }
        }
    }

    fun publishPendingUserAction(
        sessionId: Int,
        confirmationIntent: Intent,
    ) {
        _installEvent.value = InstallEvent.PendingUserAction(
            sessionId = sessionId,
            confirmationIntent = confirmationIntent,
        )
    }

    fun publishSuccess(sessionId: Int) {
        _installEvent.value = InstallEvent.Success(sessionId)
    }

    fun publishError(
        sessionId: Int,
        message: String,
    ) {
        _installEvent.value = InstallEvent.Error(
            sessionId = sessionId,
            message = message,
        )
    }

    fun clearPendingUserAction(sessionId: Int) {
        val event = _installEvent.value
        if (event is InstallEvent.PendingUserAction && event.sessionId == sessionId) {
            _installEvent.value = InstallEvent.Idle
        }
    }

    fun clearInstallEvent() {
        _installEvent.value = InstallEvent.Idle
    }

    private fun buildSessionParams(context: Context): PackageInstaller.SessionParams {
        return PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
            }
        }
    }

    private suspend fun writeAssetToSession(
        session: PackageInstaller.Session,
        asset: GitHubAsset,
        onProgress: suspend (Float?) -> Unit,
    ) {
        val request = Request.Builder()
            .url(asset.browserDownloadUrl)
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Update download failed (${response.code})")
            }

            val responseBody = response.body ?: throw IOException("Update download returned an empty response")
            val totalBytes = resolveTotalBytes(responseBody, asset)

            responseBody.byteStream().use { input ->
                session.openWrite(asset.name, 0, totalBytes).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var downloadedBytes = 0L
                    var lastPercent = -1

                    while (true) {
                        coroutineContext.ensureActive()
                        val readCount = input.read(buffer)
                        if (readCount < 0) break
                        output.write(buffer, 0, readCount)
                        downloadedBytes += readCount
                        lastPercent = publishDownloadProgress(
                            downloadedBytes = downloadedBytes,
                            totalBytes = totalBytes,
                            lastPercent = lastPercent,
                            onProgress = onProgress,
                        )
                    }

                    session.fsync(output)
                }
            }
        }
    }

    private fun resolveTotalBytes(
        responseBody: ResponseBody,
        asset: GitHubAsset,
    ): Long {
        return responseBody.contentLength()
            .takeIf { it > 0 }
            ?: asset.size.takeIf { it > 0 }
            ?: -1L
    }

    private suspend fun publishDownloadProgress(
        downloadedBytes: Long,
        totalBytes: Long,
        lastPercent: Int,
        onProgress: suspend (Float?) -> Unit,
    ): Int {
        if (totalBytes > 0) {
            val progressPercent = ((downloadedBytes * 100L) / totalBytes).toInt().coerceIn(0, 100)
            if (progressPercent == lastPercent) {
                return lastPercent
            }
            withContext(Dispatchers.Main.immediate) {
                onProgress(progressPercent / 100f)
            }
            return progressPercent
        }

        if (lastPercent >= 0) {
            return lastPercent
        }

        withContext(Dispatchers.Main.immediate) {
            onProgress(null)
        }
        return 0
    }

    private suspend fun notifyDownloadComplete(onProgress: suspend (Float?) -> Unit) {
        withContext(Dispatchers.Main.immediate) {
            onProgress(1f)
        }
    }

    private fun buildStatusPendingIntent(
        context: Context,
        sessionId: Int,
    ): PendingIntent {
        val statusIntent = Intent(context, UpdateInstallerActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        return PendingIntent.getActivity(
            context,
            sessionId,
            statusIntent,
            buildStatusPendingIntentFlags(),
        )
    }

    private fun buildStatusPendingIntentFlags(): Int {
        val mutabilityFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE
        } else {
            0
        }
        return PendingIntent.FLAG_UPDATE_CURRENT or mutabilityFlag
    }
}
