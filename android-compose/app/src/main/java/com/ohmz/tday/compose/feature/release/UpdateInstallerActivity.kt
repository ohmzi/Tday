package com.ohmz.tday.compose.feature.release

import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.ohmz.tday.compose.R

class UpdateInstallerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleStatusIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleStatusIntent(intent)
    }

    private fun handleStatusIntent(statusIntent: Intent) {
        val sessionId = statusIntent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1)
        when (statusIntent.getIntExtra(PackageInstaller.EXTRA_STATUS, Int.MIN_VALUE)) {
            Int.MIN_VALUE -> finish()
            PackageInstaller.STATUS_PENDING_USER_ACTION -> publishPendingUserAction(
                statusIntent = statusIntent,
                sessionId = sessionId,
            )
            PackageInstaller.STATUS_SUCCESS -> {
                InAppApkUpdater.publishSuccess(sessionId)
                Toast.makeText(this, getString(R.string.release_install_success), Toast.LENGTH_SHORT).show()
                finish()
            }

            else -> {
                val statusMessage = statusIntent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                    ?.takeIf { it.isNotBlank() }
                    ?: getString(R.string.release_install_failed)
                InAppApkUpdater.publishError(sessionId, statusMessage)
                Toast.makeText(this, statusMessage, Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private fun publishPendingUserAction(
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
            val statusMessage = getString(R.string.release_install_failed)
            InAppApkUpdater.publishError(sessionId, statusMessage)
            Toast.makeText(this, statusMessage, Toast.LENGTH_LONG).show()
            finish()
            return
        }

        InAppApkUpdater.publishPendingUserAction(sessionId, confirmationIntent)
        finish()
    }
}
