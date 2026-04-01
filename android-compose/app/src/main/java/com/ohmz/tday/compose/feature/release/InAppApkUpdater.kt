package com.ohmz.tday.compose.feature.release

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import kotlin.coroutines.coroutineContext

internal object InAppApkUpdater {

    private val httpClient = OkHttpClient()

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
            val request = Request.Builder()
                .url(asset.browserDownloadUrl)
                .build()
            val packageInstaller = context.packageManager.packageInstaller
            val sessionParams = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(context.packageName)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
                }
            }
            val sessionId = packageInstaller.createSession(sessionParams)
            var committed = false

            packageInstaller.openSession(sessionId).use { session ->
                try {
                    httpClient.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            throw IOException("Update download failed (${response.code})")
                        }

                        val responseBody = response.body ?: throw IOException("Update download returned an empty response")
                        val totalBytes = responseBody.contentLength()
                            .takeIf { it > 0 }
                            ?: asset.size.takeIf { it > 0 }
                            ?: -1L

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

                                    if (totalBytes > 0) {
                                        val progressPercent =
                                            ((downloadedBytes * 100L) / totalBytes).toInt().coerceIn(0, 100)
                                        if (progressPercent != lastPercent) {
                                            lastPercent = progressPercent
                                            withContext(Dispatchers.Main.immediate) {
                                                onProgress(progressPercent / 100f)
                                            }
                                        }
                                    } else if (lastPercent < 0) {
                                        lastPercent = 0
                                        withContext(Dispatchers.Main.immediate) {
                                            onProgress(null)
                                        }
                                    }
                                }

                                session.fsync(output)
                            }
                        }
                    }

                    withContext(Dispatchers.Main.immediate) {
                        onProgress(1f)
                    }

                    val statusIntent = Intent(context, UpdateInstallerActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    }
                    val statusPendingIntent = PendingIntent.getActivity(
                        context,
                        sessionId,
                        statusIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    )
                    session.commit(statusPendingIntent.intentSender)
                    committed = true
                } catch (error: Exception) {
                    if (!committed) {
                        session.abandon()
                    }
                    throw error
                }
            }
        }
    }
}
