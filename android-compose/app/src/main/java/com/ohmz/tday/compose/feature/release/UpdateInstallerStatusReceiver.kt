package com.ohmz.tday.compose.feature.release

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.widget.Toast
import com.ohmz.tday.compose.R

class UpdateInstallerStatusReceiver : BroadcastReceiver() {

    override fun onReceive(
        context: Context,
        statusIntent: Intent,
    ) {
        val sessionId = statusIntent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1)
        when (statusIntent.getIntExtra(PackageInstaller.EXTRA_STATUS, Int.MIN_VALUE)) {
            Int.MIN_VALUE -> Unit
            PackageInstaller.STATUS_PENDING_USER_ACTION -> publishPendingUserAction(
                context = context,
                statusIntent = statusIntent,
                sessionId = sessionId,
            )

            PackageInstaller.STATUS_SUCCESS -> {
                InAppApkUpdater.publishSuccess(sessionId)
                Toast.makeText(context, context.getString(R.string.release_install_success), Toast.LENGTH_SHORT).show()
            }

            PackageInstaller.STATUS_FAILURE_CONFLICT -> {
                InAppApkUpdater.publishSignatureConflict(sessionId)
                Toast.makeText(
                    context,
                    context.getString(R.string.release_signature_conflict),
                    Toast.LENGTH_LONG,
                ).show()
            }

            else -> {
                val statusMessage = statusIntent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                    ?.takeIf { it.isNotBlank() }
                    ?: context.getString(R.string.release_install_failed)
                InAppApkUpdater.publishError(sessionId, statusMessage)
                Toast.makeText(context, statusMessage, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun publishPendingUserAction(
        context: Context,
        statusIntent: Intent,
        sessionId: Int,
    ) {
        val confirmationIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            statusIntent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            statusIntent.getParcelableExtra(Intent.EXTRA_INTENT)
        }

        if (confirmationIntent == null) {
            val statusMessage = context.getString(R.string.release_install_failed)
            InAppApkUpdater.publishError(sessionId, statusMessage)
            Toast.makeText(context, statusMessage, Toast.LENGTH_LONG).show()
            return
        }

        InAppApkUpdater.publishPendingUserAction(sessionId, confirmationIntent)
    }
}
