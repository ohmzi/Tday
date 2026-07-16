package com.ohmz.tday.compose

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.ohmz.tday.compose.core.notification.BootRescheduleReceiver
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ -> }

    private val _deepLinkIntent = MutableStateFlow<Intent?>(null)
    val deepLinkIntent = _deepLinkIntent.asStateFlow()

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_Tday)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val launchIntent = intent.withTdayDeepLinkData()
        setIntent(launchIntent)
        dispatchDeepLinkIntent(launchIntent)
        setContent {
            TdayApp(
                onFirstFrameDrawn = {
                    (application as? TdayApplication)?.runDeferredStartup()
                    dismissUpdateReadyNotification()
                    requestNotificationPermissionIfNeeded()
                },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val deepLinkIntent = intent.withTdayDeepLinkData()
        setIntent(deepLinkIntent)
        dismissUpdateReadyNotification()
        dispatchDeepLinkIntent(deepLinkIntent)
    }

    private fun dispatchDeepLinkIntent(intent: Intent) {
        _deepLinkIntent.value = intent.withTdayDeepLinkData()
    }

    /** Clears the pending deep link once it has been navigated, so it fires exactly once. */
    fun consumeDeepLink() {
        _deepLinkIntent.value = null
    }

    private fun dismissUpdateReadyNotification() {
        getSystemService(NotificationManager::class.java)
            .cancel(BootRescheduleReceiver.UPDATE_NOTIFICATION_ID)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            == PackageManager.PERMISSION_GRANTED
        ) return
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

internal fun Intent.withTdayDeepLinkData(): Intent {
    if (data != null) return this
    val deepLink = getStringExtra(EXTRA_DEEP_LINK)?.takeIf { it.isNotBlank() } ?: return this
    return Intent(this).apply {
        data = Uri.parse(deepLink)
    }
}

private const val EXTRA_DEEP_LINK = "deepLink"
